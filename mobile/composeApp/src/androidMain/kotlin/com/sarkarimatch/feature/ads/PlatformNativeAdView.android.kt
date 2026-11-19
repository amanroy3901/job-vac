package com.sarkarimatch.feature.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sarkarimatch.core.ads.AdHandle

/** FE-27: `AndroidView { NativeAdView(it) … }` from the GMA Next-Gen SDK. No SDK yet, so nothing is loaded. */
@Composable
actual fun PlatformNativeAdView(handle: AdHandle, modifier: Modifier) {
    Box(modifier)
}
