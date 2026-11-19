# v2 · States, account & data, dark, a11y, locales: notes

Direction C · Marigold Home. File: `states.html` (uses `../marigold.css` unchanged; extra CSS inline, prefixed `st-`).
Frames are generated from one bilingual content script (`t(en, hi)`), so EN and HI always match. Toggle at top right.
Screens: `../screens/states-{en,hi}-1.png` (states), `states-{en,hi}-2.png` (account, minor mode, a11y),
`dark-1.png` (EN) / `dark-2.png` (HI), `locales.png` (Tamil + Urdu).

## Screen IDs covered

| Section | Frame | ID | P |
| --- | --- | --- | --- |
| A States | Offline banner + cached list + queued-action snackbar | O01 · S20 | P0 |
| | Notifications-denied banner + undo snackbar (6 s, timer ring) | O02 + O04 · S20 | P0 |
| | Server outage banner with cache | O03 · S20 | P0 |
| | Service problem, full screen (no cache) | S42 | P0 |
| | Force update, blocking | S41 | P0 |
| | Profile edit → re-match result sheet | S27 → B10 | P0 |
| | Newly eligible after corrigendum | S21 · N-11c (SC-19 X3) | P0 |
| | No longer eligible after corrigendum | S21 · N-11a (SC-19 X1) | P0 |
| | Deadline passed: “Did you fill these forms?” grouped card | S20 · SC-17 | P0 |
| | Notice cancelled (applied user) | S21 · SC-18 | P0 |
| | Age limit crossed sheet | B14 | P1 |
| | Why ads? sheet from the ad slot | B17 · O10 | P0 |
| B Account | Account & data, signed in | S38 | P0 |
| | Account & data, guest + delete explainer expanded | S38 · guest | P0 |
| | Export ready | D04 | P0 |
| | Delete, signed in: Google re-auth, Play-subscription step | D03 · Google | P0 |
| | Delete, guest: type the word | D03 · guest | P0 |
| | Sign out | D02 | P0 |
| | Minor mode explained | S10 · minor (`Minor(ageYears)` state) | P0 |
| | Profile in minor mode, what’s off | S26 · minor | P0 |
| C A11y | Home card at font scale 2.0 (full-scroll capture) | S20 @200% | P0 |
| | Job detail at font scale 2.0 (full-scroll capture) | S21 @200% | P0 |
| | TalkBack focus order + announcement, match card | S20 · TalkBack | P0 |
| D Dark | Home, job detail, why you match + what you need, application timeline (Pro), paywall, alert settings | S20, S21, S21, S23, S30, S28 | P0 |
| E Locales | Home + job detail in Tamil; home + job detail in Urdu (RTL) | S20/S21 · ta, ur | preview |

The onboarding designer handed off B17, S41 and S42. All three are here.

## New patterns (`st-` components)

- **Banner `st-ban`** with variants `off` (offline, sunk), `warn` (permission, ochre), `err` (our outage / notice
  changed, terracotta) and `ok` (new chance, forest). Optional pill action on the card surface, 44 dp min height. Home shows at most one banner.
- **Snackbar `st-snack`** on inverse surface. The undo variant has a small countdown ring (6 s). Polite live region.
- **Sheet `st-sheet` / dialog `st-dlg`** on a cocoa scrim (52%) in light and a black scrim (62%) in dark. The dialog
  surface is `#2D241B` in dark (one step above the card). Destructive confirms are terracotta *text* buttons, and the expanded
  delete uses an outlined terracotta button (`btn.st-danger`). Neither uses the filled green CTA.
- **Settings row `st-row`** (40 dp icon tile, title, value, chevron/switch) inside `st-list` cards. A `danger` variant is used
  for delete. An `off` variant + `st-tag` “From 18” lock pill marks rows disabled in minor mode, so nothing silently disappears.
- **Switch `st-sw`** and radio `st-radio` use the new outline token for their off state (WCAG 1.4.11 non-text ≥ 3:1).
- **Data-model line** under the account card: “Your profile and matches are stored only on this phone”. This reflects
  the on-device matching decision (matching-engine §0). Offline, outage, export and sign-out copy all follow from it.
- **Verdict chip `vchip.no`** (terracotta container) and **rule bubble `wy.no`** for “not eligible now”, plus a
  `st-chg-tag` “Changed” tag on the rule that a corrigendum altered.
- **Google button `st-g`** keeps Google’s official light/dark colours (the same values the onboarding section uses). It is 48 dp tall for touch.
- **Full-screen state `st-center` + `st-illu`**: a 140 dp tinted disc with a dashed halo, the house style for S41/S42.
- **Annotation layer `st-fo`**: magenta (#C2185B) outlines and numbers. They are clearly not UI, and they are only used in spec frames.

## Dark-mode token notes

- Uses the direction C dark palette from `marigold.css` unchanged. This is the same `.ph.dark` set as `onboarding.html`.
  Like onboarding, marigold icons switch to `var(--mari)` in dark.
- New st- tokens, as light / dark pairs: snackbar `#2A1E14`/`#F6EDE1`, snackbar action `#F4B755`/`#1E5B46`, dialog `#FFFFFF`/`#2D241B`,
  outline `#8F7B66`/`#8C7A66`, skeleton `#EFE4D4`/`#3A2F24`.
- Measured contrast in dark: text 16:1; muted on card 8.1:1, on sunk 7.3:1; forest on its container 7.8:1; ochre 8.5:1;
  terracotta on card 7.3:1; today ribbon (dark cocoa on `#FF8A6B`) 7.7:1; onMari on marigold 9.4:1; snackbar action
  6.9:1. Outline vs card is 4.1:1 (dark) and 4.0:1 (light). The hairline (1.3:1) is used only as decoration.
- Fix: the key-dates “past” chip used `opacity:.6`, which dropped muted text in dark to about 3:1. In `states.html` it now uses
  muted colour at full opacity. Suggest moving that into `marigold.css` (I did not edit it).

## Accessibility notes

- 200% uses Android 14 non-linear scaling (14→28, 16→30, 19→34, 28→47 sp). Rules shown: titles and ribbon wrap, the
  facts row and two-button rows stack full width, chips wrap, nothing truncates, and icons stay in dp. The sticky action bar
  on S21 stacks to about 26% of the screen, so “Not interested” moves into the overflow menu at ≥ 1.5×.
- The wordmark “Sarkari Match” is treated as a brand image and not scaled. Bottom-nav labels are capped at 1.5×. Both choices are open questions below.
- TalkBack: the match card is one merged stop that reads the deadline first, then title, verdict, fee, education and the age rule
  spoken in words (“plus 5 for OBC, so 27”). The inner Applied/Later buttons stay separate stops for explore-by-touch. Custom actions
  use the same verbs as the buttons. Section headers are headings.

## Localization notes

- Tamil: Hind/Baloo 2 first with per-glyph fallback to **Noto Sans Tamil**, so Latin (brand, exam names, OBC) matches the
  launch look. Line height ×1.05. The card’s two buttons stack because “விண்ணப்பித்துவிட்டேன்” fills the width. Nav is 88 dp.
- Urdu: **Noto Nastaliq Urdu** (Noto Naskh Arabic fallback), line height ×1.6, labels ≤ 14 at weight 400. `dir="rtl"` is set on
  the app window only, and the status bar stays LTR. Mirrored: top bar, back arrow, section count, nav order, badge/unread dot,
  greeting ornament, and the corrigendum arrow (14 ← 21). Not mirrored: clock/timer/check/? icons, ₹, digits. LTR runs (names,
  “OBC +5 = 27”) are wrapped in `<bdi>`.
- Notice titles stay as published (English) in ta/ur, per 07 §1. Every frame carries “PREVIEW — translation not reviewed”
  in English and in the language itself.

## Open questions

1. **Sign-out copy conflict.** 04-screens D02 / SC-39 say sign-out clears local data and that “the profile is safe in your Google
   account”. frontend/data-layer.md (on-device model) keeps the local profile as a guest and offers an opt-in “delete everything”. I followed
   data-layer. 04/03 need updating.
2. **Export.** SC-38 still says `POST /export`. Here export is local JSON (`exportLocal()`), and the server holds only the account summary. Should
   the file also include `GET /v1/me` server data (account id, purchases, family links)?
3. **Delete with active Pro.** Is “Open Play subscriptions” a soft nudge, as shown, or a required step before delete is enabled?
4. **Minor mode.** Should a 17-year-old see the “Pro” row at all, or only the “From 18” lock (shown)? Legal review is pending (backend Q2).
   The birthday prompt date is computed from the DOB on the phone.
5. **200% caps.** Capping nav labels at 1.5× and not scaling the wordmark is common Android practice, but it needs sign-off against WCAG 1.4.4.
6. **Direction C reference bug.** `direction-c.html` labels 21 Nov 2026 as “Friday”, but it is a Saturday. Fixed in these frames.
7. **Tamil headings.** Baloo has a Tamil sibling (Baloo Thambi 2) that would keep the rounded heading voice. 07 specifies Noto Sans Tamil. Keep 07?
8. **B14 copy.** The limits in the “still open” list are illustrative. The real list comes from the catalog for the user’s exam interests.
