package com.koretide.app.data.repository

import android.util.Log
import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KhoaDataApiService
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
    private val khoaDataApi: KhoaDataApiService
) : WeatherRepository {

    private val apiKey get() = BuildConfig.KHOA_API_KEY

    override suspend fun getWindData(lat: Double, lng: Double, stationCode: String): WindData {
        if (apiKey.isBlank()) return MockDataSource.mockWindData(stationCode)

        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())

        return try {
            val resp = khoaDataApi.getWind(apiKey, stationCode, date)
            val item = resp.result?.data?.lastOrNull()
            Log.d(TAG, "surveyWind [$stationCode] speed=${item?.windSpeed} dir=${item?.windDir}")

            val speedMs = item?.windSpeed ?: return MockDataSource.mockWindData(stationCode)
            val dirDeg  = item.windDir ?: 225f
            val bft     = BeaufortConverter.toBft(speedMs)

            val waveHeightM: Float? = runCatching {
                val wr = khoaDataApi.getWave(apiKey, stationCode, date)
                wr.result?.data?.lastOrNull()?.waveHeight
                    .also { Log.d(TAG, "noonWave [$stationCode] height=$it") }
            }.onFailure { Log.d(TAG, "noonWave [$stationCode] skipped: ${it.message}") }
             .getOrNull()

            WindData(stationCode, speedMs, bft, dirDeg, BeaufortConverter.name(bft), waveHeightM)
        } catch (e: Exception) {
            Log.w(TAG, "surveyWind [$stationCode] failed → mock", e)
            MockDataSource.mockWindData(stationCode)
        }
    }

    companion object {
        private const val TAG = "WeatherRepo"
    }
}
