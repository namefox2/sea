package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.local.dao.TideRecordDao
import com.koretide.app.data.local.entity.TideRecordEntity
import com.koretide.app.data.remote.KhoaDataApiService
import com.koretide.app.domain.model.RecentTideLevel
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.TideRecord
import com.koretide.app.domain.model.TideStatus
import com.koretide.app.domain.repository.TideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TideRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stationDao: StationDao,
    private val tideRecordDao: TideRecordDao,
    private val khoaDataApi: KhoaDataApiService
) : TideRepository {

    private val apiKey get() = BuildConfig.KHOA_API_KEY

    // 30-min cache for batch recent levels. In-memory (singleton lives for app
    // lifetime) + persisted to disk so cold starts within the TTL skip the
    // ~60 parallel API calls entirely.
    private val batchMutex = Mutex()
    @Volatile private var batchCache: List<RecentTideLevel> = emptyList()
    @Volatile private var batchCacheTime: Long = 0L
    @Volatile private var diskLoaded = false
    private val BATCH_CACHE_TTL = 30 * 60 * 1000L

    private val batchPrefs by lazy { context.getSharedPreferences("tide_batch_cache", Context.MODE_PRIVATE) }

    // 디스크 캐시를 메모리로 1회 적재 (process 재시작 후에도 캐시 재사용)
    private fun ensureMemoryFromDisk() {
        if (diskLoaded) return
        if (batchCache.isEmpty()) {
            val encoded = batchPrefs.getString(KEY_BATCH_DATA, null)
            val time    = batchPrefs.getLong(KEY_BATCH_TIME, 0L)
            if (!encoded.isNullOrBlank() && time > 0L) {
                val list = encoded.split('\n').mapNotNull { line ->
                    val p = line.split(',')
                    if (p.size < 4) return@mapNotNull null
                    val lat = p[0].toDoubleOrNull() ?: return@mapNotNull null
                    val lon = p[1].toDoubleOrNull() ?: return@mapNotNull null
                    RecentTideLevel(lat, lon, p[2].toIntOrNull(), p[3].toFloatOrNull())
                }
                if (list.isNotEmpty()) { batchCache = list; batchCacheTime = time }
            }
        }
        diskLoaded = true
    }

    private fun persistBatch(levels: List<RecentTideLevel>, time: Long) {
        val encoded = levels.joinToString("\n") { l ->
            "${l.lat},${l.lon},${l.levelCm ?: ""},${l.windSpeedMs ?: ""}"
        }
        batchPrefs.edit().putString(KEY_BATCH_DATA, encoded).putLong(KEY_BATCH_TIME, time).apply()
    }

    // 캐시(메모리/디스크)만 즉시 반환 — 신선도 무관, 네트워크 호출 없음.
    // 첫 화면에서 캐시를 바로 보여주고, 백그라운드 새로고침은 별도로 수행.
    override suspend fun getCachedBatchLevels(): List<RecentTideLevel> = withContext(Dispatchers.IO) {
        ensureMemoryFromDisk()
        batchCache
    }

    override suspend fun getTideData(stationCode: String): TideData {
        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        Log.d(TAG, "▶ getTideData station=$stationCode date=$date")

        val current = khoaDataApi.getTideRecent(apiKey, stationCode, date)
        val table   = khoaDataApi.getTideForecast(apiKey, stationCode, date)

        val dataItems  = current.body?.items?.item ?: emptyList()
        val tableItems = table.body?.items?.item   ?: emptyList()
        Log.d(TAG, "  dtRecent items=${dataItems.size}  tideFcst items=${tableItems.size}")

        val currentLevel = dataItems.lastOrNull()?.tideLevel?.toInt() ?: 300
        Log.d(TAG, "  lastItem obsrvnDt=${dataItems.lastOrNull()?.obsrvnDt}  tideLevel=${dataItems.lastOrNull()?.tideLevel}cm")

        // 하루 조석예보(고/저조)에서 고조(extrSe 1,3)·저조(extrSe 2,4)를 모아 일조차 계산.
        // 예보가 없으면 허구값(600/50, =조차 5.5)을 실제처럼 보이면 안 되므로 현재 수위로 폴백.
        val allHigh  = tableItems.filter { it.extrSe == 1 || it.extrSe == 3 }
        val allLow   = tableItems.filter { it.extrSe == 2 || it.extrSe == 4 }
        val maxLevel = allHigh.mapNotNull { it.predcTdlvVl?.toInt() }.maxOrNull() ?: currentLevel
        val minLevel = allLow.mapNotNull  { it.predcTdlvVl?.toInt() }.minOrNull() ?: currentLevel
        val range    = (maxLevel - minLevel).coerceAtLeast(0).toFloat()
        Log.d(TAG, "  조석예보 items=${tableItems.size}  고조=${allHigh.size} 저조=${allLow.size}  조차=${range}cm")
        val tidePercent = if (range > 0f)
            ((currentLevel - minLevel).toFloat() / range).coerceIn(0f, 1f)
        else 0.5f
        Log.d(TAG, "  maxLevel=${maxLevel}cm  minLevel=${minLevel}cm  currentLevel=${currentLevel}cm  tidePercent=${"%.2f".format(tidePercent)}")

        val prevLevel = if (dataItems.size >= 2)
            dataItems[dataItems.size - 2].tideLevel?.toInt() ?: currentLevel
        else currentLevel
        val tideStatus = when {
            tidePercent > 0.92f       -> TideStatus.HIGH_TIDE
            tidePercent < 0.08f       -> TideStatus.LOW_TIDE
            currentLevel >= prevLevel -> TideStatus.RISING
            else                      -> TideStatus.FALLING
        }

        // predcDt "yyyy-MM-dd HH:mm" → "HH:mm"
        fun com.koretide.app.data.remote.dto.KhoaTideFcstItem.hm(): String =
            predcDt?.substringAfter(' ', "")?.take(5).orEmpty()
        val nowTime  = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
        val highItem = allHigh.firstOrNull { it.hm() >= nowTime } ?: allHigh.lastOrNull()
        val lowItem  = allLow.firstOrNull  { it.hm() >= nowTime } ?: allLow.lastOrNull()
        Log.d(TAG, "  tideStatus=${tideStatus.displayName}  nextHigh=${highItem?.hm()}(${highItem?.predcTdlvVl}cm)  nextLow=${lowItem?.hm()}(${lowItem?.predcTdlvVl}cm)")

        val records = dataItems.map { item ->
            val ts = parseDateToMillis(item.obsrvnDt ?: date)
            TideRecordEntity(stationCode = stationCode, timestamp = ts, waterLevel = item.tideLevel?.toInt() ?: 0)
        }

        tideRecordDao.insertAll(records)
        tideRecordDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 3_600_000)

        return TideData(
            stationCode  = stationCode,
            currentLevel = currentLevel,
            maxLevel     = maxLevel,
            minLevel     = minLevel,
            tidePercent  = tidePercent,
            tideStatus   = tideStatus,
            highTideTime = highItem?.hm(),
            lowTideTime  = lowItem?.hm(),
            records      = records.map { it.toDomain() }
        )
    }

    override suspend fun getTideHistory(stationCode: String, date: String): List<TideRecord> {
        val since = System.currentTimeMillis() - 24 * 3_600_000L
        val cached = tideRecordDao.getRecords(stationCode, since)
        Log.d(TAG, "▶ getTideHistory station=$stationCode  cached=${cached.size}")
        if (cached.isNotEmpty()) return cached.map { it.toDomain() }
        val data = getTideData(stationCode)
        return data.records
    }

    // 각 관측소 코드로 dtRecent 개별 병렬 호출 (obsCode=null 벌크 호출은 API가 지원하지 않음)
    // WeatherRepo.getWindData()와 동일한 방식 — numOfRows=1로 최신 1건만 요청
    override suspend fun getBatchRecentLevels(): List<RecentTideLevel> {
        withContext(Dispatchers.IO) { ensureMemoryFromDisk() }
        // Return cache if still fresh
        val now = System.currentTimeMillis()
        if (batchCache.isNotEmpty() && now - batchCacheTime < BATCH_CACHE_TTL) {
            Log.d(TAG, "▶ getBatchRecentLevels 캐시 사용 (${(now - batchCacheTime) / 1000}초 경과)")
            return batchCache
        }

        return batchMutex.withLock {
            val now2 = System.currentTimeMillis()
            if (batchCache.isNotEmpty() && now2 - batchCacheTime < BATCH_CACHE_TTL) {
                return@withLock batchCache
            }

            val stations = stationDao.getAllStationsSnapshot()
            if (stations.isEmpty()) return@withLock emptyList()

            val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
            Log.d(TAG, "▶ getBatchRecentLevels 병렬 호출 stations=${stations.size}  date=$date")
            val t0 = System.currentTimeMillis()

            val result = coroutineScope {
                stations.map { station ->
                    async {
                        try {
                            val item = khoaDataApi.getTideRecent(
                                serviceKey = apiKey,
                                obsCode    = station.code,
                                date       = date,
                                numOfRows  = 1
                            ).body?.items?.item?.lastOrNull()
                            if (item != null && (item.tideLevel != null || item.windSpeed != null)) {
                                RecentTideLevel(station.lat, station.lng, item.tideLevel?.toInt(), item.windSpeed)
                            } else null
                        } catch (e: Exception) {
                            Log.w(TAG, "dtRecent[${station.code}] 실패: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            Log.d(TAG, "  완료 ${System.currentTimeMillis() - t0}ms  valid=${result.size}/${stations.size}")

            if (result.isNotEmpty()) {
                batchCache = result
                batchCacheTime = System.currentTimeMillis()
                persistBatch(result, batchCacheTime)
            }
            // 갱신 실패(전부 실패로 result 비었을 때)엔 오래된 캐시라도 유지해 반환
            result.ifEmpty { batchCache }
        }
    }

    private fun parseDateToMillis(dateStr: String): Long {
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyyMMdd HHmm")
        for (fmt in formats) {
            runCatching { SimpleDateFormat(fmt, Locale.KOREA).parse(dateStr)?.time }
                .getOrNull()?.let { return it }
        }
        return System.currentTimeMillis()
    }

    companion object {
        private const val TAG = "TideRepo"
        private const val KEY_BATCH_DATA = "batch_data"
        private const val KEY_BATCH_TIME = "batch_time"
    }
}
