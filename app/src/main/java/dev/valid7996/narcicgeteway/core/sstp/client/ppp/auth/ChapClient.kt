package dev.valid7996.narcicgeteway.core.sstp.client.ppp.auth

import dev.valid7996.narcicgeteway.core.sstp.SharedBridge
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.ChapChallenge
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.ChapFrame
import dev.valid7996.narcicgeteway.core.sstp.unit.ppp.auth.ChapMessageFrame
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


internal abstract class ChapClient(protected val bridge: SharedBridge) {
    internal val mailbox = Channel<ChapFrame>(Channel.BUFFERED)
    protected var challengeID: Byte = 0
    private var jobAuth: Job? = null

    internal fun launchJobAuth() {
        jobAuth = bridge.scope.launch(bridge.handler) {
            while (isActive) {
                when(val received = mailbox.receive()) {
                    is ChapChallenge -> {
                        challengeID = received.id

                        responseChallenge(received)
                    }

                    is ChapMessageFrame -> {
                        if (received.id == challengeID) {
                            processResult(received)
                        }
                    }
                }
            }
        }
    }

    protected abstract suspend fun responseChallenge(challenge: ChapChallenge)

    protected abstract suspend fun processResult(result: ChapMessageFrame)

    internal fun cancel() {
        jobAuth?.cancel()
        mailbox.close()
    }
}
