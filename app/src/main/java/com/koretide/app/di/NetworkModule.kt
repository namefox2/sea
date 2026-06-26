package com.koretide.app.di

import android.util.Log
import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KasiApiService
import com.koretide.app.data.remote.KhoaDataApiService
import com.koretide.app.data.remote.KhoaIndexApiService
import com.koretide.app.data.remote.OdCloudApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KhoaRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OdCloudRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KasiRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        })
        .addInterceptor { chain ->
            val request  = chain.request()
            val response = chain.proceed(request)
            val raw = response.body?.string() ?: ""
            val text = raw.trimStart('﻿').trim()

            // data.go.kr KHOA/KASI API는 XML 반환 → JSON으로 변환
            // KASI(RiseSetInfoService)는 시간 값("1957" 등)을 항상 문자열로 유지해야 함
            val isKasi = request.url.toString().contains("RiseSetInfoService")
            val json = if (text.startsWith("<")) {
                if (isKasi) kasiXmlToJson(text) else khoaXmlToJson(text)
            } else text

            if (BuildConfig.DEBUG && json.isNotEmpty() && !json.startsWith("{") && !json.startsWith("[")) {
                Log.w("RawResponse", "${chain.request().url} → ${json.take(400)}")
            }

            val jsonType = "application/json; charset=utf-8".toMediaTypeOrNull()
            response.newBuilder().body(json.toResponseBody(jsonType)).build()
        }
        .build()

    @Provides
    @Singleton
    @KhoaRetrofit
    fun provideKhoaRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://apis.data.go.kr/1192136/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    @OdCloudRetrofit
    fun provideOdCloudRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.odcloud.kr/api/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideKhoaDataApiService(@KhoaRetrofit retrofit: Retrofit): KhoaDataApiService =
        retrofit.create(KhoaDataApiService::class.java)

    @Provides
    @Singleton
    fun provideKhoaIndexApiService(@KhoaRetrofit retrofit: Retrofit): KhoaIndexApiService =
        retrofit.create(KhoaIndexApiService::class.java)

    @Provides
    @Singleton
    fun provideOdCloudApi(@OdCloudRetrofit retrofit: Retrofit): OdCloudApi =
        retrofit.create(OdCloudApi::class.java)

    @Provides
    @Singleton
    @KasiRetrofit
    fun provideKasiRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://apis.data.go.kr/B090041/openapi/service/RiseSetInfoService/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideKasiApiService(@KasiRetrofit retrofit: Retrofit): KasiApiService =
        retrofit.create(KasiApiService::class.java)

    // 공통 XML 파싱: <response><header>…</header><body><items><item>…</item></items></body></response>
    // valueSerializer 로 값 직렬화 방식을 주입받아 KHOA/KASI 양쪽에서 재사용
    private fun xmlToJson(xml: String, valueSerializer: (String?) -> String): String = try {
        val parser = android.util.Xml.newPullParser()
        parser.setInput(xml.reader())

        val header  = LinkedHashMap<String, String?>()
        val bodyExt = LinkedHashMap<String, String?>()
        val items   = mutableListOf<LinkedHashMap<String, String?>>()
        var curItem: LinkedHashMap<String, String?>? = null
        val path    = ArrayDeque<String>()
        var curText = StringBuilder()

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    path.addLast(parser.name)
                    curText = StringBuilder()
                    if (parser.name == "item") curItem = LinkedHashMap()
                }
                XmlPullParser.CDSECT,
                XmlPullParser.TEXT -> curText.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val name   = parser.name
                    val text   = curText.toString().trim()
                    path.removeLastOrNull()
                    val parent = path.lastOrNull()
                    when {
                        name == "item" -> { curItem?.let { items.add(it) }; curItem = null }
                        curItem != null && parent == "item" -> curItem!![name] = text.ifEmpty { null }
                        parent == "header"                  -> header[name]   = text.ifEmpty { null }
                        parent == "body" && name != "items" -> bodyExt[name]  = text.ifEmpty { null }
                    }
                    curText = StringBuilder()
                }
            }
            ev = parser.next()
        }

        buildString {
            append("{\"header\":{")
            header.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) append(',')
                append("\"$k\":${valueSerializer(v)}")
            }
            append("},\"body\":{\"items\":{\"item\":[")
            items.forEachIndexed { i, item ->
                if (i > 0) append(',')
                append('{')
                item.entries.forEachIndexed { j, (k, v) ->
                    if (j > 0) append(',')
                    append("\"$k\":${valueSerializer(v)}")
                }
                append('}')
            }
            append("]}")
            bodyExt.forEach { (k, v) -> append(",\"$k\":${valueSerializer(v)}") }
            append("}}")
        }
    } catch (e: Exception) {
        Log.e("XmlConvert", "failed: ${e.message}")
        "{}"
    }

    // KHOA: 숫자는 JSON number 로, 선행 0 코드값은 문자열로
    private fun khoaXmlToJson(xml: String) = xmlToJson(xml) { toJsonVal(it) }

    // KASI: 모든 값을 문자열로 유지 (일출/일몰 "1957" 등이 숫자로 변환되면 Moshi 파싱 실패)
    private fun kasiXmlToJson(xml: String) = xmlToJson(xml) { s ->
        if (s == null) "null"
        else "\"${s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")}\""
    }

    private fun toJsonVal(s: String?): String {
        if (s == null) return "null"
        // "00", "01" 같은 코드값은 숫자로 변환 안 함
        if (s.length >= 2 && s.startsWith("0") && s[1].isDigit()) return "\"$s\""
        val d = s.toDoubleOrNull()
        if (d != null && !d.isInfinite() && !d.isNaN()) {
            return if (d == kotlin.math.floor(d) && d > -1e15 && d < 1e15) d.toLong().toString()
            else d.toString()
        }
        val esc = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
        return "\"$esc\""
    }
}
