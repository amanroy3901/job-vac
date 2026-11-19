package com.sarkarimatch.app

import androidx.compose.ui.window.ComposeUIViewController
import com.sarkarimatch.app.di.initKoin
import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.Platform
import com.sarkarimatch.core.data.di.iosDataModule
import com.sarkarimatch.core.platform.IosPlatformBridges
import org.koin.mp.KoinPlatformTools
import platform.UIKit.UIViewController

/**
 * Entry point for iosApp (Swift: `MainViewControllerKt.MainViewController(bridges:apiBaseUrl:versionName:versionCode:debug:)`).
 * Starts Koin on first use with the Swift bridges (StoreKit 2, Google/Apple sign-in, FCM, AdMob) and hosts [App].
 */
@Suppress("FunctionName", "ktlint:standard:function-naming") // Swift-facing factory, named like the type it returns
fun MainViewController(
    bridges: IosPlatformBridges,
    apiBaseUrl: String,
    versionName: String,
    versionCode: Int,
    debug: Boolean,
): UIViewController {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin(
            config = AppConfig(Platform.IOS, apiBaseUrl, versionName, versionCode, httpLogging = debug),
            platformModule = iosDataModule(bridges),
        )
    }
    return ComposeUIViewController { App() }
}
