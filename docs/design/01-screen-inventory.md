# 01 — Screen Inventory & Navigation Map

Sarkari Match · Android (Kotlin + Jetpack Compose, Material 3) · design v1.0 · 2026-11-19

This is the master list of every destination in the app. Every other design doc (03-scenarios, 04-screens,
05-notifications, 06-components) refers to these IDs. **Do not renumber**; add new IDs at the end of a range.

## Conventions

| Prefix | Kind | Compose implementation |
| --- | --- | --- |
| `S..` | Full-screen route | `NavHost` destination, typed `@Serializable` route object |
| `B..` | Modal bottom sheet | `ModalBottomSheet` hosted by the calling screen (state in that screen's ViewModel), *not* a nav destination unless marked "(route)" |
| `D..` | Dialog | M3 `AlertDialog` / `BasicAlertDialog` |
| `O..` | Overlay / inline system UI | Snackbar, banner, system notification, Play Billing sheet, OS settings |

Priority: **P0** = first release (weeks 3–10), **P1** = within 3 months, **P2** = later. A P0 screen may contain P1 sections; those are flagged in 04-screens.md.

Route naming: `snake_case` strings shown for deep-link and analytics; in code use `@Serializable data object Home`, `@Serializable data class JobDetail(val jobId: String, val src: String? = null)` etc.

Top-level structure: **3-tab bottom navigation** (shown only on S20, S22, S26):

| Tab | Hindi label | English label | Icon (Material Symbols Rounded) | Start route |
| --- | --- | --- | --- | --- |
| 1 | नौकरियाँ | Jobs | `work` (filled when selected) | S20 `home` |
| 2 | मेरे फ़ॉर्म | My forms | `assignment_turned_in` | S22 `applied` |
| 3 | प्रोफ़ाइल | Profile | `account_circle` | S26 `profile` |

Why only 3: low-literacy users, one job ("act on my alerts"). Notification history, settings and Pro live behind the top bar / Profile (secondary nav, `nav-hierarchy` rule). Badge on tab 1 = number of matches still needing a reply (max "9+").

---

## A. Launch, onboarding & sign-in

| ID | Route | Name (HI / EN) | Purpose | Entry points | Key components | P |
| --- | --- | --- | --- | --- | --- | --- |
| S01 | `splash` | स्प्लैश / Splash | Android 12 SplashScreen API; decide start route (onboarding vs home vs force-update vs outage) | App launch, notification tap when process dead | `installSplashScreen()`, logo, no custom delay (>0 ms only while DataStore read) | P0 |
| S02 | `onb/language` | भाषा चुनें / Choose language | Pick Hindi (default) or English. Big two-option list; each option written in its own script | First launch; S40 | `LanguageOptionCard` ×2, primary button | P0 |
| S03 | `onb/welcome` | स्वागत / Welcome | Promise + trust: "सिर्फ़ आपकी नौकरियाँ। कोई मौका न छूटे।"; 3 icon+text bullets; "सरकारी ऐप नहीं" disclaimer; "शुरू करें" | After S02 | `IllustrationHeader` (vector), `BulletRow` ×3, `DisclaimerNote`, button, text link "पहले से अकाउंट है? लॉग इन" → S11 | P0 |
| S04 | `onb/exams` | कौन-सी नौकरियाँ? / Exam interests | Step 1 of 6: multi-select exam groups (SSC, Railway, Bank, Police, Teacher, UPSC/State PSC, Defence, Other State jobs) + "सब" | S03 | `StepProgressHeader` (1/6), `SelectableTile` grid (2 col), sticky `BottomActionBar` | P0 |
| S05 | `onb/qualification` | पढ़ाई / Qualification | Step 2: highest qualification (10th / 12th / ITI / Diploma / Graduate + stream / PG) + status (passed / final year–result awaited) + optional extras (CCC, typing, NCC, B.Ed/D.El.Ed/TET) | S04 | `RadioListItem`, `SegmentedButton` (passed / result awaited), expandable "और योग्यता" chips | P0 |
| S06 | `onb/dob` | जन्मतिथि / Date of birth | Step 3: DOB as on 10th certificate; 3 numeric fields DD/MM/YYYY (not a calendar wheel); shows computed age | S05 | `DobInputRow` (3 `OutlinedTextField`, numeric keyboard), live helper "आपकी उम्र आज: 23 साल 4 महीने" | P0 |
| S07 | `onb/category` | वर्ग / Category | Step 4: General / EWS / OBC (non-creamy layer) / SC / ST; for EWS/OBC/SC/ST ask "certificate kis saal ka hai?" (FY, optional, P1 feeds S36) | S06 | `RadioListItem` ×5 with one-line Hindi explainers, optional `CertificateYearPicker` | P0 |
| S08 | `onb/state` | राज्य / State & domicile | Step 5: domicile state (searchable list; launch states pinned on top), "क्या आपके पास इस राज्य का निवास प्रमाण पत्र है?" yes/no | S07 | `SearchField`, `RadioListItem` list, `YesNoToggle` | P0 |
| S09 | `onb/more` | और जानकारी / A few more | Step 6: gender (पुरुष/महिला/अन्य), PwD (नहीं/हाँ → type: locomotor/visual/hearing/other + ≥40% yes/no), ex-servicemen (नहीं/हाँ → service years), marital status *only if* a selected exam group needs it (hidden by default) | S08 | `ChipGroup` single-select, conditional `ExpandableSection`, "छोड़ें" text button per question (stored as `unknown`) | P0 |
| S10 | `onb/result` | आपका नतीजा / Your matches | Payoff: "आप अभी 7 नौकरियों के योग्य हैं" with top 3 job titles; completeness meter; CTA "मुझे सूचना भेजें" → permission flow; secondary "प्रोफ़ाइल सेव करें (मोबाइल नंबर से)" | S09 (after server match, ≤3 s, else skeleton) | `MatchCountHero`, `MiniJobRow` ×3, `ProfileCompletenessBar`, buttons | P0 |
| S11 | `auth/phone` | मोबाइल नंबर / Phone | Enter +91 mobile; explain why ("प्रोफ़ाइल सुरक्षित रखने और WhatsApp अलर्ट के लिए"). Phone Number Hint API prefill. Skippable during onboarding | S10 secondary, S03 link, S26 "लॉग इन करें", S33 (WhatsApp needs a number), S32 restore | `PhoneField` (+91 prefix, 10 digits), consent checkbox (DPDP), button | P0 |
| S12 | `auth/otp` | OTP डालें / Verify OTP | 6-digit OTP, SMS Retriever auto-fill, resend timer 30 s, "नंबर बदलें" | S11 | `OtpField` (single field, `KeyboardType.NumberPassword`, autofill hint `SmsOtpCode`), countdown, error text | P0 |
| S13 | `perm/notifications` | सूचना की इजाज़त / Notification permission primer | Pre-permission explainer before the Android 13+ `POST_NOTIFICATIONS` system prompt; on Android 8–12 checks channel/app enabled instead | S10 primary CTA; Home banner when disabled; S28 | Illustration of sample notification, 2 bullets, button "इजाज़त दें" → system dialog; "अभी नहीं" | P0 |
| S14 | `perm/exact_alarm` | पक्का रिमाइंडर / Exact-alarm primer | Only if user turns on "बिना इंटरनेट भी आखिरी दिन का रिमाइंडर" (local backup). Android 12+: `canScheduleExactAlarms()` false → explain → `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` | S28 toggle; S29 suggestion | Explainer, button "सेटिंग खोलें" | P1 |
| S15 | `perm/oem_guide` | फ़ोन की सेटिंग / Phone setup guide (OEM) | Brand-specific step list (Xiaomi/Redmi/POCO, Vivo, Oppo, Realme, OnePlus, Samsung, Motorola/stock) for autostart + battery "No restrictions" + lock in recents; deep-link buttons to the exact settings screen with fallback to app-info | After S13 (only on detected aggressive OEM or any device where `isIgnoringBatteryOptimizations` = false); S29; Home "अलर्ट नहीं आ रहे?" banner | `OemStepCard` list (step number, icon, Hindi text, "सेटिंग खोलें" button, "हो गया" checkbox), brand selector chip if detection unsure | P0 |
| S16 | `perm/self_test` | टेस्ट सूचना / Delivery self-test | Server sends a real FCM high-priority test push; screen waits up to 60 s; asks user to swipe app away for the "killed app" test | End of onboarding; S29 "फिर से जाँचें"; weekly auto-test result | `SelfTestStepper` (3 checks: permission, push received, push when app closed), result states | P0 |

## B. Main (bottom-nav) destinations

| ID | Route | Name | Purpose | Entry points | Key components | P |
| --- | --- | --- | --- | --- | --- | --- |
| S20 | `home` | नौकरियाँ / Jobs (Home) | Inbox of eligible jobs **that still need a reply**, grouped by urgency: "आज आखिरी दिन" → "जल्द ख़त्म" (≤3 days) → "नई" → "जवाब बाकी" ; plus "जाँच लें" (check-yourself) section; one native ad at the very bottom for free users (O10); banners (permission off, delivery problem, trial, corrigendum, offline) | Bottom nav; app open; digest push; most pushes when job list context needed | `TopAppBar` (logo wordmark, bell → S24, help), `StatusBanner` slot, `SectionHeader`, `MatchCard` list (`LazyColumn`, `key = jobId`), `EmptyState`, pull-to-refresh | P0 |
| S21 | `job/{jobId}` | नौकरी का पूरा ब्योरा / Job detail ("alert card") | The full alert: headline verdict, deadline countdown, primary actions (भर दिया / रुचि नहीं / बाद में), "आपको क्या चाहिए" card, "आप योग्य क्यों हैं" rules, official PDF + apply link, report wrong match, change log (corrigenda) | Push tap; WhatsApp link; S20/S22/S24/S25 rows | `DeadlineHero`, `ActionBar` (sticky bottom), `WhatYouNeedCard`, `WhyYouMatchList`, `OfficialLinksCard`, `ChangeLogCard`, `DisclaimerNote` | P0 |
| S22 | `applied` | मेरे फ़ॉर्म / My forms (tracker) | All jobs marked Applied. Each row shows next lifecycle stage + date. Free users: rows show "अगला चरण: एडमिट कार्ड — Pro में अलर्ट" lock row; Pro: live stage | Bottom nav; push "admit card out" | `TrackerRow` list, filter chips (चालू / पूरे), `ProUpsellInline` (free), `ClashBanner` (P1) | P0 |
| S23 | `applied/{jobId}` | फ़ॉर्म की टाइमलाइन / Application timeline | Vertical lifecycle timeline: Applied → City slip → Admit card → Exam → Answer key (objection window) → Result → Cut-off → DV/Medical → Final. Each stage: status, date, official link, "क्या करें" | S22 row; lifecycle push; WhatsApp link | `LifecycleTimeline` (`TimelineStep` items), `StageActionCard`, `RescheduleNotice` | P0 (Pro) |
| S26 | `profile` | प्रोफ़ाइल / Profile | Profile summary (each field a row → edit), completeness, Pro status card, settings entry list, account | Bottom nav | `ProfileHeader`, `ProStatusCard`, `SettingsRow` list grouped | P0 |

## C. Secondary screens (pushed on top, no bottom nav)

| ID | Route | Name | Purpose | Entry points | Key components | P |
| --- | --- | --- | --- | --- | --- | --- |
| S24 | `inbox` | पुरानी सूचनाएँ / Alert history | Every alert we sent (push/WhatsApp), newest first, with channel icons and delivered/opened status; lets user recover a dismissed push | S20 bell | `InboxRow` list, date `stickyHeader` | P1 |
| S25 | `archive` | पुरानी नौकरियाँ / Past & hidden jobs | Tabs: "रुचि नहीं" (with undo → back to S20), "तारीख निकल गई", "अब योग्य नहीं" (corrigendum/age) | S20 overflow menu; S26 | `TabRow`, `ArchiveRow` | P1 |
| S27 | `profile/edit/{field}` | प्रोफ़ाइल बदलें / Edit profile field | Reuses S04–S09 bodies in edit mode (top bar with back + "सेव करें"), then shows B15 re-match result | S26 rows; B05 ("मेरी प्रोफ़ाइल गलत है"); "check yourself" missing-info chip | Same components as onboarding step, `TopAppBar` with save | P0 |
| S28 | `settings/alerts` | अलर्ट सेटिंग / Alert settings | Quiet hours (default 22:00–07:00), digest on/off + time (P1), max per day (fixed 3, info only), snooze defaults, exact-alarm backup (P1), notification channels shortcut | S26; S13 denied path | `SwitchRow`, `TimeRangeRow` → `TimePicker` dialog, `InfoRow` | P0 |
| S29 | `settings/delivery` | अलर्ट पक्के करें / Delivery health | Traffic-light checklist: permission, channel importance, battery optimisation, autostart (self-reported), last test result + date, WhatsApp backup (Pro). Fix buttons | S26; Home banner "अलर्ट शायद नहीं पहुँच रहे"; weekly self-test failure push | `HealthCheckRow` ×5, buttons to S13/S15/S16/S33 | P0 |
| S30 | `pro` (route) | Pro प्लान / Paywall (full) | Plans: ₹199/साल (₹17/महीना), ₹49/सीज़न (4 महीने); benefits list; trial info; Play Billing primary, UPI secondary (test builds); restore link | B02 "प्लान देखें"; S26 Pro card; S22 lock rows; trial-expiry push; locked features | `PlanOptionCard` ×2 (radio), `BenefitRow` ×5, `PrimaryButton`, `PaymentMethodRow`, legal text | P0 |
| S31 | `pro/status` | भुगतान की स्थिति / Payment status | Result after purchase: success / pending (UPI or Play "pending") / failed / cancelled, with next step | Return from Play Billing or UPI intent; pending purchase resolves | `ResultIllustration`, title/body, buttons | P0 |
| S32 | `pro/manage` | मेरा प्लान / Manage plan | Current plan, expiry date, renew/upgrade season→year, "Play Store में मैनेज करें", restore purchase, receipt list | S26 Pro card | `PlanSummaryCard`, `SettingsRow` list | P0 |
| S33 | `whatsapp` | WhatsApp अलर्ट / WhatsApp backup | Pro: opt-in (explicit consent toggle, number confirm or different number + OTP), preview of a sample message, opt-out | S26; B02 benefit; S29; trial-start sheet | `WhatsAppPreviewBubble`, `PhoneField`, `SwitchRow`, consent text | P0 (Pro) |
| S34 | `family` | परिवार अलर्ट / Family alerts | Pro: up to 2 family members (name + relation + number); status invited/active; which alerts they get (1 day before + last day + DV/admit card) | S26; B02 benefit | `FamilyMemberRow`, add button → B19 form sheet, remove → D06 | P1 |
| S35 | `family/accept/{token}` | परिवार निमंत्रण / Family invite (invitee) | For a family member who installs the app via invite link: accept (OTP) → "आप Ravi के लिए अलर्ट पाएँगे"; no profile required | WhatsApp/SMS invite link (App Link) | `InviteSummaryCard`, button, decline link | P1 |
| S36 | `certificates` | सर्टिफ़िकेट / Certificates | Pro: category (OBC-NCL/EWS) certificate FY, domicile certificate date, PwD certificate; per-job validity verdict, renew reminder | S26; certificate alert push; S21 WhatYouNeed row "⚠ आपका OBC सर्टिफ़िकेट" | `CertificateRow`, `MonthYearPicker`, `ValidityBadge` | P1 |
| S37 | `clash/{clashId}` | परीक्षा टकराव / Exam clash | Pro: two applied exams on same day/week; shows both with dates, what to do (check official change-of-date rules) | Clash push; S22 banner | `ClashCompareCard` ×2, guidance text, official links | P1 |
| S38 | `account` | अकाउंट और डेटा / Account & privacy | Phone number, linked devices (count), download my data (DPDP), delete account, logout, consent history | S26 | `SettingsRow`, destructive section separated | P0 |
| S39 | `help` | मदद / Help & about | FAQ (simple Hindi), "यह सरकारी ऐप नहीं है" disclaimer, data sources list, contact via WhatsApp/email, app version, licences | S20 top bar "?"; S26 | `FaqItem` (expandable), `SettingsRow` | P0 |
| S40 | `settings/language` | भाषा / Language | Same body as S02; applies instantly via `AppCompatDelegate.setApplicationLocales` | S26 | `LanguageOptionCard` | P0 |
| S41 | `force_update` | अपडेट ज़रूरी / Update required | Blocking screen when `minSupportedVersion` > current; Play In-App Update (IMMEDIATE) or Play Store link | S01 remote-config check | Illustration, text, button | P0 |
| S42 | `outage` | सर्वर में दिक्कत / Service problem | Shown when the backend health endpoint says `down` or 3 consecutive 5xx: cached data stays readable, banner otherwise; full-screen only if no cache at all | S01; any screen (as banner O03) | Illustration, cached-data link, retry | P0 |
| S43 | `age_countdown` | उम्र की सीमा / Age-limit countdown | Per exam: "SSC CGL — 2 मौके और", list sorted by fewest chances | S26; age-crossing push | `AgeChanceRow` | P2 |
| S44 | `digest/{date}` | आज का सारांश / Daily digest | Opens from the digest push: the matches that overflowed the 3-push cap + low-priority updates, as a compact list | Digest push | `DigestHeader`, `MatchCard` (compact variant) | P1 |
| S45 | `refer` | दोस्त को बताएँ / Invite a friend | Share link; +1 month Pro for both on friend's first Applied | S26; S10 share | Share sheet trigger, referral status | P1 |
| S46 | `share_card` | योग्यता कार्ड / Share eligibility card | Render "मैं 7 नौकरियों के योग्य हूँ" image (no personal data except first name optional) and share via Android sharesheet | S10, S20 overflow | Composable rendered to bitmap, share button | P1 |

## D. Bottom sheets

| ID | Name | Trigger | Content | P |
| --- | --- | --- | --- | --- |
| B01 | फ़ॉर्म भर दिया? / Confirm Applied | Tap "भर दिया" on S21/S20 card/push action "भर दिया" (push action marks directly, then opens B02 on next app open) | "क्या आपने {job} का फ़ॉर्म सबमिट कर दिया?" + optional "फ़ीस भी भर दी" checkbox; buttons "हाँ, भर दिया" / "अभी नहीं" | P0 |
| B02 | आखिर तक ट्रैक करें? / Paywall moment | Immediately after first B01 confirm (and after every Applied for free users whose trial is used, max once per 7 days) | Trial not used: "अगले 14 दिन Pro मुफ़्त — एडमिट कार्ड, रिज़ल्ट, DV की सूचना" button "मुफ़्त ट्रायल शुरू करें" (no payment). Trial used: plans summary → S30 | P0 |
| B03 | रुचि नहीं / Not interested | Tap "रुचि नहीं" | Optional reason chips (पोस्ट पसंद नहीं, जगह दूर है, तैयारी नहीं, पहले से नौकरी है, मैं योग्य नहीं हूँ → B05); hides job; Snackbar "छुपा दिया · वापस लाएँ" (Undo 6 s) | P0 |
| B04 | बाद में याद दिलाएँ / Snooze | Tap "बाद में" or push action "बाद में" | Options: "कल सुबह 9 बजे", "आखिरी तारीख से 2 दिन पहले", (if ≤2 days left only "आज शाम 6 बजे"); never past the last-day morning reminder | P0 |
| B05 | गलत मैच बताएँ / Report wrong match | S21 "यह मैच गलत है?" link; B03 reason | Reason list (उम्र, पढ़ाई, वर्ग, राज्य, लिंग, कुछ और) + optional note (140 chars); "मेरी प्रोफ़ाइल गलत है" → S27 | P0 |
| B06 | ख़ुद जाँच लें / Check-yourself explainer | Tap "?" verdict chip or "क्यों?" on a check-yourself card | Which rule is uncertain and why (e.g. "नोटिस में 'समकक्ष डिग्री' लिखा है — आपकी डिग्री मान्य है या नहीं, PDF पेज 7 देखें"); buttons "PDF खोलें" / "मैं योग्य हूँ — अलर्ट चालू रखें" / "मैं योग्य नहीं — छुपाएँ" | P0 |
| B07 | नोटिस में बदलाव / Corrigendum details | Tap corrigendum banner on S21/S20 | Before → after diff rows ("आखिरी तारीख: 15 सितं → 22 सितं"), effect on your verdict, link to corrigendum PDF | P0 |
| B08 | ट्रायल शुरू / Trial started | After B02 trial CTA | "14 दिन Pro चालू — {date} तक", 3 things now on, CTA "WhatsApp अलर्ट भी चालू करें" → S33 | P0 |
| B09 | ट्रायल ख़त्म हो रहा है / Trial ending | App open on day 12–14 of trial; tap trial-ending push | "{n} दिन बचे। आपके {k} फ़ॉर्म की अगली सूचना: {stage} {date}" + plans | P0 |
| B10 | प्रोफ़ाइल बदली — नए नतीजे / Re-match result | After S27 save | "+2 नई नौकरियाँ मिलीं", "−1 अब योग्य नहीं" lists, CTA "देखें" | P0 |
| B11 | UPI ऐप चुनें / UPI payment | S30 "UPI से भरें" (test/flavour `upi`) | Intent chooser fallback list (PhonePe, Google Pay, Paytm, BHIM) + "कोई UPI ऐप नहीं" note | P0 (test) |
| B12 | परिवार सदस्य जोड़ें / Add family member | S34 add | Name, relation chips, mobile; consent line "उन्हें SMS/WhatsApp से निमंत्रण जाएगा" | P1 |
| B13 | सूचना का सार / Digest time | S28 digest time row | Time options 8 am / 1 pm / 7 pm / 9 pm | P1 |
| B14 | उम्र सीमा पार / Age limit crossed | Push "age crossed" tap or app open after crossing | Empathetic copy, which jobs/exams closed, remaining options with relaxations | P1 |
| B15 | डिवाइस बदली / New device signed in | App open on old device after login elsewhere (single active push device for alerts) | "आपके अलर्ट अब {device} पर आएँगे" + "इसी फ़ोन पर पाएँ" | P0 |

## E. Dialogs

| ID | Name | Trigger | P |
| --- | --- | --- | --- |
| D01 | सूचना बंद है — सेटिंग खोलें / Notifications blocked | Permission permanently denied (`shouldShowRequestPermissionRationale` false after denial) or channel disabled | P0 |
| D02 | लॉग आउट करें? / Logout confirm | S38 | P0 |
| D03 | अकाउंट हमेशा के लिए हटाएँ? / Delete account (step 2: type "हटाएँ" or confirm OTP) | S38 | P0 |
| D04 | डेटा डाउनलोड / Data export ready | S38 export | P0 |
| D05 | WhatsApp अलर्ट बंद करें? / WhatsApp opt-out confirm | S33 | P0 |
| D06 | परिवार सदस्य हटाएँ? / Remove family member | S34 | P1 |
| D07 | बिना सेव किए जाएँ? / Discard unsaved changes | Back on S27 with edits | P0 |
| D08 | ख़रीद वापस लाएँ — नतीजा / Restore purchase result | S32/S30 restore | P0 |
| D09 | ऐप अपडेट उपलब्ध / Flexible update available | Remote config `recommendedVersion` | P1 |

## F. Overlays, banners & system surfaces

| ID | Name | Where | P |
| --- | --- | --- | --- |
| O01 | Offline banner "इंटरनेट नहीं है — पुरानी जानकारी दिख रही है ({time})" | Top of S20/S21/S22/S23 content, below top bar | P0 |
| O02 | Permission/delivery warning banner | S20 top | P0 |
| O03 | Outage banner "हमारे सर्वर में दिक्कत है। अलर्ट थोड़ी देर से आ सकते हैं।" | S20 top | P0 |
| O04 | Snackbars (undo hide, saved, copied, applied) | Any | P0 |
| O05 | System push notifications (see 05-notifications.md) | OS | P0 |
| O06 | Play Billing purchase sheet | OS/Play | P0 |
| O07 | OS settings deep links (app notification settings, battery optimisation, exact alarm, OEM autostart) | OS | P0 |
| O08 | Android sharesheet | OS | P1 |
| O09 | Play In-App Update (immediate/flexible) | Play | P0 |
| O10 | Native ad slot "विज्ञापन · Ad" (free tier only; 06 §J1) | Last item of S20 feed and of S44 digest — nowhere else | P0 |

---

## Navigation map

```mermaid
flowchart TD
  S01[S01 Splash] -->|first run| S02[S02 Language]
  S01 -->|has profile| S20
  S01 -->|min version fail| S41[S41 Force update]
  S01 -->|backend down & no cache| S42[S42 Outage]
  S02 --> S03[S03 Welcome]
  S03 -->|शुरू करें| S04[S04 Exams 1/6]
  S03 -->|लॉग इन| S11
  S04 --> S05[S05 Qualification 2/6] --> S06[S06 DOB 3/6] --> S07[S07 Category 4/6] --> S08[S08 State 5/6] --> S09[S09 More 6/6] --> S10[S10 Your matches]
  S10 -->|मुझे सूचना भेजें| S13[S13 Notif primer]
  S10 -->|सेव करें| S11[S11 Phone] --> S12[S12 OTP] --> S13
  S13 -->|granted| S15[S15 OEM guide]
  S13 -->|denied| D01{{D01 Blocked}}
  S15 --> S16[S16 Self-test] --> S20
  D01 --> S20

  subgraph Tabs[Bottom navigation]
    S20[S20 Jobs/Home]
    S22[S22 My forms]
    S26[S26 Profile]
  end

  S20 --> S21[S21 Job detail]
  S20 -->|bell| S24[S24 Alert history]
  S20 -->|menu| S25[S25 Past & hidden]
  S20 -->|?| S39[S39 Help]
  S20 -->|digest| S44[S44 Digest]
  S21 -->|भर दिया| B01([B01 Confirm applied]) --> B02([B02 Paywall moment])
  B02 -->|trial| B08([B08 Trial started]) --> S33
  B02 -->|plans| S30[S30 Paywall]
  S21 -->|रुचि नहीं| B03([B03 Not interested])
  S21 -->|बाद में| B04([B04 Snooze])
  S21 -->|गलत मैच| B05([B05 Report])
  S21 -->|?| B06([B06 Check yourself])
  S21 -->|बदलाव| B07([B07 Corrigendum])
  S22 --> S23[S23 Timeline]
  S22 -->|lock row| S30
  S22 -->|clash| S37[S37 Clash]
  S23 --> S21
  S26 --> S27[S27 Edit field] --> B10([B10 Re-match])
  S26 --> S28[S28 Alert settings]
  S26 --> S29[S29 Delivery health]
  S29 --> S13 & S15 & S16 & S14[S14 Exact alarm]
  S26 --> S32[S32 Manage plan]
  S26 --> S30
  S30 -->|Play / UPI| S31[S31 Payment status]
  S26 --> S33[S33 WhatsApp]
  S26 --> S34[S34 Family]
  S26 --> S36[S36 Certificates]
  S26 --> S38[S38 Account & data]
  S26 --> S40[S40 Language]
  S26 --> S45[S45 Invite]
  S26 --> S39
  S38 --> D02{{D02 Logout}} & D03{{D03 Delete}}
```

## Deep links (all routes reachable; `deep-linking` rule)

Assumed domain `sarkarimatch.in` (placeholder until bought). App Links verified via `assetlinks.json`; custom scheme `sarkarimatch://` for push payloads.

| Link | Destination | Back-stack synthesised |
| --- | --- | --- |
| `https://sarkarimatch.in/j/{jobId}?src=wa|push|share` | S21 | S20 → S21 |
| `https://sarkarimatch.in/a/{jobId}` | S23 | S22 → S23 |
| `https://sarkarimatch.in/pro?plan=year|season&src=` | S30 | S20 → S30 |
| `https://sarkarimatch.in/f/{token}` | S35 | none (S35 is root for invitees) |
| `sarkarimatch://digest/{yyyy-mm-dd}` | S44 | S20 → S44 |
| `sarkarimatch://delivery` | S29 | S26 → S29 |
| `sarkarimatch://clash/{clashId}` | S37 | S22 → S37 |
| `sarkarimatch://certificates` | S36 | S26 → S36 |
| `sarkarimatch://whatsapp` | S33 | S26 → S33 |
| `sarkarimatch://test` | S16 | S26 → S29 → S16 |

Rules: deep link opened before onboarding is complete → S02 flow, then continue to the target (store pending link in `SavedStateHandle`/DataStore). Unknown or expired `jobId` → S21 "नहीं मिली" error state with button to S20. Every deep link adds `src` for analytics.

## Count

- Full screens: 43 (S01–S16, S20–S46; S17–S19 reserved) — 32 P0, 10 P1 (S14, S24, S25, S34–S37, S44–S46), 1 P2 (S43)
- Bottom sheets: 15 · Dialogs: 9 · Overlays: 9
