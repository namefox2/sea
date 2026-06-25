package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KhoaTideRecentResponse
import com.koretide.app.data.remote.dto.KhoaTideFcstResponse
import com.koretide.app.data.remote.dto.KhoaWaveResponse
import retrofit2.http.GET
import retrofit2.http.Query

// All endpoints at https://apis.data.go.kr/1192136/
// 파라미터명: data.go.kr 표준 camelCase (obsCode, not ObsCode)
interface KhoaDataApiService {

    // dtRecent: 복합해양환경관측소 실시간 데이터 (조위 bscTdlvHgt + 풍향/풍속 포함)
    // obsCode nullable → null이면 전체 관측소 조회 후 좌표로 최근접 선택
    @GET("dtRecent/GetDTRecentApiService")
    suspend fun getTideRecent(
        @Query("serviceKey") serviceKey: String,
        @Query("obsCode")    obsCode: String?,
        @Query("reqDate")       date: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")      type: String = "json"
    ): KhoaTideRecentResponse

    @GET("tideFcstHghLw/GetTideFcstHghLwApiService")
    suspend fun getTideForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("obsCode")    obsCode: String?,
        @Query("reqDate")       date: String,
        @Query("numOfRows")  numOfRows: Int = 20,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")      type: String = "json"
    ): KhoaTideFcstResponse

    @GET("noonWave/GetNoonWaveApiService")
    suspend fun getWave(
        @Query("serviceKey") serviceKey: String,
        @Query("obsCode")    obsCode: String?,
        @Query("reqDate")       date: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")      type: String = "json"
    ): KhoaWaveResponse
}
