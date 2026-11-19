# Billing (Pro)

Library: **Google Play Billing Library 9.1.0** (`com.android.billingclient:billing-ktx`), wrapped in `:core:billing`. Server is the authority for entitlement (API README §12). **Play Billing is the only payment path** (founder decision 4, 2026-11-19): UPI, cards and Play balance are offered inside Play's own sheet (O06); there are no UPI links, gateways or user-choice billing.

## 1. Products (from `listProducts`, never hard-coded)

| Our `product_id` | Play product / base plan | Kind | Shown as |
| --- | --- | --- | --- |
| `pro_year` | `pro` / `yearly-autorenew` | auto-renewing subscription | ₹199/साल (≈ ₹17/महीना, computed from `priceAmountMicros / 12`) — highlighted |
| `pro_season` | `pro` / `season-4m-prepaid` (`P4M`; fallback `P3M`) | **prepaid base plan** of the subscription (no auto-renew, user can top up) — chosen over a one-time product, backend payments §1.1 | ₹49 / 4 महीने · "अपने-आप रिन्यू नहीं होगा" |
| `pro_month` (only if tests show demand) | `pro` / `monthly-autorenew` | auto-renewing | ₹20/महीना |

Prices, currency formatting and offer eligibility come only from Play `ProductDetails` (`formattedPrice`), so Play price experiments, taxes and regional pricing are correct. If `ProductDetails` can't load (no Play/old Play Store), the paywall shows `billingAvailable = false` with "Play Store अपडेट करें".

**Account first.** Purchase and trial need a server account. For a guest, B16 `pro` (once, before the Play sheet) offers Google sign-in; "अभी नहीं" creates a Firebase anonymous account (`SessionRepository.ensureAccount`). Minor mode hides purchase and trial.

**Trial** is *not* a Play free trial (prepaid plans cannot carry offers anyway). It is a server-granted 14-day Pro, **offered** at the first *भर दिया* (decided locally: B02 `trial_offer` while `trial_available`, guests count as available) and **started** only when the user taps it (`startTrial` → `Entitlement` with `source = trial`, 14 days from the tap). No card/UPI mandate is needed, matching the PRD ("Trial: 14 days of Pro from first Applied") and the B02 caption "कोई पैसा नहीं लगेगा, कार्ड नहीं चाहिए". While `trial_available`, S30 also shows a "14 दिन मुफ़्त आज़माएँ" text button that calls `startTrial`.

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
    Note over E,API: later: Play RTDN → server grants; app learns via the `account` push (template `entitlement`) or next start
  else USER_CANCELED
    P-->>U: stay on S30 (no error)
  else error
    P-->>U: PurchaseStep.Failed (retry / "Play Store अपडेट करें")
  end
```

Rules:

- **The client never calls `acknowledgePurchase`** — the server acknowledges after verifying (API contract). If verification can't reach the server, `PurchaseVerifyWorker` (unique, network-constrained, exponential backoff) retries; Play refunds unacknowledged purchases after 3 days, so the worker is also triggered on every app start for any `unverifiedPurchases()`.
- `409 purchase_owned_by_other_account` → S31 Failed with "यह खरीद दूसरे Google अकाउंट से जुड़ी है — उसी से साइन इन करें" (→ O11).
- `402` (payment invalid) → S31 Failed.
- **Pro UI is gated on the server entitlement only** (Room `entitlement` row + signed `token` from `getEntitlement` / `startSession` / verify / trial result). Never on local `Purchase` state.

## 4. Entitlement caching

| Field | Use |
| --- | --- |
| `plan`, `status` (`active / in_grace / on_hold / expired / none`) | `isPro = plan == pro && status in (active, in_grace)` |
| `token` (JWT, ES256, verified with `config.entitlement_token_jwks`) | offline gating; lifecycle topic subscription (`n_{notice}`) only while the token says Pro/trial and has not expired (≤ 7 days); after that, show Pro UI read-only with "इंटरनेट से जुड़ें" |
| `features.*` | per-feature gates (lifecycle, family, clash, certificate, ad-free) — combined with `AppConfig.features` remote flags |
| `manage_url` | S32 "Play Store में मैनेज करें", D03 warning |
| `trial_available`, `source = trial` | B02/B08/B09 variants |

Refresh points (accounts only): app start (`startSession`), `account` push with template `entitlement` (sent after RTDN changes), after verify/trial, S32 open, pull-to-refresh on S22, daily reconcile. Guests without an account are always `free` with `trial_available = true`.

`in_grace` / `on_hold`: banner on S22/S32 "भुगतान में दिक्कत — Play Store में ठीक करें" with `manage_url` deep link (`https://play.google.com/store/account/subscriptions?sku=…&package=…`).

## 5. Restore & multi-device

- S30/S32 "ख़रीद वापस लाएँ": `queryPurchasesAsync(SUBS)` → `verifyPlayPurchase` for each → `getEntitlement` → D08 result ("Pro वापस आ गया" / "इस Google खाते में कोई ख़रीद नहीं मिली").
- A purchase is bound to our account through `obfuscatedAccountId`; on a new phone, Google sign-in (O11) restores the entitlement via `startSession` without touching Play. A guest who never linked Google restores through "ख़रीद वापस लाएँ" (Play account history → `verifyPlayPurchase`; the server moves it from the idle anonymous account).

## 6. Upgrade season → year (S32)

`launch(…, replace = ReplaceSpec(oldToken))` using `SubscriptionProductReplacementParams` (the per-product replacement API; `setReplacementMode` on the old update params is deprecated). Replacement mode for prepaid → auto-renew is chosen per Play's prepaid-plan rules (verify at implementation time). The server re-verifies and returns the new entitlement.

## 7. Removed: UPI payment links

The `upi` product flavour, `createUpiPaymentLink`, B11 and the `upi_link_purchase` flag are removed (founder decision
4). The validation test is a **Play closed-testing** build with real Play Billing purchases (decision 8);
licence testers are used only for QA purchases.

## 8. Paywall triggers (PRD + inventory)

| Trigger | Surface | Rule |
| --- | --- | --- |
| Right after *भर दिया* (in-app B01 confirm or push action) | B02 | `trial_offer`: "14 दिन मुफ़्त शुरू करें" → (guest: B16 `pro` / anonymous account) → `startTrial` → B08; `plans` (trial used): plan cards → S30. First Applied always; afterwards max once / 7 days. Never in minor mode |
| Trial day 12–14 on app open, or trial-ending push | B09 → S30 | once per day |
| Locked lifecycle row on S22 / locked S23 | S30 (`source = LockedRow`) | always available |
| S34/S36/S37 opened by a free user | inline locked state → S30 | |
| Profile Pro card | S30 / S32 | |
| "विज्ञापन हटाएँ" on the free-tier native ad (O10) | S30 (`source = AdSlot`) | ads.md; Pro and trial remove ads (`Entitlement.ads_enabled = false`) |
| Deep link `https://sarkarimatch.in/pro?plan=` | S30 preselect | |

Never on first launch, never during onboarding, no interstitials (PRD: "no upselling", free tier ad-free). `paywall_enabled` remote flag hides all upsell surfaces during the validation phase if needed.

Metrics: with the usage-counter opt-in, daily counts `paywall_view_{source}`, `purchase_start`, `purchase_ok`, `trial_started` (no identifiers); conversion truth comes from server `purchase` rows and Play Console.

## 9. Testing

- `FakeBillingRepository` in `:core:testing` drives all PaywallViewModel tests (purchased / pending / cancelled / error / 409).
- Play's static test product ids + licence testers on the internal test track for end-to-end; server verification hits staging. The closed-testing track (validation test) uses real purchases.
