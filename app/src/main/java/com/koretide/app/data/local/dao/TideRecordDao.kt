package com.koretide.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.koretide.app.data.local.entity.TideRecordEntity

@Dao
interface TideRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<TideRecordEntity>)

    @Query("DELETE FROM tide_records WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
