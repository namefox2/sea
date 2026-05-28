package com.koretide.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "region") val region: String,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lng") val lng: Double,
    @ColumnInfo(name = "last_tide_level") val lastTideLevel: Int? = null,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long? = null
) {
    fun toDomain() = Station(
        code = code,
        name = name,
        region = StationRegion.valueOf(region),
        lat = lat,
        lng = lng,
        lastTideLevel = lastTideLevel,
        lastUpdated = lastUpdated
    )

    companion object {
        fun fromDomain(station: Station) = StationEntity(
            code = station.code,
            name = station.name,
            region = station.region.name,
            lat = station.lat,
            lng = station.lng,
            lastTideLevel = station.lastTideLevel,
            lastUpdated = station.lastUpdated
        )
    }
}
