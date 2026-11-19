# Sarkari Match API — conventions for the Android team

Contract: [`openapi.yaml`](./openapi.yaml) (OpenAPI 3.1, validated with `openapi-spec-validator`).
Generate a Retrofit/Ktor client from it or hand-write DTOs with kotlinx.serialization; either way,
**ignore unknown JSON keys** (`Json { ignoreUnknownKeys = true; coerceInputValues = true }`) and map unknown
enum values to the fallback each schema documents.

Backend design: [`../backend/README.md`](../backend/README.md).

**Founder decisions of 2026-11-19 change the shape of this API** ([`../PRD.md`](../PRD.md)):

| Decision | Effect on the contract |
|---|---|
| Data minimisation (ADR-11) | The eligibility profile, matching, matches, reminders, actions, applications and inbox live **on the device**. The API has **no profile, match, sync, action, application, inbox, prefs or receipt endpoints**. The app reads a public **catalog** (§6) and evaluates it locally with `:matching-core`. |
| Login | Google Sign-In (Credential Manager → Firebase Auth Google provider) or guest. Guests have **no account** until a server feature needs one. No OTP, no phone. |
| Payments | Play Billing only (no UPI link endpoints). |
| No WhatsApp / SMS | No WhatsApp endpoints, consents or templates. Delivery = FCM + on-device reminders. |
| Languages | `LocalizedText` covers 13 Indian languages; hi + en enabled at launch; `?locales=` on catalog files. |

## 1. Base URL, versioning, compatibility

| Env | Base URL (placeholder domain) |
|---|---|
| prod | `https://api.sarkarimatch.in` |
| staging | `https://api-staging.sarkarimatch.in` |

- Path version `/v1`. Within v1 the server only makes **additive** changes (new fields, new endpoints, new enum
  values). Breaking changes would ship as `/v2` with v1 kept for at least 6 months.
- `GET /v1/config` returns `min_supported_version` and `update: none|soft|force`. Any call from a version below
  the minimum returns `426` with `code=upgrade_required` → show the blocking update screen (Play In-App Updates,
  IMMEDIATE flow). The catalog carries `evaluator_min_version` per rule; an older evaluator shows the post as
  `check_yourself` with "update the app" instead of guessing.

## 1a. Identifiers and links (canonical; see [`../contract-alignment.md`](../contract-alignment.md))

| Id | Prefix | Meaning | Who issues it |
|---|---|---|---|
| `notice_id` | `n_` | One recruitment advertisement (DB: `recruitment`) | server (catalog) |
| `post_id` | `p_` | One post inside a notice; eligibility is per post ("the job") | server (catalog) |
| `match_id` | `m_` | This device's match on one post. **Derived**: `m_` + post id without `p_`. The design's `jobId` route argument | device |
| `application_id` | `a_` | This device's tracked application. **Derived**: `a_` + post suffix | device |
| `event_id` | `e_` | One lifecycle event of a notice | server (catalog) |
| `link_id` | `f_` | One family link | server |

Because `match_id` is derived, the same link or reminder id means the same job on every device and in topic pushes,
without the server knowing who matched.

| Link | Opens | Used by |
|---|---|---|
| `https://sarkarimatch.in/j/{match_id}?src=push\|share` | S21 job detail (evaluated locally; "not in your matches" state if the profile does not match) | push, in-app share to self |
| `https://sarkarimatch.in/p/{post_id}?src=share` | S21 for that post, evaluated against the viewer's own local profile (`getNotice` if the post is not in the local catalog) | job share to another person |
| `https://sarkarimatch.in/a/{application_id}` | S23 timeline | lifecycle notifications |
| `https://sarkarimatch.in/f/{token}#k={key}` | S35 family invite (QR on S48 encodes the same URL) | family invite |
| `https://sarkarimatch.in/pro?plan=year\|season&src=` | S30 | trial-ending push |
| `sarkarimatch://job/{match_id}`, `…/digest/{yyyy-mm-dd}`, `…/delivery`, `…/test`, `…/clash/{clash_id}`, `…/certificates`, `…/plan`, `…/forms`, `…/inbox`, `…/home`, `…/profile`, `…/privacy`, `…/family/following` | in-app routes | notification content intents |

`/go/{match_id}` (WhatsApp URL-button redirect) and `sarkarimatch://whatsapp` are retired. The domain is a
placeholder; `assetlinks.json` and web fallbacks for `/j`, `/p`, `/a`, `/f`, `/pro` are served by Firebase Hosting.
The `/f/` web page must keep the `#k=` fragment client-side (never log or forward it).

## 2. Headers

| Header | Value |
|---|---|
| `Authorization` | `Bearer <Firebase ID token>` on `/v1/session`, `/v1/me/**`, `/v1/family/**`, family accept. Never on catalog/config/reference/health |
| `X-Firebase-AppCheck` | App Check token (Play Integrity) on every non-public call; **limited-use** tokens on `/v1/reports`, `/v1/telemetry`, `/v1/push-tests`, `/v1/me/trial`, purchases, referral redeem, family accept |
| `X-App-Version` | `BuildConfig.VERSION_CODE` |
| `X-Installation-Id` | Firebase Installations ID — **account calls only** (never on reports/telemetry/push-tests, which must stay unlinkable) |
| `Accept-Language` | the app language (one of the 13 codes); selects `Problem.message` and the default `locales` of catalog files |
| `Idempotency-Key` | UUIDv4 on every POST/PUT/PATCH/DELETE (see §5) |
| `Accept-Encoding` | `gzip` (OkHttp default) |

## 3. Auth & accounts

1. **Guest by default, no account.** First launch signs in to nothing. Matching, reminders, topic pushes, reports,
   self-test and the catalog work without any account.
2. **Google Sign-In** (S03 link "पहले इस्तेमाल किया है?", B16 variants, S10 "Google से सेव करें", S26/S38): Credential
   Manager `GetSignInWithGoogleOption` (button) or `GetGoogleIdOption` (bottom sheet O11) → Google ID token →
   `GoogleAuthProvider.getCredential(idToken)` → `FirebaseAuth.signInWithCredential` (or
   `currentUser.linkWithCredential` when a guest already has an anonymous account) → `POST /v1/session`.
   - `FirebaseAuthUserCollisionException` on link (that Google account already has an account) → sign in to it
     instead: keep the anonymous account's last ID token, sign in with the Google credential, then call
     `POST /v1/session {merge_anonymous_id_token}` — the server moves its grants, purchases and family links over.
   - `SessionResponse.has_backup=true` and this phone has a local profile → **D10** (keep the saved profile =
     restore the backup; keep this phone's = overwrite the backup). No server-side merge, no 409.
3. **Anonymous account** only when a guest uses a server feature without signing in: trial, purchase, family
   accept. `FirebaseAuth.signInAnonymously()` → `POST /v1/session`. B16 offers Google first ("so you keep Pro on a
   new phone").
4. **Minor mode** (on-device DOB < 18): never call `/v1/session`, never offer sign-in, purchase, trial, backup,
   family owner, report attributes, telemetry; no ads. Re-enabled automatically at 18.
5. On `401 token_expired`, call `getIdToken(true)` once and retry. On a second 401, sign out locally.
6. **Alert device (B15/N-47).** An account's newest restored/signed-in install becomes primary; the others get an
   `account` push N-47 and go quiet (stop local reminders, unsubscribe `p_`/`n_` topics, keep data).
   `POST /v1/me/devices/{id}:make-primary` switches back.
7. **Switch Google account (D11):** sign out → wipe account-scoped local state (entitlement, backup key reference,
   family links) but **keep** the local profile and matches unless the user also chooses "सब कुछ हटाएँ".

Family member (S35): App Link `https://sarkarimatch.in/f/{token}#k={key}` → `GET /v1/family-invites/{token}` →
`POST …:accept` (anonymous account if not signed in; no OTP) → S13 family variant → S49.

## 4. Errors

RFC 9457 `application/problem+json`:

```json
{ "type": "https://api.sarkarimatch.in/problems/pro_required", "title": "Pro required", "status": 403,
  "code": "pro_required", "message": {"ta": "இது Pro வசதி", "en": "This is a Pro feature"},
  "retryable": false, "request_id": "01JD3Z..." }
```

- Branch on `code`, never on `title`. Show `message` in the app language, falling back per `languages[].fallback`.
- `retryable=true` or status 429/502/503/504 → retry with exponential backoff (1 s, 2 s, 4 s … max 60 s, jitter),
  honouring `Retry-After`. Never auto-retry 4xx other than 408/429.
- `422 validation_failed` carries `errors[]` with field paths.

## 5. Idempotency

- Generate the `Idempotency-Key` **when the user taps**, persist it with the queued request in Room, reuse it on
  every retry. Server keeps keys 24 h; same key + different body → `422 idempotency_key_reused`.
- User actions on matches (Applied, Not interested, Snooze, Seen, Undo, Confirm eligible) are **local Room writes**
  and never reach the server. The only queued server writes are account-level: backup upload, family watch-list,
  consents, reports, telemetry, device upsert.

## 6. Catalog sync (replaces `/v1/me/sync`)

- `GET /v1/catalog/head` (public, 60 s CDN TTL) → `seq`, `server_time`, snapshot, batch list with `sha256` per
  locale set.
- Empty local catalog or `local_seq < snapshot.seq` → `GET /v1/catalog/snapshots/{seq}?locales=…`; then every batch
  `GET /v1/catalog/batches/{seq}?locales=…` with `seq > local_seq`, **in order**, verifying `sha256`, each applied in
  one Room transaction, followed by local matching (`:matching-core`) and reminder planning.
- `locales` = the app language + its fallback chain (e.g. `en,hi,mr` for Marathi). Changing the app language
  re-fetches the snapshot for the new set (cached by the CDN per set).
- Triggers: FCM topic `catalog` (`type=sync`, normal priority), app start/resume (debounced 5 min), pull-to-refresh,
  daily reconcile worker (also re-arms reminders and expires matches).
- `GET /v1/config` and `GET /v1/reference` are public and ETag-cached; refetch reference when
  `config.reference_version` differs; apply `reference.code_map` to the stored profile.
- Clock: if `|device − server_time| > 10 min`, schedule with the server date and show the clock banner.

## 7. Rate limits

`429 rate_limited` + `Retry-After`. Keys: account, installation, IP /24 (in memory only).

| Scope | Limit |
|---|---|
| Account calls | 120/min, burst 30 |
| `PUT /v1/me/backup` | 30/day |
| `PUT /v1/me/family/{id}/watchlist` | 60/day per link |
| `POST /v1/reports` | 10/day per installation |
| `POST /v1/telemetry` | 2/day per installation |
| `POST /v1/push-tests` | 5/hour per installation |
| Public files (`/v1/catalog/**`, `/v1/config`, `/v1/reference`, `/v1/health`, `/v1/notices/{id}`) | CDN; origin per IP 300/min |

## 8. Dates, money, text

- Instants: RFC 3339 with offset; calendar dates (`last_date`, rule `cutoff_date`) are IST dates (`LocalDate`).
- Money: `{ "amount_paise": 10000, "currency": "INR" }` → ₹100.
- All user-facing content: `LocalizedText` map. Pick the app language, else walk `languages[].fallback`, else `en`.
  Never show a raw key. Notification and UI text come from app string resources in the app language.
- Every job screen shows the disclaimer ("Not an official government app…") and the official PDF link.

## 9. Verdicts and "Why you match" (on device)

`:matching-core` returns `verdict` (`eligible | check_yourself | not_eligible`) and an ordered `RuleExplanation[]`
(documented in the spec as a schema for reference; never returned by the server). `fix_field` drives the "जोड़ें"
button → S27. B06 "मैंने जाँच लिया — मैं योग्य हूँ" is a local flag. B03 not-interested reasons are local; with the
user's opt-in they are counted in the usage counters. B05 "wrong match" → `POST /v1/reports` (§13).

## 10. Push (FCM) payload contract

All pushes are **data-only** and carry **no text**: the app renders templates in its language and can add action
buttons. Full schema: `components.schemas.PushData`.

| Sent by the server | Target | `type` | Priority | Notes |
|---|---|---|---|---|
| Catalog changed | topic `catalog` (every install) | `sync` (+ `seq`) | normal | fetch head → batches → match locally → compose N-01/N-10… locally |
| R3 / R1 / R-last backup | topic `p_{post_id}` (installs with an open match; family followers) | `reminder` (`d_minus_3`) / `deadline` (`d_minus_1`, `last_day`) | normal / high / high | `reminder_id` = `r:{match_id}:{kind}:{YYYYMMDD}`; the device shows it at the user's slot if later, dedupes with the on-device backup |
| Lifecycle event | topic `n_{notice_id}` (Applied + Pro/trial; family followers) | `lifecycle` | high if `critical=1` | device filters by its application's post/region/progress |
| Account | the account's devices | `account` (`template` N-31, N-33, N-47, `entitlement`) | normal | `fallback_title/body` from `notification_text` in the device locale |
| Family link | owner's/member's devices | `family` (`family_event`) | normal | `sync` → fetch watch-list; `removed` → N-54 |
| Self-test | one FCM token | `self_test` (+ `test_id`) | high | N-41 on `critical` |

Composed **on the device only** (never over FCM): `match_new`, `extension`, `corrigendum`, `cancelled`, periodic and
snooze reminders, `digest`, `clash`, `certificate`, family alerts N-50…N-53 on a member's phone. Channel ids are the
design's seven: `critical`, `new_match`, `reminders`, `job_updates`, `digest`, `account`, `family`.

Topic membership is managed by the app with `FirebaseMessaging.subscribeToTopic`: `catalog` always; `p_{post_id}`
while a match is open (and the install is the account's primary, if signed in); `n_{notice_id}` after Applied with a
valid Pro/trial `Entitlement.token`. There are **no delivery receipts and no weekly ping**; the server never learns
who received what.

## 11. Devices and self-test

- `PUT /v1/me/devices/{installation_id}` **for accounts only** (sign-in, `onNewToken`, locale change). Guests never
  register.
- Self-test (S16, S29, weekly): `POST /v1/push-tests {fcm_token}` → the app shows N-41 when the message arrives and
  calls `POST /v1/push-tests/{id}:received`; `GET /v1/push-tests/{id}` gives `received` or `timeout` (120 s; UI shows
  "अभी भी इंतज़ार…" at 60 s). Advice (autostart, battery, channel, Play services) is computed on the device.

## 12. Payments (Play Billing only)

- Show prices from Play `ProductDetails`, never hard-coded. `GET /v1/products` maps our plan ids to Play
  product/base-plan ids, `billing_period` (`P4M`, `P1Y`) and `renews`.
- Products: subscription `pro` with base plans `season-4m-prepaid` (₹49, prepaid, no auto-renew, top-up allowed) and
  `yearly-autorenew` (₹199). UPI, cards and Play balance are offered inside Play's sheet (O06).
- Needs an account (Google, or anonymous for a guest). Launch billing with
  `setObfuscatedAccountId(user.billing_account_ref)`.
- After purchase (and on every app start for any unacknowledged purchase) call `POST /v1/me/purchases/google-play`.
  **Do not acknowledge on the client.**
- Gate Pro on `GET /v1/me/entitlement` (cached in Room with its signed `token`; verify offline with
  `config.entitlement_token_jwks`). Never on local purchase state.
- **Trial is opt-in**: offered at the first Applied (B02, decided on the device while `trial_available`), started by
  `POST /v1/me/trial`.
- Restore (S32/D08): `queryPurchasesAsync` → verify each → `getEntitlement`. A different active account owns it →
  `409 purchase_owned_by_other_account` ("sign in with the Google account you used then").

## 13. Reports, usage counters, backup, family

- **Reports (B05):** `POST /v1/reports` — post, rule version, reason, rule line; `attributes` only with the per-report
  tick, never in minor mode. No auth; no installation id.
- **Usage counters:** `POST /v1/telemetry` once a day, only after the user opted in on S47/S38; no identifiers;
  never in minor mode.
- **Backup (Google-linked accounts, opt-in):** `PUT /v1/me/backup` with `If-Match` (412 `backup_conflict` → fetch,
  merge per-row last-writer-wins locally, retry). Key in Block Store; offered only when the phone has a screen lock.
- **Family (Pro, P1):** owner `POST /v1/me/family` → append `#k=` → share/QR (S48); after `family_event=accepted`,
  `PUT /v1/me/family/{link_id}/watchlist` after every relevant local change. Member: `GET /v1/family/following`,
  `GET …/{link_id}/watchlist` on `family_event=sync`, `DELETE …/{link_id}` to stop (D12).

## 14. Ads (free tier, adults only)

Show the single native ad slot (feed bottom, digest screen) only when
`config.ads.enabled && entitlement.ads_enabled (guests: true) && placement ∈ config.ads.placements && !minorMode &&
UMP canRequestAds()`, and not during the first `config.ads.frequency.hide_on_first_sessions` sessions. Requests are
always non-personalised (`npa=1`). Never in notifications, the what-you-need card or why-you-match.

## 15. Health / outage

`GET /v1/health` (no auth, cached 30 s) returns `up|degraded|down` and `alerts_delayed` for O03/S42. The app keeps
working from its local catalog during an outage; check health after 3 consecutive failures, not on every start.

## 16. Not in this contract / removed

`/admin/**` (curation console), `/hooks/play-rtdn` (Pub/Sub push) and `/internal/**` (scheduler ticks) are
server-only. **Removed on 2026-11-19:** `/v1/auth/otp:*`, `/v1/me/profile`, `/v1/eligibility/check`,
`/v1/me/sync`, `/v1/me/matches/**`, `/v1/me/actions:batch`, `/v1/notices` (list), `/v1/me/posts/{id}/eligibility`,
`/v1/me/applications/**`, `/v1/me/clashes`, `/v1/me/notification-prefs`, `/v1/me/notifications`,
`/v1/me/delivery-receipts`, `/v1/me/devices/{id}/self-tests/**` (→ `/v1/push-tests`), `/v1/me/exports/**` (→ local
export + `GET /v1/me`), `/v1/me/purchases/upi-link/**`, `/v1/me/whatsapp/**`, `/v1/me/family/{member_id}` (→
`{link_id}`).

**Deferred (PRD priorities):** age countdown S43 (P2, needs attempt rules in the catalog); age-crossed B14/N-43 (P1,
local); digest S44/N-30 (P1, local); certificate validity S36 (P1, local); browse all notices (P1, from the local
catalog).
