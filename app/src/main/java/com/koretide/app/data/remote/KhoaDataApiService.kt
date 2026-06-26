package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KhoaTideRecentResponse
import com.koretide.app.data.remote.dto.KhoaTideFcstResponse
import com.koretide.app.data.remote.dto.KhoaWaveResponse
import com.koretide.app.data.remote.dto.StationResponse
import retrofit2.http.GET
import retrofit2.http.Query

// All endpoints at https://apis.data.go.kr/1192136/
// 파라미터명: data.go.kr 표준 camelCase (obsCode, not ObsCode)
interface KhoaDataApiService {

    // dtRecent: 복합해양환경관측소 실시간 데이터 (조위 bscTdlvHgt + 풍향/풍속 포함)
    // obsCode: 특정 관측소 코드, null이면 전체 관측소 조회
    // include: 응답에 포함할 필드만 지정 (예: "lat,lot,bscTdlvHgt") → 불필요한 필드 제외로 속도 향상
    @GET("dtRecent/GetDTRecentApiService")
    suspend fun getTideRecent(
        @Query("serviceKey") serviceKey: String,
        @Query("obsCode")    obsCode: String?,
        @Query("reqDate")    date: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json",
        @Query("include")    include: String? = null
    ): KhoaTideRecentResponse

    @GET("tideFcstHghLw/GetTideFcstHghLwApiService")
    suspend fun getTideForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("obsCode")    obsCode: String?,
        @Query("reqDate")    date: String,
        @Query("numOfRows")  numOfRows: Int = 20,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json",
        @Query("include")    include: String? = null
    ): KhoaTideFcstResponse

    @GET("noonWave/GetNoonWaveApiService")
    suspend fun getWave(
        @Query("serviceKey") serviceKey: String,
        @Query("obsCode")    obsCode: String?,
        @Query("reqDate")    date: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json"
    ): KhoaWaveResponse
}

// odcloud.kr 조위관측소 목록 API (baseUrl: https://api.odcloud.kr/api/)
interface OdCloudApi {

    @GET("15146602/v1/uddi:81b0665b-4f21-41e8-91f1-d3ecc4a7a3f1")
    suspend fun getStations(
        @Query("page")        page: Int = 1,
        @Query("perPage")     perPage: Int = 200,
        @Query("returnType")  returnType: String = "json",
        @Query("serviceKey")  serviceKey: String
    ): StationResponse
}