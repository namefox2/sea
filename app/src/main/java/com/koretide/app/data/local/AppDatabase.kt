package com.koretide.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.local.dao.TideRecordDao
import com.koretide.app.data.local.entity.StationEntity
import com.koretide.app.data.local.entity.TideRecordEntity

@Database(
    entities = [StationEntity::class, TideRecordEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun tideRecordDao(): TideRecordDao
}
