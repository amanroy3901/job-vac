package com.sarkarimatch.feature.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.sarkarimatch.core.ads.AdHandle
import com.sarkarimatch.core.platform.IosPlatformBridges
import org.koin.compose.koinInject

/** Hosts the Swift `GADNativeAdView` for [handle] (IosAdsBridge.makeNativeAdView). */
@Composable
actual fun PlatformNativeAdView(handle: AdHandle, modifier: Modifier) {
    val ads = koinInject<IosPlatformBridges>().ads
    if (ads == null) {
        Box(modifier)
        return
    }
    UIKitView(factory = { ads.makeNativeAdView(handle.id) }, modifier = modifier)
}
