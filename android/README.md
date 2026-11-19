# Sarkari Match — Android app

Hindi-first, alert-only government-job app. Architecture and decisions live in [`../docs/frontend/`](../docs/frontend/README.md);
versions and module layout in [`project-skeleton.md`](../docs/frontend/project-skeleton.md). Visual style: Direction C
"Marigold Home" ([`../docs/design/02-design-system.md`](../docs/design/02-design-system.md), `tokens.json` v2.0).

Matching runs **on the phone** (`:core:eligibility`); the profile never leaves the device
([`matching-engine.md`](../docs/backend/matching-engine.md) §0).

## Setup

- JDK 17+ (CI uses Temurin 17), Android Studio with Android SDK platform 37 (compileSdk 37, targetSdk 36) and build-tools 36.
- `local.properties` with `sdk.dir=/path/to/Android/sdk` (or `ANDROID_HOME`). Not committed.
- Gradle comes from the wrapper (`./gradlew`, Gradle 9.9.1).

```bash
./gradlew assembleProdDebug          # debug APK, prod API base URL
./gradlew assembleStagingDebug       # staging API
./gradlew testProdDebugUnitTest testDebugUnitTest :core:eligibility:test
./gradlew detekt
```

### Without the Android SDK

`:core:model` and `:core:eligibility` are plain Kotlin/JVM modules. When no SDK is found (or with
`-Psm.jvmOnly=true`) `settings.gradle.kts` includes only those two, so the evaluator builds and tests anywhere,
including environments where Google Maven (`dl.google.com`) is unreachable:

```bash
./gradlew -Psm.jvmOnly=true :core:model:test :core:eligibility:test detekt
```

## Modules

| Module | Kind | Contents |
| --- | --- | --- |
| `:app` | application | `SmApplication` (Hilt), `MainActivity` (edge-to-edge, splash, per-app language, Hindi on first run), `SmApp` (3-tab scaffold), `SmNavHost` (type-safe Navigation Compose), `StartViewModel` (S01), BuildConfig → network bindings |
| `:core:model` | JVM | `AppLanguage` (13 languages, hi + en launched), `LocalizedText`, profile enums, `Verdict`, `DeadlineTone`, IST + `SmFormat` (Western digits, Indian grouping), nav-arg enums, `MatchCardUi` |
| `:core:eligibility` | JVM | Rule model of `rule.schema.json` v1 + `EligibilityEvaluator` (age on cutoff with relaxations / ESM deduction / category-unsure, qualification, domicile, gender, category, PwD, ESM, marital, advisories, fee). Tests use `docs/backend/examples/*.rule.json` (copied to `src/test/resources/rules`) |
| `:core:designsystem` | Android | Marigold theme (light + dark `ColorScheme`, `SarkariColors` = tokens `ext.*`), Baloo 2 + Hind via Downloadable Google Fonts with system fallback, type scale 28/19/16/14, shapes, spacing, sizes; `SmButton`, chips, ribbon, category tile, `MatchCard`, `VerdictChip`, nav bar with marigold pill, `LanguageOptionCard`, `PlaceholderScreen`; Material Symbols Rounded as vector drawables; `TokensParityTest` |
| `:core:network` | Android | `SmApi` (Retrofit 3 + OkHttp 5 + kotlinx.serialization) stub; base URL from `:app` BuildConfig |
| `:core:database` | Android | Room `SmDatabase` v1 stub (profile, catalog posts, matches), schemas exported to `core/database/schemas` |
| `:core:data` | Android | DataStore prefs, `LanguageController` (AppCompat per-app locales), `MatchEngine` (adapter to the evaluator), `SampleMatchRepository` |
| `:core:notifications` | Android | The 7 notification channels (`critical`, `new_match`, `reminders`, `job_updates`, `digest`, `account`, `family`) in hi/en |
| `:core:billing`, `:core:ads` | Android | Interfaces/stubs (Play Billing FE-24, native ads FE-27) |
| `:core:testing` | Android | `MainDispatcherRule`, fakes |
| `:feature:onboarding` | feature | **S02 language picker (real)**, S03–S10, S27, S40, S47 |
| `:feature:jobs` | feature | **S20 Home feed (real, sample data)**, S21, S24, S25, S44 |
| `:feature:alerts` | feature | S13–S16 (setup, `mode` arg), S28, S29 |
| `:feature:applications` | feature | S22, S23, S37 |
| `:feature:pro` | feature | S30–S32 |
| `:feature:profile` | feature | S26, S34–S36, S38, S39, S43, S45, S46, S48, S49 |

Unbuilt screens render `PlaceholderScreen` with their inventory ID (also the test tag). Features never depend on each
other; `:app` wires cross-feature navigation through callbacks.

## Build logic

`build-logic/` (included build) has two projects:

- `:jvm` — `sm.jvm.library` + detekt helper; no Android dependencies.
- `:convention` — `sm.android.application`, `sm.android.library`, `sm.android.compose`, `sm.android.feature`,
  `sm.android.room`, `sm.hilt`.

Plugins are `compileOnly` there and put once on the root buildscript classpath (`build.gradle.kts`), so every module
shares one copy of KGP/AGP. AGP 9 compiles Kotlin itself (no `kotlin-android` plugin).

## Conventions

- Code packages: `com.sarkarimatch.*`; `applicationId` = `in.sarkarimatch.app` (placeholder domain).
- `values/` = English (fallback), `values-hi/` = Hindi. Hindi is applied on first run; `locales_config.xml` and
  `sm.enabledLocales` (gradle.properties) list only enabled languages.
- No hex colours, raw `.sp` or ad-hoc fonts outside `:core:designsystem`.
- Style: `.editorconfig` (ktlint, android_studio style) + `config/detekt/detekt.yml`.

## Not done yet

`:baselineprofile`, Firebase (FCM, Auth, App Check, Crashlytics), Play Billing, ads SDK, catalog sync, Roborazzi
screenshots, signing. See the phased tickets in `project-skeleton.md` §3.
