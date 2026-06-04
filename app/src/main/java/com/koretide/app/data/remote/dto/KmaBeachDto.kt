package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KmaBeachResponse(
    @Json(name = "response") val response: KmaBeachResult?
)

@JsonClass(generateAdapter = true)
data class KmaBeachResult(
    @Json(name = "header") val header: KmaBeachHeader?,
    @Json(name = "body")   val body: KmaBeachBody?
)

@JsonClass(generateAdapter = true)
data class KmaBeachHeader(
    @Json(name = "resultCode") val resultCode: String?,
    @Json(name = "resultMsg")  val resultMsg: String?
)

@JsonClass(generateAdapter = true)
data class KmaBeachBody(
    @Json(name = "items")      val items: KmaBeachItems?,
    @Json(name = "totalCount") val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class KmaBeachItems(
    @Json(name = "item") val item: List<KmaBeachItem>?
)

@JsonClass(generateAdapter = true)
data class KmaBeachItem(
    @Json(name = "beachName") val beachName: String?,  // 해수욕장명
    @Json(name = "fcstDate")  val fcstDate: String?,   // 예보일자 YYYYMMDD
    @Json(name = "fcstTime")  val fcstTime: String?,   // 예보시각 HHMM
    @Json(name = "ww")        val weather: String?,    // 날씨 상태
    @Json(name = "at")        val airTemp: String?,    // 기온 °C
    @Json(name = "wt")        val waterTemp: String?,  // 수온 °C
    @Json(name = "ws")        val windSpeed: String?,  // 풍속 m/s
    @Json(name = "wd")        val windDir: String?,    // 풍향 deg
    @Json(name = "wh")        val waveHeight: String?, // 파고 m
    @Json(name = "grade")     val grade: String?       // 등급 A/B/C/D/E 또는 Korean
)
