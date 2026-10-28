package dev.valid7996.narcicgeteway.core.sstp

import android.net.VpnService
import dev.valid7996.narcicgeteway.core.LogBus
import dev.valid7996.narcicgeteway.core.sstp.control.Controller
import dev.valid7996.narcicgeteway.domain.config.SstpProfile
import java.util.concurrent.atomic.AtomicLong

class SstpController(
    private val service: VpnService,
    private val profile: SstpProfile,
    private val configureBuilder: (VpnService.Builder) -> Unit,
    private val fallbackDns: List<String> = emptyList(),
    private val onUp: () -> Unit = {},
    private val onDown: (String?) -> Unit = {},
) {
    private var bridge: SharedBridge? = null
    private var controller: Controller? = null

    private val lastRx = AtomicLong(0)
    private val lastTx = AtomicLong(0)

    @Volatile
    var isRunning = false
        private set

    fun traffic(): Pair<Long, Long> {
        val b = bridge ?: return 0L to 0L
        return b.rxBytes.get() to b.txBytes.get()
    }

    fun trafficDelta(): Pair<Long, Long> {
        val b = bridge ?: return 0L to 0L
        val rx = b.rxBytes.get()
        val tx = b.txBytes.get()
        val dRx = (rx - lastRx.getAndSet(rx)).coerceAtLeast(0)
        val dTx = (tx - lastTx.getAndSet(tx)).coerceAtLeast(0)
        return dRx to dTx
    }

    fun start(): Boolean {
        if (!profile.isValid) {
            val err = "Invalid SSTP profile: server and username are required"
            LogBus.append("E/SSTP $err")
            onDown(err)
            return false
        }

        LogBus.append("I/SSTP Starting MS-SSTP session to ${profile.serverHost()}:${profile.effectivePort()} (user: ${profile.username})...")
        val b = SharedBridge(
            service = service,
            profile = profile,
            configureBuilder = configureBuilder,
            fallbackDns = fallbackDns,
            onUp = {
                isRunning = true
                LogBus.append("I/SSTP Session UP - Connected to ${profile.serverHost()}")
                onUp()
            },
            onDown = { reason ->
                isRunning = false
                if (reason != null) LogBus.append("E/SSTP Session DOWN: $reason")
                else LogBus.append("I/SSTP Session DOWN")
                onDown(reason)
            },
        )
        bridge = b
        val c = Controller(b)
        controller = c
        c.launchJobMain()
        return true
    }

    fun stop() {
        LogBus.append("I/SSTP Stopping SSTP session...")
        isRunning = false
        runCatching { controller?.disconnect() }
        bridge = null
        controller = null
    }
}
