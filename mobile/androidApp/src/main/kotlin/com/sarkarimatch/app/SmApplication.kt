package com.sarkarimatch.app

import android.app.Application
import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.Platform

class SmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startSarkariMatch(
            config = AppConfig(
                platform = Platform.ANDROID,
                apiBaseUrl = BuildConfig.API_BASE_URL,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                httpLogging = BuildConfig.DEBUG,
            ),
            googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID.ifBlank { null },
        )
    }
}
