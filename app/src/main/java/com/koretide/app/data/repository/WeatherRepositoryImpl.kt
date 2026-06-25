package com.koretide.app.data.repository

import android.util.Log
import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KhoaDataApiService
import com.koretide.app.data.remote.KmaApiService
import com.koretide.app.data.remote.MockDataSource
import com.koretide.app.domain.model.WindData
import com.koretide.app.domain.repository.WeatherRepository
import com.koretide.app.util.BeaufortConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val kmaApi: KmaApiService,
    private val khoaDataApi: KhoaDataApiService
) : WeatherRepository {

    private val apiKey get() = BuildConfig.KHOA_API_KEY

    override suspend fun getWindData(lat: Double, lng: Double, stationCode: String): WindData {
        if (apiKey.isBlank()) return MockDataSource.mockWindData(stationCode)

        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())

        // 1차: surveyWind (KHOA 관측) — 조위관측소에 풍속 센서가 있을 때 우선 사용
        val surveyResult = runCatching {
            val resp = khoaDataApi.getWind(apiKey, stationCode, date)
            val item = resp.result?.data?.lastOrNull()
            if (item?.windSpeed != null) {
                Log.d(TAG, "surveyWind [$stationCode] ok: ${item.windSpeed}m/s ${item.windDir}°")
                item
            } else {
                Log.d(TAG, "surveyWind [$stationCode] empty data")
                null
            }
        }.onFailure { Log.d(TAG, "surveyWind [$stationCode] failed: ${it.message}") }
         .getOrNull()

        val speedMs: Float
        val dirDeg: Float

        if (surveyResult != null) {
            speedMs = surveyResult.windSpeed!!
            dirDeg  = surveyResult.windDir ?: 225f
        } else {
            // 2차: KMA 단기예보 (위경도 그리드) — 모든 위치에서 동작
            val kmaResult = runCatching {
                val kmaDate = date
                val time = "0600"
                val nx = ((lng - 124.0) * 4).toInt() + 1
                val ny = ((lat - 33.0) * 4).toInt() + 1
                val resp = kmaApi.getVillageForecast(apiKey, baseDate = kmaDate, baseTime = time, nx = nx, ny = ny)
                val items = resp.response?.body?.items?.item ?: emptyList()
                Log.d(TAG, "KMA [$stationCode] items=${items.size}")
                val ws = items.firstOrNull { it.category == "WSD" }?.fcstValue?.toFloatOrNull()
                val wd = items.firstOrNull { it.category == "VEC" }?.fcstValue?.toFloatOrNull()
                Pair(ws, wd)
            }.onFailure { Log.w(TAG, "KMA [$stationCode] failed → mock", it) }
             .getOrNull()

            speedMs = kmaResult?.first  ?: return MockDataSource.mockWindData(stationCode)
            dirDeg  = kmaResult.second ?: 225f
        }

        val bft = BeaufortConverter.toBft(speedMs)

        // 파랑 보정: noonWave — 실패해도 무시
        val waveHeightM: Float? = runCatching {
            val wr = khoaDataApi.getWave(apiKey, stationCode, date)
            wr.result?.data?.lastOrNull()?.waveHeight
                .also { Log.d(TAG, "noonWave [$stationCode] height=$it") }
        }.onFailure { Log.d(TAG, "noonWave [$stationCode] skipped: ${it.message}") }
         .getOrNull()

        return WindData(stationCode, speedMs, bft, dirDeg, BeaufortConverter.name(bft), waveHeightM)
    }

    companion object {
        private const val TAG = "WeatherRepo"
    }
}
