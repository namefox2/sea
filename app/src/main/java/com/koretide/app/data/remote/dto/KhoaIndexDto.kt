package com.koretide.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KhoaIndexResponse(
    @Json(name = "header") val header: KhoaHeader?,
    @Json(name = "body")   val body: KhoaIndexBody?
)

@JsonClass(generateAdapter = true)
data class KhoaIndexBody(
    @Json(name = "items")      val items: KhoaIndexItems?,
    @Json(name = "totalCount") val totalCount: Int?
)

@JsonClass(generateAdapter = true)
data class KhoaIndexItems(
    @Json(name = "item") val item: List<KhoaIndexItem>?
)

@JsonClass(generateAdapter = true)
data class KhoaIndexItem(
    @Json(name = "bbchNm")        val bbchNm: String?,        // 해수욕장명
    @Json(name = "seafsPstnNm")   val seafsPstnNm: String?,   // 바다낚시 지점명
    @Json(name = "nvgtNm")        val nvgtNm: String?,        // 뱃멀미 운항 노선명 (예: 인천-백령)
    @Json(name = "lat")           val lat: Double?,           // 위도 (뱃멀미는 응답에 없음)
    @Json(name = "lot")           val lot: Double?,           // 경도 (뱃멀미는 응답에 없음)
    @Json(name = "predcYmd")      val predcYmd: String?,      // 예보일자 (yyyy-MM-dd)
    @Json(name = "predcNoonSeCd") val predcNoonSeCd: String?, // 오전/오후
    @Json(name = "totalIndex")    val totalIndex: String?,    // 종합지수 (매우좋음/좋음/보통/나쁨/매우나쁨)
    @Json(name = "maxWvhgt")      val maxWvhgt: String?,      // 최대파고 m
    @Json(name = "avgWtem")       val avgWtem: String?,       // 평균수온 °C
    @Json(name = "avgArtmp")      val avgArtmp: String?,      // 평균기온 °C
    @Json(name = "maxWspd")       val maxWspd: String?,       // 최대풍속 m/s
    @Json(name = "opnStat")       val opnStat: String?        // 개장상태 (개장/폐장/비개장)
)
