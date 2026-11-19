# i18n and the design system in code

Sources: `docs/design/02-design-system.md`, `docs/design/07-localization.md` (languages, scripts, expansion, RTL), `docs/design/tokens.json` (W3C DTCG; `font.script.*`, `layout.i18n`), component catalogue `docs/design/06-components.md` (when published; until then the "Key components" column of `01-screen-inventory.md`).

## Part A — Internationalisation

### A1. Languages and resources (13 languages, launch hi + en)

Founder decision 1 (2026-11-19): the architecture supports all major Indian languages; Hindi + English ship first.

| Item | Decision |
| --- | --- |
| Languages | `hi` (default for every new user), `en` enabled at launch. Ready in code and resources: `bn`, `mr`, `te`, `ta`, `gu`, `kn`, `or`, `ml`, `pa`, `as`, `ur` (RTL). Which ones are selectable comes from `ReferenceBundle.languages[].status` (`enabled` / `coming_soon`) **∩** the locales compiled into this build; S02/S40 list enabled ones and a non-selectable "जल्द आ रही हैं" list (design 07). |
| Resource folders | `values/` = **English** (safe system fallback), `values-hi/`, and one folder per language as it is translated: `values-bn/`, `values-mr/`, `values-te/`, `values-ta/`, `values-gu/`, `values-kn/`, `values-or/`, `values-ml/`, `values-pa/`, `values-as/`, `values-ur/`. Strings live per feature module; plurals in `<plurals>`; no concatenation, only positional placeholders (`%1$s`) because word order differs (SOV vs SVO, Urdu RTL). Exam names (SSC CGL, RRB NTPC, DV) are never translated. |
| Translation workflow | Source of truth = `values/` (en) + `values-hi/` (authored). Other languages: export XLIFF from the string resources → TMS (tool open: Crowdin/Weblate/Tolgee) with LLM draft + native reviewer → import. A language is shipped only when 100 % of strings are reviewed; `MissingTranslation` lint = error for enabled locales only (non-enabled folders may be partial in debug). |
| Build packaging | `androidResources { localeFilters += listOf(<enabled locales>) }` and `res/xml/locales_config.xml` list **only enabled locales**, so a half-translated language never ships and library translations for other locales are stripped. Enabling a language = add it to both + flip its server status. App Bundle language splits keep each user's download to their languages. |
| Per-app language | `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))`; `android:localeConfig` so Android 13+ shows the app in *Settings → App languages*; for API 26–32 `AppLocalesMetadataHolderService` with `autoStoreLocales=true`. `MainActivity` extends `AppCompatActivity`. First run: S02 preselects Hindi (or the device language if it is enabled). |
| Change at runtime (S40) | Applies instantly (activity recreated); `LanguageController` mirrors the code into DataStore for workers, `FirebaseMessagingService` and notification channel names (no Activity there), re-creates channels with the same ids, triggers a catalog snapshot fetch for the new locale set, and (accounts only) `upsertDevice(locale)` so server-composed account pushes follow. |
| Server content | `LocalizedText` = `Map<LanguageCode, String>`. `pick(lang)` walks `[lang] + languages[lang].fallback + en` (e.g. mr → hi → en; ta → en → hi; ur → en → hi). The catalog is fetched with `?locales=<that chain>`, so a language switch works offline only for languages already fetched. |
| Notifications | All push/backup text is built on the device from string resources in the app language (design 05: titles ≤ 45, actions ≤ 15 characters per language); server sends ids only. |
| Plurals and numbers | `<plurals>` per language (Hindi `one`/`other`; Urdu/Bengali same CLDR sets via ICU). **Western digits everywhere** (design 07), via `-u-nu-latn` locales. |
| Pseudo-locales | `pseudoLocalesEnabled = true` in debug: `en-XA` (accented, +30 % length) and `ar-XB` (bidi/RTL mirror). Both run in screenshot tests on every PR (testing-ci.md), plus the longest real locale (`ta`) and `ur` once their folders exist. |

### A1a. RTL (Urdu) readiness

- `android:supportsRtl="true"`; Compose uses `LocalLayoutDirection` from the locale automatically. Only `start`/`end` paddings and alignments (lint `RtlHardcoded`, `RtlSymmetry` = error); `Arrangement.Start`, never `Left`.
- Directional icons use `ImageVector` with `autoMirror = true` (back arrow, chevrons, "open" arrows); non-directional ones (check, bell, Google "G") never mirror (design 06 L4 `MirrorableIcon`).
- Mixed text: dates, ₹ amounts, exam codes ("SSC CGL 2026") and URLs inside Urdu text are wrapped with `BidiFormatter.unicodeWrap` / Unicode isolates (U+2066…U+2069) so they keep LTR order (design 06 L3 `localizedAnnotatedString`).
- Number keyboards, progress bars and swipe actions mirror; timelines (S23) run right-to-left.
- Screenshot tests in `ar-XB` catch any hard-coded left/right before Urdu strings exist.

### A2. Dates, numbers, money — always IST

```kotlin
object Ist { val ZONE: ZoneId = ZoneId.of("Asia/Kolkata") }

/** All "today", countdowns and deadline tones use the IST calendar date, never device zone or UTC. */
class IstClock @Inject constructor(private val clock: Clock) {
    fun today(): LocalDate = LocalDate.now(clock.withZone(Ist.ZONE))
    /** Emits at every IST midnight so feed sections re-bucket while the app is open. */
    fun istDateTicks(): Flow<LocalDate> = flow { while (true) { emit(today()); delay(millisUntilNextIstMidnight()) } }
}

object SmFormat {
    // Western digits in Hindi too (design §3.2): force the latn numbering system.
    private val HI = Locale.forLanguageTag("hi-IN-u-nu-latn")
    private val EN = Locale.forLanguageTag("en-IN")
    fun locale(l: AppLanguage) = if (l == AppLanguage.HI) HI else EN

    fun longDate(d: LocalDate, l: AppLanguage): String =            // "24 अगस्त 2026" / "24 Aug 2026"
        d.format(DateTimeFormatter.ofPattern(if (l == AppLanguage.HI) "d MMMM yyyy" else "d MMM yyyy", locale(l)))
    fun chipDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("dd/MM"))
    fun rupees(paise: Long): String =                                 // "₹100", "₹1,00,000" (Indian grouping), no decimals
        "₹" + NumberFormat.getIntegerInstance(EN).format(paise / 100)
    fun daysLeft(today: LocalDate, last: LocalDate): Long = ChronoUnit.DAYS.between(today, last)   // 0 = today
    fun timeOfDay(t: LocalTime, l: AppLanguage, res: Resources): String = /* "सुबह 9 बजे" / "9:00 am" via string resources */ TODO()
}

enum class DeadlineTone { Today, Urgent, Soon, Calm, Closed }  // tokens.json semantic.deadline.*
fun deadlineTone(daysLeft: Long) = when {
    daysLeft < 0 -> DeadlineTone.Closed; daysLeft == 0L -> DeadlineTone.Today
    daysLeft <= 3 -> DeadlineTone.Urgent; daysLeft <= 7 -> DeadlineTone.Soon; else -> DeadlineTone.Calm
}
```

- API calendar dates (`last_date`, `dob`, key dates) are parsed directly as `LocalDate` (IST) and **never** converted through UTC (API README §8). Instants are parsed with `OffsetDateTime` and displayed in IST.
- `java.time` is available natively from API 26 (our minSdk), so no core-library desugaring and no ThreeTen.
- Age display ("आपकी उम्र आज: 23 साल 4 महीने") uses `Period.between(dob, todayIst)`. Eligibility is computed **on the device** by `:matching-core`; its `RuleExplanation` lines are rendered from per-language string templates with computed `params` (dates and numbers formatted by `SmFormat` for the app language).
- `SmFormat.locale(lang)` maps every `LanguageCode` to `Locale.forLanguageTag("<code>-IN-u-nu-latn")` (Urdu: `ur-IN-u-nu-latn`); month names come from CLDR for that language.

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

- All fonts via **Downloadable Fonts** (`androidx.compose.ui:ui-text-google-fonts`, provider `com.google.android.gms.fonts`): **0 KB in the APK**. Only the **active language's** script is requested (plus Latin for exam codes), lazily after the first frame with `FontFamily.Resolver.preload`; switching language triggers the new script's download. Weights 400/500/600/700.
- Fallback: until downloaded, or without GMS, the system font (Android 8+ ships Noto for every Indic script; Nastaliq may be missing on older phones → Naskh). Layouts are screenshot-tested with fallback and downloaded fonts.
- `SarkariTheme(locale)` (design 06 L2) builds the `Typography` with the script's family and multiplies every token line height by the script's `lineHeightMultiplier`; sizes stay the same.
- Every `TextStyle`: `PlatformTextStyle(includeFontPadding = false)`, `LineHeightStyle(Alignment.Center, Trim.None)`, `letterSpacing = 0.sp`; no fixed text container heights; no ALL CAPS; wrap, don't truncate (titles ≤ 3 lines, buttons ≤ 2, chips 1 with tooltip; button rows stack at fontScale ≥ 1.5 or when any label needs 2 lines — `layout.i18n`).
- Expansion budgets vs English (`layout.i18n.expansionVsEnglish`): up to +60 % for ta/ml, +40 % kn, +35 % te. `en-XA` (+30 %) and `ta` must pass the screenshot suite before a language is enabled.
- Notification text uses the system font (we can't control it) — another reason for the per-language length limits.

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
| `breakpoint.*` | `WindowSizeClass` → content `widthIn(max = 600.dp)` on medium/expanded | `Layout.kt` |

Dynamic colour is **off** (design §2.6). Theme mode: System / Light / Dark from DataStore.

```kotlin
@Composable
fun SmTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val reducedMotion = rememberReducedMotion()          // ANIMATOR_DURATION_SCALE == 0 or a11y "remove animations"
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

**Keeping code and tokens in sync:** `Color.kt`/`Type.kt` are written by hand once (they are small), and a JVM unit test `TokensParityTest` parses `docs/design/tokens.json` and asserts every colour role, type size/line height/weight, spacing and radius equals the Kotlin value. A token change in design fails CI until the theme is updated — cheaper than a code generator, and it catches drift both ways.

Lint guard: a custom detekt/lint rule (or a simple grep in CI) forbids `Color(0x…)`, raw `.sp` and `.dp` literals in `:feature:*` sources (design system only).

### B2. Component catalogue (`:core:designsystem`)

Two layers: `component/` (generic, model-free — previews and screenshot tests are trivial) and `domain/` (knows `:core:model` UI types such as `MatchCardUi`). Every component is stateless (state + lambdas), has `@Preview`s at font scale 1.0/1.3/2.0 in light/dark, a Roborazzi screenshot, and a `testTag`.

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
| `GoogleSignInButton`, `AccountHeader`, `SignInOfferContent` (design 06 K1–K3) | component | S03, S10, S26, S38, B08, B16 | official Google button colours (`component.googleSignInButton`), never restyled; label from our strings |
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
| `ProStatusCard`, `PlanOptionCard`, `BenefitRow`, `PaymentMethodRow`, `PlanSummaryCard` | domain | S26, S30, S32 | prices from Play |
| `OemStepCard`, `SelfTestStepper`, `HealthCheckRow` | domain | S15, S16, S29 | |
| `PrivacyNoticeItem`, `InviteShareCard`, `FamilyLinkRow`, `FamilyFollowingCard`, `ProfileConflictDialog`, `AdNoticeSheet` (design 06 K4–K8, J2) | domain | S47, S48, S34, S49, D10, B17 | QR rendered with a tiny pure-Kotlin encoder (no image loader) |
| `FamilyMemberRow`, `CertificateRow`, `ValidityBadge`, `ClashCompareCard`, `InboxRow`, `ArchiveRow`, `AgeChanceRow`, `DigestHeader` | domain | P1/P2 screens | built when their screen is built |

Icons: ~60 Material Symbols Rounded glyphs imported as vector drawables (`res/drawable/ic_*.xml`) — **no** `material-icons-extended` (multi-MB). Reference with `ImageVector.vectorResource`.
