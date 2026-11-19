# 05 — Notifications: Push, on-device backup, family, digest

Scheduling rules (slots, daily cap 3, quiet hours 22:00–07:00, snooze, collapse) are in [03 §0.2](03-scenarios.md). This file defines **what each message looks like**.

**2026-11-19:** push (FCM) is the only message channel. WhatsApp templates (W-00…W-91, F-01…F-09) and SMS (SMS-01…03, OTP) are **retired** (§4, §5); family alerts are now pushes to the member's own app (N-50…N-54, channel `family`); the on-device backup reminder (§3.6) is the fallback. All text is built on the device from string keys in the app language (07), so every template below exists in each enabled language.

## 1. Android channels (created at first launch; IDs are permanent)

These **seven** IDs are the **canonical set** for app, API (`PushData.channel`) and backend (six agreed 2026-11-19 in `../contract-alignment.md`; `family` added the same day with the family-link redesign). The server sends them as-is.

| Channel ID | Name HI / EN | Importance | Sound / vibrate | Lock screen | Used by |
| --- | --- | --- | --- | --- | --- |
| `critical` | आखिरी तारीख और ज़रूरी सूचना / Last dates & critical | `IMPORTANCE_HIGH` (heads-up) | default sound + vibrate pattern `[0,250,150,250]` | `VISIBILITY_PUBLIC` | N-04, N-05, N-21, N-22, N-27, N-41 test |
| `new_match` | नई नौकरी / New job matches | `IMPORTANCE_HIGH` | default | `PUBLIC` | N-01, N-11c |
| `reminders` | याद दिलाना / Reminders | `IMPORTANCE_DEFAULT` | default | `PUBLIC` | N-02, N-03 |
| `job_updates` | नोटिस में बदलाव / Notice changes | `IMPORTANCE_DEFAULT` | default | `PUBLIC` | N-10–N-12, N-20, N-23–N-26, N-28, N-29 |
| `digest` | रोज़ का सारांश / Daily digest | `IMPORTANCE_LOW` | none | `PUBLIC` | N-30 |
| `account` | अकाउंट और Pro / Account & Pro | `IMPORTANCE_LOW` | none | `PRIVATE` | N-31–N-33, N-40, N-42–N-47, N-54 |
| `family` | परिवार के अलर्ट / Family alerts | `IMPORTANCE_HIGH` | default sound + vibrate | `PRIVATE` (shows "Sarkari Match · परिवार अलर्ट" on the lock screen, details after unlock) | N-50–N-53 (created only when this phone accepts a family invite) |

- Channel names follow the app language at creation; on language switch, update names via `createNotificationChannel` (same ID).
- Small icon: monochrome `ic_stat_sm` (24 dp, white tick inside rounded square). Accent colour `primary` (`#1F4FB8`); `critical` uses `today` red as `setColor` only for R-last.
- **Grouping:** job pushes share group key `jobs`; when ≥ 2 visible post a summary (`InboxStyle`, title "Sarkari Match", summary "{n} नौकरियाँ आपका जवाब माँग रही हैं"). Lifecycle pushes group `forms`.
- **Notification ID** = stable hash of `match_id` (+ `stage` for lifecycle, keyed by `application_id`) so a newer reminder replaces the older one; tag = push `collapse`.
- **Delivery:** FCM **data-only, priority `high`** for N-01/N-04/N-05/critical lifecycle; `normal` for the rest. App builds the notification in `FirebaseMessagingService.onMessageReceived` (localised strings, actions, grouping) and posts `received` + `displayed` receipts (`POST /v1/me/delivery-receipts`). TTL: reminders `lastDate − now`; test 10 min.
- **Actions** use `BroadcastReceiver` → WorkManager expedited job (API call), then replace the notification with a confirmation (no app open). Action labels ≤ 15 characters.
- **Tap** → `PendingIntent` (FLAG_IMMUTABLE) to deep link (01 deep-link table) with `src=push&nid={template}`.
- **No ads, sponsored lines or coaching promotions in any push** (free tier ads exist only in-app, 06 §J1).
- **Language:** title/body are string resources (`n01_title`, `n01_body` …) filled with payload values on the device, so a language switch applies to the next push immediately. Length limits in §2 apply per language (07 §3); the server never sends display text except job short titles and rule fragments, which it renders in the user's `lang`.
- Every job push body ends with nothing promotional; the app name in header is the "not official" signal; the S21 screen carries the disclaimer.

## 2. Length limits (enforced by the string lint and the server renderer)

| Field | Limit | Why |
| --- | --- | --- |
| Title | ≤ 45 characters (Unicode code points) **in every language** (Tamil/Malayalam translators shorten, not truncate) | collapsed title fits one line on 360 dp at 100 % font |
| Body (collapsed) | first 60 chars must carry the key fact | one-line preview |
| Body (`BigTextStyle`) | ≤ 200 characters | 4–5 lines expanded |
| Action label | ≤ 15 characters | 3 actions fit |
| Job short name `{job}` | ≤ 28 characters (curated field `shortTitle`, e.g. "SSC GD कांस्टेबल 2027") | — |

Variables: `{job}` short title · `{org}` "SSC" · `{date}` "21 नवं"/"21 Nov" · `{time}` "रात 11 बजे"/"11 pm" · `{n}` integer · `{fee}` "₹100" or "कोई फ़ीस नहीं" · `{cat}` "OBC" · `{stage}` · `{old}`/`{new}`.

## 3. Push templates

### 3.1 Matches & reminders (Free + Pro)

| ID | Slot | Channel | Title HI | Body HI | Title EN | Body EN | Actions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| N-01 | R0 new match (eligible) | new_match | नई नौकरी: आप योग्य हैं ✓ | {job} · आखिरी तारीख {date} · फ़ीस {fee} ({cat}) | New job: you're eligible ✓ | {job} · Last date {date} · Fee {fee} ({cat}) | भर दिया · बाद में |
| N-01b | R0 new match (check yourself) | new_match | नई नौकरी: एक बात जाँच लें | {job} · {rule_short} — बाकी सब ठीक · आखिरी तारीख {date} | New job: check one thing | {job} · {rule_short} — rest OK · Last date {date} | देखें |
| N-02 | Rp periodic | reminders | {job} — फ़ॉर्म अभी भरा नहीं? | {n} दिन बाकी · आखिरी तारीख {date} | {job} — not applied yet? | {n} days left · Last date {date} | भर दिया · बाद में |
| N-03 | R3 | reminders | 3 दिन बाकी: {job} | आखिरी तारीख {date}। फ़ोटो {photo_kb} KB, हस्ताक्षर {sig_kb} KB तैयार रखें। | 3 days left: {job} | Last date {date}. Keep photo {photo_kb} KB and signature {sig_kb} KB ready. | भर दिया · बाद में |
| N-04 | R1 | critical | कल आखिरी दिन: {job} | फ़ीस {fee} · कल {time} तक फ़ॉर्म भरें। | Last day tomorrow: {job} | Fee {fee} · Apply by {time} tomorrow. | भर दिया · रुचि नहीं |
| N-05 | R-last | critical | आज आखिरी दिन: {job} | आज {time} तक। साइट पर भीड़ होती है — जल्दी भरें। | Last day today: {job} | Until {time} today. Sites get slow — apply early. | साइट खोलें · भर दिया |

Notes: the ✓ in titles is a text character in the push (acceptable; push text is content). "साइट खोलें" opens the official apply URL via `ACTION_VIEW` directly and logs `apply_site_open{src=push}`. No "Snooze" on N-04/N-05 (cannot be snoozed).

Action result replacements (same ID, `reminders` channel, auto-cancel 10 s via `setTimeoutAfter`):
- भर दिया → "✓ दर्ज हो गया: {job}" / body "Pro से एडमिट कार्ड और रिज़ल्ट की सूचना पाएँ" (free) or "आगे की हर सूचना मिलेगी" (Pro).
- बाद में → "ठीक है — कल सुबह 9 बजे फिर याद दिलाएँगे".
- रुचि नहीं → "छुपा दिया: {job}" + action "वापस लाएँ".

### 3.2 Notice changes

| ID | Event | Title HI | Body HI | Title EN | Body EN |
| --- | --- | --- | --- | --- | --- |
| N-10 | Deadline extended | तारीख बढ़ी: {job} | अब आखिरी तारीख {new} (पहले {old}) | Deadline extended: {job} | New last date {new} (was {old}) |
| N-11a | Corrigendum → not eligible | नोटिस बदला: {job} | बदलाव के बाद आप योग्य नहीं रहे — {reason}। ब्योरा देखें। | Notice changed: {job} | You're no longer eligible — {reason}. See details. |
| N-11b | Corrigendum affects Applied | नोटिस बदला: {job} | आपकी पात्रता पर असर हो सकता है। आधिकारिक बदलाव देखें। | Notice changed: {job} | This may affect your eligibility. Check the official change. |
| N-11c | Corrigendum → newly eligible | नया मौका! {job} | बदलाव के बाद आप योग्य हैं · आखिरी तारीख {date} | New chance! {job} | After a change you're eligible · Last date {date} |
| N-12 | Notice cancelled | भर्ती रद्द: {job} | {org} ने यह भर्ती रद्द कर दी है। आधिकारिक सूचना देखें। | Recruitment cancelled: {job} | {org} has cancelled this recruitment. See the official notice. |
| N-13 | Date moved earlier (rare) | तारीख पहले हुई: {job} | आखिरी तारीख अब {new} (पहले {old}) | Deadline moved earlier: {job} | Last date now {new} (was {old}) |

### 3.3 Lifecycle (Pro / trial)

| ID | Stage | Channel | Title HI | Body HI | EN title |
| --- | --- | --- | --- | --- | --- |
| N-20 | City slip | job_updates | परीक्षा शहर की सूचना: {job} | आपका परीक्षा शहर देखें — परीक्षा {date} से | Exam city slip out: {job} |
| N-21 | Admit card | critical | एडमिट कार्ड आ गया: {job} | परीक्षा {date}। डाउनलोड करें और 2 प्रिंट रखें। | Admit card out: {job} |
| N-22 | Reschedule | critical | परीक्षा की तारीख बदली: {job} | {old} → {new}। नया एडमिट कार्ड आने पर बताएँगे। | Exam date changed: {job} |
| N-23 | Exam tomorrow | job_updates | कल परीक्षा: {job} | रिपोर्टिंग {time}। एडमिट कार्ड और फ़ोटो ID साथ रखें। | Exam tomorrow: {job} |
| N-24 | Answer key | job_updates | आंसर की आई: {job} | आपत्ति {date} तक · {fee_per_q} प्रति प्रश्न | Answer key out: {job} |
| N-25 | Result | job_updates | रिज़ल्ट आ गया: {job} | PDF में अपना रोल नंबर देखें। | Result out: {job} |
| N-26 | Cut-off | job_updates | कट-ऑफ़ आया: {job} | {cat}: {marks} अंक | Cut-off out: {job} |
| N-27 | DV / medical | critical | DV की तारीख: {job} | {date} {time}, {venue_short}। न जाने पर दोबारा मौका नहीं मिलेगा। | Document verification: {job} |
| N-28 | Clash (P1) | job_updates | परीक्षा टकराव | {job1} और {job2} — {date} को | Exam clash |
| N-29 | Certificate (P1) | job_updates | सर्टिफ़िकेट जाँचें: {job} | आपका {cert} {cert_date} का है — इस नोटिस के लिए नया चाहिए हो सकता है। | Check your certificate |

### 3.4 Account, Pro, system

| ID | Event | Channel | Title HI | Body HI | EN |
| --- | --- | --- | --- | --- | --- |
| N-30 | Digest (P1) | digest | आज का सारांश · {n} अपडेट | `InboxStyle` ≤ 5 lines: "नई: {job} · {date} तक", "बदलाव: {job} — तारीख बढ़ी", … + "+{k} और" | Today's summary · {n} updates |
| N-31 | Trial ending (day 12) | account | Pro ट्रायल 2 दिन में ख़त्म | आपके {n} फ़ॉर्म की अगली सूचना: {stage}। ₹17/महीना से जारी रखें। | Pro trial ends in 2 days |
| N-32 | Pending purchase done | account | Pro चालू हो गया ✓ | {date} तक हर ज़रूरी सूचना मिलेगी। | Pro is active ✓ |
| N-33 | Season ending | account | Pro सीज़न 7 दिन में ख़त्म | साल भर का प्लान ₹199 (₹17/महीना) | Pro season ends in 7 days |
| N-40 | Profile incomplete (local, day 1) | account | प्रोफ़ाइल 1 मिनट में पूरी करें | पूरी प्रोफ़ाइल से ही आपकी नौकरियाँ मिलेंगी। | Finish your profile in 1 minute |
| N-41 | Self-test | critical | टेस्ट सूचना ✓ | यह सूचना आ गई — मतलब आपके अलर्ट पहुँचेंगे। टैप करें। | Test alert ✓ |
| N-42 | Report outcome | account | आपकी रिपोर्ट: {job} | नोटिस के हिसाब से मैच सही है — अपनी प्रोफ़ाइल में {field} देखें। | Your report: {job} |
| N-43 | Age crossed (P1) | account | {exam}: उम्र सीमा पार | आपके लिए अब भी खुली नौकरियाँ देखें। | Age limit crossed |
| N-44 | Family accepted (P1) | account | {relation} ने परिवार अलर्ट स्वीकार किया | अब आखिरी तारीख, एडमिट कार्ड और DV की सूचना उनके फ़ोन पर भी जाएगी। | {relation} accepted family alerts |
| N-45 | Referral reward (P1) | account | 1 महीना Pro मुफ़्त मिला | आपके दोस्त ने पहला फ़ॉर्म भरा। | You got 1 month Pro free |
| N-46 | Fee unpaid check (P2) | reminders | फ़ीस भर दी? {job} | फ़ीस की आखिरी तारीख {date}। फ़ीस बिना फ़ॉर्म पूरा नहीं होता। | Fee paid? {job} |
| N-47 | Alerts moved to another phone (new 2026-11-19) | account | अलर्ट अब दूसरे फ़ोन पर | आपके अलर्ट अब {device} पर आएँगे। इसी फ़ोन पर चाहिए तो ऐप खोलें। | Alerts moved to {device} |
| — | Weekly ping | — (data only, no UI) | — | — | — |

### 3.5 Family alerts (Pro, P1) — on the family member's own phone

Sent only to linked members (SC-32). Channel `family`. Push `type` = `family`. Payload adds `family_link_id`,
`aspirant_name` (first name or the nickname the member gave), `stage`. Never contains DOB, category, state or the
aspirant's account. Tap → S49 `family/following`. No actions (the member cannot mark Applied).

| ID | Trigger (aspirant's slot) | Title HI | Body HI | Title EN | Body EN |
| --- | --- | --- | --- | --- | --- |
| N-50 | R1 (aspirant has not marked Applied) | {name}: कल आखिरी दिन | {job} का फ़ॉर्म कल {time} तक भरना है। उन्हें याद दिला दें। | {name}: last day tomorrow | {job} form closes tomorrow at {time}. Remind them. |
| N-51 | R-last | {name}: आज आखिरी दिन | {job} का फ़ॉर्म आज {time} तक। | {name}: last day today | {job} form closes today at {time}. |
| N-52 | Admit card out (Applied) | {name} का एडमिट कार्ड आ गया | {job} · परीक्षा {date} | {name}'s admit card is out | {job} · exam {date} |
| N-53 | DV / medical slot | {name} की DV तारीख | {job} · {date} — न जाने पर दोबारा मौका नहीं मिलता। | {name}'s document verification | {job} · {date} — there is no second chance. |
| N-54 | Link removed by the aspirant (channel `account`) | परिवार अलर्ट बंद | अब आपको {name} के अलर्ट नहीं आएँगे। | Family alerts stopped | You will no longer get {name}'s alerts. |

Family pushes are suppressed when the aspirant has already acted (Applied stops N-50/N-51) and respect the
**member's** quiet hours except N-51 and N-53 within 24 h.

### 3.6 On-device backup reminders (all users, P0)

Not a server push: the app schedules a local notification for each open match's R1 and R-last from the local
reminder mirror (same `reminder_id`), and cancels it when the FCM push with that id arrives. It uses the same
templates N-04/N-05 and channel `critical`, with a short body suffix "(फ़ोन से याद दिलाया)" / "(reminder from
your phone)" so support can tell them apart. Fires at R1 slot + 30 min and R-last 08:15. Needs exact alarms on
Android 12+ (S14); without them ±15 min. It is the reason S28's "बिना इंटरनेट भी याद" row is on by default.

## 4. WhatsApp Business templates — retired 2026-11-19

Founder decision 7: **no WhatsApp messaging at all**. The IDs below are retired (never reused) and must be removed
from backend template stores, payloads and analytics: `W-00 sm_welcome`, `W-01 sm_new_match`, `W-02 sm_deadline_soon`,
`W-03 sm_last_day`, `W-04 sm_notice_change`, `W-10…W-15 sm_stage_update / sm_dv_slot`, `W-20 sm_family_invite`,
`F-01…F-03 sm_family_alert`, `F-09 sm_family_removed`, `W-90 sm_pro_ended`, `W-91` opt-out reply, and STOP/START
keyword handling. Replacements: family → N-50…N-54; escalation → on-device backup (§3.6); welcome/test → S16 self-test.


## 5. SMS — retired 2026-11-19

No phone number is collected (founder decision 5), so there is no SMS fallback (was P2) and no OTP SMS. Retired IDs:
`SMS-01`, `SMS-02`, `SMS-03`, `OTP`, sender ID `SRKMCH`, short domain `smch.in`.


## 6. Digest (P1) rules

- Sent once a day at the user's digest time (default 19:00) **only if** ≥ 1 item was deferred by the cap or ≥ 2 low-priority updates (N-10/N-20/N-24/N-26) accumulated; otherwise nothing.
- Push N-30 `InboxStyle`: max 5 lines, then "+{k} और"; tap → S44 `digest/{date}`.
- Items are removed from the digest if the user already acted on them in-app.
- The digest is in-app and push only; free users see one ad at the end of S44 (06 §J1), never in the push.

## 7. Copy QA checklist for every new template

- [ ] Key fact (what + by when) in the first 60 characters
- [ ] No "!!", no ALL CAPS, max one ✓ (push)
- [ ] Names the job, never "आपकी नौकरी" alone
- [ ] Date includes month name, not only numbers (avoid DD/MM vs MM/DD confusion)
- [ ] Every enabled language within limits (titles ≤ 45, actions ≤ 15); rendered once at 200 % font on a 360 dp phone; placeholders are whole values, never sentence fragments (07 §7)
- [ ] Deep link + `src` param present
