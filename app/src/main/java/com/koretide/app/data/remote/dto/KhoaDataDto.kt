package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// data.go.kr 공통 헤더
@JsonClass(generateAdapter = true)
data class KhoaHeader(
    @Json(name = "resultCode") val resultCode: String?,
    @Json(name = "resultMsg")  val resultMsg: String?
)

// ── surveyWind: 풍향/풍속 관측 ────────────────────────────────────────────────
// 실제 응답: {"header":{...},"body":{"items":{"item":[...]},"totalCount":16}}
@JsonClass(generateAdapter = true)
data class KhoaWindResponse(
    @Json(name = "header") val header: KhoaHeader?,
    @Json(name = "body")   val body: KhoaWindBody?
)

@JsonClass(generateAdapter = true)
data class KhoaWindBody(
    @Json(name = "items")      val items: KhoaWindItems?,
    @Json(name = "totalCount") val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class KhoaWindItems(
    @Json(name = "item") val item: List<KhoaWindItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaWindItem(
    @Json(name = "obsvtrNm") val stationName: String?,
    @Json(name = "lat")      val lat: Double?,
    @Json(name = "lot")      val lon: Double?,
    @Json(name = "obsrvnDt") val obsrvnDt: String?,
    @Json(name = "wndrct")   val windDir: Float?,
    @Json(name = "wspd")     val windSpeed: Float?
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

// ── dtRecent: 실시간 조위 관측 ─────────────────────────────────────────────────
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
    @Json(name = "obsrvnDt") val obsrvnDt: String?,
    @Json(name = "wl")       val tideLevel: Int?
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

@JsonClass(generateAdapter = true)
data class KhoaTideFcstItem(
    @Json(name = "tphTime")  val tphTime: String?,
    @Json(name = "tphLevel") val tphLevel: Int?,
    @Json(name = "hlCode")   val hlCode: String?
)
