# Testing and CI

Goal for a solo developer: catch the expensive bugs (wrong on-device verdicts, lost "Applied" taps, missed reminders,
a broken iOS notification window, broken layouts in any language incl. RTL, billing state on two stores, data leaving
the device) with fast **`commonTest`** tests that run on both platforms, and keep device/simulator tests to a thin
smoke layer.

## 1. Test pyramid

Most code is `commonMain`, so most tests are `commonTest` (kotlin.test + kotlinx-coroutines-test + Turbine + Koin test +
Ktor `MockEngine`; no MockK, which is JVM-only). They run twice in CI: on the JVM/Android host (`jvmTest` /
`testAndroidHostTest`, seconds, on Linux) and on the **iOS simulator** (`iosSimulatorArm64Test`, macOS), which catches
Kotlin/Native differences (string formatting, `Dispatchers`, kotlinx-datetime time zones, Room's native driver).

| Layer | Tools | What | Where it runs |
| --- | --- | --- | --- |
| Pure unit (`commonTest`) | kotlin.test, coroutines-test, **Turbine**, fakes | ViewModels (state sequences), mappers/formatters (IST dates, ₹, "2 दिन बाकी", deadline tone, section bucketing, `RuleLine` rendering), `DeliveryGate`, **`ReminderWindow` selection (64-cap, R-last/R1 first, sentinel)**, `:core:eligibility` **golden tests shared with the backend** (SSC GD 2026, UP Police 2025 v1/v2, boundary generators), reminder slot tests, `CatalogSync` ordering/sha256/evaluator gate, backup merge, `AdsGate` (incl. minor mode), `PaywallMomentController` (Play server trial vs iOS intro offer), `DeepLinkRouter` back stacks, `KoinGraphTest` (`verify()` with fake platform bindings) | every PR: JVM host < 2 min; iOS simulator on the macOS job |
| Data/integration (`commonTest` + host) | Room KMP in-memory (`inMemoryDatabaseBuilder` + driver), **Ktor `MockEngine`**, DataStore in a temp dir | DAO queries, snapshot streaming + batch apply, corrupt batch skipped, `Idempotency-Key` reuse, 401 refresh once, 426 → force update, ETag 304, outbox drain order, **privacy guard**: a MockEngine test asserts no request body or header ever contains profile fields (dob, category, pwd, qualifications) and that reports/telemetry/push-tests carry no installation id or ID token | every PR, both platforms |
| Room migrations | Room KMP `MigrationTestHelper` against `core/data/schemas` | every version N-1 → N and 1 → latest; **profile, matches and action log must survive** | every PR touching `core.database`, Android host + iOS simulator |
| Android platform code (host) | Robolectric, WorkManager test helpers | channels, receivers (`goAsync` path), alarm scheduling, `FirebaseMessagingService` → `PushHandler`, OEM intent table, Play Billing port with a fake `BillingClient` | every PR |
| Compose UI (host) | Compose Multiplatform UI test (`runComposeUiTest`) on the Android host/Robolectric, `enableAccessibilityChecks()`, `StateRestorationTester` | each screen's stateless `XScreen(state)` for every UiState variant; custom accessibility actions on `MatchCard`; sheets B01–B07 flows | every PR |
| Screenshot — gate | **Roborazzi** (Robolectric native graphics) | every designsystem component + every screen state × {light, dark} × {hi, en} × {font 1.0, 2.0}; key screens (S02, S10, S20, S21, S30, S47) also in generated pseudo-locales **`en-XA`** and **`ar-XB`**, and later `ta`, `ur`; Devanagari conjunct sheet with the bundled fonts | every PR; diffs as artifact |
| Screenshot — iOS deltas | Roborazzi's Compose Multiplatform iOS capture (experimental — verify) or an XCTest snapshot of `MainViewController` | ~10 key screens: Skia text shaping with the bundled fonts, safe areas/notch, Dynamic Type L / AX2, Apple sign-in button, iOS paywall terms | macOS job, every PR touching UI |
| Swift bridges | **XCTest** in `iosAppTests`: `StoreKitBillingPort` with `SKTestSession` + `SarkariMatch.storekit` (purchase, intro offer, pending/Ask to Buy, renewal, expiry, refund, unfinished-transaction redelivery), notification category registration, `AppDelegate` → `IosEntryPoints` wiring, Keychain key store | macOS job |
| Contract | JSON fixtures from `openapi.yaml` examples + a script diffing DTO `@SerialName`s against schema properties | DTOs never drift from the API | every PR |
| Android instrumented smoke | Gradle Managed Devices (API 26, API 35 ATD), UiAutomator | onboarding → Home with a fixture catalog; *भर दिया* from the shade while killed; topic push held until reminder time; on-device backup fires without push; POST_NOTIFICATIONS + exact alarm; "don't keep activities"; deep link back stack | nightly + release branch |
| iOS UI smoke | **XCUITest** on the simulator (iPhone SE 3rd gen + iPhone 16-class, iOS 16 and latest runtimes) | onboarding → Home with a fixture catalog; grant notifications (`addUIInterruptionMonitor`); schedule a reminder 1 min ahead and act on it from Notification Center (long-press → भर दिया) with the app terminated; Universal Link / custom-scheme cold start; `performAccessibilityAudit()` on key screens | nightly + release branch |
| Performance | Android Macrobenchmark + Baseline Profile; iOS `XCTApplicationLaunchMetric` / `XCTOSSignpostMetric` on a device | cold start, feed scroll, **evaluate 300 posts < 50 ms, apply 1 MB snapshot < 1.5 s** on both | weekly + release |
| Manual device matrix | Android: Redmi, Vivo, Oppo/Realme, Samsung. iOS: iPhone XR/11 (iOS 16/17), a current iPhone (latest iOS), Focus + Scheduled Summary on, Low Power Mode, BG App Refresh off | push when killed/force-quit, autostart (Android), window refill after 3 days unopened (iOS), self-test, Google/Apple sign-in + backup restore on a second phone, sandbox purchase + restore | each release (checklist in repo) |

## 2. Fakes (`commonTest` helpers in each module; shared fixtures in `core/data/src/commonTest`)

`FakeSmApi` / `MockEngine` dispatcher (in-memory server for the operations used, incl. a fixture catalog
head/snapshot/batches, switchable failures/latency), `FakeMatchRepository`, **fake platform ports**
(`FakeAuthPlatform` with Google/Apple/anonymous, `FakeSecureKeyStore`, `FakeBillingClientPort` with Play and App Store
modes, `FakePushPlatform`, `FakeLocalNotifier` that enforces the 64-request cap like iOS, `FakeBackgroundScheduler`,
`FakeNativeAdLoader`, `FakeConnectivity`), `TestIstClock` (settable `Instant`), `fixtures/` (JSON from OpenAPI
examples). The fake backend also runs in debug builds on both platforms (`-Pbackend=fake` / Xcode scheme env
`SM_BACKEND=fake`) so UI work proceeds before the real backend.

## 3. Examples

```kotlin
class HomeViewModelTest {                                      // commonTest: runs on JVM/Android host AND iOS simulator
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()
    private val clock = TestIstClock("2026-11-30T09:00+05:30")
    private val matches = FakeMatchRepository()
    private val actions = FakeMatchActionRepository()

    @Test fun lastDayMatchGoesIntoTodayAndAppliedRemovesIt() = runTest {   // backtick names are not allowed on Kotlin/Native
        matches.emit(match(id = "m1", lastDate = "2026-11-30"), match(id = "m2", lastDate = "2026-12-20"))
        val vm = homeVm()
        vm.state.test {
            val content = awaitItem().feedContent()
            assertEquals(SectionKind.LastDayToday, content.sectionOf("m1"))
            vm.onAction(HomeAction.ConfirmApplied("m1", feePaid = false))
            assertEquals("m1" to UserAction.Applied(false), actions.recorded.single())
            assertEquals(listOf("m2"), awaitItem().feedContent().ids())
        }
    }
}

class RuleLineFormattingTest {
    @Test fun ageRuleRendersTextAndPassGlyph() {
        val ui = RuleLineMapper.map(ruleExplanation(rule = "age", result = "pass",
            hi = "उम्र 24 साल (01-08-2026 को), सीमा 27, OBC +3"), AppLanguage.HI)
        assertEquals(Glyph.Pass, ui.glyph)            // ✓ + check_circle + success colour
        assertEquals("उम्र 24 साल (01-08-2026 को), सीमा 27, OBC +3", ui.text)
    }
    @Test fun missingEnglishFallsBackToHindi() { /* LocalizedText.pick */ }
}

class DeliveryGateTest {
    @Test fun quietHoursDeferNormalReminderButNotLastDay() { /* 23:30 IST */ }
    @Test fun fourthPushOfTheIstDayGoesToDigest() { }
    @Test fun reminderSuppressedWhenAppliedIsPendingLocally() { }
}

class ReminderWindowPlanTest {                                 // commonTest (planner) + iosTest (diff against a fake UNUserNotificationCenter)
    @Test fun keepsEveryRLastAndR1Within21DaysFirst() { }
    @Test fun neverExceeds60AndAddsOpenTheAppSentinelWhenCut() { }
    @Test fun appliedRemovesAllPendingRequestsOfThatMatch() { }
}
```

## 4. CI (GitHub Actions)

Two jobs per PR: a fast Linux job (everything except Apple targets) and a macOS job for iOS. macOS runner minutes cost
~10× Linux, so the macOS job is skipped for docs-only changes and caches `~/.konan`, Gradle and SPM.

```yaml
# .github/workflows/mobile.yml
name: mobile
on:
  pull_request: { paths: ['mobile/**', 'docs/api/openapi.yaml', 'docs/design/tokens.json', '.github/workflows/mobile.yml'] }
  push: { branches: [main] }
  schedule: [{ cron: '17 21 * * *' }]           # nightly device/simulator smoke
concurrency: { group: mobile-${{ github.ref }}, cancel-in-progress: true }
defaults: { run: { working-directory: mobile } }
jobs:
  android:
    runs-on: ubuntu-latest
    timeout-minutes: 35
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew detekt lint                              # incl. ForbiddenImport package-boundary rules
      - run: ./gradlew jvmTest testAndroidHostTest              # commonTest on the JVM/Android host + Robolectric
      - run: ./gradlew verifyRoborazziDebug verifyTranslations verifyFontBudget
      - run: ../scripts/check-dto-contract.sh ../docs/api/openapi.yaml
      - run: ./gradlew :androidApp:bundleProdRelease -Pci       # R8 on
      - run: ../scripts/check-size.sh androidApp/build/outputs/bundle/prodRelease/androidApp-prod-release.aab 10
  ios:
    runs-on: macos-15                                           # Xcode version pinned with xcode-select (latest stable)
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - uses: actions/cache@v4
        with: { path: ~/.konan, key: konan-${{ hashFiles('mobile/gradle/libs.versions.toml') }} }
      - run: ./gradlew iosSimulatorArm64Test                    # commonTest + iosTest on the simulator
      - run: ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
      - run: xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 16' -testPlan PR
      - if: github.ref == 'refs/heads/main'
        run: ../scripts/ios-archive-size.sh 25                  # archive + export (no upload), App-Thinned arm64 size budget in MB
  nightly-devices:
    if: github.event_name == 'schedule'
    strategy: { matrix: { os: [ubuntu-latest, macos-15] } }
    runs-on: ${{ matrix.os }}
    steps:
      - run: ./gradlew pixel2Api26DebugAndroidTest pixel6Api35DebugAndroidTest   # ubuntu (KVM)
      - run: xcodebuild test -scheme iosApp -testPlan Nightly -destination 'platform=iOS Simulator,name=iPhone SE (3rd generation),OS=16.4'   # macOS
```

- Version catalog + Renovate (weekly, grouped: Kotlin/KSP/Compose Multiplatform together; AndroidX; Koin; Ktor;
  Firebase BoM). SPM packages are bumped by Renovate's `swift` manager in the same weekly PR.
- Branch protection: `android` and `ios` must pass; screenshots updated only via `recordRoborazziDebug` in the PR.

## 5. Release

| | Android | iOS |
| --- | --- | --- |
| Build | tag → `:androidApp:bundleProdRelease` signed with the upload key from secrets | tag → `xcodebuild archive` (Release, automatic signing with an **App Store Connect API key** from secrets: `-allowProvisioningUpdates -authenticationKeyPath …`) → `-exportArchive` with `method = app-store-connect`, `destination = upload` |
| Symbols | R8 mapping + Crashlytics upload | dSYMs (app + `ComposeApp` framework) → Crashlytics |
| First track | Play **internal** → closed testing (validation) → production | **TestFlight internal** (no review) → **external** group (Beta App Review, Hindi-speaking testers) → App Store review |
| Rollout | staged rollout (manual, Play Console) | **phased release** (7 days) in App Store Connect; pause on crash spikes |
| Store metadata | Hindi ASO listing, Data safety form | Hindi + English listing (subtitle without "official"/"सरकारी ऐप"), App Privacy labels, age rating questionnaire, IAP products in review with the first build, review notes with a test profile and the "not a government app" statement (platform-interop.md §4) |
| Purchases in pre-release | licence testers (free), closed test = real purchases | sandbox only (TestFlight purchases are free) |
| Force update lever | `config.min_supported_version.android` + Play In-App Updates | `config.min_supported_version.ios` + App Store link |

Release checklist (both): version bump in one place (`gradle.properties` → Android `versionCode` + iOS
`CFBundleShortVersionString`/`CFBundleVersion` via the xcconfig), changelog in hi/en, `config.latest_version` updated
after the store approves.
