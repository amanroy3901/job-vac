# 07 — Localization: 13 Indian languages, Hindi + English at launch

Founder decision (2026-11-19, PRD): design for Hindi, English, Bengali, Marathi, Telugu, Tamil, Gujarati, Kannada,
Odia, Malayalam, Punjabi (Gurmukhi), Assamese and Urdu (right-to-left). **Launch with Hindi + English only.** Adding a
language later must need only translated strings and a font entry, never a layout change.

Reads with: [02 design system](02-design-system.md) (type scale, icons) · [`tokens.json`](tokens.json) → `font.script`,
`layout.i18n` · [06 components](06-components.md) §L · prototype group **L** (Tamil and Urdu preview frames).

## 1. Language registry

| Code | Language (own script) | Script | Dir | Status | Font (07 §2) | Plural categories (CLDR) |
| --- | --- | --- | --- | --- | --- | --- |
| `hi` | हिंदी | Devanagari | LTR | **launch, default** | Baloo 2 (headings) + Hind (body) | one, other |
| `en` | English | Latin | LTR | **launch** | Baloo 2 + Hind | one, other |
| `bn` | বাংলা | Bengali | LTR | planned | Noto Sans Bengali | one, other |
| `mr` | मराठी | Devanagari | LTR | planned | Baloo 2 + Hind | one, other |
| `te` | తెలుగు | Telugu | LTR | planned | Noto Sans Telugu | one, other |
| `ta` | தமிழ் | Tamil | LTR | planned | Noto Sans Tamil | one, other |
| `gu` | ગુજરાતી | Gujarati | LTR | planned | Noto Sans Gujarati | one, other |
| `kn` | ಕನ್ನಡ | Kannada | LTR | planned | Noto Sans Kannada | one, other |
| `or` | ଓଡ଼ିଆ | Odia | LTR | planned | Noto Sans Oriya | one, other |
| `ml` | മലയാളം | Malayalam | LTR | planned | Noto Sans Malayalam | one, other |
| `pa` | ਪੰਜਾਬੀ | Gurmukhi | LTR | planned | Noto Sans Gurmukhi | one, other |
| `as` | অসমীয়া | Bengali-Assamese | LTR | planned | Noto Sans Bengali | one, other |
| `ur` | اردو | Arabic (Nastaliq) | **RTL** | planned | Noto Nastaliq Urdu | one, other |

- "Enabled" is data, not code: `ReferenceBundle.languages[] {code, status: enabled|coming_soon}` (server). A language
  becomes selectable only when its `values-xx/strings.xml` ships in the app **and** the server marks it `enabled`
  (the server also needs its push templates and rule-line templates, because pushes are rendered on the device from
  string keys but job data such as rule lines is templated server-side).
- `locales_config.xml` lists only languages whose strings ship, so Android 13+'s per-app language setting never offers
  an untranslated language.
- Job data that comes from notices (post titles, organisation names) is shown as published, usually English or Hindi.
  We do **not** machine-translate notice content. The UI around it is translated ("SSC CGL 2026 — கடைசி தேதி 24 ஆகஸ்ட்").

## 2. Fonts per script

All fonts are **Downloadable Google Fonts** (`androidx.compose.ui.text.googlefonts`, provider
`com.google.android.gms.fonts`), so the APK grows by 0 KB per language (NFR < 15 MB). Only the font of the
**selected** language is requested (plus Latin, which Baloo 2 and Hind already cover); fonts for other scripts are never
prefetched.

| Script | Primary | Fallback chain (until downloaded / no GMS) | Line-height × | Notes |
| --- | --- | --- | --- | --- |
| Devanagari + Latin | Baloo 2 600 (headings, numbers) + Hind 400/600 (body) | Noto Sans Devanagari (system) → Roboto | 1.0 (baseline scale in 02 §3.2) | Unchanged |
| Bengali, Assamese | Noto Sans Bengali | system Noto Sans Bengali | 1.05 | Assamese ৰ ৱ are in the font |
| Telugu | Noto Sans Telugu | system | 1.15 | Tall vowel signs above and below |
| Tamil | Noto Sans Tamil | system | 1.05 | Longest strings (§3) |
| Gujarati | Noto Sans Gujarati | system | 1.0 | |
| Kannada | Noto Sans Kannada | system | 1.15 | Stacked conjuncts below the baseline |
| Odia | Noto Sans Oriya | system | 1.15 | Google Fonts family name is "Oriya" |
| Malayalam | Noto Sans Malayalam | system | 1.1 | Very long compound words |
| Gurmukhi | Noto Sans Gurmukhi | system | 1.0 | |
| Urdu | **Noto Nastaliq Urdu** | Noto Naskh Arabic (system) → sans-serif | **1.6** | Nastaliq is steep and tall. Body never below 16 sp |

Rules:
- **Sizes stay the same in every script**; only line height changes (`lineHeight = base × multiplier`, rounded to
  the 1 sp grid). Implementation: one `Typography` per script built from the same scale (`AppTypography(script)`),
  chosen from the current locale in `SarkariTheme`.
- Keep `includeFontPadding = false`, `LineHeightStyle(Center, Trim.None)` and **no fixed heights** on text containers
  (02 §3.2). This matters most for Telugu, Kannada and Urdu, where marks extend far above or below the line.
- Letter-spacing 0 for every non-Latin script. No ALL-CAPS, no italics (Indic and Arabic scripts have no true italic).
- Mixed lines (Tamil UI + English exam name, Urdu UI + "SSC GD") use the script font for the UI words and fall back
  per glyph to Hind/Baloo 2 (then Roboto) for Latin. Noto families are designed to sit together, so mixed lines keep one baseline.
- Weights: Noto Sans scripts use 400/600 like Hind (600 like Baloo 2 for headings). Noto Nastaliq Urdu has 400–700; use 400 for body and
  600 for titles. Bold Nastaliq at small sizes fills in, so labels ≤ 14 sp stay 400.
- Test every new script with its longest real strings at font scale 1.0 and 2.0 on a 360 dp phone before enabling.

## 3. String length and layout

Measured growth versus English for typical UI strings (button labels, chips, headings), used as design budgets
(`tokens.json → layout.i18n.expansionVsEnglish`):

| Language | × English | Language | × English |
| --- | --- | --- | --- |
| Hindi | 1.15 | Telugu | 1.35 |
| Gujarati, Punjabi | 1.15 | Kannada | 1.4 |
| Marathi, Bengali, Urdu | 1.2 | **Tamil** | **1.6** |
| Assamese, Odia | 1.25 | **Malayalam** | **1.6** |

Short labels grow most: "Applied" (7) → "விண்ணப்பித்துவிட்டேன்" (Tamil, 21 characters, about 3× wider).

Layout rules (every screen; checked in review):
1. **No fixed widths on anything that holds text.** Buttons, chips, tabs and labels size to content
   (`wrapContentWidth`, `widthIn(min = …)` only). Two-button rows use `weight(1f)` and **wrap to 2 lines** before
   stacking; if any label still needs more than 2 lines, the row stacks vertically (rule shared with font scale ≥ 1.5).
2. **Text wraps; it does not truncate**, except job titles in list cards (max 3 lines, full text in TalkBack and on
   S21). Never truncate actions, verdict words, deadlines, errors or legal text (skill rule "Essential text
   truncation").
3. **Chips stay on one line** (skill rule "Compact label overflow") and the chip row wraps (`FlowRow`). Chip texts are
   kept short in every language: translators get a hard limit of 18 characters for chip strings.
4. Bottom-nav labels may wrap to 2 lines (`maxLines = 2`, `textAlign = Center`); the bar grows from 80 to 88 dp
   rather than truncating. Tab labels in the source language must be one word.
5. Top-bar titles: one line with ellipsis is allowed (the screen heading repeats it in the body); actions never
   truncate.
6. Notification limits (05 §2) are per language: title ≤ 45 code points, action label ≤ 15. Translators get these
   limits as `<!-- maxLength -->` comments; the build runs a lint that fails on overflow.
7. Numbers and dates do not grow with language (Western digits everywhere, §5), so countdown chips keep
   `widthIn(min = 88.dp)` and never a fixed width.
8. Test with the pseudolocale `en-XA` (+30 %, accented) in every debug build, and with real Tamil and Malayalam
   strings before those languages ship.

## 4. Right-to-left (Urdu)

Android mirrors the layout automatically when the locale is RTL if `android:supportsRtl="true"` (set it now, at
launch). Compose follows `LocalLayoutDirection`. Design rules:

| Mirror (follows reading direction) | Do not mirror |
| --- | --- |
| Screen layout: start/end padding, list item leading icon on the right, sheet close button position, top-bar back on the right | Phone status bar and Android nav gestures (system) |
| Back / forward arrows, `chevron_right`, `arrow_forward` in corrigendum diffs (old → new reads right-to-left), `open_in_new`, `send` | Clock, `alarm`, `timer`, `schedule` (clock hands turn clockwise in every culture) |
| Progress bars and step indicators (fill from the right), onboarding "चरण 2 / 6" position | `check_circle`, `cancel`, `help` (? is not mirrored in Urdu UI), `currency_rupee`, `qr_code_2`, logos |
| Lifecycle timeline connector side, tracker progress dots order | Numbers, dates, fees, roll numbers, times (always LTR runs) |
| Switch thumb "on" side, slider direction | Media/brand marks, the Google "G" button layout (Google mirrors it itself) |
| Swipe/predictive-back animation direction | Charts (none in the app) |

Rules:
- Use `start`/`end` everywhere (`PaddingValues(start = …)`, `Alignment.Start`, `TextAlign.Start`). `left`/`right`
  only for things that must not mirror. Icons that should mirror use `Icons.AutoMirrored.*` or
  `Modifier.graphicsLayer { scaleX = -1f }` guarded by `LocalLayoutDirection`.
- **Bidi text:** Urdu sentences contain LTR runs (SSC GD, ₹100, 21/11, URLs). Wrap every dynamic value in a
  direction isolate (`BidiFormatter.unicodeWrap` or `⁨…⁩` FSI/PDI) so "SSC GD" and "₹100" never jump to
  the wrong end of the line, and punctuation stays with its run. Template variables in strings are always isolated
  by our string helper, never by translators.
- Phone-number-like and code-like values (none at launch, roll numbers later) are forced LTR.
- Text alignment is `Start` (right in Urdu). Never centre long Urdu paragraphs; centre only short headings, as in LTR.
- The 4 dp urgency stripe on MatchCard is on the **top** edge, so it does not mirror. Left-edge accents are not used
  anywhere for this reason.
- Motion: forward navigation slides in from the end (left in RTL); `slideInHorizontally` uses layout direction.
- Testing: pseudolocale `ar-XB` (bidi, mirrored) in debug builds now; real Urdu strings before enabling `ur`.

The skill database rates this product type's RTL support `partial` (mirrors, but each component needs checking) and
Flat Design `full`; the component checks are the table above and 06 §L.

## 5. Numbers, dates, money, time per locale

| Item | Rule | Hindi | English | Tamil | Urdu |
| --- | --- | --- | --- | --- | --- |
| Digits | Western digits in every locale (`Locale.Builder().setUnicodeLocaleKeyword("nu","latn")`); official notices use them | 23 | 23 | 23 | 23 |
| Grouping | Indian lakh/crore grouping in every locale | 39,481 | 39,481 | 39,481 | 39,481 |
| Date long | Day + month name (CLDR, from `DateTimeFormatter.ofLocalizedDate` / ICU skeleton `d MMMM y`) + year | 21 नवंबर 2026 | 21 November 2026 | 21 நவம்பர் 2026 | 21 نومبر 2026 |
| Date short (chips) | Skeleton `d MMM`; never numeric-only dates in sentences (DD/MM vs MM/DD confusion) | 21 नवं | 21 Nov | 21 நவ. | 21 نومبر |
| Weekday | ICU `EEEE` | शुक्रवार | Friday | வெள்ளி | جمعہ |
| Time | 12-hour with a day-period word, never "PM" in non-English UI | रात 11 बजे | 11 pm | இரவு 11 மணி | رات 11 بجے |
| Money | `₹` + amount, no decimals; "कोई फ़ीस नहीं" style string for 0 | ₹100 | ₹100 | ₹100 | ₹100 |
| Relative | Plural strings ("2 दिन बाकी"), not `DateUtils.getRelativeTimeSpanString` (its wording is too formal in Hindi) | 2 दिन बाकी | 2 days left | 2 நாட்கள் மீதம் | 2 دن باقی |
| Age | Plural strings with two numbers ("23 साल 8 महीने") | | | | |

Dates are always computed in IST (`Asia/Kolkata`) regardless of device time zone, as today.

## 6. Language picker (S02, S40)

- Lists the **enabled** languages as radio cards, each name in its own script with the English name below in
  `bodySmall` (so a helper can pick for someone else): "हिंदी · Hindi", "English · अंग्रेज़ी".
- Below, a collapsed row "जल्द आ रही हैं (11)" / "Coming soon (11)" expands to the planned languages, each in its own
  script with a `schedule` icon and the word "जल्द / Soon". They are **not selectable** (no radio; `Role` none;
  TalkBack "தமிழ், जल्द आ रही है"). This answers "is my language coming?" without promising a date. Tapping a row
  does nothing; there is no waiting list (it would need contact data).
- Order: enabled languages first (current first), then planned languages in a fixed order by number of speakers
  (bn, mr, te, ta, gu, kn, or, ml, pa, as, ur), not alphabetical, so the list is the same in every UI language.
- Scales to 13+: once more than 5 languages are enabled the picker gains a `SearchField` that matches the native name,
  the English name and the code. The coming-soon section disappears when empty.
- Default selection on first run: device locale if enabled, else Hindi.
- The language names in the planned list are rendered with their own fonts. To avoid downloading 10 fonts for one
  screen, the coming-soon list uses the **system** fonts (every Android 8+ phone ships Noto for all 12 scripts); only
  the selected language's Google font is downloaded.

## 7. Copy guidelines for translators

Translators receive a string file plus this section, the screenshot of each screen (prototype), and the glossary.

1. **String keys** are `screen_element_purpose`, e.g. `s21_action_applied`, `n04_title`, `b16_pro_title`. The key
   never changes when the copy changes; a new meaning gets a new key.
2. **No concatenation.** Every sentence is one string with named placeholders (`%1$s` with a translator comment
   naming it: `<!-- %1$s = job short title, e.g. "SSC GD 2027" -->`). Word order differs (Hindi and Tamil are
   verb-final, Urdu is right-to-left), so we never build "आखिरी तारीख" + date + "है" from parts.
3. **Plurals** use `<plurals>` (`quantity="one|other"` for all 13 languages; Android also passes `zero`/`two`/`few`/
   `many` where a language needs them). Never "1 दिन(s)". Numbers inside plurals are formatted by our helper (§5).
4. **Gender-neutral where possible.** Address the user with the respectful plural ("आप", "நீங்கள்", "آپ"), which in
   Hindi and Urdu also avoids gendered verb forms in most sentences ("आप योग्य हैं"). When a verb must agree with the
   user's gender (e.g. Hindi "पाऊँगा/पाऊँगी"), rephrase to a neutral construction ("आपको अलर्ट मिलेंगे") instead of
   using slashes. Do not use the profile's gender field to pick grammar: it is optional and collected only for
   eligibility.
5. **Fixed vocabulary** (glossary, one per language, owned by the copy reviewer): verdict words (योग्य / ख़ुद जाँचें /
   योग्य नहीं), action words (फ़ॉर्म भर दिया / रुचि नहीं / बाद में), Pro, "सरकारी ऐप नहीं" disclaimer. Exam and
   organisation names stay in their official form (SSC CGL, RRB NTPC, DV) in every language.
6. **Plain, spoken register** (02 §7): words people say, not the officialese of notices. A native-speaker aspirant
   reviews each language before it is enabled.
7. **Limits** travel with the string as comments: push titles ≤ 45, push actions ≤ 15, chips ≤ 18 characters.
   Buttons have no limit but must make sense on 2 lines.
8. **No emoji, no exclamation marks in a row, no ALL CAPS** in any language. The single ✓ in push titles is kept.
9. **Legal and disclaimer strings** (privacy notice, Play Billing renewal text, "not a government app") are translated
   by a professional translator and reviewed; they are flagged `translatable="true"` with a `<!-- legal -->` comment.
10. **Do not translate**: brand "Sarkari Match", "Pro", Google/Play names, URLs, placeholders.

## 8. Accessibility per language

- Set the locale on text for TalkBack: each `Text` inherits the app locale; mixed spans (English exam name inside
  Tamil) get `LocaleSpan(Locale.ENGLISH)` via our `localizedAnnotatedString` helper so the right voice reads them.
- TTS "सुनें" (P1, S21) uses the app language's voice if installed (`TextToSpeech.isLanguageAvailable`), else hides
  the button.
- Contrast and touch targets are language-independent; Nastaliq's thin strokes are the reason Urdu body stays at
  ≥ 16 sp and 400 weight on `onSurface` (17:1), never on `onSurfaceVariant` for long text.

## 9. What changes per release

| Step | Who | Done when |
| --- | --- | --- |
| Strings exported (`values/strings.xml` + comments) | Android | Every UI string has a key, comment and limit |
| Translation + glossary | Translator + native reviewer | Lint passes (limits, placeholders, plurals) |
| Push + rule-line templates | Backend | Server renders `n01_*` etc. for the locale and stores `lang` |
| Font entry | Android | `font.script` entry exists (already, for all 13) |
| Screenshot test | QA | Every P0 screen rendered at 1.0 and 2.0 font scale with no clipping; RTL check for `ur` |
| Enable | Ops | `ReferenceBundle.languages[code].status = enabled` and `locales_config.xml` updated in the release |

## 10. Use of the UI UX Pro Max skill for this file

| Search | Result | Used |
| --- | --- | --- |
| `"right to left mirroring" --domain ux` (and `--rtl` with `ux`) | The tool accepts `--rtl` only with `style`/`product`; the `ux` query returned **0 results** | RTL rules come from Android platform guidance (§4), labelled as such |
| `"flat utility mobile" --domain style --rtl` | Flat Design `rtl_level: full`; Material 3 mobile listed | Our flat M3 style needs no directional rework beyond §4 |
| `"job alert utility government" --domain product --rtl` | Job board / Government service rows, `rtl_level: partial` | Hence the per-component mirror table (§4) and 06 §L checks |
| `"localization text expansion" --domain ux` | "Text reflow and spacing" (no fixed-width boxes), "Essential text truncation" (wrap, don't clamp actions) | §3 rules 1–2 |
| `"label wraps truncation" --domain ux` | "Compact label overflow" (chips one line, disclose truncation) | §3 rule 3 |
| `"accessible authentication" --domain ux` | Offer OAuth/passkeys instead of manual OTP transcription | Supports the Google sign-in decision (no OTP) |
| `"tamil urdu nastaliq"`, `"noto sans bengali/gurmukhi/malayalam" --domain google-fonts` | Noto Nastaliq Urdu (arabic+latin subsets), Noto Sans Bengali/Gurmukhi/Malayalam with latin subsets | §2 font table |
| `--stack jetpack-compose` "rtl layout direction", "localization strings plurals", "locale font" | **0 results**; only generic `Modifier.semantics` / `contentDescription` rules | Compose specifics (`LocalLayoutDirection`, `AutoMirrored`, `LocaleSpan`) come from Android documentation, not the skill |
