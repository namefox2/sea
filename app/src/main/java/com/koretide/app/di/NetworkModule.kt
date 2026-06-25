package com.koretide.app.di

import android.util.Log
import com.koretide.app.BuildConfig
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
            val response = chain.proceed(chain.request())
            val raw = response.body?.string() ?: ""
            val text = raw.trimStart('﻿').trim()

            // data.go.kr KHOA API는 _type=json 무시하고 XML 반환 → JSON으로 변환
            val json = if (text.startsWith("<")) khoaXmlToJson(text) else text

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

    // KHOA data.go.kr XML 응답 → JSON 변환
    // 구조: <response><header>...</header><body><items><item>...</item></items></body></response>
    private fun khoaXmlToJson(xml: String): String = try {
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
                append("\"$k\":${toJsonVal(v)}")
            }
            append("},\"body\":{\"items\":{\"item\":[")
            items.forEachIndexed { i, item ->
                if (i > 0) append(',')
                append('{')
                item.entries.forEachIndexed { j, (k, v) ->
                    if (j > 0) append(',')
                    append("\"$k\":${toJsonVal(v)}")
                }
                append('}')
            }
            append("]}")
            bodyExt.forEach { (k, v) -> append(",\"$k\":${toJsonVal(v)}") }
            append("}}")
        }
    } catch (e: Exception) {
        Log.e("XmlConvert", "failed: ${e.message}")
        "{}"
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
