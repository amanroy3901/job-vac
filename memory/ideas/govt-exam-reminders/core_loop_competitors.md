# Who does the exact core loop? (checked 2026-11-19)

Core loop being checked:
1. Rich profile (exam levels, qualification, age, category, state/region, other params)
2. Continuous matching of new notifications against the profile; only true matches surface
3. On match: a reminder that repeats until the user applies or marks "not interested"
4. The reminder carries everything needed (dates, fee, documents, photo/signature spec, link)
Goal: kill notification blindness from broadcast alerts. No upsell; pure personalization and less friction.

| Product | 1 Profile | 2 Matching | 3 Persistent until dismissed | 4 Complete reminder | Notes / source |
|---|---|---|---|---|---|
| Sarkari Scan (Android) | Education, age, category, experience | Yes, "personalized recommendations + eligibility status" | Not found | Not found | Closest on matching. Small, unverified claims. https://appgoblin.info/apps/com.sarkariscan |
| GovFitAI (web) | Education, age, qualifications | Claims AI eligibility matching | Not found | Not found | Self-reported 10K users; unverified. https://sarkariaiapp.github.io/about.html |
| India Govt Job Finder / Naukri Alert (iOS) | Location, qualification, category filters | Filter-based | Deadline reminders for *saved* jobs | Partial (age limit in details) | https://apps.apple.com/us/app/-/id6743082222 , https://apps.apple.com/us/app/-/id6748553839 |
| EasyShiksha Govt Job Alerts | Profile customization | Filter-based (state/sector/qualification) | Deadline push mentioned | Eligibility + links | https://apps.apple.com/in/app/govt-job-alerts-easyshiksha/id6751952231 |
| FreeJobAlert app (~2.5M installs) | State, keyword, qualification | Filter-based (no age/category) | Reminders before last date (search summary; not verified on listing) | Partial | https://play.google.com/store/apps/details?id=com.freejobalerts.app |
| Sarkari Job Alert (NaukriLatest) | Qualification/profession | Filter | "Last date reminder", repeated alerts until last date (per listing) | Partial | https://play.google.com/store/apps/details?id=com.naukrilatest.sarkarijobalert |
| TrueJobs (web) | Eligibility checker | On-demand check | No | Checklists (generic) | https://truejobs.co.in/tools |
| Testbook / Adda247 | Target exam at onboarding | Exam/state lists, not eligibility | No (reminders only for live classes) | Rich notification pages | https://testbook.com/free-job-alerts , https://www.adda247.com/jobs/free-job-alert/ |
| Telegram/WhatsApp channels | None | None (broadcast) | No | Post has eligibility + last date | https://t.me/s/FreeJobAlertOfficially |

## Conclusion
- Every piece exists somewhere; nobody found combines all four, with true eligibility matching (age-on-cutoff incl. category relaxation, domicile) + persistent reminders until apply/dismiss + a complete "what you need" card.
- Closest: Sarkari Scan / GovFitAI (matching) and NaukriLatest / FreeJobAlert (deadline reminders).
- The combination is a real, uncrowded product position. It does not by itself fix monetization or distribution: the pieces are free, so the moat is execution (data accuracy per notification, matching correctness, Hindi UX) and distribution.
- Unverified: Play Store pages couldn't be read directly from this environment; feature claims come from listings/aggregators.
