package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KasiRiseSetResponse(
    @Json(name = "header") val header: KasiHeader?,
    @Json(name = "body")   val body: KasiBody?
)

@JsonClass(generateAdapter = true)
data class KasiHeader(
    @Json(name = "resultCode") val resultCode: String?,
    @Json(name = "resultMsg")  val resultMsg: String?
)

@JsonClass(generateAdapter = true)
data class KasiBody(
    @Json(name = "items") val items: KasiItems?
)

@JsonClass(generateAdapter = true)
data class KasiItems(
    @Json(name = "item") val item: List<KasiRiseSetItem>?
)

@JsonClass(generateAdapter = true)
data class KasiRiseSetItem(
    @Json(name = "locdate")    val locdate: String?,    // YYYYMMDD
    @Json(name = "sunrise")    val sunrise: String?,    // HHMMSS
    @Json(name = "sunset")     val sunset: String?,     // HHMMSS
    @Json(name = "suntransit") val suntransit: String?, // solar noon HHMMSS
    @Json(name = "civilm")     val civilm: String?,     // civil twilight morning
    @Json(name = "civile")     val civile: String?,     // civil twilight evening
    @Json(name = "moonrise")   val moonrise: String?,
    @Json(name = "moonset")    val moonset: String?
)
