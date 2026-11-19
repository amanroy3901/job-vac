# Sarkari Match API — conventions for the Android team

Contract: [`openapi.yaml`](./openapi.yaml) (OpenAPI 3.1, validated with `openapi-spec-validator`).
Generate a Retrofit/Ktor client from it or hand-write DTOs with kotlinx.serialization; either way,
**ignore unknown JSON keys** (`Json { ignoreUnknownKeys = true; coerceInputValues = true }`) and map unknown
enum values to the fallback each schema documents.

Backend design: [`../backend/README.md`](../backend/README.md).

## 1. Base URL, versioning, compatibility

| Env | Base URL (placeholder domain) |
|---|---|
| prod | `https://api.sarkarimatch.in` |
| staging | `https://api-staging.sarkarimatch.in` |

- Path version `/v1`. Within v1 the server only makes **additive** changes (new fields, new endpoints, new enum
  values). Breaking changes would ship as `/v2` with v1 kept for at least 6 months.
- `GET /v1/config` returns `min_supported_version` and `update: none|soft|force`. Any call from a version below
  the minimum returns `426` with `code=upgrade_required` → show the blocking update screen (Play In-App Updates,
  IMMEDIATE flow).

## 1a. Identifiers and links (canonical; see [`../contract-alignment.md`](../contract-alignment.md))

| Id | Prefix | Meaning | Shared? |
|---|---|---|---|
| `notice_id` | `n_` | One recruitment advertisement (the DB calls it `recruitment`) | yes (CDN-cached `GET /v1/notices/{id}`) |
| `post_id` | `p_` | One post inside a notice. Eligibility is decided per post, so this is "the job" | yes |
| `match_id` | `m_` | This user × one post (verdict, status, reminders). The design's `jobId` route argument | no |
| `application_id` | `a_` | This user's tracked application, created by Applied | no |
| `event_id` | `e_` | One lifecycle event of a notice (admit card, result…) | yes |

| Link | Opens | Used by |
|---|---|---|
| `https://sarkarimatch.in/j/{match_id}?src=wa\|push\|share` | S21 job detail | WhatsApp buttons, push |
| `https://sarkarimatch.in/p/{post_id}?src=share` | S21 via `GET /v1/me/posts/{post_id}/eligibility` (opens the user's own match if one exists, else a read-only verdict) | job share, anything sent to another person |
| `https://sarkarimatch.in/a/{application_id}` | S23 timeline | lifecycle WhatsApp |
| `https://sarkarimatch.in/f/{token}` | S35 family invite | family invite |
| `https://sarkarimatch.in/pro?plan=year\|season&src=` | S30 | trial-ending push |
| `https://sarkarimatch.in/go/{match_id}` | 302 to the official apply URL (web only, logs the click) | WhatsApp "फ़ॉर्म की साइट खोलें" button (Meta URL buttons need a fixed base URL) |
| `sarkarimatch://job/{match_id}`, `…/digest/{yyyy-mm-dd}`, `…/delivery`, `…/test`, `…/clash/{clash_id}`, `…/certificates`, `…/whatsapp`, `…/plan`, `…/forms`, `…/inbox`, `…/home`, `…/profile` | in-app routes | push content intents (`PushData.link`) |

The domain `sarkarimatch.in` is a placeholder until bought; it is a single build constant on the app and a config value
on the server. `https://sarkarimatch.in/.well-known/assetlinks.json` and the web fallbacks for `/j`, `/p`, `/a`, `/f`,
`/pro` (a "get the app" page) and `/go` are served by Firebase Hosting.

## 2. Headers on every request

| Header | Value |
|---|---|
| `Authorization` | `Bearer <Firebase ID token>` (all `/v1/me/**`, `/v1/session`, `/v1/auth/**`, `/v1/family-invites/**`, `/v1/eligibility/check`, `/v1/notices` list) |
| `X-Firebase-AppCheck` | App Check token (Play Integrity provider; debug provider in debug builds) |
| `X-App-Version` | `BuildConfig.VERSION_CODE` |
| `X-Installation-Id` | Firebase Installations ID |
| `Accept-Language` | `hi` or `en` (only affects `Problem.message` ordering/logs; content always carries both) |
| `Idempotency-Key` | UUIDv4 on every POST/PUT/PATCH/DELETE (see §5) |
| `Accept-Encoding` | `gzip` (OkHttp default) |

## 3. Auth & session

1. First launch: `FirebaseAuth.signInAnonymously()`. The free app works without a phone number, which keeps
   onboarding under 2 minutes.
2. `POST /v1/session` creates the user. Then onboarding → `PUT /v1/me/profile` (or `POST /v1/eligibility/check`
   for the S10 preview before saving).
3. Phone (S11/S12) is optional and done with **our own OTP**, not Firebase phone auth (Firebase bills about
   US$0.07 per SMS to India; WhatsApp authentication/DLT SMS is about ₹0.12–0.25):
   `POST /v1/auth/otp:send` → `POST /v1/auth/otp:verify` → `firebase_custom_token` →
   `FirebaseAuth.signInWithCustomToken()` → `POST /v1/session`. If the number is new, the custom token is for the
   **same UID**, so nothing moves. If it belongs to an existing account (reinstall or new phone),
   `existing_account=true` and you sign into that account (`sync_hint=full`). SMS bodies are SMS Retriever
   formatted, so pass `app_hash`.
4. Google linking (`linkWithCredential`) is also supported as a free recovery option.
5. On `401 token_expired`, call `getIdToken(true)` once and retry. On a second 401, sign out locally.
5a. If the number already has an account with a profile and this phone also has one, `otp:verify` returns
   `409 profile_conflict`; show "कौन-सी प्रोफ़ाइल रखें?" (SC-03) and repeat the verify with
   `merge=discard_local` (keep the old account's) or `merge=keep_local` (keep this phone's).
6. Only the **primary device** gets alert pushes (B15). The newest signed-in device becomes primary;
   `POST /v1/me/devices/{id}:make-primary` switches it back.

WhatsApp opt-in (S33): with a verified phone, `POST /v1/me/whatsapp/opt-in {use_verified_phone:true}` turns it on
at once. For a different number, the response has a `wa.me` link with a prefilled `START <code>`. The user sends it
from WhatsApp and the server binds the number. Poll `GET /v1/me/whatsapp` every 3 s for up to 2 min, or on app
resume, until `state=active`.

Family invitee (S35): the App Link carries a token. Call `GET /v1/family-invites/{token}`, verify the phone with
OTP (`purpose=family_accept`), then `POST …:accept`.

## 4. Errors

RFC 9457 `application/problem+json`:

```json
{ "type": "https://api.sarkarimatch.in/problems/pro_required", "title": "Pro required", "status": 403,
  "code": "pro_required", "message": {"hi": "यह सुविधा Pro में है", "en": "This is a Pro feature"},
  "retryable": false, "request_id": "01JD3Z..." }
```

- Branch on `code`, never on `title`. Show `message[locale]` when present.
- `retryable=true` or status 429/502/503/504 → retry with exponential backoff (1 s, 2 s, 4 s … max 60 s, jitter),
  honouring `Retry-After`. Never auto-retry 4xx other than 408/429.
- `422 validation_failed` carries `errors[]` with field paths for inline form errors.

## 5. Idempotency & the offline action queue

- Generate the `Idempotency-Key` **when the user taps**, persist it with the queued request in Room, reuse it on
  every retry. Server keeps keys 24 h; same key + different body → `422 idempotency_key_reused`.
- Match actions (`applied`, `not_interested`, `snooze`, `seen`, `undo`, `confirm_eligible`) additionally carry `action_id` (UUID) and
  `occurred_at`. Apply them optimistically to Room, queue them, flush with `POST /v1/me/actions:batch`
  (≤ 50) via WorkManager (`NetworkType.CONNECTED`). The server orders by `occurred_at`; stale actions come back
  with `applied=false` and the authoritative `match` — overwrite the local row.
- Notification action buttons ("Applied", "Not interested", "Kal yaad dilao") should enqueue the same action
  without opening the app.

## 6. Offline-first sync

- `GET /v1/me/sync` (no `since`) = full snapshot, paged with `page_token` while `has_more=true`. Persist
  `next_sync_token` only after the last page has been committed to Room in one transaction.
- Then `GET /v1/me/sync?since=<token>` returns only changed rows plus tombstones in `removed`.
- `410 sync_token_expired` → clear the tables named in `details.reset`, full resync.
- Trigger sync on: app start/resume (debounced 5 min), pull-to-refresh, FCM `type=sync`, after `PUT profile`,
  after purchase, and a periodic WorkManager job (12 h, also helps detect missed pushes).
- Shared notice content is fetched separately and **cached by ETag**: `GET /v1/notices/{id}` with
  `If-None-Match`. A match's `notice_etag` tells you when the cached notice is stale. Notices are the same for every
  user, so they are CDN-cached — cheap even when 1M phones open the same notice after a push.
- `GET /v1/reference` and `GET /v1/config` are ETag-cached too; refetch reference when
  `config.reference_version` differs from the cached bundle.

## 7. Pagination & limits

- Cursor pagination: pass `next_cursor` back as `cursor`; `null` = end. Don't parse cursors.
- Default/max page sizes are in the spec (`limit`).
- Rate limits (per user unless noted; `429 rate_limited` + `Retry-After`):

| Scope | Limit |
|---|---|
| All authenticated calls | 120/min, burst 30 |
| `PUT /v1/me/profile` | 20/day |
| `POST /v1/eligibility/check` | 10/min |
| Self-test | 5/hour/device |
| OTP send | 3 per number per 15 min, 5/number/day, 10/installation/day |
| WhatsApp test | 2/day |
| Unauthenticated (`/v1/config`, `/v1/reference`, `/v1/notices/{id}`) | per IP 300/min |

## 8. Dates, money, text

- Instants: RFC 3339 with offset; parse with `OffsetDateTime`. Calendar dates (`last_date`, `dob`) are IST dates
  (`LocalDate`) — never convert them through UTC.
- Money: `{ "amount_paise": 10000, "currency": "INR" }` → ₹100. `fee_for_you: null` = free/not applicable;
  `{amount_paise: 0}` = explicitly no fee (e.g. SC/ST/female).
- All user-facing content: `LocalizedText {hi, en}`; pick by in-app language, fall back to `hi`.
- Every match/notice screen must show `disclaimer` ("Not an official government app…") and the official PDF link.

## 9. Verdicts and the "Why you match" list

- `verdict`: `eligible` | `check_yourself` | `not_eligible`. The feed only contains `eligible` and
  `check_yourself`; `not_eligible` appears only in browse/`/v1/me/posts/{id}/eligibility`.
- `why[]` is ordered for display: each row has `result` (`pass` ✓, `fail` ✗, `check_yourself` ?, `info` i) and
  ready-made `text`. Use `code`/`params` only for analytics or custom formatting; the text is authoritative.
  `fix_field` (when set) is the profile field whose "जोड़ें" button opens S27.
- B06 "मैंने जाँच लिया — मैं योग्य हूँ" sends action `confirm_eligible {rule}`: the verdict stays `check_yourself`,
  reminders continue, `user_confirmed_eligible=true` hides the prompt, and curation sees the confirmation.
- B03 reasons are a multi-select `not_interested_reasons[]`; "मैं योग्य नहीं हूँ" (`not_eligible`) also opens B05.

## 10. Push (FCM) payload contract

All pushes are **data-only** messages (so the app renders in the user's language, can attach action buttons and
can post receipts). High priority is used only for messages that always produce a visible notification
(Android deprioritizes high-priority messages that don't).

Full schema: `components.schemas.PushData` in the spec (all values are strings on the wire).

| key | example | notes |
|---|---|---|
| `type` | `match_new` · `reminder` · `deadline` · `extension` · `corrigendum` · `cancelled` · `lifecycle` · `digest` · `clash` · `certificate` · `account` · `self_test` · `ping` · `sync` | unknown → treat as `sync`. `reminder` = periodic/R3/snooze due; `deadline` = R1/R-last |
| `msg_id` | `d_01JD…` | post receipts with this id |
| `reminder_id` | `r:m_01JD…:d_minus_1:20260731` | deterministic for scheduled reminders (`r:{match_id}:{kind}:{IST date}`); equals `msg_id` for reminder pushes, so the on-device mirror alarm (`next_reminder_id`, `reminder_plan[].reminder_id`) can dedupe |
| `kind` | `d_minus_1` | `ReminderKind` = design slot (new_match R0, periodic Rp, d_minus_3 R3, d_minus_1 R1, last_day R-last) |
| `rank` | `2` | daily-cap rank 1–6; the device never defers ranks 1–3 |
| `template` | `N-04` | design template id (analytics `nid`) |
| `match_id` / `post_id` / `application_id` / `notice_id` / `event_id` / `stage` | | deep-link and action targets |
| `link` | `sarkarimatch://job/m_01JD…?src=push` | content intent (§1a) |
| `title_hi`, `title_en`, `body_hi`, `body_en` | | ≤ 4 KB total payload |
| `priority` | `high` / `normal` | |
| `channel` | `critical` · `new_match` · `reminders` · `job_updates` · `digest` · `account` | Android channel IDs (design 05 §1), **immutable after release**. Unknown → `reminders` |
| `critical` | `1` | lifecycle: critical events go to `critical` |
| `actions` | `applied,snooze` | buttons: `applied`, `snooze`, `not_interested`, `open_site`, `view` |
| `last_date`, `last_date_time` | `2026-08-01`, `23:00` | for "2 din baaki" rendering |
| `apply_url`, `pdf_url` | official URLs | "साइट खोलें" (N-05) opens `apply_url` directly |
| `collapse` | `m_01JD…` | use as notification tag so a newer reminder replaces the older one |
| `digest_date` | `2026-11-19` | `type=digest` (P1) → S44 |
| `test_id` | | `type=self_test` |
| `sync` | `1` | also run a delta sync |

`type=sync` is normal priority and shows nothing. `type=self_test` shows N-41 "टेस्ट सूचना ✓" on the `critical`
channel (it must prove that heads-up alerts arrive).
`type=ping` (weekly at about 12:00 IST) is a **silent, high-priority** data message used for the delivery health
check (SC-35). The app must post a `received` receipt (expedited WorkManager) and show nothing. Two missed pings mark
the device `failing` (O02 banner; for Pro, WhatsApp becomes the default for 1-day-before and last-day). One silent
high-priority message a week is well within Android's tolerance for high-priority messages that show nothing.

**Receipts:** in `onMessageReceived`, enqueue an expedited WorkManager job that posts `received` + `displayed`
to `POST /v1/me/delivery-receipts`; post `opened`/`action_tapped` when the user interacts. For Pro users the server
escalates critical alerts to WhatsApp when no `received` receipt arrives within the escalation window (15–60 min),
so prompt receipts save the user from duplicate messages.

## 11. Device registration & self-test

- `GET /v1/me/devices` lists the account's devices (S38 count, B15 device name).
- `PUT /v1/me/devices/{installation_id}` on: first session, `onNewToken`, app start when any of
  (token, notification permission, blocked channels, battery-optimization state, app version) changed.
- Onboarding permission step: after notification permission + OEM autostart guide, call
  `POST …/self-tests`, show a spinner, and pass when the test notification arrives (or poll `GET …/self-tests/{id}`).
  On `timeout`, show `advice[]` steps. Repeat weekly (WorkManager, `config.self_test_interval_days`).

## 12. Payments

- Show prices from Play `ProductDetails`, never hard-coded. `GET /v1/products` maps our plan ids to Play
  product/base-plan ids.
- Launch billing with `setObfuscatedAccountId(user.billing_account_ref)`.
- After purchase (and on every app start for any unacknowledged purchase) call
  `POST /v1/me/purchases/google-play`. **Do not acknowledge on the client** — the server does it after verifying.
- Gate Pro UI on `GET /v1/me/entitlement` (cached in Room, refreshed by sync). Never on local purchase state.
- **Trial is opt-in.** It is *offered* at the first Applied and *starts* when the user taps the trial CTA: the
  `applied` result carries `paywall.variant=trial_offer`, B02 shows "14 दिन मुफ़्त शुरू करें", and the tap calls
  `POST /v1/me/trial` (14 days from the tap, no payment method). Once used, the hint is `plans`. Pro and trial users
  get no hint.
- `GET /v1/me/purchases` lists receipts for S32 (no purchase tokens).

## 13. Ads (free tier)

Show the single native ad slot (feed bottom, digest screen) only when
`config.ads.enabled && entitlement.ads_enabled && placement ∈ config.ads.placements`, and not during the first
`config.ads.frequency.hide_on_first_sessions` sessions. `entitlement.ads_enabled` is false for every Pro source,
including trial and referral, so the trial demonstrates "no ads". Flipping `config.ads.enabled` to false is the
remote kill switch. Ads are never placed in notifications, WhatsApp, the what-you-need card or the why-you-match list.

## 14. Health / outage

`GET /v1/health` (no auth, cached 30 s) returns `up|degraded|down` and `alerts_delayed` for O03/S42. Check it
after 3 consecutive 5xx/timeouts, not on every start.

## 15. Not in this contract

`/admin/**` (curation console), `/hooks/**` (Play RTDN via Pub/Sub push, WhatsApp, Razorpay, SMS DLR) and
`/internal/**` (scheduler ticks) are server-only and protected separately. The web routes `/j`, `/p`, `/a`, `/f`,
`/pro` and `/go` (§1a) are served by Firebase Hosting, not by the app API.

**Deferred on purpose (PRD priorities):**

| Need | Priority | Plan |
|---|---|---|
| Age-limit countdown S43 ("2 मौके और") | P2 | `GET /v1/me/age-chances` designed with the P2 attempt rules; not in v1 |
| Age-crossed sheet B14 / N-43 | P1 | ships with P1 as `type=account, template=N-43`, opening B14 from the inbox item; the exam list comes from `listNotices` (open notices with the user's verdict) |
| Daily digest S44 / N-30 | P1 | no new endpoint: `InboxItem.digest` + `PushData.digest_date`, rows from Room |
| Certificate validity S36 | P1 | `RuleExplanation.rule=certificate` lines + `type=certificate` push |
| Personalised ads | — | consent purpose `ads_personalisation` reserved; never requested in v1 |
| Browse all notices (`listNotices`) | P1 | used by B14; no P0 screen |
