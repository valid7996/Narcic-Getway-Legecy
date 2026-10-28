package dev.valid7996.narcicgeteway.core.sstp.client.ppp

import dev.valid7996.narcicgeteway.core.sstp.ControlMessage
import dev.valid7996.narcicgeteway.core.sstp.Result
import dev.valid7996.narcicgeteway.core.sstp.SharedBridge
import dev.valid7996.narcicgeteway.core.sstp.Where
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.Frame
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.LCPCodeReject
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.LCPEchoReply
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.LCPEchoRequest
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.LCPProtocolReject
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.LCPTerminalAck
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.LCPTerminalRequest
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.LcpDiscardRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


internal class PPPClient(val bridge: SharedBridge) {
    internal val mailbox = Channel<Frame>(Channel.BUFFERED)

    private var jobControl: Job? = null

    internal fun launchJobControl() {
        jobControl = bridge.scope.launch(bridge.handler) {
            while (isActive) {
                when (val received = mailbox.receive()) {
                    is LCPEchoRequest -> {
                        LCPEchoReply().also {
                            it.id = received.id
                            it.holder = "Abura Mashi Mashi".toByteArray(Charsets.US_ASCII)
                            bridge.sslTerminal!!.send(it.toByteBuffer())
                        }
                    }

                    is LCPEchoReply -> { }

                    is LcpDiscardRequest -> { }

                    is LCPTerminalRequest -> {
                        LCPTerminalAck().also {
                            it.id = received.id
                            bridge.sslTerminal!!.send(it.toByteBuffer())
                        }

                        bridge.controlMailbox.send(
                            ControlMessage(Where.PPP, Result.ERR_TERMINATE_REQUESTED)
                        )
                    }

                    is LCPProtocolReject -> {
                        bridge.controlMailbox.send(
                            ControlMessage(Where.PPP, Result.ERR_PROTOCOL_REJECTED)
                        )
                    }

                    is LCPCodeReject -> {
                        bridge.controlMailbox.send(
                            ControlMessage(Where.PPP, Result.ERR_CODE_REJECTED)
                        )
                    }

                    else -> {
                        bridge.controlMailbox.send(
                            ControlMessage(Where.PPP, Result.ERR_UNEXPECTED_MESSAGE)
                        )
                    }
                }
            }
        }
    }

    internal fun cancel() {
        jobControl?.cancel()
        mailbox.close()
    }
}
