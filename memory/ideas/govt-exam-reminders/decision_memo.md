---
idea_slug: "govt-exam-reminders"
verdict: "drop"
final_score: 13
score_confidence: "medium"
created_at: "2026-11-19"
---

# Decision Memo: Personalized Govt-Exam Notifications & Reminders (India, Android)

## Verdict: DROP

**Score: 13/100** | Confidence: medium

This score is based on incomplete data. Missing: founder profile (`user_profile.md`, so founder fit was set to a neutral 50 and the budget was assumed to be bootstrap) and market sizing (`market_size.json`). Run these analyses before making a build/no-build decision. Neither gap can lift the idea out of drop: without the missing-data discount it scores 18.7, and with both floor penalties removed it would still only reach "pivot."

---

## Why This Score

The audience is huge and the anxiety is real, but alerts are a free commodity. Testbook, Adda247, Sarkari Result sites, Telegram channels, YouTube and the official mySSC app all give them away as bait for paid test prep. Personalization does not change that, because free eligibility filters already exist. Each user would be worth about ₹4.5 over their lifetime, while an install costs ₹20–68. Two dimensions score below the 25 floor, and that penalty sinks the total.

## Top 3 Strengths

1. **Demand** (52/100): About 1.22 crore people applied for RRB NTPC 2025 and about 1.08 crore for RRB Group D 2025. "Sarkari result" gets an estimated 79–124M searches a month.
2. **Founder-Market Fit** (50/100, neutral default): An Android-first push app is well within a builder's skills. There is no evidence of any domain or distribution edge.
3. **Retention** (42/100): Exam events are high-stakes, repeat over 3–6 years of preparation, and arrive as triggers from outside the app. People keep alert apps installed even when they rarely open them (estimated D30 of 8% active users).

## Top 3 Risks

1. **Monetization** (15/100): Alerts must be free to compete. The keyword "sarkari result" sells for about $0.01 per click (CPC), so advertisers barely value this audience. India/SEA download-to-paid conversion is 0.7–1.4%. Lifetime revenue is about ₹4.5 per user against a ₹20–68 install cost, so every growth rupee is lost. Even the scaled players lose money on prep: Adda247 lost about ₹104 Cr in FY25 and Testbook about ₹85 Cr in FY24.
2. **Competition** (22/100): The saturation score is the maximum 15/15. Testbook has 50M+ installs and about 879K ratings. Adda247 is free. TrueJobs and Sarkari Scan already offer eligibility filtering, and mySSC handles full SSC applications in-app since June 2025. If personalization works, any incumbent ships it in a sprint, to users who already gave them their target exam.
3. **Distribution** (30/100): Word of mouth is weak (each user brings about 0.12 new users), creator fit is low because the big YouTubers run their own apps, and there is no founder audience. The only free paths, long-tail ASO and Telegram seeding, return about 1.1× their cost at best. TikTok, the usual indie channel, is banned in India.

## Riskiest Assumption

> "Aspirants will pay ₹199/yr for verified, quiet, eligibility-filtered alerts, or ₹39 per application for 'application-ready' help, instead of using free Telegram, Testbook and Sarkari Result."

**Test it before building anything.** Over 14 days, spend about ₹5–8K on a Hindi landing page with a working eligibility checker, followed by two real UPI payment offers. Seed it in 20–30 aspirant Telegram and WhatsApp groups plus up to ₹4K of Meta ads. **Pass** requires all three: at least 3% of 300+ checker completions click a paid offer, at least 10 actual payments, and at least 25% opt in to free WhatsApp alerts.

## Pre-mortem: If This Fails in 12 Months

1. **No one pays.** The app reaches 20K installs, Pro converts at 0.5%, and ads earn under ₹15K a month. Lifetime revenue of ₹4.5 per user cannot fund even organic growth.
2. **Users go back to Telegram.** Alerts arrive late or get suppressed by phone battery savers. One missed SSC or RRB deadline sends users back to Telegram plus Testbook, and they uninstall.
3. **An incumbent copies it.** Testbook or Adda247 adds an "eligible exams for you" feed for its tens of millions of users, mySSC adds push alerts, and ASO rankings collapse.

---

## What To Do Now

Archive this idea as stated. Do not write production code for an alerts-and-reminders app. Lesson for future ideas: **massive search volume with near-zero CPC means the intent is informational, not monetizable.** Before choosing a feature, look for the step users already pay for. Here that step is filling the form, which costs ₹30–150 at a cyber cafe, not the notification.

**Kill criteria:** If you run the RAT anyway, fewer than 10 payments, or under 1% clicking a paid offer from 300+ checker completions, means stop. Do not build.

## If That Doesn't Work

Run the `pivot-optimization` workflow on one thesis: an app that makes applications ready (alert → eligibility → documents → submitted form) for a single Hindi-belt state's police, teacher and patwari recruitments. The RAT's ₹39 offer doubles as that pivot's first test.
