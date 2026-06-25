package com.koretide.app.di

import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KhoaDataApiService
import com.koretide.app.data.remote.KhoaIndexApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KhoaRetrofit

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
            // 릴리스 빌드에서는 로깅 완전 비활성 — 개인정보보호법·GDPR 준수
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        })
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
    fun provideKhoaDataApiService(@KhoaRetrofit retrofit: Retrofit): KhoaDataApiService =
        retrofit.create(KhoaDataApiService::class.java)

    @Provides
    @Singleton
    fun provideKhoaIndexApiService(@KhoaRetrofit retrofit: Retrofit): KhoaIndexApiService =
        retrofit.create(KhoaIndexApiService::class.java)
}
