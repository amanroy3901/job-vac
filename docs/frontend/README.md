# Sarkari Match — Android frontend architecture

Status: design-level architecture v1 (2026-11-19). Inputs: `docs/PRD.md`, `docs/design/01–06 + tokens.json`, `docs/api/openapi.yaml` + `docs/api/README.md`, `docs/backend/README.md`.

Stack: **Kotlin 2.4 · Jetpack Compose (Material 3, BOM 2026.11.01) · Navigation Compose 2.10 (type-safe) · Hilt · Coroutines/Flow · Room 2.8 · DataStore · WorkManager 2.12 · Retrofit 3 + OkHttp 5 + kotlinx.serialization · Firebase (Auth, FCM, App Check, Crashlytics, Analytics) · Play Billing 9.1 · Google Mobile Ads (native, free tier only)**. minSdk 26, target 36, Hindi first.

## Documents

| File | What |
| --- | --- |
| [navigation.md](navigation.md) | Every inventory screen → route → ViewModel → UiState; graphs; deep links; back stack; process death |
| [data-layer.md](data-layer.md) | Repositories, Room entities/DAOs, DataStore, sync, offline action queue, auth/session, **repository → operationId table** |
| [state-management.md](state-management.md) | UDF pattern; UiState/UiAction/UiEffect; examples S20 Home, S21 Job detail, S30 Paywall |
| [notifications.md](notifications.md) | FCM data messages, channels, action buttons → Room → WorkManager → API, local reminder mirror, quiet hours/cap, OEM autostart, self-test |
| [billing.md](billing.md) | Play Billing flow, server verification, entitlement cache, restore, paywall triggers, UPI policy |
| [ads.md](ads.md) | Free-tier native ad slot (feed bottom + digest), lazy SDK init, entitlement + consent gating |
| [i18n-and-design-system.md](i18n-and-design-system.md) | Per-app language, IST dates/₹, Devanagari fonts; tokens → Compose theme; component catalogue |
| [performance-accessibility.md](performance-accessibility.md) | Size/startup budgets, R8, baseline profiles, Compose hygiene, low-RAM, a11y; analytics/crash, flags, force update, DPDP privacy |
| [testing-ci.md](testing-ci.md) | Test pyramid, fakes/fake backend, Roborazzi, migrations, GitHub Actions |
| [project-skeleton.md](project-skeleton.md) | Version catalog (verified versions), module/package layout, phased tickets |

## 1. Architecture in one picture

```mermaid
flowchart LR
  subgraph Device
    UI["Compose screens<br/>(stateless XScreen)"] -- UiAction --> VM["ViewModel<br/>StateFlow&lt;UiState&gt; + Effects"]
    VM -- UiState --> UI
    VM --> REPO["Repositories (:core:data)"]
    REPO -- "observe Flow" --> ROOM[("Room = source of truth")]
    REPO -- "optimistic write + enqueue" --> Q[("pending_actions / outbox")]
    Q --> WM["WorkManager<br/>ActionFlush, Sync, Outbox,<br/>Receipts, Device, SelfTest"]
    WM --> NET["SmApi (Retrofit/OkHttp)<br/>auth · App Check · idempotency"]
    FCM["FirebaseMessagingService<br/>(data-only)"] --> PH["PushHandler · DeliveryGate"]
    PH --> NOTIF["Notifications<br/>(channels, actions)"]
    NOTIF -- "भर दिया / रुचि नहीं / बाद में" --> RCV["ActionReceiver"] --> REPO
    PH --> WM
    ALARM["Local reminder mirror<br/>(WorkManager + inexact alarms)"] --> PH
    BILL["Play Billing"] --> REPO
    ADS["Ads SDK (lazy, free tier)"] --> UI
  end
  NET <--> API["Backend /v1 (Cloud Run)"]
  API -- "FCM data push" --> FCM
  API -- "WhatsApp (Pro)" --> WA((User's WhatsApp))
```

**How UI ↔ data ↔ backend connect:** screens render only `UiState` from their ViewModel; ViewModels combine Room `Flow`s (matches, detail, notices, applications, entitlement, prefs) with connectivity/config; every user command goes to a repository that writes Room first (optimistic) and queues the network call (idempotent, `action_id` / `Idempotency-Key` persisted at tap time). `SyncEngine` pulls `/v1/me/sync` deltas into Room (on start, push, pull-to-refresh, every 12 h). Pushes are data-only; the device renders them in the user's language, enforces quiet hours / cap / "already acted", and handles action buttons without opening the app. The server owns eligibility, schedules and entitlement; the app never decides a verdict or Pro status itself.

## 2. Modules

```mermaid
flowchart TD
  app[":app"]
  subgraph features
    onb[":feature:onboarding<br/>S02–S12, S27, B10"]
    alerts[":feature:alerts<br/>S13–S16, S28, S29"]
    jobs[":feature:jobs<br/>S20, S21, S24, S25, S44, B01–B07"]
    apps[":feature:applications<br/>S22, S23, S37"]
    pro[":feature:pro<br/>S30–S33, B02, B08, B11"]
    prof[":feature:profile<br/>S26, S34–S40, S43, S45, S46"]
  end
  subgraph core
    ds[":core:designsystem"]
    data[":core:data"]
    notif[":core:notifications"]
    bill[":core:billing"]
    ads[":core:ads"]
    net[":core:network"]
    db[":core:database"]
    model[":core:model (JVM)"]
    testing[":core:testing"]
  end
  bp[":baselineprofile"]
  app --> onb & alerts & jobs & apps & pro & prof
  app --> notif & bill & ads & data & ds
  onb & alerts & jobs & apps & pro & prof --> ds & data & model
  alerts --> notif
  jobs --> ads
  pro --> bill
  data --> net & db & model
  notif --> data
  bill --> model
  ads --> ds & data
  ds --> model
  net --> model
  db --> model
  bp -.-> app
  testing -.->|testImplementation| data & features
```

16 modules (+ `build-logic`). Why not fewer: a solo dev pays for modules once (convention plugins), and gets incremental builds, enforced boundaries (features can't reach each other or Retrofit/Room), and per-module screenshot tests. Why not more (e.g. `:core:datastore`, `:feature:settings`, `:feature:family`, `:feature:matchdetail` separately): those would split code that changes together; family (P1) lives as a package in `:feature:profile` and can be extracted later. Feature modules never depend on each other — cross-feature navigation is via callbacks wired in `:app` (navigation.md §1).

## 3. Key decisions

| # | Decision | Alternatives | Why |
| --- | --- | --- | --- |
| D1 | MVVM + UDF, one `StateFlow<UiState>` per screen, effects via `Channel` | MVI framework (Orbit, Mavericks) | Zero extra deps; matches Compose guidance; easy to test with Turbine |
| D2 | Hilt | Koin, manual DI | Compile-time safety, first-class ViewModel/WorkManager integration (`hilt-work`) |
| D3 | Navigation Compose 2.10 type-safe, nested graphs per tab | Navigation 3 | Built-in deep links with synthesised back stacks + multiple back stacks; Nav 3 later if needed |
| D4 | **Offline-first: Room is the source of truth**; delta sync via `/v1/me/sync` tokens; ETags for notices/config/reference | network-first with cache | Slow/flaky networks; push tap must open offline; one sync call instead of many |
| D5 | Optimistic actions in `pending_actions` + `actOnMatchesBatch` via expedited WorkManager; `action_id` generated at tap | direct API call | Notification buttons must work offline and without opening the app; server orders by `occurred_at` |
| D6 | Data-only FCM + device-rendered notifications + local reminder mirror (WorkManager + inexact alarms; exact alarms only as P1 opt-in) | notification messages; exact alarms by default | Hindi/English, action buttons, dedupe, quiet hours; OEMs kill FCM; Android 14 exact-alarm policy |
| D7 | Firebase Auth anonymous → backend OTP → custom token; no own token storage | Firebase phone auth; own JWT store | Matches backend contract (cheaper OTP via WhatsApp/DLT); SDK handles refresh; nothing secret to encrypt locally |
| D8 | Retrofit 3 + OkHttp 5 + kotlinx.serialization, hand-written DTOs + CI contract diff | Ktor client; OpenAPI generator | Smallest dependency surface; generator adds build complexity for ~40 DTOs |
| D9 | Server-authoritative entitlement; client never acknowledges purchases | client-side entitlement | API contract; fraud-resistant; one truth across devices |
| D10 | Mukta via downloadable fonts, system Noto fallback | bundled subset | 0 KB in APK; Noto Devanagari exists on all Android 8+ |
| D11 | Tokens hand-mapped to Compose + `TokensParityTest` against `tokens.json` | Style Dictionary codegen | Small token set; test catches drift both ways |
| D12 | No Paging, no image loader, no Remote Config SDK, no SQLCipher | — | Small volumes, no rasters, `/v1/config` has flags, FBE + data minimisation |
| D13 | Native ad via Google Mobile Ads Next-Gen SDK, lazily initialised on first slot scroll-in, never for Pro, gated by `config.ads` + `entitlement.ads_enabled` | classic SDK; mediation | PRD free-tier light ads without hurting startup; kill switch server-side |
| D14 | Roborazzi (JVM) screenshots in Hindi/English × light/dark × font 1.0/2.0 | Paparazzi; device screenshots | Runs in CI without emulators; Robolectric shared with Compose UI tests |

## 4. Size & performance targets

Download ≤ 8 MB without ads / ≤ 10 MB with the ads SDK (PRD < 15 MB); cold start TTID ≤ 2.0 s on Galaxy A03-class 2 GB phones; feed jank < 5 %; notification action → Room < 100 ms. Details in performance-accessibility.md.

## 5. Contract gaps (found while reconciling with docs/design/* and docs/api/* on 2026-11-19)

Resolved during reconciliation (no action needed): phone OTP (backend added `sendOtp`/`verifyOtp`), family invitee S35 (`getFamilyInvite`/`accept`/`decline`), primary device B15 (`makeDevicePrimary` + `Device.primary`), outage S42 (`getHealth`), snooze "आज शाम" (`today_evening`), WhatsApp opt-in S33 (`use_verified_phone` + wa.me), ads gating (`AppConfig.ads`, `Entitlement.ads_enabled`), B01 "फ़ीस भी भर दी" (`MatchActionInput.fee_paid`).

Open:

| ID | Between | Gap | Proposed resolution |
| --- | --- | --- | --- |
| G-02 | Design B02 ↔ API | Design B02 asks the user to **opt in** ("14 दिन मुफ़्त शुरू करें" / "अभी नहीं"); API **auto-starts** the trial on the first `applied` and returns `paywall.variant=trial_started`. | Pick one. Recommend: API returns `paywall.variant=trial_offer` without starting; client calls `startTrial` on the CTA (endpoint exists). Until decided, client shows B02 as "trial started" info when the API auto-starts. |
| G-03 | Design routes ↔ API ids | Inventory uses `job/{jobId}`, `applied/{jobId}`, links `/j/{jobId}`, `/a/{jobId}`; API has user-specific `match_id`, `application_id` and shared `post_id`/`notice_id`. Share links (S46, referral) can't use a per-user `match_id`. | Define: `/j/{match_id}` for per-user WhatsApp/push links, `/a/{application_id}` for timelines, and a new `/p/{post_id}` for shared links (client falls back to `getMyEligibilityForPost`). Server WhatsApp templates must use these ids. |
| G-04 | Design 05 ↔ API push contract | Channel IDs differ: design `critical, new_match, reminders, job_updates, digest, account`; API `deadline_critical, new_match, reminders, lifecycle, digest, system`. Channel IDs are permanent once shipped. | Adopt design IDs in the push `channel` field before release. Client maps both meanwhile (notifications.md §2). |
| G-05 | Design 05 N-05 ↔ API push | "साइट खोलें" action needs the official apply URL; the payload has none. | Add `apply_url` (and `pdf_url`) to `match_new`/`deadline` payloads; client falls back to cached `match_detail.links`. |
| G-06 | Design B03 ↔ API `not_interested_reason` | Design reasons: पोस्ट पसंद नहीं, जगह दूर है, तैयारी नहीं, पहले से नौकरी है, मैं योग्य नहीं हूँ (→ B05). API enum: `not_interested_in_post, location, salary, already_applied_elsewhere, other`. | Align enum to design: add `not_prepared`, `already_employed`; drop or hide `salary`/`already_applied_elsewhere`; "मैं योग्य नहीं" opens B05 (`reportMatch`). |
| G-08 | Design 03 §0.2 ↔ API push/prefs | (a) No `rank` in push payload for the on-device cap/quiet-hours mirror; (b) no deterministic reminder id to dedupe FCM vs local-mirror reminders; (c) user "reminder time" (default 19:00, used by Rp/R3/R1 slots) is not in `NotificationPrefs`. | Add `rank` and `slot` (`R0, Rp, R3, R1, Rlast, …`) to the payload; make `msg_id` = `{match_id}:{slot}:{ist_date}` or add `reminder_key`; add `reminder_time` to `NotificationPrefs`. |
| G-09 | Design B06 ↔ API actions | B06 "मैं योग्य हूँ — अलर्ट चालू रखें" (user confirms a check-yourself rule) has no action type. | Add action `confirm_eligible {rule}` (keeps reminders, logs for curation) or treat as `seen` and drop the button. |
| G-10 | Design S44 / N-30 ↔ API | Digest screen `digest/{date}` has no content endpoint; inbox only has `kind=digest` items. | Either add `GET /v1/me/digests/{date}` or include `match_ids[]` in the digest inbox item; client renders from Room. |
| G-11 | Design S43 + B14 ↔ API | Age-limit countdown ("2 मौके और") and age-crossed sheet have no data source (P2/P1). | Add `GET /v1/me/age-chances` or an `age_chances` block in sync. |
| G-12 | Design S36 ↔ API | Per-job certificate validity verdict + renewal reminder (`ValidityBadge`) not in `MatchDetail`. | Add `certificate` rule results to `why[]` (rule enum has no `certificate`) or a `certificate_checks[]` field. |
| G-13 | Design S38 ↔ API | "linked devices (count)" — no device list endpoint. | Add `GET /v1/me/devices` (also useful for B15 device name). |
| G-14 | Design S32 ↔ API | "receipt list" — no purchases/orders list endpoint. | Add `GET /v1/me/purchases` or drop from S32 (Play shows receipts). |
| G-15 | Ads ↔ API consents | No consent purpose for personalised ads; design says non-personalised by default. | Add `ads_personalisation` to `Consent.purpose` before ever enabling personalised ads. |
| G-16 | Design S09 ↔ API | Gender option "अन्य" vs enum `transgender`; design S05 "passed / result awaited" vs `completed / final_year / appearing`. | Mapping only (client maps); confirm wording with design (legal term is "ट्रांसजेंडर"). |
| G-17 | API ↔ inventory | `listNotices` (browse all notices) has no screen. | Fine to leave unused in v1; or add a P1 "सभी नौकरियाँ" screen. |
| G-18 | Design S16 ↔ API | Design waits 60 s for the test push; API times out at 120 s. | Client shows "अभी भी इंतज़ार…" after 60 s and the final result at 120 s. |

Every screen in `01-screen-inventory.md` (S01–S46, B01–B15, D01–D09, O01–O10) is mapped in navigation.md §3; every repository call maps to an operationId in data-layer.md §8, except the items above.

## 6. Open questions

1. Launch state(s) — affects pinned states in S08, Hindi copy review and OEM mix for QA.
2. Domain for App Links (`sarkarimatch.in` placeholder) and who hosts `assetlinks.json`.
3. Trial semantics (G-02) and whether Play user-choice billing will be used (UPI in production).
4. Ads: final SDK version/size measurement; whether ads are on from day 1 or after the validation test (`config.ads.enabled`).
5. Is a Wear/tablet layout ever needed? (Assumed no; content max width 600 dp only.)
6. Minimum supported Play services version for App Check (Play Integrity) on very old devices — fallback behaviour if App Check fails.
