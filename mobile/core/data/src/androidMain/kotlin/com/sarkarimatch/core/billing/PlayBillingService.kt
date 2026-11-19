package com.sarkarimatch.core.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.sarkarimatch.core.platform.ActivityProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing 9 (founder decision 4: the only payment path on Android). Product: subscription `pro` with base
 * plans `yearly-autorenew` and `season-4m-prepaid` (billing.md §1). Purchase tokens are sent to the backend for
 * verification + acknowledgement (FE-24); [plan] is set from the backend's answer, never from Play alone (D9).
 */
class PlayBillingService(
    context: Context,
    private val activities: ActivityProvider,
) : BillingService, PurchasesUpdatedListener {
    private val _plan = MutableStateFlow(Plan.Free)
    override val plan: StateFlow<Plan> = _plan.asStateFlow()

    private val details = mutableMapOf<String, ProductDetails>()
    private var pendingPurchase: CompletableDeferred<PurchaseResult>? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection()
        .build()

    private suspend fun connected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
        }
    }

    override suspend fun products(productIds: List<String>): List<ProProduct> {
        if (!connected()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ProProducts.PLAY_SUBSCRIPTION)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        val pro = result.productDetailsList.orEmpty().firstOrNull() ?: return emptyList()
        return productIds.mapNotNull { id ->
            val basePlan = ProProducts.playBasePlan[id] ?: return@mapNotNull null
            val offer = pro.subscriptionOfferDetails.orEmpty()
                .filter { it.basePlanId == basePlan }
                .minByOrNull { it.offerId != null } // base plan first; offers (trials) via FE-25
                ?: return@mapNotNull null
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
            details[id] = pro
            ProProduct(
                productId = id,
                storeProductId = pro.productId,
                basePlanId = basePlan,
                offerToken = offer.offerToken,
                title = pro.name,
                formattedPrice = phase.formattedPrice,
                priceMicros = phase.priceAmountMicros,
                currencyCode = phase.priceCurrencyCode,
                billingPeriodIso = phase.billingPeriod,
            )
        }
    }

    override suspend fun purchase(product: ProProduct, accountToken: String?): PurchaseResult {
        val activity = activities.current ?: return PurchaseResult.Failed("no foreground activity")
        val productDetails = details[product.productId] ?: return PurchaseResult.Failed("products() not loaded")
        if (!connected()) return PurchaseResult.Failed("Play Billing unavailable")
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(product.offerToken)
                        .build(),
                ),
            )
            .apply { if (accountToken != null) setObfuscatedAccountId(accountToken) }
            .build()
        val deferred = CompletableDeferred<PurchaseResult>().also { pendingPurchase = it }
        val launch = client.launchBillingFlow(activity, params)
        if (launch.responseCode != BillingResponseCode.OK) {
            pendingPurchase = null
            return PurchaseResult.Failed(launch.debugMessage)
        }
        return deferred.await()
    }

    override suspend fun restore(): List<PurchaseResult.Purchased> {
        if (!connected()) return emptyList()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        return client.queryPurchasesAsync(params).purchasesList
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .map { it.toResult(product = null) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val outcome = when (result.responseCode) {
            BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull()
                when (purchase?.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> purchase.toResult(product = null)
                    Purchase.PurchaseState.PENDING -> PurchaseResult.Pending
                    else -> PurchaseResult.Failed("no purchase in result")
                }
            }
            BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
            else -> PurchaseResult.Failed(result.debugMessage)
        }
        pendingPurchase?.complete(outcome)
        pendingPurchase = null
    }

    private fun Purchase.toResult(product: String?): PurchaseResult.Purchased =
        PurchaseResult.Purchased(product ?: products.firstOrNull().orEmpty(), purchaseToken)
}
