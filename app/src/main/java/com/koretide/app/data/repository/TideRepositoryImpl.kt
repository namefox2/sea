package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import android.util.Log
import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.local.dao.TideRecordDao
import com.koretide.app.data.local.entity.TideRecordEntity
import com.koretide.app.data.remote.KhoaDataApiService
import com.koretide.app.domain.model.RecentTideLevel
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.TideRecord
import com.koretide.app.domain.model.TideStatus
import com.koretide.app.domain.repository.TideRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TideRepositoryImpl @Inject constructor(
    private val stationDao: StationDao,
    private val tideRecordDao: TideRecordDao,
    private val khoaDataApi: KhoaDataApiService
) : TideRepository {

    private val apiKey get() = BuildConfig.KHOA_API_KEY

    // 30-min in-memory cache for batch recent levels (singleton lives for app lifetime)
    private val batchMutex = Mutex()
    @Volatile private var batchCache: List<RecentTideLevel> = emptyList()
    @Volatile private var batchCacheTime: Long = 0L
    private val BATCH_CACHE_TTL = 30 * 60 * 1000L

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

        val allHigh  = tableItems.filter { it.hlCode == "HH" }
        val allLow   = tableItems.filter { it.hlCode == "LL" }
        val maxLevel = allHigh.mapNotNull { it.tphLevel }.maxOrNull() ?: 600
        val minLevel = allLow.mapNotNull  { it.tphLevel }.minOrNull() ?: 50
        val range    = (maxLevel - minLevel).toFloat()
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

        val nowTime  = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
        val highItem = allHigh.firstOrNull { it.tphTime.orEmpty() >= nowTime } ?: allHigh.lastOrNull()
        val lowItem  = allLow.firstOrNull  { it.tphTime.orEmpty() >= nowTime } ?: allLow.lastOrNull()
        Log.d(TAG, "  tideStatus=${tideStatus.displayName}  nextHigh=${highItem?.tphTime}(${highItem?.tphLevel}cm)  nextLow=${lowItem?.tphTime}(${lowItem?.tphLevel}cm)")

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
            highTideTime = highItem?.tphTime,
            lowTideTime  = lowItem?.tphTime,
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

    // obsCode=null 단일 호출로 전체 조위 일괄 수집 (include로 필드 제한 → 최소 응답 크기)
    // 개별 obsCode 병렬 호출은 OkHttp maxRequestsPerHost=5 기본값에 막혀 ~6초 소요
    override suspend fun getBatchRecentLevels(): List<RecentTideLevel> {
        // Return cache if still fresh
        val now = System.currentTimeMillis()
        if (batchCache.isNotEmpty() && now - batchCacheTime < BATCH_CACHE_TTL) {
            Log.d(TAG, "▶ getBatchRecentLevels 캐시 사용 (${(now - batchCacheTime) / 1000}초 경과)")
            return batchCache
        }

        return batchMutex.withLock {
            // Double-check after acquiring lock (another coroutine may have just fetched)
            val now2 = System.currentTimeMillis()
            if (batchCache.isNotEmpty() && now2 - batchCacheTime < BATCH_CACHE_TTL) {
                return@withLock batchCache
            }

            val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
            Log.d(TAG, "▶ getBatchRecentLevels API 호출 date=$date (numOfRows=1000)")
            val t0 = System.currentTimeMillis()

            val raw = khoaDataApi.getTideRecent(
                serviceKey = apiKey,
                obsCode    = null,
                date       = date,
                numOfRows  = 1000
            ).body?.items?.item.orEmpty()

            // API returns time-series rows per station — keep only the latest row per station
            val result = raw
                .filter { it.lat != null && it.lon != null && (it.tideLevel != null || it.windSpeed != null) }
                .groupBy { it.stationName ?: "${it.lat}_${it.lon}" }
                .values
                .map { group -> group.maxByOrNull { it.obsrvnDt ?: "" }!! }
                .map { RecentTideLevel(it.lat!!, it.lon!!, it.tideLevel?.toInt(), it.windSpeed) }

            Log.d(TAG, "  완료 ${System.currentTimeMillis() - t0}ms  raw=${raw.size}  valid=${result.size}")

            if (result.isNotEmpty()) {
                batchCache = result
                batchCacheTime = System.currentTimeMillis()
            }
            result
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
    }
}
