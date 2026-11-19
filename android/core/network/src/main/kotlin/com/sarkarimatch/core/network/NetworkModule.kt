package com.sarkarimatch.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Base URL from the app's BuildConfig (`env` flavour: prod / staging), bound in :app. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl

/** App version name for `X-App-Version`, bound in :app. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppVersionName

/** Whether HTTP logging is on (debug builds only), bound in :app. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HttpLogging

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttp(@AppVersionName version: String, @HttpLogging logging: Boolean): OkHttpClient {
        val headers = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("X-App-Version", version)
                    .header("Accept-Language", Locale.getDefault().toLanguageTag())
                    .build(),
            )
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS) // tuned for 2G/3G (data-layer.md §2)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(headers)
            .apply {
                if (logging) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
            .build()
    }

    @Provides
    @Singleton
    fun api(@ApiBaseUrl baseUrl: String, client: OkHttpClient, json: Json): SmApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SmApi::class.java)
}
