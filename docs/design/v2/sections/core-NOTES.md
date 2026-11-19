# Core loop · Direction C (Marigold Home) — notes

Source: `core.html` (EN/HI toggle, top right). Screenshots: `../screens/core-en-1.png` / `-2.png`, `core-hi-1.png` / `-2.png`
(-1 = home + job detail, -2 = push + sheets + history). Theme: `../marigold.css` (unchanged); extra CSS is inline with prefix `co-`.

## Screens (28 per language)

| Group | ID · frame | P |
| --- | --- | --- |
| Home S20 | S20 top: today + closing in 3 days (reuses direction C frame 1) | P0 |
| | S20·end free: later + check yourself + end marker + ad O10 (label row with “Why?” → B17, “Remove ads” → S30) + disclaimer | P0 |
| | S20·end·pro: same without ad, Pro chip in top bar, “Pro · no ads” end card | P0 |
| | S20 + O02 + O04: notifications-off banner, undo snackbar after hide | P0 |
| | S20·loading: skeleton + “matching 312 open notices on this phone…” | P0 |
| | S20·empty (no matches yet) · S20·done (all answered) | P0 |
| | S20·SC-17: “Did you apply for these?” grouped card | P0 |
| Job detail S21 | S21 hero + extended banner (reuse 2) · S21·2 what you need + official links (reuse 2b) | P0 |
| | S21·3 why you match ✓ / ? / i + legend + “matched on this phone” footer + report link → B05 + change log | P0 |
| | S21·ineligible (SC-19): ✗ chip, grey calendar, corrigendum banner → B07, ✗ row, links → S27 / B05, no action bar | P0 |
| | S21·today: ribbon in hero, back-from-site prompt → B01, bar = Applied + Open site (no snooze) | P0 |
| | S21·applied (free): applied chip, journey with Pro-locked stages, bar strip + “Get the next alerts — Pro” | P0 |
| Push O05 | Lock screen: N-05 (Open site · Applied), N-01 (Applied · Later), N-21 | P0 |
| | Shade: N-03 expanded (Applied · Later), action results (“✓ Recorded”, “we’ll remind you 9 am”), group summary | P0 |
| | Family member phone: private lock-screen form + N-51 / N-52 after unlock, no actions | P1 |
| Sheets | B01 confirm applied · B02 celebration + 14-day trial (trial variant) · B03 not interested + reasons · B04 snooze (2–3 days variant) · B06 check-yourself (notice-ambiguity variant) · B06·done “I’m eligible” confirmation · B05 report wrong match · B07 corrigendum | P0 |
| History | S24 alert history · S44 digest (ad at end) · S25 past & hidden (“Not interested” tab) | P1 |

## New patterns / classes (`co-`)

- `co-sheet` + `co-scrim` + `co-hdl` + `co-sth` (icon tile title; `.grn` / `.q` tints) + `co-job` (job summary row in a sheet).
- `co-fc` filter chip (40 dp visual, 8 dp gaps = 48 dp target; `.on` = forest tint + check) · `co-rad` radio card (64 dp) · `co-cbrow` checkbox row · `co-tf` text field.
- `co-local`: lock note for anything kept on the device (B03 reasons, B04 snooze, S25), per matching-engine §0.
- `co-ad`: O10 with a separate label row (Ad · Why? · Remove ads) above the creative.
- `co-ban` banner (terracotta tint + pill action), `co-snk` cocoa snackbar with marigold action (≈ 8:1).
- `co-sk` skeleton, `co-spin`, `co-sync` status line; `co-emp` + `co-ill` (dashed-ring illustration; `.grn` = done).
- `co-end` end-of-feed marker, `co-endc` calm info card, `co-new` marigold “New” tag.
- `wy.co-no` (✗) and `wy.co-in` (i) rule rows; `vchip.co-no`, `vchip.co-ap`; `cal.co-caloff` (grey) / `cal.co-calq` (ochre).
- `co-ret` back-from-site prompt, `co-done` applied strip, `co-df` before → after rows, `co-eff` effect box, `co-mj` mini journey (B02).
- `co-lk` / `co-shade` phone variants, `co-ntf` notification card (`.crit` terracotta, `.fam` plum icon), `co-na` action pills, `co-anno` dashed marigold annotation (spec note, not UI).
- `co-bot` + `co-tbfix`: simulates a scrolled feed (content pinned above the nav).
- Override: `.abar` background raised to 97 % opacity so scrolled text does not show through under the gesture bar.

## Decisions and open questions

1. **Date fix:** 19 Nov 2026 is a Thursday, so 21 Nov is **Saturday**. Direction C’s calendar tile said “Friday”; corrected here. Please fix `direction-c.html` too.
2. **Feed groups:** today / closing in 3 days / later / check yourself (non-urgent check-yourself jobs only; urgent ones stay in their urgency group). The greeting pills count time only (1 / 1 / 3), so “3 later” includes the check-yourself job. Inventory’s separate “New” and “Waiting for reply” sections are shown as a “New” tag on the card. Confirm we can drop them as sections.
3. Home top bar has a bell and an overflow menu (Past & hidden, share card, help). The help icon from 04 has moved into the overflow menu.
4. **Notification accent:** 05 still says `#1F4FB8`. Proposed: forest `#1E5B46` (critical: terracotta `#B3361E`, family: plum `#7A3D80`). The system draws the notification, so Hind is used in the mock only for Devanagari rendering.
5. B02 shows only the trial variant. The plans variant reuses direction C frame 4. The trial is server-side and needs no Play subscription, so the copy says “Ends by itself”. Billing needs to confirm.
6. B05: the profile attributes are **opt-in** (“Also send my age and category”, off by default), per matching-engine §5. Removed the old “we’ll check within 24 h” promise. What should the snackbar say instead?
7. B06·done: the verdict chip stays “Check yourself”, and the confirmation is stored only on the phone. Should `confirm_eligible` still be counted anywhere (for example, in coarse opt-in counters)?
8. S21·today: no snooze on the last day. The space above the bar says “No snoozing on the last day” instead of “Not interested”, which stays in the overflow menu. Confirm.
9. Not drawn (reuse other sections or old specs): B08/B09 trial sheets, B17 why ads, O01 offline / O03 outage banners (same `co-ban` with `cloud_off` / `report`), S21 not-found / expired / cancelled, B04 ≤1-day info variant, B06 missing-profile variant, dark mode (direction C frames 1 and 4 cover it).
