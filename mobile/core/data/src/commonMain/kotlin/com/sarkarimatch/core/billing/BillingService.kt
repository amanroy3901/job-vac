package com.sarkarimatch.core.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pro entitlement as the app sees it. The server decides; the app never grants Pro itself (billing.md D9). */
enum class Plan { Free, Trial, Pro }

/** A Pro product as the store presents it (price already localised by Play / the App Store). */
data class ProProduct(
    /** Our product id (billing.md §1): `pro_year`, `pro_season`. */
    val productId: String,
    /** Play: subscription `pro`; App Store: the product id in App Store Connect (see [ProProducts]). */
    val storeProductId: String,
    /** Play: base plan id (`yearly-autorenew`, `season-4m-prepaid`); App Store: null. */
    val basePlanId: String?,
    /** Play: offer token of the base plan / offer; App Store: empty. */
    val offerToken: String,
    val title: String,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
    val billingPeriodIso: String?,
)

/** Outcome of one purchase attempt. The token goes to the backend for verification, never trusted locally. */
sealed interface PurchaseResult {
    /** Play: purchaseToken; App Store: the signed JWS transaction (StoreKit 2 `jwsRepresentation`). */
    data class Purchased(val productId: String, val verificationToken: String) : PurchaseResult
    data object Pending : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

/** The store-side purchase flows: Google Play Billing on Android, StoreKit 2 on iOS (founder decision 9). */
interface BillingService {
    /** Current plan; updated by the backend's verification result (FE-24). */
    val plan: StateFlow<Plan>

    /** Our product ids ([ProProducts.ALL]) → what the store sells right now, in the store's currency. */
    suspend fun products(productIds: List<String>): List<ProProduct>

    /** [accountToken] links the store transaction to our user (Play obfuscatedAccountId / StoreKit appAccountToken). */
    suspend fun purchase(product: ProProduct, accountToken: String?): PurchaseResult

    /** Re-reads owned purchases (Play queryPurchases / StoreKit Transaction.currentEntitlements). */
    suspend fun restore(): List<PurchaseResult.Purchased>
}

/** Free plan, no store. Used in tests, previews and until a store is configured. */
class StubBillingService : BillingService {
    override val plan: StateFlow<Plan> = MutableStateFlow(Plan.Free)
    override suspend fun products(productIds: List<String>): List<ProProduct> = emptyList()
    override suspend fun purchase(product: ProProduct, accountToken: String?): PurchaseResult =
        PurchaseResult.Failed("billing not configured")
    override suspend fun restore(): List<PurchaseResult.Purchased> = emptyList()
}

/**
 * Product mapping (billing.md §1). Play: one subscription `pro` with base plans. App Store: one auto-renewable
 * subscription per plan in a subscription group, plus a non-renewing subscription for the season pass (App Store has
 * no prepaid plans). The App Store ids are placeholders until App Store Connect is set up (bundle id placeholder).
 */
object ProProducts {
    const val YEAR = "pro_year"
    const val SEASON = "pro_season"
    val ALL = listOf(YEAR, SEASON)

    const val PLAY_SUBSCRIPTION = "pro"
    val playBasePlan = mapOf(YEAR to "yearly-autorenew", SEASON to "season-4m-prepaid")

    val appStoreProductId = mapOf(YEAR to "in.sarkarimatch.app.pro.year", SEASON to "in.sarkarimatch.app.pro.season")
}
