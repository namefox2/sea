package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import com.koretide.app.data.local.dao.TideRecordDao
import com.koretide.app.data.local.entity.TideRecordEntity
import com.koretide.app.data.remote.KhoaApiService
import com.koretide.app.data.remote.MockDataSource
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
    private val khoaApi: KhoaApiService
) : TideRepository {

    private val apiKey get() = BuildConfig.KHOA_API_KEY

    override suspend fun getTideData(stationCode: String): TideData {
        if (apiKey.isBlank()) return MockDataSource.mockTideData(stationCode)

        return try {
            val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
            val current = khoaApi.getCurrentTide(apiKey, stationCode, date)
            val table = khoaApi.getTideTable(apiKey, stationCode, date)

            val currentLevel = current.result?.data?.lastOrNull()?.tideLevel ?: 300
            val tableItems = table.result?.data ?: emptyList()
            val highItem = tableItems.firstOrNull { it.hlCode == "HH" }
            val lowItem = tableItems.firstOrNull { it.hlCode == "LL" }
            val maxLevel = highItem?.tphLevel ?: 600
            val minLevel = lowItem?.tphLevel ?: 50
            val tidePercent = ((currentLevel - minLevel).toFloat() / (maxLevel - minLevel).toFloat())
                .coerceIn(0f, 1f)

            val records = current.result?.data?.map { item ->
                val ts = parseDateToMillis(item.recordTime ?: date)
                TideRecordEntity(stationCode = stationCode, timestamp = ts, waterLevel = item.tideLevel ?: 0)
            } ?: emptyList()

            tideRecordDao.insertAll(records)
            // 30일 초과 기록 자동 삭제 — 개인정보 최소 보관 원칙
            tideRecordDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 3_600_000)

            TideData(
                stationCode = stationCode,
                currentLevel = currentLevel,
                maxLevel = maxLevel,
                minLevel = minLevel,
                tidePercent = tidePercent,
                tideStatus = TideStatus.RISING,
                highTideTime = highItem?.tphTime,
                lowTideTime = lowItem?.tphTime,
                records = records.map { it.toDomain() }
            )
        } catch (e: Exception) {
            MockDataSource.mockTideData(stationCode)
        }
    }

    override suspend fun getTideHistory(stationCode: String, date: String): List<TideRecord> {
        val since = System.currentTimeMillis() - 24 * 3_600_000L
        val cached = tideRecordDao.getRecords(stationCode, since)
        if (cached.isNotEmpty()) return cached.map { it.toDomain() }
        val data = getTideData(stationCode)
        return data.records
    }

    private fun parseDateToMillis(dateStr: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
            fmt.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
