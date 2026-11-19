package com.sarkarimatch.app.di

import com.sarkarimatch.app.BuildConfig
import com.sarkarimatch.core.network.ApiBaseUrl
import com.sarkarimatch.core.network.AppVersionName
import com.sarkarimatch.core.network.HttpLogging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Build-time values the core modules need, from this app's BuildConfig (`env` flavour + build type). */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @ApiBaseUrl
    fun apiBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @AppVersionName
    fun appVersion(): String = BuildConfig.VERSION_NAME

    @Provides
    @HttpLogging
    fun httpLogging(): Boolean = BuildConfig.DEBUG
}
