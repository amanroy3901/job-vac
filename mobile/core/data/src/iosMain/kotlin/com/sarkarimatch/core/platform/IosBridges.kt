package com.sarkarimatch.core.platform

/*
 * Swift-facing bridge protocols. StoreKit 2, Google Sign-In for iOS, Sign in with Apple, Firebase Messaging and
 * Google Mobile Ads are Swift/ObjC SDKs (StoreKit 2 is Swift-only), so they are implemented in iosApp/ by Swift
 * classes conforming to these protocols and handed to MainViewController(bridges:). The Kotlin adapters
 * (IosServices.kt) turn the callbacks into suspend functions / flows behind the common interfaces.
 *
 * Shapes are kept ObjC-friendly on purpose: plain classes, String / Long / List / Map, and callback objects with
 * one method per outcome (no Kotlin lambdas, generics or suspend functions cross the boundary).
 */

/** A StoreKit 2 `Product` as Kotlin needs it. */
data class IosStoreProduct(
    val storeProductId: String,
    val displayName: String,
    val displayPrice: String,
    /** `Product.price` × 1 000 000, rounded. */
    val priceMicros: Long,
    val currencyCode: String,
    /** ISO-8601 period of `subscription.subscriptionPeriod` (e.g. "P1Y"), or null. */
    val period: String?,
)

/** A verified StoreKit 2 transaction: the JWS goes to the backend (App Store Server API verification). */
data class IosTransaction(val storeProductId: String, val jwsRepresentation: String)

class IosProductsCallback internal constructor(
    private val onResult: (List<IosStoreProduct>) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    fun onSuccess(products: List<IosStoreProduct>) = onResult(products)
    fun onError(message: String) = onFailure(message)
}

class IosPurchaseCallback internal constructor(private val onOutcome: (IosPurchaseOutcome) -> Unit) {
    fun onPurchased(transaction: IosTransaction) = onOutcome(IosPurchaseOutcome.Purchased(transaction))
    fun onPending() = onOutcome(IosPurchaseOutcome.Pending)
    fun onCancelled() = onOutcome(IosPurchaseOutcome.Cancelled)
    fun onError(message: String) = onOutcome(IosPurchaseOutcome.Failed(message))
}

internal sealed interface IosPurchaseOutcome {
    data class Purchased(val transaction: IosTransaction) : IosPurchaseOutcome
    data object Pending : IosPurchaseOutcome
    data object Cancelled : IosPurchaseOutcome
    data class Failed(val message: String) : IosPurchaseOutcome
}

class IosTransactionsCallback internal constructor(private val deliver: (List<IosTransaction>) -> Unit) {
    fun onResult(transactions: List<IosTransaction>) = deliver(transactions)
}

/** StoreKit 2 (Swift: `StoreKitBillingBridge`). */
interface IosBillingBridge {
    fun loadProducts(storeProductIds: List<String>, callback: IosProductsCallback)

    /** [appAccountToken] is a UUID string linking the transaction to our user, or null. */
    fun purchase(storeProductId: String, appAccountToken: String?, callback: IosPurchaseCallback)

    /** `Transaction.currentEntitlements`, verified transactions only. */
    fun currentEntitlements(callback: IosTransactionsCallback)
}

class IosAuthCallback internal constructor(
    private val onOk: (idToken: String, displayName: String?, email: String?) -> Unit,
    private val onCancel: () -> Unit,
    private val onFailure: (String) -> Unit,
) {
    fun onSuccess(idToken: String, displayName: String?, email: String?) = onOk(idToken, displayName, email)
    fun onCancelled() = onCancel()
    fun onError(message: String) = onFailure(message)
}

/** Google Sign-In for iOS + Sign in with Apple (Swift: `IosAuthBridgeImpl`). */
interface IosAuthBridge {
    fun isGoogleConfigured(): Boolean

    fun signInWithGoogle(nonce: String?, callback: IosAuthCallback)

    /** ASAuthorizationAppleIDProvider; [nonce] is the raw nonce, its SHA-256 goes into the request. */
    fun signInWithApple(nonce: String?, callback: IosAuthCallback)

    fun signOut()
}

class IosTokenCallback internal constructor(private val deliver: (String?) -> Unit) {
    fun onToken(token: String?) = deliver(token)
}

/** Firebase Messaging iOS SDK (APNs underneath). Swift also forwards events to [IosPushEvents]. */
interface IosPushBridge {
    fun fetchToken(callback: IosTokenCallback)
}

class IosConsentCallback internal constructor(private val deliver: (Boolean) -> Unit) {
    fun onResult(canRequestAds: Boolean) = deliver(canRequestAds)
}

class IosNativeAdCallback internal constructor(private val deliver: (IosNativeAd?) -> Unit) {
    fun onAdLoaded(ad: IosNativeAd) = deliver(ad)

    @Suppress("UnusedParameter") // reported by the SDK; the slot simply stays empty
    fun onAdFailed(message: String) = deliver(null)
}

data class IosNativeAd(
    val adId: String,
    val headline: String,
    val body: String?,
    val callToAction: String?,
    val advertiser: String?,
)

/** Google Mobile Ads iOS SDK + UMP + App Tracking Transparency (Swift). */
interface IosAdsBridge {
    fun requestConsent(callback: IosConsentCallback)
    fun loadNativeAd(adUnitId: String, callback: IosNativeAdCallback)
    fun recordImpression(adId: String)
    fun recordClick(adId: String)
}

/**
 * Everything Swift provides, passed to `MainViewControllerKt.MainViewController(bridges:)`. Any bridge may be nil:
 * the app then uses the stub (free plan, guest only, no push, no ads).
 */
class IosPlatformBridges(
    val billing: IosBillingBridge?,
    val auth: IosAuthBridge?,
    val push: IosPushBridge?,
    val ads: IosAdsBridge?,
)
