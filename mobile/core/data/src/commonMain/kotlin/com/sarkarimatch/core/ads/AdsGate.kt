package com.sarkarimatch.core.ads

import javax.inject.Inject

/**
 * Decides whether the single native ad slot may be shown (ads.md §3): free tier only, never in minor mode, only
 * after UMP consent, and only while `config.ads` is on. The GMA Next-Gen SDK is lazily initialised on first
 * scroll-in (FE-27); until then the slot is never shown.
 */
class AdsGate @Inject constructor() {
    fun canShowAd(isPro: Boolean, isMinor: Boolean, configAdsOn: Boolean): Boolean =
        SDK_INTEGRATED && !isPro && !isMinor && configAdsOn

    private companion object {
        const val SDK_INTEGRATED = false
    }
}
