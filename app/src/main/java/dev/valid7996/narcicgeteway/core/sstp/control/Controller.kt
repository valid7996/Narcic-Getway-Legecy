package dev.valid7996.narcicgeteway.core.sstp.control

import dev.valid7996.narcicgeteway.core.sstp.AUTH_PROTOCOL_EAP_MSCHAPv2
import dev.valid7996.narcicgeteway.core.sstp.AUTH_PROTOCOL_MSCHAPv2
import dev.valid7996.narcicgeteway.core.sstp.AUTH_PROTOCOl_PAP
import dev.valid7996.narcicgeteway.core.sstp.ControlMessage
import dev.valid7996.narcicgeteway.core.sstp.Result
import dev.valid7996.narcicgeteway.core.sstp.SharedBridge
import dev.valid7996.narcicgeteway.core.sstp.Where
import dev.valid7996.narcicgeteway.core.sstp.client.SSTP_REQUEST_TIMEOUT
import dev.valid7996.narcicgeteway.core.sstp.client.SstpClient
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.IpcpClient
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.Ipv6cpClient
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.LCPClient
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.PPPClient
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.PPP_NEGOTIATION_TIMEOUT
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.auth.ChapClient
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.auth.ChapMSCHAPV2Client
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.auth.EAPClient
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.auth.EAPMSAuthClient
import dev.valid7996.narcicgeteway.core.sstp.client.ppp.auth.PAPClient
import dev.valid7996.narcicgeteway.core.sstp.debug.assertAlways
import dev.valid7996.narcicgeteway.core.sstp.io.OutgoingManager
import dev.valid7996.narcicgeteway.core.sstp.io.incoming.IncomingManager
import dev.valid7996.narcicgeteway.core.sstp.terminal.SSL_REQUEST_INTERVAL
import dev.valid7996.narcicgeteway.core.sstp.unit.sstp.SSTP_MESSAGE_TYPE_CALL_ABORT
import dev.valid7996.narcicgeteway.core.sstp.unit.sstp.SSTP_MESSAGE_TYPE_CALL_DISCONNECT
import dev.valid7996.narcicgeteway.core.sstp.unit.sstp.SSTP_MESSAGE_TYPE_CALL_DISCONNECT_ACK
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

internal class Controller(internal val bridge: SharedBridge) {
    private var sstpClient: SstpClient? = null
    private var pppClient: PPPClient? = null
    private var incomingManager: IncomingManager? = null
    private var outgoingManager: OutgoingManager? = null

    private var lcpClient: LCPClient? = null
    private var papClient: PAPClient? = null
    private var chapClient: ChapClient? = null
    private var eapClient: EAPClient? = null
    private var ipcpClient: IpcpClient? = null
    private var ipv6cpClient: Ipv6cpClient? = null

    private var jobMain: Job? = null
    private val mutex = Mutex()
    private var isConnected = false

    private fun attachHandler() {
        bridge.handler = CoroutineExceptionHandler { _, throwable ->
            bridge.logError("Unhandled SSTP exception: ${throwable.message}")
            kill {
                bridge.onDown(throwable.message)
            }
        }
    }

    internal fun launchJobMain() {
        attachHandler()

        jobMain = bridge.scope.launch(bridge.handler) {
            bridge.log("Attaching SSL and IP terminals...")
            bridge.attachSSLTerminal()
            bridge.attachIPTerminal()

            bridge.sslTerminal!!.initialize()
            if (!expectProceeded(Where.SSL, SSL_REQUEST_INTERVAL)) {
                return@launch
            }

            IncomingManager(bridge).also {
                it.launchJobMain()
                incomingManager = it
            }

            SstpClient(bridge).also {
                sstpClient = it
                incomingManager!!.registerMailbox(it)
                it.launchJobRequest()

                if (!expectProceeded(Where.SSTP_REQUEST, SSTP_REQUEST_TIMEOUT)) {
                    return@launch
                }

                sstpClient!!.launchJobControl()
            }

            PPPClient(bridge).also {
                pppClient = it
                incomingManager!!.registerMailbox(it)
                it.launchJobControl()
            }

            LCPClient(bridge).also {
                incomingManager!!.registerMailbox(it)
                it.launchJobNegotiation()

                if (!expectProceeded(Where.LCP, PPP_NEGOTIATION_TIMEOUT)) {
                    return@launch
                }

                incomingManager!!.unregisterMailbox(it)
            }

            val authTimeout = 15_000L
            bridge.log("Authenticating using: ${bridge.currentAuth}...")
            when (bridge.currentAuth) {
                AUTH_PROTOCOl_PAP -> PAPClient(bridge).also {
                    incomingManager!!.registerMailbox(it)
                    it.launchJobAuth()

                    if (!expectProceeded(Where.PAP, authTimeout)) {
                        return@launch
                    }

                    incomingManager!!.unregisterMailbox(it)
                }

                AUTH_PROTOCOL_MSCHAPv2 -> ChapMSCHAPV2Client(bridge).also {
                    chapClient = it
                    incomingManager!!.registerMailbox(it)
                    it.launchJobAuth()

                    if (!expectProceeded(Where.CHAP, authTimeout)) {
                        return@launch
                    }
                }

                AUTH_PROTOCOL_EAP_MSCHAPv2 -> EAPMSAuthClient(bridge).also {
                    eapClient = it
                    incomingManager!!.registerMailbox(it)
                    it.launchJobAuth()

                    if (!expectProceeded(Where.EAP, authTimeout)) {
                        return@launch
                    }
                }

                else -> throw NotImplementedError(bridge.currentAuth)
            }

            bridge.log("Authentication successful! Sending SSTP CALL_CONNECTED...")
            sstpClient!!.sendCallConnected()

            if (bridge.PPP_IPv4_ENABLED) {
                bridge.log("Negotiating IPCP for IPv4 configuration...")
                IpcpClient(bridge).also {
                    incomingManager!!.registerMailbox(it)
                    it.launchJobNegotiation()

                    if (!expectProceeded(Where.IPCP, PPP_NEGOTIATION_TIMEOUT)) {
                        return@launch
                    }

                    incomingManager!!.unregisterMailbox(it)
                }
            }

            if (bridge.PPP_IPv6_ENABLED) {
                Ipv6cpClient(bridge).also {
                    incomingManager!!.registerMailbox(it)
                    it.launchJobNegotiation()

                    if (!expectProceeded(Where.IPV6CP, PPP_NEGOTIATION_TIMEOUT)) {
                        return@launch
                    }

                    incomingManager!!.unregisterMailbox(it)
                }
            }

            bridge.log("Establishing TUN interface...")
            bridge.ipTerminal!!.initialize()
            if (!expectProceeded(Where.IP, null)) {
                return@launch
            }

            OutgoingManager(bridge).also {
                it.launchJobMain()
                outgoingManager = it
            }

            isConnected = true
            bridge.log("SSTP Tunnel UP and running! Traffic is now routed.")
            bridge.onUp()

            expectProceeded(Where.SSTP_CONTROL, null)
        }
    }

    private suspend fun expectProceeded(where: Where, timeout: Long?): Boolean {
        val received = if (timeout != null) {
            withTimeoutOrNull(timeout) {
                bridge.controlMailbox.receive()
            } ?: ControlMessage(where, Result.ERR_TIMEOUT)
        } else {
            bridge.controlMailbox.receive()
        }

        if (received.result == Result.PROCEEDED) {
            assertAlways(received.from == where)
            return true
        }

        val lastPacketType = if (received.result == Result.ERR_DISCONNECT_REQUESTED) {
            SSTP_MESSAGE_TYPE_CALL_DISCONNECT_ACK
        } else {
            SSTP_MESSAGE_TYPE_CALL_ABORT
        }

        val header = "${received.from.name}: ${received.result.name}${if (received.supplement != null) " (${received.supplement})" else ""}"
        bridge.logError(header)

        kill {
            runCatching { sstpClient?.sendLastPacket(lastPacketType) }
            bridge.onDown(header)
        }

        return false
    }

    internal fun disconnect() {
        kill {
            runCatching { sstpClient?.sendLastPacket(SSTP_MESSAGE_TYPE_CALL_DISCONNECT) }
            bridge.onDown(null)
        }
    }

    internal fun kill(cleanup: (suspend () -> Unit)? = null) {
        if (!mutex.tryLock()) return

        bridge.scope.launch {
            jobMain?.cancel()
            cancelClients()
            cleanup?.invoke()
            closeTerminals()
            isConnected = false
        }
    }

    private fun cancelClients() {
        lcpClient?.cancel()
        papClient?.cancel()
        chapClient?.cancel()
        eapClient?.cancel()
        ipcpClient?.cancel()
        ipv6cpClient?.cancel()
        sstpClient?.cancel()
        pppClient?.cancel()
        incomingManager?.cancel()
        outgoingManager?.cancel()
    }

    private fun closeTerminals() {
        bridge.sslTerminal?.close()
        bridge.ipTerminal?.close()
    }
}
