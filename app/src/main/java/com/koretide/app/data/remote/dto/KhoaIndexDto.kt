package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KhoaIndexResponse(
    @Json(name = "result") val result: KhoaIndexResult?
)

@JsonClass(generateAdapter = true)
data class KhoaIndexResult(
    @Json(name = "meta") val meta: KhoaDataMeta?,
    @Json(name = "data") val data: List<KhoaIndexItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaIndexItem(
    @Json(name = "obs_post_nm") val obsPostNm: String?,   // 관측소/해수욕장명
    @Json(name = "obs_code")    val obsCode: String?,      // 관측소코드
    @Json(name = "fcst_date")   val fcstDate: String?,     // 예보일자 YYYYMMDD
    @Json(name = "fcst_grade")  val fcstGrade: String?,    // 예보등급 A-E
    @Json(name = "fcst_value")  val fcstValue: Float?,     // 예보값 0-100
    @Json(name = "wt")          val waterTemp: String?,    // 수온 °C
    @Json(name = "at")          val airTemp: String?,      // 기온 °C
    @Json(name = "wh")          val waveHeight: String?,   // 파고 m
    @Json(name = "ws")          val windSpeed: String?,    // 풍속 m/s
    @Json(name = "wd")          val windDir: String?,      // 풍향 deg
    @Json(name = "ww")          val weather: String?       // 날씨
)
