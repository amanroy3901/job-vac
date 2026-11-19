---
niche: govt-exam-alerts-india (government job / sarkari exam notification + prep apps, India)
platform: apps
analyzed_at: 2026-11-19
status: fresh
stale_after: 2027-05-19
trend_velocity: stable
overall_verdict: warm
key_insight: "Government-exam aspirant volume is enormous and growing (RRB NTPC 1.22 cr + Group D 1.08 cr applications in 2025), but the money in the category sits in test-prep (Testbook, Adda247, PW) and even those leaders are loss-making or flat; job/exam ALERTS are a free loss-leader every incumbent gives away."
top_signals:
  - "RRB NTPC 2025: ~1.22 crore applications for 11,558 posts; RRB Group D 2025: ~1.08 crore for 32,438 posts; SSC CGL 2025: ~28.15 lakh; UPSC CSE 2025: ~9.3 lakh"
  - "Testbook Android app: 50M+ installs, 4.34 stars on ~879K ratings (Quash, AppBrain cross-listing)"
  - "Adda247 developer account 10M+ installs; FY25 operating revenue ~INR 217 Cr (flat), net loss ~INR 103.6 Cr"
  - "Official mySSC app (Android 11+, Aadhaar OTR) moved full SSC application flow in-app from June 2025"
  - "India app IAP revenue hit a record ~$345M in Q2 2026 but revenue per download is only ~$0.03"
monetization_evidence:
  - "Testbook unified Pass: ~INR 674-749/year (list INR 1,199) for all-exam mock tests; Testbook FY24 revenue INR 136.7 Cr, net loss INR 84.9 Cr"
  - "Physics Wallah govt-exam batches: INR 399 (SSC MTS practice) to INR 1,499-1,999 (SSC CHSL/CGL) per batch"
  - "Cyber cafes / CSCs charge ~INR 30-150 per government job application form filled (transactional WTP for form help, not alerts)"
---

# Govt-exam alerts (India) — Apps & Web App Category Analysis

**Data observed as of November 2026.**

Platforms run: **Apps** (this file) and **Web Search** (companion file `govt-exam-alerts-india-web-search-2026-11.md`).
Platform-selection note: the skill normally asks the user which platforms to include. This was run non-interactively, so Apps + Web Search were chosen as the two most relevant surfaces for an Android-first India product. **TikTok was deliberately skipped — TikTok has been banned in India since 2020**; the short-video equivalent is YouTube Shorts / Instagram Reels. Reddit and X were skipped because Indian sarkari-exam aspirants congregate on YouTube, Telegram and WhatsApp, not Reddit/X (flagged as a gap; not researched).

Tooling caveat: Play Store pages, AppGoblin and Similarweb could not be fetched directly from this environment (network-blocked); install counts and ratings below come from search-result snippets of third-party trackers (AppBrain, Quash, AppGoblin). Treat them as approximate.

## 1. Executive Summary (Key Insights)

1. **The audience is one of the largest single-intent audiences in the world.** 2025 recruitment cycles alone: RRB NTPC ~1.22 crore applications, RRB Group D ~1.08 crore, SSC CGL ~28 lakh, UPSC CSE ~9.3 lakh. Commonly cited estimates put annual central-govt job applications at ~2.2-3 crore (Statesman / Drishti; non-official, conflicting). CMIE data cited by Business Today: ~80% of Indian job aspirants still pursue government jobs.
2. **Money is in prep, not alerts.** Every scaled player (Testbook, Adda247, PW, Oliveboard, Unacademy) monetizes test series / courses and uses free job alerts, exam calendars and notification blogs as top-of-funnel acquisition. Alerts are a loss-leader.
3. **Even prep is a hard business.** Adda247 FY25 revenue flat at ~INR 217 Cr with ~INR 103.6 Cr loss; Testbook FY24 INR 136.7 Cr revenue with INR 84.9 Cr loss. Category leaders price aggressively low (Testbook Pass ~INR 700/yr; PW batches INR 399-1,999).
4. **Government is entering the "official app" layer.** SSC's mySSC app (June 2025) handles OTR, applications, Aadhaar/face verification; UMANG has an exam calendar module. This erodes the "single source of truth" value an alert app could claim, at least for SSC.
5. **India app monetization is improving but from a very low base.** Record ~$345M IAP revenue in Q2 2026 (Sensor Tower via TechCrunch), but ~$0.03 revenue per download; RevenueCat-cited India/SEA download-to-paid medians of 0.7-1.4% vs 2.6-2.8% North America.

## 2. Established Trends

### A. Full-stack govt-exam test-prep apps
- **Platform:** mobile-first (Android), cross-platform web.
- **Type:** established category / freemium-to-pass monetization.
- **Description:** Mock tests, PYQs, live classes, current affairs, plus free job alerts / exam notifications. Testbook, Adda247, Oliveboard, PW, Unacademy, Prepp, Exampur, Rojgar with Ankit app.
- **Metrics:** Testbook 50M+ installs, ~879K ratings at 4.34★ (Quash release-health), claims 5+ crore registered users (marketing claim). Adda247 developer account 10M+ installs; company claims 40-50M monthly users and ~2M paid (claims, unaudited). Adda247 FY25 operating revenue ~INR 217 Cr (flat YoY), loss ~INR 103.6 Cr. Testbook FY24 revenue INR 136.7 Cr (+140%), loss INR 84.9 Cr (now consolidated in Classplus).
- **Why it persists:** structural — exam volume, multi-year prep cycles (3-6 years per Drishti), cheap Android data.

### B. "Sarkari result" aggregator websites + their thin apps
- **Platform:** web-native, with many low-quality Android wrappers.
- **Description:** sarkariresult.com and dozens of clones (sarkariexam.com, freejobalert.com, rojgarresult.com, sarkariresult.com.cm/.im...), ad-monetized listing pages for notifications, admit cards, answer keys, results.
- **Metrics:** FreeJobAlert Android app ~2.4-2.5M installs (AppGoblin estimate, ~5.2K ratings); many "Sarkari Result" named apps with 0.5K-113K installs (AppGoblin). Web traffic: see web-search file (keyword "sarkari result" 79-124M monthly searches per third-party tools).
- **Monetization:** display ads (AdSense); CPC for the head keyword ~$0.01 (Semrush) — advertisers place almost no commercial value on this intent.

### C. Telegram / WhatsApp channel alerts and YouTube "job news"
- **Platform:** messaging + video.
- **Description:** Free alert channels (sarkarijobs.com runs several Telegram channels and pushes via email/push/WhatsApp/Telegram), coaching-institute WhatsApp groups, YouTube channels doing daily "vacancy news". Rojgar with Ankit ~20M YouTube subscribers (Social Blade, Oct 2026), Recruitment Hub ~1.1M (Qoruz, Jul 2026), Rojgar Samachar India ~573K.
- **Why it matters:** This is the default behavior of the target user — zero cost, already installed, socially embedded.

## 3. Emerging / Rising Trends

### A. Official government apps absorbing the workflow
- **Platform:** mobile (Android-only for mySSC, Android 11+).
- **Type:** platform behavior / big-player entry.
- **Description:** mySSC: from June 2025 full SSC application can be completed in-app with Aadhaar OTP login and face recognition; UMANG exam-calendar module.
- **Velocity:** moderate. Threat to any "SSC-only" alert value prop; does not (yet) cover RRB/IBPS/state PSCs uniformly.

### B. Eligibility-matching tools
- **Platform:** web + small apps.
- **Description:** TrueJobs eligibility checker (enter DOB, qualification, category → matched active exams), Sarkari Scan (Android; "analyzes your education, age, category, and experience"), India Govt Job Finder (iOS; qualification/category filters), Peerlist "Sarkarifilled" side project.
- **Velocity:** slow/moderate; small install bases, but it proves the "personalized eligibility" feature already exists and is free. **The core differentiator of the proposed idea is not novel.**

### C. Indian consumers paying more for apps
- **Metrics:** India app market ~$345M IAP revenue in Q2 2026 (record, reported ~+35% YoY; non-gaming >$200M, +44% YoY) — but driven by Google One, YouTube, ChatGPT, video entertainment, not utilities. Revenue per download ~$0.03.
- **Velocity:** moderate. Rising tide, but not lifting "alert utility" apps.

## 4. Key Categories & Signals by Cluster

| Cluster | Examples | Monetization | Scale signal |
|---|---|---|---|
| Full-stack prep | Testbook, Adda247, PW, Oliveboard, Unacademy | Pass/batches INR 399-2,000/yr | 10M-50M+ installs |
| Alert aggregators (web) | sarkariresult.com, freejobalert.com, sarkariexam.com | Display ads | Head keyword 79-124M searches/mo |
| Alert aggregators (apps) | FreeJobAlert, dozens of "Sarkari Result" clones | Ads | ~1K-2.5M installs |
| Messaging/video | Telegram channels, WhatsApp channels, YouTube (Rojgar with Ankit 20M subs) | Funnel to paid batches | 20M-sub creators |
| Official | mySSC, UMANG, ssc.gov.in, rrbapply.gov.in | n/a | Mandatory for applying |
| Eligibility matchers | TrueJobs, Sarkari Scan, India Govt Job Finder | Ads / free | Small |

## 5. Strategic Insights (Product & Marketing Takeaways)

- Alerts alone are a **feature, not a product**, in this market: every incumbent ships them free as acquisition bait for paid prep.
- The only demonstrated non-prep WTP signal found is **transactional form-filling help**: cyber cafes / CSCs charge ~INR 30-150 per application form. That is a "done-for-me" job, not a "notify-me" job.
- Distribution in this audience flows through **YouTube creators and Telegram/WhatsApp**, not app-store browsing; incumbents already own those channels.

## Additional Analysis

- **Pattern recognition:** consolidation (Testbook into Classplus), flattening revenue at Adda247, and price compression (Testbook merged Pass/Pro/Pro Max/Elite into one ~INR 700/yr pass) indicate a mature, price-compressed category rather than an expanding one.
- **Retention formats that work here:** daily current-affairs quizzes, streaks on mock tests, live classes with batch deadlines — all prep-side. Alerts produce sparse, event-driven engagement.
- **Overhyped vs real:** "100M+ monthly searches" looks like a giant opportunity, but CPC ~$0.01 and incumbents' losses show this intent is high-volume / near-zero-value. Looks big, monetizes tiny.
- **Indie opportunity:** narrow vernacular/state-level segments (e.g., a single state's police/teacher/patwari recruitment in the local language) where national players' coverage is thinner — but these are smaller and still alert-commodity.

## 6. Financial Opportunities

1. **Test-prep passes** — users pay INR 399-2,000 per year/batch (Testbook, PW). Saturation: very high, heavily funded. Market: Adda247 + Testbook alone ~INR 350+ Cr revenue, both loss-making. Not an indie wedge.
2. **Form-filling / application assistance** — users pay INR 30-150 per form at cyber cafes/CSCs (commercial pages, NIOS CSC rate table ~INR 33). Bottom-up (estimate): even 1% of ~2.3 crore RRB applicants × INR 50 = ~INR 1.15 Cr per mega-cycle. Saturation: offline-fragmented; online "assisted apply" largely unaddressed. Why now: mySSC/OTR digitization raises friction (Aadhaar, face verification, photo/signature specs). **Highest-signal monetizable adjacency found** — but regulatory/trust-sensitive.
3. **B2B alert/eligibility data feed** for coaching institutes / Telegram admins — speculative, no evidence found.

## 7. Niche Risks

| Risk | Signal | Severity | Mitigation |
|---|---|---|---|
| Market saturation | Testbook 50M+ installs; Adda247 10M+; dozens of alert clones; eligibility matchers already exist | High | Hyper-niche (state + vernacular) |
| Race to zero | Alerts free everywhere; head keyword CPC ~$0.01; Testbook pass compressed to ~INR 700/yr | High | Monetize a different job (form help, prep accountability) |
| Big player / govt entry | mySSC (June 2025) in-app applications; UMANG exam calendar | Medium | Cover multi-board + state exams official apps don't unify |
| Retention cliff | Alerts are event-driven; relevance windows per exam are a few days per month | High | Pair with daily prep loop (but that's where incumbents are strongest) |
| Platform dependency | Push delivery on Indian OEM Android (Xiaomi/Vivo/Oppo battery optimizers) is unreliable — a missed alert breaks the core promise (developer-community knowledge; not quantified here) | Medium | WhatsApp/SMS fallback (costs money) |
| Regulatory / trust | Govt warnings about fake recruitment notices/websites; many clones disclaim "not a government app" | Medium | Link every alert to official source PDF |
| AI disruption | Google AI Overviews / chat assistants answer "SSC CGL last date" directly | Medium | Push (proactive) beats pull (search) |

## Sources
- [Adda247 — Over 1.21 crore candidates applied for RRB NTPC 2025](https://www.adda247.com/exams/railway/over-1-21-crore-candidates-applied-for-railway-ntpc-2025-exam)
- [Careerpower — 12167679 candidates applied for RRB NTPC 2025](https://www.careerpower.in/blog/12167679-candidates-applied-for-rrb-ntpc-2025-exam)
- [Free Press Journal — RRB Group D 2025: over 1.08 crore applications](https://www.freepressjournal.in/education/rrb-group-d-recruitment-2025-over-108-crore-applications-received-mumbai-zone-records-highest-numbers)
- [Careerpower — 28 lakh candidates applied for SSC CGL 2025](https://www.careerpower.in/blog/28-lakhs-candidates-applied-for-ssc-cgl-2025-exam)
- [Careers360 — SSC CGL 2025 tier-1, 13.5 lakh appear](https://news.careers360.com/ssc-cgl-2025-tier-1-concludes-135-lakh-candidates-re-exam-scheduled-for-mumbai-centre-on-october-14)
- [Indian Masterminds — UPSC CSE 2025 prelims result](https://indianmasterminds.com/news/upsc-cse-2025-prelims-result-declared-14161-qualify-for-mains-application-window-opens-june-16-check-all-details-here-122377/)
- [Business Today — Govt jobs preferred despite private sector growth (CMIE 80%)](https://www.businesstoday.in/amp/personal-finance/news/story/government-jobs-preferred-despite-private-sector-growth-india-481617-2025-06-24)
- [Drishti IAS — Youth unemployment and the quest for quality jobs](https://www.drishtiias.com/daily-updates/daily-news-analysis/youth-unemployment-and-the-quest-for-quality-jobs/print_manually)
- [AppBrain — Testbook developer listing](https://www.appbrain.com/app/indian-army-gd-preparation-app/com.testbook.tbapp.indianarmygd)
- [Quash — Testbook release health](https://quashbugs.com/release-health/testbook)
- [Testbook — Pass page](https://testbook.com/pass?tab=passPro)
- [Testbook App Store listing](https://apps.apple.com/in/app/testbook/id1666802218)
- [Inc42 — Testbook financials](https://inc42.com/company/testbook/financials/)
- [Inc42 — Adda247 FY25 loss rises 2.5%, revenue flat](https://inc42.com/?p=532454)
- [Inc42 — Adda247 revenue jumps 88% in FY24](https://inc42.com/buzz/adda247-revenue-jumps-88-to-inr-243-39-cr-in-fy24/amp)
- [AppBrain — Adda247 developer](https://www.appbrain.com/dev/Adda247/)
- [YourStory — Adda247 bets on skilling](https://ts.yourstory.com/2024/09/google-backed-edtech-adda247-bets-skilling-amid-rising-demand)
- [AppGoblin — FreeJobAlert.com app](https://appgoblin.info/apps/com.freejobalert)
- [AppGoblin — Sarkari Result App](https://appgoblin.info/apps/com.sarkariresult.com.app)
- [AppGoblin — Sarkari Scan](https://appgoblin.info/apps/com.sarkariscan)
- [Apple App Store — India Govt Job Finder](https://apps.apple.com/us/app/-/id6743082222)
- [TrueJobs — Government Exam Eligibility Checker](https://truejobs.co.in/govt-exam-eligibility-checker)
- [Business Standard — SSC launches mySSC app](https://www.business-standard.com/india-news/ssc-launches-myssc-mobile-app-for-government-job-applications-125060501384_1.html)
- [Free Press Journal — mySSC with Aadhaar and face verification](https://www.freepressjournal.in/education/ssc-launches-my-ssc-app-with-aadhaar-face-verification-for-secure-exams)
- [Aglasem — SSC on UMANG app](https://news.aglasem.com/ssc-launches-umang-app-for-recruitment-result-exam-dates-vacancies/)
- [TechCrunch — India is starting to pay for apps (Jul 2026)](https://techcrunch.com/2026/07/31/india-is-starting-to-pay-for-apps-not-just-download-them/)
- [TechRepublic — India's app boom has a revenue problem](https://www.techrepublic.com/article/news-apac-india-app-boom-revenue-problem/)
- [RevenueCat — State of Subscription Apps 2025](https://revenuecat.com/state-of-subscription-apps-2025)
- [The Social Outline — download-to-paid benchmarks](https://thesocialoutline.com/blog/download-to-paid-conversion-rate-benchmarks)
- [Physics Wallah — SSC CHSL 2026 Kamyabi batch](https://www.pw.live/ssc/exams/pw-kamyabi-ssc-chsl-2026-batch-launch)
- [Physics Wallah — SSC MTS 2025 MCQ practice batch](https://www.pw.live/ssc/exams/pw-ssc-mts-havaldar-2025-mcq-practice-batch-test-series)
- [Sarkarijobs.com — Telegram channels](https://sarkarijobs.com/join-telegram-channel-for-sarkari-jobs-sarkari-result-and-naukri-alerts)
- [Sarkarijobs.com — Sarkari Naukri app](https://www.sarkarijobs.com/sarkari-naukri-app/)
- [Social Blade — Rojgar with Ankit](https://socialblade.com/youtube/handle/rojgarwithankit/achievements)
- [Qoruz — Recruitment Hub YouTube](https://qoruz.com/Recruitment_Hub/youtube)
- [NIOS — CSC rates for online services](https://rcchandigarh.nios.ac.in/page/faq-csc-table.html)
- [Jadu Desk — online form filling service](https://jadudesk.pages.dev/services/online-form-filling/)
- [Outlook Money — Govt cautions against fake recruitment notices](https://www.outlookmoney.com/news/govt-cautions-job-seekers-against-fake-recruitment-notices-and-websites)
