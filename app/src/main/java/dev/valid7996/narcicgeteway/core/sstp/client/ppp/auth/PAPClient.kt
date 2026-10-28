package dev.valid7996.narcicgeteway.core.sstp.client.ppp.auth

import dev.valid7996.narcicgeteway.core.sstp.ControlMessage
import dev.valid7996.narcicgeteway.core.sstp.Result
import dev.valid7996.narcicgeteway.core.sstp.SharedBridge
import dev.valid7996.narcicgeteway.core.sstp.Where
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.PAPAuthenticateRequest
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.PAPFrame
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.PAP_CODE_AUTHENTICATE_ACK
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.PAP_CODE_AUTHENTICATE_NAK
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


internal class PAPClient(private val bridge: SharedBridge) {
    internal val mailbox = Channel<PAPFrame>(Channel.BUFFERED)
    private var jobAuth: Job? = null

    internal fun launchJobAuth() {
        jobAuth = bridge.scope.launch(bridge.handler) {
            val currentID = bridge.allocateNewFrameID()

            sendPAPRequest(currentID)

            while (isActive) {
                val received = mailbox.receive()

                if (received.id != currentID) continue

                when (received.code) {
                    PAP_CODE_AUTHENTICATE_ACK -> {
                        bridge.controlMailbox.send(
                            ControlMessage(Where.PAP, Result.PROCEEDED)
                        )
                    }

                    PAP_CODE_AUTHENTICATE_NAK -> {
                        bridge.controlMailbox.send(
                            ControlMessage(Where.PAP, Result.ERR_AUTHENTICATION_FAILED)
                        )
                    }
                }
            }
        }
    }

    private suspend fun sendPAPRequest(id: Byte) {
        PAPAuthenticateRequest().also {
            it.id = id
            it.idFiled = bridge.HOME_USERNAME.toByteArray(Charsets.US_ASCII)
            it.passwordFiled = bridge.HOME_PASSWORD.toByteArray(Charsets.US_ASCII)

            bridge.sslTerminal!!.send(it.toByteBuffer())
        }
    }

    internal fun cancel() {
        jobAuth?.cancel()
        mailbox.close()
    }
}
