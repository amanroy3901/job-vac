package com.sarkarimatch.feature.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sarkarimatch.core.ads.AdHandle

/** Desktop dev target: no ad SDK. */
@Composable
actual fun PlatformNativeAdView(handle: AdHandle, modifier: Modifier) {
    Box(modifier)
}
