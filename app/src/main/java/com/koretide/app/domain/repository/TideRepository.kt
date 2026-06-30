package com.koretide.app.domain.repository

import com.koretide.app.domain.model.RecentTideLevel
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.TideRecord

interface TideRepository {
    suspend fun getTideData(stationCode: String): TideData
    suspend fun getTideHistory(stationCode: String, date: String): List<TideRecord>
    suspend fun getBatchRecentLevels(): List<RecentTideLevel>
    /** 캐시(메모리/디스크)만 즉시 반환, 네트워크 호출 없음. 비어있을 수 있음. */
    suspend fun getCachedBatchLevels(): List<RecentTideLevel>
}
