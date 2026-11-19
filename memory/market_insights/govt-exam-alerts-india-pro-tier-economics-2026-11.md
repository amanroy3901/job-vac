---
niche: govt-exam-alerts-india (paid "Pro" layer: post-application tracking, reliable delivery, low-ticket subscriptions)
platform: multi
analyzed_at: 2026-11-19
status: fresh
stale_after: 2027-05-19
trend_velocity: stable
overall_verdict: cool
key_insight: "Indians do pay for low-ticket utility subscriptions at scale only when the utility is daily (Truecaller ~2M India subs on a base of hundreds of millions, i.e. well under 1%); an event-driven govt-job Pro tier at INR 199/yr has cheap delivery costs (WhatsApp utility ~INR 0.12-0.15/msg) but no observed payers anywhere in the category."
top_signals:
  - "Truecaller: close to 2M India premium subscribers at end-2025 (of >4M global); India Premium ~INR 75-99/mo or ~INR 529-899/yr; India user base ~350M (unverified) -> implied paid share well under 1%"
  - "RevenueCat-cited India/SEA D35 download-to-paid ~0.7% vs North America ~2.8% (secondary sources)"
  - "WhatsApp Business API India utility template ~INR 0.115-0.145 per delivered message (BSP blogs, 2026); transactional SMS ~INR 0.10-0.25 + GST + DLT"
  - "Competitor paid tiers exist but are tiny/unproven: India Govt Job Finder (iOS) lists IAP $2.99/mo, $4.99/yr while also claiming '100% free'; Naukri Alert (iOS) 'In-App Purchases' (price not visible)"
  - "Post-application tracking exists only as free web tools (TrueJobs Application Status Tracker); no app found doing per-application lifecycle tracking"
monetization_evidence:
  - "Truecaller Premium India (daily-use utility) — ~2M India subscribers"
  - "JioHotstar Mobile INR 79/mo, INR 149/quarter, INR 499/yr (Jan 2026) — Indians accept sachet/quarter passes for entertainment"
  - "Category: no evidence found of any govt-job alert app generating subscription revenue"
---

# Govt-exam alerts — Pro-tier economics & feasibility inputs (Nov 2026)

Research scope: only what the v2 "Sarkari Match Pro" idea changes versus the v1 files. Tooling caveat: WebFetch could not resolve appgoblin.info, truejobs.co.in or sarkariaiapp.github.io from this environment; everything below comes from search-result snippets and is flagged accordingly. No figure here is a measured result.

## 1. Competitors on the v2 core loop + Pro features

| Product | Matching | Persistent reminder | Post-application tracking | Paid tier | Source |
|---|---|---|---|---|---|
| Sarkari Scan (Android) | Education, age, category, experience + "eligibility status" insights | Not found | Not found | Not found | https://appgoblin.info/apps/com.sarkariscan (snippet) |
| India Govt Job Finder (iOS) | Filters; eligibility details | Not confirmed | Not found | Lists IAP $2.99/mo, $4.99/yr but also claims "100% free" (contradictory) | https://apps.apple.com/us/app/-/id6743082222 ; https://apps.appfollow.io/ios/india-govt-job-finder/6743082222?country=us |
| Naukri Alert: Govt Job App (iOS, Sep 2025) | Filters | Saved-job deadline reminders | Not found | "In-App Purchases", price not visible | https://apps.apple.com/us/app/-/id6748553839 |
| EasyShiksha Govt Job Alerts | Filters | Deadline push | Not found | Not found | https://apps.apple.com/us/app/govt-job-alerts-easyshiksha/id6751952231 |
| Sarkari Job Alert (NaukriLatest, Android) | Qualification filter | Repeated last-date reminders (listing) | Admit card/result lists, not per-user | Not found | https://appgoblin.info/apps/com.naukrilatest.sarkarijobalert (snippet) |
| ApplyZ (Android) | — | — | "Admit card and result status updates" (informational lists) | Not found | https://appgoblin.info/apps/com.manish.applyZ (snippet) |
| TrueJobs (web) | Eligibility checker | No | **Application Status Tracker** (web tool, free as far as visible) | Not found | https://truejobs.co.in/application-status-tracker (snippet; page blocked) |
| SarkariFilled (Chrome ext, Android in testing) | Free eligibility check | — | — | Free; stores details and autofills official forms | https://peerlist.io/prrince/project/sarkarifilled--autofill-govt-forms |

**Read:** nobody found sells a lifecycle tracker; the closest (TrueJobs) gives a tracker away on the web. The only paid tiers in the category are tiny iOS apps with contradictory listings. That is a gap — but also zero evidence that anyone pays.

## 2. Indian WTP for INR 19–49/month utilities
- **Truecaller** (strongest analogue: daily-use Indian utility): close to 2M India subscribers at end-2025, >4M global; India pricing reported variously at INR 75/mo–INR 529/yr (Connect), INR 39/mo or INR 399/yr (ad-free), INR 99/mo–INR 899/yr. Paying users generate ~40x a free user. https://www.inderes.se/releases/truecaller-ab-truecaller-surpasses-4-million-premium-subscribers ; https://www.fonearena.com/blog/455409/truecaller-3-million-paid-users-1-million-ios-users.html/amp ; https://telecomtalk.info/truecaller-premium-family-one-plan-for-five/635234 ; https://zeebiz.com/technology/news-truecaller-ai-call-recording-feature-in-android-iphone-ios-transcribe-all-calls-summary-app-download-premium-subscription-monthly-fees-278046
  - Implication (estimate): even a ubiquitous daily utility converts well under 1% of Indian users.
- **RevenueCat-cited** India/SEA D35 download-to-paid ~0.7% vs NA ~2.8%; median India/SEA price ~$3.75/mo (secondary). https://thesocialoutline.com/blog/download-to-paid-conversion-rate-benchmarks ; https://revenuecat.com/state-of-subscription-apps-2025
- **Sachet / season pricing:** JioHotstar Mobile INR 79/mo, INR 149/quarter, INR 499/yr (Jan 2026) — quarter passes are normal in Indian consumer subscriptions; often bundled free into telecom recharges. https://www.indiatvnews.com/technology/news/jiohotstar-introduces-new-subscription-plans-starting-at-rs-79-for-new-users-from-january-28-2026-01-19-1026531
- **Google Play**: India minimum price cut to INR 10 (2015); prepaid (non-auto-renewing, top-up) subscriptions available in India — fits a INR 49 "season pass". Current floor not reconfirmed. https://india.googleblog.com/2015/07/minimum-purchase-price-for-apps-on.html ; https://inc42.com/buzz/google-play-introduces-prepaid-app-subscriptions-developers-emerging-markets-india
- **UPI AutoPay**: >50M new mandates in July 2025 (~2x YoY, NPCI via Business Standard); vendor guidance says AutoPay is cheapest for <INR 500/mo subs. But **~20M AutoPay revocations/month due to low balance** — involuntary churn is real for low-income users. https://www.business-standard.com/amp/finance/news/upi-autopay-revocations-hit-20-mn-monthly-over-low-customer-balances-125090700500_1.html ; https://www.zoho.com/payments/academy/payment-processing/upi-autopay-charges-limits-and-economics.html
- **DigiLocker** is free and government-run (document vault is a commodity). (Known; not re-searched.)

## 3. Retention: does post-application tracking add usage?
- SSC CGL 2025 (one real cycle): ~28 lakh applied (v1 file) → Tier 1 Sept 12–26 → provisional key Oct 16 with objections till Oct 19 (INR 50/question) → ~13.5 lakh response sheets → 1,39,395 qualified Tier 2 → Tier 2 Jan 18–19 → Tier 2 key objections till Feb 3 → DV/preferences later. https://news.careers360.com/ssc-cgl-2025-answer-key-out-tier-1-13-5-lakh-response-sheets-ssc-gov-in-challenge-link-fee-active-question-id-last-date ; https://news.careers360.com/ssc-cgl-2025-tier1-final-answer-key-response-sheet-marks-download-availability-deadline-february-8-official-notice-tier2-exam-date/amp
- **Read (estimate):** for the ~95% who stop at stage 1, a single application generates ~5–7 trackable events (correction window, city slip, admit card, exam, answer key/objection, result) over ~3–5 months; only ~5% get the further 4–6 events (Tier 2, DV, preference, medical). The task's "6–10 events per application" holds only for the minority who progress. With 6–10 applications/yr per active aspirant (estimate), that is ~30–60 events/yr — roughly weekly, with clusters. Also notable: roughly half of applicants did not even sit Tier 1, so many "applied" users go cold.

## 4. Delivery cost for Pro (WhatsApp/SMS fallback)
- WhatsApp utility template India ~INR 0.115–0.145 per delivered message (2026, BSP blogs; utility messages inside the 24h window chargeable from late 2026 per AiSensy). Marketing ~INR 1.09. BSP platform fees extra. https://m.aisensy.com/blog/whatsapp-pricing-update-october-2026/ ; https://telecrm.in/blog/whatsapp-business-api-pricing/
- Transactional SMS ~INR 0.10–0.25 + 18% GST, DLT registration required. https://www.messagecentral.com/blog/a2p-sms-pricing-india ; https://fast2sms.com/pricing
- **Read (estimate):** ~60–120 critical messages per Pro user per year ≈ INR 8–20/yr, i.e. 5–12% of net Pro revenue (INR ~170 after Play's 15%). Affordable; NOT affordable for the free tier at scale.
- OEM push-killing (Xiaomi/Oppo/Vivo) is well documented by push vendors; no India delivery-rate figure found. https://www.braze.com/docs/user_guide/message_building_by_channel/push/best_practices/chinese_push_deliverability/ ; https://dontkillmyapp.com/oppo

## 5. Data-pipeline feasibility (solo dev)
- No published benchmark of LLM accuracy on Indian recruitment-notification eligibility extraction was found.
- Rules are layered and conditional: category relaxations, departmental/ex-servicemen rules, time-limited state relaxations with exclusions (e.g., Telangana 2026 +10 yrs valid one year, not for uniformed services), override clauses, cross-references to other notifications. https://news.careers360.com/telangana-raises-upper-age-limit-by-10-years-for-govt-jobs-oc-candidates-revanth-reddy ; https://www.gconnect.in/orders-in-brief/service-rules/age-limit-central-government-service.html
- **Read (estimate):** LLM API cost per notification is small; the binding constraint is human verification time (est. 15–45 min per notification for a schema of age-on-cutoff, relaxations, qualification-with-cutoff-date, domicile, fee, documents, photo spec) and post-publication corrigenda. All-India coverage means hundreds of notifications/month — infeasible solo; central + one state (~30–80/month, estimate) is ~15–50 hrs/month. A false "eligible"/"not eligible" is a trust-killing error.
