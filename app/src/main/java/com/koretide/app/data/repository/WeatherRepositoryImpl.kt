package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
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
    private val kmaApi: KmaApiService
) : WeatherRepository {

    override suspend fun getWindData(lat: Double, lng: Double, stationCode: String): WindData {
        if (BuildConfig.KMA_API_KEY.isBlank()) return MockDataSource.mockWindData(stationCode)

        return try {
            val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
            val time = "0600"
            val nx = ((lng - 124.0) * 4).toInt() + 1
            val ny = ((lat - 33.0) * 4).toInt() + 1
            val response = kmaApi.getVillageForecast(BuildConfig.KMA_API_KEY, baseDate = date, baseTime = time, nx = nx, ny = ny)
            val items = response.response?.body?.items?.item ?: emptyList()
            val wsdItem = items.firstOrNull { it.category == "WSD" }
            val vecItem = items.firstOrNull { it.category == "VEC" }
            val speedMs = wsdItem?.fcstValue?.toFloatOrNull() ?: 5.5f
            val dirDeg = vecItem?.fcstValue?.toFloatOrNull() ?: 225f
            val bft = BeaufortConverter.toBft(speedMs)
            WindData(stationCode, speedMs, bft, dirDeg, BeaufortConverter.name(bft))
        } catch (e: Exception) {
            MockDataSource.mockWindData(stationCode)
        }
    }
}
