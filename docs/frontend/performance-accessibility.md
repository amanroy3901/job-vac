# Performance, size, accessibility, observability, privacy

Target devices: **Android** 8–16, **2 GB RAM**, slow eMMC storage, Mali/PowerVR GPUs, 2G/3G/patchy 4G. Reference test phones: Redmi A2/A3 (Go-class), Samsung Galaxy A03/A05, Realme C-series, Vivo Y-series. **iOS** 16+; reference: iPhone XR / 11 / SE (2nd gen) (A12/A13, 3–4 GB RAM — the oldest iPhones common in India's second-hand market) and one ProMotion device.

## 1. Budgets (enforced in CI)

| Metric | Budget | How measured |
| --- | --- | --- |
| Download size (AAB → per-device APK, arm64, hi/en) | **≤ 8 MB without ads, ≤ 10 MB with the ads SDK** (PRD hard limit < 15 MB) | `bundletool get-size total` in CI; fail above budget |
| Cold start, time to initial display | ≤ 1.2 s on Pixel 4a-class emulator profile, ≤ 2.0 s on Galaxy A03 | Macrobenchmark `StartupTimingMetric`, `CompilationMode.Partial(BaselineProfile)` |
| Time to full display (Home from Room) | ≤ 2.5 s on Galaxy A03 | `reportFullyDrawn()` when the first feed emission is rendered |
| Feed scroll jank | < 5 % frames over budget | Macrobenchmark `FrameTimingMetric` on S20 with 50 cards |
| Notification action → Room write | < 100 ms | receiver trace |
| Java heap at steady state on Home | < 60 MB | Android Studio profiler, `isLowRamDevice` devices |
| Data per day (normal use) | < 200 KB | Ktor client byte counters in debug (both platforms) |
| **iOS** App Store download (arm64, App Thinning) | **≤ 25 MB** (stretch 20 MB) | App Store Connect "App Store File Sizes" report; CI estimate from the exported `.ipa` (`xcodebuild -exportArchive` + `du` of the thinned variant) |
| **iOS** first frame after cold launch | ≤ 1.5 s on iPhone XR/11 | XCTest `XCTApplicationLaunchMetric` on a device in the weekly job; MetricKit launch histograms in production (no third-party upload, aggregated by Apple) |
| **iOS** feed scroll | ≥ 58 fps at 60 Hz, ≤ 1 % hitches; 120 Hz on ProMotion | Instruments (Animation Hitches) on a release build; `CADisableMinimumFrameDurationOnPhone = YES` in Info.plist so CMP can render at 120 Hz |
| **iOS** memory on Home | < 120 MB resident | Xcode memory gauge; Kotlin/Native GC + Skia caches |
| **iOS** notification action → Room write | < 300 ms incl. background cold launch of the Kotlin runtime | signpost in `IosEntryPoints.onNotificationResponse` |

## 2. Size strategy

- **R8 full mode** (AGP default), `isMinifyEnabled = true`, `isShrinkResources = true`; keep rules only for kotlinx.serialization (`@Serializable` generated serializers are handled by the bundled rules), Ktor (bundled), Koin (none needed: no reflection), Firebase (bundled).
- **Locale filtering:** `androidResources { localeFilters += <enabled locales> }` (en, hi at launch) drops hundreds of KB of library translations; App Bundle language splits deliver only the user's languages as more are enabled. Script fonts are downloadable (0 KB).
- **No native libraries** (no SQLCipher, no ML Kit) → no ABI splits needed; App Bundle still splits density/language.
- **No raster images in the app:** vector drawables for ~60 icons and the 2-colour illustrations; no `material-icons-extended`; no Coil/Glide (nothing to load).
- **Fonts:** Baloo 2 + Hind via downloadable fonts (0 KB).
- **Firebase à la carte:** Auth, Messaging, App Check (Play Integrity), Installations, Crashlytics (opt-in). No Analytics. **Not** Remote Config (we use `/v1/config`), **not** Firestore/Realtime DB, **not** Performance Monitoring (heavy, bytecode instrumentation).
- **Ads SDK** (free-tier native ad, ads.md) is the largest single dependency: lazy-initialised on first slot scroll-in, never for Pro, excluded from the baseline profile; `AD_ID` permission removed.
- Track per-dependency cost with `./gradlew :androidApp:dependencies` + `apkanalyzer dex packages` in a monthly check; any new dependency PR must state its size delta.

### 2a. iOS size

A Compose Multiplatform iOS app carries the Kotlin/Native runtime + our code + Skia (Skiko) in one static framework —
the main cost of shared UI on iOS. Measures:

- Release framework with `-Xbinary=…` defaults + **`freeCompilerArgs += "-Xadd-light-debug=disable"`**, Link-Time
  dead-code elimination (on by default for release), static framework (no dynamic-framework overhead), bitcode n/a.
- **No `iosX64`**, only arm64 device + simulator slices; App Thinning delivers arm64 only.
- Compose resources: only enabled locales (i18n §A1), fonts ≤ 450 KB, vector icons only.
- Firebase à la carte through SPM products: `FirebaseAuth`, `FirebaseMessaging`, `FirebaseAppCheck`,
  `FirebaseCrashlytics` (+ Installations, Core) — **not** `FirebaseAnalytics` (Crashlytics on iOS can run without
  Analytics; breadcrumbs off).
- Google Mobile Ads iOS is the largest third-party dependency; Pro users still download it (no on-demand resources for
  code). If the 25 MB budget is exceeded, the first lever is ads (the Next-Gen iOS SDK if lighter, or a server-side
  decision to drop ads on iOS), not shared UI.
- dSYMs for the Kotlin framework are uploaded for Crashlytics, never shipped.

## 3. Startup and runtime performance

- **Baseline Profile + Startup Profile** generated by `:baselineprofile` (Macrobenchmark + UiAutomator) covering: cold start → Home, open JobDetail, scroll feed, tap *भर दिया* → B01, open S22. The startup profile also drives DEX layout optimisation. `profileinstaller` ships the profile for sideloads/first launch.
- **Nothing blocking in `Application.onCreate`:** Koin module declarations (lazy singletons; `createdAtStart` nowhere) + `WorkManager` on-demand init (`Configuration.Provider`, default initializer removed) + channel creation (cheap) only. Billing client connects lazily on paywall/start-check after first frame; config/health/sync run after first frame in `ProcessLifecycleOwner` ON_START.
- **Splash** (`installSplashScreen`) is held only while `StartViewModel` reads DataStore + Room (no network), max 1.5 s.
- **iOS startup:** `didFinishLaunching` does `FirebaseApp.configure()`, `startSm(bindings)` (Koin declarations only),
  notification delegate + category registration, `BGTaskScheduler.register`, `Transaction.updates` listener — all
  synchronous work < 100 ms; the Kotlin/Native runtime initialises lazily on first call. The SwiftUI `ContentView`
  hosts `MainViewController()` immediately; `App()` shows the launch-screen colour until `StartViewModel` decides
  (same 1.5 s cap). No Ads/StoreKit product loads at launch. Measured with the launch metric above.
- **iOS rendering (Compose Multiplatform on Metal via Skia):** the same Compose hygiene rules apply and matter more,
  because recomposition + Skia drawing run on the main thread without Android's RenderThread. Extra rules: no
  `UIKitView` inside scrolling lists except the single ad slot at the end; avoid large `Modifier.blur`/shadows (design
  uses 0–1 dp elevation anyway); text-heavy cards keep `TextStyle` objects static (cached `Typography`) so Skia's
  paragraph cache hits; verify with the CMP iOS performance overlay in debug.
- **Compose hygiene:**
  - Strong skipping (default with the Kotlin 2.x Compose compiler) + `@Immutable` UI models + `ImmutableList` → list items skip when unchanged.
  - `LazyColumn` with `key = matchId` and `contentType`; never `Column.verticalScroll` for lists; no nested scroll containers.
  - Formatting (dates, ₹, countdown labels, section bucketing) done in VMs/mappers, never in composables.
  - Countdown is in **days** (re-bucketed at IST midnight), so there is no per-second ticking recomposition.
  - `derivedStateOf` for scroll-dependent UI (top-bar elevation, "scroll to top"); lambdas passed as stable references (`vm::onAction`).
  - Animations limited to 1–2 per screen; `Modifier.animateItem()` for card removal; reduced-motion → 0 ms.
  - Avoid `IntrinsicSize`, deep `Box→Column→Box` nesting; flat card layouts.
  - Debug builds enable Compose compiler reports (`composeCompiler { reportsDestination }`) and Layout Inspector recomposition counts; CI fails on new unstable classes in `:core:designsystem`.
- **Room on the main thread is forbidden** (Room KMP DAOs are `suspend`/`Flow` only); DAO `Flow`s are distinct-until-changed; JSON blobs decoded off-main in mappers.
- **Low-RAM devices** (`ActivityManager.isLowRamDevice()`): apply catalog batches in smaller transactions, stream the snapshot with `decodeSourceToSequence` (never one big string), disable shimmer, HTTP cache 2 MB.
- **iOS background work** runs inside `BGAppRefreshTask` (≤ ~25 s used), `BGProcessingTask` (snapshot only), background
  pushes (≤ 30 s) and notification responses (a few seconds): every entry point checks `expirationHandler`, works in
  one Room transaction per batch, and leaves the window/outbox consistent if cut off.
- **Background work (Android)** is batched and constrained: one unique `catalog-sync` job (topic-triggered + daily reconcile, which also re-arms reminders and needs no network), on-device backup alarms only for the next 3 R1/R-last slots. No foreground services except WorkManager's expedited fallback on API < 31. Matching a batch costs < 10 ms of CPU.

## 4. Network resilience

Gzip, ETags, immutable CDN-cached catalog batches (2–10 KB) and one snapshot on first run (≤ 1 MB gzipped, streamed), timeouts tuned for 2G (connect 15 s / read 30 s), exponential backoff with jitter honouring `Retry-After`, never auto-retry non-idempotent calls without the persisted `Idempotency-Key`. Notice bodies (~10–30 KB) only for open matches. Offline UX: cached content + O01 banner, queued actions with a small pending clock icon, pull-to-refresh disabled offline with explanation.

## 5. Accessibility

| Requirement | Implementation | Test |
| --- | --- | --- |
| TalkBack **and VoiceOver**: one stop per card with all key info | `Modifier.semantics(mergeDescendants = true)` + `contentDescription` built in the mapper (Compose Multiplatform maps the same semantics tree to `UIAccessibilityElement`s on iOS) ("SSC CGL 2026. आप योग्य हैं. आखिरी तारीख 3 दिसंबर, 12 दिन बाकी. फ़ीस शून्य.") | Compose UI test asserts merged description |
| Act without hunting small buttons | `customActions = [भर दिया, रुचि नहीं, बाद में]` on `MatchCard` (VoiceOver: swipe up/down "Actions" rotor via `UIAccessibilityCustomAction`) | UI test invokes custom action |
| Headings | `semantics { heading() }` on screen titles and `SectionHeader` (→ VoiceOver `.header` trait, navigable with the Headings rotor) | |
| Touch targets ≥ 48 dp, ≥ 8 dp apart (≥ 44 pt Apple HIG is met by 48 dp = 48 pt) | `minimumInteractiveComponentSize()`; `SmSize.touchMin` | ATF checks |
| Font scale 200 % (Android 14 non-linear) / iOS Dynamic Type up to AX2 (capped 2.0, i18n §A4) | all text `sp`; `heightIn(min=…)`, never fixed heights; buttons wrap 2 lines then stack at fontScale ≥ 1.5 | Roborazzi at 1.0 / 1.3 / 2.0 for every screen |
| Colour never alone | verdict/deadline/stage = icon + word + colour; ✓ ? ✗ glyphs in rule lines | design review + screenshot |
| Contrast | tokens verified ≥ 4.5:1 (design §2.5); `TokensParityTest` keeps code = tokens | unit test |
| Live updates | snackbars & result banners polite; countdown chips not live regions | |
| Toggles | `stateDescription` ("चालू"/"बंद") (→ VoiceOver accessibility value) | |
| Reduced motion | `LocalReducedMotion` → durations 0, static skeleton (iOS Reduce Motion) | |
| Spoken language | Hindi text must be read with a Hindi voice: Android TalkBack uses the locale span / app locale; on iOS the app language comes from `AppleLanguages`, and the mapped elements get `accessibilityLanguage` = app language (verify CMP propagates `LocaleList` semantics; else a `iosMain` workaround on the root) | manual VoiceOver pass in hi/en |
| Native islands | Ad slot (`UIKitView`/`AndroidView`) and system sheets (StoreKit, Apple/Google sign-in) are native: label the ad view "विज्ञापन:" in the Swift/Android factory | |
| Predictable focus order | visual order; `traversalIndex` only where layout forces it | |
| Low literacy | icon + word on every action; big numbers; label/value rows not tables; P1 "सुनें" TTS (`TextToSpeech`, `hi-IN`) on S21 summary | user testing |

Automated: `composeTestRule` with `enableAccessibilityChecks()` (Accessibility Test Framework) in Compose UI tests for every screen; Android Lint accessibility checks as errors. iOS: an XCUITest smoke on the simulator runs `XCUIApplication().performAccessibilityAudit()` (Xcode 15+/iOS 17+ simulator) on S02, S10, S20, S21, S30, plus a manual VoiceOver pass per release (Hindi and English).

## 6. Metrics and crash reporting (minimal data)

- **No Firebase/Google Analytics SDK** (founder decision 5: minimise what leaves the device).
- **Crashlytics** on both platforms (Android NDK off; iOS via the Swift `CrashReporter` port, with
  `setUnhandledExceptionHook` in Kotlin/Native forwarding Kotlin exceptions with their Kotlin stack as non-fatal +
  crash records — CrashKiOS-style — and dSYMs for the Kotlin framework uploaded in the release job) only after the user opts in on S47/S38 (`setCrashlyticsCollectionEnabled(false)` in the manifest by default); never in minor mode. Custom keys: `app_lang`, `oem`, `api_level`, `is_pro` — no user id, no profile fields, no post ids.
- **Usage counters** (opt-in, never in minor mode): `UsageCounters.increment(name)` in Room; `TelemetryWorker` sends one identifier-free aggregate per IST day (`postUsageCounters`) and clears it. Counter names map to PRD metrics:

| PRD metric | Counters (per day, no identifiers) + other source |
| --- | --- |
| Profile completion ≥ 60 % | `onb_step_{n}`, `onb_complete`, `profile_saved_band_{b}`; Play Console installs as the denominator |
| Alerts acted on ≥ 50 % | `applied_{src}`, `not_interested_{src}`, `reminder_shown_{push\|backup\|local}` |
| Notification disable < 15 % | `notif_permission_{granted\|denied}`, `notif_blocked` |
| Matches unknown before ≥ 30 % | in-app one-tap survey "क्या आपको पहले से पता था?" → `knew_before_{yes\|no}` (P1) |
| Wrong verdicts < 1 % | `match_shown` vs server-side count of anonymous `reports` |
| 30-day active, paying share | Play Console retention + server `purchase` rows / `user_entitlement` |
| Delivery | `self_test_{pass\|timeout}_{oem}`, `backup_fired_without_push` |

The server keeps only aggregates (`usage_counter`); nothing is joinable to a person.

## 7. Feature flags, kill switches, force update

- Source: `getAppConfig` (`features`, `kill_switches`, `maintenance`, `update`, `min_supported_version`, `latest_version`, `oem_guides`, `self_test_interval_days`, `privacy_notice_version`, `reminder_backup`, `entitlement_token_jwks`, `disclaimer`, `support`) — ETag-cached in Room, refreshed every cold start and every 6 h. **No Firebase Remote Config SDK.**
- `FeatureFlags` interface in `:core:data` (`isEnabled(Flag.FamilyAlerts)`) combines remote flags with a local debug override screen (debug builds only).
- Force update: `update = force` or any `426 upgrade_required` → S41 with Play In-App Updates **IMMEDIATE**; `update = soft` → D09 with **FLEXIBLE** update once per 3 days. S41 falls back to a Play Store link when in-app updates are unavailable (sideload, old Play). **iOS:** no in-app update API — S41/D09 open the App Store page (`itms-apps://apps.apple.com/app/id<APP_ID>`); `config.min_supported_version` / `latest_version` are one build-number sequence shared by both platforms (Android `versionCode` = iOS `CFBundleVersion`, API `X-App-Version`), so a forced update is announced only when both stores carry the fixed build. Phased release in App Store Connect plays the role of staged rollout.
- Maintenance / outage: `config.maintenance` or `getHealth = down` → O03 banner (cache stays usable) or S42 when nothing is cached.

## 8. Privacy (DPDP Act 2023) and trust

- **Data minimisation (founder decision 5, ADR-11):** the profile (DOB, category, state, qualification, gender, PwD/ex-serviceman flags) is entered and **kept on the phone**; matching runs locally. No phone number, Aadhaar, documents, contacts, location or ad id. The server gets nothing about a guest; accounts hold only Google uid, purchases, encrypted backup/watch-list. Data safety form: backend security-privacy.md §6.1.
- **Notice and consent (minimal):** S47 privacy notice before any profile question (one "समझ गया" button, no checkbox wall); `privacy_notice_ack{version, at}` in DataStore, re-shown when `config.privacy_notice_version` changes; account features record `privacy_notice`, `cloud_backup`, `family_link_*` via `recordConsent`. Optional toggles on S47/S38: crash reports, usage counters (both off by default). S13 is our own notification-permission explanation before the system dialog; B17 explains the ad.
- **Minor mode:** DOB < 18 → no account, sign-in, purchase, trial, backup, family owner, ads, crash reports or counters; everything else works.
- **Rights:** S38 "मेरा डेटा डाउनलोड करें" (local JSON via SAF + `getMe` summary for accounts), "सब कुछ हटाएँ" (local wipe; + `deleteAccount` with 7-day grace when signed in), backup off, consent withdrawal.
- **On device:** app-private storage (Android FBE; iOS Data Protection `CompleteUntilFirstUserAuthentication` in the App Group container), OS backups exclude DB and auth state on both, no logs of PII (Timber trees stripped in release), `HttpLoggingInterceptor` debug-only, screenshots allowed (users share them; no secrets on screen).
- **Trust UI:** `DisclaimerNote` ("Sarkari Match एक निजी ऐप है, कोई सरकारी ऐप नहीं…") on S03, S21, S39, and the official PDF/source link on every match (PRD NFR). No government emblems/tricolour (Play impersonation policy).
- **Store disclosures:** Play Data safety form (backend security-privacy.md §6.1) and the **App Store privacy "nutrition" labels** must say the same: profile processed on device (not collected); account id, purchases, encrypted backup, crash data (opt-in), ads SDK data not linked/not tracking. `PrivacyInfo.xcprivacy` declares our required-reason API use; third-party SDK manifests come with their SPM packages.
- **Network security:** HTTPS only (Android `usesCleartextTraffic=false`; iOS App Transport Security defaults, no exceptions), App Check on every non-public call (Play Integrity on Android; **App Attest** on iOS 14+ with DeviceCheck fallback); catalog files verified by `sha256` from `catalog/head`; no certificate pinning (operational risk with CDN rotation outweighs benefit for this data).
