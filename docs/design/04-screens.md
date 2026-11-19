# 04 — Screen Specifications (implementation-ready)

Reads with: [01 inventory](01-screen-inventory.md) (IDs, routes) · [02 design system](02-design-system.md) (tokens) · [06 components](06-components.md) (component APIs).
Copy format: **HI** (default) / *EN*. String keys in `code` are suggested `strings.xml` names. All dimensions in dp/sp.

**2026-11-19:** S11, S12, S33, B11, D05 retired; new S47, S48, S49, B16, B17, D10–D12; Google sign-in or guest replaces phone/OTP; Play Billing only; no WhatsApp. Every screen must also pass the localization rules in [07](07-localization.md) (no fixed widths, wrap don't truncate, start/end only, Western digits, bidi isolates).

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
| Font scale ≥ 1.5 / long languages | Two-button rows stack vertically (full-width), chips wrap (`FlowRow`), countdown chip moves below title. The same stacking applies at 1.0 when a label needs more than 2 lines in the current language (Tamil, Malayalam). |
| Direction | Layout uses `start`/`end`; Urdu mirrors per 07 §4. Copy below is Hindi/English; other languages come from the same string keys. |
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
  - `LanguageList` (06 §L1). Enabled languages as `LanguageOptionCard` radio cards (72 dp min height, full width, 12 dp gap): **"हिंदी"** (sub "Hindi") · **"English"** (sub "अंग्रेज़ी"). Selected: 2 dp `primary` border + `primaryContainer` fill + `check_circle` filled.
  - Below: collapsed row `schedule` **"जल्द आ रही हैं (11)"** / *"Coming soon (11)"* → expands to non-selectable rows, each in its own script: বাংলা · मराठी · తెలుగు · தமிழ் · ગુજરાતી · ಕನ್ನಡ · ଓଡ଼ିଆ · മലയാളം · ਪੰਜਾਬੀ · অসমীয়া · اردو, with the English name and the tag "जल्द / Soon". Helper `bodySmall`: "आपकी भाषा जल्द आएगी — तब तक हिंदी या English चुनें।" 
- **Bottom:** `BottomActionBar` primary button "आगे बढ़ें / Continue".
- **States:** default selection = the device language if it is enabled, else Hindi. Enabled/coming-soon lists come from `ReferenceBundle.languages` (cached; bundled default hi+en enabled when offline on first run).
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
- **Bottom:** primary "शुरू करें — 2 मिनट लगेंगे" / *"Start — takes 2 minutes"* → S47; below, `TextButton` "पहले इस्तेमाल किया है? Google से साइन इन करें" / *"Used it before? Sign in with Google"* → O11 (SC-46: restores profile and skips S47/S04–S10).
- **Analytics:** `onb_start`.

## S47 Privacy notice ("आपकी जानकारी") — P0 (new 2026-11-19)
- **Top bar:** onboarding: back (→ S03), no title; settings variant: back + "प्राइवेसी नोटिस" / *"Privacy notice"*.
- **Content (scroll):** icon `privacy_tip` 48 `primary` · `headlineSmall` **"आपकी जानकारी, सीधी बात"** / *"Your data, plainly"* · `bodyLarge` "नौकरियाँ मिलाने के लिए हमें बस कुछ बातें चाहिए। यह रही पूरी बात:" / *"We need only a few details to match jobs. Here is all of it:"*
- 5 `PrivacyNoticeItem`s (06 §K4):
  1. `school` **"हम क्या पूछते हैं"** — "कौन-सी नौकरियाँ, पढ़ाई, जन्मतिथि, वर्ग, राज्य। लिंग, दिव्यांग और पूर्व सैनिक — चाहें तो छोड़ दें।" · क्यों: "इन्हीं से तय होता है कि आप किस नौकरी के योग्य हैं।" / *"What we ask: jobs you want, education, date of birth, category, state. Gender, disability and ex-servicemen are optional." Why: "These decide which jobs you are eligible for."*
  2. `block` **"हम क्या कभी नहीं माँगते"** — "मोबाइल नंबर, आधार, दस्तावेज़, फ़ोटो, आपके कॉन्टैक्ट या लोकेशन।" / *"Never: phone number, Aadhaar, documents, photos, contacts or location."*
  3. `lock` **"जानकारी कहाँ रहती है"** — "आपकी प्रोफ़ाइल आपके फ़ोन में ही रहती है, और नौकरियाँ भी फ़ोन पर ही मिलाई जाती हैं। Google से साइन इन करके बैकअप चालू करें, तो वह ताले में बंद (एन्क्रिप्ट) होकर सेव होती है — उसे सिर्फ़ आप खोल सकते हैं। हम इसे किसी को बेचते या देते नहीं। Google से साइन इन करें तो सिर्फ़ नाम और ईमेल लेते हैं।" / *"Your profile is stored on your phone, and jobs are matched on your phone too. If you sign in with Google and turn on backup, it is saved encrypted so only you can open it. Never sold or shared. If you sign in with Google we take only your name and email."*
  4. `info` **"विज्ञापन"** — "मुफ़्त ऐप में नीचे एक छोटा विज्ञापन होता है। विज्ञापन वालों को आपकी प्रोफ़ाइल नहीं मिलती। Pro में कोई विज्ञापन नहीं।" / *"The free app shows one small ad at the bottom. Advertisers never get your profile. Pro has no ads."* (link "और जानें" → B17)
  5. `delete` **"हटाना आपके हाथ में"** — "प्रोफ़ाइल → अकाउंट और डेटा में अपना डेटा डाउनलोड करें या सब कुछ हमेशा के लिए हटाएँ।" / *"Profile → Account & data: download your data or delete everything."*
- `TextButton` "पूरी प्राइवेसी नीति पढ़ें" (Custom Tab) and the line "सूचनाएँ भेजने के लिए हम आगे इजाज़त माँगेंगे।" / *"We'll ask for notification permission later."*
- **Bottom:** primary **"समझ गया, आगे बढ़ें"** / *"Got it, continue"* → S04. No checkbox (nothing here needs special consent). Settings variant: no bottom bar.
- **Logic:** stores `privacy_notice_ack{version, at}` on the phone (DataStore); there is no server profile. Accounts also get one `privacy_notice` entry in the account-level consent log. A new `AppConfig.privacy_notice_version` shows S47 once more on next open.
- **A11y:** each item one node ("हम क्या पूछते हैं: …; क्यों: …").
- **ev:** `privacy_notice_view{surface}`, `privacy_notice_ack{version}`, `privacy_policy_open`.

## S04–S09 Onboarding steps — P0 (shared frame `OnboardingStepScaffold`)
- **Top bar:** back arrow (S04 back → S47) · title empty · right: `TextButton` "छोड़ें" only on S09.
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
  1. `wc` लिंग / Gender: पुरुष · महिला · ट्रांसजेंडर (Male · Female · Transgender — the legal term used in notices; API `transgender`; "छोड़ें" → `null`)
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
- **Bottom:** primary **"मुझे सूचना भेजें"** / *"Send me alerts"* (→ S13; if permission already granted → S15/S16). Secondary `GoogleSignInButton` **"Google से सेव करें"** / *"Save with Google"* with caption `bodySmall` "ज़रूरी नहीं · फ़ोन बदलने पर प्रोफ़ाइल वापस मिलेगी" / *"Optional · get your profile back on a new phone"* → O11 → snackbar "Google अकाउंट से जुड़ गया ✓" → stays on S10 (button becomes "✓ {email} से सेव"). Cancel → nothing changes.
- **A11y:** hero is one node: "आप अभी 7 नौकरियों के योग्य हैं, और 2 में ख़ुद जाँचना होगा".
- **Analytics:** `match_count_shown{eligible, check, secs}`, `onb_result_cta{cta}`.

## S11 Phone — retired 2026-11-19
No phone number is collected. Sign-in is Google via O11 (see B16, S10, S26, S38 and 03 SC-03).

## S12 OTP — retired 2026-11-19
No OTP. The "which profile to keep" question is D10.

## O11 Sign in with Google (system sheet) — P0
- Started only by `GoogleSignInButton` (06 §K1): `CredentialManager.getCredential(GetCredentialRequest(listOf(GetSignInWithGoogleOption(serverClientId, nonce))))`. The bottom sheet, account list and consent text are Google's; we do not draw or restyle them. Account switching (D11) uses the same call, which always shows the account chooser.
- Result → Firebase `GoogleAuthProvider.getCredential(idToken)` → `linkWithCredential` (guest) or `signInWithCredential` (account exists) → API `merge` (SC-03).
- Errors: `GetCredentialCancellationException` → silent return; `NoCredentialException` (no Google account) → Google offers "Add account"; other → snackbar "साइन इन नहीं हो पाया, फिर कोशिश करें" / *"Couldn't sign in, try again"*. Offline → button disabled with "साइन इन के लिए इंटरनेट चाहिए".
- **ev:** `signin_start{surface}`, `signin_result{result, merge}`.

### B16 Sign-in offer — P0 (new)
- Sheet (H4). Icon `cloud_done` 40 in `primaryContainer` circle. Title per variant:
  - `pro`: **"Pro को अपने Google अकाउंट से जोड़ें?"** / *"Link Pro to your Google account?"*
  - `sync`: **"अपने फ़ॉर्म Google अकाउंट में सेव करें"** / *"Save your forms to your Google account"*
  - `family`: **"परिवार का लिंक नए फ़ोन पर भी रहे"** / *"Keep family links on a new phone"*
  - `settings`: **"Google से साइन इन करें"** / *"Sign in with Google"*
- 3 bullets (`check_circle` success 20): "फ़ोन बदलने पर प्रोफ़ाइल, फ़ॉर्म और Pro वापस मिलेंगे" · "मुफ़्त है — कोई पासवर्ड या मोबाइल नंबर नहीं" · "हम सिर्फ़ आपका नाम और ईमेल लेते हैं" / *"Get your profile, forms and Pro back on a new phone" · "Free — no password or phone number" · "We take only your name and email"*.
- `GoogleSignInButton` full width "Google से साइन इन करें" · `TextButton` **"अभी नहीं"** (variant `pro`: "बिना साइन इन के जारी रखें" / *"Continue without signing in"* → Play sheet).
- Frequency: `pro` once per install; `sync` dismiss hides 30 days; never two offers in a session.
- **ev:** `signin_offer_view{variant}`, `signin_offer_result{variant, action}`.

## S13 Notification permission primer — P0
- **Content (centred):**
  - Mock notification card (static composable, not an image): app icon + "Sarkari Match · अभी" + **"कल आखिरी दिन! SSC GD कांस्टेबल"** + "फ़ीस ₹100 · आप योग्य हैं ✓".
  - `headlineSmall`: **"आखिरी तारीख से पहले याद दिलाएँ?"** / *"Get reminded before the last date?"*
  - 3 `BulletRow`s (`bodyLarge`):
    1. `event` "कब: नई नौकरी आने पर, हर 3 दिन, आखिरी 3 दिन में और आखिरी दिन सुबह" / *"When: a new job, every 3 days, in the last 3 days and on the last morning"*
    2. `bedtime` "दिन में 3 से ज़्यादा नहीं · रात 10 से सुबह 7 चुप" / *"Never more than 3 a day · silent 10 pm–7 am"*
    3. `notifications_active` "सिर्फ़ इसी ऐप में — कोई SMS या WhatsApp नहीं। इजाज़त न दी तो हम याद नहीं दिला पाएँगे।" / *"Only in this app — no SMS or WhatsApp. Without permission we can't remind you."*
  - Family-member variant (from S35): title **"{name} के ज़रूरी अलर्ट पाने के लिए इजाज़त दें"**, bullet 1 "सिर्फ़ आखिरी तारीख, एडमिट कार्ड और DV" 
- **Bottom:** primary "इजाज़त दें" / *"Allow"* → `rememberLauncherForActivityResult(RequestPermission(POST_NOTIFICATIONS))` (API 33+); API < 33 → if disabled open settings, else skip. `TextButton` "अभी नहीं" / *"Not now"*.
- **Result:** granted → S15 (if needed) else S16 · denied → S20 with O02 · permanently denied (rationale false after a denial) → D01.
- **ev:** `notif_perm_prompt{api}`, `notif_perm_result{granted, permanent}`.

## S14 Exact-alarm primer — P0 (was P1)
- Icon `alarm_on` 48 · `headlineSmall` "बिना इंटरनेट भी आखिरी दिनों की याद" · body "अगर किसी वजह से हमारी सूचना न पहुँचे, तो आपका फ़ोन ख़ुद याद दिलाएगा — आखिरी तारीख से एक दिन पहले और आखिरी दिन सुबह 8:15 बजे।" / *"If our alert doesn't arrive, your phone reminds you itself — the day before and at 8:15 am on the last day."* · steps: "‘अलार्म और रिमाइंडर’ में Sarkari Match को चालू करें" · primary "सेटिंग खोलें" · text "रहने दें".

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
- Result card: success (`successContainer`) "सब ठीक है! अब आपकी कोई आखिरी तारीख नहीं छूटेगी।" / failure (`warningContainer`) "आपका फ़ोन सूचना रोक रहा है" + buttons "फ़ोन की सेटिंग देखें" (→S15) and "फ़ोन पर बैकअप रिमाइंडर चालू है ✓" status line (or "चालू करें" → S14 if exact alarms are blocked). No upsell on this screen.
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
5. **Ad slot (free users only)** — `NativeAdSlot` (06 §J1) as the last list item after all job sections, never between job cards; label row "विज्ञापन · Ad" · "क्यों?" (→ B17) · "विज्ञापन हटाएँ" (→ S30). Pro/trial: not rendered. No-fill/offline: collapses to nothing.
6. Footer `DisclaimerNote` (short): "सरकारी ऐप नहीं · हर जानकारी आधिकारिक नोटिस से मिलाएँ".

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
**Analytics:** `home_view{n_today, n_soon, n_new, n_pending, n_check, ad_slot}`, `card_tap{match_id, section}`, `card_action{action, section}`, `home_refresh`.

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
**Analytics:** `job_view{match_id, verdict, days_left, src}`, `pdf_open`, `apply_site_open`, `apply_site_return{mins}`, `rule_add_info{field}`, `wyn_copy{field}`, `doc_checklist_tick`, `job_share`.

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
  - Primary (filled `primary`): **"14 दिन मुफ़्त शुरू करें"** / *"Start 14 days free"* · caption "कोई पैसा नहीं लगेगा, कार्ड नहीं चाहिए" · text "अभी नहीं". The tap starts the trial (`POST /v1/me/trial`) → B08; "अभी नहीं" leaves the trial available (S30, next B02 after 7 days).
- Variant **plans** (trial used): same header; two compact `PlanOptionCard`s (₹199/साल — "₹17/महीना · सबसे सस्ता" badge; ₹49/सीज़न — "4 महीने") + primary "₹199 में Pro लें" → S30 with plan preselected.
- ev `paywall_view{variant}`, `paywall_cta{cta}`.

### B08 Trial started
- Title **"14 दिन Pro चालू — {date} तक"** · 3 `check_circle` rows: "एडमिट कार्ड से रिज़ल्ट तक हर सूचना" · "परीक्षा की तारीख बदली तो तुरंत" · "कोई विज्ञापन नहीं".
- Guest: `GoogleSignInButton` "Google से सेव करें" + caption "फ़ोन बदलने पर भी Pro और फ़ॉर्म साथ रहेंगे" · text "अभी नहीं". Signed in: primary "ठीक है".

### B17 Why ads — P0 (new)
- See 06 §J2. Title **"विज्ञापन क्यों दिखता है?"** / *"Why do I see an ad?"* · items: "मुफ़्त अलर्ट चलाने का ख़र्च इसी से निकलता है" · "विज्ञापन वालों को आपकी प्रोफ़ाइल, उम्र या राज्य नहीं मिलता; विज्ञापन आपकी पसंद के हिसाब से नहीं चुने जाते" · "रिमाइंडर, नौकरी के ब्योरे और सूचनाओं में कभी विज्ञापन नहीं" · `TextButton` "यह विज्ञापन ठीक नहीं — बताएँ" · `SmSecondaryButton` "Pro में कोई विज्ञापन नहीं".
- **ev:** `ads_notice_view{surface}`, `ad_report`.

### B03 Not interested
- Title **"इसमें रुचि नहीं?"** · body "हम इसकी याद नहीं दिलाएँगे। (वैकल्पिक) वजह बताएँ — इससे आगे बेहतर नौकरियाँ दिखाएँगे।"
- `FilterChip`s (multi): पोस्ट पसंद नहीं · जगह दूर है · तैयारी नहीं है · पहले से नौकरी है · मैं योग्य नहीं हूँ (→ also opens B05 after). API values in the same order: `not_interested_in_post`, `location`, `not_prepared`, `already_employed`, `not_eligible`.
- Primary "छुपाएँ" / *"Hide"* · text "रद्द करें".
- After: card removed, snackbar "छुपा दिया · वापस लाएँ" (6 s).
- ev `job_not_interested{reasons}`, `undo_hide`.

### B04 Snooze
- Title **"कब याद दिलाएँ?"** · options as `RadioListItem` (see SC-12) with computed dates: ≥ 4 days left "कल सुबह 9 बजे (20 नवं)" · "आखिरी तारीख से 2 दिन पहले (19 दिसं)"; 2–3 days left "कल सुबह 9 बजे (20 नवं)" · "आज शाम 7 बजे" (prototype frame). Note `bodySmall`: "आखिरी दिन और उससे एक दिन पहले की याद हमेशा आएगी।"
- Primary "ठीक है".  ≤ 1 day variant: info only.
- ev `job_snoozed{option}`.

### B05 Report wrong match
- Title **"मैच गलत क्यों लगता है?"** · radio: उम्र गलत है · पढ़ाई गलत है · वर्ग/आरक्षण गलत है · राज्य/निवास गलत है · लिंग गलत है · कुछ और · Optional `OutlinedTextField` "और बताएँ (वैकल्पिक)" 140 chars with counter.
- Link "मेरी प्रोफ़ाइल में गलती है → बदलें" (→S27).
- Primary "भेजें" → snackbar "धन्यवाद! हम 24 घंटे में जाँचेंगे।"
- ev `wrong_match_report{reason, has_note}`.

### B06 Check-yourself
- Title **"यह बात ख़ुद जाँच लें"** · rule card (warning container): "? कंप्यूटर सर्टिफ़िकेट: नोटिस में ‘CCC या समकक्ष’ लिखा है। आपने यह जानकारी नहीं दी।"
- Actions (vertical, full width): primary "मेरे पास है — प्रोफ़ाइल में जोड़ें" (→S27 for the rule's `fix_field`) · outlined "PDF खोलें (पेज 7)" · text "मैं योग्य नहीं — छुपाएँ" (`not_interested` with reason `not_eligible`).
- For notice-ambiguity variant (no `fix_field`): primary "मैंने जाँच लिया — मैं योग्य हूँ" → action `confirm_eligible {rule}`; verdict stays "ख़ुद जाँचें", the "?" prompt is hidden, reminders continue.

### B07 Corrigendum details
- Title **"नोटिस में बदलाव"** · date "18 नवंबर 2026 को" · diff rows: label + `old` (strikethrough, onSurfaceVariant) → `new` (SemiBold): "आखिरी तारीख: ~~14 नवं~~ → **21 नवं**". Effect row with icon: "आप पर असर: कोई नहीं — आप अब भी योग्य हैं ✓" / "आप पर असर: अब आप योग्य नहीं (उम्र सीमा 27 → 25)".
- Button "बदलाव वाला नोटिस (PDF)" + "ठीक है".

---

## S22 My forms (tracker) — P0
- **Top bar:** "मेरे फ़ॉर्म" / *"My forms"*. Filter chips under bar: "चालू (4)" · "पूरे हुए (1)".
- **Pro status strip** (free users): `ProUpsellInline` (tertiaryContainer): `workspace_premium` "फ़ॉर्म भरने के बाद की सूचना (एडमिट कार्ड, रिज़ल्ट) Pro में मिलती है" + `TextButton` "₹17/महीना से" → S30. Trial users: "Pro ट्रायल · 9 दिन बाकी".
- **`ClashBanner`** (P1) above list when relevant.
- **Sync card** (guest with ≥ 1 Applied, dismissible 30 days): `cloud_done` "अपने फ़ॉर्म Google अकाउंट में सेव करें — फ़ोन बदलने पर भी रहेंगे" + `TextButton` "सेव करें" (→ B16 `sync`) · `close`.
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
`InboxRow`: icon (push `notifications` / on-device backup `alarm_on` / sent to family `family_restroom`), title, time, delivery status text ("पहुँचा · खोला" / "पहुँचा" / "नहीं पहुँचा"), tap → target. Sticky date headers "आज", "कल", "17 नवं". Empty "अभी कोई सूचना नहीं भेजी".

## S25 Past & hidden jobs — P1
`PrimaryTabRow`: "रुचि नहीं" · "तारीख निकल गई" · "अब योग्य नहीं". Rows: title, reason/date, action `TextButton` ("वापस लाएँ" if window open / "भरा था" within 30 days). Empty per tab.

## S26 Profile — P0
- **Top bar:** "प्रोफ़ाइल" / *"Profile"*.
- **`AccountHeader`** (06 §K2): signed in → Google photo/initials, name, email (`bodySmall`), chevron → S38; guest → `person` avatar, **"मेहमान (Guest)"**, "फ़ोन बदलने पर प्रोफ़ाइल साथ रखने के लिए साइन इन करें" + compact `GoogleSignInButton` "Google से साइन इन करें" (→ O11 directly; no B16 here). Below: `ProfileCompletenessBar`.
- **`ProStatusCard`:** Free: tertiaryContainer, `workspace_premium`, "Pro लें — फ़ॉर्म भरने के बाद की हर सूचना" · 3 bullets ("एडमिट कार्ड से रिज़ल्ट तक", "कोई विज्ञापन नहीं", "परिवार को भी अलर्ट (जल्द)") · button "₹199/साल देखें". Trial: "Pro ट्रायल · 26 नवं तक" + "प्लान चुनें". Pro: "Pro चालू · 18 नवं 2027 तक" + "मैनेज करें" (→S32).
- **Section "मेरी जानकारी"** (`SettingsRow` with leading icon, label, value, chevron; tap → S27): कौन-सी नौकरियाँ (SSC, रेलवे, पुलिस) · पढ़ाई (ग्रेजुएशन — BA, पास) · जन्मतिथि (12/03/2003 · 23 साल) · वर्ग (OBC-NCL) · राज्य (उत्तर प्रदेश · निवास प्रमाण: हाँ) · लिंग (पुरुष) · दिव्यांग (नहीं) · पूर्व सैनिक (नहीं). Unknown values show warning text "बताया नहीं" + `?` icon.
- **Section "अलर्ट"**: अलर्ट सेटिंग (S28; value "चुप: रात 10–सुबह 7") · अलर्ट पक्के करें (S29; value with status dot+text "सब ठीक" / "ध्यान दें") · परिवार अलर्ट (S34, P1; "Pro" tag if locked) · मुझे किसके अलर्ट आते हैं (S49, P1; only if this phone follows someone) · सर्टिफ़िकेट (S36, P1).
- **Section "ऐप"**: भाषा (हिंदी; value shows the native name) · थीम (सिस्टम जैसा) · दोस्त को बताएँ (S45, P1) · मदद और जानकारी (S39).
- **Section "अकाउंट"** (separated by 24 dp, destructive items live inside S38): अकाउंट और डेटा (S38) · प्राइवेसी नोटिस (S47).
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
  - `alarm_on` **फ़ोन पर बैकअप रिमाइंडर** / *Backup reminder on this phone* (P0, **on by default**) — switch; supporting text "सूचना न पहुँचे तो फ़ोन ख़ुद याद दिलाएगा (आखिरी दिन से एक दिन पहले और आखिरी दिन सुबह)"; → S14 if exact alarms are blocked; value "समय पर" / "लगभग समय पर (±15 मिनट)".
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
  5. आख़िरी टेस्ट — "19 नवं, पहुँच गया ✓" / "14 दिन से जवाब नहीं" [टेस्ट करें → S16]. Fed by the weekly self-test push (`/v1/push-tests`), which replaces the retired weekly silent ping
  6. फ़ोन पर बैकअप रिमाइंडर — "चालू · समय पर" / "चालू · लगभग समय पर" / "बंद" [चालू करें → S28 / S14]
- **ev:** `delivery_health_view{issues}`, `delivery_fix_tap{item}`.

## S30 Paywall (Pro plans) — P0
- **Top bar:** `close` · title none.
- **Header:** `workspace_premium` 48 in tertiaryContainer circle · `headlineMedium` **"Sarkari Match Pro"** · `bodyLarge` **"फ़ॉर्म भरने से नतीजे तक — कोई ज़रूरी तारीख न छूटे"** / *"From application to result — never miss a key date"*.
- **Benefits** (`BenefitRow`: icon 24 `tertiary` + `titleSmall` + `bodySmall`):
  1. `badge` एडमिट कार्ड, सिटी स्लिप, परीक्षा की तारीख — "आते ही सूचना"
  2. `update` परीक्षा की तारीख बदली — "तुरंत बताएँगे"
  3. `key` आंसर की, आपत्ति की आखिरी तारीख, रिज़ल्ट, कट-ऑफ़
  4. `fact_check` DV और मेडिकल की तारीख — "न जाने पर दोबारा मौका नहीं मिलता"
  5. `block` **कोई विज्ञापन नहीं** / *No ads* — "ऐप में कहीं भी विज्ञापन नहीं"
  6. `event_repeat` **परीक्षा टकराव** / *Exam clash alerts* — "दो परीक्षाएँ एक ही दिन या हफ़्ते में हों तो बताएँगे" · "जल्द" tag (P1)
  7. `verified` **सर्टिफ़िकेट की जाँच** / *Certificate validity* — "OBC/EWS या निवास प्रमाण पत्र इस नोटिस के लिए पुराना हो तो बताएँगे" · "जल्द" (P1)
  8. `family_restroom` **परिवार को भी अलर्ट** / *Family alerts* — "माता-पिता या भाई-बहन के फ़ोन पर आखिरी तारीख और DV की सूचना — एक लिंक भेजकर जोड़ें" · "जल्द" (P1)
  - Benefits 1–4 can be merged into one row "फ़ॉर्म भरने के बाद हर सूचना" on short screens (prototype shows the merged version).
- **Plans** (`PlanOptionCard` radio group, 12 gap):
  - **साल भर — ₹199** · sub "सिर्फ़ ₹17/महीना" · badge (tertiary filled) "सबसे सस्ता" · default selected.
  - **एक सीज़न — ₹49** · sub "4 महीने · एक भर्ती के मौसम के लिए".
  - Prices from `ProductDetails.formattedPrice`; per-month computed.
- **Primary** (full-width filled `Button`, `primary` colour — the one CTA blue across the app; Pro saffron is used for badges/icons only) **"₹199 में Pro लें"** / *"Get Pro for ₹199"*; trial-eligible users additionally see a `TextButton` **"14 दिन मुफ़्त आज़माएँ"** (calls `POST /v1/me/trial`; no Play free-trial offer exists, so the buy button never mentions a trial).
- Secondary row: `TextButton` "पहले ख़रीदा था? वापस लाएँ".
- Legal `bodySmall`: "भुगतान सिर्फ़ Google Play से — UPI, कार्ड या Play बैलेंस। साल वाला प्लान अपने-आप रिन्यू होगा — Play Store में कभी भी बंद करें। मुफ़्त अलर्ट हमेशा मुफ़्त रहेंगे।" / *"Paid only through Google Play — UPI, card or Play balance. The yearly plan renews automatically — cancel any time in the Play Store. Free alerts stay free."*
- **Guest:** tapping the primary button the first time shows B16 `pro`; either answer continues to the Play sheet (O06). The purchase carries `obfuscatedAccountId` = the server's opaque `billing_account_ref` for this account (not derived from profile data).
- **States:** products loading → plan skeletons, button disabled; billing unavailable → error card "Google Play से भुगतान अभी नहीं हो पा रहा" + retry; offline → button disabled "भुगतान के लिए इंटरनेट चाहिए".
- **ev:** `paywall_view{surface, trial_eligible}`, `plan_select{plan}`, `purchase_start{plan, method}`.

## S31 Payment status — P0
| State | Icon (48) | Title | Body | Buttons |
| --- | --- | --- | --- | --- |
| success | `check_circle` success | **"Pro चालू हो गया!"** | "{date} तक आपके सभी फ़ॉर्म की हर सूचना मिलेगी। अब कोई विज्ञापन नहीं।" | primary "मेरे फ़ॉर्म देखें" (→S22) · text "परिवार को जोड़ें" (→S34, P1; hidden until P1) · guest: compact `GoogleSignInButton` "Pro को Google अकाउंट से जोड़ें" |
| pending | `hourglass_top` warning | **"भुगतान की पुष्टि हो रही है"** | "पैसे कट गए हैं तो चिंता न करें — पुष्टि होते ही Pro चालू होगा और हम सूचना भेजेंगे। आमतौर पर 10 मिनट।" | primary "ठीक है" · text "मदद चाहिए" |
| failed | `error` error | **"भुगतान नहीं हो पाया"** | "कोई पैसा नहीं कटा। अगर कटा है तो 3–5 दिन में वापस आ जाएगा।" | primary "फिर से कोशिश करें" · text "बाद में" |
| cancelled | — | (no screen; snackbar on S30 "भुगतान रद्द किया") | | |
- ev `purchase_result{status, code, plan}`.

## S32 Manage plan — P0
`PlanSummaryCard` (plan, "{date} तक", renew state) · rows: "साल वाले प्लान पर जाएँ" (season users) · "Play Store में मैनेज करें" (`https://play.google.com/store/account/subscriptions?sku=…&package=…`) · "पहले ख़रीदा था? वापस लाएँ" → D08 · "रसीदें" (list). Free users never land here (redirect S30).

## S33 WhatsApp alerts — retired 2026-11-19
No WhatsApp. Pro delivery reliability is S29 + on-device backup (S28/S14).

## S34 Family alerts — P1 (Pro)
- **Top bar:** back · "परिवार अलर्ट" / *"Family alerts"*.
- Explainer card (`primaryContainer`): `family_restroom` "माता-पिता या भाई-बहन को अपने फ़ोन पर आपकी ज़रूरी तारीख़ें मिलें" · "उन्हें सिर्फ़ आखिरी 2 दिन, एडमिट कार्ड और DV की सूचना जाएगी — आपकी प्रोफ़ाइल, जन्मतिथि या वर्ग नहीं। मोबाइल नंबर नहीं चाहिए: आप उन्हें एक लिंक भेजेंगे।"
- List of `FamilyLinkRow` (relation, nickname, status chip "जुड़ गए" / "निमंत्रण भेजा" / "मना किया" / "रुका हुआ", overflow "हटाएँ" → D06 / "लिंक फिर से भेजें" → S48).
- Primary "सदस्य जोड़ें (2 तक)" → B12. Disabled at 2 with reason.
- Locked (free): explainer + lock card "यह Pro में है" + "Pro देखें".
- **ev:** `family_view{n_linked}`, `family_add_tap`.

### B12 Add family member
- Title **"किसे जोड़ना है?"** / *"Who do you want to add?"* · `ChipGroup` single: पिता · माँ · भाई · बहन · पति · पत्नी · दूसरा · optional `OutlinedTextField` "नाम (सिर्फ़ आपको दिखेगा)" (30 chars).
- Note `bodySmall`: "आप उन्हें एक लिंक भेजेंगे। लिंक खोलकर वे अपने फ़ोन पर Sarkari Match में अलर्ट पाएँगे।"
- Primary **"निमंत्रण बनाएँ"** / *"Create invite"* → S48.

## S48 Family invite (share) — P1 (new)
- **Top bar:** back · "निमंत्रण भेजें" / *"Send invite"*.
- `InviteShareCard` (06 §K5): title "पापा के लिए निमंत्रण" · QR (200 dp, dark on white) · link `sarkarimatch.in/f/8KQ2-…` + `content_copy` · "7 दिन तक चलेगा · एक बार इस्तेमाल होगा" · status chip "इंतज़ार" → "जुड़ गए ✓".
- Primary **"लिंक शेयर करें"** / *"Share link"* (`share`) → Android sharesheet (O08) with text (gender-neutral, 07 §7): "नमस्ते! मेरे सरकारी नौकरी के फ़ॉर्म की ज़रूरी तारीख़ें आपको भी याद दिलाई जाएँ, इसके लिए यह लिंक खोलें: {link} — Sarkari Match" / *"Hi! Open this link to get reminders of my government job form dates too: {link} — Sarkari Match"*. The user picks any app; we send nothing ourselves.
- Secondary `TextButton` "पास बैठे हैं? QR दिखाएँ" (full-screen QR at max brightness) · destructive `TextButton` "निमंत्रण रद्द करें".
- States: expired → "समय निकल गया" + "नया लिंक बनाएँ"; linked → success card "पापा जुड़ गए ✓ — अब उन्हें भी ज़रूरी तारीख़ें मिलेंगी" + "ठीक है" (→ S34).
- **ev:** `family_invite_create{relation}`, `family_invite_share{method}`, `family_invite_cancel`.

## S35 Family invite (invitee) — P1
- No top bar (root for invitees). Icon `family_restroom` 48 · `headlineSmall` **"Ravi ने आपको परिवार अलर्ट के लिए जोड़ा है"** / *"Ravi added you for family alerts"* (name = aspirant's first name or the account's given name; never email. The name travels inside the end-to-end-encrypted watch-list and is rendered on the member's phone; the server never sees it).
- Body: "आपको उनके फ़ॉर्म की सिर्फ़ ज़रूरी तारीख़ें मिलेंगी — आखिरी दिन, एडमिट कार्ड और DV। कोई मोबाइल नंबर या अकाउंट नहीं चाहिए।"
- Primary **"हाँ, मुझे अलर्ट भेजें"** → S13 family variant → S49 · text "नहीं चाहिए" (declines; aspirant sees "मना किया").
- States: invalid/expired token → "यह निमंत्रण अब नहीं चलता — उनसे नया लिंक माँगें"; already linked → S49.

## S49 Alerts I receive (family member) — P1 (new)
- **Top bar:** "मुझे किसके अलर्ट आते हैं" / *"Alerts I receive"* (as a root for family-only users; else back).
- One `FamilyFollowingCard` per aspirant: "Ravi (बेटा)" · next dates (deadline chips): "SSC GD — कल आखिरी दिन", "RRB NTPC — एडमिट कार्ड आया" · last alert time · `TextButton` "अलर्ट बंद करें" → D12. Paused: "Ravi का Pro रुका हुआ है — अलर्ट अभी बंद हैं".
- Footer: "अपनी नौकरियाँ भी देखें?" `TextButton` → S04 (becomes a normal user too) · guest: B16 `family` once ("नया फ़ोन लें तो भी यह लिंक रहे").
- No ads on this screen.
- **ev:** `family_following_view{n}`, `family_stop`.

## S36 Certificates — P1
Rows: OBC-NCL / EWS सर्टिफ़िकेट ("मार्च 2025 में बना") · निवास प्रमाण पत्र · दिव्यांग प्रमाण पत्र; each `ValidityBadge` ("इस साल के नोटिस के लिए ठीक" ✓ / "नया बनवाना पड़ सकता है" ⚠) + "बदलें" (month-year picker). Note "हम सर्टिफ़िकेट की फ़ोटो नहीं रखते — सिर्फ़ तारीख।"

## S37 Clash — P1
Header `event_repeat` "परीक्षा टकराव" · two `ClashCompareCard`s side-by-side (stack at font ≥ 1.3) with exam name, date, shift, city · guidance: "दोनों बोर्ड के नियम अलग हैं — तारीख बदलने के लिए आधिकारिक सूचना देखें। हम तारीख बदलते ही बताएँगे।" · links.

## S38 Account & data — P0
- **Account card:** signed in → Google photo, name, email + row `switch_account` "Google अकाउंट बदलें" (→ D11); guest → "आप मेहमान के रूप में इस्तेमाल कर रहे हैं" / *"You're using the app as a guest"* + "साइन इन नहीं किया तो फ़ोन बदलने पर प्रोफ़ाइल दोबारा बनानी होगी" + `GoogleSignInButton`.
- Rows: अलर्ट वाला फ़ोन ("यही फ़ोन अलर्ट पाता है" / "दूसरे फ़ोन पर: Redmi Note 12" + "इसी फ़ोन पर पाएँ") · `privacy_tip` प्राइवेसी नोटिस (S47) · `family_restroom` परिवार लिंक (S34/S49 counts) · **"मेरा डेटा डाउनलोड करें"** (`download`) → D04.
- Danger zone (24 dp gap, `titleSmall` "सावधानी" in error colour): `logout` "साइन आउट" (signed in only, → D02) · `delete` **"अकाउंट हमेशा के लिए हटाएँ"** (error text) → explainer card expands: "क्या हटेगा: प्रोफ़ाइल, फ़ॉर्म, सूचनाओं का इतिहास, परिवार लिंक और Google से जुड़ाव। 7 दिन में पूरी तरह हट जाएगा। Pro का पैसा अपने-आप वापस नहीं होगा — सब्सक्रिप्शन Play Store में बंद करें।" → D03.
- **ev:** `data_export_request`, `signout`, `account_switch`, `account_delete{step}`.

## S39 Help & about — P0
FAQ (`FaqItem` expandable, 10 items): "आप किन नोटिस को देखते हैं?" · "‘ख़ुद जाँचें’ का क्या मतलब है?" · "सूचना नहीं आ रही, क्या करूँ?" (→S29) · "क्या आप मेरा फ़ॉर्म भरते हैं?" ("नहीं…") · "क्या यह सरकारी ऐप है?" ("नहीं…") · "मेरा डेटा कहाँ जाता है?" · "Pro कैसे बंद करूँ?" · "गलत मैच कैसे बताऊँ?" · "उम्र कैसे गिनते हैं?" · "किन राज्यों की नौकरियाँ?" (launch states). Contact rows: ईमेल · "ऐप में सुझाव भेजें" (form, attaches app version and anonymous ID only). FAQ adds "मेरा मोबाइल नंबर क्यों नहीं माँगा?" and "फ़ोन बदलूँ तो क्या होगा?" (SC-46). About: disclaimer, sources list, privacy policy, terms, open-source licences, version.

## S41 Force update — P0
Icon `system_update` 48 · "ज़रूरी अपडेट" · "नए वर्ज़न में अलर्ट सही समय पर आते हैं। आगे बढ़ने के लिए अपडेट करें।" · primary "अपडेट करें" (In-App Update IMMEDIATE → fallback Play Store). No back (back exits app).

## S42 Outage — P0
Icon `report` 48 · "अभी हमारा सर्वर काम नहीं कर रहा" · body + optional Remote Config message · primary "फिर कोशिश करें" · if cache exists this is never full-screen (O03 instead).

## S43/S44/S45/S46 — P1/P2 (summary)
- **S43 Age countdown (P2):** list `AgeChanceRow`: exam, "आपकी आख़िरी योग्य साल: 2029", "लगभग 2 मौके" chip (urgent if 1).
- **S44 Digest (P1):** header "आज का सारांश · 19 नवं" + compact `MatchCard`s + "बदलाव" list + (free only) one `NativeAdSlot` at the very end.
- **S45 Invite (P1):** headline "दोस्त को बताएँ — दोनों को 1 महीना Pro मुफ़्त", share button (Android sharesheet), status "2 दोस्त जुड़े".
- **S46 Share card (P1):** preview of the rendered card (primary bg, white text "मैं 7 सरकारी नौकरियों के योग्य हूँ") + "शेयर करें"; no DOB/category/phone on card.

## Dialog copy (D01–D09)

| ID | Title | Body | Confirm | Dismiss |
| --- | --- | --- | --- | --- |
| D01 | सूचना बंद है | "आखिरी तारीख की याद के लिए सेटिंग में ‘सूचना’ चालू करें।" | सेटिंग खोलें | अभी नहीं |
| D02 | साइन आउट करें? | "इस फ़ोन पर अलर्ट आना बंद हो जाएगा। आपकी प्रोफ़ाइल आपके Google अकाउंट में सुरक्षित रहेगी।" | साइन आउट (error) | रद्द करें |
| D03 | अकाउंट हमेशा के लिए हटाएँ? | "यह वापस नहीं होगा। पुष्टि के लिए नीचे ‘हटाएँ’ लिखें।" → text field (signed-in users: "Google से पुष्टि करें" → O11 re-auth instead) | हटाएँ (error, enabled after typing / re-auth) | रद्द करें |
| D04 | आपका डेटा तैयार है | "फ़ाइल ‘sarkari-match-data.json’ में आपकी प्रोफ़ाइल, नौकरियों पर आपके जवाब और सूचनाओं का इतिहास है।" | फ़ाइल सेव करें | बंद करें |
| ~~D05~~ | retired 2026-11-19 (WhatsApp) | — | — | — |
| D06 | {relation} को हटाएँ? | "उन्हें अब आपके अलर्ट नहीं जाएँगे। उन्हें एक सूचना जाएगी कि लिंक बंद हुआ।" | हटाएँ | रद्द करें |
| D07 | बदलाव सेव नहीं हुए | "बिना सेव किए वापस जाएँ?" | छोड़ दें | सेव करें |
| D08 | Pro वापस आ गया / कोई ख़रीद नहीं मिली | "{date} तक Pro चालू है।" / "इस Google अकाउंट या इस फ़ोन के Play अकाउंट पर Pro नहीं मिला।" | ठीक है | मदद |
| D09 | नया वर्ज़न उपलब्ध | "अपडेट में सुधार हैं। अभी अपडेट करें?" | अपडेट करें | बाद में |
| D10 | कौन-सी प्रोफ़ाइल रखें? | "इस Google अकाउंट का एक सेव किया हुआ बैकअप है।" + two option cards (K8) with summaries: "सेव की हुई (बैकअप)" / "इस फ़ोन वाली". Shown when sign-in returns `has_backup` and this phone already has a profile (no server 409). "सेव की हुई" restores the encrypted backup; "इस फ़ोन वाली" overwrites the backup | यह रखें | — (must choose; back = keep saved) |
| D11 | Google अकाउंट बदलें? | "{email} की जानकारी उसी अकाउंट में सुरक्षित रहेगी। इस फ़ोन पर दूसरे अकाउंट से साइन इन करेंगे।" | अकाउंट बदलें | रद्द करें |
| D12 | {name} के अलर्ट बंद करें? | "आपको अब {name} की ज़रूरी तारीख़ों की सूचना नहीं आएगी। दोबारा चाहिए तो उनसे नया लिंक माँगें।" | बंद करें (error) | रद्द करें |

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
