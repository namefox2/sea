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

    // install() 에서 보관해, Context 를 주입받기 어려운 리포지토리 등에서 log(message) 로 기록 가능.
    @Volatile private var appCtx: Context? = null

    fun install(context: Context) {
        val appContext = context.applicationContext
        appCtx = appContext
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

    /** Context 없이 기록 (install 이후 사용 가능). 리포지토리/데이터 계층 진단용. */
    fun log(message: String) {
        val ctx = appCtx ?: return
        log(ctx, message)
    }

    /** 임의 진단 메시지를 로그에 append (크래시 외 이벤트: 예) 지도 인증 실패). */
    fun log(context: Context, message: String) {
        try {
            val entry = "[${dateFormat.format(Date())}] $message\n"
            val file = File(context.applicationContext.filesDir, LOG_FILE)
            file.appendText(entry)
            if (file.length() > MAX_BYTES) {
                val trimmed = file.readText().takeLast(MAX_BYTES)
                file.writeText(trimmed)
            }
        } catch (_: Exception) { /* 진단 로그 실패는 무시 */ }
    }

    fun read(context: Context): String {
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        return if (file.exists()) file.readText().trim() else ""
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, LOG_FILE).delete()
    }
}
