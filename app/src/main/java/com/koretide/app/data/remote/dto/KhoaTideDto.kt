package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KhoaTideCurrentResponse(
    @Json(name = "result") val result: KhoaTideCurrentResult?
)

@JsonClass(generateAdapter = true)
data class KhoaTideCurrentResult(
    @Json(name = "meta") val meta: KhoaTideMeta?,
    @Json(name = "data") val data: List<KhoaTideCurrentItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaTideMeta(
    @Json(name = "obsCode") val obsCode: String?,
    @Json(name = "obsName") val obsName: String?
)

@JsonClass(generateAdapter = true)
data class KhoaTideCurrentItem(
    @Json(name = "record_time") val recordTime: String?,
    @Json(name = "tide_level") val tideLevel: Int?
)

@JsonClass(generateAdapter = true)
data class KhoaTideTableResponse(
    @Json(name = "result") val result: KhoaTideTableResult?
)

@JsonClass(generateAdapter = true)
data class KhoaTideTableResult(
    @Json(name = "meta") val meta: KhoaTideMeta?,
    @Json(name = "data") val data: List<KhoaTideTableItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaTideTableItem(
    @Json(name = "tph_time") val tphTime: String?,
    @Json(name = "tph_level") val tphLevel: Int?,
    @Json(name = "hl_code") val hlCode: String?
)
