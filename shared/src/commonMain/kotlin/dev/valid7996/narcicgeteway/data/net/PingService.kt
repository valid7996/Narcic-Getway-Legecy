package dev.valid7996.narcicgeteway.data.net

import dev.valid7996.narcicgeteway.core.CoreProbe
import dev.valid7996.narcicgeteway.core.LogBus
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.domain.config.AetherProfile
import dev.valid7996.narcicgeteway.domain.config.CustomConfig
import dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.platform.httpTimedTransfer
import dev.valid7996.narcicgeteway.platform.tcpConnectMillis
import dev.valid7996.narcicgeteway.platform.udpProbeIkev2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object PingService {
    private const val TCP_TIMEOUT_MS = 2_500

    /** An Aether profile has no server to knock on: its core is started for the test (see AetherDelayProbe) and timed through. */
    private const val AETHER_PROBE_TIMEOUT_MS = 150_000L

    /**
     * Runs a core for an Aether profile and hands the port of its SOCKS listener to the block, whose result it returns.
     * The platform sets it; without it an Aether profile cannot be pinged.
     */
    /**
     * Keeps the no-root SNI spoof engine running (with the settings of the app) until the returned function is called;
     * null when the engine could not be started. A profile that points at 127.0.0.1:40443 has no server of its own: it
     * can only be measured while the engine is up, which is what the real delay needs. The platform sets it.
     */
    @Volatile
    var rstaSpoofLease: (suspend () -> (() -> Unit)?)? = null

    @Volatile
    var aetherTunnel: (suspend (AetherProfile, suspend (Int) -> Long) -> Long)? = null

    @Volatile
    var sniProbe: (suspend (String, String) -> Long)? = null

    private const val REAL_PING_CONCURRENCY = 16

    private const val GEO_CONCURRENCY = 8

    const val FAILED_PING = -1

    private const val PRECHECK_TIMEOUT_MS = 2_200

    private const val PROBE_TIMEOUT_MS = 5_500L

    private const val FIRST_PROBE_TIMEOUT_MS = 3_800L

    private const val FALLBACK_PROBE_TIMEOUT_MS = 2_200L

    private const val REQUEST_PROBE_TIMEOUT_MS = 10_000

    private const val CUSTOM_PROBE_CONCURRENCY = 16

    private val geoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun ikev2Probe(host: String, port: Int = 500, timeoutMs: Int = 2_500): Long =
        udpProbeIkev2(host, port, timeoutMs)

    suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = TCP_TIMEOUT_MS): Long =
        withContext(Dispatchers.IO) {
            val tcp = tcpConnectMillis(host, port, timeoutMs)
            if (tcp > 0) return@withContext tcp
            if (port == 500 || port == 4500) {
                val ike = ikev2Probe(host, port, timeoutMs)
                if (ike > 0) return@withContext ike
            }
            -1L
        }

    suspend fun realDelay(
        profile: VpnProfile,
        url: String = NetworkInfoRepository.DELAY_TEST_URL,
        chainConfig: (VpnProfile) -> String? = { null },
    ): Long = withContext(Dispatchers.IO) { measureProfile(profile, url, chainConfig) }

    suspend fun activeConnectionHealth(
        url: String = NetworkInfoRepository.DELAY_TEST_URL,
    ): CoreProbe.DelayOutcome = withContext(Dispatchers.IO) {
        val first = probeActiveConnection(url)
        if (first.ok) return@withContext first
        if (url != NetworkInfoRepository.DELAY_TEST_URL2) {
            val second = probeActiveConnection(NetworkInfoRepository.DELAY_TEST_URL2)
            if (second.ok) return@withContext second
            return@withContext CoreProbe.DelayOutcome(-1L, second.error ?: first.error)
        }
        first
    }

    private suspend fun probeActiveConnection(url: String): CoreProbe.DelayOutcome {
        if (runsXrayCore(VpnManager.activeKind.value)) {
            return runCatching { CoreProbe.measureDelayDetailed(url) }
                .getOrElse { CoreProbe.DelayOutcome(-1L, it.message) }
        }
        return runCatching {
            val timing = httpTimedTransfer(
                url,
                socksPort = VpnManager.activeSocksPort,
                upload = false,
                uploadBytes = 0,
                connectTimeoutMs = REQUEST_PROBE_TIMEOUT_MS,
                readTimeoutMs = REQUEST_PROBE_TIMEOUT_MS,
            )
            CoreProbe.DelayOutcome((timing.ttfbNanos / 1_000_000).coerceAtLeast(1), null)
        }.getOrElse { CoreProbe.DelayOutcome(-1L, it.message ?: it.toString()) }
    }

    fun runsXrayCore(kind: String?): Boolean =
        kind == null || kind == VpnManager.KIND_XRAY || kind == VpnManager.KIND_SNISPOOF ||
            kind == VpnManager.KIND_CROSS_CHAIN

    suspend fun realDelayOfActiveConnection(
        url: String = NetworkInfoRepository.DELAY_TEST_URL,
    ): Long = withContext(Dispatchers.IO) {
        val first = probeActiveConnection(url)
        if (first.ok) return@withContext first.ms

        if (url != NetworkInfoRepository.DELAY_TEST_URL2) {
            val second = probeActiveConnection(NetworkInfoRepository.DELAY_TEST_URL2)
            if (second.ok) return@withContext second.ms
        }

        -1L
    }

    suspend fun measureAll(
        profiles: List<VpnProfile>,
        useRealDelay: Boolean,

        concurrency: Int = REAL_PING_CONCURRENCY,
        delayUrl: String = NetworkInfoRepository.DELAY_TEST_URL,

        chainConfig: (VpnProfile) -> String? = { null },
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onResult: (id: String, ms: Int?, countryCode: String?) -> Unit,

    ) = withContext(Dispatchers.IO) {
        val realPermits = concurrency.takeIf { it in 1..128 } ?: REAL_PING_CONCURRENCY
        val url = delayUrl.ifBlank { NetworkInfoRepository.DELAY_TEST_URL }
        val permits = Semaphore(if (useRealDelay) realPermits else realPermits * 2)

        val customPermits = Semaphore(minOf(realPermits, CUSTOM_PROBE_CONCURRENCY))
        val geoPermits = Semaphore(GEO_CONCURRENCY)

        val targets = profiles.filterNot { ((it.isManagedTunnel && !it.isSingBoxConfig && !it.isSniSpoof && !it.isNoRootSniSpoof && !it.isMhrvRelay && !it.isSstp && !it.isIkev2) || it.isDnsBasedTunnel) || it.isAether }
        val aetherTargets = if (aetherTunnel != null) profiles.filter { it.isAether } else emptyList()
        val aetherPermits = Semaphore(1)
        val total = targets.size + aetherTargets.size
        var done = 0
        val doneLock = Semaphore(1)
        onProgress(0, total)
        val aetherJobs = aetherTargets.map { profile ->
            launch(Dispatchers.IO) {
                // One core at a time: the cores share the exit proxy and the key files.
                val ms = aetherPermits.withPermit { aetherDelay(profile, url) }
                onResult(profile.id, if (ms > 0) ms.toInt() else FAILED_PING, profile.countryCode)
                doneLock.withPermit { onProgress(++done, total) }
            }
        }
        // The engine stays up for the whole batch when some profile points at it, instead of starting and stopping around each probe.
        val batchLease = if (targets.any { it.isSniSpoof || it.isNoRootSniSpoof || SniSpoofConfig.isSpoofTarget(it.address, it.port) }) rstaSpoofLease?.invoke() else null
        try {
        (aetherJobs + targets.map { profile ->
            launch(Dispatchers.IO) {
                val isSpoof = profile.isSniSpoof || profile.isNoRootSniSpoof || SniSpoofConfig.isSpoofTarget(profile.address, profile.port)
                val isSealed = profile.isLocked || profile.source is dev.valid7996.narcicgeteway.domain.config.ProfileSource.Sealed
                val isMhrv = profile.isMhrvRelay || (isSealed && profile.resolveUnsealed()?.isMhrvRelay == true)
                val isIkev2 = profile.isIkev2 || profile.ikev2Settings() != null || (isSealed && profile.resolveUnsealed()?.isIkev2 == true)
                val isSstp = profile.isSstp || profile.sstpSettings() != null || (isSealed && profile.resolveUnsealed()?.isSstp == true)
                val heavy = useRealDelay || profile.isProxyChain || isSpoof || isSealed || isMhrv || isIkev2 || isSstp
                val pool = if (heavy && (profile.isCustom || isSealed || isMhrv || isIkev2 || isSstp)) customPermits else permits
                val ms = pool.withPermit {
                    if (heavy) measureProfile(profile, url, chainConfig)
                    else tcpPing(profile.address, profile.port)
                }

                val latency = if (ms > 0) ms.toInt() else FAILED_PING

                onResult(profile.id, latency, profile.countryCode)
                doneLock.withPermit { onProgress(++done, total) }

                if (latency > 0 && profile.countryCode == null && !isSpoof && !isSealed) {
                    geoScope.launch {
                        val country = geoPermits.withPermit { GeoLookup.countryOf(profile.address) }
                        if (country != null) onResult(profile.id, latency, country)
                    }
                }
            }
        }).joinAll()
        } finally {
            batchLease?.invoke()
        }
    }

    private suspend fun aetherDelay(profile: VpnProfile, url: String): Long {
        val runner = aetherTunnel ?: return -1L
        val effective = profile.resolveUnsealed() ?: profile
        val settings = effective.aetherSettings() ?: return -1L
        return withTimeoutOrNull(AETHER_PROBE_TIMEOUT_MS) {
            runner(settings) { port ->
                var best = -1L
                repeat(2) {
                    val ms = runCatching {
                        httpTimedTransfer(url, port, false, 0, REQUEST_PROBE_TIMEOUT_MS, REQUEST_PROBE_TIMEOUT_MS).ttfbNanos / 1_000_000
                    }.getOrDefault(-1L)
                    if (ms > 0 && (best < 0 || ms < best)) best = ms.coerceAtLeast(1)
                }
                best
            }
        } ?: -1L
    }

    private suspend fun measureProfile(
        profile: VpnProfile,
        url: String,
        chainConfig: (VpnProfile) -> String? = { null },
    ): Long {
        val effective = profile.resolveUnsealed() ?: profile
        if (effective.isIkev2 || effective.ikev2Settings() != null) {
            if (VpnManager.status.value.state == dev.valid7996.narcicgeteway.domain.model.ConnectionState.Connected &&
                VpnManager.activeKind.value == VpnManager.KIND_IKEV2
            ) {
                val activeMs = realDelayOfActiveConnection(url)
                if (activeMs > 0) return activeMs
            }
            val port = if (effective.port in 1..65535) effective.port else 500
            val ike = ikev2Probe(effective.address, port)
            if (ike > 0) return ike
            val tcp = tcpPing(effective.address, port)
            return if (tcp > 0) tcp else -1L
        }
        if (effective.isAether) return aetherDelay(effective, url)
        if (effective.isSstp) {
            val sni = effective.address
            val probe = sniProbe?.invoke(effective.address, sni)
            if (probe != null && probe > 0) return probe
            val tcp = tcpPing(effective.address, effective.port)
            return if (tcp > 0) tcp else -1L
        }
        if (effective.isMhrvRelay) {
            val mhrv = effective.mhrvRelaySettings()
            if (mhrv != null) {
                val ip = mhrv.googleIp.trim().ifEmpty { dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile.DEFAULT_GOOGLE_IP }
                val sni = mhrv.sniList().firstOrNull()?.trim()?.ifEmpty { null }
                    ?: mhrv.frontDomain.trim().ifEmpty { dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile.DEFAULT_FRONT_DOMAIN }
                val probe = sniProbe?.invoke(ip, sni)
                if (probe != null && probe > 0) return probe
                val tcp = tcpPing(ip, 443)
                return if (tcp > 0) tcp else -1L
            }
        }
        if (effective.isProxyChain) {
            val config = chainConfig(effective) ?: return -1L
            return probeWithTimeout(effective, config, url)
        }

        val config = runCatching { effective.toXrayConfigJson(forSpeedtest = true) }
            .onFailure { LogBus.append("W/PingService build config failed for ${effective.name}: ${it.message}") }
            .getOrElse { return -1L }

        return probeWithTimeout(effective, config, url)
    }

    fun shouldPrecheck(profile: VpnProfile): Boolean {
        val effective = profile.resolveUnsealed() ?: profile
        if (effective.isMhrvRelay) return false
        if (effective.isAether) return false
        if (effective.isSstp) return false
        if (effective.isIkev2 || effective.ikev2Settings() != null) return false
        if (effective.isProxyChain) return false
        if (effective.isSniSpoof || effective.isNoRootSniSpoof || SniSpoofConfig.isSpoofTarget(effective.address, effective.port)) return false
        if (effective.address.isBlank() || effective.address == "-") return false
        if (effective.port !in 1..65535) return false
        if (effective.isCustom) {
            val raw = effective.rawPayload() ?: return false
            return CustomConfig.tcpProbeable(raw)
        }

        (effective.source as? dev.valid7996.narcicgeteway.domain.config.ProfileSource.SingBox)?.let { src ->
            val servers = dev.valid7996.narcicgeteway.domain.config.SingBoxJson.servers(src.json)
            val server = servers.firstOrNull() ?: return false
            return !server.udp && !src.json.contains("\"detour\"")
        }
        if (effective.isSingBoxConfig) return false
        return effective.protocol !in UDP_OUTBOUND_PROTOCOLS
    }

    private val UDP_OUTBOUND_PROTOCOLS = setOf("WIREGUARD", "AMNEZIAWG", "HYSTERIA")

    private suspend fun probeWithTimeout(profile: VpnProfile, config: String, url: String): Long {
        val isSpoof = profile.isSniSpoof || profile.isNoRootSniSpoof || SniSpoofConfig.isSpoofTarget(profile.address, profile.port)
        if (!isSpoof) return probeCore(profile, config, url)
        val release = rstaSpoofLease?.invoke()
        if (release == null) {
            LogBus.append("W/PingService the SNI spoof engine did not start; ${profile.name} cannot be measured")
            return -1L
        }
        try {
            return probeCore(profile, config, url)
        } finally {
            release()
        }
    }

    private suspend fun probeCore(profile: VpnProfile, config: String, url: String): Long =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                val start = System.currentTimeMillis()
                val firstMs = withTimeoutOrNull(FIRST_PROBE_TIMEOUT_MS) {
                    runCatching { CoreProbe.measureOutboundDelay(config, url) }
                        .onFailure {
                            LogBus.append("W/PingService real delay failed for ${profile.name}: ${it.message}")
                        }
                        .getOrDefault(-1L)
                } ?: -1L

                if (firstMs > 0) return@withTimeoutOrNull firstMs

                val elapsed = System.currentTimeMillis() - start
                val remainingTime = PROBE_TIMEOUT_MS - elapsed
                // If first attempt timed out or almost exhausted time, don't waste time on fallback
                if (remainingTime < 1_500L) return@withTimeoutOrNull -1L

                val fallbackUrl = if (url != NetworkInfoRepository.DELAY_TEST_URL2) {
                    NetworkInfoRepository.DELAY_TEST_URL2
                } else {
                    "http://cp.cloudflare.com/generate_204"
                }

                val fallbackTimeout = minOf(remainingTime, FALLBACK_PROBE_TIMEOUT_MS)
                withTimeoutOrNull(fallbackTimeout) {
                    runCatching { CoreProbe.measureOutboundDelay(config, fallbackUrl) }
                        .onFailure {
                            LogBus.append("W/PingService fallback delay failed for ${profile.name}: ${it.message}")
                        }
                        .getOrDefault(-1L)
                } ?: -1L
            } ?: run {
                LogBus.append("W/PingService real delay timed out for ${profile.name}")
                -1L
            }
        }
}
