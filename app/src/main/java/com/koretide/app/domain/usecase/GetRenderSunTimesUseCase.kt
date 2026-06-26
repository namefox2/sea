package com.koretide.app.domain.usecase

import android.util.Log
import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KasiApiService
import com.koretide.app.domain.model.SunTimes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetRenderSunTimesUseCase @Inject constructor(
    private val kasiApi: KasiApiService
) {
    private val key get() = BuildConfig.KASI_API_KEY

    suspend operator fun invoke(lat: Double, lon: Double): SunTimes {
        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        return try {
            val item = kasiApi.getRiseSetByCoords(
                serviceKey = key,
                locdate    = date,
                longitude  = "%.4f".format(lon),
                latitude   = "%.4f".format(lat)
            ).body?.items?.item?.firstOrNull()

            val rise = parseHhmm(item?.sunrise)  ?: 6.0f
            val set  = parseHhmm(item?.sunset)   ?: 18.0f
            val mr   = parseHhmm(item?.moonrise) ?: -1f
            var ms   = parseHhmm(item?.moonset)  ?: -1f
            // 자정을 넘는 월몰(예: 01:29)은 +24h 보정해 arc 계산이 올바르게 작동하도록
            if (ms > 0f && mr > 0f && ms < mr) ms += 24f

            Log.d(TAG, "SunTimes lat=%.4f lon=%.4f → rise=%.2f set=%.2f moonrise=%.2f moonset=%.2f"
                .format(lat, lon, rise, set, mr, ms))
            SunTimes(rise, set, mr, ms)
        } catch (e: Exception) {
            Log.w(TAG, "KASI API 실패: ${e.message}")
            SunTimes(6.0f, 18.0f, -1f, -1f)
        }
    }

    // "HHMM" 문자열 → float 시간 (예: "0512" → 5.2f, "1957" → 19.95f)
    private fun parseHhmm(raw: String?): Float? {
        val s = raw?.trim()?.takeIf { it.length >= 4 } ?: return null
        val hh = s.substring(0, 2).toIntOrNull() ?: return null
        val mm = s.substring(2, 4).toIntOrNull() ?: return null
        if (hh > 23 || mm > 59) return null
        return hh + mm / 60f
    }

    companion object { private const val TAG = "SunTimesUseCase" }
}
