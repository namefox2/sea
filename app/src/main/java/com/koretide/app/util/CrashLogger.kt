package com.koretide.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    private const val LOG_FILE = "crash_log.txt"
    private const val MAX_BYTES = 64 * 1024  // 64 KB

    // Allocated once, not on every crash
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                save(appContext, thread, throwable)
            } catch (_: Exception) { /* never suppress the original crash */ }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun save(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val entry = buildString {
            append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            append("[${dateFormat.format(Date())}]\n")
            append("Thread : ${thread.name}\n")
            append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("Device : ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append(sw.toString())
            append("\n")
        }
        val file = File(context.filesDir, LOG_FILE)
        // Append-only: no full read on the normal path
        file.appendText(entry)
        // Trim only when the file has grown past the limit (rare)
        if (file.length() > MAX_BYTES) {
            val trimmed = file.readText().takeLast(MAX_BYTES)
            file.writeText(trimmed)
        }
    }

    fun read(context: Context): String {
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        return if (file.exists()) file.readText().trim() else ""
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, LOG_FILE).delete()
    }
}
