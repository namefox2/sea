package com.koretide.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.koretide.app.data.local.entity.TideRecordEntity

@Dao
interface TideRecordDao {
    @Query("""
        SELECT * FROM tide_records
        WHERE station_code = :stationCode
          AND timestamp >= :since
        ORDER BY timestamp ASC
    """)
    suspend fun getRecords(stationCode: String, since: Long): List<TideRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<TideRecordEntity>)

    @Query("DELETE FROM tide_records WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
