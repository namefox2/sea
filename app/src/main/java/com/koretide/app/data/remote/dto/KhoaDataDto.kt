package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// data.go.kr 공통 헤더
@JsonClass(generateAdapter = true)
data class KhoaHeader(
    @Json(name = "resultCode") val resultCode: String?,
    @Json(name = "resultMsg")  val resultMsg: String?
)

// ── dtRecent: 복합해양환경관측소 실시간 (조위 + 풍향/풍속 + 기상 포함) ──────────
// 실제 응답 예시:
// {"obsvtrNm":"고흥발포","lot":127.34,"lat":34.48,"obsrvnDt":"2026-06-25 15:42",
//  "wndrct":172.0,"wspd":4.2,"bscTdlvHgt":209.0,"artmp":23.5,...}
@JsonClass(generateAdapter = true)
data class KhoaTideRecentResponse(
    @Json(name = "header") val header: KhoaHeader?,
    @Json(name = "body")   val body: KhoaTideRecentBody?
)

@JsonClass(generateAdapter = true)
data class KhoaTideRecentBody(
    @Json(name = "items")      val items: KhoaTideRecentItems?,
    @Json(name = "totalCount") val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class KhoaTideRecentItems(
    @Json(name = "item") val item: List<KhoaTideRecentItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaTideRecentItem(
    @Json(name = "obsvtrNm")   val stationName: String?,
    @Json(name = "lat")        val lat: Double?,
    @Json(name = "lot")        val lon: Double?,
    @Json(name = "obsrvnDt")   val obsrvnDt: String?,
    @Json(name = "bscTdlvHgt") val tideLevel: Float?,   // 기본조위높이 (cm)
    @Json(name = "wndrct")     val windDir: Float?,      // 풍향 (deg)
    @Json(name = "wspd")       val windSpeed: Float?,    // 풍속 (m/s)
    @Json(name = "artmp")      val airTemp: Float?,      // 기온 (°C)
    @Json(name = "wtem")       val waterTemp: Float?     // 수온 (°C)
)

// ── tideFcstHghLw: 고저조 예보 ────────────────────────────────────────────────
@JsonClass(generateAdapter = true)
data class KhoaTideFcstResponse(
    @Json(name = "header") val header: KhoaHeader?,
    @Json(name = "body")   val body: KhoaTideFcstBody?
)

@JsonClass(generateAdapter = true)
data class KhoaTideFcstBody(
    @Json(name = "items")      val items: KhoaTideFcstItems?,
    @Json(name = "totalCount") val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class KhoaTideFcstItems(
    @Json(name = "item") val item: List<KhoaTideFcstItem>?
)

// tideFcstHghLw 실제 응답 필드 (API 문서 기준):
//   obsvtrNm(관측소명), predcDt(예보일시 "yyyy-MM-dd HH:mm"),
//   predcTdlvVl(예보 조위, cm), extrSe(극치구분: 1=오전고조 2=오전저조 3=오후고조 4=오후저조)
@JsonClass(generateAdapter = true)
data class KhoaTideFcstItem(
    @Json(name = "obsvtrNm")    val stationName: String?,
    @Json(name = "predcDt")     val predcDt: String?,
    @Json(name = "predcTdlvVl") val predcTdlvVl: Double?,
    @Json(name = "extrSe")      val extrSe: Int?
)

// ── noonWave: 실측파랑 ────────────────────────────────────────────────────────
@JsonClass(generateAdapter = true)
data class KhoaWaveResponse(
    @Json(name = "header") val header: KhoaHeader?,
    @Json(name = "body")   val body: KhoaWaveBody?
)

@JsonClass(generateAdapter = true)
data class KhoaWaveBody(
    @Json(name = "items")      val items: KhoaWaveItems?,
    @Json(name = "totalCount") val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class KhoaWaveItems(
    @Json(name = "item") val item: List<KhoaWaveItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaWaveItem(
    @Json(name = "obsvtrNm") val stationName: String?,
    @Json(name = "lat")      val lat: Double?,
    @Json(name = "lot")      val lon: Double?,
    @Json(name = "obsrvnDt") val obsrvnDt: String?,
    @Json(name = "wh")       val waveHeight: Float?,
    @Json(name = "wp")       val wavePeriod: Float?,
    @Json(name = "wd")       val waveDir: Float?
)
