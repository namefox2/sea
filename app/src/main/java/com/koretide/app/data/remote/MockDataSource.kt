package com.koretide.app.data.remote

import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.TideRecord
import com.koretide.app.domain.model.TideStatus
import com.koretide.app.domain.model.WindData
import com.koretide.app.util.BeaufortConverter
import kotlin.math.PI
import kotlin.math.sin

object MockDataSource {

    val stations: List<Station> = listOf(
        // 서해안
        Station("DT_0001", "인천", StationRegion.WEST, 37.4538, 126.5937),
        Station("DT_0002", "평택", StationRegion.WEST, 37.0000, 126.8300),
        Station("DT_0003", "안흥", StationRegion.WEST, 36.6800, 126.1300),
        Station("DT_0004", "보령", StationRegion.WEST, 36.4100, 126.4800),
        Station("DT_0005", "군산", StationRegion.WEST, 35.9800, 126.7200),
        Station("DT_0006", "위도", StationRegion.WEST, 35.6200, 126.3000),
        Station("DT_0007", "목포", StationRegion.WEST, 34.7900, 126.3800),
        Station("DT_0008", "흑산도", StationRegion.WEST, 34.6800, 125.4400),
        Station("DT_0009", "강화", StationRegion.WEST, 37.7200, 126.4700),
        Station("DT_0010", "대산", StationRegion.WEST, 37.0000, 126.3800),
        Station("DT_0011", "장항", StationRegion.WEST, 36.0000, 126.6800),
        Station("DT_0012", "신안", StationRegion.WEST, 34.8300, 126.1200),
        Station("DT_0069", "옹진", StationRegion.WEST, 37.5000, 126.2000),
        Station("DT_0070", "덕적도", StationRegion.WEST, 37.2300, 126.1300),
        // 남해안
        Station("DT_0013", "여수", StationRegion.SOUTH, 34.7500, 127.7500),
        Station("DT_0014", "완도", StationRegion.SOUTH, 34.3200, 126.7500),
        Station("DT_0015", "거문도", StationRegion.SOUTH, 34.0200, 127.3000),
        Station("DT_0016", "통영", StationRegion.SOUTH, 34.8500, 128.4300),
        Station("DT_0017", "부산", StationRegion.SOUTH, 35.1000, 129.0400),
        Station("DT_0018", "마산", StationRegion.SOUTH, 35.2000, 128.5700),
        Station("DT_0019", "고흥", StationRegion.SOUTH, 34.6200, 127.2800),
        Station("DT_0020", "해남", StationRegion.SOUTH, 34.5500, 126.6000),
        Station("DT_0021", "진도", StationRegion.SOUTH, 34.4800, 126.2700),
        Station("DT_0022", "남해", StationRegion.SOUTH, 34.8300, 127.8800),
        Station("DT_0023", "거제", StationRegion.SOUTH, 34.8700, 128.6200),
        Station("DT_0024", "욕지도", StationRegion.SOUTH, 34.0800, 128.3500),
        Station("DT_0063", "완도항", StationRegion.SOUTH, 34.3300, 126.7700),
        Station("DT_0064", "외나로도", StationRegion.SOUTH, 34.4300, 127.5300),
        Station("DT_0067", "해남화원", StationRegion.SOUTH, 34.6200, 126.4800),
        Station("DT_0068", "여천", StationRegion.SOUTH, 34.7500, 127.7500),
        // 동해안
        Station("DT_0025", "속초", StationRegion.EAST, 38.2000, 128.5900),
        Station("DT_0026", "강릉", StationRegion.EAST, 37.7700, 128.9000),
        Station("DT_0027", "동해", StationRegion.EAST, 37.5200, 129.1200),
        Station("DT_0028", "울진", StationRegion.EAST, 36.9900, 129.4100),
        Station("DT_0029", "포항", StationRegion.EAST, 36.0500, 129.3700),
        Station("DT_0030", "울산", StationRegion.EAST, 35.5000, 129.3700),
        Station("DT_0031", "고성", StationRegion.EAST, 38.3800, 128.4700),
        Station("DT_0032", "삼척", StationRegion.EAST, 37.4300, 129.1700),
        Station("DT_0033", "영덕", StationRegion.EAST, 36.5300, 129.4100),
        Station("DT_0034", "후포", StationRegion.EAST, 36.6800, 129.4500),
        Station("DT_0039", "죽변", StationRegion.EAST, 37.0500, 129.4200),
        Station("DT_0040", "왜관", StationRegion.EAST, 35.9700, 128.3900),
        // 제주
        Station("DT_0035", "제주", StationRegion.JEJU, 33.5100, 126.5200),
        Station("DT_0036", "서귀포", StationRegion.JEJU, 33.2500, 126.5600),
        Station("DT_0037", "모슬포", StationRegion.JEJU, 33.2200, 126.2500),
        Station("DT_0038", "한림", StationRegion.JEJU, 33.4100, 126.2600),
        Station("DT_0041", "성산", StationRegion.JEJU, 33.4700, 126.9200),
        Station("DT_0042", "추자도", StationRegion.JEJU, 33.9500, 126.3000),
        Station("DT_0043", "우도", StationRegion.JEJU, 33.5000, 126.9500),
        Station("DT_0044", "마라도", StationRegion.JEJU, 33.1200, 126.2700)
    )

    fun mockTideData(stationCode: String): TideData {
        val station = stations.firstOrNull { it.code == stationCode }
        val (maxLevel, minLevel) = when (station?.region) {
            StationRegion.WEST  -> 870 to 80   // 서해: 대조차
            StationRegion.SOUTH -> 400 to 60   // 남해: 중조차
            StationRegion.JEJU  -> 220 to 50   // 제주
            else                -> 170 to 110  // 동해: 소조차
        }

        // Station-specific phase offset so each station shows a different tide stage
        val stationHash = (stationCode.hashCode() and 0x7FFFFFFF)
        val phaseOffset = (stationHash % 1200) * PI / 600.0

        val now = System.currentTimeMillis()
        val minuteOfCycle = (now / 60_000L % 720L).toInt()  // 12-hour tidal cycle
        val phase = minuteOfCycle * PI / 360.0 + phaseOffset
        val tidePercent = ((sin(phase) + 1.0) / 2.0).toFloat()
        val currentLevel = (minLevel + (maxLevel - minLevel) * tidePercent).toInt()

        val status = when {
            tidePercent > 0.9f -> TideStatus.HIGH_TIDE
            tidePercent < 0.1f -> TideStatus.LOW_TIDE
            sin(phase) > 0.0   -> TideStatus.RISING
            else               -> TideStatus.FALLING
        }

        val records = (0..23).map { h ->
            val p = sin(h * PI / 6.0 + phaseOffset)
            val level = (minLevel + (maxLevel - minLevel) * ((p + 1.0) / 2.0)).toInt()
            TideRecord(stationCode, now - (23 - h) * 3_600_000L, level, false)
        }

        val hourNow = (minuteOfCycle / 60)
        return TideData(
            stationCode  = stationCode,
            currentLevel = currentLevel,
            maxLevel     = maxLevel,
            minLevel     = minLevel,
            tidePercent  = tidePercent,
            tideStatus   = status,
            highTideTime = "${(hourNow + 6) % 24}:00",
            lowTideTime  = "${(hourNow + 12) % 24}:00",
            records      = records
        )
    }

    fun mockWindData(stationCode: String): WindData {
        val speedMs = 5.5f
        val bft = BeaufortConverter.toBft(speedMs)
        return WindData(
            stationCode = stationCode,
            speedMs = speedMs,
            beaufort = bft,
            directionDeg = 225f,
            beaufortName = BeaufortConverter.name(bft)
        )
    }
}
