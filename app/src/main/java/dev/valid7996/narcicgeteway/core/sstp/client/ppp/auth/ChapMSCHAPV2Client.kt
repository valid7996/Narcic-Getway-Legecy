package dev.valid7996.narcicgeteway.core.sstp.client.ppp.auth

import dev.valid7996.narcicgeteway.core.sstp.ControlMessage
import dev.valid7996.narcicgeteway.core.sstp.Result
import dev.valid7996.narcicgeteway.core.sstp.SharedBridge
import dev.valid7996.narcicgeteway.core.sstp.Where
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.ChapChallenge
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.ChapFailure
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.ChapMessageFrame
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.ChapResponse
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.ChapSuccess


internal class ChapMSCHAPV2Client(bridge: SharedBridge) : ChapClient(bridge) {
    private val authClient = MSCHAPV2Client(bridge)

    override suspend fun responseChallenge(challenge: ChapChallenge) {
        ChapResponse().also {
            it.id = challengeID
            it.valueName = authClient.processChallenge(challenge.valueName)

            bridge.sslTerminal!!.send(it.toByteBuffer())
        }
    }

    override suspend fun processResult(result: ChapMessageFrame) {
        when (result) {
            is ChapSuccess -> {
                if (authClient.verifyAuthenticator(result.message)) {
                    authClient.prepareHlak()

                    bridge.controlMailbox.send(
                        ControlMessage(Where.CHAP, Result.PROCEEDED)
                    )
                } else {
                    bridge.controlMailbox.send(
                        ControlMessage(Where.MSCHAPV2, Result.ERR_VERIFICATION_FAILED)
                    )
                }
            }

            is ChapFailure -> {
                bridge.controlMailbox.send(
                    ControlMessage(Where.MSCHAPV2, Result.ERR_AUTHENTICATION_FAILED)
                )
            }
        }
    }
}
