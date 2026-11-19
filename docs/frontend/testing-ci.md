# Testing and CI

Goal for a solo developer: catch the expensive bugs (wrong on-device verdicts, lost "Applied" taps, missed reminders, broken layouts in any language incl. RTL, billing state, data leaving the device) with fast JVM tests, and keep device tests to a thin smoke layer.

## 1. Test pyramid

| Layer | Tools | What | Where it runs |
| --- | --- | --- | --- |
| Pure unit (JVM) | JUnit 4, kotlinx-coroutines-test, **Turbine**, Truth/kotlin.test, fakes (MockK only for Android framework edges) | ViewModels (state sequences), mappers/formatters (IST dates, ₹, "2 दिन बाकी", deadline tone, section bucketing, `RuleLine` rendering from `RuleExplanation`), `DeliveryGate` (quiet hours, cap, last-day exception, hold-until-reminder-time), `:matching-core` **golden tests shared with the backend** (SSC GD 2026, UP Police 2025 v1/v2, boundary generators) run on the JVM, `:reminder-core` slot tests, `CatalogSync` ordering/sha256/evaluator-version gate, backup merge (per-row LWW, applied never downgraded), `AdsGate` (incl. minor mode), `PaywallMomentController`, OEM intent table | every PR, < 2 min |
| Data/integration (JVM + Robolectric) | Room in-memory (Robolectric), **MockWebServer** (OkHttp 5), WorkManager `TestListenableWorkerBuilder` / `WorkManagerTestInitHelper` | DAO queries, snapshot streaming + batch apply, corrupt batch skipped, `Idempotency-Key` reuse on retry, 401 refresh once, 426 → force update, ETag 304 handling, **privacy guard**: a MockWebServer test asserts no request body or header ever contains profile fields (dob, category, pwd, qualifications) and that reports/telemetry/push-tests carry no installation id or ID token | every PR |
| Room migrations | `MigrationTestHelper` against exported schemas | every schema version N-1 → N, and 1 → latest; **profile, matches and action log must survive** (they exist nowhere else unless backup is on) | every PR touching `:core:database` |
| Compose UI (JVM) | Compose UI test on Robolectric (`createComposeRule`), `enableAccessibilityChecks()`, `StateRestorationTester` | each screen's stateless `XScreen(state)` for every UiState variant; custom accessibility actions on `MatchCard`; sheets B01–B07 flows | every PR |
| Screenshot | **Roborazzi** (Robolectric native graphics) | every designsystem component + every screen state × {light, dark} × {hi, en} × {font 1.0, 2.0}; fallback font vs Baloo 2/Hind; key screens (S02, S10, S20, S21, S30, S47, notifications) also in pseudo-locales **`en-XA`** (+30 % length) and **`ar-XB`** (RTL) and, once strings exist, `ta` (longest) and `ur` (Nastaliq, RTL) | every PR; diffs uploaded as artifact |
| Contract | JSON fixtures from `openapi.yaml` examples + a script that diffs DTO `@SerialName`s against schema properties | DTOs never drift from the API | every PR |
| Instrumented smoke | Gradle Managed Device (API 26 and API 34 ATD images), UiAutomator | onboarding → Home with a fixture catalog; notification action *भर दिया* from the shade while app killed; topic push → held until reminder time; on-device backup fires when no push arrives; POST_NOTIFICATIONS + exact-alarm flow; "don't keep activities" restore; deep link with synthesised back stack | nightly + release branch |
| Macrobenchmark / Baseline Profile | `:baselineprofile` (Macrobenchmark 1.5, UiAutomator 2.4) | cold start, feed scroll, open detail, **evaluate 300 posts < 50 ms, apply 1 MB snapshot < 1.5 s** (backend operations.md §8); generates the baseline + startup profile | weekly + release |
| Manual device matrix | Redmi (MIUI/HyperOS), Vivo (Funtouch/OriginOS), Oppo/Realme (ColorOS), Samsung (One UI) | FCM topic delivery when killed, OEM autostart intents, on-device backup alarms after reboot, self-test, Google sign-in + backup restore on a second phone | each release (checklist in repo) |

## 2. Fakes (`:core:testing`)

`FakeSmApi` (in-memory server implementing the operations used, incl. a fixture catalog head/snapshot/batches, with switchable failures/latency), `FakeMatchRepository`, `FakeCredentialManager`, `FakeBlockStore`, `FakeEntitlementRepository`, `FakeBillingRepository`, `FakeNativeAdLoader`, `FakeConnectivity`, `TestIstClock` (settable `Instant`), `FakeNotifier` (records posted notifications), `TestDispatchers` rule, `fixtures/` (JSON copied from OpenAPI examples). The **fake backend** also runs as a debug-only `MockWebServer` dispatcher (`-Pbackend=fake`) so UI work can proceed before the real backend exists — essential while the backend is built in parallel.

## 3. Examples

```kotlin
class HomeViewModelTest {
    @get:Rule val dispatchers = MainDispatcherRule()
    private val clock = TestIstClock("2026-11-30T09:00+05:30")
    private val matches = FakeMatchRepository()
    private val actions = FakeMatchActionRepository()

    @Test fun `last-day match goes into Today section and Applied removes it optimistically`() = runTest {
        matches.emit(match(id = "m1", lastDate = "2026-11-30"), match(id = "m2", lastDate = "2026-12-20"))
        val vm = homeVm()
        vm.state.test {
            val content = awaitItem().feedContent()
            assertThat(content.sectionOf("m1")).isEqualTo(SectionKind.LastDayToday)
            vm.onAction(HomeAction.ConfirmApplied("m1", feePaid = false))
            assertThat(actions.recorded.single()).isEqualTo("m1" to UserAction.Applied(false))
            assertThat(awaitItem().feedContent().ids()).containsExactly("m2")
        }
    }
}

class RuleLineFormattingTest {
    @Test fun `age rule renders server text and pass glyph`() {
        val ui = RuleLineMapper.map(ruleExplanation(rule = "age", result = "pass",
            hi = "उम्र 24 साल (01-08-2026 को), सीमा 27, OBC +3"), AppLanguage.HI)
        assertThat(ui.glyph).isEqualTo(Glyph.Pass)            // ✓ + check_circle + success colour
        assertThat(ui.text).isEqualTo("उम्र 24 साल (01-08-2026 को), सीमा 27, OBC +3")
    }
    @Test fun `missing english falls back to hindi`() { /* LocalizedText.pick */ }
}

class DeliveryGateTest {
    @Test fun `quiet hours defer normal reminder but not last-day`() { /* 23:30 IST */ }
    @Test fun `fourth push of the IST day goes to digest`() { }
    @Test fun `reminder suppressed when Applied is still pending locally`() { }
}
```

## 4. CI (GitHub Actions)

```yaml
# .github/workflows/android.yml
name: android
on:
  pull_request:
  push: { branches: [main] }
concurrency: { group: android-${{ github.ref }}, cancel-in-progress: true }
jobs:
  check:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4      # build cache + configuration cache
      - run: ./gradlew spotlessCheck detekt lintDebug
      - run: ./gradlew testDebugUnitTest          # JVM unit + Robolectric + Compose UI + migrations
      - run: ./gradlew verifyRoborazziDebug       # screenshot diffs
      - run: ./scripts/check-dto-contract.sh docs/api/openapi.yaml
      - run: ./gradlew :app:bundleRelease -Pci     # R8 on (signed with upload key from secrets on main only)
      - run: ./scripts/check-size.sh app/build/outputs/bundle/release/app-release.aab 10   # bundletool get-size, MB budget
      - uses: actions/upload-artifact@v4
        if: failure()
        with: { name: reports, path: '**/build/reports/**' }
  nightly-device:
    if: github.event_name == 'schedule'
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew pixel2Api26DebugAndroidTest pixel6Api34DebugAndroidTest   # Gradle Managed Devices (ATD), KVM enabled
```

- Version catalog + Renovate (weekly, grouped: AndroidX, Firebase BoM, Kotlin/KSP/Compose compiler together).
- Release: tag → GitHub Action builds AAB → uploads to Play **internal** track via the Play Developer API (Gradle Play Publisher or `r0adkll/upload-google-play`); staged rollout manually from Play Console. Crashlytics mapping upload in the same job.
- Branch protection: `check` must pass; screenshots updated only via `recordRoborazziDebug` in the PR.
