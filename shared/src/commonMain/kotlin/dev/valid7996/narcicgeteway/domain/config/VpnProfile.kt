@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.valid7996.narcicgeteway.domain.config

import dev.valid7996.narcicgeteway.crypto.ZsxCrypto
import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ProfileSource {
    @Serializable
    @SerialName("link")
    data class Link(val link: String) : ProfileSource

    @Serializable
    @SerialName("json")
    data class RawJson(val json: String) : ProfileSource

    @Serializable
    @SerialName("sealed")
    data class Sealed(val zsxBase64: String) : ProfileSource

    @Serializable
    @SerialName("psiphon")
    data class Psiphon(val settings: PsiphonProfile) : ProfileSource

    @Serializable
    @SerialName("sni_spoof")
    data class SniSpoof(val settings: SniSpoofProfile) : ProfileSource

    @Serializable
    @SerialName("aether")
    data class Aether(val settings: AetherProfile) : ProfileSource

    @Serializable
    @SerialName("proxy_chain")
    data class ProxyChain(val memberIds: List<String>) : ProfileSource

    @Serializable
    @SerialName("cross_chain")
    data class CrossChain(val innerId: String, val outerId: String) : ProfileSource

    @Serializable
    @SerialName("auto_select")
    data class AutoSelect(val subscriptionId: String? = null) : ProfileSource

    @Serializable
    @SerialName("sing_box")
    data class SingBox(val json: String, val carrier: String? = null, val link: String? = null) : ProfileSource

    @Serializable
    @SerialName("sing_box_config")
    data class SingBoxConfig(val json: String) : ProfileSource
}

@Serializable
data class VpnProfile(
    val id: String,
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val transportLabel: String,
    val source: ProfileSource,
    val addedAt: Long,
    val isLocked: Boolean = false,

    val subscriptionId: String = "",
    val lastPingMs: Int? = null,
    val countryCode: String? = null,

    val bytesDown: Long = 0,
    val bytesUp: Long = 0,

    val note: String? = null,
    val expiresAt: Long? = null,
) {
    val isCustom: Boolean get() = source is ProfileSource.RawJson

    val isServerless: Boolean by lazy { (source as? ProfileSource.RawJson)?.json?.let(CustomConfig::isServerless) == true }

    val isPsiphon: Boolean get() = source is ProfileSource.Psiphon

    val isDnsTunnel: Boolean get() = false

    val isMasterDns: Boolean get() = false

    val isMhrvRelay: Boolean get() = false

    val isTor: Boolean get() = false

    val isSsh: Boolean get() = false

    val isLoopbackOrRstaPort: Boolean
        get() = SniSpoofConfig.isSpoofTarget(address, port)

    val isSniSpoof: Boolean get() = source is ProfileSource.SniSpoof || isLoopbackOrRstaPort

    /** The SNI spoof that runs without root, on the SNI spoof engine; its address says nothing about the server, so it is pinged through the engine. */
    val isNoRootSniSpoof: Boolean
        get() = (source as? ProfileSource.SniSpoof)?.settings?.noRoot == true ||
            (isLoopbackOrRstaPort && source !is ProfileSource.CrossChain && source !is ProfileSource.AutoSelect)

    val isOpenConnect: Boolean get() = false

    val isAether: Boolean get() = ((resolveUnsealed() ?: this).source is ProfileSource.Aether)

    val isIkev2: Boolean get() = false

    val isSstp: Boolean get() = false

    val isProxyChain: Boolean get() = source is ProfileSource.ProxyChain

    val isAutoSelect: Boolean get() = source is ProfileSource.AutoSelect

    val isSingBox: Boolean get() = source is ProfileSource.SingBox

    val isSingBoxConfig: Boolean get() = source is ProfileSource.SingBoxConfig

    fun autoSelectSettings(): ProfileSource.AutoSelect? = source as? ProfileSource.AutoSelect

    private val chainProtocol: Protocol?
        get() = (source as? ProfileSource.Link)
            ?.let { runCatching { ConfigParser.parse(it.link) }.getOrNull()?.protocol }

    val needsUdpHop: Boolean
        get() = chainProtocol?.let { it.isWireguardFamily || it == Protocol.HYSTERIA } == true

    val carriesUdpHop: Boolean get() = chainProtocol?.carriesUdp != false

    fun sshSettings(): Any? = null

    fun sniSpoofSettings(): SniSpoofProfile? {
        if (source is ProfileSource.CrossChain || source is ProfileSource.AutoSelect) return null
        if (source is ProfileSource.SniSpoof) {
            val s = (source as ProfileSource.SniSpoof).settings
            return SniSpoofProfile(
                link = s.link,
                fakeSni = SniSpoofConfig.defaultFakeSni.ifBlank { s.fakeSni },
                cleanIp = SniSpoofConfig.defaultConnectIp.ifBlank { s.cleanIp },
                noRoot = true,
                method = SniSpoofConfig.defaultMethod.ifBlank { s.method },
                connectPort = SniSpoofConfig.defaultConnectPort.takeIf { it in 1..65535 && it != SniSpoofConfig.RSTA_LOCAL_PORT } ?: s.connectPort,
            )
        }
        if (isLoopbackOrRstaPort) {
            val rawLnk = (source as? ProfileSource.Link)?.link
                ?: (source as? ProfileSource.RawJson)?.json
                ?: ""
            return SniSpoofProfile(
                link = rawLnk,
                fakeSni = SniSpoofConfig.defaultFakeSni,
                cleanIp = SniSpoofConfig.defaultConnectIp,
                noRoot = true,
                method = SniSpoofConfig.defaultMethod,
                connectPort = SniSpoofConfig.defaultConnectPort,
            )
        }
        return null
    }

    val isManagedTunnel: Boolean
        get() = isPsiphon || isSniSpoof || isAether || isCrossChain || isSingBoxConfig

    val isDnsBasedTunnel: Boolean get() = false

    fun psiphonSettings(): PsiphonProfile? = (source as? ProfileSource.Psiphon)?.settings

    fun dnsTunnelSettings(): Any? = null

    fun masterDnsSettings(): Any? = null

    fun mhrvRelaySettings(): Any? = null

    fun openConnectSettings(): Any? = null

    fun aetherSettings(): AetherProfile? = ((resolveUnsealed() ?: this).source as? ProfileSource.Aether)?.settings

    fun ikev2Settings(): Any? = null

    fun sstpSettings(): Any? = null

    fun proxyChainSettings(): List<String>? = (source as? ProfileSource.ProxyChain)?.memberIds

    val isCrossChain: Boolean get() = source is ProfileSource.CrossChain

    val canCarryChain: Boolean
        get() = when (source) {
            is ProfileSource.Link, is ProfileSource.RawJson, is ProfileSource.Sealed,
            is ProfileSource.ProxyChain, is ProfileSource.SingBox -> true
            else -> false
        }

    val canDialThroughProxy: Boolean
        get() = when (source) {
            is ProfileSource.Link, is ProfileSource.RawJson, is ProfileSource.Sealed,
            is ProfileSource.ProxyChain, is ProfileSource.SingBox -> true
            else -> false
        }

    val carrierRelaysUdp: Boolean
        get() = when (source) {
            is ProfileSource.Link, is ProfileSource.RawJson, is ProfileSource.Sealed,
            is ProfileSource.ProxyChain, is ProfileSource.SingBox, is ProfileSource.Psiphon -> true
            else -> false
        }

    fun crossChainSettings(): Pair<String, String>? =
        (source as? ProfileSource.CrossChain)?.let { it.innerId to it.outerId }

    val isEditable: Boolean get() = !isLocked

    fun resolveUnsealed(): VpnProfile? {
        val sealed = source as? ProfileSource.Sealed ?: return null
        val payload = runCatching {
            dev.valid7996.narcicgeteway.crypto.ZsxCrypto.open(Base64.decode(sealed.zsxBase64), password = null)
        }.getOrNull()?.trim() ?: return null
        if (payload.isEmpty()) return null

        // 1. narcicgeteway:// link
        if (NrgLink.isNrgLink(payload)) {
            val parsed = NrgLink.parse(payload)
            if (parsed != null) {
                val src = parsed.source
                return when (src) {
                    is ProfileSource.Aether -> copy(
                        source = src,
                        protocol = "AETHER",
                        address = src.settings.server,
                        port = src.settings.serverPort,
                        transportLabel = src.settings.protocol.uppercase(),
                    )
                    else -> copy(source = src)
                }
            }
        }

        // 2. Aether link
        if (AetherLink.isAetherLink(payload)) {
            val parsed = AetherLink.parse(payload)
            if (parsed != null) {
                val (profName, settings) = parsed
                return copy(
                    source = ProfileSource.Aether(settings),
                    protocol = "AETHER",
                    address = settings.server,
                    port = settings.serverPort,
                    name = profName.ifBlank { name },
                    transportLabel = settings.protocol.uppercase(),
                )
            }
        }

        // 3. SniSpoof link
        if (SniSpoofLink.isSniSpoofLink(payload)) {
            val parsed = SniSpoofLink.parse(payload)
            if (parsed != null) {
                val (profName, settings) = parsed
                return copy(
                    source = ProfileSource.SniSpoof(settings),
                    protocol = "SniSpoof",
                    name = profName.ifBlank { name },
                )
            }
        }

        // 4. SingBox links (vless with reality, hysteria2, tuic, wireguard, etc.)
        if (SingBoxLinks.handles(payload)) {
            val parsed = SingBoxLinks.parse(payload)
            return copy(source = ProfileSource.SingBox(parsed.fragment, parsed.name), protocol = parsed.name)
        }

        // 5. Sing-box JSON
        if (SingBoxJson.isSingBox(payload)) {
            return copy(source = ProfileSource.SingBoxConfig(payload), protocol = "SingBox")
        }

        // 6. Standard Xray link
        val xScheme = payload.substringBefore("://", "").lowercase()
        if (Protocol.fromScheme(xScheme) != null) {
            val parsed = runCatching { ConfigParser.parse(payload) }.getOrNull()
            if (parsed != null) {
                return copy(
                    source = ProfileSource.Link(payload),
                    protocol = parsed.protocol.name,
                    address = parsed.address,
                    port = parsed.port,
                )
            }
        }

        // 7. Custom Xray JSON
        if (CustomConfig.looksLikeCustomJson(payload)) {
            return copy(source = ProfileSource.RawJson(payload), protocol = "Xray")
        }

        return null
    }

    fun rawPayload(): String? = when (val src = source) {
        is ProfileSource.Link -> src.link
        is ProfileSource.RawJson -> src.json

        is ProfileSource.Sealed -> null

        is ProfileSource.Psiphon -> NrgLink.build(name, src)

        is ProfileSource.SniSpoof -> SniSpoofLink.build(src.settings, name)
        is ProfileSource.Aether -> AetherLink.build(src.settings, name)

        is ProfileSource.ProxyChain -> null
        is ProfileSource.CrossChain -> null

        is ProfileSource.AutoSelect -> null

        is ProfileSource.SingBox -> src.link ?: src.json
        is ProfileSource.SingBoxConfig -> src.json
    }

    fun toXrayConfigJson(
        options: XrayJsonBuilder.BuildOptions = XrayJsonBuilder.BuildOptions(),
        forSpeedtest: Boolean = false,
    ): String = when (val src = source) {
        is ProfileSource.Link -> if (SingBoxLinks.handles(src.link)) {
            val parsed = SingBoxLinks.parse(src.link)
            XrayJsonBuilder.buildSingBox(parsed.fragment, parsed.name, options = options, forSpeedtest = forSpeedtest)
        } else {
            val parsed = ConfigParser.parse(src.link)
            val target = if (isLoopbackOrRstaPort && forSpeedtest) {
                val serverName = parsed.tls.sni?.takeIf { it.isNotBlank() && !SniSpoofConfig.isLoopback(it) }
                    ?: parsed.transport.host?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() && !SniSpoofConfig.isLoopback(it) }
                    ?: SniSpoofConfig.defaultFakeSni
                parsed.copy(
                    address = "127.0.0.1",
                    port = SniSpoofConfig.RSTA_LOCAL_PORT,
                    tls = parsed.tls.copy(
                        sni = serverName,
                        verifyPeerCertByName = parsed.tls.verifyPeerCertByName?.takeIf { it.isNotBlank() } ?: serverName,
                        allowInsecure = true,
                    ),
                )
            } else {
                parsed
            }
            XrayJsonBuilder.build(
                target,
                options = options,
                forSpeedtest = forSpeedtest,
            )
        }
        is ProfileSource.RawJson -> XrayJsonBuilder.normalizeRawJson(
            src.json,
            forSpeedtest = forSpeedtest,
            geoAssetsAvailable = options.geoAssetsAvailable,
        )
        is ProfileSource.Sealed -> {
            val unsealed = resolveUnsealed()
            if (unsealed != null && unsealed.source !is ProfileSource.Sealed) {
                unsealed.toXrayConfigJson(options, forSpeedtest)
            } else {
                configJsonFromPayload(
                    dev.valid7996.narcicgeteway.crypto.ZsxCrypto.open(Base64.decode(src.zsxBase64), password = null),
                    options,
                    forSpeedtest,
                )
            }
        }

        is ProfileSource.Psiphon -> throw IllegalStateException("Psiphon profiles do not build Xray config")
        is ProfileSource.Aether -> throw IllegalStateException("Aether profiles do not build Xray config")

        is ProfileSource.ProxyChain ->
            throw IllegalStateException("Proxy-chain profiles are built via ConfigRepository.buildChainConfig")

        is ProfileSource.CrossChain ->
            throw IllegalStateException("Cross-chain profiles are built via ConfigRepository.buildCrossChainConfig")
        is ProfileSource.AutoSelect ->
            throw IllegalStateException("Auto-select profiles are built via ConfigRepository.buildAutoSelectConfig")
        is ProfileSource.SingBox -> XrayJsonBuilder.buildSingBox(
            fragment = src.json,
            carrier = src.carrier,
            options = options,
            forSpeedtest = forSpeedtest,
        )

        is ProfileSource.SingBoxConfig -> if (forSpeedtest) {
            val server = SingBoxJson.probeServer(src.json)
                ?: throw IllegalStateException("The sing-box config has no server to measure")
            XrayJsonBuilder.buildSingBox(server.fragment, server.tag, options, forSpeedtest = true)
        } else {
            throw IllegalStateException("sing-box configs run on the sing-box engine")
        }

        is ProfileSource.SniSpoof -> if (CustomConfig.looksLikeCustomJson(src.settings.link)) {
            val normalized = XrayJsonBuilder.normalizeRawJson(
                src.settings.link,
                forSpeedtest = forSpeedtest,
                geoAssetsAvailable = options.geoAssetsAvailable,
            )
            if (forSpeedtest) {
                XrayJsonBuilder.rewriteToLoopbackSpoof(normalized)
            } else {
                normalized
            }
        } else {
            val parsed = ConfigParser.parse(src.settings.link)
            val target = if (forSpeedtest) {
                val serverName = parsed.tls.sni?.takeIf { it.isNotBlank() && !SniSpoofConfig.isLoopback(it) }
                    ?: parsed.transport.host?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() && !SniSpoofConfig.isLoopback(it) }
                    ?: if (!SniSpoofConfig.isLoopback(parsed.address)) parsed.address else null
                    ?: src.settings.fakeSni.ifBlank { SniSpoofConfig.defaultFakeSni }

                parsed.copy(
                    address = "127.0.0.1",
                    port = SniSpoofConfig.RSTA_LOCAL_PORT,
                    tls = parsed.tls.copy(
                        sni = serverName,
                        verifyPeerCertByName = parsed.tls.verifyPeerCertByName?.takeIf { it.isNotBlank() } ?: serverName,
                        allowInsecure = true,
                    ),
                )
            } else {
                parsed
            }
            XrayJsonBuilder.build(
                target,
                options = options,
                forSpeedtest = forSpeedtest,
            )
        }
    }

    companion object {
        fun fromPsiphon(
            settings: PsiphonProfile,
            id: String,
            addedAt: Long,
            name: String,
            subscriptionId: String = "",
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "Psiphon" },
            protocol = "PSIPHON",
            address = "psiphon",
            port = 0,
            transportLabel = "Psiphon",
            source = ProfileSource.Psiphon(settings),
            addedAt = addedAt,
            subscriptionId = subscriptionId,
            countryCode = settings.country.takeIf { it.isNotBlank() }?.uppercase(),
        )

        fun fromAether(
            settings: AetherProfile,
            id: String,
            addedAt: Long,
            name: String,
            subscriptionId: String = "",
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "Aether ${settings.server.ifBlank { "scan" }}" },
            protocol = "AETHER",
            address = settings.server,
            port = settings.serverPort,
            transportLabel = settings.protocol.uppercase(),
            source = ProfileSource.Aether(settings),
            addedAt = addedAt,
            subscriptionId = subscriptionId,
        )

        fun fromAutoSelect(subscriptionId: String?, groupName: String, members: Int): VpnProfile = VpnProfile(
            id = AutoSelectIds.of(subscriptionId),
            name = groupName,
            protocol = "AUTO",
            address = "auto",
            port = 0,
            transportLabel = "Auto-select · $members",
            source = ProfileSource.AutoSelect(subscriptionId),
            addedAt = 0,
            subscriptionId = subscriptionId.orEmpty(),
        )

        fun fromSingBox(
            server: SingBoxJson.Server,
            id: String,
            addedAt: Long,
            subscriptionId: String = "",
            name: String? = null,
            link: String? = null,
        ): VpnProfile = VpnProfile(
            id = id,
            name = name?.takeIf { it.isNotBlank() } ?: server.tag,

            protocol = server.type.removeSuffix("-client").uppercase(),
            address = server.address ?: "-",
            port = server.port ?: 0,
            transportLabel = "${server.label} · sing-box",
            source = ProfileSource.SingBox(json = server.fragment, carrier = server.tag, link = link),
            addedAt = addedAt,
            subscriptionId = subscriptionId,
        )

        fun fromSingBoxConfig(
            json: String,
            id: String,
            addedAt: Long,
            name: String? = null,
            subscriptionId: String = "",
        ): VpnProfile {
            val probe = SingBoxJson.probeServer(json)
            return VpnProfile(
                id = id,
                name = name?.takeIf { it.isNotBlank() } ?: probe?.tag ?: "sing-box config",
                protocol = "SING-BOX",
                address = probe?.address ?: "-",
                port = probe?.port ?: 0,
                transportLabel = "sing-box config",
                source = ProfileSource.SingBoxConfig(json),
                addedAt = addedAt,
                subscriptionId = subscriptionId,
            )
        }

        fun fromProxyChain(
            memberIds: List<String>,
            id: String,
            addedAt: Long,
            name: String,
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "Proxy chain" },
            protocol = "CHAIN",
            address = "chain",
            port = 0,
            transportLabel = "Proxy chain · ${memberIds.size}",
            source = ProfileSource.ProxyChain(memberIds),
            addedAt = addedAt,
        )

        fun fromCrossChain(
            innerId: String,
            outerId: String,
            id: String,
            addedAt: Long,
            name: String,
            innerName: String = "",
            outerName: String = "",
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank {
                if (innerName.isNotBlank() && outerName.isNotBlank()) "$innerName via $outerName"
                else "Cross chain"
            },
            protocol = "CROSSCHAIN",
            address = "cross",
            port = 0,

            transportLabel = if (innerName.isNotBlank() && outerName.isNotBlank()) {
                "$innerName \u2192 $outerName"
            } else "Cross chain",
            source = ProfileSource.CrossChain(innerId = innerId, outerId = outerId),
            addedAt = addedAt,
        )

        fun fromSniSpoof(settings: SniSpoofProfile, id: String, addedAt: Long, name: String): VpnProfile {
            val custom = CustomConfig.looksLikeCustomJson(settings.link)
            val info = if (custom) CustomConfig.inspect(settings.link) else null
            val parsed =
                if (custom) null else runCatching { ConfigParser.parse(settings.link) }.getOrNull()
            return VpnProfile(
                id = id,
                name = name.ifBlank {
                    (info?.remarks ?: parsed?.remark)?.ifBlank { null } ?: "SNI spoof"
                },
                protocol = "SNI-SPOOF",

                address = info?.address ?: parsed?.address ?: "sni-spoof",
                port = info?.port ?: parsed?.port ?: 0,
                transportLabel = "SNI spoof",
                source = ProfileSource.SniSpoof(settings),
                addedAt = addedAt,
            )
        }


        fun fromNrgLink(
            link: String,
            id: String,
            addedAt: Long,
            subscriptionId: String = "",
        ): VpnProfile? {
            val payload = NrgLink.parse(link) ?: return null
            val name = payload.name
            return when (val src = payload.source) {
                is ProfileSource.Aether -> fromAether(src.settings, id, addedAt, name, subscriptionId = subscriptionId)
                is ProfileSource.Psiphon -> fromPsiphon(src.settings, id, addedAt, name, subscriptionId = subscriptionId)
                else -> null
            }
        }

        internal fun configJsonFromPayload(
            payload: String,
            options: XrayJsonBuilder.BuildOptions = XrayJsonBuilder.BuildOptions(),
            forSpeedtest: Boolean = false,
        ): String {
            val trimmed = payload.trim()
            return when {
                CustomConfig.looksLikeCustomJson(trimmed) -> {
                    XrayJsonBuilder.normalizeRawJson(
                        trimmed,
                        forSpeedtest = forSpeedtest,
                        geoAssetsAvailable = options.geoAssetsAvailable,
                    )
                }
                SingBoxLinks.handles(trimmed) -> {
                    val parsed = SingBoxLinks.parse(trimmed)
                    XrayJsonBuilder.buildSingBox(parsed.fragment, parsed.name, options = options, forSpeedtest = forSpeedtest)
                }
                else -> {
                    XrayJsonBuilder.build(
                        ConfigParser.parse(trimmed),
                        options = options,
                        forSpeedtest = forSpeedtest,
                    )
                }
            }
        }

        fun fromLink(
            link: String,
            id: String,
            addedAt: Long,
            locked: Boolean = false,
            subscriptionId: String = "",
        ): VpnProfile {
            val parsed = ConfigParser.parse(link)
            return VpnProfile(
                id = id,
                name = parsed.remark.ifBlank { "${parsed.address}:${parsed.port}" },
                protocol = parsed.protocol.name,
                address = parsed.address,
                port = parsed.port,
                transportLabel = parsed.transportLabel,
                source = ProfileSource.Link(link),
                addedAt = addedAt,
                isLocked = locked,
                subscriptionId = subscriptionId,

                countryCode = null,
            )
        }

        fun fromRawJson(
            rawJson: String,
            id: String,
            addedAt: Long,
            fallbackName: String? = null,
            subscriptionId: String = "",
        ): VpnProfile {
            val info = CustomConfig.inspect(rawJson)
            val name = info.remarks
                ?: fallbackName?.takeIf { it.isNotBlank() }
                ?: info.address?.let { a -> info.port?.let { p -> "$a:$p" } ?: a }
                ?: "Custom config"

            return VpnProfile(
                id = id,
                name = name,
                protocol = "CUSTOM",
                address = info.address ?: "-",
                port = info.port ?: 0,
                transportLabel = "Custom config",
                source = ProfileSource.RawJson(rawJson),
                addedAt = addedAt,
                subscriptionId = subscriptionId,
                countryCode = null,
            )
        }
    }
}
