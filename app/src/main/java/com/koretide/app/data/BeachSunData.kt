package com.koretide.app.data

import com.koretide.app.domain.model.SunriseSpot

/**
 * Representative Korean coastal beaches with their sea-facing azimuths.
 * seaAzimuth is the compass bearing (from North, clockwise) toward the open sea.
 *
 * East coast (동해안): sea to the east  → ~90°  → sunrise over sea
 * West coast (서해안): sea to the west  → ~270° → sunset over sea
 * South coast (남해안): sea to the south → ~135–180°
 * Jeju: varies by beach
 */
object BeachSunData {

    val spots: List<SunriseSpot> = listOf(

        // ── 동해안 (East coast — sunrise over sea) ──────────────────
        SunriseSpot("정동진 해수욕장",   lat = 37.6880, lon = 129.0182, seaAzimuth = 90.0),
        SunriseSpot("경포 해수욕장",     lat = 37.7975, lon = 128.9040, seaAzimuth = 90.0),
        SunriseSpot("낙산 해수욕장",     lat = 38.1178, lon = 128.6272, seaAzimuth = 90.0),
        SunriseSpot("속초 해수욕장",     lat = 38.2093, lon = 128.5843, seaAzimuth = 85.0),
        SunriseSpot("망상 해수욕장",     lat = 37.5940, lon = 129.1155, seaAzimuth = 90.0),
        SunriseSpot("삼척 해수욕장",     lat = 37.4393, lon = 129.1740, seaAzimuth = 95.0),
        SunriseSpot("죽도 해수욕장",     lat = 36.3155, lon = 129.3833, seaAzimuth = 90.0),
        SunriseSpot("영일대 해수욕장",   lat = 36.0482, lon = 129.3741, seaAzimuth = 85.0),
        SunriseSpot("일산 해수욕장",     lat = 35.5230, lon = 129.4380, seaAzimuth = 90.0),

        // ── 서해안 (West coast — sunset over sea) ───────────────────
        SunriseSpot("을왕리 해수욕장",   lat = 37.4680, lon = 126.3780, seaAzimuth = 270.0),
        SunriseSpot("대천 해수욕장",     lat = 36.3170, lon = 126.5040, seaAzimuth = 270.0),
        SunriseSpot("만리포 해수욕장",   lat = 36.9480, lon = 126.1330, seaAzimuth = 270.0),
        SunriseSpot("변산 해수욕장",     lat = 35.6790, lon = 126.5580, seaAzimuth = 270.0),
        SunriseSpot("왜목마을 해수욕장", lat = 36.9780, lon = 126.6280, seaAzimuth = 60.0),  // NE — both sunrise & sunset visible

        // ── 남해안 (South coast) ─────────────────────────────────────
        SunriseSpot("해운대 해수욕장",   lat = 35.1585, lon = 129.1585, seaAzimuth = 135.0),
        SunriseSpot("광안리 해수욕장",   lat = 35.1526, lon = 129.1183, seaAzimuth = 150.0),
        SunriseSpot("송정 해수욕장",     lat = 35.1791, lon = 129.2003, seaAzimuth = 100.0),
        SunriseSpot("다대포 해수욕장",   lat = 35.0590, lon = 128.9660, seaAzimuth = 180.0),
        SunriseSpot("상주 해수욕장",     lat = 34.8760, lon = 128.0430, seaAzimuth = 180.0),

        // ── 제주 (Jeju) ──────────────────────────────────────────────
        SunriseSpot("협재 해수욕장",     lat = 33.3900, lon = 126.2390, seaAzimuth = 280.0),
        SunriseSpot("함덕 해수욕장",     lat = 33.5465, lon = 126.6700, seaAzimuth = 10.0),
        SunriseSpot("중문 색달 해수욕장",lat = 33.2440, lon = 126.4140, seaAzimuth = 185.0),
        SunriseSpot("김녕 해수욕장",     lat = 33.5508, lon = 126.7677, seaAzimuth = 20.0),
        SunriseSpot("표선 해수욕장",     lat = 33.3195, lon = 126.8458, seaAzimuth = 135.0)
    )

    fun findByName(name: String): SunriseSpot? = spots.find { it.name == name }
}
