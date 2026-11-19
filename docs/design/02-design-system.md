# 02 — Design System

Sarkari Match · Material 3 for Jetpack Compose · machine-readable copy: [`tokens.json`](tokens.json)

## 1. Design principles (decide every trade-off with these)

1. **One glance = one decision.** Every alert answers three questions above the fold: *Am I eligible? By when? What do I do?* Then the actions.
2. **Trust before polish.** Official PDF link, "सरकारी ऐप नहीं" disclaimer, plain verdict words (योग्य / ख़ुद जाँचें / योग्य नहीं). No tricolour, Ashoka emblem, or anything that looks like a government site (impersonation risk + Play policy).
3. **Urgency is earned.** Red is reserved for "last date today" and destructive actions. Everything else is calm, so red still means something.
4. **Words + icon, never colour alone.** Every status = icon + Hindi word (+ ✓ / ? / ✗ glyph in rule lines).
5. **Simple Hindi (बोलचाल की हिंदी).** Use words people say: "फ़ॉर्म भर दिया", "आखिरी तारीख", "उम्र". Avoid Sanskritised words (e.g. "अंतिम तिथि" → "आखिरी तारीख", "आवेदन करें" → "फ़ॉर्म भरें", "अधिसूचना" → "नोटिस/सूचना"). Keep English exam names (SSC CGL, RRB NTPC, DV) as people say them.
6. **Ads stay out of the way.** Free tier shows one clearly labelled native ad ("विज्ञापन · Ad") only at the end of the feed/digest, on a grey dashed surface that can never be mistaken for a match card or an official notice (06 §J1).
7. **Light, fast, offline-tolerant.** Flat surfaces, vector icons, no images in lists, no custom fonts in the APK.

## 2. Colour

### 2.1 Rationale

- **Primary — "Bharosa blue" `#1F4FB8`.** Blue is the trust colour of Indian banking/UPI apps (BHIM, Paytm, SBI) that this audience already uses daily; deeper than the skill's suggested `#0369A1` sky-blue so white text on it passes 7.3:1 and it reads well on cheap, over-saturated LCDs.
- **Tertiary — saffron `#9A4A00` = Pro.** Warm, premium, culturally positive, and clearly *not* an urgency colour (urgency uses red-orange). Light saffron container `#FFE8CF` for Pro cards.
- **Status colours** are a separate extended palette (`SarkariColors`) so M3 components don't accidentally use them:
  - success green (eligible, applied, stage done) · amber (check-yourself, deadline 4–7 days, changes) · burnt-orange "urgent" (1–3 days) · red "today" (last date today, filled chip) · M3 error (not eligible, failures).
- Skill output (Flat Design, blue + green CTA) kept the *flat* style and blue/green semantics; **deviation**: green is not the CTA colour (green = "done/eligible"; a green CTA would blur the verdict colour). CTAs are primary blue.
- **Dark mode** uses desaturated tonal variants (skill rule `color-dark-mode`), not inversions; "today" becomes a light-red fill with dark text so it stays the loudest element without glowing.

### 2.2 Light scheme (M3 roles)

| Role | Hex | Role | Hex |
| --- | --- | --- | --- |
| primary | `#1F4FB8` | onPrimary | `#FFFFFF` |
| primaryContainer | `#DCE5FF` | onPrimaryContainer | `#0B2A6E` |
| secondary | `#4E5B78` | onSecondary | `#FFFFFF` |
| secondaryContainer | `#E1E6F3` | onSecondaryContainer | `#1A2438` |
| tertiary (Pro) | `#9A4A00` | onTertiary | `#FFFFFF` |
| tertiaryContainer | `#FFE8CF` | onTertiaryContainer | `#5C2C00` |
| error | `#B3261E` | onError | `#FFFFFF` |
| errorContainer | `#FCE3E0` | onErrorContainer | `#7A1410` |
| background / surface | `#F6F7FA` | onBackground / onSurface | `#151A23` |
| surfaceVariant | `#ECEFF5` | onSurfaceVariant | `#4A5263` |
| surfaceContainerLowest (cards) | `#FFFFFF` | surfaceContainerLow | `#F1F3F8` |
| surfaceContainer | `#ECEFF5` | surfaceContainerHigh | `#E6E9F0` |
| surfaceContainerHighest | `#E0E4EC` | outline | `#737B8C` |
| outlineVariant (dividers, card border) | `#D3D8E2` | inverseSurface / inverseOnSurface | `#2A2F38` / `#EFF1F6` |
| inversePrimary | `#AEC4FF` | scrim | `#000000` @ 50 % |

### 2.3 Dark scheme

| Role | Hex | Role | Hex |
| --- | --- | --- | --- |
| primary | `#AEC4FF` | onPrimary | `#0B2A6E` |
| primaryContainer | `#22408C` | onPrimaryContainer | `#DCE5FF` |
| secondary | `#BAC6E3` | onSecondary | `#24304A` |
| secondaryContainer | `#3A4660` | onSecondaryContainer | `#DCE3F7` |
| tertiary | `#FFB977` | onTertiary | `#4F2600` |
| tertiaryContainer | `#472500` | onTertiaryContainer | `#FFDDBC` |
| error | `#FFB4AB` | onError | `#690005` |
| errorContainer | `#5A1611` | onErrorContainer | `#FFDAD5` |
| background / surface | `#11141B` | onSurface | `#E5E8EF` |
| surfaceVariant | `#2D3340` | onSurfaceVariant | `#B5BCC9` |
| surfaceContainerLowest | `#0C0F14` | surfaceContainerLow (cards) | `#171B23` |
| surfaceContainer | `#1B2029` | surfaceContainerHigh | `#232833` |
| surfaceContainerHighest | `#2D3340` | outline | `#8C93A1` |
| outlineVariant | `#3E4552` | inverseSurface / inverseOnSurface | `#E5E8EF` / `#2A2F38` |
| inversePrimary | `#1F4FB8` | scrim | `#000000` @ 60 % |

Card surface: light `surfaceContainerLowest` (#FFF) on `#F6F7FA` + 1 dp `outlineVariant` border; dark `surfaceContainerLow` on `#11141B` + 1 dp `outlineVariant`.

### 2.4 Extended status palette (`SarkariColors`, provided via `staticCompositionLocalOf`)

| Token | Light | Dark | Used for |
| --- | --- | --- | --- |
| success / onSuccess | `#17713A` / `#FFFFFF` | `#7FD49A` / `#003919` | ✓ rule icons, "भर दिया" state, stage done |
| successContainer / onSuccessContainer | `#DDF2E3` / `#0B4A24` | `#123B22` / `#C9F0D4` | "आप योग्य हैं" verdict chip |
| warning / onWarning | `#8A5300` / `#FFFFFF` | `#F1C25A` / `#3F2D00` | ? rule icons, changed stage |
| warningContainer / onWarningContainer | `#FFEFC7` / `#5A3600` | `#3B2B06` / `#FFE3A3` | "ख़ुद जाँचें" chip, deadline 4–7 days, corrigendum banner |
| urgent / onUrgent | `#B93D0A` / `#FFFFFF` | `#FFB28C` / `#511E00` | deadline 1–3 days accent |
| urgentContainer / onUrgentContainer | `#FFE6D8` / `#6E2304` | `#4A1E07` / `#FFDCCB` | deadline 1–3 days chip & card stripe |
| today / onToday | `#B3261E` / `#FFFFFF` | `#FFB4AB` / `#410E0B` | **filled** "आज आखिरी दिन" chip, card top stripe |
| whatsappBrand | `#25D366` | `#25D366` | only inside the official WhatsApp glyph |

Semantic mapping (deadline, verdict, stage, Pro) is in `tokens.json → semantic`. Thresholds use **IST calendar days** between today and `lastDate` (inclusive of last date):
`today` (0) · `urgent` (1–3) · `soon` (4–7) · `calm` (>7) · `closed` (<0).

### 2.5 Contrast verification (WCAG 2.1, computed)

| Pair | Ratio | | Pair (dark) | Ratio |
| --- | --- | --- | --- | --- |
| primary on white | 7.32 | | primary on bg | 10.65 |
| onSurface on white | 17.44 | | onSurface on card | 14.06 |
| onSurfaceVariant on white / bg / surfaceVariant | 7.84 / 7.32 / 6.81 | | onSurfaceVariant on card / container | 9.04 / 8.13 |
| success / warning / error / urgent text on white | 6.07 / 6.33 / 6.54 / 5.62 | | same on card | 9.68 / 10.35 / 10.16 / 9.86 |
| onSuccessC / onWarningC / onErrorC / onUrgentC on their containers | 8.86 / 9.39 / 8.89 / 9.22 | | same | 10.09 / 10.93 / 10.43 / 11.05 |
| onToday on today | 6.54 | | onToday on today | 9.57 |
| pro text on white / onProC on proC | 6.26 / 9.75 | | | 10.23 / 10.65 |
| outline (UI boundary) on white | 4.25 (≥3 ✓) | | outline on card | 5.59 |

All text pairs ≥ 4.5:1 (most ≥ 6:1, chosen for sunlight readability on cheap screens). Disabled content uses onSurface @ 38 % (exempt per WCAG, but always paired with a reason text).

### 2.6 Compose implementation

```kotlin
// ui/theme/Color.kt — generated from tokens.json; never use hex in feature code.
val LightColors = lightColorScheme(
  primary = Color(0xFF1F4FB8), onPrimary = Color.White,
  primaryContainer = Color(0xFFDCE5FF), onPrimaryContainer = Color(0xFF0B2A6E),
  secondary = Color(0xFF4E5B78), onSecondary = Color.White,
  secondaryContainer = Color(0xFFE1E6F3), onSecondaryContainer = Color(0xFF1A2438),
  tertiary = Color(0xFF9A4A00), onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFFE8CF), onTertiaryContainer = Color(0xFF5C2C00),
  error = Color(0xFFB3261E), onError = Color.White,
  errorContainer = Color(0xFFFCE3E0), onErrorContainer = Color(0xFF7A1410),
  background = Color(0xFFF6F7FA), onBackground = Color(0xFF151A23),
  surface = Color(0xFFF6F7FA), onSurface = Color(0xFF151A23),
  surfaceVariant = Color(0xFFECEFF5), onSurfaceVariant = Color(0xFF4A5263),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F3F8),
  surfaceContainer = Color(0xFFECEFF5), surfaceContainerHigh = Color(0xFFE6E9F0),
  surfaceContainerHighest = Color(0xFFE0E4EC),
  outline = Color(0xFF737B8C), outlineVariant = Color(0xFFD3D8E2),
  inverseSurface = Color(0xFF2A2F38), inverseOnSurface = Color(0xFFEFF1F6),
  inversePrimary = Color(0xFFAEC4FF), scrim = Color.Black,
)
// DarkColors analogous (table 2.3).

@Immutable data class SarkariColors(
  val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
  val warning: Color, val onWarning: Color, val warningContainer: Color, val onWarningContainer: Color,
  val urgent: Color, val onUrgent: Color, val urgentContainer: Color, val onUrgentContainer: Color,
  val today: Color, val onToday: Color,
)
val LocalSarkariColors = staticCompositionLocalOf { LightSarkari }
object SarkariTheme { val colors @Composable get() = LocalSarkariColors.current }
```

- **Dynamic colour (Material You) is OFF.** Status semantics and brand recognition must be identical on every phone.
- Theme follows system by default; S26 → "थीम" row offers System / Light / Dark (stored in DataStore).
- Status bar & nav bar: edge-to-edge (`enableEdgeToEdge()`), icons dark in light theme.

## 3. Typography

### 3.1 Font choice

| Option considered | Verdict |
| --- | --- |
| Skill output: EB Garamond / Lato (and earlier Playfair / Inter) | ✗ No Devanagari; serif display is wrong for a utility app |
| Skill google-fonts search "devanagari hindi" → Tiro Devanagari, Kadwa, Sahitya… | ✗ All serif; poor at 14 sp on low-dpi screens |
| Skill google-fonts "devanagari sans" → **Noto Sans Devanagari**, IBM Plex Sans Devanagari, Anek Devanagari; plus CSV rows Hind, Mukta, Poppins | Shortlist |
| **Mukta** (Ek Type, OFL, 7 weights, Devanagari + Latin designed together) | ✓ **Chosen.** Open apertures and generous x-height at small sizes; Latin is drawn to sit with Devanagari (same stroke, same weight steps), so mixed lines like "SSC CGL की आखिरी तारीख 24 अगस्त" don't jump; widely used in Hindi news apps → familiar |
| Noto Sans Devanagari | ✓ **Fallback.** Already on every Android 8+ phone as the system Devanagari font → zero bytes |
| Hind | Good, but Latin is narrower/lighter than its Devanagari at the same weight |

**Delivery:** Mukta via **Downloadable Fonts** (`androidx.compose.ui.text.googlefonts.GoogleFont`, provider `com.google.android.gms.fonts`) — 0 KB added to the APK (NFR < 15 MB). Until downloaded / if GMS absent, `FontFamily` falls back to `FontFamily.Default` (Roboto + system Noto Sans Devanagari); metrics are close enough that layouts are tested with both. Weights used: 400, 500, 600, 700 only.

### 3.2 Type scale (M3 roles; sp; Devanagari-safe line heights ≈ 1.5×)

| M3 role | Size / line height (sp) | Weight | Use |
| --- | --- | --- | --- |
| displaySmall | 32 / 44 | 700 | "7" in "आप 7 नौकरियों के योग्य हैं" |
| headlineMedium | 26 / 36 | 600 | Result screens (S31, S10 headline) |
| headlineSmall | 22 / 32 | 600 | Onboarding question title, S21 job title |
| titleLarge | 20 / 30 | 600 | Top app bar, sheet titles |
| titleMedium | 18 / 27 | 600 | Job title in MatchCard |
| titleSmall | 16 / 24 | 600 | Section headers, card section labels |
| bodyLarge | 17 / 27 | 400 | Onboarding explanation text, sheet body |
| bodyMedium | 16 / 25 | 400 | **Default body** (never smaller for sentences) |
| bodySmall | 14 / 22 | 400 | Meta lines (department, "3 घंटे पहले") |
| labelLarge | 16 / 22 | 600 | Buttons |
| labelMedium | 14 / 20 | 500 | Chips, nav labels, tabs |
| labelSmall | 13 / 18 | 500 | Badges only. **Floor: 13 sp** |

Rules:
- Devanagari matras (ि ी ु ू े ै ँ ं) need vertical room. In `Typography` set for every style:
  `platformStyle = PlatformTextStyle(includeFontPadding = false)` **and**
  `lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)`.
  Never use `Trim.Both` and never set a fixed `height` on a text container — use `heightIn(min = …)`.
- Letter-spacing 0 for all Hindi text (M3 default tracking of 0.1–0.5 sp on labels must be overridden to 0; positive tracking breaks conjunct shaping visually).
- No ALL-CAPS (meaningless in Devanagari, ugly in mixed lines). Buttons are sentence case.
- Numbers: Western digits (0–9) in both languages — aspirants read official notices with Western digits. Dates: `24 अगस्त 2026` (HI) / `24 Aug 2026` (EN); short `24/08` only in compact chips. Money: `₹100` no decimals. Time: `सुबह 9 बजे` / `9:00 am`.
- **Font scaling to 200 %:** Android 14 non-linear scaling; all text in `sp`; containers grow vertically; buttons wrap to 2 lines (`maxLines = 2`) then stack vertically when `LocalDensity.current.fontScale >= 1.5f`. Test previews at 1.0, 1.3, 2.0.
- Line length: body max ~ 60 characters → on medium/expanded widths constrain content to `widthIn(max = 600.dp)` centred.

```kotlin
val Mukta = FontFamily(
  Font(GoogleFont("Mukta"), provider, FontWeight.Normal),
  Font(GoogleFont("Mukta"), provider, FontWeight.Medium),
  Font(GoogleFont("Mukta"), provider, FontWeight.SemiBold),
  Font(GoogleFont("Mukta"), provider, FontWeight.Bold),
)
private val hi = TextStyle(
  fontFamily = Mukta, letterSpacing = 0.sp,
  platformStyle = PlatformTextStyle(includeFontPadding = false),
  lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
val AppTypography = Typography(
  displaySmall = hi.copy(fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
  headlineMedium = hi.copy(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
  headlineSmall = hi.copy(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
  titleLarge = hi.copy(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
  titleMedium = hi.copy(fontSize = 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
  titleSmall = hi.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
  bodyLarge = hi.copy(fontSize = 17.sp, lineHeight = 27.sp),
  bodyMedium = hi.copy(fontSize = 16.sp, lineHeight = 25.sp),
  bodySmall = hi.copy(fontSize = 14.sp, lineHeight = 22.sp),
  labelLarge = hi.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
  labelMedium = hi.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
  labelSmall = hi.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)
```

## 4. Spacing, layout, shape, elevation

**Spacing (4/8 dp rhythm):** 2 · 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48.
- Screen gutter 16 dp (compact), 24 dp (medium); content max width 600 dp.
- Card padding 16 dp; gap between cards 12 dp; between sections 24 dp; inside a card, between blocks 12 dp, label→value 4 dp.
- Minimum 8 dp between adjacent touch targets.

**Sizes:** touch target ≥ 48 × 48 dp (icon buttons use `IconButton` 48 dp; small chips get `minimumInteractiveComponentSize()`); primary button height 52 dp; text field 56 dp; top app bar 64 dp; bottom nav 80 dp; sticky bottom action bar 84 dp + nav-bar inset.

**Shape (M3 `Shapes`):** extraSmall 4 dp (badges) · small 8 dp (chips, text fields) · medium 12 dp (cards, banners) · large 16 dp (selectable tiles, dialogs content cards) · extraLarge 28 dp (bottom sheet top corners, dialogs) · full (buttons are **full/pill**, M3 default `ButtonDefaults.shape`).

**Elevation:** Flat style (skill recommendation kept). Cards elevation 0 + 1 dp `outlineVariant` border. Tonal elevation only for: bottom nav (level 2 = 3 dp), sticky bottom action bar (3 dp + 1 dp top divider), sheets (1 dp), dialogs (6 dp), snackbar (6 dp). No drop shadows elsewhere.

**Z-order:** content 0 · sticky headers 1 · banners 2 · bottom bars 3 · FAB (none) · sheets/dialogs (system window) · snackbar host above bottom bar.

## 5. Iconography

- **Set:** Google **Material Symbols Rounded** (Apache 2.0) — native to M3, has every glyph needed incl. `workspace_premium`, `assignment_turned_in`, `battery_saver`. Weight 400, grade 0, optical size 24, **fill 0** (outlined) by default; **fill 1** only for: selected bottom-nav tab, status icons inside verdict/deadline chips. Rounded matches pill buttons and friendly tone.
- **Delivery:** do **not** add `material-icons-extended` (multi-MB). Import only the ~60 glyphs below as vector drawables (`res/drawable/ic_*.xml`) with a Gradle task or Android Studio Vector Asset; reference with `ImageVector.vectorResource`.
- **Sizes:** 16 (inline in rule lines), 20 (chips/meta), 24 (default, buttons, top bar), 32 (empty state small), 48 (permission/result icons). Illustrations: flat 2-colour vector (primary + primaryContainer) 120 dp, built from the same icon shapes — no raster.
- **Brand logos:** WhatsApp glyph from Meta's official brand resources only (green on white, not recoloured), only on WhatsApp entry points. Play, UPI app logos only inside system choosers (we don't draw them).
- **No emoji as icons** anywhere in the app UI. (Emoji are allowed *inside* WhatsApp/push text where they are content, see 05.)
- Accessibility: icons beside visible text → `contentDescription = null`; standalone meaningful icons → `contentDescription` in the current language; icon buttons → label via `contentDescription`, and `Modifier.semantics { stateDescription = … }` for toggles.

| Meaning | Symbol | Meaning | Symbol |
| --- | --- | --- | --- |
| Jobs tab | `work` | My forms tab | `assignment_turned_in` |
| Profile tab | `account_circle` | Alert history | `notifications` |
| Help | `help` (top bar) | Back | `arrow_back` (auto-mirrors) |
| Eligible ✓ | `check_circle` | Check yourself ? | `help` (filled, in chips) |
| Not eligible ✗ | `cancel` | Last date | `event` / `event_busy` |
| Today | `alarm` | 1–3 days | `timer` |
| 4–7 days | `schedule` | Applied (action) | `task_alt` |
| Not interested | `visibility_off` | Snooze | `snooze` |
| Official PDF | `picture_as_pdf` | Apply link | `open_in_new` |
| Fee | `currency_rupee` | Documents | `description` |
| Photo spec | `photo_camera` | Signature spec | `draw` |
| Age | `cake` | Qualification | `school` |
| Category | `groups` | State/domicile | `location_on` |
| Gender | `wc` | PwD | `accessible` |
| Ex-servicemen | `military_tech` | Report wrong | `flag` |
| Corrigendum/changed | `update` | Extended | `more_time` |
| Cancelled notice | `block` | Pro | `workspace_premium` |
| WhatsApp | official glyph | Family | `family_restroom` |
| Clash | `event_repeat` | Certificate | `verified` |
| Admit card | `badge` | City slip | `location_city` |
| Exam | `edit_note` | Answer key | `key` |
| Result | `emoji_events` | Cut-off | `leaderboard` |
| DV / medical | `fact_check` / `medical_services` | Battery | `battery_saver` |
| Autostart | `restart_alt` | Notification permission | `notifications_active` |
| Offline | `cloud_off` | Server problem | `report` |
| Lock (Pro feature) | `lock` | Settings | `tune` |
| Language | `translate` | Logout | `logout` |
| Delete | `delete` | Download data | `download` |
| Share | `share` | Phone | `smartphone` |
| Quiet hours | `bedtime` | Digest | `summarize` |
| Refresh/test | `sync` | Success big | `check_circle` (48) |
| Close sheet | `close` | Copy | `content_copy` |

## 6. Motion

Tokens (ms): instant 0 · short1 50 · short2 100 · short3 150 · short4 200 · medium1 250 · medium2 300 · medium4 400 · long2 500.
Easing: standard / emphasized `(0.2, 0, 0, 1)`, emphasizedDecelerate `(0.05, 0.7, 0.1, 1)` for entering, emphasizedAccelerate `(0.3, 0, 0.8, 0.15)` for exiting (exit ≈ 65 % of enter, skill rule `exit-faster-than-enter`).

| Interaction | Spec |
| --- | --- |
| Press | M3 ripple (≤ 100 ms feedback). No scale on cards (avoids jitter on low-end GPUs) |
| Forward navigation | `fadeIn(300) + slideInHorizontally { it / 12 }` emphasizedDecelerate; back = reverse 200 ms. Predictive back enabled (`android:enableOnBackInvokedCallback="true"`) |
| Bottom-tab switch | crossfade 150 ms, no slide |
| Bottom sheet | M3 default (enter 300 decel / exit 200 accel) |
| Card leaves list after Applied/Not interested | `Modifier.animateItem()` (fade 150 + placement 250) **after** the snackbar is shown |
| Verdict/deadline chip colour change | `animateColorAsState(150)` |
| Success (Applied, purchase) | check icon scale 0.8→1.0 over 200 ms + `HapticFeedbackType.Confirm` (API 30+) else `LongPress` |
| Skeleton | shimmer 1200 ms linear loop |
| Reduced motion | if `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` or accessibility "Remove animations": all durations → 0, shimmer → static `surfaceContainerHigh`, no haptics changes |

Maximum 1–2 animated elements per screen (skill rule `excessive-motion`). No animation blocks input.

## 7. Content & voice

- **Voice:** a helpful elder sibling (बड़ा भाई/बहन) — short, warm, direct. Use "आप". Never shout: no "!!!", no "जल्दी करो!!". Urgency comes from facts: "आज रात 11:59 बजे तक".
- **Verdict words (fixed):** योग्य (Eligible) · ख़ुद जाँचें (Check yourself) · योग्य नहीं (Not eligible). Never "शायद".
- **Action words (fixed):** फ़ॉर्म भर दिया (Applied) · रुचि नहीं (Not interested) · बाद में याद दिलाएँ (Remind later) · PDF देखें · फ़ॉर्म भरने की साइट खोलें (Open apply site).
- **Nukta:** use ज़/फ़ consistently (फ़ॉर्म, ज़रूरी, फ़ीस). Chandrabindu where natural (नौकरियाँ, जाँचें).
- **Disclaimer (fixed string):** "Sarkari Match एक निजी ऐप है, कोई सरकारी ऐप नहीं। हमेशा आधिकारिक नोटिस से मिलान करें।" / "Sarkari Match is a private app, not a government app. Always verify with the official notice."
- String resources: `values/strings.xml` = Hindi (default locale is Hindi by product decision, but the system default resource must be safe → we put Hindi in `values-hi` and English in `values`, and set app locale to `hi` on first run via `AppCompatDelegate.setApplicationLocales`; `locales_config.xml` lists `hi`, `en`). Plurals via `<plurals>` (Hindi has one/other).

## 8. Accessibility baseline (applies to every screen)

- Contrast per §2.5. Touch ≥ 48 dp. Focus order = visual order (use `Modifier.semantics { traversalIndex }` only if layout forces otherwise).
- Every card is one TalkBack stop with a merged description (`Modifier.semantics(mergeDescendants = true)`) plus **custom actions** for its buttons (`customActions = listOf(CustomAccessibilityAction("फ़ॉर्म भर दिया") {…}, …)`), so a TalkBack user can act without hunting for small targets.
- Headings: `Modifier.semantics { heading() }` on section headers and screen titles.
- Live updates: countdown chips are *not* live regions; result banners/snackbars are polite.
- 200 % font scale and display size "largest" must not clip; tested via `@PreviewFontScale` and `@PreviewScreenSizes`.
- Low literacy: every action button has icon + word; important numbers are big; avoid tables in UI (use label/value rows); offer "सुनें" (TTS) on S21 summary — **P1**, uses Android `TextToSpeech` with `hi-IN`.
