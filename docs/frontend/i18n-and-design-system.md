# i18n and the design system in code

Sources: `docs/design/02-design-system.md`, `docs/design/tokens.json` (W3C DTCG), component catalogue `docs/design/06-components.md` (when published; until then the "Key components" column of `01-screen-inventory.md`).

## Part A — Internationalisation

### A1. Languages and resources

| Item | Decision |
| --- | --- |
| Languages | Hindi (default for every new user) + English |
| Resource folders | `values/` = **English** (safe system fallback), `values-hi/` = **Hindi**. On first run, S02 is preselected to Hindi and we call `setApplicationLocales(hi)` — so Hindi is the product default without making Hindi the fallback for unknown locales (design §7). |
| Per-app language | `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))`; `res/xml/locales_config.xml` (`hi`, `en`) + `android:localeConfig` in the manifest so Android 13+ shows the app in system *Settings → App languages*; for API 26–32 the `AppLocalesMetadataHolderService` with `autoStoreLocales=true` persists the choice. `MainActivity` extends `AppCompatActivity` (needed for pre-33 locale switching). |
| Change at runtime (S40) | Applies instantly (activity recreated by AppCompat); `LanguageController` mirrors the value into DataStore for workers, `FirebaseMessagingService` and notification channel names (no Activity there), then re-creates channels and calls `upsertDevice` (+ `startSession` locale) so server-rendered WhatsApp text follows. |
| Server content | Every user-visible field is `LocalizedText {hi, en}`; `LocalizedText.pick(lang) = if (lang == EN) en.ifBlank { hi } else hi`. Because both languages are cached in Room, a language switch works offline. |
| Plurals | `<plurals>` (Hindi: `one`/`other`). E.g. `days_left`: "%d दिन बाकी" — Hindi uses the same form for 1 and many in this phrase, but we still go through plurals for English. |
| Strings rules | Simple spoken Hindi per design §7 (word list in a lint baseline: "अंतिम तिथि" → "आखिरी तारीख"); no string concatenation — always placeholders (`%1$s`) because word order differs; exam names (SSC CGL, RRB NTPC, DV) are not translated. Lint `MissingTranslation` = error. |
| Pseudo-locales | `pseudoLocalesEnabled = true` in debug to catch truncation (en-XA) |

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
- Age display ("आपकी उम्र आज: 23 साल 4 महीने") uses `Period.between(dob, todayIst)`. Eligibility itself is computed server-side; the app only formats `RuleExplanation.text` (authoritative) and uses `params` for analytics.

### A3. Devanagari fonts and text rendering

- **Mukta** via Downloadable Fonts (`androidx.compose.ui:ui-text-google-fonts`, provider `com.google.android.gms.fonts`, certs in `res/values/font_certs.xml`): **0 KB in the APK**. Weights 400/500/600/700 only.
- Fallback chain: until downloaded, or without GMS, `FontFamily.Default` (Roboto + system Noto Sans Devanagari, present on all Android 8+ devices). Layouts are screenshot-tested with both (testing-ci.md).
- Warm-up: `MainApplication` calls `FontFamily.Resolver.preload(MuktaFamily)` on a background dispatcher after first frame, so the second screen already has the font; the first frame may use the fallback (no FOIT blocking).
- We do **not** bundle a subset: a 4-weight Devanagari+Latin subset still costs ~400–600 KB, and the system Noto fallback is good enough for the rare no-GMS phone.
- Every `TextStyle`: `PlatformTextStyle(includeFontPadding = false)`, `LineHeightStyle(Alignment.Center, Trim.None)`, `letterSpacing = 0.sp`; line heights ≈ 1.5× (matras need room); no fixed text container heights; no ALL CAPS.
- Notification text and WhatsApp text use the system font (we can't control it) — another reason to keep copy short.

## Part B — Design tokens → Compose theme

### B1. Mapping

| tokens.json | Compose | File (`:core:designsystem/theme`) |
| --- | --- | --- |
| `color.light/dark.*` (M3 roles) | `lightColorScheme(...)` / `darkColorScheme(...)` | `Color.kt` |
| `color.*.ext.*` (success, warning, urgent, today, whatsappBrand) | `@Immutable data class SarkariColors` via `staticCompositionLocalOf` | `SarkariColors.kt` |
| `semantic.*` (verdict, deadline, stage, pro) | `VerdictStyle`, `DeadlineStyle`, `StageStyle` lookups returning container/content/icon | `Semantic.kt` |
| `font.family` + `font.scale` | `Typography` (12 M3 roles) with Mukta + Devanagari-safe style | `Type.kt` |
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
| `PhoneField`, `OtpField` | component | S11, S12, S33 | `+91` prefix; OTP single field, `ContentType.SmsOtpCode` |
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
| `WhatsAppPreviewBubble` | domain | S33 | WhatsApp glyph is the only `whatsappBrand` use |
| `FamilyMemberRow`, `CertificateRow`, `ValidityBadge`, `ClashCompareCard`, `InboxRow`, `ArchiveRow`, `AgeChanceRow`, `DigestHeader` | domain | P1/P2 screens | built when their screen is built |

Icons: ~60 Material Symbols Rounded glyphs imported as vector drawables (`res/drawable/ic_*.xml`) — **no** `material-icons-extended` (multi-MB). Reference with `ImageVector.vectorResource`.
