package com.koretide.app.domain.repository

import com.koretide.app.domain.model.RecentTideLevel
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.TideRecord

interface TideRepository {
    suspend fun getTideData(stationCode: String): TideData
    suspend fun getTideHistory(stationCode: String, date: String): List<TideRecord>
    suspend fun getBatchRecentLevels(): List<RecentTideLevel>
}
