package com.sarkarimatch.core.platform

import com.sarkarimatch.core.ads.AdsService
import com.sarkarimatch.core.ads.NativeAdContent
import com.sarkarimatch.core.auth.AuthProvider
import com.sarkarimatch.core.auth.AuthResult
import com.sarkarimatch.core.auth.AuthService
import com.sarkarimatch.core.auth.ProviderCredential
import com.sarkarimatch.core.billing.BillingService
import com.sarkarimatch.core.billing.Plan
import com.sarkarimatch.core.billing.ProProduct
import com.sarkarimatch.core.billing.ProProducts
import com.sarkarimatch.core.billing.PurchaseResult
import com.sarkarimatch.core.push.PushMessage
import com.sarkarimatch.core.push.PushService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** StoreKit 2 through [IosBillingBridge]. The JWS of each transaction is verified by the backend (FE-24). */
internal class IosBillingService(private val bridge: IosBillingBridge) : BillingService {
    private val _plan = MutableStateFlow(Plan.Free)
    override val plan: StateFlow<Plan> = _plan.asStateFlow()

    override suspend fun products(productIds: List<String>): List<ProProduct> {
        val byStoreId = productIds.associateBy { ProProducts.appStoreProductId[it] }
        val storeIds = byStoreId.keys.filterNotNull()
        val products = suspendCancellableCoroutine { cont ->
            bridge.loadProducts(
                storeIds,
                IosProductsCallback(onResult = { cont.resume(it) }, onFailure = { cont.resume(emptyList()) }),
            )
        }
        return products.mapNotNull { p ->
            val ourId = byStoreId[p.storeProductId] ?: return@mapNotNull null
            ProProduct(
                productId = ourId,
                storeProductId = p.storeProductId,
                basePlanId = null,
                offerToken = "",
                title = p.displayName,
                formattedPrice = p.displayPrice,
                priceMicros = p.priceMicros,
                currencyCode = p.currencyCode,
                billingPeriodIso = p.period,
            )
        }
    }

    override suspend fun purchase(product: ProProduct, accountToken: String?): PurchaseResult {
        val outcome = suspendCancellableCoroutine { cont ->
            bridge.purchase(product.storeProductId, accountToken, IosPurchaseCallback { cont.resume(it) })
        }
        return when (outcome) {
            is IosPurchaseOutcome.Purchased ->
                PurchaseResult.Purchased(product.productId, outcome.transaction.jwsRepresentation)
            IosPurchaseOutcome.Pending -> PurchaseResult.Pending
            IosPurchaseOutcome.Cancelled -> PurchaseResult.Cancelled
            is IosPurchaseOutcome.Failed -> PurchaseResult.Failed(outcome.message)
        }
    }

    override suspend fun restore(): List<PurchaseResult.Purchased> {
        val ourIdByStoreId = ProProducts.appStoreProductId.entries.associate { (ours, store) -> store to ours }
        val transactions = suspendCancellableCoroutine { cont ->
            bridge.currentEntitlements(IosTransactionsCallback { cont.resume(it) })
        }
        return transactions.map {
            PurchaseResult.Purchased(ourIdByStoreId[it.storeProductId] ?: it.storeProductId, it.jwsRepresentation)
        }
    }
}

/** Google Sign-In for iOS + Sign in with Apple through [IosAuthBridge]. */
internal class IosAuthService(private val bridge: IosAuthBridge) : AuthService {
    // Apple first: App Store guideline 4.8 (equivalent login option) — shown whenever Google is offered.
    override val providers: List<AuthProvider> =
        if (bridge.isGoogleConfigured()) listOf(AuthProvider.APPLE, AuthProvider.GOOGLE) else listOf(AuthProvider.APPLE)

    override suspend fun signIn(provider: AuthProvider, nonce: String?): AuthResult = suspendCancellableCoroutine { cont ->
        val callback = IosAuthCallback(
            onOk = { token, name, email -> cont.resume(AuthResult.Success(ProviderCredential(provider, token, nonce, name, email))) },
            onCancel = { cont.resume(AuthResult.Cancelled) },
            onFailure = { cont.resume(AuthResult.Failed(it)) },
        )
        when (provider) {
            AuthProvider.GOOGLE -> bridge.signInWithGoogle(nonce, callback)
            AuthProvider.APPLE -> bridge.signInWithApple(nonce, callback)
        }
    }

    override suspend fun signOut() = bridge.signOut()
}

/**
 * Entry points Swift calls from the AppDelegate / `MessagingDelegate` / `UNUserNotificationCenterDelegate`:
 * `IosPushEvents.shared.onNewToken(token:)` and `IosPushEvents.shared.onMessage(data:)`.
 */
object IosPushEvents {
    internal val tokens = MutableSharedFlow<String>(extraBufferCapacity = 8)
    internal val messages = MutableSharedFlow<PushMessage>(extraBufferCapacity = 32)

    fun onNewToken(token: String) {
        tokens.tryEmit(token)
    }

    /** [data] is the notification's `userInfo` (string keys and values are kept, the `aps` dictionary is dropped). */
    fun onMessage(data: Map<Any?, *>) {
        val strings = data.entries.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }.toMap()
        messages.tryEmit(PushMessage(strings))
    }
}

internal class IosPushService(private val bridge: IosPushBridge) : PushService {
    override suspend fun token(): String? = suspendCancellableCoroutine { cont ->
        bridge.fetchToken(IosTokenCallback { cont.resume(it) })
    }

    override val tokenUpdates: Flow<String> = IosPushEvents.tokens.asSharedFlow()
    override val messages: Flow<PushMessage> = IosPushEvents.messages.asSharedFlow()
}

internal class IosAdsService(private val bridge: IosAdsBridge) : AdsService {
    override val isAvailable: Boolean = true

    override suspend fun ensureConsent(): Boolean = suspendCancellableCoroutine { cont ->
        bridge.requestConsent(IosConsentCallback { cont.resume(it) })
    }

    override suspend fun loadNativeAd(adUnitId: String): NativeAdContent? = suspendCancellableCoroutine { cont ->
        bridge.loadNativeAd(
            adUnitId,
            IosNativeAdCallback { ad ->
                cont.resume(ad?.let { NativeAdContent(it.adId, it.headline, it.body, it.callToAction, it.advertiser) })
            },
        )
    }

    override fun recordImpression(adId: String) = bridge.recordImpression(adId)

    override fun recordClick(adId: String) = bridge.recordClick(adId)
}
