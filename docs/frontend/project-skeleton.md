# Project skeleton, Gradle setup, implementation plan

## 1. Gradle setup

- **AGP 9.5.1** (built-in Kotlin support: no `org.jetbrains.kotlin.android` plugin needed), **Gradle 9.9.1** wrapper (AGP 9.5 needs ≥ 9.6), JDK 17 toolchain, Kotlin **2.4.21**, KSP **2.3.14** (KSP2, own version line).
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`. (Raise target to 37 when Play requires it; AGP 9.5 supports API 37.)
- `build-logic/` included build with convention plugins: `sm.android.application`, `sm.android.library`, `sm.android.compose`, `sm.android.feature` (library + compose + hilt + common feature deps), `sm.android.room`, `sm.hilt`, `sm.jvm.library`. Configuration cache + build cache on.
- Product flavours: `env` = `prod` / `staging` (base URL, Firebase project); optional `upi` flavour only for the closed WhatsApp test build (billing.md §7). Build types: `debug` (fake backend switch, StrictMode, LeakCanary), `release` (R8 full, shrink resources), `benchmark` (release-like, debug-signed).

### Versions verified 2026-11-19

Sources: developer.android.com AndroidX versions table (updated 2026-11-18), Compose BOM mapping page, AGP release notes, Play Billing release notes, Maven Central metadata, Firebase release notes (via search). Entries marked *verify* could not be read from Google Maven from this environment; pin them at implementation time.

```toml
# gradle/libs.versions.toml
[versions]
agp = "9.5.1"
kotlin = "2.4.21"
ksp = "2.3.14"
composeBom = "2026.11.01"            # → compose ui/foundation 1.13.0, material3 1.5.0
activity = "1.13.0"
appcompat = "1.8.0"                   # per-app locales on API < 33
coreKtx = "1.19.1"
coreSplashscreen = "1.2.0"            # verify
lifecycle = "2.11.1"
navigation = "2.10.2"
room = "2.8.5"
work = "2.12.0"
datastore = "1.3.0"
androidxHilt = "1.4.0"                # hilt-navigation-compose / hilt-work
hilt = "2.60.1"
profileinstaller = "1.4.1"
benchmark = "1.5.0"                   # macro-junit4 + baselineprofile plugin
uiautomator = "2.4.1"
kotlinxSerialization = "1.13.0"
kotlinxCoroutines = "1.11.0"
kotlinxImmutable = "0.5.2"
retrofit = "3.0.0"
okhttp = "5.5.1"
playBilling = "9.1.0"
firebaseBom = "35.1.0"                # minSdk 24+, OK for 26
playAppUpdate = "2.1.0"               # verify (Play In-App Updates, S41/D09)
playServicesAuthApiPhone = "18.2.0"   # verify (SMS Retriever + Phone Number Hint)
gmaNextGen = "1.6.0"                  # verify (Google Mobile Ads Next-Gen SDK, native ads; ads.md)
ump = "3.2.0"                         # verify (User Messaging Platform)
googleServices = "4.4.4"              # verify (plugin)
crashlyticsPlugin = "3.0.6"           # verify (plugin)
junit4 = "4.13.2"
turbine = "1.2.1"
mockk = "1.14.12"
robolectric = "4.18.1"
roborazzi = "1.79.0"
androidxTestExtJunit = "1.3.0"        # verify
detekt = "1.23.8"                     # verify
spotless = "7.2.1"                    # verify

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "coreSplashscreen" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-text-google-fonts = { module = "androidx.compose.ui:ui-text-google-fonts" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-navigation-testing = { module = "androidx.navigation:navigation-testing", version.ref = "navigation" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { module = "androidx.work:work-testing", version.ref = "work" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "androidxHilt" }
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "androidxHilt" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "androidxHilt" }
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
androidx-benchmark-macro = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "benchmark" }
androidx-uiautomator = { module = "androidx.test.uiautomator:uiautomator", version.ref = "uiautomator" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-android-testing = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinxImmutable" }
retrofit-core = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { module = "com.squareup.retrofit2:converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp-core = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver3", version.ref = "okhttp" }
play-billing-ktx = { module = "com.android.billingclient:billing-ktx", version.ref = "playBilling" }
play-app-update-ktx = { module = "com.google.android.play:app-update-ktx", version.ref = "playAppUpdate" }
play-services-auth-api-phone = { module = "com.google.android.gms:play-services-auth-api-phone", version.ref = "playServicesAuthApiPhone" }
gma-next-gen = { module = "com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk", version.ref = "gmaNextGen" }
ump = { module = "com.google.android.ump:user-messaging-platform", version.ref = "ump" }
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { module = "com.google.firebase:firebase-auth" }
firebase-messaging = { module = "com.google.firebase:firebase-messaging" }
firebase-appcheck-playintegrity = { module = "com.google.firebase:firebase-appcheck-playintegrity" }
firebase-appcheck-debug = { module = "com.google.firebase:firebase-appcheck-debug" }
firebase-installations = { module = "com.google.firebase:firebase-installations" }
firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics" }
firebase-analytics = { module = "com.google.firebase:firebase-analytics" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
android-test = { id = "com.android.test", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
baselineprofile = { id = "androidx.baselineprofile", version.ref = "benchmark" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "crashlyticsPlugin" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

Deliberately **absent**: Paging (volumes are small), Coil/Glide (no raster images), material-icons-extended (multi-MB), Firebase Remote Config/Performance/Firestore, `androidx.security:security-crypto` (deprecated; nothing to encrypt ourselves), SQLCipher, Ktor, Timber in release, Accompanist (superseded).

`app/build.gradle.kts` (excerpt):

```kotlin
plugins {
    alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose); alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp); alias(libs.plugins.hilt); alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics); alias(libs.plugins.baselineprofile)
}
android {
    namespace = "in.sarkarimatch.app"
    compileSdk = 36
    defaultConfig { applicationId = "in.sarkarimatch"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
    androidResources { localeFilters += listOf("en", "hi"); generateLocaleConfig = false } // locales_config.xml is hand-written
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true
                  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    buildFeatures { buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(projects.core.designsystem); implementation(projects.core.data); implementation(projects.core.notifications)
    implementation(projects.core.billing); implementation(projects.core.ads)
    implementation(projects.feature.onboarding); implementation(projects.feature.alerts); implementation(projects.feature.jobs)
    implementation(projects.feature.applications); implementation(projects.feature.pro); implementation(projects.feature.profile)
    implementation(libs.androidx.appcompat); implementation(libs.androidx.core.splashscreen); implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.navigation.compose); implementation(libs.androidx.hilt.work)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler); ksp(libs.androidx.hilt.compiler)
    implementation(libs.play.app.update.ktx)
    baselineProfile(projects.baselineprofile)
}
```

## 2. Repository layout and packages

```
android/
├─ app/                         in.sarkarimatch.app        MainActivity, SmApplication, SmNavHost, StartViewModel, DI wiring, deep links, S41/S42
├─ build-logic/convention/      convention plugins
├─ core/
│  ├─ model/        (JVM)       in.sarkarimatch.core.model  domain models, LocalizedText, UiText, AppLanguage, enums, IST formatters
│  ├─ designsystem/             …core.designsystem           theme/, component/, domain/ (MatchCard, VerdictChip…), icons (vector xml)
│  ├─ network/                  …core.network                SmApi, dto/, interceptors/, ApiResult, ProblemJson
│  ├─ database/                 …core.database               SmDatabase, entity/, dao/, converters/, schemas/ (exported)
│  ├─ data/                     …core.data                   repository/, sync/, queue/ (actions, outbox), work/, prefs/ (DataStore), auth/, flags/
│  ├─ notifications/            …core.notifications          SmMessagingService, PushHandler, DeliveryGate, SmNotifier, channels, receivers, ReminderMirror, OemAutostart
│  ├─ billing/                  …core.billing                PlayBillingRepository
│  ├─ ads/                      …core.ads                    GmaNativeAdLoader, AdConsentManager, NativeAdSlot
│  └─ testing/                  …core.testing                fakes, rules, fixtures
├─ feature/
│  ├─ onboarding/               S02–S12, S27, B10 (profile form owner + phone auth)
│  ├─ alerts/                   S13–S16, S28, S29, B13
│  ├─ jobs/                     S20, S21, S24, S25, S44, B01, B03–B07, B09, B14
│  ├─ applications/             S22, S23, S37
│  ├─ pro/                      S30–S33, B02, B08, B11, PaywallMomentController, D05, D08
│  └─ profile/                  S26, S34–S36, S38–S40, S43, S45, S46, B12, D02–D04, D06
└─ baselineprofile/             com.android.test module
```

Inside a feature: `navigation/` (routes + `xxxGraph()`), `ui/` (`XRoute`, stateless `XScreen`, previews), `XViewModel`, `model/` (UiState, mappers). No `domain/usecase` layer by default — add a use case only when logic is shared by ≥ 2 VMs (e.g. `RecordMatchActionUseCase` is just `MatchActionRepository.record`).

## 3. Phased implementation plan (tickets)

Aligned with PRD roadmap: **weeks 1–2** WhatsApp payment test (no app needed), **weeks 3–10** P0 release, **months 3–6** P1, **month 6+** P2. Estimates are ideal days for one developer; the P0 total (~44 d) fits weeks 3–10 only if the backend's fake/staging is ready by week 4 — otherwise build against the `FakeSmApi` server first.

### Phase 0 — weeks 1–2 (parallel with the WhatsApp test)

| ID | Ticket | Est |
| --- | --- | --- |
| FE-01 | Repo, `build-logic`, version catalog, modules, CI workflow (lint/detekt/unit/Roborazzi/size gate) | 1.5 |
| FE-02 | `:core:designsystem` theme from tokens (+ `TokensParityTest`), Mukta downloadable font, 10 base components with previews/screenshots | 2 |
| FE-03 | `:core:network` DTOs from openapi.yaml, interceptors, ProblemJson, contract-diff script; `FakeSmApi` | 2 |
| FE-04 | Firebase project wiring (Auth anon, App Check, FCM, Crashlytics), staging/prod flavours | 0.5 |

### Phase 1 — P0 release (weeks 3–10)

| ID | Ticket | Screens | Est |
| --- | --- | --- | --- |
| FE-10 | Room DB v1 + DAOs + migrations test harness; DataStore prefs | — | 2 |
| FE-11 | `SessionRepository` + `StartViewModel` (S01 routing, force update, outage) + `ConfigRepository`, `HealthRepository` | S01, S41, S42 | 2 |
| FE-12 | Onboarding form S02–S09 with shared VM + draft persistence + reference cache | S02–S09 | 4 |
| FE-13 | S10 result (`putProfile`, preview fallback) | S10 | 1 |
| FE-14 | Phone OTP S11/S12 (`sendOtp`/`verifyOtp`, SMS Retriever, custom token, account-switch full sync) | S11, S12 | 2 |
| FE-15 | SyncEngine + workers + notice/detail prefetch + cleanup | — | 3 |
| FE-16 | Home feed S20 (sections, banners, empty states, pull-to-refresh, tab badge) | S20, O01–O04 | 3 |
| FE-17 | Job detail S21 + B05/B06/B07 | S21 | 3 |
| FE-18 | Action queue (optimistic, batch flush, undo) + B01/B03/B04 | B01, B03, B04 | 2.5 |
| FE-19 | Notifications core: channels, data-message rendering, actions receiver, receipts, DeliveryGate | O05 | 3 |
| FE-20 | Local reminder mirror (WorkManager + inexact alarms, boot re-arm) + B15 primary device | — | 2 |
| FE-21 | Permission primer S13, OEM guide S15, self-test S16, delivery health S29 | S13, S15, S16, S29 | 3 |
| FE-22 | My forms S22 + timeline S23 (locked/unlocked) | S22, S23 | 2.5 |
| FE-23 | Play Billing: S30, S31, S32, restore, verify worker, entitlement gating, B02/B08/B09 | S30–S32 | 4 |
| FE-24 | WhatsApp opt-in S33 (+ D05) | S33 | 1 |
| FE-25 | Profile S26, edit S27 + B10, alert settings S28, language S40, help S39 | S26–S28, S39, S40 | 3 |
| FE-26 | Account & privacy S38 (logout, delete with grace, export, consents) | S38, D02–D04 | 1.5 |
| FE-27 | Free-tier native ad slot (`:core:ads`, UMP, `AdsGate`) on S20 | O10 | 1.5 |
| FE-28 | Analytics events + consent gating; Crashlytics keys | — | 1 |
| FE-29 | Baseline profile, size/perf pass on Galaxy A03/Redmi, a11y pass (TalkBack, 200 %) | — | 2 |
| FE-30 | OEM device matrix QA, Play listing assets (Hindi ASO), internal → closed testing | — | 2 |

### Phase 2 — P1 (months 3–6)

| ID | Ticket | Screens |
| --- | --- | --- |
| FE-40 | Daily digest S44 (+ ad slot) and digest settings B13 | S44, B13 |
| FE-41 | Alert history S24, archive S25 | S24, S25 |
| FE-42 | Last-date extension / corrigendum polish (B07 diff, badges) | B07 |
| FE-43 | Exam clash S37 + S22 banner | S37 |
| FE-44 | Certificates S36 (+ validity badges on S21) | S36 |
| FE-45 | Family alerts S34/B12/D06 + invitee S35 | S34, S35 |
| FE-46 | Exact-alarm opt-in S14 | S14 |
| FE-47 | Referral S45 + install referrer, share card S46 | S45, S46 |
| FE-48 | Flexible in-app update D09, TTS "सुनें" on S21 | D09 |

### Phase 3 — P2 (month 6+)

FE-60 age-limit countdown S43 + B14 · FE-61 payment-status watch (B01 fee checkbox) · FE-62 SMS backup settings · FE-63 early alerts from exam calendar (new feed section) · FE-64 evaluate Navigation 3 / Room 3 migration.
