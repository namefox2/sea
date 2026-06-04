package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KmaBeachResponse
import retrofit2.http.GET
import retrofit2.http.Query

// 모든 해양 활동 지수 서비스가 동일한 키, 동일한 응답 구조를 사용
interface KmaBeachApiService {

    @GET("BeachFrcstInfoService/getBeachFrcst")
    suspend fun getBeachForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("dataType")   dataType: String = "JSON",
        @Query("baseDate")   baseDate: String
    ): KmaBeachResponse

    @GET("MarFshFrcstInfoService/getMarFshFrcst")
    suspend fun getFishingForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("dataType")   dataType: String = "JSON",
        @Query("baseDate")   baseDate: String
    ): KmaBeachResponse

    @GET("SeaSickFrcstInfoService/getSeaSickFrcst")
    suspend fun getSeasicknessForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("dataType")   dataType: String = "JSON",
        @Query("baseDate")   baseDate: String
    ): KmaBeachResponse

    @GET("SkinFrcstInfoService/getSkinFrcst")
    suspend fun getScubaForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("dataType")   dataType: String = "JSON",
        @Query("baseDate")   baseDate: String
    ): KmaBeachResponse

    @GET("TdlFltFrcstInfoService/getTdlFltFrcst")
    suspend fun getTidalFlatForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("dataType")   dataType: String = "JSON",
        @Query("baseDate")   baseDate: String
    ): KmaBeachResponse

    @GET("SrfFrcstInfoService/getSrfFrcst")
    suspend fun getSurfingForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("dataType")   dataType: String = "JSON",
        @Query("baseDate")   baseDate: String
    ): KmaBeachResponse

    @GET("MarTripFrcstInfoService/getMarTripFrcst")
    suspend fun getSeaTravelForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("dataType")   dataType: String = "JSON",
        @Query("baseDate")   baseDate: String
    ): KmaBeachResponse
}
