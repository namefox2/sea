package com.koretide.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.koretide.app.domain.model.TideRecord

@Entity(
    tableName = "tide_records",
    indices = [Index("station_code", "timestamp")],
    foreignKeys = [ForeignKey(
        entity = StationEntity::class,
        parentColumns = ["code"],
        childColumns = ["station_code"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TideRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "station_code") val stationCode: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "water_level") val waterLevel: Int,
    @ColumnInfo(name = "is_forecast") val isForecast: Boolean = false
) {
    fun toDomain() = TideRecord(
        stationCode = stationCode,
        timestamp = timestamp,
        waterLevel = waterLevel,
        isForecast = isForecast
    )

    companion object {
        fun fromDomain(record: TideRecord) = TideRecordEntity(
            stationCode = record.stationCode,
            timestamp = record.timestamp,
            waterLevel = record.waterLevel,
            isForecast = record.isForecast
        )
    }
}
