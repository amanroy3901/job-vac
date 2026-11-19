# Project skeleton, Gradle setup, implementation plan (Android + iOS)

v2 (2026-11-19): the Android-only `android/` project is migrated to **`mobile/`**, a Kotlin Multiplatform +
Compose Multiplatform project for Android and iOS (founder decision 9). This file records what is built there and the
plan for both platforms.

## 1. Gradle and Xcode setup

- **AGP 9.5.1** (built-in Kotlin; KMP library modules use `com.android.kotlin.multiplatform.library`, the only
  supported way to combine KMP with AGP 9), **Gradle 9.9.1** wrapper, JDK 17 toolchain, Kotlin **2.4.21**, KSP
  **2.3.14**, **Compose Multiplatform 1.12.2** (JetBrains Compose compiler plugin = Kotlin's).
- Targets of every shared module: `android` (KMP Android library target), `iosArm64`, `iosSimulatorArm64`; pure
  modules (`:core:model`, `:core:eligibility`) also `jvm` so they build and test without the Android SDK or a Mac and can
  be consumed by tooling. No `iosX64` (Intel simulators). Apple targets compile only on macOS;
  `kotlin.native.ignoreDisabledTargets=true` keeps Linux builds (Android + JVM tests) quiet.
- Android: `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`. iOS: deployment target **16.0**, Xcode 26.x
  (whatever App Store Connect currently requires for submission — verify at release), Swift 6 language mode for the
  Swift shell.
- `build-logic/` included build with convention plugins: `sm.kmp.library` (KMP: jvm + iOS targets, detekt),
  `sm.kmp.android.library` (adds the Android KMP target), `sm.kmp.compose` (Compose Multiplatform + resources),
  `sm.android.application` (the `:androidApp` shell). Third-party Gradle plugins sit on the root buildscript classpath
  (one shared copy of KGP/AGP). Configuration cache + build cache on.
- Android flavours: `env` = `prod` / `staging` (base URL, Firebase project). No payment flavours. Build types: `debug`
  (fake backend switch, pseudo-locales), `release` (R8 full, shrink resources), `benchmark`. iOS: Xcode configurations
  `Debug-Staging`, `Debug-Prod`, `Release` with `.xcconfig` files carrying the API base URL and the Firebase
  `GoogleService-Info.plist` per environment; the same values reach Kotlin through a generated `BuildKonfig`-style
  `AppConfigDefaults` object (from `gradle.properties` `sm.apiBaseUrl.*`).
- As built (`mobile/`, 2026-11-19): `settings.gradle.kts` always includes the pure KMP modules (`:core:model`,
  `:core:eligibility`) and includes the Android-target modules only when an Android SDK is found
  (`./gradlew -Psm.jvmOnly=true :core:eligibility:jvmTest` works anywhere). Code packages are `com.sarkarimatch.*`;
  only the applicationId / bundle id use the `in.` domain (`in.sarkarimatch.app`).

### Versions (`mobile/gradle/libs.versions.toml`, 2026-11-19)

| Area | Library | Version | Notes |
| --- | --- | --- | --- |
| Build | AGP / Gradle / Kotlin / KSP | 9.5.1 / 9.9.1 / 2.4.21 / 2.3.14 | |
| UI | Compose Multiplatform (`org.jetbrains.compose.*`) | 1.12.2 | Android artifacts resolve to AndroidX Compose 1.12.x |
| UI | `org.jetbrains.compose.material3` | 1.9.0 | = AndroidX Material 3 1.4.0, the version CMP 1.12.2 pins |
| UI | compose resources (`components-resources`) | 1.12.2 | strings, plurals, fonts, drawables |
| Arch | `org.jetbrains.androidx.lifecycle` (ViewModel, runtime-compose) | 2.11.0 | KMP ViewModel + `SavedStateHandle` |
| Nav | `org.jetbrains.androidx.navigation:navigation-compose` | 2.10.0 | type-safe routes, multiplatform deep links |
| DI | Koin (`koin-core`, `koin-android`, `koin-compose`, `koin-compose-viewmodel`, `koin-test`) | 4.2.2 | |
| Data | Room KMP (`room-runtime`, `room-compiler`, Room Gradle plugin) | 2.8.5 | |
| Data | `androidx.sqlite:sqlite-bundled` | 2.6.2 | `BundledSQLiteDriver` on both platforms (data-layer.md §3) |
| Data | DataStore Preferences KMP | 1.3.0 | |
| Net | Ktor client (core, content-negotiation, logging, OkHttp, Darwin, mock) | 3.6.1 | |
| Kotlin | kotlinx.serialization / coroutines / datetime / collections-immutable | 1.13.0 / 1.11.0 / 0.8.0 / 0.5.2 | |
| Android | activity 1.13.0 · appcompat 1.8.0 · core-ktx 1.19.1 · splashscreen 1.2.0 · WorkManager 2.12.0 | | `androidMain` / `:androidApp` only |
| Android | Play Billing (`billing-ktx`) | 9.1.0 | |
| Android | Credential Manager / Google ID | 1.6.0 / 1.2.1 | |
| Android | Firebase BoM | 35.2.0 | Messaging now; Auth, App Check, Crashlytics, Installations to add (FE-04) |
| Android, to add | Block Store, Play In-App Updates, GMA Next-Gen + UMP, profileinstaller, Macrobenchmark, Roborazzi, Robolectric | *verify at implementation* | versions in v1 of this file are still the reference |
| Test | JUnit 4.13.2, `kotlin-test`, Turbine 1.2.1, Ktor `MockEngine`, Koin test | | |
| Lint | detekt | 1.23.8 | |

iOS dependencies are **Swift packages in the Xcode project** (never Gradle/CocoaPods), pinned in
`iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`: `firebase-ios-sdk` (FirebaseAuth,
FirebaseMessaging, FirebaseAppCheck, FirebaseCrashlytics), `GoogleSignIn-iOS`, `swift-package-manager-google-mobile-ads`
(+ UMP) — exact versions *verify at implementation* (latest stable majors). StoreKit 2, AuthenticationServices,
UserNotifications and BackgroundTasks are system frameworks.

Deliberately **absent** on both platforms: Paging, image loaders (Coil), `material-icons-extended`, Firebase Analytics /
Remote Config / Performance / Firestore, SQLCipher, Retrofit/OkHttp as API (OkHttp only as the Ktor Android engine),
Hilt, GitLive Firebase, SKIE, RevenueCat, CocoaPods.

## 2. Repository layout and modules

```
mobile/
├─ androidApp/                  com.android.application · com.sarkarimatch.app
│                               MainActivity, SmApplication (startKoin with Android bindings), manifest, receivers,
│                               services (FCM), res/xml/locales_config.xml, data_extraction_rules.xml
├─ composeApp/                  KMP + CMP · produces the iOS framework "ComposeApp"
│  ├─ commonMain/               SmApp (root, ProvideAppLocale, theme), StartViewModel (S01), navigation/SmNavHost,
│  │                            DeepLinkRouter, Koin root; feature packages (below); composeResources (feature strings)
│  ├─ androidMain/              Android-only UI glue (PurchaseHost, ads view host)
│  └─ iosMain/                  MainViewController(bridges: IosPlatformBridges), IosEntryPoints (Swift → Kotlin)
├─ core/
│  ├─ model/        (KMP+jvm)   com.sarkarimatch.core.model — AppLanguage (13), LocalizedText, profile enums, Verdict,
│  │                            DeadlineTone, SmFormat (IST, Western digits, Indian grouping), nav-arg enums, MatchCardUi
│  ├─ eligibility/  (KMP+jvm)   com.sarkarimatch.core.eligibility — rule model (rule.schema.json v1) +
│  │                            EligibilityEvaluator; golden tests on docs/backend/examples (→ :matching-core when shared)
│  ├─ designsystem/ (KMP+CMP)   theme (Color, SarkariColors, Type with bundled Baloo 2 + Hind, Dimens), components,
│  │                            domain components (MatchCard, VerdictChip), Material Symbols as composeResources
│  │                            drawables, ProvideAppLocale (expect/actual), TokensParityTest
│  └─ data/         (KMP)       packages: core.network (Ktor SmApi, DTOs, plugins), core.database (Room KMP SmDatabase,
│                               entities, DAOs; schemas in core/data/schemas), core.data (repositories, catalog sync,
│                               MatchEngine adapter, prefs/DataStore, LanguageController, outbox, di), core.billing
│                               (BillingClientPort, EntitlementRepository), core.ads (AdsGate, NativeAdLoader port),
│                               core.notifications (PushHandler, DeliveryGate, composer; androidMain: channels,
│                               receivers, alarms; iosMain: UNUserNotificationCenter scheduler, ReminderWindow),
│                               core.auth (AuthService), core.push (PushService); iosMain core.platform: Swift bridge
│                               protocols (IosBillingBridge, IosAuthBridge, IosPushBridge, IosAdsBridge) + adapters;
│                               di: commonDataModule + androidDataModule / iosDataModule(bridges)
├─ build-logic/{jvm,convention}
└─ (to add) iosApp/             Xcode project: SwiftUI shell, AppDelegate, Swift bridges, entitlements, Info.plist,
                                StoreKit configuration, XCTest/XCUITest targets; later NotificationService extension
```

Feature packages inside `:composeApp` (`com.sarkarimatch.feature.<name>`), each with `navigation/` (routes +
`xxxGraph()`), `ui/` (`XRoute`, stateless `XScreen`, ViewModel, UiState):

| Package | Screens |
| --- | --- |
| `feature.onboarding` | S02–S10, S47, S27, S40, B10, B16, D10 (profile form owner + sign-in entry points) |
| `feature.alerts` | S13–S16, S28, S29, B13 (S14 Android only) |
| `feature.jobs` | S20, S21, S24, S25, S44, B01, B03–B07, B09, B14 |
| `feature.applications` | S22, S23, S37 |
| `feature.pro` | S30–S32, B02, B08, B09, PaywallMomentController, D08 |
| `feature.profile` | S26, S34–S36, S38, S39, S43, S45–S49, B12, B17, D02–D04, D06, D11, D12 |

**Why 6 Gradle modules instead of v1's 18.** On KMP every module is compiled per target (Android, two iOS klibs,
JVM where present) and every iOS-facing module must be exported into the one `ComposeApp` framework; module count
multiplies Kotlin/Native compile and link time on the (paid, slower) macOS CI runners, and Room/KSP runs per target.
For one developer, the v1 boundaries are kept as **packages** instead: features never import each other
(cross-feature navigation via callbacks wired in `navigation/SmNavHost`), and only `core.data` touches Ktor/Room. These
rules are enforced by a detekt `ForbiddenImport` rule set (or Konsist tests in `commonTest`), which is cheaper than
modules. Split a package out into its own module only when a measured build-time or ownership reason appears (first
candidates: `:core:notifications` if its platform code grows, `:feature:profile` with family in P1).

**Platform glue rule:** a platform API is used only in the `androidMain`/`iosMain` of the module that owns the concern,
or in Swift in `iosApp` behind a port (platform-interop.md). `commonMain` never imports `android.*`, `java.*`,
`platform.*`.

## 3. Phased implementation plan (tickets, both platforms)

Roadmap after the founder decisions (backend operations.md §9): **weeks 1–5** validation build, **weeks 6–7** Play
closed test (14 days, ≥ 12 testers, real Play Billing), go/stop, **weeks 8–13** rest of P0 **incl. iOS**, **months
3–6** P1, **month 6+** P2. Estimates are ideal days for one developer; **V** = in the validation build; **A** =
Android-only work, **i** = iOS-only work, unmarked = shared (both platforms get it at once).

**Why the validation test stays Android-only:** decision 8 is a Play closed test with *real* payments. TestFlight
builds can only make sandbox (free) purchases, so iOS cannot validate willingness to pay before release. All shared
code is written for both from day 1 (and CI compiles the iOS framework on every PR), but the iOS shell, Swift bridges
and App Store work start after go/stop. If the test fails, the iOS spend is limited to keeping shared code
iOS-compilable.

### Phase 0 — validation build (weeks 1–5)

| ID | Ticket | Screens | Est |
| --- | --- | --- | --- |
| FE-01 V | `mobile/` KMP repo: `build-logic` (sm.kmp.*), version catalog, 6 modules, Koin root, CI (Android check + **iOS framework compile + iOS simulator `commonTest` on macOS**) | — | 2.5 |
| FE-02 V | `:core:designsystem` CMP theme from tokens (+ `TokensParityTest`), bundled Baloo 2 + Hind **subset to ≤ 450 KB**, 10 base components, Material Symbols drawables | — | 2.5 |
| FE-03 V | `core.network`: Ktor client, DTOs from openapi.yaml, `Call` tags + plugins (App Check, auth, headers, problem+json), `FakeSmApi` / `MockEngine` | — | 2 |
| FE-04 V A | Firebase Android wiring (FCM topics, App Check Play Integrity, Crashlytics opt-in; Auth Google + anonymous) behind ports, staging/prod | — | 1 |
| FE-10 V | Room KMP DB v1 (`@ConstructedBy`, kotlinx-datetime converters) + DAOs + migration test harness (Android host + iOS simulator); DataStore KMP prefs | — | 2.5 |
| FE-11 V | `StartViewModel` (S01 routing incl. family-only, force update, outage) + config/health/reference; `DeepLinkRouter` | S01, S41, S42 | 2.5 |
| FE-12 V | Onboarding S02–S09 + **S47 privacy notice** with shared VM + draft persistence; minor-mode switch; per-app language (Android) | S02–S09, S47 | 4.5 |
| FE-13 V | `CatalogSync` (head/snapshot streamed with `decodeSourceToSequence`, batches, sha256, locales) + `MatchEngine` + S10 | S10 | 4 |
| FE-15 V A | `ReminderPlanner` (common) + Android delivery: WorkManager + alarms, on-device backup R1/R-last, boot re-arm, `TopicSync`, daily reconcile | S14 | 3.5 |
| FE-16 V | Home feed S20 | S20, O01–O04 | 3 |
| FE-17 V | Job detail S21 + B05/B06/B07 | S21 | 3 |
| FE-18 V | Local actions + undo + B01/B03/B04 | B01, B03, B04 | 1.5 |
| FE-19 V | Notifications core: common `PushHandler`/`DeliveryGate`/composer/`NotificationActionHandler`; **A:** 7 channels, receiver | O05 | 3 |
| FE-21 V | S13 primer, S15 OEM guide (A), S16 self-test, S29 | S13, S15, S16, S29 | 3 |
| FE-22 V | My forms S22 + timeline S23 | S22, S23 | 2 |
| FE-23 V | `AuthPlatform` port + Android impl (Credential Manager O11, B16, D10, D11) + `startSession` + anonymous fallback | O11, B16, D10 | 2.5 |
| FE-24 V | `BillingClientPort` + Play Billing impl: S30, S31, S32, restore, verify job, entitlement token gating, B02/B08/B09 | S30–S32 | 4 |
| FE-27 V | Ads: `AdsGate`, `NativeAdSlot` (common), Android GMA Next-Gen + UMP + `AndroidView` host, B17 | O10, B17 | 1.5 |
| FE-28 V | Opt-in usage counters + Crashlytics opt-in | — | 1 |
| FE-30 V A | OEM device matrix QA, Play listing (Hindi ASO), Data safety form, internal → **closed testing** track | — | 2 |

### Phase 1 — rest of P0 for Android + iOS launch (weeks 8–13)

| ID | Ticket | Screens | Est |
| --- | --- | --- | --- |
| FE-20 | Primary alert install (B15, N-47), `upsertDevice` (+ `platform`), `makeDevicePrimary` | B15 | 1 |
| FE-25 | Profile S26, edit S27 + B10, alert settings S28, language S40, help S39 | S26–S28, S39, S40 | 3 |
| FE-26 | Account & privacy S38 (sign out, switch, delete local/account with grace, local export, backup toggle) | S38, D02–D04 | 2 |
| FE-31 | Encrypted backup: common AES-GCM + merge; **A:** Block Store key; **i:** iCloud Keychain key | — | 3.5 |
| FE-29 A | Baseline profile, size/perf pass on Galaxy A03/Redmi, TalkBack/200 % pass | — | 2 |
| FE-32 | Localization pipeline: XLIFF export/import from compose resources, `verifyTranslations`, generated pseudo-locales, iOS `Localizable.xcstrings` generation for notification loc-keys | — | 2 |
| FE-70 i | `iosApp` shell: SwiftUI + `MainViewController`, AppDelegate, `startSm(bindings)`, launch screen, safe areas, entitlements (push, associated domains, Sign in with Apple, time-sensitive, App Group, keychain group), Info.plist (`CFBundleLocalizations`, BG task ids, background modes), `PrivacyInfo.xcprivacy`, xcconfigs per env | — | 2.5 |
| FE-71 i | Firebase iOS via SPM; `FcmPushPlatform` (APNs token → FCM, topics, token refresh), background-push entry point, `AppCheckTokenProvider` (App Attest + DeviceCheck fallback), Crashlytics + Kotlin exception hook + dSYM upload | — | 2.5 |
| FE-72 i | Notifications on iOS: permission flow (S13 iOS), categories + background action handling, `ReminderWindow` (64-request window, plan-time gate, refill triggers), time-sensitive levels, S15/S29 iOS variants | S13, S15, S29 | 4 |
| FE-73 i | Background execution: `BGAppRefreshTask` + `BGProcessingTask` + foreground sync + outbox drain + `NWPathMonitor` connectivity | — | 2 |
| FE-74 i | Auth on iOS: `FirebaseAuthPlatform`, Google Sign-In iOS, **Sign in with Apple** (O11a, button in CMP), Apple token revocation on delete | O11a, B16, D03 | 2.5 |
| FE-75 i | StoreKit 2: `StoreKitBillingPort`, products (yearly + intro offer, season non-renewing), `appAccountToken`, `Transaction.updates`/unfinished sweep, restore, manage subscriptions, iOS paywall terms; StoreKit config file + `SKTestSession` tests; sandbox E2E against staging | S30–S32, B02 | 4 |
| FE-76 i | Ads on iOS: Google Mobile Ads + UMP via SPM, `AdMobNativeAdLoader`, `NativeAdViewFactory` in `UIKitView`, `npa=1`, no ATT, SKAdNetwork items | O10 | 1.5 |
| FE-77 i | Universal Links (AASA with backend), custom scheme, `.onOpenURL` + `continueUserActivity` → `ExternalUriHandler`; per-app language (CFBundleLocalizations, AppleLanguages) + Dynamic Type mapping | — | 1.5 |
| FE-78 i | iOS QA + perf: iPhone XR/11 launch and scroll, memory, binary size ≤ 25 MB, VoiceOver pass (hi/en), Dynamic Type AX sizes, Low Power Mode / BG refresh off behaviour | — | 2.5 |
| FE-79 i | App Store Connect: app record, IAP products + subscription group + intro offer + Family Sharing off, privacy labels, age rating, review notes (test profile, on-device matching, "not a government app", sources), screenshots (hi/en), **TestFlight** internal → external beta → submission | — | 2.5 |

### Phase 2 — P1 (months 3–6)

| ID | Ticket | Screens |
| --- | --- | --- |
| FE-40 | Daily digest S44 (+ ad slot) and digest settings B13 (iOS: digest as `.passive` scheduled notification) | S44, B13 |
| FE-41 | Alert history S24, archive S25 | S24, S25 |
| FE-42 | Last-date extension / corrigendum polish (B07 diff, badges) | B07 |
| FE-43 | Exam clash S37 + S22 banner | S37 |
| FE-44 | Certificates S36 (+ validity badges on S21) | S36 |
| FE-45 | Family: S34/B12/D06 + S48 invite link/QR + invitee S35 + S49/D12; encrypted watch-list; `family` channel N-50…N-54; **i:** copy-code fallback (no install referrer), local N-50/N-51 in the window | S34, S35, S48, S49 |
| FE-80 i | Notification Service Extension + `notification-hints.json` in the App Group: personalised lifecycle/family alerts; apply for the notification-filtering entitlement | — |
| FE-46 | First non-launch language (pilot, e.g. mr or bn): translation, fonts (Android downloadable; iOS system/CDN), QA on both | S02, S40 |
| FE-47 | Referral S45 + Android install referrer / iOS code entry, share card S46 (`GraphicsLayer.toImageBitmap` → platform share sheet) | S45, S46 |
| FE-48 | Android flexible in-app update D09; TTS "सुनें" on S21 (`TextToSpeech` / `AVSpeechSynthesizer` behind a port) | D09 |

### Phase 3 — P2 (month 6+)

FE-60 age-limit countdown S43 + B14 · FE-61 payment-status watch (B01 fee checkbox) · FE-62 Urdu (RTL) release on both
platforms · FE-63 early alerts from exam calendar (new feed section) · FE-64 evaluate Navigation 3 multiplatform,
Swift Export/SKIE for the Swift bridges, iPad layouts.
