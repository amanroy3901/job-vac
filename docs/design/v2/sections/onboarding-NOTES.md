# Onboarding, sign-in, privacy, permissions & delivery: v2 notes (Direction C · Marigold Home)

Page: `onboarding.html` (EN / हिंदी / Both toggle). Screenshots: `../screens/onboarding-{en,hi}-{1,2}.png`
(-1 = header + launch & onboarding, -2 = sign-in + notifications & delivery). 32 frames per language, all 360 × 800.

## Screens (frame captions)

| # | ID | Frame | P |
| --- | --- | --- | --- |
| 1 | S01 | Splash: Android 12 icon on cream + wordmark branding | P0 |
| 2 | S02 | Language: हिंदी / English radio cards, "Coming soon (11)" open, each name in its own script (07 §6 order) | P0 |
| 3 | S03 | Welcome: promise, 3 icon bullets, not-a-government-app note, "Start · takes 2 minutes", "Used it before? Sign in with Google" → O11 | P0 |
| 4–5 | S47 | Privacy notice (2 frames, top + scrolled). Leads with **"Your profile stays on your phone · matching happens on your phone"** (contract-alignment #63); what we ask / never ask / what leaves the phone / ads / under-18 minor mode / delete; optional crash-report switch (off) | P0 |
| 6 | S04 | Exam interests 1/6 (multi-select tiles, "Select all" in top bar) | P0 |
| 7 | S05 | Education 2/6 (2-col radio cards, stream dropdown, passed / result awaited segmented, "other qualifications" expander) | P0 |
| 8 | S06 | DOB 3/6 (DD / MM / YYYY boxes, live age, wrong-date warning, "stays on your phone") | P0 |
| 9 | S06 | DOB · under 18 → minor-mode card | P0 |
| 10 | S07 | Category 4/6 (OBC selected reveals optional certificate year) | P0 |
| 11 | S08 | State & domicile 5/6 (search, pinned UP/Bihar, domicile Yes/No/Not sure inside the selected card) | P0 |
| 12 | S09 | More details 6/6: gender incl. ट्रांसजेंडर, PwD Yes → type + 40%, ex-servicemen; per-question "Skip" | P0 |
| 13–16 | S10 | Your matches: peak (medallion + confetti), matching-on-phone loading, empty, minor mode (no Google button) | P0 |
| 17 | O11 | Credential Manager sheet over S10 (Google's own UI, illustrative only) | P0 |
| 18 | S10 | Guest → Google upgrade done (dark): snackbar + "Saved as …" state | P0 |
| 19 | B16 | Sign-in offer, `pro` variant over the paywall | P0 |
| 20 | D10 | Profile conflict (Google account's vs this phone's; "Suggested" on account's) | P0 |
| 21 | S13 | Notification pre-prompt (mock push, when / cap & quiet hours / push only) | P0 |
| 22 | D01 | Permission permanently denied → Settings path crumbs + "Open settings" | P0 |
| 23 | S14 | Exact-alarm primer for on-device backup reminders (date chips reuse `.dts/.dt`) | P0 |
| 24 | S15 | OEM guide · Xiaomi/Redmi (detected; step 1 done, step 2 active) | P0 |
| 25 | S15 | OEM guide · Vivo (detection unsure → brand chips) | P0 |
| 26–28 | S16 | Self-test running / passed (peak) / failed (backup reminders status, no upsell) | P0 |
| 29 | S13 | Family-member variant | P1 |
| 30–32 | — | Dark: S13, S16 passed, S16 failed | P0 |

## New patterns (all prefixed `ob-`, inline in the page; fold into marigold.css as needed)

| Class | Pattern | Notes |
| --- | --- | --- |
| `ob-bar` | Sticky action bar (thumb zone) | Solid `--bg` with a 32 px fade above (`:before`) instead of the `.abar` glass: content scrolling under the glass read as a glitch. Primary pill + optional `ob-tlink` / caption, gap 8 |
| `ob-tlink` (`.m` muted) | Text button, 48 px min height | Primary-green or muted; used for "Not now", "Used it before?", "Later" |
| `ob-steprow` + `ob-prog` | Step header "Step n of 6" + 8 px forest progress bar | Back button uses `.tb .ib.c` |
| `ob-q`, `ob-help` | Question (t28) + helper (t16 mut) paddings | |
| `ob-hero` (`.pri`, `.tc`) | 64 px rounded icon badge on marigold / forest / terracotta container | |
| `ob-tiles` + `ob-tl(.on)` | Multi-select tile (category `.tile` icon + label + sub, check at top right) | Selected = 2.5 px forest ring + filled check (same as `.plan.sel`) |
| `ob-list` + `ob-opt(.on, .sm)` + `ob-rad(.on)` | Radio card, single select | `ob-langc` = 72 px language card; `ob-g2` = 2-col grid of `ob-opt.sm`; `ob-sub` = revealed follow-up inside the selected card |
| `ob-chips` + `ob-chip(.on)` | Choice chip, 48 px | Tinted when on (button stays filled: shape rule from direction C) |
| `ob-qb`, `ob-qi`, `ob-skip` | Question block card with icon, label and per-question Skip | S09 |
| `ob-seg` | 2-option segmented control | |
| `ob-sel`, `ob-search`, `ob-box(.on)`, `ob-field` | Dropdown field, pill search, numeric DOB box (Baloo 28, focus ring) | Field outlines use `--mut` 1.5 px (6.3:1) |
| `ob-age` | Live computed-age banner (forest container) | |
| `ob-note` (`.q`) | Inline note with icon (sunk or ochre) | |
| `ob-bul`, `ob-bi(.pri)` | Icon-tile bullet row (40 px marigold tile) | S03, S13 |
| `ob-panel`, `ob-notif`, `ob-nic`, `ob-nact` | Marigold gradient panel + static notification mock | S03, S13 |
| `ob-key`, `ob-pv`, `ob-sw(.on)`, `ob-tgl` | Privacy key fact, privacy item row, switch | |
| `ob-soon`, `ob-sgrid`, `ob-sl`, `ob-sn`, `ob-f-{code}` | Coming-soon language grid (3 col) | In-app uses system Noto; the page loads Noto from Google Fonts as a stand-in |
| `ob-minor` | Minor-mode card (marigold gradient + ring, like `.pcard`) | |
| `ob-mj`, `ob-comp`, `ob-ring`, `ob-skel`, `.medal.ob-wait` | Mini job row, completeness row with ring, skeleton, waiting medallion | |
| `ob-snack`, `ob-gsaved` | Snackbar (inverse) and "Saved as {email}" state | |
| `ob-scrim`, `ob-sheet`, `ob-handle`, `ob-dlg` | Bottom sheet and centred dialog (radius 28) | |
| `ob-gbtn` | Google Sign-In button (white / #747775 outline; dark #131314 / #8E918F) | Official colours, not themed. In-app: Roboto Medium per Google branding; mock uses Hind |
| `ob-gsheet`, `ob-g*` | Credential Manager sheet mock | Google's UI, never built by us |
| `ob-oem(.done)`, `ob-num(.next,.done)`, `ob-done`, `ob-srow`, `ob-brands` | OEM step card, numbered step, "Done" checkbox, brand chip row | |
| `stp.ob-fail`, `ob-recents`, `ob-backup` | Failed self-test step, Recent-apps hint, backup-reminder status card | Self-test reuses direction-C `.path/.stp` |

Global additions: `text-wrap:balance` on `.t28/.t19` and `pretty` on `.t16/.t14` inside phones (stops one-word
orphans such as "हैं" in Hindi titles). All new text pairs checked ≥ 4.5:1 (e.g. hero icon #9A5B00 on #FDEFD6 = 4.8:1,
field outline 6.3:1); `#B06E00` from direction C was swapped for `#9A5B00` on hero/bullet icons for more margin.

## Open questions

1. **Crash-report switch on S47:** frontend docs say Crashlytics is opt-in "on S47/S38". Placed as one optional switch
   (default off). Does that break the "no checkbox wall" rule? Alternative: only on S38.
2. **Minor mode end:** the copy says "ends by itself on your 18th birthday". Confirm that sign-in and ads offers then appear
   (and when: on the next app open?), and whether a 17-year-old may buy Pro (frontend README Q4).
3. **S10 count with on-device matching:** the loading copy names "312 open notices"; this should come from the catalog
   size on the phone. Is the slow-network state still needed if matching is local (only catalog sync can be slow)?
4. **D10 wording:** the "Google account" profile is the encrypted backup blob. Is "Saved 12 Oct 2026" available from
   backup metadata? Contract row #46 says D10 may happen without a 409.
5. **S13 family variant name:** `aspirant_name` source is open (contract #55); the mock uses the first name.
6. **S15 brands:** Vivo steps written from FuntouchOS menus. Need a device check, and the deep-link table per brand.
7. **S14 day-before time:** the mock shows 7 pm for the day-before backup; 04-screens gives only 8:15 am for the last day.
   Confirm the R1 backup time.
8. **B17 (Why ads)** is linked from S47's ads row but not drawn here (it belongs with the feed/ad slot section).
   S41/S42 (force update, outage) from the splash route are also left to the section that owns them.
