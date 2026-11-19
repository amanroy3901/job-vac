---
idea_slug: govt-exam-reminders
artifact: pivot_memo
created_at: 2026-11-19
original_score: 13
recommended_pivot: pivot-1 (primary) + pivot-2 (discovery probe only)
projected_score: 29 (range 26-33; 41 undiscounted, range 36-46)
score_confidence: low
---

# Pivot Memo: From "notify me" to "make my application rejection-proof"

## Bottom line

**No pivot clears the bar on paper.** The best option projects to **~29/100** (26–33) against the original 13. That's still in the drop band, because the same missing-input discount applies (no founder profile, no market sizing). Without the discount it's **~41** (36–46), the "pivot" band. Nothing reaches 50. Every number is an estimate.

**Still, one pivot is worth a single cheap test.** It moves the paid unit to the one step aspirants already pay for (getting the form done right), and it can be falsified in 2 weeks for under ₹10K. Only a behavioral result can lift monetization and distribution enough to matter. More analysis can't.

## Projected scores

| Pivot | What changes | Fixes | Projected (discounted) | Undiscounted | Effort (solo Android) | Key risk | Verdict |
|---|---|---|---|---|---|---|---|
| **1. Form Pakka: rejection-proofing + application ledger, one Hindi-belt state, ₹49 Form Check** | Feature + paid unit | Monetization 15→25–40; competition 22→28–38 | **26–33** | 36–46 | Medium (4–6 wks MVP; concierge in days) | B2C users may want the *service* (café does it) rather than a *check*; liability | **Recommended** |
| 2. Operator kit for cafés and CSCs (₹199–499/mo) | Customer + revenue model | Monetization →30–52; competition →35–50; retention ↑ | 25–36 | 35–50 | High (6–10 wks, likely desktop web too) | No evidence operators will pay; platform mismatch | Probe only |
| 3. Telegram/WhatsApp channel + affiliate | Distribution | Distribution →35–45 | 9–26 | 12–36 | Low | Keeps both floor penalties; most saturated surface | No |
| 4. Post-exam tracker (DV, medical, waiting list, joining) | Lifecycle phase | Competition →35–45 | 12–29 | 17–41 | Medium | Audience ≈ 1–3× vacancies; no channel | Fold into #1 later |
| 5. Study accountability pods | Problem | Retention →45–58 | 10–31 | 15–43 | Medium | Free substitutes; people pay for physical seats, not apps | No |

## Why Pivot 1

1. **The pain is documented, large and costly.** RRB rejected about 1.27 lakh forms for faulty photos (2018) and 4.86 lakh for photo or signature (Gangman 2021). SSC charges ₹200 and then ₹500 for corrections, and allows nothing after the window closes. Courts uphold rejections even when the cyber café made the error. Category certificates get rejected for the wrong financial year or format. (Sources in gap_analysis.md §4.)
2. **People already pay at this step:** ₹100–150 per form at cafés or WhatsApp services, ₹25–33 at CSCs. Alerts get ₹0.
3. **Incumbents don't do it, for structural reasons.** Edtechs earn from prep passes and avoid liability. Aggregators earn from ads. Official apps are single-board.
4. **Honest counterweights.** Photo resizing is a free commodity. TrueJobs already has web checklist and tracker tools. SarkariFilled auto-fills forms. The moat has to be **per-notification rule checks against the user's own documents, in Hindi, on mobile, plus a ledger that follows each application through DV**, not resizing.

## MVP scope (v1)

**Build (Android, Hindi-first, one state, 2–3 live recruitments):**
- Profile: DOB, category, domicile, qualification.
- **Document vault on-device** (photo, signature, 10th/12th, caste/NCL/EWS/domicile certificates with issue date). Keeping it on-device avoids DPDP-Act server storage in v1.
- **Spec-perfect export** for each curated notification: KB and pixel size, JPG, photo date stamp where required.
- **Pre-submit audit checklist**: DOB as on cutoff, certificate FY, format and validity window, domicile requirement, qualification cut-off date, fee category. Every rule links to the official PDF page.
- **Application ledger and reminders**: registration no., fee paid, last date, correction window, admit card, exam city, answer-key objection, result, DV slot.
- **Paid ₹49 "Form Check"**: upload the preview PDF or screenshots, a human reviews them against the checklist within 6 hours, and you get a refund if a listed rule is missed. ₹99 for a 3-pack. UPI payments.
- Share card: "Mera form check ho gaya ✅ – <exam>, last date <x>".

**Do NOT build in v1:** an all-India alert feed, prep content or study reminders, auto-filling or submitting on official portals (ToS, Aadhaar/face-auth and liability problems), exam-fee payment handling, server-side document storage, iOS, multiple states, community or chat, AI document parsing, or the operator product.

## Validation experiment (≤ 2 weeks, ≤ ₹10K)

**Timing:** run it while a big application window in the chosen state is **open**. Check the UPSSSC, UPPRPB and RSSB calendars first. If no window is open within 3 weeks, switch to the state that has one.

**Setup (days 1–3, ~₹1K):** Hindi landing page plus WhatsApp Business number. Free tools: a "document validity + eligibility checklist" for the open notification and a spec-perfect photo/signature export (hook). Paid: **₹49 Form Check** (UPI link, refund guarantee). Concierge delivery by the founder. No app yet.

**Traffic (days 3–14, ~₹7–8K):** seed 20–30 state-level aspirant Telegram and WhatsApp groups (with admin permission). Buy 2–3 paid posts on 10–20K-member state channels (₹500–2,000 each). Up to ₹4K of Meta ads targeting 18–32-year-olds in the state, in Hindi.

**Track:** free checklist completions; Form Check purchases; time per check; errors actually found; repeat or 3-pack purchases; share-card forwards; installs of a stub "join waitlist" APK (optional).

**Pass (all must hold):**
- ≥ 300 free checklist completions in 14 days.
- **≥ 25 paid Form Checks** (≥ 8% of completers), with refund rate ≤ 10%.
- ≥ 1 real error found in ≥ 20% of checks. This proves the check has value.
- Median human time per check ≤ 15 min. This proves it can be productized.
- ≥ 30% of payers opt in to ledger reminders for their next application.

**Kill (any one):** < 10 paid checks; < 3% of completers pay; blended cost > ₹150 per paying user; or > 30 min per check with < 10% of checks finding errors. Then **drop the domain.** If people pay for *full form-filling* (ask "would you pay ₹99 for us to fill it?") but not for a check, note that as a service business, not an app.

**Operator probe (parallel, ₹0, ~6 hrs):** call or visit 15 cyber cafés and CSCs in one district. Ask about forms per day in peak season, time per form, top rejection causes, and how they handle customer documents and photos. Then offer ₹199 for the first month of a beta tool. **Signal:** ≥ 5 of 15 agree to pre-pay, or ≥ 8 agree to use a free prototype for a week. If so, re-score pivot-2 with real data.

## Research gaps
- No direct Play Store review mining (play.google.com, AppBrain and TrueJobs pages were blocked here).
- No 2024–26 rejection totals. No data on what share of forms are filled at cafés vs by the aspirant.
- No primary evidence of operator willingness to pay. TrueJobs premium pricing and traction unknown; SarkariFilled traction unknown.
- Segments with no evidence: women re-entrants, PwD candidates, parents.
- Still missing `user_profile.md` and `market_size.json`. These alone cost the score ~29%.

Files: gap_analysis.md · weaknesses.json · pivot_options.json · pivot_report.md · pivot_scores.json
