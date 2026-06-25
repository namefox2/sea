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

    private val apiKey get() = BuildConfig.KMA_API_KEY

    override suspend fun getWindData(lat: Double, lng: Double, stationCode: String): WindData {
        if (apiKey.isBlank()) return MockDataSource.mockWindData(stationCode)

        return try {
            val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
            val time = "0600"
            // KMA 위경도 → 격자 변환
            val nx = ((lng - 124.0) * 4).toInt() + 1
            val ny = ((lat - 33.0) * 4).toInt() + 1
            val response = kmaApi.getVillageForecast(apiKey, baseDate = date, baseTime = time, nx = nx, ny = ny)
            val items = response.response?.body?.items?.item ?: emptyList()
            val wsdItem = items.firstOrNull { it.category == "WSD" }
            val vecItem = items.firstOrNull { it.category == "VEC" }
            val speedMs = wsdItem?.fcstValue?.toFloatOrNull() ?: 5.5f
            val dirDeg  = vecItem?.fcstValue?.toFloatOrNull()  ?: 225f
            val bft     = BeaufortConverter.toBft(speedMs)

            // noonWave: 실측파랑으로 windAmp 보정 — 조위관측소 코드와 다를 수 있어 실패 시 무시
            val waveHeightM: Float? = try {
                val waveResponse = khoaDataApi.getWave(BuildConfig.KHOA_API_KEY, stationCode, date)
                waveResponse.result?.data?.lastOrNull()?.waveHeight
            } catch (e: Exception) {
                Log.d(TAG, "noonWave [$stationCode] skipped: ${e.message}")
                null
            }

            WindData(stationCode, speedMs, bft, dirDeg, BeaufortConverter.name(bft), waveHeightM)
        } catch (e: Exception) {
            Log.w(TAG, "getWindData [$stationCode] KMA failed → mock", e)
            MockDataSource.mockWindData(stationCode)
        }
    }

    companion object {
        private const val TAG = "WeatherRepo"
    }
}
