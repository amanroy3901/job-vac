package com.sarkarimatch.core.ads

/**
 * Decides whether the single native ad slot may be shown (ads.md §3): free tier only, never in minor mode, only
 * after consent (UMP on Android, UMP + App Tracking Transparency prompt on iOS), and only while `config.ads` is on.
 * The AdMob SDK is lazily initialised on first scroll-in (FE-27); until then the slot is never shown.
 */
class AdsGate(private val ads: AdsService) {
    fun canShowAd(isPro: Boolean, isMinor: Boolean, configAdsOn: Boolean): Boolean =
        ads.isAvailable && !isPro && !isMinor && configAdsOn
}

/** A loaded native ad's assets; the slot is drawn by our own Compose layout (ads.md §4). */
data class NativeAdContent(
    val id: String,
    val headline: String,
    val body: String?,
    val callToAction: String?,
    val advertiser: String?,
)

/**
 * AdMob native ads on both platforms (Google Mobile Ads Next-Gen SDK on Android, Google Mobile Ads iOS SDK from
 * Swift). Interface only in this skeleton; the SDKs are integrated with FE-27.
 */
interface AdsService {
    val isAvailable: Boolean

    /** Shows the consent form when required; returns whether ads may be requested. */
    suspend fun ensureConsent(): Boolean

    suspend fun loadNativeAd(adUnitId: String): NativeAdContent?

    /** Records an impression / click for a loaded ad (the SDK does the reporting). */
    fun recordImpression(adId: String)

    fun recordClick(adId: String)
}

class NoAdsService : AdsService {
    override val isAvailable: Boolean = false
    override suspend fun ensureConsent(): Boolean = false
    override suspend fun loadNativeAd(adUnitId: String): NativeAdContent? = null
    override fun recordImpression(adId: String) = Unit
    override fun recordClick(adId: String) = Unit
}
