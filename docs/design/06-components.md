# 06 — Component Library (Compose / Material 3) · Direction C "Marigold Home"

Package: `:core:designsystem` → `com.sarkarimatch.designsystem.component` (generic) and `.domain` (knows `:core:model`). All components are **stateless** (state hoisted, callbacks out), take `modifier: Modifier = Modifier` as the first optional param, read colours only from `MaterialTheme.colorScheme` / `SarkariTheme.colors`, shapes from `MaterialTheme.shapes` / `SmShapes`, and ship `@Preview`s for light, dark, fontScale 2.0, Hindi + English, and the pseudolocales `en-XA` / `ar-XB` (07 §3–§4). No fixed width on text; no `left`/`right` paddings.

Visual source: [02-design-system.md](02-design-system.md) (tokens, shadows, motion), [`directions/direction-c.html`](directions/direction-c.html), [`v2/marigold.css`](v2/marigold.css) (CSS class names are given in *italics* so the screens in [`v2/`](v2/) can be cross-checked).

**Change log**
- **2026-11-19 (v2, Direction C):** every component restyled; new A5 `SmCard`, A6 `CategoryTile`, B7 `MarigoldButton`, C11 `DeadlineRibbon`, C12 `CalendarTile`, C13 `FactStrip`, C14 `KeyDateChips`, C15 `GreetingCard`, C16 `SectionHeader` (replaces C10), D5 `JourneyPath` (replaces D2's look), F10 `AppliedCelebration` / `ConfettiMedallion`, G7 `ProCard`, G8 `ProUpsellStrip`, H6 `SegmentedTabs`. Card borders and 4 dp stripes retired (shadow + ribbon instead). §M collects patterns from the v2 screen agents' notes.
- **2026-11-19 (v1.1):** retired E10 `PhoneField`, E11 `OtpField`, G6 `WhatsAppPreviewBubble`; added §K (account, privacy, family) and §L (localization). See [CHANGES-2026-11-19.md](CHANGES-2026-11-19.md).

Shared UI models (in `:core:model`, all `@Immutable`):

```kotlin
enum class Verdict { Eligible, CheckYourself, NotEligible }
enum class DeadlineLevel { Calm, Soon, Urgent, Today, Closed }  // from IST daysLeft per 02 §2.5
enum class RuleStatus { Pass, Unknown, Fail, Info }
enum class StageStatus { Done, Current, Upcoming, Locked, Changed, Missed }
enum class JobCategory { Police, Ssc, Railway, Bank, State }    // tokens semantic.category; unknown → State
@Immutable data class RuleLineUi(val status: RuleStatus, val label: String, val text: String, val fixField: ProfileField? = null)
@Immutable data class MatchCardUi(
  val matchId: String, val title: String, val org: String, val posts: String?, val category: JobCategory,
  val verdict: Verdict, val deadline: DeadlineUi, val feeText: String, val qualificationText: String,
  val topRule: RuleLineUi, val isNew: Boolean, val snoozedUntil: String?, val isChanged: Boolean,
)
@Immutable data class DeadlineUi(
  val level: DeadlineLevel, val daysLeft: Int, val lastDate: LocalDate,
  val shortText: String,   // chip: "2 दिन बाकी"
  val ribbonText: String,  // ribbon sentence: "सिर्फ़ 2 दिन बाकी · 21 नवं तक" / "आज आखिरी दिन · रात 11 बजे तक"
  val a11y: String,
)
```

---

## A. Foundations

| # | Component | M3 base | Notes |
| --- | --- | --- | --- |
| A1 | `SarkariTheme(darkTheme, script, content)` | `MaterialTheme` | Provides `LightColors/DarkColors`, `appTypography(script)`, `AppShapes`, `LocalSarkariColors`, `LocalReducedMotion`. Dynamic colour off; `surfaceTint` transparent (02 §2.7). |
| A2 | `Spacing`, `SmSize` | — | 02 §4 (`ScreenGutter 16`, `CardPadding 16`, `CardGap 12`, `SectionGap 20`). |
| A3 | `SmIcons` | `ImageVector` | Material Symbols Rounded, **filled w500** for content/status, **outlined w400** for chrome (02 §6): `SmIcons.Work.filled` / `.outlined`. |
| A4 | `Modifier.cardSemantics(description, actions)` | semantics | `semantics(mergeDescendants = true) { contentDescription; customActions }` |
| A5 | **`SmCard`** *(.card, .sec, .dh)* | `Surface` (not `OutlinedCard`) | `onClick?`, `content`. Colour `SarkariTheme.colors.card`, shape `large` (24), **no border**, `tonalElevation 0`, `Modifier.cardShadow()` (02 §5.3) drawn before `clip`. Horizontal margin 16, gap 12. Pressed: ripple only. Content clipped so a top `DeadlineRibbon` follows the 24 dp corners. |
| A6 | **`CategoryTile`** *(.tile)* | `Box` | `category: JobCategory, size = 48.dp`. Shape `medium` (16), container/icon from `SarkariTheme.colors.<category>`, filled icon 24 (`semantic.category`). Decorative (`contentDescription = null`); the org text next to it carries meaning. Sizes: 48 (cards, detail), 40 (compact rows). |
| A7 | `IconTile` *(.st .ic, .bn .bi)* | `Box` | `icon, size = 36.dp, container = marigoldContainer, tint = marigoldIcon`. 36/12 for card section titles; 32/10 white + `shadow.benefitIcon` for Pro benefits. |

## B. Buttons & actions

All buttons: pill (`CircleShape`), `labelLarge` 16 sp/600, icon 22 dp + 8 gap, horizontal padding 20, `maxLines = 2`, text centred. Loading → 18 dp `CircularProgressIndicator(strokeWidth 2)` in the icon slot, `enabled = false`, `stateDescription = "लोड हो रहा है"`. Disabled → M3 38 % + caller-rendered `disabledReason`.

| # | Component | M3 base | Spec *(CSS)* |
| --- | --- | --- | --- |
| B1 | `SmPrimaryButton` | `Button` | *.btn.pri* — 52 dp (`compact = true` → 44 dp), `primary`/`onPrimary`, `Modifier.ctaShadow(primary)` (glow + 1 dp top highlight). Full width by default on screens and sheets; `weight(1f)` inside card action rows. Only **one** per screen region. |
| B2 | `SmSecondaryButton` | `OutlinedButton` | *.btn.out* — 52/44 dp, container `card`, content `onSurface`, border **1.5 dp `hairline`**. Used for ad CTA, "PDF देखें", dialogs. |
| B3 | `SmTonalButton` | `FilledTonalButton` | *.btn.ton* — 52/44 dp, container `sunken` (= `secondaryContainer`), content `onSurface`. The "Later / बाद में" action on cards; "रुचि नहीं" in the sticky bar. |
| B4 | `SmTextButton` | `TextButton` | `text, onClick, icon?, destructive = false` — content `primary` (destructive → `error`); 48 dp touch. |
| B5 | `BottomActionBar` *(.abar)* | `Surface(color = background, tonalElevation = 0)` + 32 dp fade above | `primary, secondary?, tertiary?, caption?`. **Solid** `background` with a 32 dp gradient fade (transparent → background) above it, no blur (§M1: glass read as a glitch). Padding 16 h, 4 top, 16 bottom + nav inset. Row: primary `weight(1f)` + tonal; at `fontScale ≥ 1.5` stacks. Optional caption row 40 dp (`labelMedium` `onSurfaceVariant` + 18 dp icon), e.g. "रुचि नहीं" as a text action under the buttons. |
| B6 | `JobActionBar` (S21) | B5 | `state: UserJobState, onApplied, onLater, onNotInterested, onUndoState, onOpenTimeline, onGetPro` — primary "फ़ॉर्म भर दिया" (`task_alt`), tonal "बाद में" (`snooze`), caption action "रुचि नहीं" (`visibility_off`, outlined icon). |
| B7 | **`MarigoldButton`** *(.btn.mari)* | `Button` with `ButtonDefaults.buttonColors(marigold, onMarigold)` | 52/44/40 dp; `Modifier.ctaShadow(marigold)` with 45 % highlight. **Pro upsell only** (Pro strip "₹17/महीने से", Pro card when the plan list is hidden). Never next to a primary button; max one per screen (02 §2.1). |

## C. Job & match

### C1 `MatchCard` *(.card)*
- **Base:** A5 `SmCard` with `onClick` → S21.
- **Props:** `ui: MatchCardUi, onClick, onApplied, onLater, onMore: (MatchMenuAction) -> Unit, variant: MatchCardVariant = Full`.
- **Layout (Full, S20):**
  1. `DeadlineRibbon` (C11) at the very top **only** for `Today` (filled) and `Urgent` (tinted) — full card width, clipped by the card's top corners.
  2. Body (*.cb*, padding 14→16 top, 16 sides/bottom): row [`CategoryTile` 48 · 12 gap · column(title `titleMedium` Baloo 19, max 3 lines; meta `bodySmall` `onSurfaceVariant` "UPPRPB · 19,220 पद")] + trailing `NewDot`/overflow `IconButton(more_vert outlined)`.
  3. Verdict / deadline chips row (`FlowRow`, gap 8): `VerdictChip` + `DeadlineChip` (the deadline chip is **omitted** when a ribbon already says it).
  4. `FactStrip` (C13): fee (`currency_rupee` + "400", tnum) · qualification (`school` + "12वीं").
  5. Top `WhyRow` (C5 compact): one rule line, `bodyLarge`.
  6. Action row (*.acts*, top 14→16, gap 8): `SmPrimaryButton(compact, "फ़ॉर्म भर दिया", task_alt, Modifier.weight(1f))` + `SmTonalButton(compact, "बाद में", snooze)`.
- **Variants:** `Full` (S20) · `Compact` (*.lr* — S20 "बाद में" group, S44 digest, S10: rows inside one shared card, `CategoryTile` 48, title `titleSmall`, meta line, chips; rows separated by 1 dp hairline; no actions) · `Skeleton` (sunken blocks with the same geometry, shimmer).
- **States:** `isNew` → 8 dp `primary` dot before the title + a11y "नई" · `snoozedUntil` → meta line `snooze` "{date} को याद दिलाएँगे" · `isChanged` → `ChangeChip` (`update`, warning tinted) in the chip row · `Closed` → title/meta in `onSurfaceVariant` (no alpha), no actions.
- **A11y:** `cardSemantics("आज आखिरी दिन, रात 11 बजे तक. UP Police Constable 2026. योग्य. फ़ीस ₹400 …", [Applied, Later, NotInterested, Open])`; inner buttons stay focusable.
- **Removal animation:** caller uses `Modifier.animateItem()`.

### C2 `VerdictChip` *(.vchip.ok / .q / .n)*
- `Surface(shape = CircleShape, color = container)`; height 28 visual (48 touch when tappable: CheckYourself → B06); padding 10 h; icon 18 filled + `labelMedium`.
- Eligible → `successContainer` / `onSuccessContainer` / `check_circle` / "योग्य" · CheckYourself → `warningContainer` / `onWarningContainer` / `help` / "ख़ुद जाँचें" · NotEligible → `errorContainer` / `onErrorContainer` / `cancel` / "योग्य नहीं" · Neutral (*.n*) → `sunken` / `onSurface` (e.g. "9 दिन बाकी", "10 दिस तक").
- **Tinted, never filled** — keeps it distinct from the filled green CTA.

### C3 `DeadlineChip` *(.left, .vchip.n)*
- Same construction as C2 (height 28; S21 hero variant 32 dp, `labelLarge`).
- Today "आज आखिरी दिन" (filled `today`/`onToday`, `alarm`) · Urgent "2 दिन बाकी" (`urgentContainer`/`onUrgentContainer`, `timer`) · Soon "6 दिन बाकी" (`warningContainer`, `schedule`) · Calm "21 नवं तक" (`sunken`/`onSurface`, `event`) · Closed "तारीख निकल गई" (`surfaceContainerHighest`, `event_busy`).
- Figures tnum; `widthIn(min = 88.dp)` (no jitter at IST midnight). `a11y` = "आखिरी तारीख 21 नवंबर, 2 दिन बाकी".

### C4 `DeadlineHero` (S21 header) *(.dh)*
- `SmCard` containing: row [`CategoryTile` 48 · title `titleMedium` · meta "Staff Selection Commission · 39,481 पद · पूरा भारत"]; then row (gap 16) [`CalendarTile` (C12) · column(`VerdictChip`, 8, `DeadlineChip` hero size, 4, "रात 11 बजे तक" `bodySmall` muted)]; optional `ChangeStrip` (C9) below.
- Props: `title, org, category, verdict, deadline, lastDateTimeText, change: ChangeUi?, onVerdictClick, onChangeClick`.

### C5 `RuleLine` / `WhyRow` *(.why, .wy)*
- **Card variant (`WhyRow`, on MatchCard):** 20 dp filled status icon (`check_circle` primary / `help` warning / `cancel` error / `info` onSurfaceVariant) + `Text(bold(label + ": ") + text)` `bodyLarge`.
- **Detail variant (`RuleBubbleRow`, S21 / B06):** 28 dp circle bubble (`successContainer`+`check` / `warningContainer`+`question_mark` / `errorContainer`+`close` / `sunken`+`info`), 18 dp glyph, 10→12 gap, text `bodyLarge`, label `titleSmall` weight. Row padding 6→8 vertical. Optional trailing `SmTextButton("जोड़ें")` when `fixField != null`.
- Icon `contentDescription` = "सही" / "जाँचें" / "नहीं" / "जानकारी" — shapes differ (tick / ? / ✗ / i), never colour only.

### C6 `WhyYouMatchList` *(.sec + .st + .wy)*
- `SmCard` with `CardSectionTitle(icon = fact_check, "आप योग्य क्यों हैं")` (A7 `IconTile` 36 + `titleLarge`), then `RuleBubbleRow`s. Order: Fail → Unknown → Pass → Info. Heading changes with verdict (04 §S21.3). Footer `bodySmall` "{date} को जाँचा · गलत लगे तो बताएँ" with `flag` text button.

### C7 `WhatYouNeedCard` *(.sec, .chk, .specs, .spc)*
- `SmCard` + `CardSectionTitle(checklist, "आपको क्या चाहिए")`. Props: `fee: FeeUi, documents: List<DocItemUi>, photo: SpecUi, signature: SpecUi, extras, onToggleDoc, onCopy`.
- **Fee row:** "आपकी फ़ीस (OBC)" + value `displaySmall` tnum ("₹100"), struck-through general fee in `bodySmall` muted if different.
- **Document checklist** (`DocChecklistItem`): 28 dp rounded-square box (radius 8; unchecked = **2 dp `outline`** ring — not hairline, for 3:1; checked = `primary` fill + white `check` 20) + text `bodyLarge`; row min 48 dp; `Role.Checkbox`; local only.
- **Spec boxes** (`SpecBox` ×2, *.spc*): `sunken`, radius 16, padding 12, header row (`photo_camera` / `draw` 18 muted + `titleSmall`) + 2–3 `bodySmall` lines ("200×230 px · 20–50 KB · JPG"); 2 columns, 1 column at fontScale ≥ 1.3.
- `documents.size > 5` → "सब देखें (n)" text button, `AnimatedVisibility` (instant if reduced motion).

### C8 `OfficialLinksCard` *(.lnk)*
- `SmCard`; two rows 60 dp min, separated by hairline: `IconTile` (`picture_as_pdf` / `open_in_new`) + headline `titleSmall` + supporting `bodySmall` (domain, LTR-isolated) + trailing `open_in_new` outlined; `Role.Button`; offline → disabled + "इंटरनेट चाहिए".

### C9 `ChangeStrip` / `ChangeLogCard` *(.chg)*
- Strip: `Surface(warningContainer, shape small 12→14)`, padding 8×12, `update` 18 + `labelMedium` "आखिरी तारीख बढ़ी: 14 → 21 नवं" (tnum, arrow auto-mirrors), tappable → B07.
- Diff row (B07): old `LineThrough` `onSurfaceVariant`, `arrow_forward`, new SemiBold.

### C11 `DeadlineRibbon` *(.rib.today / .rib.urgent)* — new
- `Row` full width at the top of `MatchCard` (and S10 mini cards), padding 16 h × 8 v, min height 36, icon 20 filled + `labelMedium` sentence, `maxLines = 2`.
- `Today`: container `today`, content `onToday`, `alarm`, "आज आखिरी दिन · रात 11 बजे तक" / "Last day today · until 11 pm".
- `Urgent`: container `urgentContainer`, content `onUrgentContainer`, `timer`, "सिर्फ़ 2 दिन बाकी · 21 नवं तक" / "Only 2 days left · until 21 Nov".
- Other levels: not rendered. Edge is the **top** of the card, so it never mirrors in RTL (07 §4). Read first in the card's merged a11y text.

### C12 `CalendarTile` *(.cal)* — new
- Tear-off calendar: width 76 dp (grows with font scale; never clips), radius 16, `card` body, `shadow.calendarTile` + 1 dp hairline bottom.
- Band: `today` / `onToday`, `labelMedium`, 22 dp min, CLDR short month ("नवं" / "NOV").
- Day: `displaySmall` Baloo 28, tnum, `onSurface`. Weekday: `labelSmall` muted ("शनिवार"), 6 bottom padding.
- Band stays terracotta for every level (it means "last date"); urgency is carried by the chip beside it.
- One a11y node: "आखिरी तारीख शनिवार 21 नवंबर". Props: `date: LocalDate, locale`.

### C13 `FactStrip` *(.facts)* — new
- `Row` gap 8 of `FactChip`s: `sunken`, radius 16 (14 in prototype), padding 8×10→12, icon 18 `onSurfaceVariant` + `labelLarge` value (tnum for money). Second chip may take `weight(1.6f)`. At fontScale ≥ 1.3 or when a value would wrap, becomes a `FlowRow` (chips wrap, never shrink text).

### C14 `KeyDateChips` *(.dts .dt)* — new
- Horizontally scrolling `LazyRow` (content padding 16, gap 8) inside the "ज़रूरी तारीख़ें" `SmCard`. `DateChip`: min width 96, radius 16, padding 8×12, label `bodySmall` muted + value `titleLarge` Baloo 19 tnum ("21 नवं · रात 11").
- States: `past` (`sunken` + `onSurfaceVariant` at full opacity — no alpha; `component.keyDateChip`) · `key` (the last date: `today`/`onToday`, label at 90 %) · `next` (`sunken`). Scroll starts at the key chip. TalkBack reads each chip as "आखिरी तारीख: 21 नवंबर, रात 11 बजे".

### C15 `GreetingCard` (S20 top) *(.greet, .pills)* — new
- `SmCard` with background `Brush.linearGradient(135°, marigoldContainer → card at 70 %)`; decorative marigold sun (circle r26 @ 35 % + dashed ring r44 @ 25 %) top-end, `invisibleToUser`, mirrored position in RTL.
- Content: `headlineSmall` "नमस्ते, रवि" · `bodyLarge` muted "गुरु, 19 नवं · 5 नौकरियों में जवाब बाकी" · `FlowRow` of `SummaryPill`s (32 dp, pill, `labelMedium`, icon 18): today = filled `today`/`onToday`; urgent = `urgentContainer`; calm = `card` + 1 dp hairline ring. Pills are tappable (scroll to section), 48 dp touch.
- Counts as **the** marigold surface of the Home screen (02 §2.1).

### C16 `SectionHeader` *(.sh)* — replaces C10
- Row padding 20 top / 8 bottom / 20 start: icon 22 + `titleLarge` + end-aligned count bubble (28 dp circle, `sunken`, `labelMedium` tnum). Today/urgent headers tint icon **and** text `urgent`. `semantics { heading() }`.

## D. Tracker & lifecycle

| # | Component | Base | Props / states |
| --- | --- | --- | --- |
| D1 | `TrackerCard` (S22) *(.sec)* | `SmCard` | `title, category, appliedOn, steps, locked, onClick`. Header row `CategoryTile` 48 + title `titleMedium` + "15 जुलाई को भरा" muted; then a `JourneyPath` (D5) collapsed to done + next + 2 upcoming, "सब चरण देखें" text button. |
| D2 | `LifecycleTimeline` (S23) | D5 full | All stages, with dates, links and `StageActionCard` under the current one. |
| D3 | `StageActionCard` | `SmCard` with `primaryContainer` fill | `icon, title, countdown: DeadlineUi?, primaryAction, secondaryAction` (e.g. "मेरा नाम है" / "नाम नहीं है"). |
| D4 | `ClashBanner` / `ClashCompareCard` | warning strip / `SmCard` | P1. |
| D5 | **`JourneyPath`** *(.path .stp)* — new | `Column` | `steps: List<StepUi(status, icon, title, date, isNext)>`. **Node** 36 dp circle: Done = `primary` fill + `onPrimary` `check`; Current/next = `marigold` fill + `onMarigold` stage icon + 4 dp `marigoldContainer` halo, title suffix "· अगला" muted; Upcoming = `card` + 2 dp hairline ring + muted icon; Locked = Upcoming + title in `onSurfaceVariant` + end `LockPill`. **Connector** 2 dp at node centre (start 17 dp), from node bottom to next node: solid `primary` after Done, **dashed 4/4 `hairline`** otherwise (`PathEffect.dashPathEffect`). Title `titleSmall`, date `bodySmall` tnum. Row padding 4→6 vertical; ≥ 48 dp when tappable. RTL: connector on the right. a11y per step "चरण 3: एडमिट कार्ड. Pro में. 1 दिसंबर तक आने की उम्मीद". |
| D6 | `LockPill` *(.lockp)* | `Surface(marigoldContainer, CircleShape)` | 24 dp, padding 8 h, `lock` 14 + `labelSmall` "Pro" in `onMarigoldContainer`. Not a button; the row or the Pro strip opens S30. |
| D7 | `SegmentedTabs` → see H6 | | |

## E. Onboarding & forms

| # | Component | Base | Props / states |
| --- | --- | --- | --- |
| E1 | `OnboardingStepScaffold` | `Scaffold` (container `background`) | `step, total = 6, title (headlineSmall Baloo 28), helper (bodyLarge muted), onBack, onSkip?, nextEnabled, nextLabel, onNext, content`. Sticky `BottomActionBar` with `SmPrimaryButton`. Auto-advance disabled under TalkBack. |
| E2 | `StepProgressHeader` | `LinearProgressIndicator` | track `sunken`, indicator `primary`, height 6, rounded caps; a11y "चरण 2 / 6". |
| E3 | `SelectableTile` | `SmCard` + `toggleable` | `icon (CategoryTile-style 48), title, subtitle, selected`; min 96 dp; selected = 2.5 dp `primary` inner border + `check_circle` primary top-end (same language as `PlanOptionCard`); `Role.Checkbox`. |
| E4 | `RadioListItem` | `ListItem` + `RadioButton` + `selectable` | 56 dp min (72 with supporting); `RadioButton` colours `primary` / unselected `outline`. Parent `selectableGroup()`. |
| E5 | `LanguageOptionCard` | E4 styled as `SmCard` | native name `titleLarge` in its own script font + English name `bodySmall`; `LocaleSpan`. |
| E6 | `DobInputRow` | 3× `OutlinedTextField` | shape `small` (12), border `outline` / focused `primary` 2 dp, container `card`; numeric keyboard; shared supporting text; error → `error`. |
| E7 | `ChipGroup` | `FlowRow` of `FilterChip` | pill 32→40 dp, unselected `card` + 1 dp hairline (`outline` when it is the only cue), selected `primaryContainer` + `check` 18 + `onPrimaryContainer`; `labelMedium`; `minimumInteractiveComponentSize()`. |
| E8 | `YesNoToggle` | `SingleChoiceSegmentedButtonRow` | styled like H6 pills. |
| E9 | `SearchField` | `OutlinedTextField` pill (`CircleShape`), container `card`, leading `search` | debounce 150 ms; clear "मिटाएँ". |
| ~~E10~~ | ~~`PhoneField`~~ | — | **Retired 2026-11-19.** |
| ~~E11~~ | ~~`OtpField`~~ | — | **Retired 2026-11-19.** |
| E12 | `ProfileCompletenessBar` | E2 + chips | `percent, missing, onFix`. |
| E13 | `MonthYearPicker` | two `ExposedDropdownMenuBox`es | no wheels. |

## F. Feedback & status

| # | Component | Base | Notes |
| --- | --- | --- | --- |
| F1 | `StatusBanner` | `Surface(shape = small 12→16)` | `kind: Offline|NotifOff|Delivery|Outage|Trial|Profile`, icon 20 + `labelMedium` text + optional `SmTextButton` / dismiss. Colours (canonical = §M2.39): Offline → `sunken`; NotifOff/Delivery → `warningContainer`; Outage / notice changed against you → `urgentContainer`; new chance → `primaryContainer`; pill action on `card`; Trial → `marigoldContainer` + `onSurface` (counts as the screen's marigold surface); Profile → `primaryContainer`. Margin 16, polite live region on first show. |
| F2 | `EmptyState` | `Column` centred, max width 320 | Illustration 120 dp: `IconTile`-style 88 dp circle in `marigoldContainer` with a 48 dp filled icon in `marigoldIcon` (dark: `marigold`), plus the decorative dashed sun ring from C15; title `titleLarge` Baloo 19; body `bodyLarge` muted; `SmPrimaryButton` / `SmTextButton`. Examples: S20 all answered "सब जवाब दे दिए — नई नौकरी आते ही बताएँगे" (`task_alt`); S22 empty "अभी कोई फ़ॉर्म नहीं भरा" (`assignment_turned_in`). |
| F3 | `ErrorState` | F2 variant | icon `report` / `cloud_off` in `errorContainer` circle, retry `SmPrimaryButton`, optional code `E-xxx`. |
| F4 | `Skeleton` | `Box` + shimmer brush `sunken → card` | `MatchCardSkeleton` keeps ribbon-less card geometry (24 radius, tile 48, two lines, fact strip, button pills). Static when reduced motion. |
| F5 | Snackbars | `SnackbarHost` + custom `Snackbar` | container `inverseSurface`, content `inverseOnSurface`, action `ext.snackbarAction` (marigold on cocoa / dark marigold on cream, §M1), shape `medium` (16), margin 16 above the bottom bar, `labelLarge` action "वापस लाएँ". `showUndo(...)` 6 s. Optional leading 20 dp icon (`task_alt` after Applied). |
| F6 | `DisclaimerNote` *(.disc)* | `Row` (no surface) | `info` outlined 18 muted + `bodySmall` muted; margin 4×20. Fixed copy (02 §8). |
| F7 | `HealthCheckRow` | `ListItem` | status icon + word, `titleSmall`, value, action. Icons: `check_circle` primary / `warning` warning / `cancel` error / `help` muted. |
| F8 | `SelfTestStepper` | `Column` of F7 rows | Idle / Running / Passed / Failed / NeedsUser. |
| F9 | `ResultScreen` (S31, S41, S42) | `Column` | icon in 80 dp circle (`primaryContainer` / `errorContainer`), `headlineMedium`, `bodyLarge`, buttons. Purchase success reuses F10 without confetti. |
| F10 | **`AppliedCelebration`** / **`ConfettiMedallion`** *(.peak, .medal)* — new | `Box` | Medallion 80 dp `primary` circle, `check` 44 filled `onPrimary`, 8 dp `primaryContainer` ring (`Modifier.border` outside or `drawBehind`), `shadow.medallion`, inner top highlight. Behind it a 300×120 dp `Canvas` with marigold / forest @ 55 % / terracotta @ 60 % confetti. Under it `headlineMedium` "शाबाश, फ़ॉर्म भर दिया!" / "Well done, applied!" + `bodySmall` muted "SSC GD Constable 2027 · मेरे फ़ॉर्म में जुड़ गया". Motion and reduced-motion fallback: 02 §7. Confetti and medallion are `invisibleToUser`; the headline is a polite live region. Used on B02 (Applied), S10 (matches peak) and S16 (self-test passed) only; waiting variant in §M1. |

## G. Pro & payments

| # | Component | Base | Notes |
| --- | --- | --- | --- |
| G1 | `PlanOptionCard` *(.plan)* | `Surface` + `selectable(role = RadioButton)` | radius 20→24, `card`, padding 10×12→12; unselected = 1.5 dp **inner** `hairline` border; selected = 2.5 dp `primary` border + `check_circle` 24 `primary` top-end. Content: `bodySmall` muted "साल भर" · price `displaySmall` Baloo 28 tnum "₹199" · `bodySmall` muted "सिर्फ़ ₹17/महीना". Optional **badge** "सबसे सस्ता": `primary`/`onPrimary` pill `labelSmall` overlapping the top edge by 10 dp at start 12. Two cards side by side (`weight(1f)`, gap 8); stack at fontScale ≥ 1.5. Parent `selectableGroup()`. |
| G2 | `BenefitRow` *(.bn)* | `Row` | `IconTile` 32 (white, radius 10, `shadow.benefitIcon`, icon 18 `marigoldIcon`) + `bodyLarge`/`bodySmall` text; optional "जल्द" tag (`sunken` pill, `labelSmall`) in a `FlowRow` after the title. |
| G3 | `ProStatusCard` | `SmCard` with `marigoldContainer` top gradient | `state: Free|Trial(daysLeft, until)|Active(until, plan)|Expired`, `onCta`. |
| G4 | `ProUpsellInline` → **G8** | | |
| G5 | `ProBadge` | `Surface(marigold, CircleShape)` | "Pro" `labelSmall` `onMarigold` + `workspace_premium` 14; 24 dp; a11y "Pro सुविधा". The 28 dp variant (*.vchip* with `workspace_premium`) heads the Pro card. |
| ~~G6~~ | ~~`WhatsAppPreviewBubble`~~ | — | **Retired 2026-11-19.** |
| G7 | **`ProCard`** *(.pcard)* — new | `Surface` | margin 12, padding 16, radius **28**, background vertical gradient `marigoldContainer → card` at 55 %, 1.5 dp inner border `tertiary` @ 45 %, `shadow.card`. Content: `ProBadge` 28 · `titleLarge` "आख़िर तक साथ चलें?" · 3–4 `BenefitRow`s · `PlanOptionCard` ×2 (gap 8, margin 12 v) · `SmPrimaryButton` full "14 दिन मुफ़्त आज़माएँ" (trial-eligible) or "₹199 में Pro लें" · `bodySmall` muted centred "कोई पैसा नहीं, कार्ड नहीं चाहिए" · `labelLarge` `primary` centred "₹199 में Pro लें · Google Play" (40 dp text action). The screen's marigold surface. Used on B02 (after F10) and S30. |
| G8 | **`ProUpsellStrip`** *(.up)* — new (replaces G4) | `Surface(marigoldContainer, radius 20→24)` | margin 16 h / 12 top, padding 12; `workspace_premium` 24 `marigoldIcon` + `labelLarge` text `onSurface` ("आवेदन के बाद के अलर्ट Pro में हैं") + `MarigoldButton` compact 40 dp "₹17/महीने से". Dismiss hides 7 days. S22 top. |

## H. Navigation & structure

| # | Component | Base | Notes |
| --- | --- | --- | --- |
| H1 | `SmBottomBar` *(.nav)* | `NavigationBar(containerColor = glass (97 %, §M1), tonalElevation = 0.dp)` + 1 dp `hairline` top line | 3 `NavigationBarItem`s; height 80 (88 with 2-line labels). **Marigold pill:** `NavigationBarItemDefaults.colors(indicatorColor = marigold, selectedIconColor = onMarigold, selectedTextColor = onSurface, unselectedIconColor = onSurfaceVariant, unselectedTextColor = onSurfaceVariant)`; indicator 64×32, radius 16. Selected icon **filled**, unselected **outlined**; selected label SemiBold, unselected Regular (`labelMedium` weights), `alwaysShowLabel = true`, `maxLines = 2`. Badge (`BadgedBox`): `today`/`onToday` (dark text in dark mode), `labelSmall`, min 20 dp, 2 dp ring in `background`, "9+" cap; a11y "नौकरियाँ, 5 जवाब बाकी". The nav pill does not count toward the one-marigold-surface rule. |
| H2 | `SmTopBar` *(.tb)* | `TopAppBar(colors = transparent on background)` | 56 dp, no colour fill, no scroll elevation tint (`scrolledContainerColor = background`, 1 dp hairline appears on scroll). Variants: **Home** = wordmark (28 dp marigold `IconTile` radius 8 with `notifications_active` 18 `onMarigold` + "Sarkari Match" `titleLarge` Baloo) + `notifications` outlined action with 9 dp `today` dot; **Large title** = `headlineSmall` Baloo 28 as title (S22, S26); **Detail** = back `arrow_back` outlined (AutoMirrored) + actions in 48 dp `card` circles with `shadow.card` (*.ib.c*) over content. |
| H3 | `SettingsRow` | `ListItem` inside `SmCard` groups | `IconTile` 36 (`sunken` + `onSurface` icon; destructive → `errorContainer` + `error`), `titleSmall`, value `bodySmall` muted, trailing chevron/switch. 56–72 dp; rows separated by hairline. `Switch` colours: checked track `primary`, thumb `onPrimary`; unchecked track `sunken`, border/thumb `outline`. |
| H4 | Sheets | `ModalBottomSheet(containerColor = card, tonalElevation = 0.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), scrimColor = scrim @ 50/60 %)` | `skipPartiallyExpanded = true`; drag handle 36×4 `hairline`→`outline` @ 50 %; header row: optional `IconTile` 36 + `titleLarge` Baloo + close `IconButton(close outlined)` "बंद करें"; content padding 24 h / 16 bottom + nav inset; actions full-width B1/B3 stacked (primary first). |
| H5 | Dialogs | `AlertDialog(containerColor = card, shape = extraLarge 28, tonalElevation = 0.dp)` | icon 24 in 40 dp tinted circle, title `titleLarge` Baloo, body `bodyLarge`; confirm `SmTextButton` (destructive → `error`), dismiss `SmTextButton`. |
| H6 | **`SegmentedTabs`** *(.segs)* — new | `Row` of pill toggles (`selectable(role = Tab)`) or `SingleChoiceSegmentedButtonRow` restyled | height 40 (48 touch), padding 16 h, gap 8, `labelLarge`; selected = **`onSurface` fill + `background` text** (cocoa pill, not green, so it is not read as a CTA); unselected = 1.5 dp hairline ring (`outline` ring if no count text distinguishes them). Counts in the label: "चालू (3)", "पूरे (1)". S22, S25. |

## J. Ads (free tier only)

### J1 `NativeAdSlot` *(.ad, .adl, .adimg)*
- **Where (only these 2 places):** last item of the S20 feed (after the "बाद में" group, before `DisclaimerNote`) and last item of S44 digest. Max 1 per screen. **Never** in S21 (incl. What-you-need / Why-you-match), sheets, S22/S23, onboarding, paywall, privacy and family screens (S47, S34, S35, S48, S49), push. Not rendered when `entitlement.isPro || trialActive` or for minors.
- **SDK:** Google Mobile Ads native (small template via `AndroidView`): icon + headline + body + CTA; no media/video/interstitial/app-open/rewarded. Requested only when the slot nears the viewport; lazy SDK init; `npa=1` (frontend ads.md).
- **Props:** `state: AdSlotState (Loading | Loaded(ad) | NoFill | Hidden), onAdClick, onWhyAds, onRemoveAds`.
- **Visual — deliberately unlike a MatchCard (Direction C):**
  - **No fill, no shadow** (cards have white + shadow; the ad is transparent on cream), radius 20→24, **1.5 dp dashed `hairline`** border (`outline` @ 60 % in dark if the hairline disappears), padding 12, margin 4 top / 12 bottom, 16 h. No ribbon, no category tile, no verdict/deadline chips, no Baloo title.
  - Row: advertiser icon 44 dp in a `sunken` radius-14→16 square (`storefront` outlined placeholder) · column [row(**"विज्ञापन · Ad"** tag: `labelSmall` `onSurfaceVariant`, 1 dp hairline ring, radius 6, padding 2×6 · `SmTextButton` "विज्ञापन हटाएँ" muted → S30 `src=ad_slot` · "क्यों?" → B17), headline `titleSmall` Hind 600 (max 1 line), body `bodySmall` muted (max 2 lines)] · CTA `SmSecondaryButton` compact 44 dp (outlined — never the filled green or marigold style). Tag row is a `FlowRow` for long languages; AdChoices in the SDK's required corner (mirrors in RTL).
- **States:** Loading → same-size static placeholder with only the tag; NoFill / error / offline → collapse to 0 height; Hidden for Pro/trial/minors.
- **Content policy, a11y, consent, analytics:** unchanged from v1.1 — blocklist (coaching, loan apps, gambling, dating, govt-emblem creatives), "विज्ञापन:" a11y prefix and separate TalkBack stop, non-personalised only, `ad_slot_view`/`ad_loaded`/`ad_no_fill`/`ad_click`/`ad_remove_tap`.

### J2 `AdNoticeSheet` (B17)
- H4 sheet. `IconTile` 40 `info` in `sunken` · `titleLarge` "विज्ञापन क्यों दिखता है?" · 3 `PrivacyNoticeItem`s (K4) · `SmTextButton` "यह विज्ञापन ठीक नहीं — बताएँ" · `SmSecondaryButton` "Pro में कोई विज्ञापन नहीं" → S30 · close.

## K. Account, privacy & family

| # | Component | Base | Props / states |
| --- | --- | --- | --- |
| K1 | `GoogleSignInButton` | official **Sign in with Google** branding (`tokens.json → component.googleSignInButton`), pill, 40 dp visual / 48 touch, multi-colour "G" 20 dp | Not restyled to Marigold (no forest/marigold, no shadow); Hind label in the app font is allowed. `text`, `onClick` → Credential Manager (O11), `loading`, `enabled` + `disabledReason`. Never the only button without a "not now" path. |
| K2 | `AccountHeader` | `SmCard` + `ListItem` | Guest: `person` in 40 dp `sunken` circle, "मेहमान (Guest)", supporting "फ़ोन बदलने पर प्रोफ़ाइल साथ रखने के लिए साइन इन करें", compact K1. SignedIn: photo 40 circle / initials on `primaryContainer`, name `titleSmall`, email `bodySmall` (LTR-isolated), chevron → S38. |
| K3 | `SignInOfferContent` (B16) | `Column` | `IconTile` 40 `cloud_done` on `primaryContainer`, `titleLarge`, 3 `BulletRow`s (filled `check_circle` 20 primary), K1 full width, `SmTextButton` "अभी नहीं". |
| K4 | `PrivacyNoticeItem` | `Row` | `IconTile` 36 (`sunken`, icon `onSurface`) + title `titleSmall` + body `bodyLarge` + why `bodySmall` muted "क्यों: …". One TalkBack node. S47 ×5 (copy: profile stored on the phone, 04 §S47), B17 ×3. |
| K5 | `InviteShareCard` (S48) | `SmCard` | QR 200 dp, always dark-on-white (even in dark theme), quiet zone 4; link row LTR-isolated + `content_copy`; status chip (C2 style): `schedule` "इंतज़ार" / `check_circle` "जुड़ गए" / `event_busy` "समय निकल गया"; `SmPrimaryButton` "लिंक शेयर करें" (`share`). |
| K6 | `FamilyLinkRow` (S34) | `ListItem` in `SmCard` | `IconTile` 36 `family_restroom`, relation + nickname, status chip, overflow. No phone number. |
| K7 | `FamilyFollowingCard` (S49) | `SmCard` | aspirant name (rendered on this phone from the encrypted watch-list), relation, up to 3 `DeadlineChip`s, paused state, `SmTextButton` "अलर्ट बंद करें" → D12. |
| K8 | `ProfileConflictDialog` (D10) | H5 | Two `RadioListItem`-style option cards: **"सेव की हुई (बैकअप)"** (restores the encrypted backup) and **"इस फ़ोन वाली"** (overwrites the backup), each with a summary ("SSC, रेलवे · ग्रेजुएशन · 3 फ़ॉर्म"); default = saved; confirm "यह रखें". Shown when sign-in returns `has_backup` and a local profile exists (no server 409). |

## L. Localization helpers (rules in 07)

| # | Component / helper | Notes |
| --- | --- | --- |
| L1 | `LanguageList` (S02/S40) | Enabled → E5 cards in `selectableGroup()`; coming soon → `ExpandableSection` "जल्द आ रही हैं ({n})" with non-focusable `ComingSoonLanguageRow` (system font). Search when > 5 enabled. |
| L2 | `SarkariTheme(script)` | Picks `appTypography(script)` — Baloo/Hind sister families or Noto per 02 §3.5 — and `LocalLayoutDirection`. |
| L3 | `localizedAnnotatedString(@StringRes, vararg args)` | Placeholders + bidi isolates + Western digits/IST + `LocaleSpan`; adds `tnum` on number args. No `+` on strings. |
| L4 | `MirrorableIcon(icon, mirror)` | `scaleX = -1` in RTL for directional glyphs without `AutoMirrored`. Greeting sun, confetti and journey connector positions mirror; the ribbon (top edge) and calendar tile do not. |
| L5 | Text rules | `softWrap = true`; `maxLines` only per 07 §3 (titles 3, buttons 2, chips 1, nav 2, ribbon 2); `TextAlign.Start/End`. |

## M. Patterns from the v2 screen redesign

Folded in from `v2/sections/*-NOTES.md` (screen agents' notes: `core-NOTES.md`, `onboarding-NOTES.md`, `account-NOTES.md`, `states-NOTES.md`). CSS prefixes `co-` / `ob-` / `ac-` / `st-` are given in italics.

### M1 Rules that change components above
- **Sticky bars are (near-)solid, not glass** (*ob-bar*, core `.abar` at 97 %): content scrolling under a 90 % glass bar read as a glitch. `BottomActionBar` (B5) = solid `background` + a 32 dp vertical fade (`background` 0 → 100 %) above the bar, no blur; the bottom nav (H1) keeps `glass` but at 97 %.
- **Marigold icon colour darkened** to `#9A5B00` (token `ext.marigoldIcon`, 4.78:1 on marigoldContainer) for hero/bullet/section icons.
- **Text-field / DOB box outlines use `onSurfaceVariant` 1.5 dp** (6.3:1) instead of hairline (*ob-field, ob-box*); focus = 2 dp `primary` ring.
- **Snackbar = cocoa with marigold action** (*co-snk, ob-snack*): container `inverseSurface`, action `ext.snackbarAction` (6.52:1 light / 5.51:1 dark). Replaces `inversePrimary` in F5.
- **Headline wrapping:** titles (28/19) use balanced wrapping, body uses "pretty" wrapping to avoid one-word orphans like "हैं". Compose: `TextStyle(lineBreak = LineBreak.Heading)` for display/headline/title styles, `LineBreak.Paragraph` for body (set in `appTypography`).
- **Calendar weekday is computed, never copied:** 21 Nov 2026 is a Saturday (direction-c.html's "Friday" was wrong). Weekday comes from CLDR for the IST date.
- **Medallion + confetti** (F10) is also used on **S10 matches peak** and **S16 self-test passed** (no confetti on failure). A waiting variant (*medal.ob-wait*: `sunken` medallion with a spinner ring) shows while on-device matching runs.

### M2 New components
| # | Component | Source | Spec |
| --- | --- | --- | --- |
| M2.1 | `StepHeader` | *ob-steprow, ob-prog* | Back in a 48 dp `card` circle (*ib.c*) + "चरण 2 / 6" `labelMedium` + 8 dp `primary` progress bar on `sunken`, rounded. Replaces E2's look. |
| M2.2 | `QuestionHeader` | *ob-q, ob-help* | `headlineSmall` question + `bodyLarge` muted helper, 16 h padding. |
| M2.3 | `HeroBadge` | *ob-hero* | 64 dp rounded (radius 20) icon badge, variants marigold / forest / terracotta container with matching icon colour; S13, S14, S47, D01. |
| M2.4 | `SelectTile` (multi) | *ob-tiles, ob-tl* | `CategoryTile` + label + sub; selected = 2.5 dp `primary` ring + filled `check_circle` top-end (same as plan cards). Refines E3. |
| M2.5 | `RadioCard` | *ob-opt, ob-rad, co-rad, ob-langc, ob-g2, ob-sub* | Single-select card 64 dp (language 72 dp; `.sm` in a 2-col grid); custom radio dot; selected card may **reveal a follow-up** inside itself (OBC certificate year, domicile Yes/No/Not sure, PwD type + 40 %). |
| M2.6 | `ChoiceChip` / `FilterChip` | *ob-chip, co-fc* | 40 dp visual + 8 dp gaps = 48 touch; selected = forest **tint** + `check` (never filled — shape rule). |
| M2.7 | `QuestionBlock` | *ob-qb, ob-qi, ob-skip* | `SmCard` per optional question with icon tile, label and its own "छोड़ें" text button (S09). |
| M2.8 | `AgeBanner` | *ob-age* | `primaryContainer` strip with computed age "आज आपकी उम्र: 23 साल 4 महीने", polite live region. |
| M2.9 | `InlineNote` | *ob-note (.q), co-local* | Icon + `bodySmall` on `sunken` (or `warningContainer` for warnings). **`LocalOnlyNote`** variant: `lock` icon + "यह सिर्फ़ आपके फ़ोन में रहता है" for anything kept on the device (DOB, B03 reasons, B04 snooze, S25, B06 confirmation). |
| M2.10 | `IconBullet` | *ob-bul, ob-bi* | 40 dp marigold `IconTile` + `bodyLarge`; `.pri` forest variant. S03, S13. |
| M2.11 | `NotificationMock` | *ob-panel, ob-notif, co-ntf, co-na* | Marigold-gradient panel with a static notification card (onboarding explanation only, not real UI). Real notification accent proposed: forest `#1E5B46`; critical terracotta `#B3361E`; family plum `#7A3D80` (05 still says `#1F4FB8` — to update). |
| M2.12 | `PrivacyKeyFact` | *ob-key, ob-pv* | Lead fact on S47 "आपकी प्रोफ़ाइल आपके फ़ोन में रहती है · मिलान भी फ़ोन पर" in a `primaryContainer` card, then K4 rows; optional crash-report `Switch` (default off — open question). |
| M2.13 | `ComingSoonGrid` | *ob-soon, ob-sgrid* | 3-column grid of native names (system Noto) with `schedule` + "जल्द"; refines L1's list. |
| M2.14 | `MinorModeCard` | *ob-minor* | Marigold gradient + ring like `ProCard`: explains under-18 mode (no sign-in, ads, purchase); counts as the screen's marigold surface. |
| M2.15 | `CompletenessRing` | *ob-comp, ob-ring* | Circular progress ring 40 dp (`primary` on `sunken`) + "प्रोफ़ाइल 90% पूरी" + missing chip. Refines E12. |
| M2.16 | `OemStepCard` | *ob-oem, ob-num, ob-done, ob-brands* | `SmCard` per step: numbered 28 dp node (done = `primary` + check, next = marigold, upcoming = hairline ring — same as JourneyPath), instruction, "सेटिंग खोलें" button, "हो गया" checkbox; brand chip row when detection is unsure. |
| M2.17 | `GoogleAccountSaved` | *ob-gsaved* | After upgrade: `check_circle` + "{email} से सेव" row replacing K1. |
| M2.18 | `StatusBanner` (feed) | *co-ban* | Pill action button ("चालू करें") on the banner. core.html tints the notifications-off banner terracotta while states.html uses ochre; **canonical: ochre `warningContainer`** (M2.39) — terracotta is kept for our outage and adverse corrigenda. core.html to align. |
| M2.19 | `FeedEnd` | *co-end, co-endc* | End-of-feed marker (hairline rule + "बस इतना ही" muted) and a calm info card (Pro users: "Pro · कोई विज्ञापन नहीं"). |
| M2.20 | `NewTag` | *co-new* | Marigold `labelSmall` "नई" pill on the card title row; replaces separate "New" / "Waiting for reply" feed sections (open question to confirm). |
| M2.21 | `AdSlot` label row | *co-ad* | J1 label row sits **above** the creative: "विज्ञापन · Ad" tag · "क्यों?" · "विज्ञापन हटाएँ". |
| M2.22 | Rule rows ✗ / i | *wy.co-no, wy.co-in, vchip.co-no, vchip.co-ap* | ✗ bubble `errorContainer` + `close`; i bubble `sunken` + `info`; "भर दिया" chip = `primaryContainer` + `task_alt`. Legend row under Why-you-match; footer "आपके फ़ोन पर मिलाया गया". |
| M2.23 | `CalendarTile` variants | *cal.co-caloff, cal.co-calq* | Not eligible / closed: band `surfaceContainerHighest` + `onSurfaceVariant`; check-yourself: band `warning` + `onWarning`. Extends C12 (terracotta stays the default). |
| M2.24 | `ReturnFromSitePrompt` | *co-ret* | On return from the apply site: `SmCard` "फ़ॉर्म भर दिया?" + primary "हाँ, भर दिया" → B01 + text "अभी नहीं". |
| M2.25 | `AppliedStrip` + `MiniJourney` | *co-done, co-mj* | S21 applied state: `primaryContainer` strip with `task_alt` "15 जुलाई को भरा" in the action-bar area + "अगले अलर्ट पाएँ — Pro"; B02 shows a 3-node mini `JourneyPath` (done / next / locked). |
| M2.26 | `DiffRows`, `EffectBox` | *co-df, co-eff* | Corrigendum before → after rows (C9) and a `warningContainer` box "आप पर असर: …". |
| M2.27 | `SheetHeader`, `SheetJobSummary` | *co-sheet, co-hdl, co-sth (.grn/.q), co-job* | H4 header with tinted `IconTile` (forest / ochre variants) + `titleLarge`; compact job row (CategoryTile 40 + title + deadline chip) at the top of action sheets. |
| M2.28 | `CheckboxRow`, `TextField` | *co-cbrow, co-tf, ob-field, ob-sel, ob-search* | 48 dp checkbox rows; fields radius 12 (search: pill) with 1.5 dp `onSurfaceVariant` outline. |
| M2.29 | `SyncStatusLine`, skeleton | *co-sync, co-sk, ob-skel* | "फ़ोन पर 312 खुले नोटिस मिलाए जा रहे हैं…" (count from the local catalog) + F4 skeleton. |
| M2.30 | `SegmentedControl` (2 options) | *ob-seg* | Same visual as H6 (cocoa selected pill). |
| M2.31 | `SettingsPathCrumbs` | D01 | Chip row "सेटिंग्स › ऐप › सूचनाएँ" (`sunken` chips, `chevron_right` auto-mirrors) + "सेटिंग खोलें". |

### M1b Rules from the states and account notes
- **Scrim** is warm: cocoa `#2A1E14` @ 48–52 % in light, black @ 60–62 % in dark. **Dialog surface** in dark = `surfaceContainerHigh` `#2D241B` (one step above the card); light = `card`.
- **Destructive actions** are terracotta *text* buttons in dialogs; the expanded delete uses an **outlined terracotta** button (`SmDangerButton`: `card` + 1.5 dp `error` border + `error` text). Never the filled green CTA.
- **Switch / radio off state** uses `outline` (≥ 3:1, WCAG 1.4.11).
- **Disabled rows never disappear**: in minor mode, rows get an `off` style + a "18 के बाद" / "From 18" `LockPill`-style tag (`st-tag`).
- **No opacity for meaning** — key-date "past" chip fixed in `v2/marigold.css` (`component.keyDateChip.past`).
- **200 % font scale:** nav labels capped at 1.5×, wordmark not scaled — needs WCAG 1.4.4 sign-off (02 §3.2, `layout.fontScale`). At ≥ 1.5× the S21 bar drops "रुचि नहीं" into the overflow menu.
- **Data-model line** under the S38 account card: "आपकी प्रोफ़ाइल और मैच सिर्फ़ इसी फ़ोन पर हैं". Sign-out keeps the local profile (guest); export is a local JSON file (04 D02/D04).
- **Weekday bug:** 21 Nov 2026 is a **Saturday** (direction-c.html says Friday); all v2 sections fixed.

### M2b Components from the account and states sections
| # | Component | Source | Spec |
| --- | --- | --- | --- |
| M2.32 | `TrialBanner` + `DaysRing` | *ac-trial, ac-ring* | `marigoldContainer` banner with a 40 dp ring (sweep = days left / 14, `marigold` on `sunken`, tnum number inside); the screen's marigold surface. |
| M2.33 | `TrackerRow` + `StageChip` + `StageDots` + `NextStepLine` | *ac-tr, ac-stage, ac-dots, ac-next* | Refines D1: stage chip icon + words (ok forest / q ochre / tc terracotta / n sunken); 8 dots (done forest, current marigold, locked hollow hairline ring); locked next line dashed hairline + `lock` + "Pro में". |
| M2.34 | `StageActionCard` with ribbon | *ac-act* | D3 uses a `DeadlineRibbon` (urgent, or forest-tinted "ok" variant `primaryContainer`) for the deadline sentence + two pill buttons. |
| M2.35 | `RescheduleChip` | *ac-resch* | Ochre tinted chip, old date struck through → new date bold; allow 2 lines (`heightIn(min)`). |
| M2.36 | `ResultIllustration` variants | *medal.ac-gold / ac-wait / ac-fail* | Same 80 dp medal as F10: success forest (+confetti on S31 success), pending marigold `hourglass_top`, failure terracotta `close`. |
| M2.37 | `HealthSummary` + `HealthCheckRow` | *ac-hsum, ac-score, ac-hc* | Score "5/6 ठीक" with ring; bad rows tinted `urgentContainer`; status always icon + words. |
| M2.38 | `SettingsGroup` | *ac-list, ac-row, ac-ic.{g,m,r}, st-row, st-list* | White grouped `SmCard`, inset dividers from 62 dp; icon tile 40 dp tinted green / marigold / terracotta. |
| M2.39 | `StatusBanner` variants | *st-ban (off, warn, err, ok)* | Supersedes F1 colours: off = `sunken`, warn = `warningContainer`, err = `urgentContainer`, ok = `primaryContainer`; optional pill action on `card`, 44 dp min; **max one banner on Home**. |
| M2.40 | `UndoSnackbar` | *st-snack* | F5 + a 20 dp countdown ring (6 s) before the action; polite live region. |
| M2.41 | `FullScreenState` | *st-center, st-illu* | 140 dp tinted disc + dashed halo + 48 dp icon; S41, S42 (house style for blocking states). |
| M2.42 | `FamilyExplainer`, `InviteeHeader`, `FamilyDateLine` | *ac-explain, ac-gets, ac-acc, ac-pair, ac-fdate* | "What they get" chips; aspirant ↔ you avatar pair; date lines tinted terracotta (urgent) / forest (ok). Family notification icon accent plum `#7A3D80` (= category state icon). |
| M2.43 | `GoogleSignInButton` sizes | *ac-gbtn, st-g* | Official colours; 52 / 48 / 44 dp heights allowed (visual ≥ 40 per tokens). |

### M3 Open questions raised by the screen agents (for the founder / owners)
Core: drop "New"/"Waiting" as feed sections? · help icon moved into the overflow menu · B05 snackbar copy after removing the 24 h promise · count `confirm_eligible` anywhere? · no snooze on the last day (S21·today) · B02 trial copy "अपने-आप ख़त्म होगा" needs billing confirmation. Onboarding: crash-report switch on S47 vs S38 only · minor-mode end behaviour · D10 "saved on {date}" from backup metadata · R1 day-before backup time (7 pm?) · Vivo OEM steps need a device check. Account: read-only S23 for free users? · completed-forms retention · season → yearly switch (proration) · S48 QR density with `#k=` fragment. States: export also include `getMe` data? (04 D04 now says yes, per data-layer) · delete with active Pro: nudge or required step · minor Pro row · 200 % caps sign-off · Tamil headings Baloo Thambi 2 (02 §3.5 says yes; 07 still Noto).

## I. Tokens → component mapping cheat-sheet

| Token | Components |
| --- | --- |
| `primary` / `onPrimary` | B1 primary pill, links, selected plan border/check, plan badge, journey Done node + connector, Applied medallion, checked checkbox |
| `primaryContainer` | medallion ring, StageActionCard, Profile banner, selected filter chip |
| `ext.success*` (= forest) | VerdictChip Eligible, ✓ rule bubbles/icons, HealthCheck OK |
| `ext.warning*` (ochre) | CheckYourself chip, ? bubbles, ChangeStrip, NotifOff banner, Soon chip |
| `ext.urgent*` (terracotta tinted) | Urgent ribbon, urgent chip, urgent section header |
| `ext.today` (terracotta filled) | Today ribbon, today summary pill, calendar band, key date chip, nav badge, notification dot |
| `error*` | NotEligible chip/bubble, destructive actions, failed payment, cancelled notice |
| `tertiary` / `ext.marigold*` | nav pill, MarigoldButton, Pro badge, journey next node + halo, LockPill, ProUpsellStrip, ProCard gradient/border, greeting gradient, section IconTiles, confetti |
| `ext.marigoldIcon` | icons on marigoldContainer / white (section tiles, benefit tiles, Pro strip) |
| `ext.card` | every SmCard, sheets, dialogs, calendar body, plan cards, unselected nodes |
| `ext.sunken` | FactStrip, DateChip (next), tonal button, SpecBox, count bubble, skeleton, settings icon tiles |
| `ext.hairline` / `outlineVariant` | dividers, bar top line, plan/segment/summary-pill rings, ad dashed border, upcoming node ring, dashed connector |
| `outline` | unchecked checkbox, text field border, radio/switch unselected (≥ 3:1 cues) |
| `ext.glass` | SmBottomBar, BottomActionBar |
| `ext.category.*` | CategoryTile (MatchCard, S21 header, TrackerCard, compact rows) |
| `shadow.*` | card, ctaPrimary, ctaMarigold, calendarTile, medallion, benefitIcon |
| `component.googleSignInButton` | K1 only |
