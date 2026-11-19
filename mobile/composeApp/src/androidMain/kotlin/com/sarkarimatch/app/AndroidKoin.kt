package com.sarkarimatch.app

import android.app.Application
import com.sarkarimatch.app.di.initKoin
import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.di.androidDataModule
import org.koin.android.ext.koin.androidContext

/** Called from the Android Application.onCreate (:androidApp), which owns BuildConfig. */
fun Application.startSarkariMatch(config: AppConfig, googleServerClientId: String?) {
    initKoin(config, androidDataModule(googleServerClientId)) {
        androidContext(this@startSarkariMatch)
    }
}
