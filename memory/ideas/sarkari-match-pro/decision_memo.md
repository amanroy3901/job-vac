---
idea_slug: "sarkari-match-pro"
verdict: "drop"
final_score: 17
score_confidence: "medium"
created_at: "2026-11-19"
parent_idea: "govt-exam-reminders (13/100, drop)"
---

# Decision Memo: Sarkari Match Pro (v2)

## Verdict: DROP (as a subscription app), but run one cheap WTP test merged with Form Pakka before walking away

**Score: 17/100** (range 13–24) | Confidence: medium

This score is based on incomplete data. Missing: `user_profile.md` (founder fit defaulted to 50, minus 2) and `market_size.json`. Together they cost about 29% of the score. With them at neutral values the score would be about 24. Without the monetization floor it would be about 37.

| | v1 alerts | **v2 Match Pro** | Form Pakka pivot |
|---|---|---|---|
| Final | 13 | **17** | 29 (26–33) |
| Undiscounted | 19 | **24** | 41 |
| Floors | monetization + competition | **monetization only** | none |

---

## Why This Score

v2 is a better product than v1. It is the first version where the free feed is clean (matched only, no ads) and the paid tier has a clear moment to sell (after you tap "Mark as applied"). That moves competition off the floor and lifts retention. It does not fix the problem that killed v1: nobody in this category has been seen paying for a utility. Removing ads also cuts base LTV per install from about ₹4.5 to about ₹1.6. It still scores below Form Pakka, because Form Pakka charges for the one step people already pay for (cafés charge ₹30–150 per form).

## Top 3 Strengths

1. **Demand** (53/100): very large audience with real losses at stake. RRB NTPC 2025 got about 1.22 crore applications, and SSC DV notices say "no further opportunity" if you miss your slot.
2. **Retention** (47/100): lifecycle tracking adds about 5–7 events per application, or about 30–60 a year for active applicants. Habit score rises from 2/5 to 3/5, and estimated D30 from 8% to 10%.
3. **Competition** (30/100): no app found combines true eligibility matching, reminders that persist until you apply, and tracking of your own applications. Post-application tracking exists only as a free TrueJobs web tool.

## Top 3 Risks

1. **Monetization** (16/100): base LTV is about ₹1.6 per install (about ₹5.1 optimistic). Telegram seeding costs about ₹4 per install and ASO about ₹10, so no channel reaches 1:1 in the base case. India's download-to-paid rate is about 0.7%, and even Truecaller, a daily-use app, has only about 2M Indian subscribers. If this is ignored, you build for 3 months and earn a few hundred rupees a month.
2. **Data operations** (part of founder fit, 48): eligibility rules come in layers. There are category relaxations, time-limited state exceptions (for example Telangana's 2026 +10 years, which excludes uniformed posts), corrigenda, and cross-references to other notices. One wrong "eligible" verdict destroys the core promise, and covering all of India means hundreds of notices a month. If ignored, the solo dev becomes a full-time data clerk, or accuracy slips and users leave.
3. **Distribution** (32/100): k is about 0.14 and the founder has no audience. Creators are themselves the alert channel, and Testbook and the Sarkari Result clones own SEO and the Play head terms. If ignored, the result is a correct, quiet app that nobody finds.

## Riskiest Assumption

> "Aspirants who have just applied will pay ₹49 a season or ₹199 a year for tracking, a pre-submit check and WhatsApp fallback, instead of relying on free Telegram, YouTube and board SMS."

**Test it before building anything.** Run a 14-day concierge on WhatsApp for central exams plus one Hindi-belt state with application windows open. Users get a free web eligibility checker, then matched alerts with the full "what you need" card. Reminders repeat until the user replies APPLIED or NO. When they reply APPLIED, offer Pakka Pro at ₹49 a season (includes one pre-submit check) or ₹199 a year, through a real UPI link. Traffic comes from 20–30 aspirant groups, 2 paid channel posts and Meta ads of ₹3K or less. Total cost is about ₹6–8K. **Pass (all five):** at least 300 profiles; at least 100 APPLIED replies; at least 8% of APPLIED users pay, with at least 10 payments; under 50% STOP/block; and median curation of 30 minutes or less per notification, with zero wrong verdicts in a 20-notice audit.

## Pre-mortem: If This Fails in 12 Months

1. Pro conversion settles at about 1% of installs. At about 3,000 installs a month from seeding plus ASO, that is about ₹4K a month gross, which is less than the data-curation hours cost.
2. A corrigendum (changed age relaxation or extended date) is missed. Users get wrong "not eligible" verdicts, posts in Telegram groups call the app unreliable, and D30 falls below 5%.
3. Persistent reminders are read as nagging. Users revoke notification permission, and free users on Xiaomi, Oppo and Vivo never get pushes anyway. The core free promise of "never miss" quietly fails.

---

## What To Do Now

Run the RAT above this month, **merged with Form Pakka**: the season pass includes the pre-submit Form Check, and Form Check is also sold alone for ₹49. That tests subscription WTP and per-check WTP side by side for the same cost. Pick the state with the biggest window opening in the next 3 weeks (check the UPSSSC, UPPRPB and RSSB calendars). Write no Kotlin until the test passes.

**Kill criteria:** stop if fewer than 3% of APPLIED users pay or fewer than 5 payments arrive, if more than 30% STOP/block, or if curation takes more than 60 minutes per notification. If only the stand-alone checks sell, continue as Form Pakka. If nothing sells, drop the domain.

## If That Doesn't Work

Archive the govt-job domain. Lesson for future ideas: in India, an event-driven utility needs a transactional paid unit tied to a moment people already pay for. A subscription will not cover it.

_v2 — re-scored after refining the alert app into matched alerts plus the ₹20/month lifecycle Pro tier (parent: govt-exam-reminders, 13/100)._
