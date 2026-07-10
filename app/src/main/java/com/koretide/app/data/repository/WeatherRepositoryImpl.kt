package com.koretide.app.data.repository

import android.util.Log
import com.koretide.app.BuildConfig
import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.remote.KhoaDataApiService
import com.koretide.app.data.remote.dto.KhoaWaveItem
import com.koretide.app.domain.model.WindData
import com.koretide.app.domain.repository.WeatherRepository
import com.koretide.app.util.BeaufortConverter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val stationDao: StationDao,
    private val khoaDataApi: KhoaDataApiService
) : WeatherRepository {

    private val apiKey get() = BuildConfig.KHOA_API_KEY

    override suspend fun getWindData(lat: Double, lng: Double, stationCode: String): WindData {
        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        Log.d(TAG, "▶ getWindData station=$stationCode lat=$lat lng=$lng date=$date")

        // 가장 가까운 조위관측소 찾기 (DB 기준)
        val nearest = stationDao.getAllStationsSnapshot()
            .minByOrNull { (it.lat - lat) * (it.lat - lat) + (it.lng - lng) * (it.lng - lng) }
        val obsCode = nearest?.code ?: stationCode
        Log.d(TAG, "  nearest 조위관측소: ${nearest?.name}($obsCode)  거리=${nearest?.let { "%.3f°".format(Math.sqrt((it.lat - lat) * (it.lat - lat) + (it.lng - lng) * (it.lng - lng))) } ?: "-"}")

        return try {
            // 바람(dtRecent)과 파고(noonWave)를 병렬 호출해 진입 지연 단축
            coroutineScope {
                val windDeferred = async {
                    khoaDataApi.getTideRecent(
                        serviceKey = apiKey,
                        obsCode    = obsCode,
                        date       = date,
                        numOfRows  = 1
                    ).body?.items?.item.orEmpty()
                }
                val waveDeferred = async {
                    runCatching {
                        val waveItems = khoaDataApi.getWave(apiKey, null, date).body?.items?.item.orEmpty()
                        val wave = waveItems.nearestWaveTo(lat, lng)
                        Log.d(TAG, "  noonWave nearest=${wave?.stationName}  wh=${wave?.waveHeight}m")
                        wave?.waveHeight
                    }.onFailure { Log.w(TAG, "  noonWave failed: ${it.message}") }
                     .getOrNull()
                }

                val item = windDeferred.await().lastOrNull()
                Log.d(TAG, "  dtRecent($obsCode) → wspd=${item?.windSpeed}m/s  wndrct=${item?.windDir}°  wtem=${item?.waterTemp}℃  artmp=${item?.airTemp}℃")

                val speedMs = item?.windSpeed ?: 0f
                val dirDeg  = item?.windDir ?: 0f
                val bft     = BeaufortConverter.toBft(speedMs)
                val waveHeightM = waveDeferred.await()
                Log.d(TAG, "  → ${bft}bft  ${BeaufortConverter.name(bft)}")

                WindData(stationCode, speedMs, bft, dirDeg, BeaufortConverter.name(bft), waveHeightM)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getWindData [$stationCode] failed: ${e.message}", e)
            WindData(stationCode, 0f, 0, 0f, "--", null)
        }
    }

    private fun List<KhoaWaveItem>.nearestWaveTo(lat: Double, lng: Double): KhoaWaveItem? {
        val latest = groupBy { it.obsrvnDt }.maxByOrNull { it.key.orEmpty() }?.value ?: return firstOrNull()
        return latest.minByOrNull { abs((it.lat ?: 0.0) - lat) + abs((it.lon ?: 0.0) - lng) }
    }

    companion object {
        private const val TAG = "WeatherRepo"
    }
}
