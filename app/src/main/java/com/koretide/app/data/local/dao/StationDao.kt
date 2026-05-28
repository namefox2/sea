package com.koretide.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.koretide.app.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY region, name")
    fun getAllStations(): Flow<List<StationEntity>>

    @Query("""
        SELECT * FROM stations
        WHERE (:query = '' OR name LIKE '%' || :query || '%')
          AND (:region IS NULL OR region = :region)
        ORDER BY region, name
    """)
    fun searchStations(query: String, region: String?): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE code = :code LIMIT 1")
    suspend fun getStation(code: String): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stations: List<StationEntity>)

    @Update
    suspend fun update(station: StationEntity)

    @Query("UPDATE stations SET last_tide_level = :level, last_updated = :time WHERE code = :code")
    suspend fun updateTideCache(code: String, level: Int, time: Long)

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun count(): Int
}
