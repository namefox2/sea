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

// odcloud.kr 조위관측소 목록 API 응답
// GET https://api.odcloud.kr/api/15146602/v1/uddi:81b0665b-...
@JsonClass(generateAdapter = true)
data class StationResponse(
    @Json(name = "currentCount") val currentCount: Int?,
    @Json(name = "data")         val data: List<OdCloudStationItem>?
)

@JsonClass(generateAdapter = true)
data class OdCloudStationItem(
    @Json(name = "조위관측소 고유번호") val code: String?,
    @Json(name = "조위관측소 명")     val name: String?,
    @Json(name = "조위관측소 영문명") val nameEn: String?,
    @Json(name = "조위관측소 위도")   val lat: String?,
    @Json(name = "조위관측소 경도")   val lon: String?,
    @Json(name = "관측소 유형")       val type: String?
)
