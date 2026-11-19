# Sarkari Match — mobile app (Android + iOS)

Hindi-first, alert-only government-job app, built once with **Kotlin Multiplatform** (shared logic) and **Compose
Multiplatform** (shared UI) for Android and iOS (founder decision 9, `docs/PRD.md`). Architecture: `../docs/frontend/`
(start with `platform-interop.md` and `project-skeleton.md`). Visual style: Direction C "Marigold Home"
(`../docs/design/02-design-system.md`, `tokens.json` v2.0), identical on both platforms.

Matching runs **on the phone** (`:core:eligibility`); the profile never leaves the device
(`../docs/backend/matching-engine.md` §0).

## Modules (6)

| Module | Kind | Targets | Contents |
| --- | --- | --- | --- |
| `:core:model` | KMP | jvm, iosArm64, iosSimulatorArm64 | `AppLanguage` (13, hi + en launched), `LocalizedText`, profile enums, `Verdict`, IST dates (`kotlinx-datetime`), `SmFormat` (Western digits, Indian grouping, hi/en month names), nav-arg enums, `MatchCardUi` |
| `:core:eligibility` | KMP | jvm, iOS | Rule model of `rule.schema.json` v1 + `EligibilityEvaluator`. Tests in `commonTest` use `docs/backend/examples/*.rule.json`, embedded at build time |
| `:core:designsystem` | KMP + CMP | android, iOS | Marigold theme (light + dark `ColorScheme`, `SarkariColors` via CompositionLocal), bundled Baloo 2 + Hind, type scale, shapes, spacing, components, `MatchCard`, Material Symbols as vector drawables, `ProvideAppLocale`, `TokensParityTest` (commonTest) |
| `:core:data` | KMP | android, iOS, desktop (dev) | Ktor `SmApi` (catalog, config, session, entitlement, trial), Room KMP `SmDatabase` (catalog, profile, matches, action log, applications, account, entitlement, config), DataStore prefs, `CatalogSync` (head → snapshot → batches) + `CatalogMatcher` (on-device matching → matches), `SyncCoordinator`, `AccountRepository` (session bootstrap, entitlement, trial), `MatchRepository` (feed, detail with why-you-match, My forms), `MatchActionRepository` (Applied / Not interested / undo, local only), `MatchEngine`, Koin `dataModule`; platform-service interfaces + Android impls (`PlayBillingService`, `CredentialManagerAuthService`, `FcmPushService`, `WorkManagerReminderScheduler`, `AndroidLanguageController`) and iOS adapters (`IosReminderScheduler`, `Ios*Bridge` protocols implemented in Swift) |
| `:composeApp` | KMP + CMP | android, iOS, desktop (dev) | `App()`, `SmApp` (3 tabs), `SmNavHost` (type-safe Navigation Compose multiplatform), every screen (real: S02 language, S06 date of birth, S20 Home, S21 job detail, B02 Applied ✓ + trial, S22 My forms; the rest placeholders with their inventory ID), ViewModels, Koin UI module; iOS framework **`ComposeApp`** (`MainViewController`, `startSmIfNeeded`); desktop `DesktopMain.kt` |
| `:androidApp` | Android app | android | `SmApplication` (starts Koin), `MainActivity` (edge-to-edge, splash, per-app language, Hindi on first run), manifest, flavours `prod`/`staging`, launcher icon |

`iosApp/` is the Xcode project (SwiftUI shell + Swift bridges). Why `:androidApp` is separate: AGP 9 no longer allows
`com.android.application` in a KMP module, so the shared UI is a KMP library and the APK is a thin shell (the current
JetBrains wizard layout). Feature code lives in packages `com.sarkarimatch.feature.<name>` inside `:composeApp`.

## Libraries (gradle/libs.versions.toml)

Kotlin 2.4.21 · AGP 9.5.1 (`com.android.kotlin.multiplatform.library` for KMP modules) · Compose Multiplatform 1.12.2
(Material 3 1.9.0, compose resources) · JetBrains Navigation Compose 2.10.0 · JetBrains Lifecycle/ViewModel 2.11.0 ·
Koin 4.2.2 · Ktor 3.6.1 (OkHttp / Darwin engines) · kotlinx.serialization 1.13.0 · coroutines 1.11.0 ·
kotlinx-datetime 0.8.0 · Room 2.8.5 KMP + sqlite-bundled 2.6.2 · DataStore 1.3.0 · Android only: Play Billing 9.1.0,
Credential Manager 1.6.0 + googleid 1.2.1, Firebase BOM 35.2.0 (messaging), WorkManager 2.12.0, AppCompat 1.8.0 ·
detekt 1.23.8. iosX64 (Intel simulators) is not a target: CMP 1.12 and Navigation 2.10 no longer publish it.

Choices: **Room KMP** over SQLDelight (keeps the v1 entities/DAOs/exported schemas, one developer already knows it;
data-layer.md §3). **Koin** over Hilt (Hilt is Android-only). **Bundled fonts** (Downloadable Google Fonts are
Android-only): `core/designsystem/src/commonMain/composeResources/font`, rebuilt by `tools/fonts/subset.sh`.
Currently 493 KB raw (~230 KB compressed in APK/IPA) — 43 KB over the 450 KB budget; options: drop rarely used
Baloo 2 conjunct ligatures after a usage audit, or use Hind 600 for `title*` roles.

## Setup

- JDK 17+, Android Studio (current stable, with the Kotlin Multiplatform plugin) with Android SDK platform 37.
- `local.properties` with `sdk.dir=/path/to/Android/sdk` (or `ANDROID_HOME`). Not committed.
- Gradle comes from the wrapper (`./gradlew`, Gradle 9.9.1).
- iOS: a Mac with Xcode 16+ (the project uses Xcode 16 synchronized folders), iOS 16+ deployment target.

```bash
./gradlew :androidApp:assembleProdDebug           # debug APK, prod API (assembleStagingDebug: staging)
./gradlew :core:model:jvmTest :core:eligibility:jvmTest \
          :core:designsystem:testAndroidHostTest :core:data:testAndroidHostTest :composeApp:testAndroidHostTest
./gradlew detekt
./gradlew -Psm.jvmOnly=true :core:eligibility:jvmTest   # no Android SDK / Google Maven needed
```

On Linux/Windows the iOS targets are disabled automatically (`kotlin.native.ignoreDisabledTargets=true`), so
everything above works without a Mac.

### Local backend (Android `dev` flavour)

`./gradlew :androidApp:assembleDevDebug` builds `in.sarkarimatch.app.dev.debug` against `http://10.0.2.2:8080/` (the
host's `backend/` seen from the emulator; override with `-Psm.apiBaseUrl.dev=http://<lan-ip>:8080/` for a phone).
Only this flavour allows cleartext HTTP (`androidApp/src/dev/`) and sends the backend's dev token
`Authorization: Bearer dev:<uid>` (`AppConfig.devAuth`; one random uid per install), so `/v1/session`, the
entitlement and the trial work without Firebase. `prod`/`staging` keep HTTPS-only and no account until Firebase
(FE-06) is wired (`NoIdTokenProvider`).

### Desktop dev target (JVM) and the end-to-end run

`:core:designsystem`, `:core:data` and `:composeApp` also have a `jvm("desktop")` target. It is a development tool
(Linux has no Android emulator without KVM, and iOS needs a Mac) and is never shipped: Room + DataStore files live in a
local directory, platform services are stubs (guest sign-in, no billing/push/ads/reminders).

```bash
cd backend && make run & scripts/seed-dev.sh            # local backend with the dev seed notices
cd mobile
./gradlew :composeApp:run                               # the app in a window (SM_API_BASE_URL, SM_DATA_DIR env)
./gradlew :composeApp:desktopTest -Psm.e2e=true         # e2e + screenshots (-Psm.e2e.baseUrl=http://host:8080/)
```

`EndToEndTest` (composeApp/src/desktopTest) starts from an empty database and runs the real Koin graph against
`localhost:8080`: dev-auth session → config → catalog head + snapshot/batches into Room → sample profile (23-year-old
OBC male BA graduate from UP) → on-device verdicts for the seeded notices (4 eligible, UPSSSC JA check-yourself, NDA not
eligible) → feed → Applied (local) → My forms → `POST /v1/me/trial` → `GET /v1/me/entitlement` is Pro/trial. It then
renders the real screens with that data (`ImageComposeScene`, Skia, 412 × 892 dp at 2×) in Hindi and English to
[`docs/screenshots/e2e/`](docs/screenshots/e2e/). Without `-Psm.e2e=true` the test is skipped, so `desktopTest` stays
green without a backend.

### Running on iOS (Mac)

1. `cd mobile && ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` once (downloads the Kotlin/Native
   toolchain into `~/.konan`).
2. Open `iosApp/iosApp.xcodeproj` in Xcode, set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` (only needed for a
   device; the simulator runs unsigned), pick an iPhone simulator, Run. The *Compile Kotlin Framework* build phase
   calls `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.
3. Or from Android Studio with the Kotlin Multiplatform plugin: run configuration *iosApp*.
4. iOS commonTest: `./gradlew :core:eligibility:iosSimulatorArm64Test` (and the same task in the other modules).

Bundle id `in.sarkarimatch.app` (placeholder, same as the Android applicationId). Info.plist: `CFBundleLocalizations`
hi + en (development region hi), `SMApiBaseUrl`, background modes, BGTask identifiers; entitlements: push, Sign in
with Apple, associated domains, time-sensitive notifications, App Group `group.in.sarkarimatch.app`.

## Platform services

Common interfaces in `:core:data` (bound per platform in Koin, faked in tests; platform-interop.md §2a):

| Concern | Interface | Android | iOS |
| --- | --- | --- | --- |
| Billing | `BillingService` | `PlayBillingService` (Play Billing 9: `pro` + base plans) | Swift `StoreKitBillingBridge` (StoreKit 2) → `IosBillingService`; ids `in.sarkarimatch.pro.yearly` / `.season4m` |
| Sign-in | `AuthService` | `CredentialManagerAuthService` (needs `-Psm.googleServerClientId=…`; else guest only) | Swift `AuthBridge`: Sign in with Apple (done), Google Sign-In (SPM, FE-06) |
| Push | `PushService` | `FcmPushService` + `SmMessagingService` (inactive until `google-services.json`) | Firebase Messaging via SPM (FE-08), Swift forwards to `IosPushEvents` |
| Local reminders | `ReminderScheduler` | WorkManager (exact alarms for `critical`: FE-14) | `IosReminderScheduler`: UNUserNotificationCenter, 60-request window + 4 reserved (iOS caps pending requests at 64), diffed, IST calendar triggers |
| Ads | `AdsService` + `PlatformNativeAdView` | GMA Next-Gen `NativeAdView` in `AndroidView` (FE-27) | Swift `GADNativeAdView` from `IosAdsBridge.makeNativeAdView` in `UIKitView` (FE-76); no ATT prompt |
| App language | `LanguageController` | AppCompat per-app locales + DataStore | DataStore + `AppleLanguages`, Compose root re-keyed (`ProvideAppLocale`) |

Swift bridges are nullable: a nil bridge falls back to a stub (free plan, guest only, no push, no ads). Koin starts in
`AppDelegate.didFinishLaunching` (`KoinIosKt.startSmIfNeeded`) so background launches have the graph too. iOS
storage: the App Group container (fallback Application Support), never `Documents/`, excluded from backup.

## Conventions

- Code packages `com.sarkarimatch.*`; `applicationId` / bundle id `in.sarkarimatch.app` (placeholder domain).
- Strings: Compose resources, `composeResources/values/` = English (fallback), `values-hi/` = Hindi; Hindi is the
  default app language on first run on both platforms. `sm.enabledLocales` (gradle.properties),
  `androidApp/src/main/res/xml/locales_config.xml` and `CFBundleLocalizations` list only enabled languages.
- No hex colours, raw `.sp` or ad-hoc fonts outside `:core:designsystem`.
- Test names are camelCase (backtick names with spaces are JVM-only and break iOS test compilation).
- Unbuilt screens render `PlaceholderScreen` with their inventory ID (also the test tag).
