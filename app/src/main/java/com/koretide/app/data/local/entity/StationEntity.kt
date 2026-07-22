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
        // 저장된 region 문자열이 enum 이름과 다를 경우(예: 구버전/손상 데이터)
        // valueOf 가 크래시하므로, 좌표 기반 재분류로 안전하게 폴백한다.
        region = runCatching { StationRegion.valueOf(region) }
            .getOrElse { StationRegion.fromCoords(lat, lng) },
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
