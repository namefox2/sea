package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KhoaTideRecentResponse
import com.koretide.app.data.remote.dto.KhoaTideFcstResponse
import com.koretide.app.data.remote.dto.KhoaWindResponse
import com.koretide.app.data.remote.dto.KhoaWaveResponse
import retrofit2.http.GET
import retrofit2.http.Query

// All endpoints at https://apis.data.go.kr/1192136/
interface KhoaDataApiService {

    @GET("dtRecent")
    suspend fun getTideRecent(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaTideRecentResponse

    @GET("tideFcstHghLw")
    suspend fun getTideForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaTideFcstResponse

    @GET("surveyWind")
    suspend fun getWind(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaWindResponse

    @GET("noonWave")
    suspend fun getWave(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String,
        @Query("Date")       date: String,
        @Query("_type")      type: String = "json"
    ): KhoaWaveResponse
}
