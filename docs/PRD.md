# PRD: Sarkari Match — Alert-Only App

Source of truth: https://claude.ai/artifact/4CbSA4F9WK5zc94no2kUkb (this file is a repo copy, 2026-11-19).

## Founder decisions (2026-11-19) — these override anything below

1. **Languages:** design for all major Indian languages (Hindi, English, Bengali, Marathi, Telugu, Tamil, Gujarati, Kannada, Odia, Malayalam, Punjabi, Assamese, Urdu incl. RTL). Launch with Hindi + English only.
2. **Login:** free only. Google Sign-In (Credential Manager / Firebase Auth Google provider) or continue as guest. No phone number, no OTP, no paid auth.
3. **Domain:** sarkarimatch.in is a placeholder; availability not yet verified (lookup blocked from the build environment).
4. **Payments:** Google Play Billing only (Play accepts UPI, cards, Play balance). No external UPI links or payment gateways.
5. **Data & consent:** collect only what matching needs and nothing requiring heavy/special consent: no phone, no Aadhaar, no documents. Show our own clear privacy notice and notification-permission explanation. Minimise what leaves the device.
6. **Ads:** light native ads in the free tier from day 1; Pro removes them.
7. **No WhatsApp messaging at all** — no WhatsApp alerts, OTP or templates. Delivery = FCM push + on-device backup reminders. Pro value = lifecycle alerts after applying, no ads, clash/certificate/family alerts (family = push to a linked family member's own app).
8. **Validation test:** run as a Play closed-testing build with Play Billing, not over WhatsApp.

## Summary

Sarkari Match is a Hindi-first Android app that alerts a government-job aspirant only about jobs they are actually eligible for, and keeps reminding them until they apply or say "not interested". It is alert-only: no courses, test series or prep content.

**Positioning line:** "Sirf aapki naukri. Koi miss nahi." (Only your jobs. Nothing missed.)

- **In scope:** eligibility profile, matching new notifications against it, persistent reminders, a complete "what you need" card in each reminder, and alerts for every later stage of an application (admit card, exam date, answer key, result, document verification).
- **Out of scope:** test prep, form filling or submission on official portals, document storage on our servers, iOS, private-sector jobs.
- **Business model:** a genuinely useful free tier, and a Pro tier at about ₹20/month (sold as ₹199/year or ₹49 per season) that makes sure critical alerts are never missed.
- **Status:** draft. Validation scored the idea 17/100 as a subscription app; the first release is gated on a 14-day WhatsApp payment test.

## Problem and evidence

Aspirants get too many alerts about jobs that aren't theirs, so they stop reading alerts and miss the few that matter. After applying they miss admit cards, reschedules and document-verification slots that appear on portals they don't check.

- ~1.22 crore applicants for RRB NTPC 2025, ~1.08 crore for RRB Group D, ~28 lakh for SSC CGL.
- Each aspirant follows 5–10 Telegram/WhatsApp channels and apps that send the same alerts to everyone; none filter by age, category or domicile.
- Aspirants keep checking SSC, RRB, IBPS and state PSC portals manually.
- SSC DV notices: "no further opportunity" if a slot is missed. RRB NTPC was rescheduled in 2025–26.
- Correction fees ₹200 then ₹500 (SSC); a missed deadline can cost a year.
- Xiaomi, Vivo and Oppo phones often kill background apps, so push may never arrive.

## Target users

Hindi-speaking, phone-only aspirants for central and Hindi-belt state recruitments.

| Persona | Who | Main need | Why they'd pay |
| --- | --- | --- | --- |
| Ravi, full-time aspirant | 23, B.A., OBC, UP. SSC, RRB, UP Police. Redmi phone. | Only jobs he can apply for; never miss a last date | Missing a form or slot costs a year |
| Pooja, working aspirant | 27, graduate, General, Bihar. Studies after work. | No noise; reminders at times she can act | Little time; values reliability |
| Suresh, near the age limit | 31, OBC, Rajasthan. Few attempts left. | Every eligible job before age runs out | Each remaining chance is worth more |
| Parent or sibling | Supports the aspirant | Knows when an important date is coming | Wants to help without checking portals |

## Competition and positioning

No app combines true eligibility matching, reminders that persist until the user acts, and lifecycle alerts after applying. Testbook/Adda247 (alerts as prep funnel), FreeJobAlert app (state/qualification filters), NaukriLatest Sarkari Job Alert (repeated last-date alerts), Sarkari Scan/GovFitAI (eligibility matching), TrueJobs (web tools), Telegram/WhatsApp channels, official apps (mySSC, UMANG). Win on matching accuracy, reliable delivery, Hindi-first design, no upselling.

## Goals and success metrics

Goals: users never miss an eligible job's last date because of us; fewer, more relevant alerts than any channel; a paid tier 3–5% of active users choose. Non-goals: test prep, form filling, private jobs, social feed.

| Metric | Target (first 90 days) |
| --- | --- |
| Profile completion after install | ≥ 60% |
| Matches the user didn't already know about | ≥ 30% |
| Wrong "eligible" verdicts reported | < 1% of matches |
| Alerts acted on (applied or not interested) | ≥ 50% |
| Users who disable notifications | < 15% |
| Active after 30 days | ≥ 15% |
| Paying share of 30-day active users | ≥ 3% |
| Curation time per notification | ≤ 30 min |

## Features (P0 = first release, P1 = within 3 months, P2 = later)

### Free

| Feature | What the user gets | Priority |
| --- | --- | --- |
| Eligibility profile | Qualification, DOB, category, state, domicile, gender, PwD, ex-servicemen, exam levels/types of interest. Under 2 minutes. | P0 |
| True eligibility matching | Age on cutoff date with category relaxation, qualification, domicile, gender, region | P0 |
| "Why you match" | One line per rule: "Age 24 on 01-08-2026 (limit 27, OBC +3)" | P0 |
| Reminders until they act | Repeat on schedule until Applied or Not interested | P0 |
| "What you need" card | Dates, fee for their category, documents, photo/signature specs, official PDF, apply link | P0 |
| Hindi and English | Hindi default | P0 |
| Daily digest | One quiet summary for low-priority matches | P1 |
| Last-date extension alerts | "Deadline extended to 7 Sept" | P1 |
| Age-limit countdown | "You can apply to SSC CGL 2 more times" | P2 |

### Pro (₹20/month; ₹199/year; ₹49/season)

| Feature | What the user gets | Priority |
| --- | --- | --- |
| Lifecycle alerts after applying | City slip, admit card, exam date, reschedule, answer key + objection window, result, cut-off, DV and medical slot | P0 |
| WhatsApp backup | Critical alerts also on WhatsApp when push fails or is unread | P0 |
| Exam clash alerts | "SSC GD and UP Police exams in the same week" | P1 |
| Certificate validity alerts | OBC-NCL/EWS FY or domicile certificate not valid for this notification | P1 |
| Family alerts | Parent/sibling also gets critical alerts | P1 |
| Payment-status watch | Prompt to check status when fee payment may have failed | P2 |
| SMS backup | For users without WhatsApp/data | P2 |
| Early alerts | Expected notifications from the official exam calendar | P2 |

## Key user flow

Profile set up → new notice reviewed → eligible? (no: nothing sent) → match alert with what-you-need card → user taps: Applied (deadline reminders stop; Pro: lifecycle alerts to final result) / Not interested (reminders stop, job hidden) / no reply (reminder repeats, more often near last date). Onboarding under 2 minutes: language, exam types, qualification, DOB, category, state, then guided notification + autostart permission step.

## Pricing

| Plan | Price | Net after Play 15% |
| --- | --- | --- |
| Free | ₹0 | — |
| Season pass | ₹49 / 4 months | ~₹42 |
| Yearly | ₹199 (shown as ₹17/month) | ~₹169 |
| Monthly | ₹20 via UPI AutoPay (only if tests show demand) | ~₹17 |

Paywall moment: right after the user taps Applied ("Track this application to the end?"). Trial: 14 days of Pro from first Applied. WhatsApp ~₹0.12–0.15/msg, SMS ~₹0.10–0.25; cap ~30 WhatsApp msgs/user/season.

Light ads (free tier only, decided 2026-11-19): one small native ad at the bottom of the matches feed and in the daily digest. Never inside a reminder, push/WhatsApp message, the "what you need" card or the "why you match" lines. No pop-ups, video or full-screen ads; no coaching/test-prep ads that look like official notices. Pro removes ads.

## Data and matching engine

1. Collect: watch official sources (SSC, RRB regional sites, IBPS, UPSC, state PSCs/police boards in launch states, Employment News) for notices, corrigenda, date changes.
2. Extract: AI model reads the PDF and fills a rule template per post (age limits + cutoff date, relaxations by category, qualification, domicile, gender, fee by category, dates, documents, photo/signature specs).
3. Review: a person checks every rule against the PDF before publishing (target ≤ 30 min/notification).
4. Match: server checks each profile vs each post → eligible / not eligible / "check yourself".
5. Update: corrigenda and extensions re-run matching and notify affected users.

Safeguards: official PDF link on every match; "check yourself" instead of guessing; one-tap wrong-match report; second reviewer for error-prone posts. Coverage at launch: SSC, RRB, IBPS, UPSC + 1–2 Hindi-belt states, ~20–40 notifications/month.

## Reminder system

| When | Free | Pro adds |
| --- | --- | --- |
| New match | Push now | WhatsApp now |
| Every 3 days while window open | Push | — |
| 3 days before last date | Push | WhatsApp |
| 1 day before | Push, high priority | WhatsApp + family |
| Last day, morning | Push, high priority | WhatsApp + family |
| After Applied | Stops | Lifecycle alerts to final result |

Stop: Applied, Not interested, deadline passed, no longer matches after corrigendum. Snooze: tomorrow / 2 days before last date. Quiet hours 10 pm–7 am except last day. OEM delivery: onboarding guide for autostart + battery optimisation, high-priority FCM, weekly delivery self-check. Max 3 pushes/day; overflow to digest.

## Technical architecture (initial)

Android: Kotlin, Jetpack Compose, Room, WorkManager. Backend: Firebase or small Kotlin/Node server. Ingestion: scheduled scrapers/page watchers. Extraction: AI model + fixed JSON schema + internal review screen. Matching: server job per new/changed post. Delivery: FCM, WhatsApp Business API via provider, SMS later. Payments: Google Play Billing; UPI link for the test.

NFRs: alert < 6 h after review; app < 15 MB; Android 8+, 2 GB RAM, slow networks; minimal personal data (no Aadhaar, no documents), encrypted, deletable (DPDP Act); every alert shows official source + "not an official government app".

## Go-to-market

Aspirant Telegram/WhatsApp groups with a free eligibility check; Hindi Play Store long-tail ASO; shareable "I'm eligible for 7 jobs" card; invite-a-friend (+1 month Pro); family alerts; small creators and coaching centres. Launch in 1–2 Hindi-belt states during a big recruitment window.

## Risks, validation, roadmap

Risks: nobody pays; wrong eligibility verdict; curation too slow for one person; push blocked on Chinese-brand phones; incumbents copy; discovery.

Validation: 14-day WhatsApp test (~₹6–8K). Continue if ≥300 profiles, ≥100 APPLIED, ≥8% of those pay (≥10 payments), <50% stop/block, ≤30 min curation with no wrong verdicts in 20. Stop if <3% pay or <5 payments, >30% stop/block, >60 min/notice.

Roadmap: weeks 1–2 WhatsApp test → weeks 3–10 P0 release → months 3–6 P1 → month 6+ P2.

Open questions: launch state; existing audience/partners; Firebase vs own server.
