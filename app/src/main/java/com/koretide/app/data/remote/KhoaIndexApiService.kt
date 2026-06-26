package com.koretide.app.data.remote

import com.koretide.app.data.remote.dto.KhoaIndexResponse
import retrofit2.http.GET
import retrofit2.http.Query

// v2 활동지수 엔드포인트: {service}/Get{Name}ApiServicev2
// 파라미터: reqDate (Date→reqDate), type (_type→type), placeCode (obsCode→placeCode)
interface KhoaIndexApiService {

    @GET("fcstBeachv2/GetFcstBeachApiServicev2")
    suspend fun getBeachForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("placeCode")  placeCode: String?,
        @Query("reqDate")    reqDate: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstFishingv2/GetFcstFishingApiServicev2")
    suspend fun getFishingForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("placeCode")  placeCode: String?,
        @Query("reqDate")    reqDate: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstSicknessv2/GetFcstSicknessApiServicev2")
    suspend fun getSeasicknessForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("placeCode")  placeCode: String?,
        @Query("reqDate")    reqDate: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstSkinScubav2/GetFcstSkinScubaApiServicev2")
    suspend fun getScubaForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("placeCode")  placeCode: String?,
        @Query("reqDate")    reqDate: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstMudflatv2/GetFcstMudflatApiServicev2")
    suspend fun getTidalFlatForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("placeCode")  placeCode: String?,
        @Query("reqDate")    reqDate: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstSurfingv2/GetFcstSurfingApiServicev2")
    suspend fun getSurfingForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("placeCode")  placeCode: String?,
        @Query("reqDate")    reqDate: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json"
    ): KhoaIndexResponse

    @GET("fcstSeaTripv2/GetFcstSeaTripApiServicev2")
    suspend fun getSeaTravelForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("placeCode")  placeCode: String?,
        @Query("reqDate")    reqDate: String,
        @Query("numOfRows")  numOfRows: Int = 100,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("type")       type: String = "json"
    ): KhoaIndexResponse
}
