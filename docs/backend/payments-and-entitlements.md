# Payments and entitlements

**One rule:** the app gates Pro features **only** on the server entitlement (`GET /v1/me/entitlement`, which also
returns a signed `entitlement_token` the app caches for offline gating and topic subscriptions). The server computes
it from `entitlement_grant` rows and nothing else. Purchases, trials, referrals and promos each create grants.

**Payments are the platform stores only**: Google Play Billing on Android (founder decision 4) and **StoreKit 2 /
App Store in-app purchase on iOS** (founder decision 9, 2026-11-19). Play's sheet already offers UPI, cards, net
banking/carrier billing where available and Play balance; the App Store offers cards, UPI and App Store credit in
India. There are no external UPI links, payment gateways or user-choice billing; Razorpay/Cashfree flows are removed.

**One entitlement per account, across both stores.** Grants belong to the account (Firebase UID), never to a device
or a store: a user who bought on Android and signs in on an iPhone with the same account (Google or Apple sign-in
linked to the same Firebase user) has Pro there too, and vice versa. Each store's purchase is verified only by its own
store; the server merges the grants (§4).

## 1. Products (both stores)

| Our id | Play product / base plan | Play type | App Store product id | App Store type | Price | Duration | Grant source |
|---|---|---|---|---|---|---|---|
| `pro_season` | `pro` / `season-4m-prepaid` | **subscription, prepaid base plan** (no auto-renew; top-up extends) | `in.sarkarimatch.pro.season4m` | **non-renewing subscription** (§1.2) | ₹49 (App Store: nearest price point, ₹49) | `P4M` (*Play: confirm P4M for prepaid; fallback `P3M`*). App Store: 4 calendar months from purchase, computed by the server | `play_season` / `appstore_season` |
| `pro_year` | `pro` / `yearly-autorenew` | auto-renewing | `in.sarkarimatch.pro.yearly` (group `pro`) | **auto-renewable**, 1 year, **introductory offer: 14-day free trial** (new subscribers to the group, per Apple ID) | ₹199 (shown as ₹17/month) | 12 months | `play_year` / `appstore_year` |
| `pro_month` | `pro` / `monthly-autorenew` | auto-renewing | — | — | ₹20 | 1 month | **disabled**; only if tests show demand (PRD) |

Prices displayed in the app come from Play `ProductDetails` / StoreKit `Product.displayPrice`, never from us.
`GET /v1/products` maps our ids to both stores' ids (`appstore_product_id`, `appstore_type`) and features. Store fees:
Play 15% on subscriptions → ~₹42 / ₹169 net (PRD). App Store: 15% for the **App Store Small Business Program**
(enrol before launch) and for auto-renewable subscriptions after a year of paid service; 30% otherwise
([Apple: Small Business Program](https://developer.apple.com/app-store/small-business-program/)); Apple collects and
remits Indian GST on App Store sales (*confirm with the CA*). App Store Connect settings: Billing Grace Period **on,
16 days** for the yearly subscription (closest to Play's grace + hold; *Apple offers fixed lengths — verify the options in
App Store Connect*), Family Sharing **off** (§8),
App Store Server Notifications **V2** URL = `https://api…/hooks/app-store` for Production and Sandbox.

### 1.1 The ₹49 "season": prepaid plan vs one-time product (recommendation)

| | **Prepaid base plan on the `pro` subscription (recommended)** | One-time product (consumable "4-month pass") |
|---|---|---|
| What Play models | time-limited access with an `expiryTime`; no renewal; user can **top up** to extend; shown in Play's subscription centre | an item with no duration; we would have to invent expiry, stacking and restore ourselves |
| Server | same `subscriptionsv2.get` + RTDN path as the yearly plan (PURCHASED, top-up, EXPIRED, REVOKED); acknowledge within 3 days | separate `products.get` / `orders` path, consume vs acknowledge rules, voided-purchase handling only |
| Upgrade season → year | Play `linkedPurchaseToken` / replacement modes | manual proration on our side |
| Policy fit | Play's Payments policy requires Play Billing for digital subscriptions/feature unlocks — met. Subscriptions policy: show price, duration and "does not auto-renew" before purchase (S30 copy: "₹49 · 4 महीने · अपने-आप रिन्यू नहीं होगा"); no misleading "free" wording | allowed, but a time-bound feature unlock sold as a consumable invites "does it renew?" confusion and support tickets |
| Limits | prepaid plans cannot carry Play free-trial/intro offers (we use a server trial anyway); a base plan's duration cannot be changed after creation | — |

**Recommendation:** keep the season as a **prepaid base plan** (`season-4m-prepaid`, `P4M`, top-up enabled) and the
year as an auto-renewing base plan (`yearly-autorenew`, `P1Y`; in India Play collects renewals via UPI Autopay or card
mandates). One subscription product keeps one RTDN path, one restore path and Play-native upgrades. If the Console
does not offer a 4-month prepaid period, ship `P3M` at ₹49 (copy "3 महीने") rather than switch to a one-time product.
Sources: [Android "About subscriptions"](https://developer.android.com/google/play/billing/subs) (prepaid plans,
top-ups, no offers on prepaid), [Play Console subscriptions help](https://support.google.com/googleplay/android-developer/answer/12154973)
(billing periods; the full list could not be fetched from the build environment — **verify P4M in the Console**).

### 1.2 The ₹49 season on iOS: non-renewing subscription (recommendation)

The App Store has no "prepaid plan". Its product types are consumable, non-consumable, **auto-renewable
subscription** and **non-renewing subscription**
([Apple: In-App Purchase types](https://developer.apple.com/in-app-purchase/)).

| | **Non-renewing subscription (recommended)** | Auto-renewable | Consumable | Non-consumable |
|---|---|---|---|---|
| 4-month duration | yes: the developer applies the duration (we grant 4 calendar months from `purchaseDate`) | **impossible**: auto-renewable durations are 1 week, 1, 2, 3, 6 months or 1 year ([App Store Connect help](https://developer.apple.com/help/app-store-connect/reference/auto-renewable-subscription-information)); and the season must not renew | possible, but Apple's type for "time-limited access" is the subscription type; review risk and "does it renew?" confusion | wrong: permanent |
| "Does not auto-renew" promise (S30 copy) | native semantics | contradicts it | ok | — |
| Top-up | buy again; the server stacks the new 4 months after the current season (§2b) — same user-visible result as a Play prepaid top-up | — | same | — |
| Restore / cross-device | Apple requires non-renewing subscriptions to be available on all the user's devices **through our own account system** — exactly our server entitlement by Firebase UID; StoreKit 2 `Transaction.currentEntitlements` / `Transaction.all` also return them | App Store restores | must be built | App Store restores |
| Server notifications | `ONE_TIME_CHARGE` (V2, production since 2025) + client verify | full lifecycle | `ONE_TIME_CHARGE` | `ONE_TIME_CHARGE` |
| Expiry data | none from Apple (we compute) | `expiresDate` | none | none |

So the iOS season = **non-renewing subscription** `in.sarkarimatch.pro.season4m`, 4 months from purchase, stacked
on top-up. It is the closest App Store equivalent of Play's prepaid plan: time-bound, no auto-renew, extend by buying
again, one entitlement path. Users need an account (anonymous at least) before buying, as on Android.

**Yearly on iOS** is an auto-renewable subscription in group `pro` with a **14-day free-trial introductory offer**
(App Store Connect → Subscription Prices → Introductory Offers). Apple decides eligibility per Apple ID (one intro offer
per subscription group); StoreKit applies it automatically to eligible users; the app shows
`Product.subscription.introductoryOffer` and `isEligibleForIntroOffer`. Interplay with our server trial: §5.

## 2. Play purchase flow (server-verified, server-acknowledged)

```mermaid
sequenceDiagram
  participant A as App
  participant GP as Google Play
  participant API as api
  participant PDA as Play Developer API
  participant PS as Pub/Sub (RTDN)
  A->>GP: launchBillingFlow(pro, basePlan, obfuscatedAccountId = billing_account_ref)
  GP-->>A: Purchase(token, state PURCHASED | PENDING)
  A->>API: POST /v1/me/purchases/google-play {product_id, base_plan_id, purchase_token}
  API->>PDA: purchases.subscriptionsv2.get(package, token)
  PDA-->>API: state, lineItems[expiryTime, productId, offer], acknowledgementState, externalAccountIdentifiers, linkedPurchaseToken
  API->>API: checks (package, product, account ref, state)
  API->>PDA: purchases.subscriptions.acknowledge (if ACKNOWLEDGEMENT_STATE_PENDING)
  API->>API: upsert purchase; grant; recompute user_entitlement
  API-->>A: Entitlement (plan=pro)
  GP-->>PS: RTDN (renewed / canceled / on hold / expired / revoked …)
  PS->>API: push /hooks/play-rtdn (OIDC JWT)
  API->>PDA: subscriptionsv2.get(token)  — never trust the notification body alone
  API->>API: upsert purchase; recompute entitlement → account push type=account (entitlement changed)
```

**Verification checks:**
1. `packageName` matches.
2. A line item's `productId`/base plan is one of ours.
3. `subscriptionState ∈ {ACTIVE, IN_GRACE_PERIOD}`. `PENDING` (e.g. UPI inside Play awaiting approval) → `402
   purchase_pending`, and the RTDN completes it later.
4. `externalAccountIdentifiers.obfuscatedExternalAccountId == user.billing_account_ref`. If it differs and that
   account is active → `409 purchase_owned_by_other_account` (the app shows "this purchase belongs to another
   account — sign in with the Google account you used then", B16 `pro`). If that account is deleted, or it is an
   anonymous account inactive for > 30 days, or the field is absent (restore on a new install) → link the purchase to
   the caller and audit-log it.
5. Token not already linked to another active user.

**Acknowledgement:** the server acknowledges after granting. Unacknowledged purchases are auto-refunded by Play after
3 days ([RevenueCat RTDN guide](https://www.revenuecat.com/guides/google-play-billing/real-time-developer-notifications-rtdn)).
A daily job re-acknowledges anything still pending (for example, if the app died before calling us). The client must
not acknowledge.

**Grant:** `entitlement_grant(source=play_season|play_year, starts_at=lineItem.startTime or now,
ends_at=lineItem.expiryTime, purchase_id)`. On renewal or top-up, the grant's `ends_at` moves forward (one grant row
per purchase token, updated). For `linkedPurchaseToken` (resubscribe/upgrade), the old purchase is marked superseded
and its grant is ended at the new start.

## 2b. App Store purchase flow (iOS, StoreKit 2, server-verified)

```mermaid
sequenceDiagram
  participant A as iOS app
  participant AS as App Store
  participant API as api
  participant N as App Store Server Notifications V2
  A->>AS: product.purchase(options: [.appAccountToken(UUID(billing_account_ref))])
  AS-->>A: .success(VerificationResult<Transaction>)  (JWS, ES256, x5c)
  A->>API: POST /v1/me/purchases/app-store {signed_transaction, signed_renewal_info?}
  API->>API: verify x5c → Apple Root CA G3, leaf/intermediate OIDs, ES256 signature, bundleId, environment
  API->>API: product → kind; owner (appAccountToken = billing_account_ref); upsert purchase (originalTransactionId); grant; recompute
  API-->>A: Entitlement (plan=pro, platform=ios)
  A->>AS: transaction.finish()   (only after 200, or 402 purchase_invalid)
  AS-->>N: SUBSCRIBED / DID_RENEW / DID_FAIL_TO_RENEW / EXPIRED / REFUND / ONE_TIME_CHARGE …
  N->>API: POST /hooks/app-store {signedPayload}
  API->>API: verify payload + signedTransactionInfo + signedRenewalInfo, dedupe notificationUUID, same upsert path
```

- **Verification is offline.** A StoreKit 2 transaction is a JWS signed by Apple; the server checks it exactly as
  Apple's App Store Server Library does (`SignedDataVerifier`): `alg=ES256`, an `x5c` chain of three certificates
  that verifies to **Apple Root CA - G3** (embedded in the binary — `backend/internal/appstore/AppleRootCA-G3.pem`,
  SHA-256 `63:34:3A:BF:…:3E:91:79`, from [Apple PKI](https://www.apple.com/certificateauthority/); the root in the
  JWS is ignored), leaf extension OID `1.2.840.113635.100.6.11.1` and intermediate OID `1.2.840.113635.100.6.2.1`,
  validity checked at the payload's `signedDate`, then the signature with the leaf key. No online OCSP check (Apple's
  library makes it optional). A unit test verifies a real Apple-signed sandbox JWS against the embedded root.
- **Checks:** (1) `bundleId` = `APPSTORE_BUNDLE_ID`; (2) `environment` ∈ `APPSTORE_ENVIRONMENTS` (default
  Production + Sandbox, because **App Review and TestFlight buy in Sandbox against the production build**; Sandbox
  purchases are stored `test_purchase=true`; Xcode/LocalTesting only with `APPSTORE_MODE=fake`); (3) `productId` is
  one of ours and its App Store type matches the product kind; (4) **ownership** = Play check 4–5 with
  `appAccountToken` in place of `obfuscatedExternalAccountId` (both are `billing_account_ref`): another active
  account owns it → `409 purchase_owned_by_other_account`; a deleted, or an anonymous account inactive for > 30 days,
  or no token (purchase made before sign-in / on another device) → linked to the caller and audit-logged.
  Notifications have no caller: the owner is the linked user, else the account whose `billing_account_ref` equals
  `appAccountToken`; otherwise the purchase is stored unlinked until the app verifies it.
- **One row per subscription:** `purchase.provider='app_store'`, keyed by `originalTransactionId`
  (`token_hash = sha256('appstore:'||originalTransactionId)`, unique index); `order_id` = latest `transactionId`; no
  secret token exists (a JWS is not a bearer secret). **Out-of-order** notifications: each purchase keeps
  `last_event_at` = the newest applied `signedDate`; older observations only (re)link the owner.
- **Fresher state:** the app also sends `signed_renewal_info` for auto-renewable products
  (`Product.SubscriptionInfo.Status.renewalInfo`). When it does not and the **App Store Server API** is configured
  (`APPSTORE_API_*`: In-App Purchase key, issuer id, ES256 JWT with `aud=appstoreconnect-v1`, `bid`, 5-minute
  `exp`), the server calls *Get All Subscription Statuses* (`GET /inApps/v1/subscriptions/{id}`) and uses the
  latest signed transaction + renewal info (also verified). Lookup and history (`GET /inApps/v1/transactions/{id}`,
  `GET /inApps/v2/history/{id}`) are in the client for support and reconciliation. The API is optional: the signed
  JWS is sufficient evidence.
- **No acknowledgement step** (StoreKit 2 has none; the app calls `finish()`); an unfinished transaction is simply
  redelivered through `Transaction.updates`, which the app forwards to the same endpoint (idempotent).
- **Season grants (non-renewing):** created once per transaction: start = `purchaseDate`, or the end of the user's
  current season time (either store) if later; end = start + 4 calendar months. Replays never move dates. Refund →
  grant revoked. Because a stacked grant starts in the future, the entitlement's `expires_at` follows contiguous grants
  (§4).

### 2c. App Store Server Notifications V2

`POST /hooks/app-store` with `{"signedPayload"}`. Authentication **is** the signature (same chain checks) plus
`data.bundleId` and, for Production, `data.appAppleId` = `APPSTORE_APP_APPLE_ID`. Response 200 = processed; Apple
retries other responses up to five times (1, 12, 24, 48, 72 h). Duplicates are dropped by `notificationUUID`
(`app_store_notification`). **State is always derived from the signed transaction + renewal info + `data.status`**
(1 active, 2 expired, 3 billing retry, 4 grace period, 5 revoked), never from the type alone, so unknown or future types
are handled safely ([Apple: notificationType](https://developer.apple.com/documentation/appstoreservernotifications/notificationtype),
[responseBodyV2DecodedPayload](https://developer.apple.com/documentation/appstoreservernotifications/responsebodyv2decodedpayload)).

| notificationType (subtype) | Meaning | Effect |
|---|---|---|
| `SUBSCRIBED` (`INITIAL_BUY`, `RESUBSCRIBE`), `OFFER_REDEEMED` | new / resumed subscription, intro trial included | upsert, grant to `expiresDate` |
| `DID_RENEW` (`BILLING_RECOVERY`) | renewal, also recovery from billing retry | grant extended; `in_grace`/`on_hold` → active |
| `DID_CHANGE_RENEWAL_STATUS` (`AUTO_RENEW_DISABLED/ENABLED`), `DID_CHANGE_RENEWAL_PREF` | auto-renew toggled / plan change queued | `canceled` (access to expiry, `auto_renewing=false`) / active |
| `DID_FAIL_TO_RENEW` (`GRACE_PERIOD`) | billing problem, Billing Grace Period running | `in_grace`, grant to `gracePeriodExpiresDate`; banner "payment failed, fix in the App Store" |
| `DID_FAIL_TO_RENEW` (no subtype), `GRACE_PERIOD_EXPIRED` | billing retry without access | `on_hold` → free, `manage_url` |
| `EXPIRED` (`VOLUNTARY`, `BILLING_RETRY`, `PRICE_INCREASE`, `PRODUCT_NOT_FOR_SALE`) | ended | `expired`, grant ended now |
| `REFUND` | Apple refunded | `refunded`, grant **revoked** |
| `REFUND_REVERSED` | refund undone | revocation cleared, grant restored |
| `REVOKE` | Family Sharing access withdrawn | `revoked`, grant revoked |
| `ONE_TIME_CHARGE` | non-renewing season bought (also consumables) | season grant (stacked) |
| `RENEWAL_EXTENDED`, `RENEWAL_EXTENSION` (`SUMMARY`/`FAILURE`) | Apple/our support extended the date | grant to the new `expiresDate` (summary notifications carry no transaction: logged) |
| `PRICE_INCREASE`, `PRICE_CHANGE`, `REFUND_DECLINED`, `METADATA_UPDATE`, `MIGRATION`, `RESCIND_CONSENT` | informational | transaction re-applied (no-op unless state changed) |
| `CONSUMPTION_REQUEST` | Apple asks for consumption data for a refund decision | logged; we do not answer (optional) |
| `TEST` | from App Store Connect / `POST /inApps/v1/notifications/test` | logged |
| `EXTERNAL_PURCHASE_TOKEN` | external purchase (not used) | ignored |

**App Store state → entitlement** (`billing.AppStoreState`, unit-tested):

| App Store observation | `purchase.state` | `user_entitlement` |
|---|---|---|
| `expiresDate` > now, `autoRenewStatus=1` (incl. intro free trial) | active | pro / active |
| `expiresDate` > now, `autoRenewStatus=0` | canceled | pro / active, `auto_renewing=false` |
| status 4, or billing retry with `gracePeriodExpiresDate` > now | in_grace | pro / in_grace (until grace end) |
| status 3, or billing retry without grace | on_hold | free / on_hold (+ App Store `manage_url`) |
| status 2, or `expiresDate` ≤ now | expired | free / expired (unless another grant is active) |
| `revocationDate` set (REFUND) | refunded | grant revoked |
| REVOKE / `revocationType=FAMILY_REVOKE` / status 5 | revoked | grant revoked |
| non-renewing, not revoked | active until server-computed end, then expired | pro / active until end |

## 3. RTDN

- Topic `projects/<prod>/topics/play-rtdn`; `google-play-developer-notifications@system.gserviceaccount.com` gets
  Pub/Sub Publisher on it; it is configured in Play Console → Monetization setup.
- Push subscription → `POST https://api…/hooks/play-rtdn` with an OIDC token (audience = that URL, service account
  `rtdn-pusher`). The handler verifies the JWT and is idempotent (Pub/Sub retries anything not acked; a slow handler
  means duplicates). It returns 2xx only after the DB commit. Dead-letter topic after 10 attempts → alert.
- Handled `subscriptionNotification.notificationType`s: PURCHASED, RENEWED, RECOVERED, RESTARTED, IN_GRACE_PERIOD,
  ON_HOLD, CANCELED (auto-renew off; access continues to expiry), EXPIRED, REVOKED, PAUSED, PENDING_PURCHASE_CANCELED,
  plus prepaid top-up notifications. **Every one triggers a fresh `subscriptionsv2.get`**; the state is derived from
  the API response ([Play RTDN reference](https://developer.android.com/google/play/billing/realtime_developer_notifications)).
- `voidedPurchaseNotification` (refund/chargeback) → `purchase.state = refunded/revoked`, grant `revoked_at`.
  The Voided Purchases API is also polled daily as a safety net.
- `testNotification` → logged.

**State → entitlement mapping:**

| Play state | `user_entitlement` |
|---|---|
| ACTIVE | pro / active |
| IN_GRACE_PERIOD | pro / in_grace (banner: "payment failed, fix in Play") |
| ON_HOLD | free / on_hold (banner + `manage_url`) |
| PAUSED | free |
| CANCELED (before expiry) | pro / active, `auto_renewing=false` |
| EXPIRED / REVOKED | free / expired (unless another grant is active) |

## 4. Entitlement computation

```text
recompute(user):                       -- grants from BOTH stores, the trial, referrals and promos
  active = grants where revoked_at is null and starts_at <= now < ends_at
  if any active: plan = pro
                 expires_at = max(ends_at), extended across non-revoked grants that start before it ends
                              (stacked App Store season top-ups)
                 source = highest-precedence active, ties → later ends_at
                          (play_year = appstore_year > play_season = appstore_season > play_month > referral > promo > trial)
                 platform = android (play_*) | ios (appstore_*) | null
                 duplicate_store_subscription = active play_* AND active appstore_* grants
  else if latest store purchase in grace → pro/in_grace; on_hold → free/on_hold (platform of that purchase)
  else plan = free, status = expired|none
  ads_enabled = (plan == free)
  manage_url = Play subscriptions link (platform android) | https://apps.apple.com/account/subscriptions (ios)
  write user_entitlement → a silent account push (`type=account, template=entitlement`) tells the app to refetch
  `GET /v1/me/entitlement` and its token
```

A housekeeping tick ends entitlements at `expires_at` (index `entitlement_expiry`). Overlapping grants never lose
time, because expiry is the max over grants.

**Feature gates.** Server-enforced: family link creation and watch-list publishing (`403 pro_required`). Device-
enforced from the signed `entitlement_token` (ES256, `exp` = min(entitlement expiry, now + 7 d), claims `plan`,
`ads_enabled`, `features`): subscribing to lifecycle topics `n_{notice}`, clash and certificate notifications,
ad-free. Lifecycle content is public catalog data, so a modified client could get it free; accepted at this price.
Timeline *viewing* stays free (`alerts_locked`).

**Account needed.** Trial and purchase need a server account. A guest who has not signed in gets a Firebase
anonymous account at that moment (B16 `pro` first offers Google sign-in, "so you keep Pro on a new phone"); linking
Google later keeps the same uid and entitlement. Minor mode (DOB < 18 on the device) hides purchase and trial in v1.

## 5. 14-day trial

- **iOS: one free trial per account.** The yearly App Store product carries a 14-day free-trial introductory offer
  (Apple-controlled, needs an Apple ID payment method, converts to paid unless cancelled). When an App Store
  free-trial transaction is applied the server marks the account's trial as used (a zero-length `trial` grant), so
  `trial_available=false` and the server trial is not offered on top. The reverse cannot be enforced: a user who
  used the server trial may still be eligible for Apple's intro offer (Apple decides per Apple ID), so the worst case
  is 28 trial days. On iOS the B02 CTA therefore offers the **server trial** while `trial_available` (no payment
  method needed, same as Android) and the App Store trial only on the S30 paywall. *Founder to confirm
  (open question).*
- **Offered at the first Applied, started by the user.** The first `applied` returns `paywall.variant=trial_offer`;
  B02 "14 दिन मुफ़्त शुरू करें" calls `POST /v1/me/trial`, which grants 14 days from the tap (also available from S30
  while unused). PRD "Trial: 14 days of Pro from first Applied" is read as: the offer appears at the first
  Applied, the clock starts on the user's tap. Lifecycle alerts for applications made earlier are backfilled.
  There is no Play free-trial offer, so no payment method is needed. This matters for Indian
  users without cards or UPI mandates.
- One per account (unique index), and one per Google account for linked users. Reinstall farming as a guest creates a
  new anonymous account; the exposure is small because no Pro feature has a per-message cost any more (no WhatsApp,
  no SMS). App Check (Play Integrity) is required on `POST /v1/me/trial`.
- Trial-end: B09 sheet on days 12–14 plus one account push N-31 on day 12 ("Pro ट्रायल 2 दिन में ख़त्म — आपके 3 फ़ॉर्म
  की अगली सूचना: …", `account` channel).

## 6. Removed: UPI links and gateways

Razorpay Payment Links, the `upi` product flavour, `POST /v1/me/purchases/upi-link`, `/hooks/razorpay`, the B11 UPI
chooser and the `upi` entitlement source are removed. UPI remains available **inside** Play's purchase sheet, where a
UPI purchase can arrive as `PENDING` and complete via RTDN (§2 check 3).

**Validation test (decision 8).** The PRD's 14-day test runs as a **Play closed-testing** release with real Play
Billing purchases (testers who are not licence testers are charged). This also satisfies Play's rule that personal
developer accounts created after 13 Nov 2023 run a closed test with ≥ 12 opted-in testers for 14 days before
applying for production access ([Play Console help](https://support.google.com/googleplay/android-developer/answer/14151465)).
`purchase.track = closed` tags test purchases; the go/stop gates use Play Console (installs, retention, revenue) plus
the opt-in usage counters (profiles completed, Applied taps). Licence testers are used only for QA purchases.

## 7. Referrals and promos

- Referral: code per user; the referee redeems within 7 days of sign-up. When the referee completes their **first
  Applied** (inventory S45), both get `entitlement_grant(source=referral, +30 days)`. Cap: 12 rewarded referrals per
  user per year. Anti-abuse: the referee must have a Play Integrity `MEETS_DEVICE_INTEGRITY` verdict at redeem time
  and a Google-linked account, and a Google account can be rewarded as referee only once ever.
- Promo grants (founding members, support goodwill) are created only in the admin console, with an audit log entry.

## 8. Edge cases

| Case | Handling |
|---|---|
| Purchase succeeded, app killed before verify | app start → `queryPurchasesAsync` → verify; RTDN PURCHASED also grants (we look up the user by `obfuscatedExternalAccountId` → `billing_account_ref`) |
| RTDN arrives before the app's verify call | same upsert path; the app's call returns the already-granted entitlement |
| User deletes account with an active Play sub | the API warns (subscription continues billing in Play); `DELETE /v1/me` response tells the app to show the Play manage link; the purchase is unlinked on hard delete |
| Family invitee (no purchase) | no entitlement needed; the aspirant's Pro gates the family alerts |
| Play Developer API outage | verify returns `503 retryable`; the app keeps the purchase unacknowledged and retries (3-day window) |
| Refund via Google | voided notification → revoke |
| Guest (anonymous) buys, then reinstalls without having linked Google | "ख़रीद वापस लाएँ": `queryPurchasesAsync` → verify → the old anonymous account is inactive, so the purchase moves to the new account (check 4) |
| Clock skew on device | server time only |
| **Moves Android → iPhone (or back)** | signs in with the same account (Google on both, or Apple/Google linked to one Firebase user) → `POST /v1/session` → the same entitlement; the old store's subscription keeps renewing in that store and is managed there (`manage_url` + `platform` tell the app which store; the other platform shows "manage in Google Play / the App Store" as text) |
| Subscribed in both stores | both grants count (no lost time); `duplicate_store_subscription=true` → S32 suggests cancelling one; refunds are each store's business |
| iOS purchase before any sign-in | the app always creates (at least) an anonymous account first and passes `appAccountToken`; a transaction without a token (bought elsewhere, restored on a new phone) links to the caller like Play check 4 |
| App Store notification before the app's verify | same upsert path; owner by `appAccountToken`; otherwise stored unlinked until the app verifies |
| Family Sharing | **off** for both products (a shared transaction has no `appAccountToken` of the family member; a `REVOKE` would be handled anyway) |
| User deletes account with an active App Store sub | as Play: billing continues in the App Store; `DELETE /v1/me` response → the app opens the App Store subscription sheet; purchases unlinked on hard delete; Sign in with Apple tokens revoked (security-privacy.md §2.1) |
| App Store refund | `REFUND` notification → revoke; `REFUND_REVERSED` → restore |
| Old JWS sent by the app (expired transaction) | `402 purchase_invalid` unless the Server API (or `signed_renewal_info`) shows a renewal/grace; the app should send `Transaction.latest(for:)` / `currentEntitlements` |

## 9. Accounting

App Store purchases are rows of the same `purchase` table (`provider = app_store`, `environment`, `test_purchase`
for Sandbox) and are included in the export; App Store Connect sales reports are the revenue source of truth for iOS.
Purchases are kept (unlinked from deleted users) for statutory retention (*8 years assumed; confirm with a CA*). GST
treatment of Play-collected revenue needs a CA's view (Google collects and remits GST on Play sales in India for some
categories). A monthly CSV export from `purchase` feeds
the accounts.
