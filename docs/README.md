# Sarkari Match — documentation

Sarkari Match is a Hindi-first **Android and iOS** app (one Kotlin Multiplatform + Compose Multiplatform codebase) that
alerts government-job aspirants **only** about jobs they are eligible for, and keeps reminding them until they tap
*Applied* or *Not interested*. The free tier carries one light native ad; Pro (₹199/year or ₹49/season, through Google
Play Billing on Android and App Store in-app purchase on iOS — no other payment path) adds lifecycle alerts after
applying, no ads and, from P1, family, clash and certificate alerts.

**Founder decisions of 2026-11-19** ([PRD.md](PRD.md), top section) are applied across design, API, backend and
frontend: all major Indian languages (hi + en at launch); Google Sign-In (+ Sign in with Apple on iOS) or guest, no
phone/OTP; store billing only (Play / App Store); **the eligibility profile and matching stay on the phone**; light
non-personalised ads (no tracking prompt on iOS); **no WhatsApp or SMS** — FCM push (APNs on iOS) + on-device
reminders; family alerts to the member's own app; validation as a Play closed test. **Decision 9** (Android + iOS via
KMP/CMP, same Marigold design) is applied in `frontend/` ([frontend/platform-interop.md](frontend/platform-interop.md))
and recorded in [contract-alignment.md](contract-alignment.md) #64–#88; the server side of App Store purchases is
being added by the backend pass.

Everything here is design-level and implementation-ready as of **2026-11-19**. The PRD is the source of truth for
scope. Design, API, backend and frontend were written separately and then reconciled; every contradiction found and
how it was resolved is in [contract-alignment.md](contract-alignment.md).

## What is in this folder

| Path | What it is | Owner / reader |
| --- | --- | --- |
| [PRD.md](PRD.md) | Product requirements: problem, personas, features with P0/P1/P2, reminder table, pricing, ads rule, risks, validation gates | everyone (scope) |
| [contract-alignment.md](contract-alignment.md) | Every cross-doc mismatch → decision → files changed | everyone |
| **design/** | | |
| [design/README.md](design/README.md) | Design package index, key UX decisions, assumptions | design, mobile |
| [design/01-screen-inventory.md](design/01-screen-inventory.md) | All screens, sheets, dialogs and overlays with IDs (S/B/D/O; v1.1: S01–S49 minus retired S11, S12, S33), routes, priority; navigation map; deep links | everyone (the IDs are used in every other doc) |
| [design/02-design-system.md](design/02-design-system.md) + [tokens.json](design/tokens.json) | Colours (light/dark, verified contrast), Mukta type scale + per-script fonts (`font.script.*`), spacing, shape, icons, motion, voice | mobile |
| [design/03-scenarios.md](design/03-scenarios.md) | 43 user flows; user-job state machine; reminder scheduler (slots, ranks, cap, quiet hours, snooze) | mobile, backend |
| [design/04-screens.md](design/04-screens.md) | Per-screen layout, states, exact Hindi/English copy, TalkBack, analytics | mobile |
| [design/05-notifications.md](design/05-notifications.md) | Android channels (7 incl. `family`; iOS maps them to interruption levels in frontend/notifications.md §2), push templates N-xx, on-device backup reminders, family alerts, digest (WhatsApp/SMS retired) | mobile, backend |
| [design/07-localization.md](design/07-localization.md) | 13 languages, fonts per script, expansion budgets, RTL (Urdu), numerals | mobile, content |
| [design/CHANGES-2026-11-19.md](design/CHANGES-2026-11-19.md) | Screen/component/token IDs added and retired by the founder decisions | everyone |
| [design/06-components.md](design/06-components.md) | Compose component library (props, variants, states), ad slot — built once in Compose Multiplatform for both platforms | mobile |
| [design/prototype/](design/prototype/) | Static HTML prototype (v1.1: 61 frames incl. Tamil and Urdu RTL previews) and screenshots | everyone |
| **api/** | | |
| [api/openapi.yaml](api/openapi.yaml) | The mobile contract (OpenAPI 3.1, 36 paths): public catalog, accounts, Play purchases, backup, family, reports; FCM data payload (`PushData`) | mobile, backend |
| [api/README.md](api/README.md) | Conventions: derived ids and links, headers, Google/guest auth, errors, catalog sync, topics/push contract, payments, ads gating, removed endpoints | mobile, backend |
| **backend/** | | |
| [backend/README.md](backend/README.md) | Stack, component diagram, ADRs, cost table, open questions | backend |
| [backend/data-model.md](backend/data-model.md) + [schema.sql](backend/schema.sql) | ERD, PostgreSQL 16 DDL (catalog, locales, minimal accounts), personal-data inventory, retention, deletion | backend |
| [backend/matching-engine.md](backend/matching-engine.md) + [rule.schema.json](backend/rule.schema.json) + [examples/](backend/examples/) | On-device vs server matching decision, rule schema, tri-state evaluation, catalog, worked examples | backend, mobile, curation |
| [backend/ingestion-pipeline.md](backend/ingestion-pipeline.md) | Source watchers, PDF/OCR, LLM extraction, human review console, versioning | backend, curation |
| [backend/reminders-and-delivery.md](backend/reminders-and-delivery.md) | Device-side state machine and schedule, FCM topic pushes + on-device backup, family relay, self-test | backend, mobile |
| [backend/payments-and-entitlements.md](backend/payments-and-entitlements.md) | Play Billing only, season as prepaid plan, RTDN, trial, entitlement truth, closed-test validation | backend |
| [backend/security-privacy.md](backend/security-privacy.md) | App Check, accounts, rate limits, admin, DPDP Act (minor mode), Play Data safety, ads privacy | backend |
| [backend/operations.md](backend/operations.md) | Observability, CI/CD, runbooks, scaling, week-by-week build plan | backend |
| **frontend/** | | |
| [frontend/README.md](frontend/README.md) | Mobile architecture (KMP + CMP, Android + iOS), modules, key decisions, contract status | mobile |
| [frontend/platform-interop.md](frontend/platform-interop.md) | Shared vs platform code, Koin ports, Swift boundary (StoreKit 2, Apple/Google sign-in, FCM/APNs, AdMob iOS), iOS shell, App Store review risks | mobile |
| [frontend/navigation.md](frontend/navigation.md) | Every screen → route → ViewModel → UiState; deep links (App Links + Universal Links); back stack | mobile |
| [frontend/data-layer.md](frontend/data-layer.md) | Repositories, Room KMP, Ktor, catalog sync + on-device matching, local actions, offline queue (WorkManager / BGTaskScheduler), sign-in/backup; repository → operationId table | mobile |
| [frontend/state-management.md](frontend/state-management.md) | UDF pattern; Home, Job detail and Paywall examples | mobile |
| [frontend/notifications.md](frontend/notifications.md) | FCM topic handling (APNs shapes on iOS), 7 channels / iOS interruption levels, actions without opening the app, device schedule (Android alarms; iOS 64-request window), OEM reliability, family | mobile |
| [frontend/billing.md](frontend/billing.md), [ads.md](frontend/ads.md) | Play Billing + StoreKit 2 behind one interface, server verification, restore, paywall triggers; free-tier native ad slot, UMP, no ATT | mobile |
| [frontend/i18n-and-design-system.md](frontend/i18n-and-design-system.md), [performance-accessibility.md](frontend/performance-accessibility.md), [testing-ci.md](frontend/testing-ci.md), [project-skeleton.md](frontend/project-skeleton.md) | 13 languages (compose resources, bundled fonts per script, RTL, Dynamic Type, per-app language on both OSes), IST/₹ and theme; budgets, a11y (TalkBack + VoiceOver) and minimal metrics; test pyramid, CI on Linux + macOS, Play/App Store release; versions, modules and phased tickets | mobile |

## Reading order for a new developer

1. **[PRD.md](PRD.md)** (15 min): what is in scope and what is P0.
2. **[contract-alignment.md](contract-alignment.md)**, first table (5 min): the canonical ids, channels, trial and snooze rules.
3. **[design/01-screen-inventory.md](design/01-screen-inventory.md)** and skim **[design/03-scenarios.md](design/03-scenarios.md)** §0 and §2–§4: the screen IDs and the core loop.
4. **[api/README.md](api/README.md)**, then the parts of **[api/openapi.yaml](api/openapi.yaml)** you will touch.
5. Then by role:
   - **Mobile (Android + iOS):** [frontend/README.md](frontend/README.md) → [platform-interop.md](frontend/platform-interop.md) → navigation → data-layer → notifications → state-management → billing/ads → [design/04](design/04-screens.md), [05](design/05-notifications.md), [06](design/06-components.md), [02](design/02-design-system.md) as you build screens → [project-skeleton.md](frontend/project-skeleton.md) for the module layout and ticket order. Code: [`mobile/`](../mobile/README.md).
   - **Backend:** [backend/README.md](backend/README.md) → data-model + schema.sql → matching-engine → reminders-and-delivery → ingestion-pipeline → payments → security-privacy → operations (build plan).
6. Open the [prototype](design/prototype/index.html) whenever copy or layout is in doubt; [design/04](design/04-screens.md) is authoritative for copy.

## Architecture on one page

```mermaid
flowchart LR
  subgraph Phone["Mobile app — Android + iOS (Kotlin Multiplatform, Compose Multiplatform, Room KMP; WorkManager / BGTaskScheduler)"]
    UI["Screens S01–S49<br/>ViewModels"] --> ROOM[("Room: profile, matches,<br/>reminders, catalog — device only")]
    CS["CatalogSync"] --> ME[":matching-core<br/>(on-device verdicts)"] --> ROOM
    ME --> RP[":reminder-core<br/>+ on-device backup alarms"]
    FCMH["FCM handler<br/>(topics + account; APNs on iOS)"] --> NOTIF["Notifications<br/>7 channels / iOS levels, action buttons"]
    RP --> NOTIF
    BILL["Play Billing / StoreKit 2"]
    ADS["AdMob native<br/>(free tier, 18+, non-personalised)"]
    GS["Google sign-in (optional)<br/>+ Sign in with Apple (iOS)"]
  end

  subgraph GCP["Google Cloud, asia-south1"]
    HOST["Firebase Hosting<br/>CDN · catalog files · App Links ·<br/>Universal Links (AASA)"]
    API["Cloud Run: api<br/>Go REST /v1 (accounts)"]
    WRK["Cloud Run: worker<br/>watch · extract · catalog build ·<br/>topic send"]
    ADM["Cloud Run: admin<br/>review console (IAP)"]
    SCH["Cloud Scheduler<br/>1-min ticks"]
    PG[("Cloud SQL Postgres 16<br/>catalog source · accounts ·<br/>purchases · encrypted blobs")]
    AUTH["Firebase Auth (Google, Apple,<br/>anonymous) · App Check"]
  end

  subgraph Ext["External services"]
    FCM["FCM HTTP v1 (→ APNs for iOS)<br/>topics: catalog · p_{post} · n_{notice}"]
    PLAY["Google Play<br/>Developer API + RTDN"]
    APPS["App Store Server API +<br/>Server Notifications V2"]
    SRC["Official sources<br/>SSC · RRB · IBPS · UPSC · state boards"]
    LLM["Claude API<br/>(PDF extraction)"]
  end

  CS -->|"public catalog (no profile sent)"| HOST
  Phone -->|"account calls: ID token + App Check"| API
  GS -.-> AUTH
  BILL -->|purchase token| API
  API <--> PG
  ADM <--> PG
  SCH --> WRK
  WRK <--> PG
  WRK --> SRC
  WRK --> LLM
  WRK -->|catalog batches| HOST
  WRK -->|data-only topic pushes| FCM --> FCMH
  API -->|verify + acknowledge| PLAY
  PLAY -->|RTDN via Pub/Sub| API
  API -->|verify signed transactions| APPS
  APPS -->|notifications V2| API
```

**How a notice becomes an alert.** A watcher finds a new PDF on an official site → the LLM fills a rule document
(`rule.schema.json`) → a person checks it in the admin console and publishes → the worker writes an immutable
**catalog batch** to the CDN and pushes `type=sync` to the FCM topic `catalog` → every phone downloads the batch,
evaluates it against its **own, local** profile with `:matching-core`, stores eligible / check-yourself matches and
composes the "new match" notification in the app language (on iOS the catalog push is a background push that iOS may
throttle; the app also syncs on open and in background refresh) → reminders are planned on the phone (iOS: scheduled
local notifications in a rolling 64-request window) (3/day cap, quiet
hours 22:00–07:00, ranks); the server adds topic pushes on `p_{post_id}` for R3/R1/R-last and `n_{notice_id}` for
lifecycle events, and the phone's **on-device backup** fires for R1/R-last if a push is missing (Android) → *भर दिया /
बाद में / रुचि नहीं* work from the notification on both platforms, offline, and never leave the phone. Target: catalog batch out < 5 min after
publish (PRD NFR < 6 h).

**What the server owns:** the public catalog (rules, dates, fees, lifecycle), topic pushes, entitlement (incl.
`ads_enabled`, signed token), minimal accounts, family relay of encrypted watch-lists, encrypted backups it cannot read.
**What the device owns:** the profile, every verdict, matches, the reminder schedule, actions, applications, inbox,
preferences, rendering in 13 languages.

## Canonical decisions

| Topic | Decision | Where it is specified |
| --- | --- | --- |
| Founder decisions 2026-11-19 | Override everything below where they conflict; change set recorded in [contract-alignment.md](contract-alignment.md) #42–#63; decision 9 (iOS) #64–#73 backend, #74–#88 frontend | [PRD.md](PRD.md) |
| Platforms | **Android + iOS from one codebase** (founder decision 9): Kotlin Multiplatform for models, eligibility engine, data, sync, notifications logic; Compose Multiplatform for all screens; same Marigold design. Platform SDKs behind Koin-injected ports, iOS SDKs implemented in Swift | [frontend/platform-interop.md](frontend/platform-interop.md) |
| Mobile stack | Kotlin 2.4, Compose Multiplatform 1.12 (Material 3, compose resources), Navigation Compose multiplatform 2.10 (type-safe), Koin 4.2, KMP Lifecycle ViewModel, Room KMP 2.8, DataStore KMP, Ktor 3 (OkHttp / Darwin) + kotlinx.serialization, kotlinx-datetime, KMP `:core:eligibility` (→ `:matching-core`); Android: WorkManager, Credential Manager, Block Store, Play Billing 9.1, Firebase (Auth, FCM, App Check, Crashlytics opt-in — **no Analytics**), GMA Next-Gen + UMP, minSdk 26 / target 36, download ≤ 10 MB; iOS 16+: StoreKit 2, Sign in with Apple, Google Sign-In iOS, Firebase iOS (FCM over APNs, App Attest), `UNUserNotificationCenter`, `BGTaskScheduler`, Google Mobile Ads iOS + UMP, download ≤ 25 MB; 6 Gradle modules + Xcode shell in `mobile/` | [frontend/README.md](frontend/README.md), [project-skeleton.md](frontend/project-skeleton.md) |
| Backend stack | **Go 1.24 + PostgreSQL 16** (founder decision 2026-11-19, ADR-13): one binary/distroless image with `api` / `worker` / `admin` roles on Cloud Run (asia-south1); stdlib `net/http`, pgx v5, goose migrations, slog; Cloud SQL (catalog source, accounts, purchases, job queue); Cloud Scheduler 1-minute ticks; Firebase Hosting as CDN for the catalog. Code in [`backend/`](../backend/) | [backend/README.md](backend/README.md) ADR-01/04/09/13, [backend/ (code)](../backend/README.md) |
| **Data & matching** | **Privacy-first (ADR-11):** profile and matching **on the device** against a public CDN catalog (snapshot + immutable batches, `evaluator_min_version`); server stores no DOB/category/PwD/phone; minimal consent log for account features only; **minor mode** (< 18: no account, ads, telemetry) instead of parental consent | [matching-engine.md](backend/matching-engine.md) §0/§4, [data-model.md](backend/data-model.md), [security-privacy.md](backend/security-privacy.md) §6 |
| Extraction | Claude API with native PDF input and structured output against `rule.schema.json`; a person reviews and publishes every rule; second reviewer for risk-flagged posts | [ingestion-pipeline.md](backend/ingestion-pipeline.md), ADR-07 |
| Auth | **Google Sign-In** (Android Credential Manager / iOS Google Sign-In SDK → Firebase Auth Google provider), **Sign in with Apple on iOS** (App Store guideline 4.8: an equivalent privacy-preserving login next to Google; Firebase Apple provider, token revoked on account deletion) or **guest**. Guests have no server account until trial/purchase/family; then Firebase anonymous, linkable to Google (same uid). No phone, no OTP. Cost $0 without the Identity Platform upgrade; worst case 50K MAU free then ~$0.0055/MAU | [api/README.md](api/README.md) §3, ADR-02 |
| Languages | 13 languages modelled (`LocalizedText` maps, `locale` table with fallback chains, `?locales=` catalog files, per-app locales, per-script downloadable fonts, Urdu RTL, pseudo-locale tests); **hi + en enabled at launch** | ADR-12, [i18n-and-design-system.md](frontend/i18n-and-design-system.md), [design/07](design/07-localization.md) |
| IDs | `notice_id` `n_` · `post_id` `p_` ("the job") · `match_id` `m_` and `application_id` `a_` **derived on the device** from the post id · `event_id` `e_` · `link_id` `f_`. Design `jobId` = `match_id` | [api/README.md](api/README.md) §1a |
| Links | `https://sarkarimatch.in/j/{match_id}`, `/p/{post_id}` (shared), `/a/{application_id}`, `/f/{token}#k={key}` (family; key never reaches the server), `/pro`; `sarkarimatch://job/{match_id}` etc. Android App Links (`assetlinks.json`) and iOS Universal Links (`apple-app-site-association`) on the same paths. `/go/` and `sarkarimatch://whatsapp` retired. Domain is a placeholder | [api/README.md](api/README.md) §1a, [design/01](design/01-screen-inventory.md) |
| Notification channels | `critical`, `new_match`, `reminders`, `job_updates`, `digest`, `account`, **`family`** — immutable after release. iOS has no channels: the same ids map to interruption levels (`critical` → **Time Sensitive**; no Critical Alerts entitlement), categories and thread ids | [design/05](design/05-notifications.md) §1, `PushData` |
| Push | Data-only, **no text** (iOS: background pushes for sync/reminders; visible lifecycle/account/self-test alerts use APNs `loc-key`s from the app bundle — still no text in the payload); FCM **topics** `catalog` (sync), `p_{post_id}` (R3/R1/R-last), `n_{notice_id}` (lifecycle) + account pushes; everything else composed on the device; no receipts, no escalation; one primary alert install per account (B15/N-47) | [api/README.md](api/README.md) §10, [reminders-and-delivery.md](backend/reminders-and-delivery.md) |
| Reminders | Computed on the device: R0 at evaluation; Rp every 3 days; R3 and R1 at the user's reminder time (default 19:00); R-last 08:00; **on-device backup** R1 + 30 min, R-last 08:15 (S14/S28, P0, default on); max 3/day (ranks 1–3 exempt); quiet hours 22:00–07:00 except near-deadline; snooze as before | [design/03](design/03-scenarios.md) §0.2, [design/05](design/05-notifications.md) §3.6, [reminders-and-delivery.md](backend/reminders-and-delivery.md) §2–3 |
| Trial | **Android:** 14 days, server-granted, no payment method. **iOS:** a 14-day **introductory offer** (free trial) on the yearly auto-renewable subscription, because unlocking Pro without in-app purchase risks App Store guideline 3.1.1 (founder to confirm the asymmetry). Android rule: **Offered** on B02 right after the first Applied, **started** only by the user's tap (`POST /v1/me/trial`); guests get an account first (B16) | [payments-and-entitlements.md](backend/payments-and-entitlements.md) §5 |
| Billing | **Store billing only**. Android: Play subscription `pro`, base plans `season-4m-prepaid` (₹49, **prepaid**, no auto-renew, top-up; fallback `P3M`) and `yearly-autorenew` (₹199); server verifies and acknowledges. iOS: StoreKit 2 — `pro.year` auto-renewable (₹199, with the trial intro offer) and `pro.season4m` **non-renewing subscription** (₹49; server computes the 4-month expiry); `appAccountToken` = `billing_account_ref` (UUID); server verifies with the App Store Server API and Server Notifications V2; Family Sharing off. Entitlement + signed token only from the server, shared across platforms. No UPI links/gateways | [billing.md](frontend/billing.md), [payments](backend/payments-and-entitlements.md) §1.1, ADR-06 |
| Ads | Free tier only, adults only; one native ad as the last item of Home (S20) and the digest (S44); never in notifications, S21, what-you-need, why-you-match, sheets, tracker, onboarding, paywall; GMA Next-Gen (Android) / Google Mobile Ads (iOS), **UMP** before the first request, **non-personalised** (`npa=1`), `AD_ID` removed on Android, **no ATT prompt / no IDFA** on iOS; B17 explains; hidden for Pro, trial and minor mode; kill switch `config.ads.enabled` | [frontend/ads.md](frontend/ads.md), ADR-10 |
| WhatsApp / SMS | **None.** No WhatsApp alerts, OTP, templates or opt-in; SMS dropped (would need a phone number) | ADR-05, [reminders-and-delivery.md](backend/reminders-and-delivery.md) §8 |
| Family | Pro (P1): invite link/QR (S48) via the sharesheet → member's own app (S35/S49); end-to-end-encrypted watch-list relayed by the server; N-50…N-54 on channel `family`; no phone numbers | [reminders-and-delivery.md](backend/reminders-and-delivery.md) §5 |
| Validation | Play **closed-testing** build with real Play Billing (≥ 12 testers × 14 days also unlocks production access for new personal accounts); roadmap: weeks 1–5 build → 6–7 test → go/stop → rest of P0 **incl. the iOS app** (TestFlight purchases are sandbox-only, so iOS cannot validate payment before release; shared code stays iOS-compilable from week 1) | [operations.md](backend/operations.md) §9, [payments](backend/payments-and-entitlements.md) §6 |
| Launch states | Working assumption **Uttar Pradesh + Bihar** + SSC, RRB, IBPS, UPSC; S08 pins `ReferenceBundle.pinned_states` | [ingestion-pipeline.md](backend/ingestion-pipeline.md) §1 |
| Privacy | DPDP Act: profile never leaves the phone (except the user's own E2E-encrypted backup; key in Block Store / iCloud Keychain); no names, phone, Aadhaar, documents; S47 privacy notice + S13 notification explanation; local export and deletion; every alert shows the official source and "not a government app" (also the App Store review stance, guidelines 5.2.1/2.3.1); Play Data safety and App Store privacy labels say the same | [security-privacy.md](backend/security-privacy.md), [data-model.md](backend/data-model.md) |

## Open questions for the founder

1. **Launch states.** Confirm UP + Bihar (drives watchers, curation load, Hindi legacy-font OCR, QA devices). PRD also asks about an existing audience or partners for launch.
2. **Domain.** Buy `sarkarimatch.in` (or choose another); App Links, the catalog CDN host and the `/f/` family page depend on it.
3. **Season length.** Confirm Play Console offers a 4-month (`P4M`) prepaid base plan; else ship 3 months at ₹49 or change the price. Season → year upgrade: full ₹199 or prorated?
4. **Minor mode** (17-year-old Agniveer/NDA/10th-pass aspirants): legal check that an install sending us nothing needs no verifiable parental consent; should minors be able to buy Pro?
5. **Firebase Auth tier.** Confirm on Google's official pricing pages that the project can stay without the Identity Platform upgrade (Google + anonymous free); else budget up to ~$300/month at 1M installs.
6. **Validation metrics.** On-device data means the closed test relies on opt-in identifier-free counters + Play Console. Is that enough for the ≥ 300 profiles / ≥ 100 Applied gates, or do we add an explicit opt-in "test participant" id for the closed test only?
7. **Ads and DPDP.** S47 + B17 + non-personalised ads with no consent toggle — confirm with the legal review; and who reviews ad quality and the category blocklist?
8. **Encrypted backup** in the Play Data safety form (declared conservatively) — confirm with Play policy support.
9. **Last-day evening reminder.** Add an 18:00 reminder on the last day in addition to 08:00? (PRD has only the morning one.)
10. **First non-launch language** for the P1 pilot (Marathi and Bengali are the cheapest: Devanagari / large Hindi-belt-adjacent audiences) and who reviews translations.
11. **Copy check.** Gender chip "ट्रांसजेंडर"; S47 must say profile data is *stored on your phone*; confirm with a Hindi copy reviewer.
12. **iOS trial and season.** Accept an iOS trial that needs an Apple payment method and auto-converts to ₹199/year
    (introductory offer), unlike Android's no-card server trial? Confirm a ₹49 non-renewing 4-month season exists as an
    App Store price point for India.
13. **iOS new-match latency.** iOS throttles background pushes and drops them after force-quit; new matches are found
    on open/background refresh. Apply to Apple for the notification-filtering entitlement (P1 Notification Service
    Extension) to make catalog alerts visible and filtered on the device?
14. **Apple developer account** (organisation vs individual; an organisation account is advisable for an app showing
    recruitment information, see guideline 5.1.1(ix) spirit) and the D-U-N-S number lead time.
