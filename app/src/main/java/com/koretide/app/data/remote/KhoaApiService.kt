package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KhoaStationListResponse
import com.koretide.app.data.remote.dto.KhoaTideCurrentResponse
import com.koretide.app.data.remote.dto.KhoaTideTableResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface KhoaApiService {
    @GET("tideObsStationList")
    suspend fun getStationList(
        @Query("ServiceKey") serviceKey: String,
        @Query("type") type: String = "json"
    ): KhoaStationListResponse

    @GET("tideCurPre")
    suspend fun getCurrentTide(
        @Query("ServiceKey") serviceKey: String,
        @Query("ObsCode") obsCode: String,
        @Query("Date") date: String,
        @Query("type") type: String = "json"
    ): KhoaTideCurrentResponse

    @GET("tideObsPreTab")
    suspend fun getTideTable(
        @Query("ServiceKey") serviceKey: String,
        @Query("ObsCode") obsCode: String,
        @Query("Date") date: String,
        @Query("type") type: String = "json"
    ): KhoaTideTableResponse
}
