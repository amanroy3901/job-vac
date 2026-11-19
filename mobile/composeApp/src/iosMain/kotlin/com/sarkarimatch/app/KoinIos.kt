package com.sarkarimatch.app

import com.sarkarimatch.app.di.initKoin
import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.Platform
import com.sarkarimatch.core.data.di.iosDataModule
import com.sarkarimatch.core.platform.IosPlatformBridges
import org.koin.mp.KoinPlatformTools

/**
 * Starts the shared graph once per process. Swift calls it from `AppDelegate.application(_:didFinishLaunchingWithOptions:)`
 * (`KoinIosKt.startSmIfNeeded(bridges:apiBaseUrl:versionName:versionCode:debug:)`) — before any UI exists, so a
 * background launch (notification action, background push, BGTask) has Koin too (platform-interop.md §2a).
 * Idempotent: later calls are ignored.
 */
fun startSmIfNeeded(
    bridges: IosPlatformBridges,
    apiBaseUrl: String,
    versionName: String,
    versionCode: Int,
    debug: Boolean,
) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    initKoin(
        config = AppConfig(Platform.IOS, apiBaseUrl, versionName, versionCode, httpLogging = debug),
        platformModule = iosDataModule(bridges),
    )
}

/** Whether [startSmIfNeeded] has run (MainViewController checks it). */
fun isSmStarted(): Boolean = KoinPlatformTools.defaultContext().getOrNull() != null
