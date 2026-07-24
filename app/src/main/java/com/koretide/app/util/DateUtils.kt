package com.koretide.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtils {

    // SimpleDateFormat 은 스레드 안전하지 않아 호출마다 새로 만든다.
    private fun fmt() = SimpleDateFormat("yyyyMMdd", Locale.KOREA)

    /** "yyyyMMdd" 하루 전. 파싱 실패 시 입력 그대로 반환. */
    fun previousDay(yyyymmdd: String): String = try {
        val cal = Calendar.getInstance().apply {
            time = fmt().parse(yyyymmdd)!!
            add(Calendar.DAY_OF_YEAR, -1)
        }
        fmt().format(cal.time)
    } catch (e: Exception) {
        yyyymmdd
    }
}
