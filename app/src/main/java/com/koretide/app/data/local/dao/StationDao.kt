package com.koretide.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stations: List<StationEntity>)

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun count(): Int

    @Query("SELECT * FROM stations")
    suspend fun getAllStationsSnapshot(): List<StationEntity>
}
