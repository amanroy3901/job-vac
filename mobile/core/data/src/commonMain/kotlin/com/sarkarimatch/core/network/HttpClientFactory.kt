package com.sarkarimatch.core.network

import com.sarkarimatch.core.data.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Lenient JSON shared by the API client and the catalog parser. */
val SmJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * The one HttpClient. [engine] is platform-specific (OkHttp on Android, Darwin/NSURLSession on iOS, MockEngine in
 * tests). Timeouts are tuned for 2G/3G (data-layer.md §2). [languageTag] is read per request so `Accept-Language`
 * follows the app language.
 */
fun createHttpClient(engine: HttpClientEngine, config: AppConfig, languageTag: () -> String): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(SmJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
        if (config.httpLogging) {
            install(Logging) { level = LogLevel.INFO }
        }
        defaultRequest {
            url(config.apiBaseUrl)
            header("X-App-Version", config.versionCode.toString())
            header(HttpHeaders.AcceptLanguage, languageTag())
        }
    }
