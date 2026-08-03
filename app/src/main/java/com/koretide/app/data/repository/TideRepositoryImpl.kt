package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.local.dao.TideRecordDao
import com.koretide.app.data.local.entity.TideRecordEntity
import com.koretide.app.data.remote.DtRecentSource
import com.koretide.app.data.remote.KhoaDataApiService
import com.koretide.app.domain.model.RecentTideLevel
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.TideStatus
import com.koretide.app.domain.repository.TideRepository
import com.koretide.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
    private val khoaDataApi: KhoaDataApiService,
    private val dtRecentSource: DtRecentSource
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
                    // 형식: code,lat,lon,level,wind,dayMin,dayMax (구버전 캐시는 폐기 후 재조회)
                    if (p.size < 7) return@mapNotNull null
                    val code = p[0].ifBlank { return@mapNotNull null }
                    val lat = p[1].toDoubleOrNull() ?: return@mapNotNull null
                    val lon = p[2].toDoubleOrNull() ?: return@mapNotNull null
                    RecentTideLevel(
                        code, lat, lon, p[3].toIntOrNull(), p[4].toFloatOrNull(),
                        p[5].toIntOrNull(), p[6].toIntOrNull()
                    )
                }
                if (list.isNotEmpty()) { batchCache = list; batchCacheTime = time }
            }
        }
        diskLoaded = true
    }

    private fun persistBatch(levels: List<RecentTideLevel>, time: Long) {
        val encoded = levels.joinToString("\n") { l ->
            "${l.code},${l.lat},${l.lon},${l.levelCm ?: ""},${l.windSpeedMs ?: ""},${l.dayMinCm ?: ""},${l.dayMaxCm ?: ""}"
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

        // 실시간(dtRecent, 공유 캐시) + 고저조예보(tideFcst)를 병렬 호출해 진입 지연 단축.
        // 한쪽 호출이 실패해도 다른 쪽으로 상세보기가 뜨도록 각각 runCatching 으로 격리한다.
        // (예전엔 예보 호출이 실패하면 coroutineScope 전체가 터져 실시간이 성공해도 상세가 깨졌다.)
        val (dataItems, table) = coroutineScope {
            val c = async {
                runCatching { dtRecentSource.items(stationCode, date) }.getOrDefault(emptyList())
            }
            val t = async {
                runCatching { khoaDataApi.getTideForecast(apiKey, stationCode, date) }.getOrNull()
            }
            c.await() to t.await()
        }
        val tableItems = table?.body?.items?.item ?: emptyList()
        Log.d(TAG, "  dtRecent items=${dataItems.size}  tideFcst items=${tableItems.size}")

        val realtimeLevel = dataItems.lastOrNull { it.tideLevel != null }?.tideLevel?.toInt()
        Log.d(TAG, "  lastItem obsrvnDt=${dataItems.lastOrNull()?.obsrvnDt}  tideLevel=${dataItems.lastOrNull()?.tideLevel}cm")

        // 하루 조석예보(고/저조)에서 고조(extrSe 1,3)·저조(extrSe 2,4)를 모아 일조차 계산.
        val allHigh  = tableItems.filter { it.extrSe == 1 || it.extrSe == 3 }
        val allLow   = tableItems.filter { it.extrSe == 2 || it.extrSe == 4 }
        val fcMax = allHigh.mapNotNull { it.predcTdlvVl?.toInt() }.maxOrNull()
        val fcMin = allLow.mapNotNull  { it.predcTdlvVl?.toInt() }.minOrNull()

        // 현재 수위: ① 실시간 우선 ② 없으면 조석예보(고+저)/2로 추정(실제 예보 기반)
        // ③ 실시간·예보 둘 다 없을 때만 오류. 허구값(300cm)은 쓰지 않는다.
        val currentLevel = realtimeLevel
            ?: (if (fcMax != null && fcMin != null) (fcMax + fcMin) / 2 else null)
            ?: throw IllegalStateException("조위 데이터를 가져올 수 없습니다")

        // 예보가 없으면 조차를 알 수 없으므로 현재 수위로 폴백(허구 조차 방지).
        val maxLevel = fcMax ?: currentLevel
        val minLevel = fcMin ?: currentLevel
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

        // 포맷터는 항목마다가 아니라 이 호출에서 한 번만 만든다(SimpleDateFormat은 스레드
        // 안전하지 않으므로 인스턴스 필드로 공유하지 않고 호출 로컬로 둔다).
        val tsFormats = DATE_TIME_PATTERNS.map { SimpleDateFormat(it, Locale.KOREA) }
        val records = dataItems.map { item ->
            val ts = parseDateToMillis(tsFormats, item.obsrvnDt ?: date)
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

    // 각 관측소 코드로 dtRecent 개별 병렬 호출 (obsCode=null 벌크 호출은 API가 지원하지 않음)
    // 응답은 시간 오름차순이므로 하루치(numOfRows=100)를 받아 lastOrNull()로 최신 기록을 취한다.
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

            // 관측소가 많아(수십 개) 한꺼번에 호출하면 rate-limit/타임아웃 위험이 있어 동시 호출 수 제한.
            val gate = Semaphore(8)
            val result = coroutineScope {
                stations.map { station ->
                    async {
                        gate.withPermit {
                            try {
                                // 실시간(수위·바람): 오늘 자료가 없으면(NODATA) 전날로 폴백.
                                // dtRecent는 시간 오름차순이라 최신은 마지막 항목.
                                var items = khoaDataApi.getTideRecent(
                                    serviceKey = apiKey, obsCode = station.code, date = date, numOfRows = 100
                                ).body?.items?.item ?: emptyList()
                                if (items.isEmpty()) {
                                    items = khoaDataApi.getTideRecent(
                                        serviceKey = apiKey, obsCode = station.code,
                                        date = DateUtils.previousDay(date), numOfRows = 100
                                    ).body?.items?.item ?: emptyList()
                                }
                                val levelCm = items.lastOrNull { it.tideLevel != null }?.tideLevel?.toInt()
                                val windMs  = items.lastOrNull()?.windSpeed

                                // 조위%용 저조/고조는 '조석예보'에서 (상세보기와 동일 기준).
                                // 관측 범위는 아침엔 좁아 부정확하므로 예보의 하루 고/저조를 쓴다.
                                val fc = runCatching {
                                    khoaDataApi.getTideForecast(apiKey, station.code, date).body?.items?.item ?: emptyList()
                                }.getOrDefault(emptyList())
                                val fcMax = fc.filter { it.extrSe == 1 || it.extrSe == 3 }
                                    .mapNotNull { it.predcTdlvVl?.toInt() }.maxOrNull()
                                val fcMin = fc.filter { it.extrSe == 2 || it.extrSe == 4 }
                                    .mapNotNull { it.predcTdlvVl?.toInt() }.minOrNull()

                                if (levelCm != null || windMs != null) {
                                    RecentTideLevel(
                                        station.code, station.lat, station.lng, levelCm, windMs,
                                        dayMinCm = fcMin, dayMaxCm = fcMax
                                    )
                                } else null
                            } catch (e: Exception) {
                                Log.w(TAG, "batch[${station.code}] 실패: ${e.message}")
                                null
                            }
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

    private fun parseDateToMillis(formats: List<SimpleDateFormat>, dateStr: String): Long {
        for (fmt in formats) {
            runCatching { fmt.parse(dateStr)?.time }.getOrNull()?.let { return it }
        }
        return System.currentTimeMillis()
    }

    companion object {
        private const val TAG = "TideRepo"
        private const val KEY_BATCH_DATA = "batch_data"
        private const val KEY_BATCH_TIME = "batch_time"
        private val DATE_TIME_PATTERNS = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyyMMdd HHmm")
    }
}
