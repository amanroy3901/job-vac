# Payments and entitlements

**One rule:** the app gates Pro features **only** on `GET /v1/me/entitlement` (also delivered via sync). The server
computes the entitlement from `entitlement_grant` rows and nothing else. Purchases, trials, referrals and promos
each create grants.

## 1. Products

| Our id | Play product / base plan | Type | Price | Duration | Notes |
|---|---|---|---|---|---|
| `pro_season` | `pro` / `season-4m-prepaid` | **prepaid plan** (no auto-renew; user can top up to extend) | ₹49 | 4 months (*confirm Play allows 4M prepaid; fallback 3M*) | lead offer at the paywall moment |
| `pro_year` | `pro` / `yearly-autorenew` | auto-renewing | ₹199 (shown as ₹17/month) | 12 months | grace period 7 d, account hold 30 d (Play Console settings) |
| `pro_month` | `pro` / `monthly-autorenew` | auto-renewing | ₹20 | 1 month | **disabled**; only if tests show demand (PRD) |

Prices displayed in the app come from Play `ProductDetails`. `GET /v1/products` maps ids and features. Play's service
fee is 15% on subscriptions → ~₹42 / ₹169 net (PRD).

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
  API->>API: upsert purchase; recompute entitlement; bump change_seq → app syncs
```

**Verification checks:**
1. `packageName` matches.
2. A line item's `productId`/base plan is one of ours.
3. `subscriptionState ∈ {ACTIVE, IN_GRACE_PERIOD}`. `PENDING` (e.g. UPI inside Play awaiting approval) → `402
   purchase_pending`, and the RTDN completes it later.
4. `externalAccountIdentifiers.obfuscatedExternalAccountId == user.billing_account_ref`. If it differs and that
   account is active → `409 purchase_owned_by_other_account` (the app shows "this purchase belongs to another
   account; sign in with that number"). If that account is deleted, or the field is absent (restore on a new
   install) → link the purchase to the caller and audit-log it.
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
                 (play_year > play_season > upi > referral > promo > trial)
  else if latest play purchase in grace → pro/in_grace; on_hold → free/on_hold
  else plan = free, status = expired|none
  ads_enabled = (plan == free)
  write user_entitlement (bump change_seq) → the app picks it up via sync; a silent FCM `sync` is sent on change
```

A housekeeping tick ends entitlements at `expires_at` (index `entitlement_expiry`). Overlapping grants never lose
time, because expiry is the max over grants.

**Feature gates** (server-side, the only place enforced): lifecycle alert sending, WhatsApp delivery, family alerts,
clash and certificate alerts, ad-free. The API returns `403 pro_required` for Pro-only mutations (family invite,
WhatsApp test). Timeline *viewing* stays free (`alerts_locked`).

## 5. 14-day trial

- **Server-granted** at the first `applied` action (PRD: "14 days of Pro from first Applied"), or explicitly via
  `POST /v1/me/trial`. There is no Play free-trial offer, so no payment method is needed. This matters for Indian
  users without cards or UPI mandates.
- One per account (unique index). Reinstall farming creates a new anonymous account; the exposure is small because
  the costly parts (WhatsApp) need a verified number, and the WhatsApp quota is keyed by `phone_hmac` per 120 days,
  not by account.
- Trial-end: B09 sheet on days 12–14 plus one push on day 13 ("your 3 applications' next stages: …"). Never via
  WhatsApp templates (that would be marketing).

## 6. UPI payment links (WhatsApp test and non-Play channels)

- **Razorpay Payment Links** API: `amount` (₹49/₹199), `upi_link: true`, `reference_id` = our purchase id,
  `customer.contact` (phone from the test), `notes.user_ref`, `expire_by` = +48 h. The link is sent over WhatsApp
  during the 14-day test, or returned by `POST /v1/me/purchases/upi-link` in the non-Play build.
- Webhook `payment_link.paid` → verify `X-Razorpay-Signature` (HMAC-SHA256 with the webhook secret) → idempotent on
  payment id → `purchase(provider=razorpay)` + `entitlement_grant(source=upi, 120 or 365 days)` → WhatsApp confirmation.
- Fees: standard 2% + GST on UPI ([Razorpay pricing](https://razorpay.com/pricing/)), about ₹1.2 on ₹49 vs ₹7.35 to
  Play. New-merchant zero-fee offers may apply (*verify*).
- Refunds: Razorpay dashboard → webhook `refund.processed` → revoke the grant.
- **Policy:** Google Play's payments policy requires Play Billing for digital goods in Play-distributed apps. India
  allows **user-choice / alternative billing** programmes with a reduced service fee, which need enrolment and
  Google's APIs. Until we enrol, the UPI link button exists only in the test/non-Play flavour and on the web
  (`upi_link_purchase` feature flag). **Decision required before P0** (README open question 5).
- During the WhatsApp test (no app), Razorpay payments are matched to test participants by phone. Conversion metrics
  come from `purchase` rows tagged `channel=wa_test`.

## 7. Referrals and promos

- Referral: code per user; the referee redeems within 7 days of sign-up. When the referee completes their **first
  Applied** (inventory S45), both get `entitlement_grant(source=referral, +30 days)`. Cap: 12 rewarded referrals per
  user per year. Anti-abuse: the referee must have a verified phone **or** a Play Integrity `MEETS_DEVICE_INTEGRITY`
  verdict at redeem time, and a referee phone can be rewarded only once ever.
- Promo grants (founding members, support goodwill) are created only in the admin console, with an audit log entry.

## 8. Edge cases

| Case | Handling |
|---|---|
| Purchase succeeded, app killed before verify | app start → `queryPurchasesAsync` → verify; RTDN PURCHASED also grants (we look up the user by `obfuscatedExternalAccountId` → `billing_account_ref`) |
| RTDN arrives before the app's verify call | same upsert path; the app's call returns the already-granted entitlement |
| User deletes account with an active Play sub | the API warns (subscription continues billing in Play); `DELETE /v1/me` response tells the app to show the Play manage link; the purchase is unlinked on hard delete |
| Family invitee (no purchase) | no entitlement needed; the aspirant's Pro gates the family alerts |
| Play Developer API outage | verify returns `503 retryable`; the app keeps the purchase unacknowledged and retries (3-day window) |
| Refund via Google | voided notification → revoke; WhatsApp quota resets to 0 |
| Clock skew on device | server time only |

## 9. Accounting

Purchases are kept (unlinked from deleted users) for statutory retention (*8 years assumed; confirm with a CA*). GST
treatment of Play-collected vs. direct UPI revenue needs a CA's view (Google collects and remits GST on Play sales in
India for some categories; direct Razorpay revenue is ours to invoice). A monthly CSV export from `purchase` feeds
the accounts.
