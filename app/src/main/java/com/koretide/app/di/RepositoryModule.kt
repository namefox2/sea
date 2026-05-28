package com.koretide.app.di

import com.koretide.app.data.repository.StationRepositoryImpl
import com.koretide.app.data.repository.TideRepositoryImpl
import com.koretide.app.data.repository.WeatherRepositoryImpl
import com.koretide.app.domain.repository.StationRepository
import com.koretide.app.domain.repository.TideRepository
import com.koretide.app.domain.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStationRepository(impl: StationRepositoryImpl): StationRepository

    @Binds
    @Singleton
    abstract fun bindTideRepository(impl: TideRepositoryImpl): TideRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository
}
