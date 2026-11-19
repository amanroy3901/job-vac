# i18n and the design system in code

Sources: `docs/design/02-design-system.md`, `docs/design/07-localization.md` (languages, scripts, expansion, RTL), `docs/design/tokens.json` (W3C DTCG; `font.script.*`, `layout.i18n`), component catalogue `docs/design/06-components.md` (when published; until then the "Key components" column of `01-screen-inventory.md`).

Both platforms render the **same Compose Multiplatform UI** from `commonMain`, so this file is shared; platform
differences are called out per row (founder decision 9: same Marigold design on Android and iOS).

## Part A — Internationalisation

### A1. Languages and resources (13 languages, launch hi + en)

Founder decision 1 (2026-11-19): the architecture supports all major Indian languages; Hindi + English ship first.

| Item | Decision |
| --- | --- |
| Languages | `hi` (default for every new user), `en` enabled at launch. Ready in code and resources: `bn`, `mr`, `te`, `ta`, `gu`, `kn`, `or`, `ml`, `pa`, `as`, `ur` (RTL). Which ones are selectable comes from `ReferenceBundle.languages[].status` (`enabled` / `coming_soon`) **∩** the locales compiled into this build; S02/S40 list enabled ones and a non-selectable "जल्द आ रही हैं" list (design 07). |
| Resource folders | **Compose Multiplatform resources** (`src/commonMain/composeResources/`), one set for both platforms: `values/strings.xml` = **English** (fallback), `values-hi/`, and one folder per language as it is translated: `values-bn/`, `values-mr/`, `values-te/`, `values-ta/`, `values-gu/`, `values-kn/`, `values-or/`, `values-ml/`, `values-pa/`, `values-as/`, `values-ur/`. Same XML format as Android (`<string>`, `<plurals>`, `<string-array>`), accessed through the generated `Res.string.*` with `stringResource()` in composables and the suspend `getString()` outside composition (ViewModels never resolve strings — they pass `UiText`). Strings live per feature module (each module has its own `Res`); no concatenation, only positional placeholders (`%1$s`) because word order differs (SOV vs SVO, Urdu RTL). Exam names (SSC CGL, RRB NTPC, DV) are never translated. Android `res/values*/strings.xml` remains only for what the OS reads without our code: app name, notification channel names are set from code, so in practice `app_name` and `locales_config.xml` in `:androidApp` |
| Translation workflow | Source of truth = `composeResources/values/` (en) + `values-hi/` (authored). Other languages: export XLIFF from the string resources → TMS (tool open: Crowdin/Weblate/Tolgee) with LLM draft + native reviewer → import. A language is shipped only when 100 % of strings are reviewed; a Gradle check `verifyTranslations` (compose resources are not covered by Android's `MissingTranslation` lint) fails for any key missing in an enabled locale (non-enabled folders may be partial in debug). |
| Build packaging | One Gradle property `sm.enabledLocales=en,hi` drives everything: release builds **exclude non-enabled `values-xx` folders** from `composeResources` (compose resources are packaged as assets on Android and bundle files on iOS, so `localeFilters` and App Bundle language splits do not apply to them — a few KB per language anyway); Android `localeFilters` still strips library translations and `locales_config.xml` lists only enabled locales; iOS `CFBundleLocalizations` is generated with the same list. Enabling a language = change the property + flip its server status. |
| Per-app language — Android | `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))`; `android:localeConfig` so Android 13+ shows the app in *Settings → App languages*; for API 26–32 `AppLocalesMetadataHolderService` with `autoStoreLocales=true`. `MainActivity` extends `AppCompatActivity`. Compose resources resolve against the Activity `Configuration`, so they follow automatically. |
| Per-app language — iOS | iOS shows *Settings → Sarkari Match → Language* only when the bundle declares more than one localization: `CFBundleLocalizations = [en, hi]` (generated from `sm.enabledLocales`), `CFBundleDevelopmentRegion = en`, and a real `hi.lproj` (`InfoPlist.strings`, plus the generated notification `Localizable.xcstrings`). The iOS per-app choice is stored in the app's `AppleLanguages` default, which is also what compose resources read (`NSLocale.preferredLanguages`) and what APNs `loc-key` alerts use. In-app S02/S40 write `AppleLanguages = [code]` **and** provide an immediate override (`LocalAppLocale` composition local + `key(locale) { App() }` recomposition, the pattern from the CMP resources guide) so the switch is instant without relaunch. |
| First run | S02 preselects Hindi (or the device language if it is enabled), on both platforms. |
| Change at runtime (S40) | Applies instantly (Android: activity recreated; iOS: root recomposed with the new locale). `LanguageController` (common, platform `LocalePort`) mirrors the code into DataStore for background jobs and notification composition (no UI there), re-creates Android channels with the same ids / re-registers iOS notification categories (action titles) and **re-plans the iOS pending window** (scheduled texts are already composed), triggers a catalog snapshot fetch for the new locale set, and (accounts only) `upsertDevice(locale)` so server-composed account pushes follow. Background text uses `getString(…)` with a resource environment for the stored app language, never the device language (verify the public `ResourceEnvironment` API in the pinned CMP version; fallback: set the process default locale before composing). |
| Server content | `LocalizedText` = `Map<LanguageCode, String>`. `pick(lang)` walks `[lang] + languages[lang].fallback + en` (e.g. mr → hi → en; ta → en → hi; ur → en → hi). The catalog is fetched with `?locales=<that chain>`, so a language switch works offline only for languages already fetched. |
| Notifications | All push/backup text is built on the device from string resources in the app language (design 05: titles ≤ 45, actions ≤ 15 characters per language); server sends ids only. |
| Plurals and numbers | `<plurals>` per language in compose resources (CLDR plural rules are built into the resources library on both platforms; Hindi `one`/`other`). **Western digits everywhere** (design 07): our own formatter (A2), so no platform locale can switch to Devanagari digits. |
| Pseudo-locales | Compose resources have no built-in pseudo-locales, so a Gradle task generates `values-en-rXA/` (accented, +30 % length) and `values-ar-rXB/` (mirrored, RTL) from `values/` into a debug/test-only resource dir. Both run in screenshot tests on every PR (testing-ci.md), plus the longest real locale (`ta`) and `ur` once their folders exist. |

### A1a. RTL (Urdu) readiness

- Android `android:supportsRtl="true"`; iOS needs nothing (Compose Multiplatform sets `LocalLayoutDirection` from the locale on both, including our in-app `LocalAppLocale` override). Only `start`/`end` paddings and alignments (a detekt rule bans `Arrangement.Left/Right`, `Alignment.Left`, `absolutePadding` in shared UI); `Arrangement.Start`, never `Left`. On iOS the swipe-back gesture comes from the right edge in RTL.
- Directional icons use `ImageVector` with `autoMirror = true` (back arrow, chevrons, "open" arrows); non-directional ones (check, bell, Google "G") never mirror (design 06 L4 `MirrorableIcon`).
- Mixed text: dates, ₹ amounts, exam codes ("SSC CGL 2026") and URLs inside Urdu text are wrapped in Unicode isolates (U+2066 LRI … U+2069 PDI) by a common `Bidi.isolateLtr()` helper (Android's `BidiFormatter` is not available in common code) so they keep LTR order (design 06 L3 `localizedAnnotatedString`). The same isolates go into iOS local-notification text.
- Number keyboards, progress bars and swipe actions mirror; timelines (S23) run right-to-left.
- Screenshot tests in `ar-XB` catch any hard-coded left/right before Urdu strings exist.

### A2. Dates, numbers, money — always IST

`kotlinx-datetime` in common code (`java.time` does not exist on iOS). Month names, "सुबह/शाम", Indian digit grouping
and ₹ formatting are **our own common formatters backed by compose-resource string arrays**, not platform locale
data: Android (ICU) and iOS (Foundation/CLDR) differ in month abbreviations and in whether Hindi uses Devanagari
digits, and one implementation gives identical text in the app, in notifications and in tests on both platforms.

```kotlin
object Ist { val ZONE: TimeZone = TimeZone.of("Asia/Kolkata") }

/** All "today", countdowns and deadline tones use the IST calendar date, never device zone or UTC. */
class IstClock(private val clock: Clock) {
    fun today(): LocalDate = clock.now().toLocalDateTime(Ist.ZONE).date
    /** Emits at every IST midnight so feed sections re-bucket while the app is open. */
    fun istDateTicks(): Flow<LocalDate> = flow { while (true) { emit(today()); delay(untilNextIstMidnight()) } }
}

object SmFormat {                                                     // commonMain
    fun longDate(d: LocalDate, months: List<String>): String =         // "24 अगस्त 2026" / "24 Aug 2026"; months from Res.array.months_long
        "${d.dayOfMonth} ${months[d.monthNumber - 1]} ${d.year}"
    fun chipDate(d: LocalDate): String = "${d.dayOfMonth.pad2()}/${d.monthNumber.pad2()}"
    fun rupees(paise: Long): String = "₹" + indianGrouping(paise / 100)   // "₹100", "₹1,00,000"; Western digits, no decimals
    fun daysLeft(today: LocalDate, last: LocalDate): Int = today.daysUntil(last)   // 0 = today
}

enum class DeadlineTone { Today, Urgent, Soon, Calm, Closed }  // tokens.json semantic.deadline.*
fun deadlineTone(daysLeft: Int) = when {
    daysLeft < 0 -> DeadlineTone.Closed; daysLeft == 0 -> DeadlineTone.Today
    daysLeft <= 3 -> DeadlineTone.Urgent; daysLeft <= 7 -> DeadlineTone.Soon; else -> DeadlineTone.Calm
}
```

- API calendar dates (`last_date`, `dob`, key dates) are parsed directly as `LocalDate` (IST) and **never** converted
  through UTC (API README §8). Instants are parsed as `Instant` and displayed in IST.
- Age display ("आपकी उम्र आज: 23 साल 4 महीने") uses `dob.periodUntil(todayIst)`. Eligibility is computed **on the
  device** by `:matching-core`; its `RuleExplanation` lines are rendered from per-language string templates with
  computed `params` (dates and numbers formatted by `SmFormat`).
- Reminder slot times (19:00, 08:00 IST) become `UNCalendarNotificationTrigger` `DateComponents` with
  `timeZone = Asia/Kolkata` on iOS and `AlarmManager` epoch millis on Android, both from the same common `Instant`.

### A3. Fonts per script and text rendering

The family chain per script comes from `tokens.json` `font.script.*` (design 07):

| Script (locales) | Family chain | Line-height × |
| --- | --- | --- |
| Latin (en), Devanagari (hi, mr) | Baloo 2 (headings) + Hind (body) → Noto Sans Devanagari / Roboto | 1.0 |
| Bengali (bn, as) | Noto Sans Bengali | 1.05 |
| Telugu (te) | Noto Sans Telugu | 1.15 |
| Tamil (ta) | Noto Sans Tamil | 1.05 |
| Gujarati (gu) | Noto Sans Gujarati | 1.0 |
| Kannada (kn) | Noto Sans Kannada | 1.15 |
| Odia (or) | Noto Sans Oriya | 1.15 |
| Malayalam (ml) | Noto Sans Malayalam | 1.1 |
| Gurmukhi (pa) | Noto Sans Gurmukhi | 1.0 |
| Arabic/Urdu (ur, RTL) | Noto Nastaliq Urdu → Noto Naskh Arabic | 1.6 |

**Delivery (changed for iOS).** Downloadable Google Fonts exist only on Android (Play services provider); iOS has no
equivalent. Decision:

| Scripts | Android | iOS | Why |
| --- | --- | --- | --- |
| **Latin + Devanagari (en, hi, mr)** — Baloo 2 600 + Hind 400/600 | **bundled** compose resources (`composeResources/font/`), subset | same files | launch languages must look identical and render the first frame in the brand font (no swap flash, no GMS dependency for Huawei/AOSP phones) |
| Bengali, Telugu, Tamil, Gujarati, Kannada, Odia, Malayalam, Gurmukhi | Downloadable Fonts for the Baloo/Hind sister or Noto family of the **active** language only (`androidMain` `expect fun scriptFontFamily(script)`), system Noto until downloaded | **system fonts** by fallback (iOS ships Kohinoor Bangla/Telugu, Tamil/Gujarati/Kannada/Oriya/Malayalam Sangam MN, Gurmukhi MN); optional P1: download the Baloo/Hind sister family from our CDN (`/fonts/{family}-{sha}.ttf`, sha256-checked, cached in Application Support) and load with `Font(identity, bytes)` | 0 KB per future language; iOS system Indic fonts are good quality; brand sisters arrive when a language is enabled |
| Urdu (Nastaliq) | Downloadable Noto Nastaliq Urdu, Naskh fallback | **system Noto Nastaliq Urdu** (ships with iOS) | — |

**Size budget for bundled fonts: ≤ 450 KB** total (uncompressed TTF), checked in CI. Subsetting (`pyftsubset`,
scripted in `tools/fonts/`) keeps Basic Latin, Latin-1 punctuation, ₹ (U+20B9), Devanagari (U+0900–097F), Devanagari
Extended, Vedic marks used in Hindi/Marathi, ZWJ/ZWNJ, U+2066–2069 isolates, and the OpenType features shaping needs
(`akhn, blwf, half, pres, abvs, blws, psts, haln, rphf, nukt, locl, kern, mark, mkmk`, plus `tnum`). Expected ≈ 150 KB
Baloo 2 SemiBold + 2 × ≈ 120 KB Hind = ≈ 390 KB. A shaping regression test renders a fixed list of conjuncts
(क्ष, त्र, ज्ञ, श्र, द्ध, र्‍) on both platforms in the screenshot suites. Android pays ≈ 0.3 MB of download for
this (within the 8/10 MB budget); in exchange the Android-only `ui-text-google-fonts` path is needed only for future
languages.

- Text shaping: Android uses the platform text stack (HarfBuzz/Minikin); iOS CMP uses Skia + HarfBuzz with CoreText
  font fallback — both shape Indic scripts correctly with the bundled OpenType fonts; iOS falls back per glyph to system
  fonts for scripts not in our files.
- Fallback: until a non-launch script font is available, the system font (Android 8+ Noto; iOS Kohinoor/Sangam/
  Nastaliq). Layouts are screenshot-tested with fallback and real fonts.
- `SarkariTheme(locale)` (design 06 L2) builds the `Typography` with the script's family and multiplies every token line height by the script's `lineHeightMultiplier`; sizes stay the same.
- Every `TextStyle`: `LineHeightStyle(Alignment.Center, Trim.None)`, `letterSpacing = 0.sp` (font padding is already off by default in current Compose on Android and does not exist on iOS; `PlatformTextStyle(includeFontPadding)` is Android-only API and is not used in common code); no fixed text container heights; no ALL CAPS; wrap, don't truncate (titles ≤ 3 lines, buttons ≤ 2, chips 1 with tooltip; button rows stack at fontScale ≥ 1.5 or when any label needs 2 lines — `layout.i18n`).
- Expansion budgets vs English (`layout.i18n.expansionVsEnglish`): up to +60 % for ta/ml, +40 % kn, +35 % te. `en-XA` (+30 %) and `ta` must pass the screenshot suite before a language is enabled.
- Notification text uses the system font on both platforms (we can't control it) — another reason for the per-language length limits.

### A4. Font scale and Dynamic Type

- Android: `fontScale` from system settings up to 200 % (Android 14 non-linear scaling), all text in `sp`.
- iOS: **Dynamic Type** (Settings → Display & Brightness → Text Size, and Accessibility → Larger Text). The shared UI
  reads only `LocalDensity.current.fontScale`. `iosMain` provides it from
  `UIApplication.preferredContentSizeCategory` (xS 0.82, S 0.88, M 0.94, **L 1.0** default, XL 1.12, XXL 1.24,
  XXXL 1.35, AX1 1.65, AX2 1.94, AX3–AX5 capped at **2.0**, the same ceiling our layouts are tested for on Android) and
  re-provides it on `UIContentSizeCategory.didChangeNotification` — unless the pinned Compose Multiplatform version
  already maps Dynamic Type to `fontScale` itself (verify; then we only apply the 2.0 cap). The design's caps
  (bottom-nav labels 1.5×, wordmark unscaled) apply identically.
- Bold Text (iOS) / bold text (Android 12+ `fontWeightAdjustment`): mapped to +100 weight where the family has it
  (Hind 400 → 600); Baloo 2 is already 600.

## Part B — Design tokens → Compose theme

### B1. Mapping

| tokens.json | Compose | File (`:core:designsystem/theme`) |
| --- | --- | --- |
| `color.light/dark.*` (M3 roles) | `lightColorScheme(...)` / `darkColorScheme(...)` | `Color.kt` |
| `color.*.ext.*` (success, warning, urgent, today) | `@Immutable data class SarkariColors` via `staticCompositionLocalOf` | `SarkariColors.kt` |
| `semantic.*` (verdict, deadline, stage, pro) | `VerdictStyle`, `DeadlineStyle`, `StageStyle` lookups returning container/content/icon | `Semantic.kt` |
| `font.family` + `font.scale` | `Typography` (12 M3 roles) with Baloo 2 + Hind (Direction C, Devanagari-safe) | `Type.kt` |
| `space.*` | `@Immutable data class Spacing(xxs=2.dp … sectionGap=24.dp)` via `LocalSpacing` | `Spacing.kt` |
| `radius.*` | M3 `Shapes(extraSmall=4, small=8, medium=12, large=16, extraLarge=28)`; buttons keep pill | `Shape.kt` |
| `size.*` | `object SmSize { touchMin=48.dp; buttonHeight=52.dp; bottomActionBar=84.dp … }` | `Size.kt` |
| `elevation.*` | `object SmElevation` (cards 0 + 1 dp outlineVariant border) | `Elevation.kt` |
| `motion.*` | `object SmMotion { durations; easings as CubicBezierEasing; fun duration(ms) = if (reducedMotion) 0 else ms }` | `Motion.kt` |
| `breakpoint.*` | `WindowSizeClass` (material3-window-size-class, multiplatform) → content `widthIn(max = 600.dp)` on medium/expanded (tablets, iPad in compatibility) | `Layout.kt` |

All theme files live in `:core:designsystem/src/commonMain/.../theme/`. Dynamic colour is **off** (design §2.6) — it
does not exist on iOS anyway, which keeps both platforms on the Marigold palette. Theme mode: System / Light / Dark from
DataStore; `isSystemInDarkTheme()` works on both. Status bar / home indicator: Android edge-to-edge
(`enableEdgeToEdge`), iOS safe areas via `WindowInsets.safeDrawing` in common code — one inset model for both.

```kotlin
@Composable
fun SmTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val reducedMotion = rememberReducedMotion()          // expect: Android ANIMATOR_DURATION_SCALE == 0 / "remove animations"; iOS UIAccessibility.isReduceMotionEnabled
    CompositionLocalProvider(
        LocalSarkariColors provides if (darkTheme) DarkSarkari else LightSarkari,
        LocalSpacing provides Spacing(),
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object SmTheme {
    val colors: SarkariColors @Composable @ReadOnlyComposable get() = LocalSarkariColors.current
    val spacing: Spacing @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val reducedMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}
```

**Keeping code and tokens in sync:** `Color.kt`/`Type.kt` are written by hand once (they are small), and a `commonTest` test `TokensParityTest` (run on the JVM/Android host in CI; reads the file via a Gradle-provided path) parses `docs/design/tokens.json` and asserts every colour role, type size/line height/weight, spacing and radius equals the Kotlin value. A token change in design fails CI until the theme is updated — cheaper than a code generator, and it catches drift both ways.

Lint guard: a custom detekt rule (or a simple grep in CI) forbids `Color(0x…)`, raw `.sp` and `.dp` literals in `feature.*` packages sources (design system only).

### B2. Component catalogue (`:core:designsystem`)

Two layers: `component/` (generic, model-free — previews and screenshot tests are trivial) and `domain/` (knows `:core:model` UI types such as `MatchCardUi`). All in `commonMain`. Every component is stateless (state + lambdas), has `@Preview`s (Compose Multiplatform `org.jetbrains.compose.ui.tooling.preview`, rendered in Android Studio/Fleet) at font scale 1.0/1.3/2.0 in light/dark, a Roborazzi screenshot, and a `testTag`.

| Component | Layer | Used by (inventory) | Notes |
| --- | --- | --- | --- |
| `SmButton` (Primary/Secondary/Text/Destructive), `BottomActionBar` | component | all | 52 dp, pill, icon + label, wraps to 2 lines; stacks vertically at fontScale ≥ 1.5 |
| `SmTopAppBar` | component | all | 64 dp; title `titleLarge`; `heading()` semantics |
| `SmBottomNav` | component | S20/S22/S26 | 3 items, badge "9+" on tab 1 |
| `StatusBanner` (Offline/Warning/Outage/Info) | component | O01–O03, S20–S23 | polite live region |
| `SectionHeader` | component | S20, S22 | `heading()` |
| `EmptyState`, `SkeletonCard` | component | all lists | static skeleton when reduced motion |
| `SelectableTile`, `RadioListItem`, `ChipGroup`, `SegmentedChoice`, `YesNoToggle`, `ExpandableSection`, `SearchField` | component | S04–S09, S27 | ≥ 48 dp targets, `selectableGroup()` |
| `StepProgressHeader` | component | S04–S09 | "1/6" announced as "चरण 1, कुल 6" |
| `DobInputRow` | component | S06 | 3 numeric fields + live age helper |
| `GoogleSignInButton`, `AppleSignInButton` (iOS only), `AccountHeader`, `SignInOfferContent` (design 06 K1–K3) | component | S03, S10, S26, S38, B08, B16 | official Google button colours (`component.googleSignInButton`), never restyled; label from our strings. Apple button drawn in CMP to Apple's HIG (black/white/outline, Apple logo glyph, "Apple से साइन इन करें" / "Sign in with Apple", same height and corner as the Google button, shown **above or equal to** Google) — a native `ASAuthorizationAppleIDButton` via `UIKitView` is the fallback if review objects |
| `SwitchRow`, `SettingsRow`, `InfoRow`, `TimeRangeRow` | component | S26, S28, S32, S38 | |
| `LanguageOptionCard` | component | S02, S40 | each label in its own script |
| `IllustrationHeader`, `BulletRow`, `ResultIllustration` | component | S03, S13, S31 | vector only |
| `FaqItem` | component | S39 | expandable |
| `VerdictChip` | domain | S20, S21, S10 | icon + word + colour (never colour alone) |
| `DeadlineChip`, `DeadlineHero` | domain | S20, S21 | tone from `deadlineTone()`; tabular numbers, reserved min width |
| `MatchCard` (+ compact variant) | domain | S20, S44, S25 | one TalkBack stop, `mergeDescendants` + custom actions (भर दिया / रुचि नहीं / बाद में) |
| `MiniJobRow`, `MatchCountHero`, `ProfileCompletenessBar` | domain | S10 | |
| `WhatYouNeedCard`, `UploadSpecRow` | domain | S21 | photo/signature spec, fee for user's category, documents |
| `WhyYouMatchList` / `RuleLine` | domain | S21, B06 | ✓ ? ✗ glyph + icon + text |
| `OfficialLinksCard`, `DisclaimerNote` | domain | S21, S03, S39 | disclaimer is mandatory on every match surface |
| `ChangeLogCard`, `CorrigendumDiffRow` | domain | S21, B07 | before → after |
| `TrackerRow`, `ProUpsellInline` | domain | S22 | lock row for free users |
| `LifecycleTimeline` / `TimelineStep`, `StageActionCard`, `RescheduleNotice` | domain | S23 | stage tones from `semantic.stage.*` |
| `ProStatusCard`, `PlanOptionCard`, `BenefitRow`, `PaymentMethodRow`, `PlanSummaryCard`, `SubscriptionTermsNote` (iOS: auto-renew terms, trial conversion, Terms/Privacy links — guideline 3.1.2) | domain | S26, S30, S32 | prices from Play / StoreKit only |
| `OemStepCard`, `SelfTestStepper`, `HealthCheckRow` | domain | S15, S16, S29 | |
| `PrivacyNoticeItem`, `InviteShareCard`, `FamilyLinkRow`, `FamilyFollowingCard`, `ProfileConflictDialog`, `AdNoticeSheet` (design 06 K4–K8, J2) | domain | S47, S48, S34, S49, D10, B17 | QR rendered with a tiny pure-Kotlin encoder (no image loader) |
| `FamilyMemberRow`, `CertificateRow`, `ValidityBadge`, `ClashCompareCard`, `InboxRow`, `ArchiveRow`, `AgeChanceRow`, `DigestHeader` | domain | P1/P2 screens | built when their screen is built |

Icons: ~60 Material Symbols Rounded glyphs as Android-vector XML in `composeResources/drawable/ic_*.xml` (supported by compose resources on both platforms) — **no** `material-icons-extended` (multi-MB). Reference with `vectorResource(Res.drawable.ic_x)`; directional ones carry `android:autoMirrored="true"`. Material Symbols on iOS is a deliberate choice (same design on both platforms), not SF Symbols; only the iOS share/system sheets show SF Symbols.
