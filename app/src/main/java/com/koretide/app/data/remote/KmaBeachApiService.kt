package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KmaBeachResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface KmaBeachApiService {
    @GET("getBeachFrcst")
    suspend fun getBeachForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("dataType")   dataType: String = "JSON",
        @Query("baseDate")   baseDate: String
    ): KmaBeachResponse
}
