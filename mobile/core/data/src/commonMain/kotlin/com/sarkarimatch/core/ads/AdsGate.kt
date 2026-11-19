package com.sarkarimatch.core.ads

/**
 * Decides whether the single native ad slot may be shown (ads.md §3): free tier only, never in minor mode, only
 * after consent is resolved (Google UMP on both platforms; no App Tracking Transparency prompt on iOS, ads.md §7),
 * and only while `config.ads` is on. The AdMob SDK is lazily initialised on first scroll-in (FE-27).
 */
class AdsGate(private val ads: AdsService) {
    fun canShowAd(isPro: Boolean, isMinor: Boolean, configAdsOn: Boolean): Boolean =
        ads.isAvailable && !isPro && !isMinor && configAdsOn
}

/** Opaque id of a loaded native ad held by the platform SDK (no SDK types in common code). */
data class AdHandle(val id: String)

sealed interface AdLoadResult {
    data class Loaded(val handle: AdHandle) : AdLoadResult
    data object NoFill : AdLoadResult
    data class Error(val message: String) : AdLoadResult
}

/**
 * AdMob native ads (Google Mobile Ads Next-Gen SDK on Android, Google Mobile Ads iOS SDK from Swift). The ad is
 * always rendered by the SDK's registered native ad view (`NativeAdView` / `GADNativeAdView`, with AdChoices), hosted
 * in Compose through `AndroidView` / `UIKitView` (`PlatformNativeAdView` in :composeApp); the SDK measures impressions
 * and clicks itself. Interface only in this skeleton; the SDKs are integrated with FE-27 / FE-76.
 */
interface AdsService {
    val isAvailable: Boolean

    /** UMP consent info update (+ form only when required); returns whether ads may be requested. */
    suspend fun ensureConsent(): Boolean

    suspend fun load(adUnitId: String): AdLoadResult

    /** Destroys the SDK ad object when the slot leaves composition. */
    fun release(handle: AdHandle)
}

class NoAdsService : AdsService {
    override val isAvailable: Boolean = false
    override suspend fun ensureConsent(): Boolean = false
    override suspend fun load(adUnitId: String): AdLoadResult = AdLoadResult.NoFill
    override fun release(handle: AdHandle) = Unit
}
