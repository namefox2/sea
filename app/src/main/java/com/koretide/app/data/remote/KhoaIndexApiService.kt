package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KhoaIndexResponse
import retrofit2.http.GET
import retrofit2.http.Query

// All v2 activity index endpoints at https://apis.data.go.kr/1192136/
interface KhoaIndexApiService {

    @GET("fcstBeachv2")
    suspend fun getBeachForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String?,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstFishingv2")
    suspend fun getFishingForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String?,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstSicknessv2")
    suspend fun getSeasicknessForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String?,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstSkinScubav2")
    suspend fun getScubaForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String?,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstMudflatv2")
    suspend fun getTidalFlatForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String?,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstSurfingv2")
    suspend fun getSurfingForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String?,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstSeaTripv2")
    suspend fun getSeaTravelForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String?,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaIndexResponse
}
