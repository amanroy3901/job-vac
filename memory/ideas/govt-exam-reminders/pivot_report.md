---
idea_slug: govt-exam-reminders
original_score: 13
original_verdict: drop
recommended_pivot: pivot-1
created_at: 2026-11-19
---

# Pivot Report: Govt-Exam Notifications & Reminders

## Why the Original Idea Scored 13/100

The product monetizes the one step aspirants never pay for. Monetization scored 15/100 because alerts are free everywhere: "sarkari result" CPC is ~$0.01 and LTV is ~₹4.5 (cac.json). Competition scored 22/100 because eligibility-filtered alerts already exist for free, from TrueJobs, Sarkari Scan, Testbook and Adda247 (competitors.json). Both are below the 25 floor, which cuts the score by 47%.

---

## What Can Be Fixed vs. What Can't

**Addressable weaknesses** (pivot targets):
- Monetization: aspirants *do* pay to get applications done (₹100–150 at cafés, ₹25–33 at CSCs) and to fix them (SSC correction fee ₹200/₹500). Move the paid unit there (gap_analysis.md §3).
- Competition: incumbents leave post-alert application work alone because it doesn't sell prep (gap_analysis.md §5, gaps 1–4).
- Distribution (situational): a higher LTV makes cheap Telegram paid posts usable. State-level focus sharpens targeting.

**Structural weaknesses** (cannot be pivoted away):
- Any variant where the alert is the paid product.
- Liability: touching the application means owning part of the blame for rejections.

**Framework caveat:** no original dimension is ≥ 60, so no pivot can "preserve a strong dimension". Every option keeps demand (52) and the Android/push base instead.

---

## Pivot Options

### Option 1: Feature + monetization: "Form Pakka", application rejection-proofing for one state · Projected 26–33/100 (36–46 undiscounted) · Effort: medium

**The change:** A Hindi-first Android app for one state's big recruitments (UP: UPSSSC PET/OTR and UP Police; or Rajasthan: RSSB Patwari/VDO). It includes a document vault, photo and signature formatted to *that* notification's spec, a pre-submit audit (DOB as on cutoff, OBC-NCL/EWS certificate FY and format, domicile), and a ledger with fee, last-date, correction-window, admit-card, answer-key, result and DV reminders. Paid: a ₹49 human "Form Check" of the preview PDF (₹99 for a 3-pack).

**Why this works:** Rejections run to lakhs per cycle: 1.27 lakh RRB photo rejections in 2018, 4.86 lakh in Gangman 2021. Courts put liability on the candidate even for café errors (Delhi HC 2025). A ₹49 check is cheaper than a single ₹200 correction. No dedicated personal application tracker exists, and guides tell aspirants to keep a spreadsheet.

**What stays the same:** Audience, Android-first, push and reminder engine, eligibility logic.

| Dimension | Current | Projected |
|---|---|---|
| Monetization | 15 | 25–40 |
| Competition | 22 | 28–38 |
| Distribution | 30 | 30–38 |
| Retention | 42 | 38–48 |
| Demand | 52 | 50–58 |
| Founder fit | 50 | 45–50 |

**Trade-offs:** Takes on liability. Revenue is lumpy around notifications. Contested by TrueJobs tools, SarkariFilled and free resizers, so photo resizing is only a hook.

**Effort:** 3–4 days for a concierge test; 4–6 weeks for the Android MVP; ~4–8 hrs of rule curation per notification.

---

### Option 2: Monetization + audience: "Operator kit" for cyber cafés and CSCs · Projected 25–36/100 (35–50 undiscounted) · Effort: high

**The change:** Sell a per-customer document vault, batch photo/signature formatting, a pre-submit checker and WhatsApp customer reminders to the operators who already earn ₹25–150 per form, at ₹199–499 a month.

**Why this works:** Ten to thirty times the B2C willingness to pay, daily in-season use, and each operator reaches hundreds of aspirants. No operator tool was found. **But** there is no primary evidence that operators feel the pain or would pay, and operators work on desktops, which is a hidden platform change.

| Dimension | Current | Projected |
|---|---|---|
| Monetization | 15 | 30–52 |
| Competition | 22 | 35–50 |
| Retention | 42 | 48–60 |
| Demand | 52 | 38–50 |
| Distribution | 30 | 25–40 |

**Trade-offs:** Slow manual sales, platform mismatch, DPDP compliance weight from storing many customers' documents.

---

### Option 3: Distribution: Telegram/WhatsApp channel + affiliate · Projected 9–26/100 · Effort: low
Keeps both floor penalties. The channel space is the most saturated part of the market, and affiliate yield is under ₹1 per subscriber per cycle (est.). **Not recommended.**

*(Also scored: post-exam tracker 12–29, where the audience is too small and it works better as Option 1's retention tail; and study accountability 10–31, which is served free and where people pay for physical library seats, not apps. Details in pivot_options.json.)*

---

## Recommendation

**Go with Option 1, "Form Pakka", and run Option 2 as a zero-cost discovery probe alongside it.** Option 1 has the best impact per unit of effort (+16 points at medium effort), rests on the strongest evidence in gap_analysis.md (lakh-scale rejections and existing per-form spending), and can be tested as a WhatsApp concierge in a few days. Option 2 has a slightly higher ceiling but would add a platform change on top of unverified demand.

**If this pivot also scores below 50:** that's the expected outcome on paper (projected 29, or 41 undiscounted). Only the behavioral test can move monetization and distribution enough. If the test hits the kill criteria, drop the domain.

## What to Do First
1. Write `memory/user_profile.md` (10 minutes). It removes part of the missing-input discount and tests whether the founder has Hindi and domain ability.
2. Pick the state with an application window open in the next 14 days (check UPSSSC, UPPRPB and RSSB calendars), then run the 2-week Form Check concierge test from pivot_memo.md (≤₹10K).
3. In parallel, phone or WhatsApp 15 cyber cafés and CSCs in one district from Google Maps listings for the operator probe.
