package dev.valid7996.narcicgeteway.core.sstp

import android.net.VpnService
import dev.valid7996.narcicgeteway.core.LogBus
import dev.valid7996.narcicgeteway.core.sstp.terminal.IPTerminal
import dev.valid7996.narcicgeteway.core.sstp.terminal.SSLTerminal
import dev.valid7996.narcicgeteway.domain.config.SstpProfile
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal enum class Where {
    CERT,
    CERT_PATH,
    SSL,
    PROXY,
    SSTP_DATA,
    SSTP_CONTROL,
    SSTP_REQUEST,
    SSTP_HASH,
    PPP,
    PAP,
    CHAP,
    MSCHAPV2,
    EAP,
    LCP,
    LCP_MRU,
    LCP_AUTH,
    IPCP,
    IPCP_IP,
    IPV6CP,
    IPV6CP_IDENTIFIER,
    IP,
    IPv4,
    IPv6,
    ROUTE,
    INCOMING,
    OUTGOING,
}

internal data class ControlMessage(
    val from: Where,
    val result: Result,
    val supplement: String? = null,
)

internal enum class Result {
    PROCEEDED,

    // common errors
    ERR_TIMEOUT,
    ERR_COUNT_EXHAUSTED,
    ERR_UNKNOWN_TYPE,
    ERR_UNEXPECTED_MESSAGE,
    ERR_PARSING_FAILED,
    ERR_VERIFICATION_FAILED,

    // for SSTP
    ERR_NEGATIVE_ACKNOWLEDGED,
    ERR_ABORT_REQUESTED,
    ERR_DISCONNECT_REQUESTED,

    // for PPP
    ERR_TERMINATE_REQUESTED,
    ERR_PROTOCOL_REJECTED,
    ERR_CODE_REJECTED,
    ERR_AUTHENTICATION_FAILED,
    ERR_ADDRESS_REJECTED,
    ERR_OPTION_REJECTED,

    // for IP
    ERR_INVALID_ADDRESS,

    // for INCOMING
    ERR_INVALID_PACKET_SIZE,
}

internal class SharedBridge(
    internal val service: VpnService,
    internal val profile: SstpProfile,
    internal val configureBuilder: (VpnService.Builder) -> Unit,
    internal val fallbackDns: List<String> = emptyList(),
    internal val onUp: () -> Unit = {},
    internal val onDown: (String?) -> Unit = {},
    internal val onLog: (String) -> Unit = {},
) {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val builder = service.Builder()
    internal lateinit var handler: CoroutineExceptionHandler

    internal val controlMailbox = Channel<ControlMessage>(Channel.BUFFERED)

    internal var sslTerminal: SSLTerminal? = null
    internal var ipTerminal: IPTerminal? = null

    internal val HOME_USERNAME = profile.username
    internal val HOME_PASSWORD = profile.password
    internal val HOME_HOSTNAME = profile.serverHost()
    internal val SSL_PORT = profile.effectivePort()
    internal val SSL_DO_VERIFY = !profile.acceptAllCertificates
    internal val SSL_CUSTOM_SNI = profile.effectiveSni()
    internal val SSL_DO_USE_CUSTOM_SNI = profile.sni.isNotBlank()

    internal val PPP_MRU = profile.mru
    internal val PPP_MTU = profile.mtu
    internal val PPP_AUTH_PROTOCOLS = setOf(AUTH_PROTOCOL_MSCHAPv2, AUTH_PROTOCOl_PAP)
    internal val PPP_IPv4_ENABLED = true
    internal val PPP_IPv6_ENABLED = profile.useIpv6

    internal var hlak: ByteArray? = null
    internal val nonce = ByteArray(32)
    internal val guid = UUID.randomUUID().toString()
    internal var hashProtocol: Byte = 0

    private val mutex = Mutex()
    private var frameID = -1

    internal var currentMRU = PPP_MRU
    internal var currentAuth = ""
    internal val currentIPv4 = ByteArray(4)
    internal val currentIPv6 = ByteArray(8)
    internal val currentProposedDNS = ByteArray(4)

    internal val rxBytes = AtomicLong(0)
    internal val txBytes = AtomicLong(0)

    internal fun log(msg: String) {
        onLog(msg)
        LogBus.append("I/SSTP $msg")
    }

    internal fun logError(msg: String) {
        onLog(msg)
        LogBus.append("E/SSTP $msg")
    }

    internal fun isEnabled(authProtocol: String): Boolean {
        return authProtocol in PPP_AUTH_PROTOCOLS
    }

    internal fun attachSSLTerminal() {
        sslTerminal = SSLTerminal(this)
    }

    internal fun attachIPTerminal() {
        ipTerminal = IPTerminal(this)
    }

    internal suspend fun allocateNewFrameID(): Byte {
        mutex.withLock {
            frameID += 1
            return frameID.toByte()
        }
    }
}
