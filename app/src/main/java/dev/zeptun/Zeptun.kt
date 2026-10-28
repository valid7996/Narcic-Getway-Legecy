package dev.zeptun

import android.os.Build

object Zeptun {
    val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    @Volatile
    private var loaded = false

    fun load(): Boolean {
        if (!isSupported) return false
        if (loaded) return true
        return synchronized(this) {
            if (loaded) return true
            try {
                runCatching { System.loadLibrary("zeptun") }
                System.loadLibrary("zeptun-jni")
                loaded = true
                true
            } catch (t: Throwable) {
                false
            }
        }
    }

    external fun nativeStart(service: Any?, fd: Int, config: String?): Int

    external fun nativeStop()

    external fun nativeVersion(): String

    external fun nativeCounter(index: Int): Long
}
