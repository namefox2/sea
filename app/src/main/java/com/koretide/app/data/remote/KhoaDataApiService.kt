package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KhoaTideRecentResponse
import com.koretide.app.data.remote.dto.KhoaTideFcstResponse
import com.koretide.app.data.remote.dto.KhoaWindResponse
import com.koretide.app.data.remote.dto.KhoaWaveResponse
import retrofit2.http.GET
import retrofit2.http.Query

// All endpoints at https://apis.data.go.kr/1192136/
interface KhoaDataApiService {

    @GET("dtRecent/GetDTRecentApiService")
    suspend fun getTideRecent(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String,
        @Query("Date")       date: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("_type")      type: String = "json"
    ): KhoaTideRecentResponse

    @GET("tideFcstHghLw/GetTideFcstHghLwApiService")
    suspend fun getTideForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String,
        @Query("Date")       date: String,
        @Query("numOfRows")  numOfRows: Int = 20,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("_type")      type: String = "json"
    ): KhoaTideFcstResponse

    // ObsCode nullable: DT_xxxx 조위코드 ≠ 풍속관측망 코드 → null로 전체 조회 후 좌표로 최근접 선택
    @GET("surveyWind/GetSurveyWindApiService")
    suspend fun getWind(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String?,
        @Query("Date")       date: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("_type")      type: String = "json"
    ): KhoaWindResponse

    @GET("noonWave/GetNoonWaveApiService")
    suspend fun getWave(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode")    obsCode: String?,
        @Query("Date")       date: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("_type")      type: String = "json"
    ): KhoaWaveResponse
}
