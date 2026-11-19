# 04 — Screen Specifications (implementation-ready)

Reads with: [01 inventory](01-screen-inventory.md) (IDs, routes) · [02 design system](02-design-system.md) (tokens) · [06 components](06-components.md) (component APIs).
Copy format: **HI** (default) / *EN*. String keys in `code` are suggested `strings.xml` names. All dimensions in dp/sp.

## 0. Global rules (apply to every screen; not repeated below)

| Topic | Rule |
| --- | --- |
| Scaffold | `Scaffold(topBar, bottomBar, snackbarHost)` with `contentWindowInsets = WindowInsets.safeDrawing`; edge-to-edge. Content `LazyColumn` with `contentPadding = PaddingValues(start=16, end=16, top=8, bottom=16 + bottomBarHeight)` so nothing hides behind sticky bars. |
| Width | Content `widthIn(max = 600.dp)` centred on ≥ 600 dp windows. Landscape: same single column, scrolls. |
| Top app bar | `TopAppBar` (small, 64 dp), title `titleLarge`, back `arrow_back` with `contentDescription = "वापस" / "Back"`. Bottom-nav screens use `CenterAlignedTopAppBar` off — left-aligned title. Scroll behaviour `enterAlwaysScrollBehavior` only on S20/S22. |
| Loading | First load: skeleton matching final layout (no spinners > 1 s). Button actions: button shows `CircularProgressIndicator(18 dp)` in place of icon, label stays, button disabled. |
| Error | Inline `ErrorState` (icon `error`, title, body, "फिर कोशिश करें / Try again"). Never a raw error code; append "कोड: E-xxx" in `bodySmall` for support. |
| Offline | `OfflineBanner` (O01) under top bar; cached content stays interactive; network-only buttons disabled with reason. |
| Snackbar | One at a time, 4 s (6 s with Undo), above bottom bar; announced politely by TalkBack. |
| Analytics | Every screen logs `screen_view{screen_id, src}`. Event names are `snake_case`; params listed per screen. No PII in params (no name, phone, DOB; age bucket only). |
| A11y | Every screen title is `heading()`. Focus starts at the top-app-bar title after navigation. Cards merge semantics + custom actions (02 §8). Test tags: `"{screenId}:{element}"`, e.g. `S21:applied_btn`. |
| Font scale ≥ 1.5 | Two-button rows stack vertically (full-width), chips wrap (`FlowRow`), countdown chip moves below title. |
| Back | System back = top-bar back. On bottom-nav roots, back from S22/S26 goes to S20; back from S20 exits. Sheets close on back. |

---

## S01 Splash — P0
- **Layout:** Android 12 SplashScreen API: background `primary` (`#1F4FB8`; dark `#11141B`), centred adaptive icon (white "सम" monogram in a rounded square with a check tick). No text, no custom duration.
- **Logic:** `setKeepOnScreenCondition { !vm.ready }` while reading DataStore (≤ 300 ms) + cached Remote Config. Route: no profile → S02; forced update → S41; `outage && no cache` → S42; else S20 (or pending deep-link).
- **Analytics:** `app_open{cold, src}`.

## S02 Language — P0 (also S40 body)
- **Top bar:** none (S40: back + "भाषा / Language").
- **Content:** 
  - Icon `translate` 48 dp `primary`.
  - Title `headlineSmall`: "अपनी भाषा चुनें" + second line `bodyLarge` `onSurfaceVariant`: "Choose your language".
  - `LanguageOptionCard` ×2 (radio-cards, 72 dp min height, full width, 12 dp gap): **"हिंदी"** (sub "Hindi") · **"English"** (sub "अंग्रेज़ी"). Selected: 2 dp `primary` border + `primaryContainer` fill + `check_circle` filled.
- **Bottom:** `BottomActionBar` primary button "आगे बढ़ें / Continue".
- **States:** default selection = Hindi unless system locale starts with `en` and no Devanagari locale installed.
- **A11y:** radio group `selectableGroup()`, each card `Role.RadioButton`, label "हिंदी, चुनी गई".
- **Analytics:** `language_select{lang, surface=onboarding|settings}`.

## S03 Welcome — P0
- **Content (scroll):**
  - Illustration 160 dp: phone with one notification card + check (vector, primary/primaryContainer).
  - `headlineMedium`: **"सिर्फ़ आपकी नौकरियाँ। कोई मौका न छूटे।"** / *"Only your jobs. Never miss one."*
  - 3 `BulletRow` (24 dp icon `primary` + `bodyLarge`):
    1. `filter_alt` "सिर्फ़ वही सरकारी नौकरियाँ जिनके आप योग्य हैं — उम्र, पढ़ाई, वर्ग, राज्य देखकर" / *"Only government jobs you're eligible for — by age, education, category, state"*
    2. `notifications_active` "जब तक आप फ़ॉर्म न भरें, हम याद दिलाते रहेंगे" / *"We keep reminding you until you apply"*
    3. `description` "हर सूचना में फ़ीस, दस्तावेज़, फ़ोटो का साइज़ और आधिकारिक लिंक" / *"Every alert has fee, documents, photo size and the official link"*
  - `DisclaimerNote` (icon `info`, `bodySmall`, `surfaceContainer` bg, radius 12): fixed disclaimer string (02 §7).
- **Bottom:** primary "शुरू करें — 2 मिनट लगेंगे" / *"Start — takes 2 minutes"*; below, `TextButton` "पहले से अकाउंट है? लॉग इन करें" / *"Have an account? Log in"* → S11 (`entry=welcome`).
- **Analytics:** `onb_start`.

## S04–S09 Onboarding steps — P0 (shared frame `OnboardingStepScaffold`)
- **Top bar:** back arrow (S04 back → S03) · title empty · right: `TextButton` "छोड़ें" only on S09.
- **Header:** `StepProgressHeader`: `LinearProgressIndicator` (determinate, step/6, 4 dp, rounded) + `labelMedium` "चरण 2 / 6" / *"Step 2 of 6"*. Below: question `headlineSmall`, helper `bodyMedium onSurfaceVariant`.
- **Bottom:** `BottomActionBar` with primary "आगे / Next" (disabled until valid; disabled label shows reason below bar in `bodySmall`: "एक विकल्प चुनें"). Single-choice screens auto-advance 250 ms after selection (button remains for TalkBack users; auto-advance disabled when TalkBack on).
- **Autosave:** each answer persisted on change.

### S04 Exams (1/6)
- Q: **"आप किन सरकारी नौकरियों की तैयारी कर रहे हैं?"** / *"Which government jobs are you preparing for?"* · helper "एक से ज़्यादा चुन सकते हैं" / *"You can pick more than one"*.
- 2-column grid of `SelectableTile` (min 96 dp tall, icon 32 + label `titleSmall` + sub `bodySmall`):
  | Icon | HI | EN | sub (HI) |
  | --- | --- | --- | --- |
  | `apartment` | SSC | SSC | CGL, CHSL, GD, MTS |
  | `train` | रेलवे | Railway | NTPC, Group D, ALP |
  | `account_balance` | बैंक | Banking | IBPS, SBI, RBI |
  | `local_police` | पुलिस | Police | राज्य पुलिस, CAPF |
  | `school` | शिक्षक | Teaching | TET, PRT, TGT |
  | `gavel` | UPSC / राज्य PSC | UPSC / State PSC | IAS, PCS |
  | `military_tech` | सेना / रक्षा | Defence | NDA, अग्निवीर |
  | `domain` | राज्य की दूसरी नौकरियाँ | Other state jobs | UPSSSC, BSSC… |
- Last full-width tile "सब दिखाएँ" (selects all). Selected tile: 2 dp `primary` border, `primaryContainer` bg, top-right `check_circle` filled.
- Valid: ≥ 1. a11y: tiles are `Role.Checkbox` with state "चुना गया / नहीं चुना".
- ev `onb_exams{groups}`.

### S05 Qualification (2/6)
- Q: **"आपकी सबसे ऊँची पढ़ाई क्या है?"** / *"What is your highest qualification?"*
- `RadioListItem`s (56 dp): 10वीं पास · 12वीं पास · ITI · डिप्लोमा (Polytechnic) · ग्रेजुएशन (BA/BSc/BCom/BTech…) · पोस्ट ग्रेजुएशन · 10वीं से कम.
- If Graduation/PG → reveal `ExposedDropdownMenu` "विषय / Stream": Arts, Science, Commerce, Engineering, Computer, Other.
- `SegmentedButton` (2): **"पास हो गया"** / *"Passed"* · **"आखिरी साल / रिज़ल्ट बाकी"** / *"Final year / result awaited"*.
- Expandable "और योग्यता (अगर है)" / *"Other qualifications (if any)"* chips (multi): CCC/O-Level कंप्यूटर · हिंदी टाइपिंग · अंग्रेज़ी टाइपिंग · NCC B/C · B.Ed · D.El.Ed/BTC · CTET/TET पास.
- ev `onb_qualification{level, status}`.

### S06 Date of birth (3/6)
- Q: **"आपकी जन्मतिथि क्या है?"** · helper **"10वीं की मार्कशीट में जो लिखी है, वही डालें"** / *"Enter it exactly as on your 10th marksheet"*.
- `DobInputRow`: three `OutlinedTextField`s side by side (weights 1/1/1.4): label "दिन/Day", "महीना/Month", "साल/Year"; `KeyboardType.NumberPassword`-style numeric, `ImeAction.Next`, auto-advance after 2/2/4 digits; paste of "12/03/2003" splits automatically.
- Live helper below (`bodyLarge`, `primary`): "आपकी उम्र आज: 23 साल 8 महीने" / *"Your age today: 23 years 8 months"*.
- Validation on blur: invalid date → error under row: "यह तारीख सही नहीं है — जैसे 12 / 03 / 2003" ; age < 14 or > 60 → "उम्र 14 से 60 साल के बीच होनी चाहिए".
- Why not a date picker: wheel/calendar pickers are slow for 20+ year back-scrolling and confusing for low-literacy users; numeric fields are faster and match certificate format.
- ev `onb_dob{age_bucket}`.

### S07 Category (4/6)
- Q: **"आपका वर्ग (Category) क्या है?"** · helper "सही वर्ग से उम्र में छूट और फ़ीस सही दिखेगी" / *"Correct category gives the right age relaxation and fee"*.
- `RadioListItem` with second line:
  - सामान्य (General / UR)
  - EWS — "आर्थिक रूप से कमज़ोर, सामान्य वर्ग"
  - OBC (नॉन-क्रीमी लेयर) — "OBC-NCL सर्टिफ़िकेट वाले"
  - SC — "अनुसूचित जाति"
  - ST — "अनुसूचित जनजाति"
  - पता नहीं / *Not sure* — "हम सामान्य मानकर दिखाएँगे और ‘ख़ुद जाँचें’ बताएँगे"
- For EWS/OBC: optional row "सर्टिफ़िकेट कब बना? (अगर याद है)" → month/year picker (feeds S36; P1 usage; capture now).
- Note line (`bodySmall`): "OBC/EWS का फ़ायदा राज्य और केंद्र में अलग हो सकता है — हम हर नोटिस के हिसाब से जाँचते हैं।"

### S08 State & domicile (5/6)
- Q: **"आप किस राज्य के निवासी हैं?"** / *"Which state are you a resident (domicile) of?"*
- `SearchField` "राज्य खोजें" → list: pinned (launch states) "उत्तर प्रदेश", "बिहार" with `location_on`, divider, then all 36 states/UTs alphabetical in current language.
- After selection: `YesNoToggle` "क्या आपके पास इस राज्य का निवास प्रमाण पत्र (Domicile) है?" — हाँ / नहीं / पता नहीं.
- ev `onb_state{state, domicile}`.

### S09 More (6/6)
- Q: **"कुछ और जानकारी"** / *"A few more details"* · helper "इनसे कुछ नौकरियाँ खुलती या बंद होती हैं" / *"Some jobs depend on these"*.
- Three grouped blocks (each `titleSmall` label + `ChipGroup` single-select, chips 40 dp tall with `minimumInteractiveComponentSize`), each with right-aligned `TextButton` "छोड़ें":
  1. `wc` लिंग / Gender: पुरुष · महिला · अन्य
  2. `accessible` दिव्यांग (PwD)? नहीं · हाँ → reveals: type chips (चलने-फिरने में · देखने में · सुनने में · दूसरा) + "40% या ज़्यादा?" हाँ/नहीं
  3. `military_tech` पूर्व सैनिक (Ex-servicemen)? नहीं · हाँ → reveals "कितने साल सेवा?" numeric field
- Primary button label here: "मेरी नौकरियाँ देखें" / *"Show my jobs"*.

## S10 Your matches — P0
- **Top bar:** none; content top padding 24.
- **States:**
  - *Loading* (server match, typically < 3 s): `MatchCountHero` skeleton + text "आपकी प्रोफ़ाइल से 30+ नोटिस मिला रहे हैं…" / *"Checking your profile against 30+ notices…"*. After 8 s → *slow* text "नतीजे थोड़ी देर में आएँगे — हम सूचना भेज देंगे" + continue button enabled.
  - *Success (n ≥ 1):* 
    - `MatchCountHero`: `check_circle` 48 `success` · "आप अभी" (`titleMedium`) · **"7"** (`displaySmall`, `primary`) · "नौकरियों के योग्य हैं" (`headlineSmall`); sub `bodyMedium`: "+ 2 में ख़ुद जाँचना होगा" (if checkYourself > 0).
    - `MiniJobRow` ×3 (title, deadline chip) — top 3 by urgency; "और 4 →" text.
    - `ProfileCompletenessBar` if any `unknown`: "प्रोफ़ाइल 90% पूरी · 1 जानकारी बाकी" + chip "PwD" → S27.
  - *Empty (n = 0):* `EmptyState` icon `notifications_active` · "अभी आपके लिए कोई खुली नौकरी नहीं है" · "नई नौकरी आते ही हम सूचना भेजेंगे।"
  - *Error:* "नतीजे नहीं आ पाए" + retry; continue allowed (match runs later, push N-01s).
- **Bottom:** primary **"मुझे सूचना भेजें"** / *"Send me alerts"* (→ S13; if permission already granted → S15/S16). Secondary `OutlinedButton` "मोबाइल नंबर से प्रोफ़ाइल सेव करें" / *"Save profile with mobile number"* → S11 (`entry=onb_result`) → then S13.
- **A11y:** hero is one node: "आप अभी 7 नौकरियों के योग्य हैं, और 2 में ख़ुद जाँचना होगा".
- **Analytics:** `match_count_shown{eligible, check, secs}`, `onb_result_cta{cta}`.

## S11 Phone — P0
- **Top bar:** back · title "मोबाइल नंबर" / *"Mobile number"*.
- **Content:** 
  - `headlineSmall`: "अपना मोबाइल नंबर डालें" / *"Enter your mobile number"*.
  - `bodyMedium`: purpose text varies by entry: default "प्रोफ़ाइल सुरक्षित रखने के लिए — फ़ोन बदलने पर भी आपकी जानकारी रहेगी।"; `entry=whatsapp`: "इसी नंबर पर WhatsApp अलर्ट आएँगे।"; `entry=pro`: "Pro आपके नंबर से जुड़ा रहेगा।"
  - `PhoneField`: leading fixed "+91" (not editable), 10 digits, `KeyboardType.Phone`, `autofill(PhoneNumber)`; on focus first time, launch Phone Number Hint API (`GetPhoneNumberHintIntentRequest`).
  - Consent `Checkbox` row (48 dp): "मैं [नियम] और [प्राइवेसी नीति] से सहमत हूँ" (links open Custom Tab).
  - Privacy note `bodySmall` + `lock` icon: "हम आपका नंबर किसी को नहीं देते। कोई कॉल या मार्केटिंग मैसेज नहीं।" / *"We never share your number. No calls or marketing messages."*
- **Bottom:** primary "OTP भेजें" / *"Send OTP"* (enabled: 10 digits starting 6–9 + consent). Onboarding entry also shows `TextButton` "अभी नहीं" / *"Not now"*.
- **Errors:** "पूरे 10 अंक डालें" · "यह नंबर सही नहीं लगता (6, 7, 8 या 9 से शुरू होना चाहिए)" · rate limit "बहुत बार कोशिश हुई। 10 मिनट बाद फिर करें।" · offline button disabled + "OTP के लिए इंटरनेट चाहिए".
- **ev:** `login_start{entry}`, `otp_sent`, `phone_hint_used`.

## S12 OTP — P0
- **Top bar:** back · "OTP डालें".
- **Content:** `bodyLarge` "+91 98765 43210 पर 6 अंकों का OTP भेजा है" + `TextButton` "नंबर बदलें". `OtpField`: 6 boxes visual (48×56 each, 8 gap), but **one** underlying `BasicTextField` (paste-friendly, `autofill(SmsOtpCode)`, SMS Retriever API auto-fill). Auto-submit at 6 digits.
- Timer `bodyMedium`: "0:24 में फिर से भेज सकेंगे" → `TextButton` "OTP फिर भेजें"; after 2nd resend also "कॉल पर OTP पाएँ".
- **States:** verifying (boxes disabled + progress), error (boxes `error` outline + text "OTP गलत है। फिर से डालें (2 मौके बाकी)"), expired ("OTP की समय सीमा ख़त्म — नया OTP भेजें"), success (check animation 200 ms → navigate back to entry).
- **Merge dialog** (existing account): title "इस नंबर पर पहले से प्रोफ़ाइल है", body "कौन-सी जानकारी रखें?", buttons "पुरानी प्रोफ़ाइल रखें (सुझाया)" / "इस फ़ोन वाली रखें".
- **a11y:** OTP field label "6 अंकों का OTP"; error announced via `liveRegion = Polite`.
- **ev:** `otp_autofilled`, `login_success{merge}`, `login_fail{reason}`.

## S13 Notification permission primer — P0
- **Content (centred):**
  - Mock notification card (static composable, not an image): app icon + "Sarkari Match · अभी" + **"कल आखिरी दिन! SSC GD कांस्टेबल"** + "फ़ीस ₹100 · आप योग्य हैं ✓".
  - `headlineSmall`: **"आखिरी तारीख से पहले याद दिलाएँ?"** / *"Get reminded before the last date?"*
  - `bodyLarge`: "सूचना की इजाज़त दें — हम दिन में 3 से ज़्यादा सूचना नहीं भेजते, और रात 10 से सुबह 7 तक चुप रहते हैं।" / *"Allow notifications — never more than 3 a day, and silent 10 pm–7 am."*
- **Bottom:** primary "इजाज़त दें" / *"Allow"* → `rememberLauncherForActivityResult(RequestPermission(POST_NOTIFICATIONS))` (API 33+); API < 33 → if disabled open settings, else skip. `TextButton` "अभी नहीं" / *"Not now"*.
- **Result:** granted → S15 (if needed) else S16 · denied → S20 with O02 · permanently denied (rationale false after a denial) → D01.
- **ev:** `notif_perm_prompt{api}`, `notif_perm_result{granted, permanent}`.

## S14 Exact-alarm primer — P1
- Icon `alarm` 48 · `headlineSmall` "बिना इंटरनेट भी आखिरी दिन की याद" · body "आखिरी दिन सुबह 8:15 बजे, फ़ोन में ही एक अलार्म जैसा रिमाइंडर लगेगा — अगर किसी वजह से हमारी सूचना न पहुँचे।" · steps: "‘अलार्म और रिमाइंडर’ में Sarkari Match को चालू करें" · primary "सेटिंग खोलें" · text "रहने दें".

## S15 Phone setup guide (OEM) — P0
- **Top bar:** back · "सूचना पक्की करें" / *"Make alerts reliable"* · right `TextButton` "बाद में".
- **Header:** brand icon `smartphone` + `headlineSmall` "{Brand} फ़ोन ऐप को बंद कर देते हैं" / *"{Brand} phones stop background apps"* + `bodyLarge` "ये {n} सेटिंग बदलें ताकि आखिरी तारीख की सूचना ज़रूर आए। 1 मिनट लगेगा।"
- Brand chips row (only if detection uncertain): Redmi/Xiaomi · Vivo · Oppo · Realme · Samsung · OnePlus · दूसरा.
- `OemStepCard` list — each: step number circle (32, `primaryContainer`), title `titleSmall`, instruction `bodyMedium` (exact menu names in English in quotes because OEM UIs are English, e.g. "‘Autostart’ चालू करें"), `OutlinedButton` "सेटिंग खोलें" (`open_in_new`), and `Checkbox` "हो गया". Done cards collapse to one line with `check_circle`.
  - Xiaomi example steps: (1) "‘Autostart’ में Sarkari Match चालू करें" (2) "‘Battery saver’ → ‘No restrictions’ चुनें" (3) "Recent apps में Sarkari Match को नीचे खींचें और ताला (lock) लगाएँ" — render an inline Material `lock` icon (16 dp) after "ताला", via `InlineTextContent` (4) "Notifications → ‘Lock screen’ और ‘Floating notifications’ चालू करें".
- **Bottom:** primary "आगे — टेस्ट सूचना भेजें" / *"Next — send a test alert"* → S16.
- Return from settings (`ON_RESUME`): re-check `isIgnoringBatteryOptimizations` → auto-tick battery step.
- **ev:** `oem_guide_view{brand}`, `oem_step_open{brand, step, resolved}`, `oem_step_done{step}`.

## S16 Delivery self-test — P0
- **Top bar:** back · "टेस्ट सूचना" / *"Test alert"*.
- `SelfTestStepper` (vertical, 3 rows; each row icon state + text):
  1. "सूचना की इजाज़त" — ✓ चालू / ✗ बंद → "चालू करें"
  2. "ऐप खुले में सूचना" — spinner "भेज रहे हैं…" → ✓ "पहुँच गई (2 सेकंड)" / ✗ "नहीं पहुँची"
  3. "ऐप बंद होने पर सूचना" — instruction card: "अब Recent apps से Sarkari Match हटा दें (ऊपर swipe करें)। 1 मिनट में टेस्ट सूचना आएगी — उस पर टैप करें।" → ✓ "बंद ऐप में भी सूचना आती है" / ✗ after 3 min "सूचना नहीं आई"
- Result card: success (`successContainer`) "सब ठीक है! अब आपकी कोई आखिरी तारीख नहीं छूटेगी।" / failure (`warningContainer`) "आपका फ़ोन सूचना रोक रहा है" + buttons "फ़ोन की सेटिंग देखें" (→S15) and Pro line "Pro में WhatsApp पर भी अलर्ट आता है" (→S30).
- **Bottom:** primary "ठीक है" (→ S20) — available at any time; test continues server-side and result is stored.
- **ev:** `self_test_start{surface}`, `self_test_result{step, ok, latency_ms}`.

---

## S20 Home "नौकरियाँ" — P0

**Top bar:** left wordmark "Sarkari Match" (`titleLarge`, `primary`; Devanagari users still see the brand in Latin) · actions: `notifications` (→S24; P0 hides if S24 not built) with badge for unread, `help` (→S39), overflow `more_vert` (पुरानी नौकरियाँ → S25, योग्यता कार्ड शेयर करें → S46).
**Bottom nav:** tab 1 selected; badge = count of jobs needing reply.

**Content (LazyColumn):**
1. Banner slot (max 1 visible, priority): O03 outage > O01 offline > O02 notifications off > delivery warning > trial ending (B09 inline) > profile incomplete card.
2. Greeting row `bodyLarge`: "नमस्ते, Ravi" (name only if provided; else "नमस्ते"; no emoji) + `bodySmall` "आज 19 नवंबर · 5 नौकरियाँ आपका जवाब माँग रही हैं" / *"Today 19 Nov · 5 jobs need your reply"*.
3. Sections (each `SectionHeader` `titleSmall` + count; hidden if empty):
   - `alarm` **"आज आखिरी दिन"** / *"Last day today"* — `MatchCard` variant `today`
   - `timer` **"जल्द ख़त्म (3 दिन के अंदर)"** / *"Closing soon"*
   - `fiber_new` **"नई नौकरियाँ"** / *"New"* (published < 72 h, not yet opened)
   - `pending` **"जवाब बाकी"** / *"Waiting for your reply"*
   - `help` **"ख़ुद जाँचें"** / *"Check yourself"* (checkYourself verdict, any deadline — sorted inside sections above if urgent ≤ 3 days)
4. "क्या आपने ये फ़ॉर्म भरे थे?" grouped card (SC-17) when applicable, between sections 2 and 3.
5. Footer `DisclaimerNote` (short): "सरकारी ऐप नहीं · हर जानकारी आधिकारिक नोटिस से मिलाएँ".

**MatchCard** (see 06 §C1 for API). Layout inside card (16 padding):
```
┌─[4dp top stripe: today=red, urgent=orange, else none]────────────────┐
│ [VerdictChip ✓ योग्य]                    [DeadlineChip ⏰ आज आखिरी दिन]│
│ SSC GD कांस्टेबल भर्ती 2027              (titleMedium, max 2 lines)   │
│ कर्मचारी चयन आयोग (SSC) · 39,481 पद      (bodySmall onSurfaceVariant) │
│ ₹100 फ़ीस · 10वीं पास · आखिरी तारीख 21 नवं (bodySmall, icons 16)     │
│ ✓ उम्र 23 (सीमा 26, OBC +3)               (one top rule line)         │
│ ───────────────────────────────────────────────────────────────────── │
│ [✓ फ़ॉर्म भर दिया] (FilledTonal)   [बाद में] (Text)   [⋮ रुचि नहीं]   │
└───────────────────────────────────────────────────────────────────────┘
```
- Card tap → S21. Buttons 40 dp visual / 48 dp touch. Overflow menu: "रुचि नहीं", "मैच गलत है?", "शेयर करें".
- checkYourself card: rule line shows the `?` rule instead.
- Applied action on card opens B01 directly.

**States:**
- *Loading (first sync):* 3 `MatchCardSkeleton`.
- *Empty — no matches ever:* `EmptyState` (icon `notifications_active` 48): "अभी आपके लिए कोई खुली नौकरी नहीं है" · "नई नौकरी आते ही सूचना भेजेंगे। हर महीने 20–40 नए नोटिस आते हैं।" · `OutlinedButton` "प्रोफ़ाइल देखें".
- *Empty — all answered:* icon `task_alt` `success`: "सब हो गया! कोई जवाब बाकी नहीं।" · "भरे हुए फ़ॉर्म ‘मेरे फ़ॉर्म’ में हैं।" · button "मेरे फ़ॉर्म देखें".
- *Error (no cache):* ErrorState. *Offline:* O01 + cached list.
- Pull-to-refresh (`PullToRefreshBox`) → sync; snackbar "अपडेट हो गया" only if changes.

**A11y:** each card: "SSC GD कांस्टेबल भर्ती 2027. आप योग्य हैं. आज आखिरी दिन, रात 11 बजे तक. फ़ीस 100 रुपये." + custom actions "फ़ॉर्म भर दिया", "बाद में याद दिलाएँ", "रुचि नहीं", "पूरा ब्योरा खोलें". Section headers `heading()`.
**Analytics:** `home_view{n_today, n_soon, n_new, n_pending, n_check}`, `card_tap{job_id, section}`, `card_action{action, section}`, `home_refresh`.

---

## S21 Job detail ("alert card") — P0  ★ core screen

**Top bar:** back · title "नौकरी का ब्योरा" / *"Job details"* · actions `share` ("शेयर करें") and overflow (मैच गलत है? · रुचि नहीं · सुनें (P1)).

**Bottom sticky `ActionBar`** (84 dp + nav inset, tonal level 2, top divider) — depends on state:
| User-job state | Bar content |
| --- | --- |
| New / Reminding / Snoozed | Primary `Button` (icon `task_alt`) **"फ़ॉर्म भर दिया"** / *"I've applied"* (weight 1.4) + `OutlinedButton` (`snooze`) **"बाद में"** / *"Later"* (weight 1). Above row, centered `TextButton` (`visibility_off`) "मुझे इसमें रुचि नहीं" / *"Not interested"*. If snoozed, sub-line "{date} को फिर याद दिलाएँगे". |
| Applied | `successContainer` strip "✓ आपने फ़ॉर्म भर दिया ({date})" + `TextButton` "बदलें" · Pro: `Button` "फ़ॉर्म की टाइमलाइन देखें" → S23 · Free: `Button` (primary, leading `workspace_premium`) "आगे की सूचना पाएँ — Pro" → S30 |
| NotInterested | "आपने ‘रुचि नहीं’ चुना था" + `TextButton` "वापस लाएँ" |
| Expired / Cancelled / NoLongerEligible | no bar; hero shows state |

**Content order:**

1. **`DeadlineHero`** (card, `surfaceContainerLowest`, 16 padding):
   - Row: `VerdictChip` (✓ योग्य / ? ख़ुद जाँचें / ✗ अब योग्य नहीं) + `DeadlineChip`.
   - Title `headlineSmall` (max 3 lines): **"SSC GD कांस्टेबल भर्ती 2027"**
   - `bodyMedium onSurfaceVariant`: "कर्मचारी चयन आयोग (SSC) · 39,481 पद · पूरे भारत में"
   - Big countdown row: `displaySmall`-weight number in deadline colour + label: **"2 दिन बाकी"** · "आखिरी तारीख: शुक्रवार, 21 नवंबर 2026, रात 11 बजे" / *"2 days left · Last date: Fri, 21 Nov 2026, 11 pm"*. Today variant: "आज आखिरी दिन · रात 11 बजे तक".
   - If changed: `ChangeBanner` (warning) "आखिरी तारीख बढ़ी: 14 → 21 नवंबर · ब्योरा" → B07.
   - If `Applied` state from push return: inline prompt chip "साइट से लौट आए — फ़ॉर्म भर दिया?" with "हाँ" (→B01).

2. **`WhatYouNeedCard`** — title row `checklist` + `titleMedium` **"आपको क्या चाहिए"** / *"What you need"*. Sub-sections separated by 12 dp, each with a 20 dp icon + `titleSmall` label + content. Long lists collapse after 4 items with "सब देखें (7)".
   | Section | Content (sample) |
   | --- | --- |
   | `event` **ज़रूरी तारीख़ें** / *Key dates* | Label/value rows (`bodyMedium`, value right-aligned `SemiBold`, tabular): फ़ॉर्म शुरू — 5 नवं · **आखिरी तारीख — 21 नवं, रात 11 बजे** · फ़ीस भरने की आखिरी तारीख — 22 नवं · सुधार (correction) विंडो — 25–27 नवं · परीक्षा (संभावित) — फ़र–मार्च 2027. Past dates struck through + "निकल गई". |
   | `currency_rupee` **आपकी फ़ीस** / *Your fee* | Big `titleLarge`: **₹100** + "(OBC, पुरुष)" · `bodySmall`: "SC/ST/महिला/पूर्व सैनिक: कोई फ़ीस नहीं · UPI, कार्ड, नेट बैंकिंग से" |
   | `description` **दस्तावेज़ तैयार रखें** / *Keep documents ready* | Checklist (local-only checkboxes, saved in Room, for the user's own prep; not sent): 10वीं की मार्कशीट (जन्मतिथि के लिए) · OBC-NCL सर्टिफ़िकेट (केंद्र सरकार वाला फ़ॉर्मेट) · आधार या कोई फ़ोटो ID · चालू मोबाइल नंबर और ईमेल. Warning row if certificate rule (P1): "⚠ OBC सर्टिफ़िकेट 1 अप्रैल 2026 के बाद का होना चाहिए". |
   | `photo_camera` **फ़ोटो** / *Photo* | `SpecGrid` 2×2: फ़ॉर्मेट JPEG · साइज़ 20–50 KB · माप 3.5 × 4.5 cm · पृष्ठभूमि सफ़ेद/हल्की. Note: "3 महीने से पुरानी न हो; टोपी/चश्मा नहीं" |
   | `draw` **हस्ताक्षर** / *Signature* | फ़ॉर्मेट JPEG · साइज़ 10–20 KB · माप 6.0 × 2.0 cm · स्याही काली/नीली |
   | `info` **और ज़रूरी बातें** | e.g. "शारीरिक मानक (ऊँचाई/छाती) — नोटिस पेज 12" |
   - Every value has `copy` affordance on long-press (copies "20–50 KB") — snackbar "कॉपी हो गया".

3. **`WhyYouMatchList`** — title `fact_check` **"आप योग्य क्यों हैं"** / *"Why you match"* (checkYourself: "किस बात की जाँच करें"; notEligible: "आप योग्य क्यों नहीं"). One `RuleLine` per rule:
   - `✓` `check_circle` (success) **उम्र:** 01-01-2027 को आपकी उम्र 23 साल 9 महीने — सीमा 18–23, OBC को +3 = 26 तक
   - `✓` **पढ़ाई:** 10वीं पास चाहिए — आप ग्रेजुएट हैं
   - `✓` **वर्ग:** OBC (नॉन-क्रीमी लेयर) — केंद्र की OBC सूची में होना चाहिए
   - `✓` **लिंग:** पुरुष और महिला दोनों फ़ॉर्म भर सकते हैं
   - `✓` **राज्य:** पूरे भारत के लिए · आपके राज्य (UP) में 1,820 पद
   - `?` `help` (warning) **कंप्यूटर सर्टिफ़िकेट:** "CCC या समकक्ष" ज़रूरी — आपने नहीं बताया · [जोड़ें]
   - `✗` `cancel` (error) **उम्र:** 01-07-2026 को 28 साल — सीमा 27 (OBC के साथ)  *(notEligible example)*
   - `i` `info` (onSurfaceVariant) **शारीरिक मानक:** हम नहीं जाँचते — नोटिस पेज 12 देखें
   - Each line: icon 20 dp + glyph text is in the **contentDescription** ("सही", "जाँचें", "नहीं", "जानकारी") so meaning ≠ colour. Text `bodyMedium`, label bold. Lines are not interactive except inline `[जोड़ें]` link (`TextButton` compact) → S27 field.
   - Footer `bodySmall`: "नोटिस {date} के हिसाब से · आख़िरी जाँच {time}" + `TextButton` "यह मैच गलत लगता है?" (→B05).

4. **`OfficialLinksCard`**: two full-width rows (56 dp, `ListItem`):
   - `picture_as_pdf` **"आधिकारिक नोटिस (PDF)"** · sub "ssc.gov.in · 2.1 MB · हिंदी/English" → opens PDF via Custom Tab / `ACTION_VIEW` (system PDF viewer); offline → disabled + reason.
   - `open_in_new` **"फ़ॉर्म भरने की साइट खोलें"** · sub "ssc.gov.in — आधिकारिक वेबसाइट" → Custom Tab (toolbar colour primary). On return (`ON_RESUME` within 30 min) show prompt chip in hero.
   - Caption: "हम फ़ॉर्म नहीं भरते। फ़ॉर्म सिर्फ़ आधिकारिक साइट पर भरें।"

5. **`ChangeLogCard`** (only if corrigenda): "नोटिस में बदलाव (2)" → list rows "18 नवं: आखिरी तारीख 14 → 21 नवं" with PDF link.

6. `DisclaimerNote` full text.

**States:** *loading* = hero skeleton + 2 card skeletons; *not found* = EmptyState `search_off` "यह नौकरी अब उपलब्ध नहीं है" + "मेरी नौकरियाँ देखें"; *expired* = hero `deadline.closed` "आखिरी तारीख निकल गई (21 नवं)" + "क्या आपने यह फ़ॉर्म भरा था? [हाँ, भरा था]"; *cancelled* = error banner "यह भर्ती रद्द हो गई है" + notice link; *no longer eligible* = ✗ chip + B07 diff + "मुझे लगता है मैं अब भी योग्य हूँ" (→B05).
**A11y:** hero merged: "SSC GD कांस्टेबल भर्ती 2027. आप योग्य हैं. 2 दिन बाकी, आखिरी तारीख 21 नवंबर रात 11 बजे." Rule lines each a node: "सही: उम्र, …". Sticky bar buttons labelled with job name for clarity: contentDescription "SSC GD का फ़ॉर्म भर दिया".
**Analytics:** `job_view{job_id, verdict, days_left, src}`, `pdf_open`, `apply_site_open`, `apply_site_return{mins}`, `rule_add_info{field}`, `wyn_copy{field}`, `doc_checklist_tick`, `job_share`.

### B01 Confirm Applied
- Sheet title `titleLarge`: **"क्या आपने फ़ॉर्म सबमिट कर दिया?"** / *"Did you submit the form?"* · body "SSC GD कांस्टेबल भर्ती 2027" (`titleSmall`).
- Checkbox "फ़ीस भी भर दी" / *"Fee paid too"* (default checked if fee = 0; else unchecked) — unchecked → server schedules one N-46 next day "SSC GD: फ़ीस भरने की आखिरी तारीख 22 नवं — फ़ीस भर दी?" (P2 payment watch; P0 stores flag only).
- Buttons: primary **"हाँ, भर दिया"** · text "अभी नहीं".
- Result: success haptic + B02 (rules SC-22) or snackbar.
- ev `applied_confirm{fee_paid}`.

### B02 Paywall moment ("आखिर तक ट्रैक करें?")
- Variant **trial** (trial never used):
  - Icon `workspace_premium` 40 `tertiary` in `tertiaryContainer` circle.
  - Title **"इस फ़ॉर्म को आखिर तक ट्रैक करें?"** / *"Track this application to the end?"*
  - Body "एडमिट कार्ड, परीक्षा की तारीख बदलने, आंसर की, रिज़ल्ट और DV की सूचना — सब समय पर।" 
  - Mini timeline (5 dots with labels: एडमिट कार्ड · परीक्षा · आंसर की · रिज़ल्ट · DV).
  - Primary (filled `primary`): **"14 दिन मुफ़्त शुरू करें"** / *"Start 14 days free"* · caption "कोई पैसा नहीं लगेगा, कार्ड नहीं चाहिए" · text "अभी नहीं".
- Variant **plans** (trial used): same header; two compact `PlanOptionCard`s (₹199/साल — "₹17/महीना · सबसे सस्ता" badge; ₹49/सीज़न — "4 महीने") + primary "₹199 में Pro लें" → S30 with plan preselected.
- ev `paywall_view{variant}`, `paywall_cta{cta}`.

### B03 Not interested
- Title **"इसमें रुचि नहीं?"** · body "हम इसकी याद नहीं दिलाएँगे। (वैकल्पिक) वजह बताएँ — इससे आगे बेहतर नौकरियाँ दिखाएँगे।"
- `FilterChip`s (multi): पोस्ट पसंद नहीं · जगह दूर है · तैयारी नहीं है · पहले से नौकरी है · मैं योग्य नहीं हूँ (→ also opens B05 after).
- Primary "छुपाएँ" / *"Hide"* · text "रद्द करें".
- After: card removed, snackbar "छुपा दिया · वापस लाएँ" (6 s).
- ev `job_not_interested{reasons}`, `undo_hide`.

### B04 Snooze
- Title **"कब याद दिलाएँ?"** · options as `RadioListItem` (see SC-12) with computed dates: "कल सुबह 9 बजे (20 नवं)" · "आखिरी तारीख से 2 दिन पहले (19 दिसं)" · "इस हफ़्ते याद न दिलाएँ (26 नवं तक)". Note `bodySmall`: "आखिरी दिन और उससे एक दिन पहले की याद हमेशा आएगी।"
- Primary "ठीक है".  ≤ 1 day variant: info only.
- ev `job_snoozed{option}`.

### B05 Report wrong match
- Title **"मैच गलत क्यों लगता है?"** · radio: उम्र गलत है · पढ़ाई गलत है · वर्ग/आरक्षण गलत है · राज्य/निवास गलत है · लिंग गलत है · कुछ और · Optional `OutlinedTextField` "और बताएँ (वैकल्पिक)" 140 chars with counter.
- Link "मेरी प्रोफ़ाइल में गलती है → बदलें" (→S27).
- Primary "भेजें" → snackbar "धन्यवाद! हम 24 घंटे में जाँचेंगे।"
- ev `wrong_match_report{reason, has_note}`.

### B06 Check-yourself
- Title **"यह बात ख़ुद जाँच लें"** · rule card (warning container): "? कंप्यूटर सर्टिफ़िकेट: नोटिस में ‘CCC या समकक्ष’ लिखा है। आपने यह जानकारी नहीं दी।"
- Actions (vertical, full width): primary "मेरे पास है — प्रोफ़ाइल में जोड़ें" (→S27) · outlined "PDF खोलें (पेज 7)" · text "मैं योग्य नहीं — छुपाएँ".
- For notice-ambiguity variant: primary "मैंने जाँच लिया — मैं योग्य हूँ".

### B07 Corrigendum details
- Title **"नोटिस में बदलाव"** · date "18 नवंबर 2026 को" · diff rows: label + `old` (strikethrough, onSurfaceVariant) → `new` (SemiBold): "आखिरी तारीख: ~~14 नवं~~ → **21 नवं**". Effect row with icon: "आप पर असर: कोई नहीं — आप अब भी योग्य हैं ✓" / "आप पर असर: अब आप योग्य नहीं (उम्र सीमा 27 → 25)".
- Button "बदलाव वाला नोटिस (PDF)" + "ठीक है".

---

## S22 My forms (tracker) — P0
- **Top bar:** "मेरे फ़ॉर्म" / *"My forms"*. Filter chips under bar: "चालू (4)" · "पूरे हुए (1)".
- **Pro status strip** (free users): `ProUpsellInline` (tertiaryContainer): `workspace_premium` "फ़ॉर्म भरने के बाद की सूचना (एडमिट कार्ड, रिज़ल्ट) Pro में मिलती है" + `TextButton` "₹17/महीना से" → S30. Trial users: "Pro ट्रायल · 9 दिन बाकी".
- **`ClashBanner`** (P1) above list when relevant.
- **`TrackerRow`** card per Applied job: title `titleMedium`; "भरा: 18 नवं"; current stage chip (stage icon + "एडमिट कार्ड आ गया" / "रिज़ल्ट का इंतज़ार"); **next** line: "अगला: परीक्षा — 12 फ़र 2027"; mini progress (8 dots). Free users: next line shows `lock` + "अगला चरण: एडमिट कार्ड · सूचना Pro में".
- Tap → S23 (Pro/trial) or S21 (free; with Pro CTA in bar).
- **Empty:** icon `assignment_turned_in`: "अभी कोई फ़ॉर्म नहीं" · "‘फ़ॉर्म भर दिया’ दबाने पर नौकरी यहाँ आ जाएगी।" · button "नौकरियाँ देखें".
- **ev:** `tracker_view{n_active, pro}`, `tracker_row_tap{stage}`, `tracker_upsell_tap`.

## S23 Application timeline — P0 (Pro)
- **Top bar:** back · job short title.
- **Header card:** title, "आपने 18 नवं को फ़ॉर्म भरा", `TextButton` "नौकरी का पूरा ब्योरा" (→S21).
- **Action card** (top, only if a current stage needs action): e.g. Answer key: `key` "आंसर की आ गई — आपत्ति 24 फ़र तक" + countdown chip + buttons "आंसर की देखें (PDF)" / "आपत्ति की साइट खोलें". Result stage: "रिज़ल्ट आ गया — PDF में अपना रोल नंबर खोजें" + buttons **"मेरा नाम है"** / **"नाम नहीं है"**.
- **`LifecycleTimeline`**: vertical; each `TimelineStep` = 24 dp node icon (state colour + icon) + connector line (2 dp, done=success, else outlineVariant) + title `titleSmall` + date `bodySmall` + optional link row + "क्या करें" `bodyMedium` text. Stages per SC-28. Changed stage shows warning `update` + "तारीख बदली: 12 फ़र → 26 फ़र".
- Bottom: `TextButton` "इस फ़ॉर्म को ट्रैक करना बंद करें" (→ confirm dialog; destructive, separated).
- **States:** Pro lapsed: timeline greyed after current stage + lock card "Pro फिर से चालू करें — आगे की सूचना रुकी हुई है". Loading skeleton of 5 steps.
- **A11y:** each step: "चरण 3: एडमिट कार्ड. पूरा. 5 फ़रवरी को आया." 
- **ev:** `timeline_view{stage}`, `timeline_link_open{stage, kind}`, `result_self_report{shortlisted}`.

## S24 Alert history — P1
`InboxRow`: channel icon (push `notifications` / WhatsApp glyph / family `family_restroom`), title, time, delivery status text ("पहुँचा · खोला" / "पहुँचा" / "नहीं पहुँचा"), tap → target. Sticky date headers "आज", "कल", "17 नवं". Empty "अभी कोई सूचना नहीं भेजी".

## S25 Past & hidden jobs — P1
`PrimaryTabRow`: "रुचि नहीं" · "तारीख निकल गई" · "अब योग्य नहीं". Rows: title, reason/date, action `TextButton` ("वापस लाएँ" if window open / "भरा था" within 30 days). Empty per tab.

## S26 Profile — P0
- **Top bar:** "प्रोफ़ाइल" / *"Profile"*.
- **`ProfileHeader`:** avatar circle with initials or `person` icon; name (optional; "नाम जोड़ें") ; phone "+91 98xxx xx210" or `TextButton` "लॉग इन करें — प्रोफ़ाइल सुरक्षित रखें"; `ProfileCompletenessBar`.
- **`ProStatusCard`:** Free: tertiaryContainer, `workspace_premium`, "Pro लें — कोई ज़रूरी तारीख न छूटे" · 3 bullets · button "₹199/साल देखें". Trial: "Pro ट्रायल · 26 नवं तक" + "प्लान चुनें". Pro: "Pro चालू · 18 नवं 2027 तक" + "मैनेज करें" (→S32).
- **Section "मेरी जानकारी"** (`SettingsRow` with leading icon, label, value, chevron; tap → S27): कौन-सी नौकरियाँ (SSC, रेलवे, पुलिस) · पढ़ाई (ग्रेजुएशन — BA, पास) · जन्मतिथि (12/03/2003 · 23 साल) · वर्ग (OBC-NCL) · राज्य (उत्तर प्रदेश · निवास प्रमाण: हाँ) · लिंग (पुरुष) · दिव्यांग (नहीं) · पूर्व सैनिक (नहीं). Unknown values show warning text "बताया नहीं" + `?` icon.
- **Section "अलर्ट"**: अलर्ट सेटिंग (S28; value "चुप: रात 10–सुबह 7") · अलर्ट पक्के करें (S29; value with status dot+text "सब ठीक" / "ध्यान दें") · WhatsApp अलर्ट (S33; "Pro" tag if locked) · परिवार अलर्ट (S34, P1) · सर्टिफ़िकेट (S36, P1).
- **Section "ऐप"**: भाषा (हिंदी) · थीम (सिस्टम जैसा) · दोस्त को बताएँ (S45, P1) · मदद और जानकारी (S39).
- **Section "अकाउंट"** (separated by 24 dp, destructive items live inside S38): अकाउंट और डेटा (S38).
- Footer: version "वर्ज़न 1.0.0 (100)".
- **ev:** `profile_view{completeness}`, `profile_row_tap{row}`.

## S27 Edit profile field — P0
- Same body as the corresponding onboarding step, inside `Scaffold` with top bar: back (`close` icon if changes) · title field name ("वर्ग बदलें") · action `TextButton` **"सेव करें"** (enabled when changed & valid).
- Save → loading in button → B10. Back with changes → D07.
- DOB/category edits show inline caution card (warning): "सही जानकारी डालें — गलत जानकारी से गलत नौकरियाँ दिखेंगी और फ़ॉर्म रिजेक्ट हो सकता है।"
- **ev:** `profile_edit{field}`, `rematch_result{added, removed}`.

### B10 Re-match result
- Loading: "आपकी नई जानकारी से नोटिस मिला रहे हैं…" skeleton rows.
- Title **"प्रोफ़ाइल सेव हो गई ✓"** (✓ = `check_circle` icon, not glyph) · sections: `add_circle` (success) "2 नई नौकरियाँ आपके लिए" + rows; `remove_circle` (error) "1 नौकरी अब आपके लिए नहीं" + row with reason "(उम्र सीमा)". Note: "भरे हुए फ़ॉर्म नहीं हटते।" · primary "नई नौकरियाँ देखें" (→S20) / "ठीक है". No changes: "कोई बदलाव नहीं — आपकी नौकरियाँ वही हैं।"

## S28 Alert settings — P0
- **Top bar:** back · "अलर्ट सेटिंग".
- Rows:
  - `bedtime` **चुप रहने का समय** / *Quiet hours* — `Switch` (on) + value "रात 10 बजे – सुबह 7 बजे" tap → two `TimePicker` dialogs (start, end). Helper: "आखिरी दिन की ज़रूरी याद इस समय में भी आ सकती है।"
  - `schedule` **याद दिलाने का समय** / *Reminder time* — radio sheet: सुबह 9 · दोपहर 1 · शाम 7 (default) · रात 9.
  - `summarize` **रोज़ का सारांश** (P1) — switch + time (B13).
  - `info` **दिन में ज़्यादा से ज़्यादा 3 सूचनाएँ** — info row (not editable): "बाकी अगले दिन या सारांश में आएँगी।"
  - `alarm` **बिना इंटरनेट भी आखिरी दिन की याद** (P1) — switch → S14 if needed.
  - `notifications` **फ़ोन की सूचना सेटिंग** — opens `ACTION_APP_NOTIFICATION_SETTINGS`.
- Changes save instantly (snackbar "सेव हो गया"); offline → queued.
- **ev:** `settings_change{key, value}`.

## S29 Delivery health — P0
- **Top bar:** back · "अलर्ट पक्के करें" / *"Alert reliability"*.
- Summary card: all OK → `successContainer` "सब ठीक है — आपके अलर्ट पहुँच रहे हैं" ; else `warningContainer` "{n} चीज़ें ठीक करें".
- `HealthCheckRow`s (status icon + text, never colour only; trailing action button when not OK):
  1. सूचना की इजाज़त — "चालू" / "बंद" [चालू करें]
  2. ज़रूरी सूचनाओं का चैनल — "तेज़ आवाज़ में" / "चुप करा दिया गया" [ठीक करें]
  3. बैटरी — "कोई रोक नहीं" / "बैटरी सेवर रोक सकता है" [ठीक करें → S15]
  4. ऑटोस्टार्ट ({brand}) — "आपने चालू किया" / "पता नहीं" [गाइड देखें]
  5. आख़िरी टेस्ट — "19 नवं, पहुँच गया ✓" / "14 दिन से जवाब नहीं" [टेस्ट करें → S16]
  6. WhatsApp बैकअप — Pro: "चालू · 98xxx" / Free: lock "Pro में" [देखें]
- **ev:** `delivery_health_view{issues}`, `delivery_fix_tap{item}`.

## S30 Paywall (Pro plans) — P0
- **Top bar:** `close` · title none.
- **Header:** `workspace_premium` 48 in tertiaryContainer circle · `headlineMedium` **"Sarkari Match Pro"** · `bodyLarge` **"फ़ॉर्म भरने से नतीजे तक — कोई ज़रूरी तारीख न छूटे"** / *"From application to result — never miss a key date"*.
- **Benefits** (`BenefitRow`: icon 24 `tertiary` + `titleSmall` + `bodySmall`):
  1. `badge` एडमिट कार्ड, सिटी स्लिप, परीक्षा की तारीख — "आते ही सूचना"
  2. `update` परीक्षा की तारीख बदली — "तुरंत बताएँगे"
  3. `key` आंसर की, आपत्ति की आखिरी तारीख, रिज़ल्ट, कट-ऑफ़
  4. `fact_check` DV और मेडिकल की तारीख — "न जाने पर दोबारा मौका नहीं मिलता"
  5. WhatsApp glyph · WhatsApp पर भी अलर्ट — "फ़ोन सूचना रोके तब भी"
  6. (P1, shown with "जल्द" tag) परिवार अलर्ट · परीक्षा टकराव · सर्टिफ़िकेट की जाँच
- **Plans** (`PlanOptionCard` radio group, 12 gap):
  - **साल भर — ₹199** · sub "सिर्फ़ ₹17/महीना" · badge (tertiary filled) "सबसे सस्ता" · default selected.
  - **एक सीज़न — ₹49** · sub "4 महीने · एक भर्ती के मौसम के लिए".
  - Prices from `ProductDetails.formattedPrice`; per-month computed.
- **Primary** (full-width filled `Button`, `primary` colour — the one CTA blue across the app; Pro saffron is used for badges/icons only) **"₹199 में Pro लें"** / *"Get Pro for ₹199"*; trial-eligible users: **"14 दिन मुफ़्त, फिर ₹199"** (only if store offer configured; else trial is server-side from B02).
- Secondary row: "UPI से भरें" (only `upi` flavour) · `TextButton` "पहले ख़रीदा था? वापस लाएँ".
- Legal `bodySmall`: "Google Play से भुगतान। साल वाला प्लान अपने-आप रिन्यू होगा — Play Store में कभी भी बंद करें। मुफ़्त अलर्ट हमेशा मुफ़्त रहेंगे।" 
- **States:** products loading → plan skeletons, button disabled; billing unavailable → error card "Google Play से भुगतान अभी नहीं हो पा रहा" + retry; offline → button disabled "भुगतान के लिए इंटरनेट चाहिए".
- **ev:** `paywall_view{surface, trial_eligible}`, `plan_select{plan}`, `purchase_start{plan, method}`.

## S31 Payment status — P0
| State | Icon (48) | Title | Body | Buttons |
| --- | --- | --- | --- | --- |
| success | `check_circle` success | **"Pro चालू हो गया!"** | "{date} तक आपके सभी फ़ॉर्म की हर सूचना मिलेगी।" | primary "WhatsApp अलर्ट चालू करें" (→S33) · text "मेरे फ़ॉर्म देखें" |
| pending | `hourglass_top` warning | **"भुगतान की पुष्टि हो रही है"** | "पैसे कट गए हैं तो चिंता न करें — पुष्टि होते ही Pro चालू होगा और हम सूचना भेजेंगे। आमतौर पर 10 मिनट।" | primary "ठीक है" · text "मदद चाहिए" |
| failed | `error` error | **"भुगतान नहीं हो पाया"** | "कोई पैसा नहीं कटा। अगर कटा है तो 3–5 दिन में वापस आ जाएगा।" | primary "फिर से कोशिश करें" · text "बाद में" |
| cancelled | — | (no screen; snackbar on S30 "भुगतान रद्द किया") | | |
- ev `purchase_result{status, code, plan}`.

## S32 Manage plan — P0
`PlanSummaryCard` (plan, "{date} तक", renew state) · rows: "साल वाले प्लान पर जाएँ" (season users) · "Play Store में मैनेज करें" (`https://play.google.com/store/account/subscriptions?sku=…&package=…`) · "पहले ख़रीदा था? वापस लाएँ" → D08 · "रसीदें" (list). Free users never land here (redirect S30).

## S33 WhatsApp alerts — P0 (Pro)
- **Top bar:** back · "WhatsApp अलर्ट".
- `WhatsAppPreviewBubble`: a static chat bubble showing W-03 sample text ("*आज आखिरी दिन* — SSC GD कांस्टेबल 2027…").
- Explanation `bodyLarge`: "सिर्फ़ ज़रूरी सूचनाएँ: नई नौकरी, आखिरी 3 दिन, एडमिट कार्ड, DV। एक सीज़न में 30 से ज़्यादा नहीं।"
- Number row: "इस नंबर पर: +91 98xxx xx210" + `TextButton` "दूसरा नंबर".
- Consent `Checkbox` (required, unchecked by default): "मैं Sarkari Match से WhatsApp पर अलर्ट पाने के लिए सहमत हूँ। ‘STOP’ भेजकर कभी भी बंद कर सकता/सकती हूँ।"
- Primary "WhatsApp अलर्ट चालू करें". After: status card "चालू ✓ — एक टेस्ट मैसेज भेजा है, मिला?" [हाँ] [नहीं].
- Active state: `Switch` row "WhatsApp अलर्ट" on; off → D05.
- Locked (free): preview + lock card "यह Pro में है" + "Pro देखें".
- **ev:** `whatsapp_optin{surface}`, `whatsapp_test_ack{received}`, `whatsapp_optout{surface}`.

## S34 Family alerts — P1
List of `FamilyMemberRow` (name, relation, masked number, status chip "चालू"/"निमंत्रण भेजा"/"मना किया", overflow "हटाएँ"), "सदस्य जोड़ें (2 तक)" → B12. Explainer: "उन्हें सिर्फ़ आखिरी 2 दिन, एडमिट कार्ड और DV की सूचना जाएगी — आपकी प्रोफ़ाइल नहीं दिखेगी।"

## S35 Family invite (invitee) — P1
Card "Ravi ने आपको परिवार अलर्ट के लिए जोड़ा है" · "आपको Ravi के फ़ॉर्म की ज़रूरी तारीख़ें (आखिरी दिन, एडमिट कार्ड, DV) भेजी जाएँगी।" · primary "हाँ, मुझे अलर्ट भेजें" (→ OTP on invitee phone) · text "नहीं चाहिए".

## S36 Certificates — P1
Rows: OBC-NCL / EWS सर्टिफ़िकेट ("मार्च 2025 में बना") · निवास प्रमाण पत्र · दिव्यांग प्रमाण पत्र; each `ValidityBadge` ("इस साल के नोटिस के लिए ठीक" ✓ / "नया बनवाना पड़ सकता है" ⚠) + "बदलें" (month-year picker). Note "हम सर्टिफ़िकेट की फ़ोटो नहीं रखते — सिर्फ़ तारीख।"

## S37 Clash — P1
Header `event_repeat` "परीक्षा टकराव" · two `ClashCompareCard`s side-by-side (stack at font ≥ 1.3) with exam name, date, shift, city · guidance: "दोनों बोर्ड के नियम अलग हैं — तारीख बदलने के लिए आधिकारिक सूचना देखें। हम तारीख बदलते ही बताएँगे।" · links.

## S38 Account & data — P0
- Rows: मोबाइल नंबर (masked) · इस अकाउंट पर फ़ोन ("यही फ़ोन अलर्ट पाता है" / "दूसरे फ़ोन पर: Redmi Note 12" + "इसी फ़ोन पर पाएँ") · सहमतियाँ (WhatsApp, परिवार — with "वापस लें") · **"मेरा डेटा डाउनलोड करें"** (`download`) → D04.
- Danger zone (24 dp gap, `titleSmall` "सावधानी" in error colour): `logout` "लॉग आउट" (→D02) · `delete` **"अकाउंट हमेशा के लिए हटाएँ"** (error text) → explainer card expands: "क्या हटेगा: प्रोफ़ाइल, सूचनाओं का इतिहास, परिवार और WhatsApp सहमति। 7 दिन में पूरी तरह हट जाएगा। Pro का पैसा अपने-आप वापस नहीं होगा।" → D03.
- **ev:** `data_export_request`, `logout`, `account_delete{step}`.

## S39 Help & about — P0
FAQ (`FaqItem` expandable, 10 items): "आप किन नोटिस को देखते हैं?" · "‘ख़ुद जाँचें’ का क्या मतलब है?" · "सूचना नहीं आ रही, क्या करूँ?" (→S29) · "क्या आप मेरा फ़ॉर्म भरते हैं?" ("नहीं…") · "क्या यह सरकारी ऐप है?" ("नहीं…") · "मेरा डेटा कहाँ जाता है?" · "Pro कैसे बंद करूँ?" · "गलत मैच कैसे बताऊँ?" · "उम्र कैसे गिनते हैं?" · "किन राज्यों की नौकरियाँ?" (launch states). Contact rows: WhatsApp support · ईमेल. About: disclaimer, sources list, privacy policy, terms, open-source licences, version.

## S41 Force update — P0
Icon `system_update` 48 · "ज़रूरी अपडेट" · "नए वर्ज़न में अलर्ट सही समय पर आते हैं। आगे बढ़ने के लिए अपडेट करें।" · primary "अपडेट करें" (In-App Update IMMEDIATE → fallback Play Store). No back (back exits app).

## S42 Outage — P0
Icon `report` 48 · "अभी हमारा सर्वर काम नहीं कर रहा" · body + optional Remote Config message · primary "फिर कोशिश करें" · if cache exists this is never full-screen (O03 instead).

## S43/S44/S45/S46 — P1/P2 (summary)
- **S43 Age countdown (P2):** list `AgeChanceRow`: exam, "आपकी आख़िरी योग्य साल: 2029", "लगभग 2 मौके" chip (urgent if 1).
- **S44 Digest (P1):** header "आज का सारांश · 19 नवं" + compact `MatchCard`s + "बदलाव" list.
- **S45 Invite (P1):** headline "दोस्त को बताएँ — दोनों को 1 महीना Pro मुफ़्त", share button (Android sharesheet), status "2 दोस्त जुड़े".
- **S46 Share card (P1):** preview of the rendered card (primary bg, white text "मैं 7 सरकारी नौकरियों के योग्य हूँ") + "शेयर करें"; no DOB/category/phone on card.

## Dialog copy (D01–D09)

| ID | Title | Body | Confirm | Dismiss |
| --- | --- | --- | --- | --- |
| D01 | सूचना बंद है | "आखिरी तारीख की याद के लिए सेटिंग में ‘सूचना’ चालू करें।" | सेटिंग खोलें | अभी नहीं |
| D02 | लॉग आउट करें? | "इस फ़ोन पर अलर्ट आना बंद हो जाएगा। आपकी प्रोफ़ाइल सुरक्षित रहेगी।" | लॉग आउट (error) | रद्द करें |
| D03 | अकाउंट हमेशा के लिए हटाएँ? | "यह वापस नहीं होगा। पुष्टि के लिए {phone} पर OTP भेजेंगे।" → OTP field | हटाएँ (error, enabled after OTP) | रद्द करें |
| D04 | आपका डेटा तैयार है | "फ़ाइल ‘sarkari-match-data.json’ में आपकी प्रोफ़ाइल, नौकरियों पर आपके जवाब और सूचनाओं का इतिहास है।" | फ़ाइल सेव करें | बंद करें |
| D05 | WhatsApp अलर्ट बंद करें? | "आखिरी दिन और DV जैसी ज़रूरी सूचनाएँ सिर्फ़ फ़ोन पर आएँगी।" | बंद करें | चालू रखें |
| D06 | {name} को हटाएँ? | "उन्हें अब आपके अलर्ट नहीं जाएँगे।" | हटाएँ | रद्द करें |
| D07 | बदलाव सेव नहीं हुए | "बिना सेव किए वापस जाएँ?" | छोड़ दें | सेव करें |
| D08 | Pro वापस आ गया / कोई ख़रीद नहीं मिली | "{date} तक Pro चालू है।" / "इस नंबर या Google अकाउंट पर Pro नहीं मिला।" | ठीक है | मदद |
| D09 | नया वर्ज़न उपलब्ध | "अपडेट में सुधार हैं। अभी अपडेट करें?" | अपडेट करें | बाद में |

Destructive confirm buttons use `error` colour text (TextButton) — never the primary filled style.

## Banner copy (O01–O03 + others on S20)

| Banner | Icon | Text | Action |
| --- | --- | --- | --- |
| O01 offline | `cloud_off` | "इंटरनेट नहीं है · 2 घंटे पहले की जानकारी" | — |
| O02 notif off | `notifications_off` | "सूचना बंद है — आखिरी तारीख की याद नहीं आएगी" | "चालू करें" |
| delivery | `battery_saver` | "अलर्ट शायद आप तक नहीं पहुँच रहे" | "ठीक करें" |
| O03 outage | `report` | "हमारे सर्वर में दिक्कत है। अलर्ट थोड़ी देर से आ सकते हैं।" | — |
| trial ending | `workspace_premium` | "Pro ट्रायल 2 दिन में ख़त्म" | "प्लान देखें" |
| profile | `person_add` | "2 जानकारी बाकी — इससे और सही मैच मिलेंगे" | "पूरी करें" · `close` |
