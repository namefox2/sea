package com.koretide.app.data.repository

import android.util.Log
import com.koretide.app.BuildConfig
import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.remote.DtRecentSource
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
    private val khoaDataApi: KhoaDataApiService,
    private val dtRecentSource: DtRecentSource
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
                // dtRecent는 공유 캐시로 조회 → 조위 조회와 같은 관측소면 호출이 합쳐짐
                val windDeferred = async { dtRecentSource.items(obsCode, date) }
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
        val latest = groupBy { it.obsrvnDt }.maxByOrNull { it.key.orEmpty() }?.value ?: this
        val nearest = latest.minByOrNull {
            abs((it.lat ?: 0.0) - lat) + abs((it.lon ?: 0.0) - lng)
        } ?: return null
        // 파고 부이는 드문드문 분포하므로, 관측소에서 너무 먼 부이의 파고를 잘못 가져오지
        // 않도록 거리 상한(약 100km)을 둔다. 초과하면 파고 없음으로 처리.
        val dist = abs((nearest.lat ?: 0.0) - lat) + abs((nearest.lon ?: 0.0) - lng)
        return if (dist <= MAX_WAVE_MATCH_DEG) nearest else null
    }

    companion object {
        private const val TAG = "WeatherRepo"
        // 위경도 절대차 합(도). 약 1도 ≈ 90~110km. 이보다 먼 파고 부이는 매칭하지 않음.
        private const val MAX_WAVE_MATCH_DEG = 1.0
    }
}
