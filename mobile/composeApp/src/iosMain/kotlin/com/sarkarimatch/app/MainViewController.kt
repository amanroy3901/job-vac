package com.sarkarimatch.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The Compose UI for iosApp (Swift: `MainViewControllerKt.MainViewController()`, wrapped in a
 * UIViewControllerRepresentable). [startSmIfNeeded] must have run first (AppDelegate does it at launch).
 */
@Suppress("FunctionName") // Swift-facing factory, named like the type it returns
fun MainViewController(): UIViewController {
    check(isSmStarted()) { "call KoinIosKt.startSmIfNeeded(...) in AppDelegate.didFinishLaunching first" }
    return ComposeUIViewController { App() }
}
