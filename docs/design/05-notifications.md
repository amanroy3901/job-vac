# 05 — Notifications: Push, WhatsApp, SMS, Digest

Scheduling rules (slots, daily cap 3, quiet hours 22:00–07:00, snooze, collapse) are in [03 §0.2](03-scenarios.md). This file defines **what each message looks like**.

## 1. Android channels (created at first launch; IDs are permanent)

| Channel ID | Name HI / EN | Importance | Sound / vibrate | Lock screen | Used by |
| --- | --- | --- | --- | --- | --- |
| `critical` | आखिरी तारीख और ज़रूरी सूचना / Last dates & critical | `IMPORTANCE_HIGH` (heads-up) | default sound + vibrate pattern `[0,250,150,250]` | `VISIBILITY_PUBLIC` | N-04, N-05, N-21, N-22, N-27, N-41 test |
| `new_match` | नई नौकरी / New job matches | `IMPORTANCE_HIGH` | default | `PUBLIC` | N-01, N-11c |
| `reminders` | याद दिलाना / Reminders | `IMPORTANCE_DEFAULT` | default | `PUBLIC` | N-02, N-03 |
| `job_updates` | नोटिस में बदलाव / Notice changes | `IMPORTANCE_DEFAULT` | default | `PUBLIC` | N-10–N-12, N-20, N-23–N-26, N-28, N-29 |
| `digest` | रोज़ का सारांश / Daily digest | `IMPORTANCE_LOW` | none | `PUBLIC` | N-30 |
| `account` | अकाउंट और Pro / Account & Pro | `IMPORTANCE_LOW` | none | `PRIVATE` | N-31–N-33, N-40, N-42–N-46 |

- Channel names follow the app language at creation; on language switch, update names via `createNotificationChannel` (same ID).
- Small icon: monochrome `ic_stat_sm` (24 dp, white tick inside rounded square). Accent colour `primary` (`#1F4FB8`); `critical` uses `today` red as `setColor` only for R-last.
- **Grouping:** job pushes share group key `jobs`; when ≥ 2 visible post a summary (`InboxStyle`, title "Sarkari Match", summary "{n} नौकरियाँ आपका जवाब माँग रही हैं"). Lifecycle pushes group `forms`.
- **Notification ID** = stable hash of `jobId` (+ `stage` for lifecycle) so a newer reminder replaces the older one.
- **Delivery:** FCM **data-only, priority `high`** for N-01/N-04/N-05/critical lifecycle; `normal` for the rest. App builds the notification in `FirebaseMessagingService.onMessageReceived` (localised strings, actions, grouping), records `delivered` receipt. TTL: reminders `lastDate − now`; test 10 min.
- **Actions** use `BroadcastReceiver` → WorkManager expedited job (API call), then replace the notification with a confirmation (no app open). Action labels ≤ 15 characters.
- **Tap** → `PendingIntent` (FLAG_IMMUTABLE) to deep link (01 deep-link table) with `src=push&nid={template}`.
- Every job push body ends with nothing promotional; the app name in header is the "not official" signal; the S21 screen carries the disclaimer.

## 2. Length limits (enforced by server template renderer)

| Field | Limit | Why |
| --- | --- | --- |
| Title | ≤ 45 characters (Unicode code points) | collapsed title fits one line on 360 dp at 100 % font |
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
| N-44 | Family accepted (P1) | account | {name} ने परिवार अलर्ट स्वीकार किया | अब ज़रूरी तारीख़ें उन्हें भी मिलेंगी। | {name} accepted family alerts |
| N-45 | Referral reward (P1) | account | 1 महीना Pro मुफ़्त मिला | आपके दोस्त ने पहला फ़ॉर्म भरा। | You got 1 month Pro free |
| N-46 | Fee unpaid check (P2) | reminders | फ़ीस भर दी? {job} | फ़ीस की आखिरी तारीख {date}। फ़ीस बिना फ़ॉर्म पूरा नहीं होता। | Fee paid? {job} |
| — | Weekly ping | — (data only, no UI) | — | — | — |

## 4. WhatsApp Business templates (Pro; via BSP)

Meta rules applied: category **UTILITY** (transactional, user-opted-in) unless marked; variables `{{n}}`; header text ≤ 60 chars; body ≤ 1024 (we keep ≤ 400); footer ≤ 60; ≤ 2 URL buttons (label ≤ 25) or quick replies (≤ 20). Bold with `*`. One emoji allowed as content at most. Footer for all: **"Sarkari Match · सरकारी ऐप नहीं · STOP भेजें बंद करने के लिए"** (EN: "Sarkari Match · Not a government app · Reply STOP to unsubscribe"). Language codes `hi` and `en`.

**W-00 `sm_welcome`** (sent on opt-in)
```
नमस्ते {{1}}! अब Sarkari Match के ज़रूरी अलर्ट आपको WhatsApp पर भी मिलेंगे — नई नौकरी, आखिरी 3 दिन, एडमिट कार्ड और DV की तारीख।
एक सीज़न में 30 से ज़्यादा मैसेज नहीं।
```
Quick reply: `मिल गया` → server marks WA verified.

**W-01 `sm_new_match`**
```
*नई नौकरी — आप योग्य हैं* ✅
{{1}}
आखिरी तारीख: *{{2}}*
आपकी फ़ीस: {{3}}
पूरा ब्योरा, दस्तावेज़ और फ़ोटो का साइज़ ऐप में देखें।
```
Button URL: `ब्योरा देखें` → `https://sarkarimatch.in/j/{{1}}?src=wa`
EN: "*New job — you're eligible* ✅ / {{1}} / Last date: *{{2}}* / Your fee: {{3}} / See documents and photo size in the app."

**W-02 `sm_deadline_soon`** (R3, R1)
```
*{{1}} दिन बाकी* ⏳
{{2}} — आखिरी तारीख *{{3}}*
अभी तक आपने ऐप में "फ़ॉर्म भर दिया" नहीं दबाया है।
```
Buttons: URL `फ़ॉर्म की साइट खोलें` → official apply URL (tracked redirect `https://sarkarimatch.in/go/{{id}}`), quick reply `भर दिया`.

**W-03 `sm_last_day`**
```
*आज आखिरी दिन* ⏰
{{1}} — आज {{2}} तक।
साइट पर भीड़ होती है, जल्दी भरें।
```
Buttons: URL `फ़ॉर्म की साइट खोलें`, quick reply `भर दिया`.

**W-04 `sm_notice_change`** — "*नोटिस में बदलाव*\n{{1}}: {{2}}\nपूरा बदलाव ऐप में देखें।" Button URL `ब्योरा देखें`.

**W-10..W-15 lifecycle** (one template `sm_stage_update` with variables to reduce approvals):
```
*{{1}}* — {{2}}
{{3}}
{{4}}
```
`{{1}}` job · `{{2}}` stage headline ("एडमिट कार्ड आ गया") · `{{3}}` key date line ("परीक्षा: 12 फ़रवरी, सुबह 9 बजे") · `{{4}}` action line ("डाउनलोड करें और 2 प्रिंट रखें।"). Button URL `आधिकारिक लिंक`. DV variant `sm_dv_slot` adds "न जाने पर दोबारा मौका नहीं मिलेगा।".

**W-20 `sm_family_invite`**
```
नमस्ते! {{1}} ने आपको Sarkari Match परिवार अलर्ट में जोड़ा है।
आपको उनके सरकारी फ़ॉर्म की सिर्फ़ ज़रूरी तारीख़ें (आखिरी दिन, एडमिट कार्ड, DV) भेजी जाएँगी।
```
Buttons: URL `स्वीकार करें` → `https://sarkarimatch.in/f/{{token}}`; quick reply `नहीं चाहिए`.

**F-01..F-03 family alerts** (`sm_family_alert`): "*{{1}} के लिए ज़रूरी तारीख*\n{{2}} — {{3}}\n{{1}} को याद दिला दें।" (variables: first name, job, "कल आखिरी दिन" / "एडमिट कार्ड आ गया" / "DV 12 मार्च"). **F-09** `sm_family_removed` "अब आपको {{1}} के अलर्ट नहीं आएँगे।"

**W-90 `sm_pro_ended`** (category **MARKETING**, sent once, only to users who opted into WhatsApp): "आपका Pro {{1}} को ख़त्म हुआ। WhatsApp अलर्ट रुक गए हैं। फिर से चालू करने के लिए: ऐप में ‘Pro’ देखें।" Button URL `Pro देखें`.

**W-91** opt-out confirmation (free-form session reply within 24 h window, no template): "आपके WhatsApp अलर्ट बंद कर दिए गए। ऐप के अलर्ट चालू रहेंगे।"

Inbound handling: `STOP`, `बंद`, `stop` → opt-out; `START` → re-opt-in (Pro only); anything else → auto-reply with help link (session message).

## 5. SMS fallback (P2)

India DLT rules: registered sender ID (header) e.g. `SRKMCH`, registered content templates with `{#var#}` placeholders, category Service-Implicit. Hindi = Unicode → **70 chars/segment, 67 per segment when concatenated**; keep ≤ 2 segments (≤ 134 chars). English GSM 160/153.

| ID | Hindi (≤ 134 chars) | English (≤ 160) |
| --- | --- | --- |
| SMS-01 last day | `आज आखिरी दिन: {#var#} का फ़ॉर्म {#var#} तक भरें। ब्योरा: {#var#} -Sarkari Match (सरकारी नहीं)` | `Last day today: apply for {#var#} by {#var#}. Details: {#var#} -Sarkari Match (not govt)` |
| SMS-02 1 day | `कल आखिरी दिन: {#var#}। फ़ीस {#var#}। {#var#} -Sarkari Match` | `Last day tomorrow: {#var#}. Fee {#var#}. {#var#} -Sarkari Match` |
| SMS-03 DV | `{#var#}: DV {#var#} को। न जाने पर दोबारा मौका नहीं। {#var#} -Sarkari Match` | `{#var#}: Document verification on {#var#}. No second chance. {#var#} -Sarkari Match` |
| OTP | `{#var#} आपका Sarkari Match OTP है। किसी को न बताएँ। {app_hash}` | `{#var#} is your Sarkari Match OTP. Do not share. {app_hash}` |

Links: short domain `smch.in/x/{6}` (≤ 20 chars). The OTP SMS ends with the 11-char SMS Retriever app hash.

## 6. Digest (P1) rules

- Sent once a day at the user's digest time (default 19:00) **only if** ≥ 1 item was deferred by the cap or ≥ 2 low-priority updates (N-10/N-20/N-24/N-26) accumulated; otherwise nothing.
- Push N-30 `InboxStyle`: max 5 lines, then "+{k} और"; tap → S44 `digest/{date}`.
- Items are removed from the digest if the user already acted on them in-app.
- Pro users with WhatsApp do **not** get a WhatsApp digest (cost).

## 7. Copy QA checklist for every new template

- [ ] Key fact (what + by when) in the first 60 characters
- [ ] No "!!", no ALL CAPS, max one emoji (WhatsApp) / one ✓ (push)
- [ ] Names the job, never "आपकी नौकरी" alone
- [ ] Date includes month name, not only numbers (avoid DD/MM vs MM/DD confusion)
- [ ] EN and HI versions both within limits; rendered once at 200 % font on a 360 dp phone
- [ ] Deep link + `src` param present
