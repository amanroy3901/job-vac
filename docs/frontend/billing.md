# Billing (Pro)

Library: **Google Play Billing Library 9.1.0** (`com.android.billingclient:billing-ktx`), wrapped in `:core:billing`. Server is the authority for entitlement (API README §12).

## 1. Products (from `listProducts`, never hard-coded)

| Our `product_id` | Play product / base plan | Kind | Shown as |
| --- | --- | --- | --- |
| `pro_year` | `pro` / `yearly-autorenew` | auto-renewing subscription | ₹199/साल (≈ ₹17/महीना, computed from `priceAmountMicros / 12`) — highlighted |
| `pro_season` | `pro` / `season-4m-prepaid` | **prepaid plan** (no auto-renew, user tops up) | ₹49 / 4 महीने |
| `pro_month` (only if tests show demand) | `pro` / `monthly-autorenew` | auto-renewing | ₹20/महीना |

Prices, currency formatting and offer eligibility come only from Play `ProductDetails` (`formattedPrice`), so Play price experiments, taxes and regional pricing are correct. If `ProductDetails` can't load (no Play/old Play Store), the paywall shows `billingAvailable = false` and, if the `upi_link_purchase` flag is on, the UPI option.

**Trial** is *not* a Play free trial. It is a server-granted 14-day Pro that starts automatically on the first *भर दिया* (`MatchActionResult.entitlement`, `paywall.variant = trial_started`). So no card/UPI mandate is needed for the trial, matching the PRD ("Trial: 14 days of Pro from first Applied") and the B02 copy "मुफ़्त — कोई पेमेंट नहीं".

## 2. Components

```kotlin
/** App-scoped (Singleton). One BillingClient for the whole process. */
interface BillingRepository {
    val purchaseUpdates: SharedFlow<PurchaseEvent>          // from PurchasesUpdatedListener
    suspend fun productDetails(products: List<Product>): Result<List<PlanOffer>>
    fun launch(activity: Activity, offer: PlanOffer, accountRef: String, replace: ReplaceSpec? = null): BillingResult
    suspend fun unverifiedPurchases(): List<Purchase>       // queryPurchasesAsync(SUBS) where !isAcknowledged
}

class PlayBillingRepository @Inject constructor(
    @ApplicationContext ctx: Context, @ApplicationScope private val scope: CoroutineScope,
) : BillingRepository {
    private val _updates = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)
    override val purchaseUpdates = _updates.asSharedFlow()

    private val client = BillingClient.newBuilder(ctx)
        .setListener { result, purchases -> _updates.tryEmit(PurchaseEvent.from(result, purchases)) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection()               // PBL 8+: reconnects automatically on SERVICE_DISCONNECTED
        .build()
    // connect lazily on first use; never at cold start (startup time on 2 GB phones)
}
```

`EntitlementRepository` (in `:core:data`) owns verification and the Room `entitlement` row; `:core:billing` only talks to Play.

## 3. Purchase flow

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant P as S30 Paywall (VM + Route)
  participant B as BillingRepository
  participant Play as Google Play
  participant E as EntitlementRepository
  participant API as Backend
  U->>P: selects ₹199/साल, taps "Pro लें"
  P->>B: productDetails() (already loaded) → launch(activity, offer,<br/>setObfuscatedAccountId(user.billing_account_ref))
  B->>Play: launchBillingFlow
  Play-->>U: Play sheet (O06), UPI / card / Play balance
  Play-->>B: onPurchasesUpdated(OK, purchase{token, state})
  B-->>P: PurchaseEvent.Purchased(token) (SharedFlow)
  alt purchaseState == PURCHASED
    P->>E: verify(product, basePlan, token, orderId)
    E->>E: persist token in outbox (PurchaseVerifyWorker backup)
    E->>API: verifyPlayPurchase {product_id, base_plan_id, purchase_token, order_id}
    API->>Play: purchases.subscriptionsv2.get + acknowledge (server-side)
    API-->>E: Entitlement{plan=pro, status=active, expires_at, features}
    E->>E: Room entitlement upsert → all screens unlock
    P-->>U: navigate S31 Success (popUpTo Paywall)
  else purchaseState == PENDING (e.g. UPI in Play, cash)
    P-->>U: S31 Pending "भुगतान पूरा होते ही Pro चालू हो जाएगा"
    Note over E,API: later: Play RTDN → server grants; app learns via sync / FCM sync push / next start
  else USER_CANCELED
    P-->>U: stay on S30 (no error)
  else error
    P-->>U: PurchaseStep.Failed (retry / "Play Store अपडेट करें")
  end
```

Rules:

- **The client never calls `acknowledgePurchase`** — the server acknowledges after verifying (API contract). If verification can't reach the server, `PurchaseVerifyWorker` (unique, network-constrained, exponential backoff) retries; Play refunds unacknowledged purchases after 3 days, so the worker is also triggered on every app start for any `unverifiedPurchases()`.
- `409 purchase_owned_by_other_account` → S31 Failed with "यह खरीद दूसरे अकाउंट से जुड़ी है — उसी नंबर से लॉग इन करें" (→ S11).
- `402` (payment invalid) → S31 Failed.
- **Pro UI is gated on the server entitlement only** (Room `entitlement` row from `getEntitlement` / `sync` / `startSession` / action results / verify result). Never on local `Purchase` state.

## 4. Entitlement caching

| Field | Use |
| --- | --- |
| `plan`, `status` (`active / in_grace / on_hold / expired / none`) | `isPro = plan == pro && status in (active, in_grace)` |
| `expires_at` | offline tolerance: treat cached `active` as Pro until `expires_at + 72 h` when we cannot refresh (no network). After that, show Pro UI read-only with "इंटरनेट से जुड़ें" |
| `features.*` | per-feature gates (lifecycle, WhatsApp, family, clash, certificate) — combined with `AppConfig.features` remote flags |
| `whatsapp_quota` | S33 "इस सीज़न 12/30 WhatsApp संदेश" |
| `manage_url` | S32 "Play Store में मैनेज करें", D03 warning |
| `trial_available`, `source = trial` | B02/B08/B09 variants |

Refresh points: app start (`startSession`), every `sync`, FCM `sync` push after RTDN, after verify, S32 open, pull-to-refresh on S22.

`in_grace` / `on_hold`: banner on S22/S32 "भुगतान में दिक्कत — Play Store में ठीक करें" with `manage_url` deep link (`https://play.google.com/store/account/subscriptions?sku=…&package=…`).

## 5. Restore & multi-device

- S30/S32 "ख़रीद वापस लाएँ": `queryPurchasesAsync(SUBS)` → `verifyPlayPurchase` for each → `getEntitlement` → D08 result ("Pro वापस आ गया" / "इस Google खाते में कोई ख़रीद नहीं मिली").
- A purchase is bound to our user through `obfuscatedAccountId`; on a new phone, signing in with the verified phone (S11/S12) restores the entitlement via `startSession` without touching Play.

## 6. Upgrade season → year (S32)

`launch(…, replace = ReplaceSpec(oldToken))` using `SubscriptionProductReplacementParams` (the per-product replacement API; `setReplacementMode` on the old update params is deprecated). Replacement mode for prepaid → auto-renew is chosen per Play's prepaid-plan rules (verify at implementation time). The server re-verifies and returns the new entitlement.

## 7. UPI payment link (WhatsApp test / fallback)

`createUpiPaymentLink(product_id)` → `UpiLink.url` → `Intent.ACTION_VIEW` (B11 lists installed UPI apps; falls back to browser) → on resume, poll `getEntitlement` every 3 s for ≤ 2 min → S31. Entitlement is granted only by the provider webhook server-side.

**Policy:** Play's Payments policy requires Play Billing for digital goods sold in a Play-distributed app, except under an enrolled alternative/user-choice billing programme. So the in-app UPI path is: (a) only in the `upi` product flavour used for the closed WhatsApp test, or (b) behind the `upi_link_purchase` remote flag **only** if the business enrols in Play's user-choice billing for India. Default production build: Play Billing only.

## 8. Paywall triggers (PRD + inventory)

| Trigger | Surface | Rule |
| --- | --- | --- |
| Right after *भर दिया* (in-app B01 confirm or push action) | B02 | first time: trial started (informational) → B08; later (trial used, free): plans, max once / 7 days |
| Trial day 12–14 on app open, or trial-ending push | B09 → S30 | once per day |
| Locked lifecycle row on S22 / locked S23 | S30 (`source = LockedRow`) | always available |
| S33/S34/S36/S37 opened by a free user | inline locked state → S30 | |
| Profile Pro card | S30 / S32 | |
| Deep link `https://sarkarimatch.in/pro?plan=` | S30 preselect | |

Never on first launch, never during onboarding, no interstitials (PRD: "no upselling", free tier ad-free). `paywall_enabled` remote flag hides all upsell surfaces during the validation phase if needed.

Analytics: `paywall_view{source, variant}`, `plan_select`, `purchase_start`, `purchase_result{status, plan}`, `trial_started`, `restore_result` — consent-gated (performance-accessibility.md §6).

## 9. Testing

- `FakeBillingRepository` in `:core:testing` drives all PaywallViewModel tests (purchased / pending / cancelled / error / 409).
- Play's static test product ids + license testers on the internal test track for end-to-end; server verification hits staging.
