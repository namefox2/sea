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

    companion object {
        private const val TAG = "WeatherRepo"
    }

    override suspend fun getWindData(lat: Double, lng: Double, stationCode: String): WindData {
        if (apiKey.isBlank()) {
            Log.d(TAG, "getWindData: API key blank → mock")
            return MockDataSource.mockWindData(stationCode)
        }

        return try {
            val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())

            val windResponse = khoaDataApi.getWind(apiKey, stationCode, date)
            val dataList = windResponse.result?.data
            Log.d(TAG, "surveyWind [$stationCode] result=${windResponse.result != null} dataSize=${dataList?.size} firstItem=${ dataList?.firstOrNull() }")
            val windItem = dataList?.lastOrNull()

            val speedMs  = windItem?.windSpeed ?: 5.5f
            val dirDeg   = windItem?.windDir   ?: 225f
            val bft      = BeaufortConverter.toBft(speedMs)

            // fetch observed wave height for windAmp correction; null on failure
            val waveHeightM: Float? = try {
                val waveResponse = khoaDataApi.getWave(apiKey, stationCode, date)
                val waveItem = waveResponse.result?.data?.lastOrNull()
                Log.d(TAG, "noonWave [$stationCode] waveItem=$waveItem")
                waveItem?.waveHeight
            } catch (e: Exception) {
                Log.w(TAG, "noonWave [$stationCode] failed", e)
                null
            }

            WindData(stationCode, speedMs, bft, dirDeg, BeaufortConverter.name(bft), waveHeightM)
        } catch (e: Exception) {
            Log.w(TAG, "getWindData [$stationCode] exception → mock", e)
            MockDataSource.mockWindData(stationCode)
        }
    }
}
