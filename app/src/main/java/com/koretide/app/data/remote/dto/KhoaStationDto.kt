package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KhoaStationListResponse(
    @Json(name = "result") val result: KhoaStationResult?
)

@JsonClass(generateAdapter = true)
data class KhoaStationResult(
    @Json(name = "meta") val meta: KhoaMeta?,
    @Json(name = "data") val data: List<KhoaStationItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaMeta(
    @Json(name = "totalCount") val totalCount: Int?,
    @Json(name = "tideObsCount") val tideObsCount: Int?
)

@JsonClass(generateAdapter = true)
data class KhoaStationItem(
    @Json(name = "obsCode") val obsCode: String?,
    @Json(name = "obsName") val obsName: String?,
    @Json(name = "lat") val lat: String?,
    @Json(name = "lon") val lon: String?,
    @Json(name = "addressKo") val addressKo: String?
)
