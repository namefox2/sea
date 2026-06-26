package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KasiRiseSetResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface KasiApiService {

    // 위도/경도 기반 일출·일몰 시각 조회
    @GET("getLCRiseSetInfo")
    suspend fun getRiseSetByCoords(
        @Query("ServiceKey") serviceKey: String,
        @Query("locdate")    locdate: String,     // YYYYMMDD
        @Query("longitude")  longitude: String,   // decimal degrees, e.g. "129.0182"
        @Query("latitude")   latitude: String,    // decimal degrees, e.g. "37.6880"
        @Query("dnYn")       dnYn: String = "Y"   // Y = decimal input
    ): KasiRiseSetResponse
}
