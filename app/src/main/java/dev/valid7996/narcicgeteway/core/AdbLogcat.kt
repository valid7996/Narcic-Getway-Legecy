package dev.valid7996.narcicgeteway.core

import android.content.Context
import android.os.Build
import dev.valid7996.narcicgeteway.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AdbLogcat {
    fun dump(maxLines: Int = 1500): String = runCatching {
        val lines = ArrayList<String>(maxLines)
        val proc = ProcessBuilder("logcat", "-d", "-v", "time", "-t", maxLines.toString())
            .redirectErrorStream(true)
            .start()
        proc.inputStream.bufferedReader().useLines { seq ->
            seq.forEach { lines.add(it) }
        }
        proc.waitFor()
        lines.joinToString("\n")
    }.getOrElse { "Failed to capture ADB logcat: ${it.message}" }

    fun buildDiagnosticReport(context: Context): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val crashReport = AppCrashHandler.getLastCrashReport(context)
        val coreCrash = readCoreCrash(context)
        val adbLog = dump(2000)

        return buildString {
            appendLine("==========================================")
            appendLine("Narcic Gateway Legacy - ADB & System Log")
            appendLine("==========================================")
            appendLine("Generated At: $time")
            appendLine("App Version: Narcic Gateway Legacy ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${context.packageName}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Active VPN Kind: ${VpnManager.activeKind.value ?: "Disconnected"}")
            appendLine("==========================================")
            if (!crashReport.isNullOrBlank()) {
                appendLine()
                appendLine("!!! PREVIOUS CRASH REPORT DETECTED !!!")
                appendLine(crashReport)
                appendLine("==========================================")
            }
            if (!coreCrash.isNullOrBlank()) {
                appendLine()
                appendLine("!!! GO CORE CRASH LOG DETECTED !!!")
                appendLine(coreCrash)
                appendLine("==========================================")
            }
            appendLine()
            appendLine("=== ADB LOGCAT BUFFER ===")
            appendLine(adbLog)
        }
    }

    private fun readCoreCrash(context: Context): String? {
        val dir = context.filesDir
        val candidates = listOf(File(dir, "core-crash-last.txt"), File(dir, "core-crash.txt"))
        for (f in candidates) {
            if (f.isFile && f.length() > 0) {
                val text = runCatching { f.readText() }.getOrNull()
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    fun clear(context: Context) {
        AppCrashHandler.clearCrashReport(context)
        runCatching {
            val dir = context.filesDir
            File(dir, "core-crash-last.txt").delete()
            File(dir, "core-crash.txt").delete()
        }
        runCatching {
            ProcessBuilder("logcat", "-c").start().waitFor()
        }
    }
}
