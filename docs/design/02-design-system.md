# 02 — Design System · Direction C "Marigold Home"

Sarkari Match · Material 3 for Jetpack Compose · machine-readable copy: [`tokens.json`](tokens.json) (v2.0) · visual source of truth: [`directions/direction-c.html`](directions/direction-c.html) and the shared CSS [`v2/marigold.css`](v2/marigold.css) · current screens: [`v2/`](v2/)

**2026-11-19 — Direction C adopted.** The founder picked "C · Marigold Home" (see [directions/README.md](directions/README.md)). This file replaces the v1.1 "Bharosa blue / Mukta / flat" system. Token **names** are unchanged where the meaning is the same, so Android code that maps names keeps compiling; values, fonts, radii and the elevation model changed, and new tokens were added (§2.4). Recommendation borrowings from the directions review are kept: **B's tabular countdown figures** and **A's restraint — at most one marigold surface per screen**.

## 1. Design principles (decide every trade-off with these)

1. **One glance = one decision.** Every alert answers *Am I eligible? By when? What do I do?* above the fold, then the actions.
2. **A trusted elder sibling (बड़ा भाई/बहन).** Warm cream paper, forest-green actions, marigold highlights, big icons beside every word. Warm, never festive: the feed must not look like a wedding card.
3. **Trust before polish.** Official PDF link, "सरकारी ऐप नहीं" disclaimer, plain verdict words. No tricolour, Ashoka emblem or anything that looks like a government site.
4. **Urgency is earned.** Terracotta is reserved for deadlines ≤ 3 days, "not eligible" and destructive actions. The *filled* terracotta ribbon appears only for "last day today".
5. **Words + icon, never colour alone.** Every status = icon + word. Category tiles and ribbons help recognition without reading, but always sit next to text.
6. **Ads stay out of the way.** One labelled native ad at the end of the feed, on a dashed transparent surface that can never be mistaken for a match card (06 §J1).
7. **Light, fast, offline-tolerant.** Vector icons, no images in lists, fonts downloaded (0 KB in the APK), shadows drawn by the GPU only on cards and CTAs.
8. **Every language is a first-class layout.** Hindi + English ship first; layouts survive Tamil/Malayalam length and Urdu RTL without redesign ([07](07-localization.md)).
9. **Ask for as little as possible.** No phone, Aadhaar, documents, contacts or location; the profile stays on the phone (S47).

## 2. Colour

### 2.1 Palette and roles (60 / 30 / 10)

| Share | Colour | Light | Dark | Role |
| --- | --- | --- | --- | --- |
| **60** | Cream | `#FBF5EC` | `#17120D` | Screen background (`background`, `surface`) |
| **30** | White card | `#FFFFFF` | `#231C15` | Cards, sheets, calendar body (`ext.card`) |
| **30** | Cocoa text | `#2A1E14` | `#F6EDE1` | All text and on-surface icons (`onSurface`, `ext.cocoa`) |
| 30 | Cocoa muted | `#6E5D4E` | `#C2B19E` | Meta text, inactive icons (`onSurfaceVariant`) |
| 30 | Sunken | `#F5ECDF` | `#2D241B` | Fact strip, tonal button, date chips, count bubble (`ext.sunken` = `surfaceContainerHigh`) |
| 30 | Hairline | `#EFE4D4` | `#3A2F24` | Dividers, unselected borders, ad dashed border (`outlineVariant`, `ext.hairline`) |
| **10** | Forest green | `#1E5B46` (container `#E2F0E8`) | `#86D4B2` (container `#1D3329`) | Primary CTA, links, selected plan, done stages **and** the "eligible" verdict (`primary`, `ext.success`) |
| **10** | Marigold | `#F2A93B` (container `#FDEFD6`) | `#F4B755` (container `#3A2A12`) | Pro, nav pill, "next" stage, greeting glow, confetti (`tertiary`, `ext.marigold`) |
| 10 | Terracotta | `#B3361E` (container `#FBE4DC`, on-container `#8A2A15`) | `#FF8A6B` (`#3E2018` / `#FFC9B8`) | Urgency ribbons and chips, calendar band, badges, not-eligible (`ext.today`, `ext.urgent*`, `error`) |
| 10 | Ochre | `#8A5A00` on `#F8EDD4` | `#EBC46E` on `#362A12` | "Check yourself ?", changed/corrigendum, deadline 4–7 days (`ext.warning*`) |

**Rules of use**

- **One marigold surface per screen** (nav pill excluded): the greeting card *or* the Pro upsell strip *or* the Pro card. Marigold is a *fill* colour; on light surfaces it is never used for text (2.0:1). Marigold text/icons use `onTertiaryContainer` `#8A5200` / `ext.marigoldIcon` `#B06E00`.
- **Green = act and eligible.** They are kept apart by **shape**: the CTA is a *filled* pill, the eligible chip is a *tinted* pill with a `check_circle`. Never put a filled green chip next to a filled green button.
- **Terracotta filled = today only.** Ribbons for 1–3 days are *tinted* (`urgentContainer`); the filled ribbon, calendar band and nav badge are the only solid terracotta.
- **Dark mode** uses the warm darks above (not inverted lights). Text on terracotta and marigold fills in dark mode is **dark** (`#2A1205`, `#2A1A02`), never white — the prototype's white nav badge in dark mode is corrected to `onToday`.
- **Dynamic colour (Material You) is OFF.** Theme follows system; S26 "थीम" offers System / Light / Dark (DataStore). Light is the designed default (sunlight, IPS-LCD screens).

### 2.2 Light scheme (M3 roles)

| Role | Hex | Role | Hex |
| --- | --- | --- | --- |
| primary | `#1E5B46` | onPrimary | `#FFFFFF` |
| primaryContainer | `#E2F0E8` | onPrimaryContainer | `#1E5B46` |
| secondary | `#5E4D3F` | onSecondary | `#FFFFFF` |
| secondaryContainer (tonal button) | `#F5ECDF` | onSecondaryContainer | `#2A1E14` |
| tertiary (Pro, **fill only**) | `#F2A93B` | onTertiary | `#3A2405` |
| tertiaryContainer | `#FDEFD6` | onTertiaryContainer | `#8A5200` |
| error | `#B3361E` | onError | `#FFFFFF` |
| errorContainer | `#FBE4DC` | onErrorContainer | `#8A2A15` |
| background / surface | `#FBF5EC` | onBackground / onSurface | `#2A1E14` |
| surfaceVariant | `#F5ECDF` | onSurfaceVariant | `#6E5D4E` |
| surfaceContainerLowest (cards) | `#FFFFFF` | surfaceContainerLow (sheets) | `#FDF9F3` |
| surfaceContainer | `#F8F0E5` | surfaceContainerHigh (sunken) | `#F5ECDF` |
| surfaceContainerHighest | `#EFE4D4` | outline (≥ 3:1 boundaries) | `#8C7A68` |
| outlineVariant (hairline) | `#EFE4D4` | inverseSurface / inverseOnSurface | `#3A2F24` / `#F6EDE1` |
| inversePrimary | `#86D4B2` | scrim | `#000000` @ 50 % |

### 2.3 Dark scheme

| Role | Hex | Role | Hex |
| --- | --- | --- | --- |
| primary | `#86D4B2` | onPrimary | `#0D2A1F` |
| primaryContainer | `#1D3329` | onPrimaryContainer | `#86D4B2` |
| secondary | `#D6C5B2` | onSecondary | `#2A1E14` |
| secondaryContainer | `#2D241B` | onSecondaryContainer | `#F6EDE1` |
| tertiary | `#F4B755` | onTertiary | `#2A1A02` |
| tertiaryContainer | `#3A2A12` | onTertiaryContainer | `#F4B755` |
| error | `#FF8A6B` | onError | `#2A1205` |
| errorContainer | `#3E2018` | onErrorContainer | `#FFC9B8` |
| background / surface | `#17120D` | onSurface | `#F6EDE1` |
| surfaceVariant | `#2D241B` | onSurfaceVariant | `#C2B19E` |
| surfaceContainerLowest | `#120E0A` | surfaceContainerLow (cards) | `#231C15` |
| surfaceContainer | `#282018` | surfaceContainerHigh (sunken) | `#2D241B` |
| surfaceContainerHighest | `#3A2F24` | outline | `#9A8875` |
| outlineVariant | `#3A2F24` | inverseSurface / inverseOnSurface | `#F6EDE1` / `#2A1E14` |
| inversePrimary | `#1E5B46` | scrim | `#000000` @ 60 % |

Card surface: always `SarkariTheme.colors.card` (light `#FFFFFF` = `surfaceContainerLowest`, dark `#231C15` = `surfaceContainerLow`), **no border**, soft shadow (§5.3).

### 2.4 Extended palette (`SarkariColors`, via `staticCompositionLocalOf`)

Names kept from v1.1: `success*`, `warning*`, `urgent*`, `today/onToday`. **New in v2:** `marigold*`, `marigoldIcon`, `terracotta*`, `cocoa`, `cocoaMuted`, `card`, `sunken`, `hairline`, `glass`, `shadowTint`, `category.*`.

| Token | Light | Dark | Used for |
| --- | --- | --- | --- |
| success / onSuccess | `#1E5B46` / `#FFFFFF` | `#86D4B2` / `#0D2A1F` | ✓ rule bubbles, stage done, "भर दिया" |
| successContainer / onSuccessContainer | `#E2F0E8` / `#1E5B46` | `#1D3329` / `#86D4B2` | "योग्य" verdict chip, ✓ bubble |
| warning / onWarning | `#8A5A00` / `#FFFFFF` | `#EBC46E` / `#2A1A02` | ? icons, changed stage |
| warningContainer / onWarningContainer | `#F8EDD4` / `#8A5A00` | `#362A12` / `#EBC46E` | "ख़ुद जाँचें" chip, change strip, deadline 4–7 days |
| urgent / onUrgent | `#B3361E` / `#FFFFFF` | `#FF8A6B` / `#2A1205` | urgent section header icon/text |
| urgentContainer / onUrgentContainer | `#FBE4DC` / `#8A2A15` | `#3E2018` / `#FFC9B8` | **tinted ribbon** (1–3 days), "2 दिन बाकी" chip |
| today / onToday | `#B3361E` / `#FFFFFF` | `#FF8A6B` / `#2A1205` | **filled ribbon** (today), calendar band, nav badge, key date chip |
| marigold / onMarigold | `#F2A93B` / `#3A2405` | `#F4B755` / `#2A1A02` | nav pill, "next" stage node, Pro chip, marigold CTA |
| marigoldContainer / onMarigoldContainer | `#FDEFD6` / `#8A5200` | `#3A2A12` / `#F4B755` | Pro strip, lock pill, section icon tiles, greeting gradient start |
| marigoldIcon | `#B06E00` | `#F4B755` | icons on marigoldContainer or white (3.64 / 4.14:1; icons only) |
| terracotta (+ on/container/onContainer) | = urgent / today | = urgent / today | alias for non-deadline uses (confetti, illustration) |
| cocoa / cocoaMuted | `#2A1E14` / `#6E5D4E` | `#F6EDE1` / `#C2B19E` | = onSurface / onSurfaceVariant (illustrations) |
| card | `#FFFFFF` | `#231C15` | every card |
| sunken | `#F5ECDF` | `#2D241B` | fact chips, date chips, tonal button, spec boxes |
| hairline | `#EFE4D4` | `#3A2F24` | dividers, bar top line (decorative only) |
| glass | background @ 90 % | background @ 90 % | bottom nav, sticky action bar |
| shadowTint | `#78460A` | `#000000` | shadow colour (§5.3) |

**Category tiles** (`ext.category.*`; icon from `semantic.category`):

| Category | Icon | Light container / icon | Dark container / icon |
| --- | --- | --- | --- |
| Police | `local_police` | `#FDEBD3` / `#9A5B00` | `#3A2A14` / `#F4B755` |
| SSC / central | `account_balance` | `#E2F0E8` / `#1E5B46` | `#1D3329` / `#86D4B2` |
| Railway | `train` | `#FBE4DC` / `#A23A1E` | `#3E2018` / `#FF9F85` |
| Bank | `savings` | `#E1EEF2` / `#1D5E73` | `#16303A` / `#8CCFE4` |
| State / other | `location_city` | `#F1E4F2` / `#7A3D80` | `#33203A` / `#E3A8EA` |

The only third-party brand colours remain those of the official **Sign in with Google** button (`tokens.json → component.googleSignInButton`, not recoloured, outside `SarkariColors`). `whatsappBrand` stays removed.

### 2.5 Semantic mapping

`tokens.json → semantic` maps product states to the roles above (unchanged names). Thresholds use **IST calendar days** to `lastDate`: `today` (0) · `urgent` (1–3) · `soon` (4–7) · `calm` (> 7) · `closed` (< 0).

| State | Card treatment | Chip / icon |
| --- | --- | --- |
| deadline.today | **filled ribbon** `today/onToday`, `alarm`, "आज आखिरी दिन · रात 11 बजे तक" | calendar band, section header in `urgent` |
| deadline.urgent | **tinted ribbon** `urgentContainer/onUrgentContainer`, `timer`, "सिर्फ़ 2 दिन बाकी · 21 नवं तक" | `timer` chip tinted |
| deadline.soon | no ribbon | `schedule` chip `warningContainer` |
| deadline.calm | no ribbon | `event` chip `sunken` / `onSurface` |
| deadline.closed | no ribbon, card at 60 % opacity in lists | `event_busy` chip `surfaceContainerHighest` |
| verdict.eligible / checkYourself / notEligible | — | tinted pill: `successContainer` `check_circle` · `warningContainer` `help` · `errorContainer` `cancel` |
| stage.done / current(next) / upcoming / locked | journey path (06 §D2) | node `primary`+`check` · `marigold` + 4 dp `marigoldContainer` halo · `card` + 2 dp hairline ring · same + "Pro" lock pill |
| pro | Pro card / strip | fill `tertiary`, container `tertiaryContainer`, icons `marigoldIcon` |

### 2.6 Contrast verification (WCAG 2.1, computed from tokens.json)

All **text** pairs ≥ 4.5:1; icon-only and UI-boundary pairs ≥ 3:1.

| Pair | Light | Dark |
| --- | --- | --- |
| onSurface on background / card / sunken | 14.97 / 16.23 / 13.87 | 16.05 / 14.51 / 13.13 |
| onSurfaceVariant on background / card / sunken | 5.80 / 6.29 / 5.38 | 8.93 / 8.07 / 7.30 |
| onSurfaceVariant on marigoldContainer (Pro strip meta) | 5.54 | 6.63 |
| primary (links) on background / card | 7.34 / 7.95 | 10.70 / 9.67 |
| onPrimary on primary (CTA) | 7.95 | 8.83 |
| onPrimaryContainer on primaryContainer (eligible chip) | 6.77 | 7.75 |
| primary on sunken | 6.80 | — |
| onSecondaryContainer on secondaryContainer (tonal button) | 13.87 | 13.13 |
| onSecondary on secondary | 8.05 | 9.65 |
| onTertiary on tertiary (marigold button, nav pill icon) | 7.33 | 9.42 |
| onTertiaryContainer on tertiaryContainer (lock pill, Pro text) | 5.63 | 7.73 |
| onSurface on marigoldContainer (Pro strip text) | 14.29 | 11.92 |
| onError on error / onErrorContainer on errorContainer | 6.06 / 7.11 | 7.67 / 10.04 |
| error / urgent text on card | 6.06 | 7.29 |
| urgent text on background (section header) | 5.59 | 8.06 |
| onUrgentContainer on urgentContainer (tinted ribbon) | 7.11 | 10.04 |
| onToday on today (filled ribbon, calendar band, badge) | 6.06 | 7.67 |
| success text on card | 7.95 | 9.67 |
| warning text on card / onWarningContainer on warningContainer | 5.93 / 5.10 | 10.14 / 8.45 |
| onMarigold on marigold | 7.33 | 9.42 |
| inverseOnSurface on inverseSurface (snackbar) | 11.24 | 14.00 |
| *Icons:* marigoldIcon on marigoldContainer / on card | 3.64 / 4.14 | 7.73 / 9.41 |
| *Icons:* category icon on tile — police / ssc / railway / bank / state | 4.65 / 6.77 / 5.45 / 6.11 / 6.10 | 7.72 / 7.75 / 7.41 / 8.01 / 7.85 |
| *Boundary:* outline on background / card | 3.80 / 4.12 | 5.45 / 4.93 |

- Lowest text pair: **5.10:1** (ochre on its container, light). Everything a user must read in sunlight is ≥ 5:1.
- **Hairline (`#EFE4D4`) is decorative only** (≈ 1.1:1). Where a border is the *only* cue, use `outline`: unchecked checkboxes and text fields use `outline` (the prototype's hairline checkbox ring is corrected); plan cards and stage nodes also have a check icon / fill change, so hairline is fine there.
- Disabled content: onSurface @ 38 % (exempt), always with a reason text.
- `marigold` as text on cream/white is **forbidden** (2.0:1). Lint: `tertiary` may only be used as a container/fill colour in feature code.

### 2.7 Compose — `Color.kt`

```kotlin
// :core:designsystem/theme/Color.kt — values from tokens.json (TokensParityTest). Never use hex in :feature:*.
val LightColors = lightColorScheme(
  primary = Color(0xFF1E5B46), onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFE2F0E8), onPrimaryContainer = Color(0xFF1E5B46),
  secondary = Color(0xFF5E4D3F), onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFF5ECDF), onSecondaryContainer = Color(0xFF2A1E14),
  tertiary = Color(0xFFF2A93B), onTertiary = Color(0xFF3A2405),
  tertiaryContainer = Color(0xFFFDEFD6), onTertiaryContainer = Color(0xFF8A5200),
  error = Color(0xFFB3361E), onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFBE4DC), onErrorContainer = Color(0xFF8A2A15),
  background = Color(0xFFFBF5EC), onBackground = Color(0xFF2A1E14),
  surface = Color(0xFFFBF5EC), onSurface = Color(0xFF2A1E14),
  surfaceVariant = Color(0xFFF5ECDF), onSurfaceVariant = Color(0xFF6E5D4E),
  surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFDF9F3),
  surfaceContainer = Color(0xFFF8F0E5), surfaceContainerHigh = Color(0xFFF5ECDF),
  surfaceContainerHighest = Color(0xFFEFE4D4),
  surfaceTint = Color.Transparent,                       // no tonal tint: cream must stay cream
  outline = Color(0xFF8C7A68), outlineVariant = Color(0xFFEFE4D4),
  inverseSurface = Color(0xFF3A2F24), inverseOnSurface = Color(0xFFF6EDE1),
  inversePrimary = Color(0xFF86D4B2), scrim = Color(0xFF000000),
)
val DarkColors = darkColorScheme(
  primary = Color(0xFF86D4B2), onPrimary = Color(0xFF0D2A1F),
  primaryContainer = Color(0xFF1D3329), onPrimaryContainer = Color(0xFF86D4B2),
  secondary = Color(0xFFD6C5B2), onSecondary = Color(0xFF2A1E14),
  secondaryContainer = Color(0xFF2D241B), onSecondaryContainer = Color(0xFFF6EDE1),
  tertiary = Color(0xFFF4B755), onTertiary = Color(0xFF2A1A02),
  tertiaryContainer = Color(0xFF3A2A12), onTertiaryContainer = Color(0xFFF4B755),
  error = Color(0xFFFF8A6B), onError = Color(0xFF2A1205),
  errorContainer = Color(0xFF3E2018), onErrorContainer = Color(0xFFFFC9B8),
  background = Color(0xFF17120D), onBackground = Color(0xFFF6EDE1),
  surface = Color(0xFF17120D), onSurface = Color(0xFFF6EDE1),
  surfaceVariant = Color(0xFF2D241B), onSurfaceVariant = Color(0xFFC2B19E),
  surfaceContainerLowest = Color(0xFF120E0A), surfaceContainerLow = Color(0xFF231C15),
  surfaceContainer = Color(0xFF282018), surfaceContainerHigh = Color(0xFF2D241B),
  surfaceContainerHighest = Color(0xFF3A2F24),
  surfaceTint = Color.Transparent,
  outline = Color(0xFF9A8875), outlineVariant = Color(0xFF3A2F24),
  inverseSurface = Color(0xFFF6EDE1), inverseOnSurface = Color(0xFF2A1E14),
  inversePrimary = Color(0xFF1E5B46), scrim = Color(0xFF000000),
)

@Immutable data class CategoryColors(val container: Color, val icon: Color)

@Immutable data class SarkariColors(
  // v1.1 names (unchanged)
  val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
  val warning: Color, val onWarning: Color, val warningContainer: Color, val onWarningContainer: Color,
  val urgent: Color, val onUrgent: Color, val urgentContainer: Color, val onUrgentContainer: Color,
  val today: Color, val onToday: Color,
  // v2 (Marigold Home)
  val marigold: Color, val onMarigold: Color, val marigoldContainer: Color, val onMarigoldContainer: Color,
  val marigoldIcon: Color,
  val card: Color, val sunken: Color, val hairline: Color, val glass: Color, val shadowTint: Color,
  val police: CategoryColors, val ssc: CategoryColors, val railway: CategoryColors,
  val bank: CategoryColors, val state: CategoryColors,
) {
  val terracotta get() = today; val onTerracotta get() = onToday            // aliases (tokens: ext.terracotta*)
  val terracottaContainer get() = urgentContainer; val onTerracottaContainer get() = onUrgentContainer
}

val LightSarkari = SarkariColors(
  success = Color(0xFF1E5B46), onSuccess = Color.White, successContainer = Color(0xFFE2F0E8), onSuccessContainer = Color(0xFF1E5B46),
  warning = Color(0xFF8A5A00), onWarning = Color.White, warningContainer = Color(0xFFF8EDD4), onWarningContainer = Color(0xFF8A5A00),
  urgent = Color(0xFFB3361E), onUrgent = Color.White, urgentContainer = Color(0xFFFBE4DC), onUrgentContainer = Color(0xFF8A2A15),
  today = Color(0xFFB3361E), onToday = Color.White,
  marigold = Color(0xFFF2A93B), onMarigold = Color(0xFF3A2405), marigoldContainer = Color(0xFFFDEFD6), onMarigoldContainer = Color(0xFF8A5200),
  marigoldIcon = Color(0xFFB06E00),
  card = Color.White, sunken = Color(0xFFF5ECDF), hairline = Color(0xFFEFE4D4),
  glass = Color(0xE6FBF5EC), shadowTint = Color(0xFF78460A),
  police = CategoryColors(Color(0xFFFDEBD3), Color(0xFF9A5B00)), ssc = CategoryColors(Color(0xFFE2F0E8), Color(0xFF1E5B46)),
  railway = CategoryColors(Color(0xFFFBE4DC), Color(0xFFA23A1E)), bank = CategoryColors(Color(0xFFE1EEF2), Color(0xFF1D5E73)),
  state = CategoryColors(Color(0xFFF1E4F2), Color(0xFF7A3D80)),
)
val DarkSarkari = SarkariColors(
  success = Color(0xFF86D4B2), onSuccess = Color(0xFF0D2A1F), successContainer = Color(0xFF1D3329), onSuccessContainer = Color(0xFF86D4B2),
  warning = Color(0xFFEBC46E), onWarning = Color(0xFF2A1A02), warningContainer = Color(0xFF362A12), onWarningContainer = Color(0xFFEBC46E),
  urgent = Color(0xFFFF8A6B), onUrgent = Color(0xFF2A1205), urgentContainer = Color(0xFF3E2018), onUrgentContainer = Color(0xFFFFC9B8),
  today = Color(0xFFFF8A6B), onToday = Color(0xFF2A1205),
  marigold = Color(0xFFF4B755), onMarigold = Color(0xFF2A1A02), marigoldContainer = Color(0xFF3A2A12), onMarigoldContainer = Color(0xFFF4B755),
  marigoldIcon = Color(0xFFF4B755),
  card = Color(0xFF231C15), sunken = Color(0xFF2D241B), hairline = Color(0xFF3A2F24),
  glass = Color(0xE617120D), shadowTint = Color(0xFF000000),
  police = CategoryColors(Color(0xFF3A2A14), Color(0xFFF4B755)), ssc = CategoryColors(Color(0xFF1D3329), Color(0xFF86D4B2)),
  railway = CategoryColors(Color(0xFF3E2018), Color(0xFFFF9F85)), bank = CategoryColors(Color(0xFF16303A), Color(0xFF8CCFE4)),
  state = CategoryColors(Color(0xFF33203A), Color(0xFFE3A8EA)),
)

val LocalSarkariColors = staticCompositionLocalOf { LightSarkari }
object SarkariTheme {
  val colors: SarkariColors @Composable @ReadOnlyComposable get() = LocalSarkariColors.current
  val spacing: Spacing get() = Spacing
}

@Composable
fun SarkariTheme(darkTheme: Boolean = isSystemInDarkTheme(), script: Script = currentScript(), content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalSarkariColors provides if (darkTheme) DarkSarkari else LightSarkari,
    LocalReducedMotion provides rememberReducedMotion(),
  ) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkColors else LightColors,   // dynamic colour OFF
      typography = appTypography(script),
      shapes = AppShapes,
      content = content,
    )
  }
}
```

- Edge-to-edge (`enableEdgeToEdge()`); status-bar icons dark on cream, light in dark theme; the status bar shows the cream background (no coloured app bar).
- `surfaceTint = Transparent` and `tonalElevation = 0.dp` everywhere: M3 tonal tint would turn cream cards pinkish-grey.

## 3. Typography

### 3.1 Font choice

| Family | Role | Why |
| --- | --- | --- |
| **Baloo 2** (Ek Type, OFL) — weight **600** only | Headings, job titles, prices, calendar day numbers, hero counts, app wordmark | Rounded, friendly, very legible in Devanagari *and* Latin (designed together); its round terminals echo the pill buttons. Big numbers look warm, not technical |
| **Hind** (Indian Type Foundry, OFL) — **400 / 600** | Body, labels, buttons, chips | Calm humanist sans with open Devanagari at 14–16 sp; Hindi news-app familiarity; Latin sits on the same baseline |
| Noto Sans Devanagari / Roboto (system) | Fallback | On every Android 8+ phone → 0 KB, used until the download finishes or without GMS |

Two families ≈ 150 KB total, delivered as **Downloadable Google Fonts** (0 KB in the APK). Mukta (v1.1) is retired.

### 3.2 Type scale — 4 reading sizes

The scale has **four reading sizes: 28 / 19 / 16 / 14 sp**, two weights (400, 600). A fifth **12 sp tag size** exists only for badge counts, the "Ad" label, "Pro" lock pill, plan badge and calendar weekday — never for a sentence. Line heights: Latin column for `en`; Devanagari column for `hi`/`mr` and as the base for other scripts (§3.5).

| M3 role | Family | Size (sp) | Line height Latin / Devanagari | Weight | Use |
| --- | --- | --- | --- | --- | --- |
| displaySmall | Baloo 2 | **28** | 36 / 40 | 600 | Hero count, calendar day number, plan price |
| headlineMedium | Baloo 2 | 28 | 36 / 40 | 600 | "शाबाश, फ़ॉर्म भर दिया!" celebration, result screens |
| headlineSmall | Baloo 2 | 28 | 36 / 40 | 600 | Large screen titles (मेरे फ़ॉर्म, प्रोफ़ाइल), onboarding question |
| titleLarge | Baloo 2 | **19** | 26 / 30 | 600 | Top bar title, sheet title, card section titles, wordmark |
| titleMedium | Baloo 2 | 19 | 26 / 30 | 600 | Job title on MatchCard and S21 header |
| titleSmall | Hind | **16** | 24 / 24 | 600 | Rule label, step title, list headline |
| bodyLarge | Hind | 16 | 24 / 24 | 400 | Onboarding / sheet body, why-you-match text |
| bodyMedium | Hind | 16 | 24 / 24 | 400 | **Default body** (never smaller for sentences) |
| bodySmall | Hind | **14** | 20 / 22 | 400 | Meta lines (org · posts), helper, captions, dates |
| labelLarge | Hind | 16 | 24 / 24 | 600 | Buttons (52 and 44 dp), fact strip values |
| labelMedium | Hind | 14 | 20 / 22 | 600 | Chips, ribbons, nav labels, tabs, calendar month band |
| labelSmall | Hind | 12 | 16 / 18 | 600 | Tags only (see above) |

Prototype normalisation: CSS 15 px (fact strip, compact buttons, segmented tabs) → 16 sp `labelLarge`; 13 px (calendar month) → 14 sp `labelMedium`. The fact strip wraps to two rows rather than shrinking.

**Rules**
- Every style: `PlatformTextStyle(includeFontPadding = false)`, `LineHeightStyle(Alignment.Center, Trim.None)`, `letterSpacing = 0.sp` (M3's label tracking overridden). No fixed text heights — `heightIn(min = …)`.
- No ALL-CAPS (the calendar band "NOV" is the CLDR short month, not uppercased; Hindi shows "नवं").
- **Figures:** `fontFeatureSettings = "tnum"` on countdowns, prices, dates and counts (borrowed from direction B). Western digits in every locale, Indian grouping (`39,481`). Formatting rules: 07 §5.
- **Font scale to 200 %:** text in `sp`; containers grow; buttons wrap to 2 lines then stack at `fontScale ≥ 1.5`; the calendar tile grows in height, never clips. Previews at 1.0 / 1.3 / 2.0.
- Line length ≤ ~60 characters → content `widthIn(max = 600.dp)` on medium/expanded.

### 3.3 Compose — `Type.kt`

```kotlin
// :core:designsystem/theme/Type.kt
private val provider = GoogleFont.Provider(
  providerAuthority = "com.google.android.gms.fonts",
  providerPackage = "com.google.android.gms",
  certificates = R.array.com_google_android_gms_fonts_certs,
)
private fun gf(name: String, vararg w: FontWeight) = w.map { Font(GoogleFont(name), provider, it) }

// Downloadable font first; FontFamily resolves per glyph and falls back to the system font (Noto / Roboto)
// until the download finishes or when GMS is missing.
val Baloo2 = FontFamily(gf("Baloo 2", FontWeight.SemiBold))
val Hind = FontFamily(gf("Hind", FontWeight.Normal, FontWeight.SemiBold))

private val base = TextStyle(
  letterSpacing = 0.sp,
  platformStyle = PlatformTextStyle(includeFontPadding = false),
  lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
private fun s(fam: FontFamily, size: Int, lh: Int, w: FontWeight) =
  base.copy(fontFamily = fam, fontSize = size.sp, lineHeight = lh.sp, fontWeight = w)

/** Line heights per script: Latin uses the compact column; every other script the Devanagari column × multiplier. */
fun appTypography(script: Script = Script.Devanagari): Typography {
  val f = ScriptFonts.of(script)                       // display + body families from tokens.json font.script
  fun lh(latin: Int, deva: Int) =
    if (script == Script.Latin) latin else (deva * script.lineHeightMultiplier).roundToInt()
  val semi = FontWeight.SemiBold; val reg = FontWeight.Normal
  return Typography(
    displaySmall   = s(f.display, 28, lh(36, 40), semi),
    headlineMedium = s(f.display, 28, lh(36, 40), semi),
    headlineSmall  = s(f.display, 28, lh(36, 40), semi),
    titleLarge     = s(f.display, 19, lh(26, 30), semi),
    titleMedium    = s(f.display, 19, lh(26, 30), semi),
    titleSmall     = s(f.body,    16, lh(24, 24), semi),
    bodyLarge      = s(f.body,    16, lh(24, 24), reg),
    bodyMedium     = s(f.body,    16, lh(24, 24), reg),
    bodySmall      = s(f.body,    14, lh(20, 22), reg),
    labelLarge     = s(f.body,    16, lh(24, 24), semi),
    labelMedium    = s(f.body,    14, lh(20, 22), semi),
    labelSmall     = s(f.body,    12, lh(16, 18), semi),
  )
}
val Numeric = SpanStyle(fontFeatureSettings = "tnum")   // apply to countdowns, prices, dates
```

Preload the active script's two families after the first frame (`FontFamily.Resolver.preload`) so the first card render doesn't swap fonts visibly.

### 3.4 Delivery and fallbacks

- `androidx.compose.ui:ui-text-google-fonts`, provider `com.google.android.gms.fonts`; certificate array from the Android docs in `res/values/font_certs.xml`.
- Only the **active language's** script families are requested (plus Latin, which Baloo 2 / Hind already include). Other scripts are never prefetched; the language picker's "coming soon" list uses system fonts (07 §6).
- Fallback order: requested Google font → system Noto for that script → Roboto. Layouts are screenshot-tested with both the downloaded font and the fallback (metrics differ by up to ~6 %; containers must grow, never clip).

### 3.5 Per-script families for future languages

Baloo 2 and Hind have sister families for several Indic scripts, so the Marigold look carries over; where no sister exists, Noto Sans is used for that role. Line-height multipliers are the same as 07 §2 and apply to the **Devanagari** line-height column. Source: `tokens.json → font.script`.

| Script (locales) | Display (600) | Body (400/600) | Fallback | LH × |
| --- | --- | --- | --- | --- |
| Latin (en) | Baloo 2 | Hind | Roboto | Latin column |
| Devanagari (hi, mr) | Baloo 2 | Hind | Noto Sans Devanagari | 1.0 |
| Bengali (bn, as) | Baloo Da 2 | Hind Siliguri | Noto Sans Bengali | 1.05 — verify Assamese ৰ ৱ before enabling `as` |
| Telugu (te) | Baloo Tammudu 2 | Hind Guntur | Noto Sans Telugu | 1.15 |
| Tamil (ta) | Baloo Thambi 2 | Hind Madurai | Noto Sans Tamil | 1.05 |
| Gujarati (gu) | Baloo Bhai 2 | Hind Vadodara | Noto Sans Gujarati | 1.0 |
| Kannada (kn) | Baloo Tamma 2 | Noto Sans Kannada | system | 1.15 |
| Odia (or) | Baloo Bhaina 2 | Noto Sans Oriya | system | 1.15 |
| Malayalam (ml) | Baloo Chettan 2 | Noto Sans Malayalam | system | 1.1 |
| Gurmukhi (pa) | Baloo Paaji 2 | Noto Sans Gurmukhi | system | 1.0 |
| Urdu (ur, RTL) | Noto Nastaliq Urdu | Noto Nastaliq Urdu | Noto Naskh Arabic | 1.6; body ≥ 16 sp; labels ≤ 14 sp stay 400 |

Baloo Bhaijaan 2 (Arabic) is not used for Urdu: it is Naskh-style, and Urdu readers expect Nastaliq. 07-localization.md §1–§2 still lists Mukta for Devanagari/Latin; this table supersedes those rows (07 to be aligned in its next edit).

## 4. Spacing and layout (8-pt grid)

`Spacing` tokens (dp): `xxs 2 · xs 4 · sm 8 · md 12 · lg 16 · xl 20 · xxl 24 · x3l 32 · x4l 40 · x5l 48`. Prototype values of 6/10/14 px snap to the nearest 4/8 step.

| Use | dp |
| --- | --- |
| Screen gutter (compact / medium) | 16 / 24; content max width 600 |
| Card padding | 16 (ribbon: 16 h × 8 v) |
| Gap between cards | 12 |
| Section header padding | 20 top, 8 bottom, 20 start |
| Inside a card between blocks | 12; label → value 4 |
| Fact strip gap / chip row gap | 8 |
| Icon → text in rows | 8 (12 beside 48 dp tiles) |
| Adjacent touch targets | ≥ 8 |

```kotlin
// :core:designsystem/theme/Spacing.kt
object Spacing {
  val xxs = 2.dp; val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp
  val xl = 20.dp; val xxl = 24.dp; val x3l = 32.dp; val x4l = 40.dp; val x5l = 48.dp
  val ScreenGutter = 16.dp; val CardPadding = 16.dp; val CardGap = 12.dp; val SectionGap = 20.dp
}
object SmSize {
  val TouchMin = 48.dp; val Button = 52.dp; val ButtonCompact = 44.dp; val TopBar = 56.dp; val BottomNav = 80.dp
  val CategoryTile = 48.dp; val SectionIconTile = 36.dp; val CalendarTileWidth = 76.dp; val StepNode = 36.dp
  val Medallion = 80.dp; val NavPillW = 64.dp; val NavPillH = 32.dp; val Chip = 32.dp; val VerdictChip = 28.dp
}
```

**Sizes:** touch ≥ 48 × 48 dp (chips 28–32 dp visual use `minimumInteractiveComponentSize()` when tappable); primary button 52 dp; compact button 44 dp; text field 56 dp; top bar 56 dp (large-title screens: the 28 sp title sits in the 56 dp bar, no coloured app bar); bottom nav 80 dp (grows to 88 for 2-line labels); sticky action bar 84 dp + nav inset.

## 5. Shape and elevation

### 5.1 Radii

| Token | dp | Used for |
| --- | --- | --- |
| `radius.xs` | 6 | "Ad" label, tiny tags |
| `radius.sm` | 8 | checkbox |
| `radius.md` | 12 | section icon tile (36), benefit icon (32), change strip |
| `radius.lg` | **16** | category tile, calendar tile, fact chips, date chips, spec boxes, nav pill |
| `radius.xl` | **24** | cards, greeting card, ad slot, plan cards |
| `radius.xxl` | 28 | Pro card, bottom-sheet top corners, dialogs |
| `radius.full` | pill | all buttons, chips, ribbons' parent card clips them |

### 5.2 Compose — `Shape.kt`

```kotlin
val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),    // checkbox, tags
  small = RoundedCornerShape(12.dp),        // icon tiles, text fields, change strip
  medium = RoundedCornerShape(16.dp),       // category / calendar tile, fact & date chips
  large = RoundedCornerShape(24.dp),        // cards, plan cards, ad slot
  extraLarge = RoundedCornerShape(28.dp),   // Pro card, sheets, dialogs
)
object SmShapes { val Pill = CircleShape; val Tag = RoundedCornerShape(6.dp) }
// Buttons: ButtonDefaults.shape is already a pill (CircleShape-like) — keep it.
```

### 5.3 Elevation: warm tinted shadows

Direction C gets depth from **soft brown-tinted shadows**, not borders or tonal elevation. M3 `tonalElevation = 0.dp` on every Surface/Card/NavigationBar/ModalBottomSheet.

| Token | Light (CSS layers) | Dark |
| --- | --- | --- |
| `shadow.card` | `0 2 4 rgba(120,70,10,.05)` + `0 12 28 rgba(120,70,10,.08)` | `0 12 28 rgba(0,0,0,.35)` + 1 dp top inner highlight white 4 % |
| `shadow.ctaPrimary` | `0 8 18 -8 primary @ 65 %` + 1 dp inner top highlight white 22 % | same with dark primary |
| `shadow.ctaMarigold` | `0 8 18 -8 marigold @ 80 %` + inner highlight white 45 % | same |
| `shadow.calendarTile` | `0 6 14 -6 rgba(120,70,10,.30)` + 1 dp hairline bottom | black |
| `shadow.medallion` | 8 dp `primaryContainer` ring + `0 14 28 -10 primary @ 60 %` + inner highlight | same |
| bars | none: `ext.glass` + 1 dp `hairline` top line | same |

**Approximating in Compose**

```kotlin
// Preferred (Compose UI 1.9+): real CSS-like layered shadows with colour, blur, spread and offset.
fun Modifier.cardShadow(shape: Shape, c: SarkariColors, dark: Boolean) =
  if (!dark) this
    .dropShadow(shape, Shadow(radius = 4.dp, offset = DpOffset(0.dp, 2.dp), color = c.shadowTint, alpha = 0.05f))
    .dropShadow(shape, Shadow(radius = 28.dp, offset = DpOffset(0.dp, 12.dp), color = c.shadowTint, alpha = 0.08f))
  else this
    .dropShadow(shape, Shadow(radius = 28.dp, offset = DpOffset(0.dp, 12.dp), color = Color.Black, alpha = 0.35f))
    .innerShadow(shape, Shadow(radius = 0.dp, offset = DpOffset(0.dp, 1.dp), color = Color.White, alpha = 0.04f))

// Fallback (older Compose BOM): one platform shadow with tinted colours (honoured on API 28+;
// API 26–27 draws grey — acceptable).
fun Modifier.cardShadowFallback(shape: Shape, c: SarkariColors) =
  shadow(elevation = 4.dp, shape = shape, clip = false,
         ambientColor = c.shadowTint.copy(alpha = 0.08f), spotColor = c.shadowTint.copy(alpha = 0.20f))

// Primary CTA: tinted glow under the pill + 1 dp highlight along the top edge.
fun Modifier.ctaShadow(color: Color) = this
  .dropShadow(CircleShape, Shadow(radius = 18.dp, spread = (-8).dp, offset = DpOffset(0.dp, 8.dp), color = color, alpha = 0.65f))
  .innerShadow(CircleShape, Shadow(radius = 0.dp, offset = DpOffset(0.dp, 1.dp), color = Color.White, alpha = 0.22f))
```

- Draw the shadow **outside** the clip (`clip = false`) and clip content separately (`Modifier.clip(shape)` after the shadow) so ribbons are clipped by the 24 dp corners.
- Lists: shadows on ≤ ~8 visible cards are cheap; never animate shadow parameters. In `LazyColumn` give cards 12 dp spacing so blur isn't cut by neighbours.
- Low-end fallback: if `ActivityManager.isLowRamDevice`, use `cardShadowFallback` only (single platform shadow).

**Z-order:** content 0 · section headers 1 · banners 2 · bottom bars (glass) 3 · sheets/dialogs (window) · snackbar above bottom bar.

## 6. Iconography

- **Set:** Google **Material Symbols Rounded**. Two styles, set once in the icon import task:
  - **Content / status icons — FILLED, weight 500, grade 0, optical size 24** (`FILL 1, wght 500`): ribbon icons, verdict/deadline chips, category tiles, fact strip, why-you-match bubbles, journey nodes, Pro benefits, selected nav item, button icons.
  - **Chrome icons — OUTLINED, weight 400** (`FILL 0, wght 400`): back, close, overflow `more_vert`, top-bar actions (`notifications`, `share`), secondary row actions (`snooze`, `visibility_off`, `open_in_new`, `download`), **unselected** nav items.
- **Delivery:** no `material-icons-extended`. Import the ~70 glyphs (02 table below) as vector drawables in both styles where needed (`ic_work_filled.xml`, `ic_work_outlined.xml`) from the Material Symbols variable font at the settings above; reference via `ImageVector.vectorResource` from `SmIcons`.
- **Sizes:** 14 (lock pill), 18 (chips, fact strip, rule bubble glyph), 20 (ribbon, rule line, section tile glyph), 22 (buttons, section headers), 24 (default, top bar, nav, category tile glyph), 44 (medallion check), 48 (result/permission icons). Illustrations: flat vector in marigold/forest/terracotta, 120 dp, built from the same shapes.
- **Containers:** icons often sit in tinted tiles — category tile 48/16, section icon tile 36/12 (`marigoldContainer` + `marigoldIcon`), benefit tile 32/10 (white + shadow), rule bubble 28 circle (`successContainer` / `warningContainer`), journey node 36 circle.
- **Brand logos:** Google "G" only inside the official Sign in with Google button. No WhatsApp glyph anywhere.
- **RTL:** directional icons mirror (`AutoMirrored`), clocks/checks/₹/QR never (07 §4).
- **No emoji as icons.** Accessibility: icons beside text → `contentDescription = null`; standalone → localized description.

| Meaning | Symbol | Meaning | Symbol |
| --- | --- | --- | --- |
| Jobs tab | `work` | My forms tab | `assignment_turned_in` |
| Profile tab | `account_circle` | Alert history | `notifications` |
| Help | `help` | Back | `arrow_back` (AutoMirrored) |
| Eligible ✓ | `check_circle` (chip) / `check` (bubble) | Check yourself ? | `help` (chip) / `question_mark` (bubble) |
| Not eligible ✗ | `cancel` / `close` (bubble) | Last date | `event` / `event_busy` |
| Today | `alarm` | 1–3 days | `timer` |
| 4–7 days | `schedule` | Applied (action) | `task_alt` |
| Not interested | `visibility_off` | Later / snooze | `snooze` |
| Official PDF | `picture_as_pdf` | Apply site | `open_in_new` |
| Fee | `currency_rupee` | Documents | `description` |
| Photo spec | `photo_camera` | Signature spec | `draw` |
| Age | `cake` | Qualification | `school` |
| Category | `groups` | State/domicile | `location_on` |
| Gender | `wc` | PwD | `accessible` |
| Ex-servicemen | `military_tech` | Report wrong | `flag` |
| Corrigendum/changed | `update` | Extended | `more_time` |
| Cancelled notice | `block` | Pro | `workspace_premium` |
| Category: Police | `local_police` | Category: SSC/central | `account_balance` |
| Category: Railway | `train` | Category: Bank | `savings` |
| Category: State/other | `location_city` | Why you match | `fact_check` |
| Key dates | `event` | What you need | `checklist` |
| Family | `family_restroom` | Clash | `event_repeat` |
| Certificate | `verified` | Admit card | `badge` |
| City slip | `location_city` | Exam | `edit_note` |
| Answer key | `key` | Result | `emoji_events` |
| DV / medical | `fact_check` / `medical_services` | Battery | `battery_saver` |
| Autostart | `restart_alt` | Notification permission | `notifications_active` |
| Offline | `cloud_off` | Server problem | `report` |
| Lock (Pro stage) | `lock` | Settings | `tune` |
| Language | `translate` | Logout | `logout` |
| Delete | `delete` | Download data | `download` |
| Share | `share` | Phone | `smartphone` |
| Quiet hours | `bedtime` | Digest | `summarize` |
| Refresh/test | `sync` | Applied medallion | `check` (44, filled) |
| Close | `close` | Copy | `content_copy` |
| Guest | `person` | Signed in | Google photo, else `account_circle` |
| Switch account | `switch_account` | Privacy notice | `privacy_tip` |
| Sign in / sync | `cloud_done` | Invite QR | `qr_code_2` |
| Invite link | `link` | Why ads / disclaimer | `info` |
| Ad placeholder | `storefront` | On-device backup | `alarm_on` |
| Coming-soon language | `schedule` | Wordmark tile | `notifications_active` (28 dp marigold tile, radius 9→8) |

## 7. Motion

Tokens (ms): instant 0 · short1 50 · short2 100 · short3 150 · short4 200 · medium1 250 · medium2 300 · medium4 400 · long2 500. Easing: standard/emphasized `(0.2, 0, 0, 1)`, emphasizedDecelerate `(0.05, 0.7, 0.1, 1)`, emphasizedAccelerate `(0.3, 0, 0.8, 0.15)`.

| Interaction | Spec |
| --- | --- |
| Press | M3 ripple tinted with the content colour; no card scale |
| Forward / back navigation | fade + 1/12-width slide from end, 300 ms decel / 200 ms accel; predictive back on |
| Bottom-tab switch | crossfade 150 ms; marigold nav pill grows 0→64 dp + fade, 200 ms decel (M3 default indicator) |
| Bottom sheet | M3 default (300 enter / 200 exit) |
| Card leaves list after Applied / Not interested | `Modifier.animateItem()` (fade 150 + placement 250) after the snackbar |
| Ribbon / chip level change at IST midnight | `animateColorAsState(150)`; no motion |
| **Applied peak (S21 → B02)** | 1) medallion scales 0.6 → 1.0 with `spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)` (~350 ms) + `HapticFeedbackType.Confirm` (API 30+, else `LongPress`); 2) **marigold confetti**: 24 particles (50 % marigold, 25 % forest @ 55 %, 25 % terracotta @ 60 %; rounded rects 6×12 r3 and dots r3–5) burst outward from behind the medallion, fall with gravity and fade, **1200 ms** total, drawn in one `Canvas` with `withFrameNanos`, never blocks input, runs once per job; 3) Pro card fades in + 24 dp slide-up 300 ms after the medallion settles |
| Skeleton | shimmer 1200 ms linear (sunken → card) |

**Reduced motion** (`ANIMATOR_DURATION_SCALE == 0` or accessibility "Remove animations", via `LocalReducedMotion`; also battery-saver for confetti): all durations → 0; **no confetti particles — draw the static 9-piece confetti arrangement from the prototype instead**; medallion appears at full size with its ring; Pro card appears without slide; shimmer → static sunken blocks. The haptic is kept (it is not motion). Maximum 1–2 animated elements per screen; confetti only on the Applied moment.

```kotlin
@Composable fun AppliedMedallion(reduced: Boolean = SmTheme.reducedMotion) {
  val scale = remember { Animatable(if (reduced) 1f else 0.6f) }
  val haptics = LocalHapticFeedback.current
  LaunchedEffect(Unit) {
    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    if (!reduced) scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
  }
  Box(contentAlignment = Alignment.Center) {
    if (reduced) StaticConfetti() else ConfettiBurst(particles = 24, durationMs = 1200)
    Medallion(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
  }
}
```

## 8. Content & voice

- **Voice:** a helpful elder sibling — short, warm, direct, "आप". Celebrate the action, not the user ("शाबाश, फ़ॉर्म भर दिया!" / "Well done, applied!"). Urgency comes from facts written as sentences on ribbons ("आज आखिरी दिन · रात 11 बजे तक"), never "!!!".
- **Verdict words (fixed):** योग्य · ख़ुद जाँचें · योग्य नहीं. **Actions:** फ़ॉर्म भर दिया · बाद में · रुचि नहीं · PDF देखें · फ़ॉर्म भरने की साइट खोलें.
- **Nukta & chandrabindu:** फ़ॉर्म, ज़रूरी, फ़ीस; नौकरियाँ, जाँचें.
- **Greeting card** (Home): "नमस्ते, रवि" / "Hi, Ravi" (28 sp) + "गुरु, 19 नवं · 5 नौकरियों में जवाब बाकी" + count pills (`alarm` 1 आज · `timer` 1 3 दिन में · `event` 3 बाद में). No emoji; guest without a name: "नमस्ते".
- **Disclaimer (fixed):** "Sarkari Match एक निजी ऐप है, कोई सरकारी ऐप नहीं। हमेशा आधिकारिक नोटिस से मिलान करें।" / "Sarkari Match is a private app, not a government app. Always verify with the official notice."
- Strings: English in `values`, Hindi in `values-hi`, others per 07 §1; translator rules 07 §7.

## 9. Accessibility baseline

- Contrast per §2.6 (text ≥ 4.5:1, icons/boundaries ≥ 3:1). Touch ≥ 48 dp. Focus order = visual order.
- **Never colour alone:** ribbons carry icon + sentence; chips icon + word; journey nodes differ by icon *and* fill; locked stages carry a "Pro" lock pill; category tiles always sit beside the job title/org.
- Every MatchCard is one TalkBack stop (`mergeDescendants`) with custom actions (फ़ॉर्म भर दिया / बाद में / रुचि नहीं / खोलें); the ribbon text is read first ("आज आखिरी दिन, रात 11 बजे तक. UP Police Constable 2026 …").
- Calendar tile = one node ("आखिरी तारीख शुक्रवार 21 नवंबर"); decorative confetti and greeting glow are `invisibleToUser`.
- Headings via `semantics { heading() }`; snackbars and result banners polite live regions; countdowns not live.
- 200 % font scale and largest display size must not clip (`@PreviewFontScale`, `@PreviewScreenSizes`); pseudolocales `en-XA`, `ar-XB` in debug; `ta` and `ur` once strings exist.
- Mixed-language text carries `LocaleSpan` so TalkBack uses the right voice (07 §8).
- Low literacy: icon + word on every action, category tiles for recognition, the largest type of the three directions (body 16, titles 19, numbers 28); TTS "सुनें" on S21 (P1).
- Reduced motion honoured (§7); confetti never flashes (no strobe, < 3 flashes/s).
