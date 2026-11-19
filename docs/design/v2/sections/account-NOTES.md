# Account section — notes (v2, Direction C · Marigold Home)

Source: `account.html` (standalone; Google Fonts + `../marigold.css`, extra CSS inline with the `ac-` prefix).
Screenshots: `../screens/account-en-1.png` (My forms + Pro), `account-en-2.png` (Profile + Family), same for `-hi-`.
33 frames per language, each fully written in English and Hindi. Toggle at top right: English / हिंदी / Both.

## Frames (caption = ID · state (priority))

| Group | ID | State shown | P |
| --- | --- | --- | --- |
| My forms | S22 | Pro trial user, active tab: trial banner with day ring, 3 tracker rows (answer key + objection countdown, admit card + exam in 5 days, exam date changed with old → new) | P0 |
| | S22 | Free guest: `ProUpsellInline`, B16 `sync` card, rows with dashed Pro-locked "next step" | P0 |
| | S22 | Completed tab: final list (congratulations), result "not this time" with a link back to open jobs, stopped tracking | P0 |
| | S22 | Empty state with a ghost journey path | P0 |
| | S23 | Timeline top: header, action card (answer key, objection closes in 3 days), journey path with reschedule chip | P0 (Pro) |
| | S23 | Timeline scrolled: current step with "What to do" + PDF link, upcoming result, cut-off, DV/medical, final list; reassurance; stop tracking | P0 (Pro) |
| | S23 | Result stage: self-report buttons (my name is there / not there), cut-off figures, DV "no second chance" card | P0 (Pro) |
| Pro & payments | S30 | Paywall full screen: benefits (P1 ones tagged Soon), ₹199 yearly preselected, ₹49 season, sticky buy + "Try 14 days free" | P0 |
| | S30 | Dark mode, scrolled: season selected, Play-only legal text, restore | P0 |
| | B16 | Sign-in offer `pro` variant over S30 (guest, once, before the Play sheet) | P0 |
| | B08 | Trial started (guest): "until 3 Dec", no payment taken, Save with Google | P0 |
| | B09 | Trial ending, 2 days left: next alert for your forms, plans, "nothing is charged automatically" | P0 |
| | S31 | Success (confetti medal, plan summary, "all 3 forms tracked to the end", guest Google link, Add family) | P0 |
| | S31 | Pending (Play `PENDING`): 3-step path started → Google confirms → Pro on | P0 |
| | S31 | Failed: no money taken, things to try inside Google Play | P0 |
| | S32 | Manage plan: yearly, auto-renew, Manage in Play Store, Restore, receipts | P0 |
| | D08 | Restore result (found) + the not-found copy | P0 |
| | S32 | Season ends in 7 days: urgent ribbon, forms with steps coming, switch to yearly / another season, N-33 push as received | P0 |
| Profile & settings | S26 | Signed in, free: account header, completeness, Pro card, My details rows | P0 |
| | S26 | Guest on trial: guest header with Google button, trial banner, Alerts + App rows | P0 |
| | S27 | Edit field: category (radio cards, certificate year chips incl. "Not sure"), caution card, Save in top bar | P0 |
| | B10 | Re-match result: +2 new jobs, −1 no longer eligible, applied forms kept | P0 |
| | S28 | Alert settings: quiet hours, reminder-time chips, daily digest, 3-a-day cap note, backup reminder on this phone (on, "On time"), OS settings; "Saved" snackbar | P0 |
| | S29 | Delivery health: 5/6 score, 6 `HealthCheckRow`s with icon + words, Fix / Test buttons | P0 |
| | S38 | Account & data: Google account, switch account, alert phone, encrypted backup, privacy notice, download data, danger zone | P0 |
| | D03 | Delete account (signed in → Google re-auth, no OTP; Play refund note) | P0 |
| Family | S34 | Explainer + what they get, Papa linked, Didi invite sent (6 days left), add disabled at 2/2 | P1 |
| | B12 | Relation chips, optional nickname, "no mobile number needed", Create invite | P1 |
| | S48 | Invite card: QR + link + copy, 7 days / one use, waiting status, Share link (Android share sheet), Show QR, Cancel invite | P1 |
| | D06 | Remove member (member gets N-54) | P1 |
| | S35 | Member's own app: "Ravi added you", what you'll get, no number/account/sign-in, then notification permission | P1 |
| | S49 | Member home: next dates per form (urgent / good / pending), last alert, stop alerts, optional Google save, "see your own jobs" | P1 |
| | N-50…53 | Notification shade: lock-screen redacted card (channel `family`, PRIVATE) and N-50, N-52, N-53 after unlock | P1 |

Not drawn here (covered elsewhere or trivial): D02 sign-out confirm, D07 discard changes, D04 export ready, D10/D11, S24, S25, S36, S37, S39, S40, S47, O06 Play sheet (system UI).

## New component patterns (`ac-` classes)

| Class | Compose name (suggested) | Notes |
| --- | --- | --- |
| `ac-trial` + `ac-ring` | `TrialBanner`, `DaysRing` | Marigold container, conic ring with days left; one per screen (counts as the screen's marigold surface) |
| `ac-tr`, `ac-st2`, `ac-stage.{ok,q,tc,n}`, `ac-dots`, `ac-next(.lock,.win)`, `ac-in` | `TrackerRow`, `StageChip`, `StageDots` (8), `NextStepLine` | Stage chip always icon + words; dots: done = forest, current = marigold, locked = hollow; locked next line is dashed with a lock icon |
| `ac-sync` | `SyncOfferCard` (B16 `sync`) | Dismissible |
| `ac-act` (+ `.rib.urgent` / `.rib.ac-ribok`) | `StageActionCard` | Ribbon carries the deadline sentence; two pill buttons |
| `ac-resch` | `RescheduleChip` | Ochre; old date struck through, new date bold |
| `ac-what`, `ac-lk`, `ac-cut`, `ac-crit`, `ac-watch`, `ac-stop` | timeline step extras | `.stp.ac-top` aligns tall steps to the top |
| `ac-phead`, `ac-crown`, `ac-ben`, `ac-soon`, `ac-plans`, `ac-tbtn`, `ac-legal` | paywall parts | Benefits use the marigold icon tile; "Soon" pill for P1 benefits |
| `medal.ac-gold / .ac-wait / .ac-fail` | `ResultIllustration` variants | Same medal shape as the Applied peak; hourglass marigold, failure terracotta |
| `ac-kv`, `ac-forms`, `ac-tip`, `ac-note`, `ac-nexta` | small info rows | |
| `ac-list`, `ac-row(.top)`, `ac-ic.{g,m,r}`, `ac-gt`, `ac-sw(.off)`, `ac-warn` | `SettingsRow`, `SectionTitle`, `Switch` | White grouped card, inset dividers from 62 dp |
| `ac-av(.g,.sm)`, `ac-bar` | `AccountHeader` avatar, completeness bar | |
| `ac-gbtn` | `GoogleSignInButton` | Google branding colours (#fff / #1F1F1F / #747775), 52 or 44 dp |
| `ac-radio`, `ac-rb`, `ac-chip(.on,.sm)`, `ac-chips`, `ac-tf` | radio cards, filter chips, outlined text field | |
| `ac-hsum`, `ac-score`, `ac-hc.{ok,bad}`, `ac-hs` | `HealthSummary`, `HealthCheckRow` | Status in words + icon; bad row tinted |
| `ac-scrim`, `ac-sheet`, `ac-hdl`, `ac-dlg`, `ac-dacts`, `ac-danger`, `ac-shic`, `ac-chk`, `ac-delta`, `ac-mrow`, `ac-alt` | sheet, dialog, destructive button | Scrim is cocoa at 48 % |
| `ac-snack` | snackbar | |
| `ac-explain`, `ac-gets` | family explainer + "what they get" chips | |
| `ac-inv`, `ac-qr`, `ac-qlogo`, `ac-link` | `InviteShareCard` | QR is illustrative (random modules) |
| `ac-acc`, `ac-pair`, `ac-link2` | invitee header (aspirant ↔ you) | |
| `ac-fdate.{tc,ok}` | `FamilyFollowingCard` date lines | |
| `ac-shade`, `ac-clock`, `ac-lslab`, `ac-ntf(.hot)`, `ac-appi`, `ac-inapp` | notification mock-ups | |

## Decisions taken in the drafts

- One marigold surface per screen: trial banner or Pro card or plan cards; Pro CTAs are forest-green filled, except the Profile Pro card ("See ₹199/year"), which uses the marigold pill as in Direction C's tracker strip.
- The season plan is shown as **paid once, does not renew** (prepaid base plan, contract #48); the yearly plan says it renews automatically. No "cheaper than seasons" claim, because 3 × ₹49 = ₹147 is less than ₹199; yearly is sold on "no gaps mid-season".
- The trial is never a Play trial: B08/B09 say "no payment taken" and "nothing is charged automatically".
- S38 states "Your profile is stored on this phone" (contract #63) and shows an "Encrypted backup" row (#47).
- Family copy is gender-neutral ("They will stop getting…", "उन्हें…"). The aspirant's name appears as the account's given name ("Ravi"), also in Hindi.
- On the member's lock screen only "Sarkari Match · Family alerts" is shown (channel `family`, VISIBILITY_PRIVATE).

## Open questions

1. S23 for free users: the inventory sends free users to S21 from a tracker row. Should they get a read-only S23 with locked steps instead (Direction C frame 3 already shows that)?
2. Completed tab: how long do completed forms stay, and is "Not this time · N jobs open now" acceptable tone after a failed result?
3. S32 season users: is "Switch to yearly" a Play subscription replacement (proration mode) or a new purchase after expiry?
4. B09 on day 12 shows the plan picker; should it also offer "remind me on the last day"?
5. S34 limit of 2 members: is the disabled button with a reason enough, or hide it?
6. S49 "Stop alerts" is a text link; needs a 48 dp touch target in build.
7. Reschedule chip and stage chips can wrap in Hindi at 1.3× font scale; the build should allow two lines (done here with `min-height`, not fixed height).
8. QR in S48: does the QR encode the full URL with the `#k=` key fragment (contract #55)? If so the QR is denser than shown.
