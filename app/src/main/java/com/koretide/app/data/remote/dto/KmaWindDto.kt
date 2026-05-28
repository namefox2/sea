package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KmaForecastResponse(
    @Json(name = "response") val response: KmaResponse?
)

@JsonClass(generateAdapter = true)
data class KmaResponse(
    @Json(name = "header") val header: KmaHeader?,
    @Json(name = "body") val body: KmaBody?
)

@JsonClass(generateAdapter = true)
data class KmaHeader(
    @Json(name = "resultCode") val resultCode: String?,
    @Json(name = "resultMsg") val resultMsg: String?
)

@JsonClass(generateAdapter = true)
data class KmaBody(
    @Json(name = "items") val items: KmaItems?,
    @Json(name = "pageNo") val pageNo: Int?,
    @Json(name = "numOfRows") val numOfRows: Int?,
    @Json(name = "totalCount") val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class KmaItems(
    @Json(name = "item") val item: List<KmaForecastItem>?
)

@JsonClass(generateAdapter = true)
data class KmaForecastItem(
    @Json(name = "baseDate") val baseDate: String?,
    @Json(name = "baseTime") val baseTime: String?,
    @Json(name = "category") val category: String?,
    @Json(name = "fcstValue") val fcstValue: String?
)
