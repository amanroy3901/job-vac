# Payments and entitlements

**One rule:** the app gates Pro features **only** on the server entitlement (`GET /v1/me/entitlement`, which also
returns a signed `entitlement_token` the app caches for offline gating and topic subscriptions). The server computes
it from `entitlement_grant` rows and nothing else. Purchases, trials, referrals and promos each create grants.

**Payments are Google Play Billing only** (founder decision 4, 2026-11-19). Play's sheet already offers UPI, cards,
net banking/carrier billing where available and Play balance. There are no external UPI links, payment gateways or
user-choice billing; Razorpay/Cashfree flows are removed.

## 1. Products

| Our id | Play product / base plan | Type | Price | Duration | Notes |
|---|---|---|---|---|---|
| `pro_season` | `pro` / `season-4m-prepaid` | **subscription, prepaid base plan** (no auto-renew; top-up extends) | ₹49 | `P4M` (*confirm "every 4 months" is offered for prepaid in Play Console; fallback `P3M` at ₹49*) | lead offer at the paywall moment |
| `pro_year` | `pro` / `yearly-autorenew` | auto-renewing | ₹199 (shown as ₹17/month) | 12 months | grace period 7 d, account hold 30 d (Play Console settings) |
| `pro_month` | `pro` / `monthly-autorenew` | auto-renewing | ₹20 | 1 month | **disabled**; only if tests show demand (PRD) |

Prices displayed in the app come from Play `ProductDetails`. `GET /v1/products` maps ids and features. Play's service
fee is 15% on subscriptions → ~₹42 / ₹169 net (PRD).

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
recompute(user):
  active = grants where revoked_at is null and starts_at <= now < ends_at
  if any active: plan = pro; expires_at = max(ends_at); source = highest-precedence active
                 (play_year > play_season > referral > promo > trial)
  else if latest play purchase in grace → pro/in_grace; on_hold → free/on_hold
  else plan = free, status = expired|none
  ads_enabled = (plan == free)
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

## 9. Accounting

Purchases are kept (unlinked from deleted users) for statutory retention (*8 years assumed; confirm with a CA*). GST
treatment of Play-collected revenue needs a CA's view (Google collects and remits GST on Play sales in India for some
categories). A monthly CSV export from `purchase` feeds
the accounts.
