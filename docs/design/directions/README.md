# Sarkari Match: visual design directions (2026-11-19)

The founder asked for premium directions that look nothing like the baseline prototype (blue flat Material in `../prototype/`). This folder has **three** of them, each shown in **English and Hindi**. Every direction uses the same data and screens, so they can be compared directly. Once the founder picks one, the whole app is restyled through `tokens.json` (see the last section).

| File | What it is |
| --- | --- |
| `overview-en.png` · `overview-hi.png` | Home screen of all three directions side by side. **Start here.** |
| `direction-a-en.png` · `direction-a-hi.png` | A · Ivory & Ink: header strip (palette, type, traits) + 8 phone frames |
| `direction-b-en.png` · `direction-b-hi.png` | B · Midnight Ledger |
| `direction-c-en.png` · `direction-c-hi.png` | C · Marigold Home |
| `direction-a.html` … `direction-c.html`, `overview.html` | Self-contained sources (Google Fonts only). The toggle at top right switches English / हिंदी / both. |

**Frames per direction (360 × 800 dp, Android status and gesture bars):** 1 Home (urgency-grouped feed) · 1b Home feed end (“Later” group, the single labelled native ad, disclaimer) · 2 Job detail (verdict, countdown, why you match with ✓ and ?, key dates, sticky Applied / Later / Not interested) · 2b Job detail continued (“What you need”: fee for your category, document checklist, photo and signature spec, official PDF and apply site) · 3 My forms (lifecycle timeline from city slip to DV for a **free** user, with Pro-locked stages) · 4 The Applied ✓ peak moment followed by the Pro paywall (14-day trial, ₹199/year, ₹49/season, Google Play) · plus two frames in the other colour mode.

These rules hold in every direction: no WhatsApp anywhere, Google Play Billing only, one ad labelled “विज्ञापन / Ad” at the end of the feed only. Urgency always has icon + words + colour (“Last day today · until 11 pm”, “2 days left”), never colour alone. Touch targets are ≥ 48 dp, icons are Material Symbols (no emoji as icons), figures are tabular, and fonts have real Devanagari support. Every text colour pair was computed at ≥ 4.5:1 (WCAG AA). Icon-only colours are ≥ 3:1.

---

## A · Ivory & Ink

- **Mood:** calm, editorial, quietly expensive, in the register of Apple, Airbnb or a private-bank app. Warm ivory paper, ink-black actions, and one sindoor-red accent that appears only when a deadline is close.
- **Suits:** aspirants who want a serious, trustworthy tool, and a brand that wants to feel premium without feeling loud. The least risky choice for a wide audience.
- **Palette (60/30/10):** ivory 60 · ink 30 · sindoor 10.

| Role | Light | Dark |
| --- | --- | --- |
| Background | `#F6F3EE` | `#12110F` |
| Card | `#FFFFFF` | `#1C1A17` |
| Text / CTA (ink) | `#1B1A17` | `#F3EFE8` |
| Muted text | `#6B655C` | `#A9A196` |
| Hairline | `#E8E2D8` | `#2E2B27` |
| Accent: urgency (sindoor) | `#C2381F` (container `#FBE7E1`) | `#FF8065` |
| Eligible ✓ (sage) | `#2F6B4F` on `#E5F0E9` | `#8CCBAA` |
| Check yourself ? (ochre) | `#8A5A00` on `#F6ECD6` | `#E8C06A` |
| Pro (brass on ink card) | `#7E5F24` text, `#D9B77A` on ink | `#D9B77A` |

- **Fonts:** Inter (Latin, `tnum` figures) + Noto Sans Devanagari, the system Devanagari face on Android, so Hindi adds 0 KB. 4 sizes: 32 / 17 / 15 / 13. Weights 400 / 600.
- **Components:** cards radius 20, white on ivory, with a soft warm-tinted shadow (`0 1px 2px` + `0 8px 24px rgba(70,50,20,.07)`) and no borders. Buttons radius 14, 48 dp, ink with a 1 px inner highlight. Large-title headers instead of a coloured app bar. Facts strip (fee / needs / last date) inside each card. Medium density.
- **Peak / paywall:** a sage check inside a soft halo, then an ink Pro card with brass details and a brass “Try 14 days free” button.
- **Pros:** most universally “premium” look. Very calm, so urgency stands out. Cheapest to build, because Hindi uses the system font and it is close to the M3 structure.
- **Cons:** the most restrained of the three, with less warmth and personality, and Inter has a slightly “global startup” feel. Its small 13 sp meta text is the least friendly of the three for low-literacy users.

## B · Midnight Ledger

- **Mood:** focused, confident, premium-dark, in the register of Cred, Linear or a trading terminal. Deadlines read like a departure board, with a big monospace countdown, coral for urgency and champagne gold for Pro.
- **Suits:** serious full-time aspirants (the “Ravi” persona) who check the app many times a day, and evening and night use. Gives the strongest brand distinctiveness.
- **Palette (60/30/10):** warm black 60 · bone text and surface steps 30 · coral and gold 10.

| Role | Dark (default) | Light |
| --- | --- | --- |
| Background | `#0E0D0C` | `#F5F3EF` |
| Surface / raised | `#171614` / `#201E1B` | `#FFFFFF` / `#EEEBE5` |
| Text | `#F3EFE8` | `#151310` |
| Muted | `#A39C91` | `#5F5950` |
| Hairline | `#2D2A26` | `#E2DDD4` |
| Urgency (coral) | `#FF7A59` | `#C2381F` |
| Pro / brand (gold) | `#E3C07E` | `#7E5F24` |
| Eligible ✓ (mint) | `#7FD1A8` | `#1F7A52` |
| Check ? (amber) | `#F0C25C` | `#8A5A00` |
| CTA | bone `#F3EFE8` with ink text | ink `#151310` |

- **Fonts:** IBM Plex Sans + IBM Plex Sans Devanagari (one superfamily), with IBM Plex Mono for figures only (countdowns, fees, dates; in Hindi, mixed text falls back to the proportional face). 4 sizes: 40 / 17 / 15 / 13. Weights 400 / 600.
- **Components:** radius 14 / 12 / 8 (crisp). No drop shadows: depth comes from 3 surface steps plus a 1 px top inner highlight. Fixed countdown column on every row (`23:00 today`, `02 days`), a 3-segment “need your reply” bar, and hatched pattern vs solid fill so urgency does not rely on colour. The densest of the three.
- **Peak / paywall:** a gold “stamp” with a soft glow, a gold-edged Pro card with a 5-dot lifecycle preview, and monospace prices.
- **Pros:** the most scannable for deadlines, the most distinctive, looks expensive, and uses little power on OLED.
- **Cons:** dark-first on cheap IPS-LCD Redmi screens looks greyer and is harder to read in sunlight. It can feel cold or “for techies” to first-time and low-literacy users, monospace adds a third face, and it is the furthest from what a worried parent expects.

## C · Marigold Home

- **Mood:** warm, reassuring, proudly Indian without kitsch: a trusted elder sibling (बड़ा भाई/बहन). Cream paper, forest-green actions, marigold highlights, big icons beside every word, and a real celebration when you apply.
- **Suits:** the core Hindi-belt audience, including low-literacy users, first-time smartphone users and family members. It is the best match for the “helpful elder sibling” voice in 02 §7.
- **Palette (60/30/10):** cream 60 · cocoa text and white cards 30 · forest green + marigold 10.

| Role | Light (default) | Dark |
| --- | --- | --- |
| Background | `#FBF5EC` | `#17120D` |
| Card | `#FFFFFF` | `#231C15` |
| Text (cocoa) | `#2A1E14` | `#F6EDE1` |
| Muted | `#6E5D4E` | `#C2B19E` |
| Hairline | `#EFE4D4` | `#3A2F24` |
| Primary CTA / eligible (forest) | `#1E5B46` (container `#E2F0E8`) | `#86D4B2` |
| Highlight / Pro (marigold) | `#F2A93B` (container `#FDEFD6`) | `#F4B755` |
| Urgency (terracotta) | `#B3361E` (container `#FBE4DC`) | `#FF8A6B` |
| Check ? (ochre) | `#8A5A00` on `#F8EDD4` | `#EBC46E` |
| Category tiles | Police `#FDEBD3` · SSC `#E2F0E8` · Railway `#FBE4DC` · Bank `#E1EEF2` · State `#F1E4F2` | tinted darks |

- **Fonts:** Baloo 2 600 (headings, prices, calendar numbers) + Hind 400 / 600 (body), both with full Devanagari + Latin. 4 sizes: 28 / 19 / 16 / 14, the largest of the three.
- **Components:** cards radius 24, tiles 16, pill buttons 52 dp. Soft brown-tinted shadows. A full-width deadline ribbon on urgent cards (“Last day today · until 11 pm”), category icon tiles, a tear-off calendar for the last date, horizontal date chips, a journey path for the lifecycle with dashed locked stages, and a marigold nav pill. Roomy density.
- **Peak / paywall:** “Well done, applied!” with a green medallion and marigold confetti (static here; animated scale and haptic in-app), then a cream-to-marigold Pro card with radio plan cards.
- **Pros:** the friendliest and most legible for the actual users. Recognition without reading (tiles, ribbons, icons). Feels Indian and premium together, and the Applied moment is the most memorable.
- **Cons:** two web fonts (Baloo 2 + Hind, about 150 KB via Downloadable Fonts). Lower density, so fewer cards per screen. Warm colours need discipline so the feed doesn't look festive. Green is both the CTA and the “eligible” colour, kept apart by shape: the button is filled, the chip is tinted.

---

## Skill references used

- **Mobile App UI Design skill** (`SKILL.md`, `references/industry-conventions.md`):
  - 60/30/10 colour rule (stated per direction)
  - ≤ 4 font sizes and 2 weights, with monospace or tabular figures for countdowns and prices
  - 8-pt grid (all spacing 4/8 multiples)
  - Soft shadows tinted to the background and inner highlights on primary buttons
  - Thumb-zone sticky actions on detail
  - Peak-end rule: Applied ✓ as the peak, the Pro card as a gentle ending
  - Status-tracking pattern (a confident “Next: city slip” status line + visual timeline)
  - Colour-coded categories with soft backgrounds (C)
  - Industry conventions: finance calm (A), crypto/terminal polish (B), health/education warmth (C)
- **UI UX Pro Max** (`search.py`):
  - `--design-system` returned *Flat Design* for “government job alerts trustworthy” and *Dark Mode (OLED)* + bold-type pairing for “high contrast dark bold minimal”. These informed B's surface-step depth and the “type as hero” countdown.
  - `--domain style` for Claymorphism (“soft rounded playful”) was used only lightly in C (soft depth, radius 16–24, no thick borders) and Exaggerated Minimalism for B's hero figures.
  - `--domain color`: the habit-tracker amber + green and warm terracotta + green rows informed C's marigold/forest/terracotta.
  - `--domain google-fonts "devanagari"`: shortlist of Noto Sans / IBM Plex Sans / Anek Devanagari. Serif options (Tiro, Kadwa) were rejected for body text.
  - `--stack jetpack-compose`: every colour comes from theme tokens (`colorScheme`, no hard-coded hex). Its anti-patterns were also checked: no emoji icons, no gray-on-gray, and no colour-only status.
  - The `pro-rules.md` pre-delivery checklist was run per direction: AA contrast in both modes, 48 dp targets, safe areas, sticky bars with content insets.
- Deliberate deviations from the skill output: the database suggested sky-blue (`#0369A1`) or purple palettes and EB Garamond / Varela Round / Plus Jakarta fonts. These were rejected because they look like the baseline the founder dislikes, or because they have no Devanagari.

## Recommendation

**C · Marigold Home**, with two borrowings: B's tabular countdown column for the feed, and A's restraint (only one marigold surface per screen).

Reasons:
1. **It fits the user.** Hindi readers on cheap phones, often with low literacy, and anxious about deadlines. C has the largest type, an icon beside every word, deadline ribbons written as sentences, and category tiles that can be recognised without reading. That serves them better than A's elegance or B's density.
2. **It is premium and new.** It shares nothing with the blue Material baseline, and the warm cream, green and marigold palette is ownable in a category where every competitor is blue, red or Telegram-grey.
3. **It has the best peak moment.** The “Well done, applied!” celebration flows straight into the trial offer, which is exactly where conversion happens (B02).
4. **It is light-first.** Light-first suits sunlight use and IPS-LCD screens, and the dark theme is still fully specified.

**Runner-up: A · Ivory & Ink** if the founder wants the most “Apple-calm” and lowest-risk look. A and C share the warm-neutral foundation, so a later shift between them is cheap. **B** works best as an optional theme or a marketing look rather than the default, because a dark-first UI on budget LCDs in daylight is a real readability risk for this audience.

## How restyling works (tokens.json)

The app's structure, components and copy stay the same. Only tokens change, then `ui/theme/*.kt` is regenerated from them.

| tokens.json key | What changes |
| --- | --- |
| `color.light.*`, `color.dark.*` | M3 roles remapped. For example, for C: `primary=#1E5B46`, `tertiary=#F2A93B` (Pro), `background/surface=#FBF5EC`, `surfaceContainerLowest=#FFFFFF`, `onSurface=#2A1E14`, `onSurfaceVariant=#6E5D4E`, `outlineVariant=#EFE4D4`. Dark values come from the tables above. |
| `color.ext.*` (SarkariColors) | `today/urgent` → the direction's urgency hue (sindoor / coral / terracotta) and containers. `success/warning` → sage, mint or forest plus ochre or amber. Add a `category.*` set for C's tiles. |
| `semantic.*` | Unchanged mapping (verdict, deadline, stage, pro). It already points at the roles above, so every state picks up the new colours. |
| `font.family.*` | A: Inter + system Noto Sans Devanagari. B: IBM Plex Sans + IBM Plex Sans Devanagari, with `font.family.numeric = IBM Plex Mono`. C: `display = Baloo 2`, `primary = Hind`. Fonts are loaded as Downloadable Fonts, so they stay out of the APK. |
| `font.scale` / M3 type roles | Collapse to the 4 sizes per direction (A 32/17/15/13 · B 40/17/15/13 · C 28/19/16/14), weights 400/600, Devanagari line height ≈ 1.5. |
| `radius.*` | Card / button / chip: A 20/14/full · B 14/12/8 · C 24/full/full. |
| `elevation.*` + new `shadow.*` | A and C: add tinted soft shadow tokens (`shadow.card`, `shadow.cta`) and set card border to 0. B: keep 0 elevation and add `surface.step1..3` + `edge.highlight`. |
| `size.buttonHeight` | A and B 48 dp, C 52 dp (touch ≥ 48 everywhere). |
| Icon style | Material Symbols variant + weight per direction: A Rounded 300 · B Sharp 300 · C Rounded filled 500. This is set once in the icon import task. |

The HTML here was generated from one shared content file and a per-direction component file, which is the same split as tokens vs composables in the app. Restyling therefore never touches screen logic or copy.
