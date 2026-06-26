package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import android.util.Log
import com.koretide.app.data.local.dao.TideRecordDao
import com.koretide.app.data.local.entity.TideRecordEntity
import com.koretide.app.data.remote.KhoaDataApiService
import com.koretide.app.domain.model.RecentTideLevel
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.TideRecord
import com.koretide.app.domain.model.TideStatus
import com.koretide.app.domain.repository.TideRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TideRepositoryImpl @Inject constructor(
    private val tideRecordDao: TideRecordDao,
    private val khoaDataApi: KhoaDataApiService
) : TideRepository {

    private val apiKey get() = BuildConfig.KHOA_API_KEY

    override suspend fun getTideData(stationCode: String): TideData {
        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        val current = khoaDataApi.getTideRecent(apiKey, stationCode, date)
        val table   = khoaDataApi.getTideForecast(apiKey, stationCode, date)

        val dataItems  = current.body?.items?.item ?: emptyList()
        val tableItems = table.body?.items?.item   ?: emptyList()

        val currentLevel = dataItems.lastOrNull()?.tideLevel?.toInt() ?: 300

        val allHigh  = tableItems.filter { it.hlCode == "HH" }
        val allLow   = tableItems.filter { it.hlCode == "LL" }
        val maxLevel = allHigh.mapNotNull { it.tphLevel }.maxOrNull() ?: 600
        val minLevel = allLow.mapNotNull  { it.tphLevel }.minOrNull() ?: 50
        val range    = (maxLevel - minLevel).toFloat()
        val tidePercent = if (range > 0f)
            ((currentLevel - minLevel).toFloat() / range).coerceIn(0f, 1f)
        else 0.5f

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
        if (cached.isNotEmpty()) return cached.map { it.toDomain() }
        val data = getTideData(stationCode)
        return data.records
    }

    override suspend fun getBatchRecentLevels(): List<RecentTideLevel> {
        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        val items = khoaDataApi.getTideRecent(apiKey, null, date, 200).body?.items?.item.orEmpty()
        return items
            .filter { it.lat != null && it.lon != null && it.tideLevel != null }
            .map { RecentTideLevel(it.lat!!, it.lon!!, it.tideLevel!!.toInt()) }
    }

    private fun parseDateToMillis(dateStr: String): Long {
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyyMMdd HHmm")
        for (fmt in formats) {
            runCatching { SimpleDateFormat(fmt, Locale.KOREA).parse(dateStr)?.time }
                .getOrNull()?.let { return it }
        }
        return System.currentTimeMillis()
    }
}
