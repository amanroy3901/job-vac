package com.sarkarimatch.feature.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.ad_label
import com.sarkarimatch.core.ads.AdHandle
import com.sarkarimatch.core.designsystem.theme.SmRadius
import org.jetbrains.compose.resources.stringResource

/**
 * The SDK's own native ad view for a loaded ad: Android `NativeAdView` in `AndroidView`, iOS `GADNativeAdView` from
 * the Swift `NativeAdViewFactory` in `UIKitView`. Never re-drawn in Compose (AdMob policy: the SDK must own the
 * registered asset views, AdChoices, impression and click measurement; ads.md §2).
 */
@Composable
expect fun PlatformNativeAdView(handle: AdHandle, modifier: Modifier = Modifier)

/** Feed slot chrome around the platform ad view (ads.md §4): sunken container, "विज्ञापन · Ad" pill. */
@Composable
fun NativeAdSlot(handle: AdHandle, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(SmRadius.xl))
            .padding(12.dp),
    ) {
        Text(
            stringResource(Res.string.ad_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PlatformNativeAdView(handle, Modifier.fillMaxWidth().heightIn(min = 120.dp))
    }
}
