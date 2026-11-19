# 06 — Component Library (Compose / Material 3)

Package suggestion: `:core:designsystem` → `com.sarkarimatch.designsystem.component`. All components are **stateless** (state hoisted, callbacks out), take `modifier: Modifier = Modifier` as first optional param, read colours only from `MaterialTheme.colorScheme` / `SarkariTheme.colors`, and ship `@Preview`s for: light, dark, fontScale 2.0, Hindi + English.

Shared UI models (in `:core:model`, all `@Immutable`):

```kotlin
enum class Verdict { Eligible, CheckYourself, NotEligible }
enum class DeadlineLevel { Calm, Soon, Urgent, Today, Closed }  // from daysLeft per 02 §2.4
enum class RuleStatus { Pass, Unknown, Fail, Info }
enum class StageStatus { Done, Current, Upcoming, Changed, Missed }
@Immutable data class RuleLineUi(val status: RuleStatus, val label: String, val text: String, val fixField: ProfileField? = null)
@Immutable data class MatchCardUi(
  val jobId: String, val title: String, val org: String, val posts: String?,
  val verdict: Verdict, val deadline: DeadlineUi, val feeText: String, val qualificationText: String,
  val topRule: RuleLineUi, val isNew: Boolean, val snoozedUntil: String?, val isChanged: Boolean,
)
@Immutable data class DeadlineUi(val level: DeadlineLevel, val daysLeft: Int, val shortText: String, val longText: String, val a11y: String)
```

---

## A. Foundations

| # | Component | M3 base | Notes |
| --- | --- | --- | --- |
| A1 | `SarkariTheme(darkTheme, content)` | `MaterialTheme` | Provides `LightColors/DarkColors`, `AppTypography`, `AppShapes`, `LocalSarkariColors`, `LocalReducedMotion` (Boolean from `ANIMATOR_DURATION_SCALE`). Dynamic colour off. |
| A2 | `Spacing` object | — | `xs=4.dp, sm=8, md=12, lg=16, xl=20, xxl=24, x3l=32` + `ScreenGutter=16`. |
| A3 | `SmIcons` | `ImageVector` | `object SmIcons { val Work = …vectorResource(R.drawable.ic_work) … }` — the ~60 Material Symbols from 02 §5. |
| A4 | `Modifier.cardSemantics(description, actions)` | semantics | `semantics(mergeDescendants = true) { contentDescription = …; customActions = … }` |

## B. Buttons & actions

| # | Component | M3 base | Props | Variants / states |
| --- | --- | --- | --- | --- |
| B1 | `SmPrimaryButton` | `Button` | `text, onClick, icon: ImageVector? = null, loading = false, enabled = true` | height 52, full width by default, `labelLarge`, `maxLines = 2`, `textAlign = Center`. Loading: icon slot → 18 dp `CircularProgressIndicator(strokeWidth 2)`, `enabled=false`, a11y `stateDescription="लोड हो रहा है"`. Disabled: M3 38 % + a `disabledReason: String?` rendered below by caller. |
| B2 | `SmSecondaryButton` | `OutlinedButton` | same | 52 dp; border `outline`. |
| B3 | `SmTonalButton` | `FilledTonalButton` | same | Used on MatchCard ("फ़ॉर्म भर दिया"), 40 dp visual + `minimumInteractiveComponentSize()`. |
| B4 | `SmTextButton` | `TextButton` | `text, onClick, icon?, destructive = false` | destructive → `contentColor = colorScheme.error`. |
| B5 | `BottomActionBar` | `Surface(tonalElevation=3.dp)` + `HorizontalDivider` | `primary: @Composable, secondary: @Composable? , tertiary: @Composable?, caption: String?` | Pads `WindowInsets.navigationBars`; 16 horizontal / 12 vertical. At `fontScale ≥ 1.5` lays primary/secondary vertically. |
| B6 | `JobActionBar` (S21) | B5 | `state: UserJobState, onApplied, onLater, onNotInterested, onUndoState, onOpenTimeline, onGetPro` | Renders the table in 04 §S21. |

## C. Job & match

### C1 `MatchCard`
- **Base:** `OutlinedCard` (container `surfaceContainerLowest`; border 1 dp `outlineVariant`; shape medium 12 dp) with `onClick`.
- **Props:** `ui: MatchCardUi, onClick, onApplied, onLater, onMore: (MatchMenuAction) -> Unit, variant: MatchCardVariant = Full`.
- **Variants:** `Full` (S20) · `Compact` (S44 digest, S10 `MiniJobRow`-like: no rule line, no actions) · `Skeleton`.
- **Visual states:** 
  - deadline `Today` → 4 dp top stripe `today`; `Urgent` → 4 dp stripe `urgent`; else none.
  - `isNew` → `NewDot` 8 dp primary before title + a11y "नई".
  - `snoozedUntil` → meta line `snooze` "{date} को याद दिलाएँगे".
  - `isChanged` → small `update` chip "बदलाव" (warning) in chip row.
  - pressed → M3 ripple; no elevation change.
- **Layout:** chip row (`FlowRow`, 8 gap: `VerdictChip`, `DeadlineChip`) → title (`titleMedium`, max 2 lines, ellipsis + full text in a11y) → org line → `MetaRow` (fee, qualification, last date — `bodySmall`, 16 dp icons, wraps) → `RuleLine` (top one) → divider → action row (`SmTonalButton` "फ़ॉर्म भर दिया" + `SmTextButton` "बाद में" + `IconButton(more_vert, "और विकल्प")`).
- **A11y:** `cardSemantics(ui.a11ySummary, [Applied, Later, NotInterested, Open])`; inner buttons still focusable.
- **Removal animation:** caller uses `Modifier.animateItem()`.

### C2 `VerdictChip`
- Base: `Surface(shape = small, color = container)` with icon 18 (filled) + `labelMedium` (non-interactive; use `AssistChip` only when tappable — CheckYourself chip is tappable → opens B06).
- `verdict` → (container, content, icon, text): Eligible → successContainer / onSuccessContainer / `check_circle` / "योग्य"; CheckYourself → warningContainer / onWarningContainer / `help` / "ख़ुद जाँचें"; NotEligible → errorContainer / onErrorContainer / `cancel` / "योग्य नहीं". Height 32 (visual), 48 touch when tappable.

### C3 `DeadlineChip`
- Same construction. Text examples: Today "आज आखिरी दिन" (filled `today` / `onToday`, icon `alarm`) · Urgent "2 दिन बाकी" · Soon "6 दिन बाकी" · Calm "21 नवं तक" · Closed "तारीख निकल गई".
- `a11y` = long text "आखिरी तारीख 21 नवंबर, 2 दिन बाकी".
- Uses tabular numerals when font supports `tnum`; `widthIn(min = 88.dp)` to avoid jitter at midnight updates.

### C4 `DeadlineHero` (S21)
- Props: `title, org, verdict, deadline: DeadlineUi, lastDateLong: String, change: ChangeUi?, onVerdictClick, onChangeClick`.
- Countdown number style: `displaySmall` in deadline accent colour (calm → `onSurface`). For Today: number replaced by text "आज".

### C5 `RuleLine`
- Row: 20 dp status icon (`check_circle` success / `help` warning / `cancel` error / `info` onSurfaceVariant) + `Text(buildAnnotatedString { bold(label + ": "); append(text) })` `bodyMedium` + optional trailing `SmTextButton("जोड़ें")` when `fixField != null`.
- Icon `contentDescription` = "सही" / "जाँचें" / "नहीं" / "जानकारी" (EN: "Pass", "Check", "Fail", "Info") — status is never colour-only; the icon *shapes* also differ (tick / question / cross / i).
- Min height 48 when interactive; vertical padding 6.

### C6 `WhyYouMatchList`
- Props: `verdict, rules: List<RuleLineUi>, checkedAt: String, onFix(ProfileField), onReport`.
- Order: Fail → Unknown → Pass → Info. Heading changes with verdict (04 §S21.3).

### C7 `WhatYouNeedCard`
- Props: `dates: List<DateRowUi>, fee: FeeUi, documents: List<DocItemUi>, photo: SpecUi, signature: SpecUi, extras: List<String>, onToggleDoc(id, checked), onCopy(text)`.
- Sub-components: `CardSectionHeader(icon, title)`, `LabelValueRow(label, value, emphasized, struck)`, `SpecGrid(items: List<Pair<label, value>>)` (2 columns; 1 column at fontScale ≥ 1.3), `DocChecklistItem` (`Checkbox` + text; 48 dp row; `Role.Checkbox`).
- Collapsing: `documents.size > 4` → "सब देखें (n)" `SmTextButton`, `AnimatedVisibility` (instant if reduced motion).

### C8 `OfficialLinksCard`
- Two `ListItem`s (leading icon, headline, supporting text, trailing `open_in_new`), each `clickable` with `Role.Button`; offline → `enabled=false` + supporting text "इंटरनेट चाहिए".

### C9 `ChangeBanner` / `ChangeLogCard`
- Banner: `Surface(warningContainer, shape medium)` row: `update` icon + text + `SmTextButton("ब्योरा")`.
- Diff row: `old` with `TextDecoration.LineThrough` and `onSurfaceVariant`, arrow `arrow_forward` (auto-mirrors), `new` SemiBold.

### C10 `SectionHeader`
- `titleSmall` + optional count `labelMedium` in a `Badge`-like pill + leading 20 dp icon. `semantics { heading() }`. Top padding 24, bottom 8.

## D. Tracker & lifecycle

| # | Component | Base | Props / states |
| --- | --- | --- | --- |
| D1 | `TrackerRow` | `OutlinedCard` | `title, appliedOn, stage: StageUi, next: NextStageUi?, locked: Boolean, onClick`. Locked → next line with `lock` icon + "सूचना Pro में" in `tertiary`. Mini progress: 8 dots (8 dp, done = success fill, current = primary ring, upcoming = outlineVariant) with a11y "8 में से 3 चरण पूरे". |
| D2 | `LifecycleTimeline` | `Column` | `steps: List<TimelineStepUi>`; each `TimelineStep(status, icon, title, date, body, links)`: node 24 dp + 2 dp connector (done → success; else outlineVariant; Changed → warning dashed). Current step title bold + `primaryContainer` background row. |
| D3 | `StageActionCard` | `Card(primaryContainer)` | `icon, title, countdown: DeadlineUi?, primaryAction, secondaryAction` (e.g. "मेरा नाम है"/"नाम नहीं है"). |
| D4 | `ClashBanner` / `ClashCompareCard` | Surface warning / OutlinedCard | P1 |

## E. Onboarding & forms

| # | Component | Base | Props / states |
| --- | --- | --- | --- |
| E1 | `OnboardingStepScaffold` | `Scaffold` | `step: Int, total = 6, title, helper, onBack, onSkip?, nextEnabled, nextLabel, onNext, content`. Handles autosave callback & auto-advance (disabled when `AccessibilityManager.isTouchExplorationEnabled`). |
| E2 | `StepProgressHeader` | `LinearProgressIndicator` | `step, total`; a11y "चरण 2 / 6". |
| E3 | `SelectableTile` | `OutlinedCard` + `toggleable` | `icon, title, subtitle, selected, onToggle`; min 96 dp; selected border 2 dp primary, bg primaryContainer, check badge. `Role.Checkbox`. |
| E4 | `RadioListItem` | `ListItem` + `RadioButton` + `selectable` | `title, supporting?, selected, onSelect`; 56 dp min (72 with supporting). Parent `Modifier.selectableGroup()`. |
| E5 | `LanguageOptionCard` | E4 styled as card | text in its own script; `Modifier.semantics { this.contentDescription = …; }` + `LocaleSpan`/`localeList` so TalkBack pronounces with the right voice. |
| E6 | `DobInputRow` | 3× `OutlinedTextField` | `day, month, year, onChange, error: String?, ageText: String?`; `KeyboardOptions(keyboardType = Number, imeAction = Next)`; `FocusRequester` chain; `supportingText` shared under row; error → all three `isError`. |
| E7 | `ChipGroup` | `FlowRow` of `FilterChip` | `options, selected (single or multi), onSelect, onSkip?`; chips `labelMedium`, `minimumInteractiveComponentSize()`. |
| E8 | `YesNoToggle` | `SingleChoiceSegmentedButtonRow` | options हाँ/नहीं/(पता नहीं). |
| E9 | `SearchField` | `OutlinedTextField` leading `search` | debounce 150 ms; clear button labelled "मिटाएँ". |
| E10 | `PhoneField` | `OutlinedTextField` | prefix "+91 ", `KeyboardType.Phone`, `maxLength 10`, `VisualTransformation` "98765 43210", `autofill` hint `PhoneNumber`. |
| E11 | `OtpField` | `BasicTextField` + 6 decoration boxes | single field (paste & autofill), `KeyboardType.NumberPassword`, `autofill` hint `SmsOtpCode`; states default/focused/error/disabled. |
| E12 | `ProfileCompletenessBar` | `LinearProgressIndicator` + chips | `percent, missing: List<ProfileField>, onFix`. |
| E13 | `MonthYearPicker` | two `ExposedDropdownMenuBox`es (month, year) | no wheels: simpler on low-end phones and with TalkBack. |

## F. Feedback & status

| # | Component | Base | Notes |
| --- | --- | --- | --- |
| F1 | `StatusBanner` | `Surface(shape medium)` | `kind: Offline|NotifOff|Delivery|Outage|Trial|Profile`, `text, actionLabel?, onAction?, onDismiss?`. Colours: Offline/Outage → surfaceContainerHigh + onSurface; NotifOff/Delivery → warningContainer; Trial → tertiaryContainer; Profile → primaryContainer. Icon always present. `liveRegion = Polite` on first appearance. |
| F2 | `EmptyState` | `Column` | `icon (48), title (titleMedium), body (bodyMedium), primaryAction?, secondaryAction?`; centred, max width 320. |
| F3 | `ErrorState` | F2 variant | icon `error`, retry button, optional code `E-xxx`. |
| F4 | `Skeleton` blocks | `Box` + shimmer brush | `SkeletonLine(widthFraction)`, `MatchCardSkeleton`, `HeroSkeleton`; `LocalReducedMotion` → static. a11y: parent `contentDescription = "लोड हो रहा है"`, children cleared. |
| F5 | Snackbars | `SnackbarHost` | Helper `showUndo(message, actionLabel="वापस लाएँ")` with `SnackbarDuration.Long` (≈ 6–10 s; we pass custom 6 s). |
| F6 | `DisclaimerNote` | `Surface(surfaceContainer)` | `short: Boolean`; icon `info`; fixed copy (02 §7). |
| F7 | `HealthCheckRow` | `ListItem` | `status: Ok|Warn|Fail|Unknown` (icon `check_circle`/`warning`/`cancel`/`help` + status word), `title, value, actionLabel?, onAction?`. |
| F8 | `SelfTestStepper` | `Column` of F7-like rows | step states: Idle, Running (spinner), Passed, Failed, NeedsUser (instruction card). |
| F9 | `ResultScreen` (S31, S41, S42) | `Column` | `icon, iconTint, title, body, primary, secondary?`. |

## G. Pro & payments

| # | Component | Base | Notes |
| --- | --- | --- | --- |
| G1 | `PlanOptionCard` | `OutlinedCard` + `selectable(role = RadioButton)` | `title "साल भर", price "₹199", sub "सिर्फ़ ₹17/महीना", badge? "सबसे सस्ता", selected`. Selected: 2 dp primary border + `RadioButton` checked. Price `titleLarge`. |
| G2 | `BenefitRow` | `Row` | icon 24 tertiary + title `titleSmall` + body `bodySmall`; optional "जल्द" tag for P1. |
| G3 | `ProStatusCard` | `Card(tertiaryContainer)` | `state: Free|Trial(daysLeft, until)|Active(until, plan)|Expired`, `onCta`. |
| G4 | `ProUpsellInline` | `Surface(tertiaryContainer)` | one-line + text button; dismiss hides for 7 days. |
| G5 | `ProBadge` | `Surface(tertiary)` | "Pro" `labelSmall` onTertiary; a11y "Pro सुविधा". |
| G6 | `WhatsAppPreviewBubble` | `Surface(shape = RoundedCornerShape(12, 12, 12, 2))` | background `successContainer` / text `onSuccessContainer` (we do not imitate WhatsApp's own chat chrome), timestamp `bodySmall`. Official WhatsApp glyph 20 dp above. |

## H. Navigation & structure

| # | Component | Base | Notes |
| --- | --- | --- | --- |
| H1 | `SmBottomBar` | `NavigationBar` | 3 `NavigationBarItem`s (02/01). Selected icon filled. Label always shown (`alwaysShowLabel = true`). Badge on Jobs: `BadgedBox` with count ≤ "9+"; a11y "नौकरियाँ, 5 जवाब बाकी". |
| H2 | `SmTopBar` | `TopAppBar` | `title, onBack?, actions`; back icon `AutoMirrored.ArrowBack` with label "वापस". |
| H3 | `SettingsRow` | `ListItem` | `icon, title, value?, statusWord?, trailing = Chevron|Switch|None, destructive=false, onClick`. 56–72 dp. Switch rows: whole row `toggleable(role = Switch)`. |
| H4 | Sheets | `ModalBottomSheet` | `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`; drag handle on; title `titleLarge` + close `IconButton` (a11y "बंद करें"); content padding 24 h / 16 bottom + nav inset. |
| H5 | Dialogs | `AlertDialog` | Title `titleLarge`, body `bodyMedium`; confirm right, dismiss left; destructive confirm uses error colour text. |

## J. Ads (free tier only)

### J1 `NativeAdSlot`
- **Where (only these 2 places):** last item of the S20 feed (after all sections, before the footer disclaimer) and last item of S44 digest. Max **1 ad per screen**. **Never** in: S21 (job detail) incl. What-you-need / Why-you-match, B-sheets, S22/S23, onboarding, paywall, push/WhatsApp/SMS. Not rendered at all when `entitlement.isPro || trialActive`.
- **SDK:** Google Mobile Ads **native** ad (`NativeAdView` hosted via `AndroidView`), small template: icon 40 dp + headline + body (1 line) + CTA. No media view / video, no interstitial, no app-open, no rewarded. Request only when the slot is about to enter the viewport (`LazyListState` visible index ≥ last−2), on Wi-Fi or mobile; never more than one request per screen visit. Ad SDK is loaded lazily (keeps cold start fast; budget note in README).
- **Props:** `state: AdSlotState (Loading | Loaded(ad) | NoFill | Hidden), onAdClick, onWhyAds`.
- **Visual — deliberately unlike a MatchCard:**
  - container `surfaceContainer` (grey, not white), **dashed** 1 dp `outline` border, radius 12, **no** top stripe, no verdict/deadline chips, no job-style title.
  - top row: label pill **"विज्ञापन · Ad"** (`labelSmall`, `onSurfaceVariant` on `surfaceContainerHighest`, 4 dp radius) + right `TextButton` "विज्ञापन हटाएँ" → S30 (`src=ad_slot`). AdChoices icon in its required corner.
  - content: advertiser icon 40, headline `titleSmall` (max 1 line), body `bodySmall` (max 2 lines), CTA `SmSecondaryButton` compact (40 dp, outlined — never the primary blue filled style).
  - 24 dp gap above, separating it from the last match card; preceded by a thin `HorizontalDivider`.
- **States:** Loading → same-size static placeholder (no shimmer, to avoid looking like job skeletons) with only the "विज्ञापन · Ad" pill; NoFill / error / offline → **collapse to 0 height** (no empty box, no layout jump: slot is the last item so collapse shifts nothing above); Hidden for Pro.
- **Content policy (ad-network blocklist + manual review):** block categories coaching/test-prep/"sarkari result" sites, loan apps, gambling, dating; block creatives using govt emblems, tricolour or words like "अधिसूचना/Notification/Last date". Report link in overflow ("यह विज्ञापन ठीक नहीं") → logs `ad_report`.
- **A11y:** container `contentDescription` prefix "विज्ञापन:"; it is a separate TalkBack stop after the last card; not announced as a job.
- **Consent:** ads are non-personalised by default (`npa=1`) until the user consents in UMP/consent sheet (only where required); no ads to users who declared age < 18.
- **Analytics:** `ad_slot_view{screen}`, `ad_loaded`, `ad_no_fill`, `ad_click`, `ad_remove_tap` (→ paywall).

## I. Tokens → component mapping cheat-sheet

| Token | Components |
| --- | --- |
| `primary` | primary buttons, links, selected tiles/radio, current stage, countdown (calm hidden) |
| `ext.success*` | VerdictChip Eligible, RuleLine Pass, Applied strip, Stage Done, HealthCheck OK |
| `ext.warning*` | CheckYourself, Soon deadline, ChangeBanner, NotifOff banner, Stage Changed |
| `ext.urgent*` | Urgent deadline chip/stripe |
| `ext.today` | Today chip (filled) + stripe + R-last notif colour |
| `error*` | NotEligible, destructive actions, failed payment, cancelled notice |
| `tertiary*` | Pro badge/cards/benefit icons |
| `surfaceContainerLowest` | cards (light) · `surfaceContainerLow` cards (dark) |
| `outlineVariant` | card borders, dividers |
