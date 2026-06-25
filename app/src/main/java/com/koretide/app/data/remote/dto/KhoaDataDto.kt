package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Shared meta wrapper used across all new KHOA data APIs
@JsonClass(generateAdapter = true)
data class KhoaDataMeta(
    @Json(name = "totalCount") val totalCount: Int?
)

// ── dtRecent: 실시간 조위 관측 ─────────────────────────────────────────────────
@JsonClass(generateAdapter = true)
data class KhoaTideRecentResponse(
    @Json(name = "result") val result: KhoaTideRecentResult?
)

@JsonClass(generateAdapter = true)
data class KhoaTideRecentResult(
    @Json(name = "meta") val meta: KhoaDataMeta?,
    @Json(name = "data") val data: List<KhoaTideRecentItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaTideRecentItem(
    @Json(name = "record_time") val recordTime: String?,
    @Json(name = "tide_level")  val tideLevel: Int?
)

// ── tideFcstHghLw: 고저조 예보 ────────────────────────────────────────────────
@JsonClass(generateAdapter = true)
data class KhoaTideFcstResponse(
    @Json(name = "result") val result: KhoaTideFcstResult?
)

@JsonClass(generateAdapter = true)
data class KhoaTideFcstResult(
    @Json(name = "meta") val meta: KhoaDataMeta?,
    @Json(name = "data") val data: List<KhoaTideFcstItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaTideFcstItem(
    @Json(name = "tph_time")  val tphTime: String?,
    @Json(name = "tph_level") val tphLevel: Int?,
    @Json(name = "hl_code")   val hlCode: String?
)

// ── surveyWind: 풍향/풍속 관측 ────────────────────────────────────────────────
@JsonClass(generateAdapter = true)
data class KhoaWindResponse(
    @Json(name = "result") val result: KhoaWindResult?
)

@JsonClass(generateAdapter = true)
data class KhoaWindResult(
    @Json(name = "meta") val meta: KhoaDataMeta?,
    @Json(name = "data") val data: List<KhoaWindItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaWindItem(
    @Json(name = "record_time") val recordTime: String?,
    @Json(name = "wind_speed")  val windSpeed: Float?,
    @Json(name = "wind_dir")    val windDir: Float?
)

// ── noonWave: 실측파랑 ────────────────────────────────────────────────────────
@JsonClass(generateAdapter = true)
data class KhoaWaveResponse(
    @Json(name = "result") val result: KhoaWaveResult?
)

@JsonClass(generateAdapter = true)
data class KhoaWaveResult(
    @Json(name = "meta") val meta: KhoaDataMeta?,
    @Json(name = "data") val data: List<KhoaWaveItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaWaveItem(
    @Json(name = "record_time") val recordTime: String?,
    @Json(name = "wave_height") val waveHeight: Float?,
    @Json(name = "wave_period") val wavePeriod: Float?,
    @Json(name = "wave_dir")    val waveDir: Float?
)
