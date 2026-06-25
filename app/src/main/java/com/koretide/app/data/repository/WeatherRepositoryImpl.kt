package com.koretide.app.data.repository

import android.util.Log
import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KhoaDataApiService
import com.koretide.app.data.remote.MockDataSource
import com.koretide.app.data.remote.dto.KhoaWaveItem
import com.koretide.app.data.remote.dto.KhoaWindItem
import com.koretide.app.domain.model.WindData
import com.koretide.app.domain.repository.WeatherRepository
import com.koretide.app.util.BeaufortConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val khoaDataApi: KhoaDataApiService
) : WeatherRepository {

    private val apiKey get() = BuildConfig.KHOA_API_KEY

    override suspend fun getWindData(lat: Double, lng: Double, stationCode: String): WindData {
        if (apiKey.isBlank()) return MockDataSource.mockWindData(stationCode)

        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())

        return try {
            // surveyWind는 조위코드(DT_xxxx)를 쓰지 않음 → null로 전체 조회 후 최근접 선택
            val windItems = khoaDataApi.getWind(apiKey, null, date).body?.items?.item.orEmpty()
            val item = windItems.nearestTo(lat, lng)
            Log.d(TAG, "surveyWind nearest=${item?.stationName} speed=${item?.windSpeed} dir=${item?.windDir}")

            val speedMs = item?.windSpeed ?: return MockDataSource.mockWindData(stationCode)
            val dirDeg  = item.windDir ?: 225f
            val bft     = BeaufortConverter.toBft(speedMs)

            val waveHeightM: Float? = runCatching {
                val waveItems = khoaDataApi.getWave(apiKey, null, date).body?.items?.item.orEmpty()
                val wave = waveItems.nearestWaveTo(lat, lng)
                wave?.waveHeight.also { Log.d(TAG, "noonWave nearest=${wave?.stationName} height=$it") }
            }.onFailure { Log.d(TAG, "noonWave skipped: ${it.message}") }
             .getOrNull()

            WindData(stationCode, speedMs, bft, dirDeg, BeaufortConverter.name(bft), waveHeightM)
        } catch (e: Exception) {
            Log.w(TAG, "getWindData [$stationCode] failed → mock", e)
            MockDataSource.mockWindData(stationCode)
        }
    }

    private fun List<KhoaWindItem>.nearestTo(lat: Double, lng: Double): KhoaWindItem? {
        val latest = groupBy { it.obsrvnDt }.maxByOrNull { it.key.orEmpty() }?.value ?: return firstOrNull()
        return latest.minByOrNull { abs((it.lat ?: 0.0) - lat) + abs((it.lon ?: 0.0) - lng) }
    }

    private fun List<KhoaWaveItem>.nearestWaveTo(lat: Double, lng: Double): KhoaWaveItem? {
        val latest = groupBy { it.obsrvnDt }.maxByOrNull { it.key.orEmpty() }?.value ?: return firstOrNull()
        return latest.minByOrNull { abs((it.lat ?: 0.0) - lat) + abs((it.lon ?: 0.0) - lng) }
    }

    companion object {
        private const val TAG = "WeatherRepo"
    }
}
