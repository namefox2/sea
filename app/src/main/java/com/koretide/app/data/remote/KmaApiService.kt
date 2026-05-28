package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KmaForecastResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface KmaApiService {
    @GET("getVilageFcst")
    suspend fun getVillageForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("dataType") dataType: String = "JSON",
        @Query("base_date") baseDate: String,
        @Query("base_time") baseTime: String,
        @Query("nx") nx: Int,
        @Query("ny") ny: Int
    ): KmaForecastResponse
}
