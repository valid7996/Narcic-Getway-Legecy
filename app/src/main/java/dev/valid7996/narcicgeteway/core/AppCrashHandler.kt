package dev.valid7996.narcicgeteway.core

import android.content.Context
import android.os.Build
import dev.valid7996.narcicgeteway.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppCrashHandler : Thread.UncaughtExceptionHandler {
    private const val CRASH_FILE = "last_crash.txt"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current != this) {
            defaultHandler = current
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            saveCrash(thread, throwable)
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrash(thread: Thread, throwable: Throwable) {
        val context = appContext ?: return
        val file = File(context.filesDir, CRASH_FILE)
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val logcatSnippet = runCatching {
            val proc = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "300").start()
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.toList().joinToString("\n")
            }
        }.getOrDefault("")

        val report = buildString {
            appendLine("=== CRASH REPORT ===")
            appendLine("Time: $time")
            appendLine("App: Narcic Gateway Legacy ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Thread: ${thread.name} (id: ${thread.id})")
            appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine()
            appendLine("=== STACK TRACE ===")
            appendLine(stackTrace)
            if (logcatSnippet.isNotBlank()) {
                appendLine()
                appendLine("=== RECENT LOGCAT AT CRASH ===")
                appendLine(logcatSnippet)
            }
        }
        runCatching { file.writeText(report) }
    }

    fun getLastCrashReport(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE)
        return if (file.exists() && file.length() > 0) {
            runCatching { file.readText() }.getOrNull()
        } else null
    }

    fun clearCrashReport(context: Context) {
        runCatching {
            val file = File(context.filesDir, CRASH_FILE)
            if (file.exists()) file.delete()
        }
    }
}
