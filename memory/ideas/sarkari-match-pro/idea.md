---
slug: sarkari-match-pro
status: validating
created: 2026-11-19
parent_idea: govt-exam-reminders (scored 13/100, drop)
---

# Idea v2: Eligibility-matched govt-job reminders + ₹20/month "Don't lose this job" Pro tier

## Problem
Indian govt-job aspirants follow 5–10 broadcast channels/apps that alert on everything → notification blindness → they miss the few jobs that matter, and they keep checking portals manually. After applying, they miss admit cards, reschedules, answer-key objection windows, DV slots; forms get rejected for avoidable errors (name mismatch, age-on-cutoff, category certificate year, photo spec), costing ₹200–500 corrections or the whole attempt.

## Free core (the hook)
- Detailed profile: exam levels/types of interest, qualification, DOB, category, state/domicile, other params.
- App continuously checks new notifications; surfaces ONLY true eligibility matches (age on cutoff incl. category relaxation, qualification, domicile, region).
- On match: reminder repeats until the user applies or marks "not interested".
- Every reminder carries everything needed: dates, fee, documents, photo/signature spec, apply link.
- No prep content, no upsell of courses.

## Pro tier — ₹20/month (sold as ₹199/yr or ₹49 per application season)
1. Post-application lifecycle tracker: city slip, admit card, reschedules, answer key + objection window, result, cut-off, DV/medical slot.
2. Pre-submit form check vs. the notification's rules.
3. WhatsApp/SMS fallback for critical reminders (OEM push-killing).
4. Certificate validity/renewal alerts (OBC-NCL/EWS FY, domicile).
5. Exam clash & reschedule detector; preference-window reminders.
6. Document vault + auto-resize per exam spec.
7. Family mode (parent also gets critical reminders).
8. Fee-payment-failed watch.

## Platform & founder
Android-first (Kotlin), Hindi-first. Solo indie developer, no existing audience (no user_profile.md yet).

## Research already done (reuse, don't redo from scratch)
See ../govt-exam-reminders/: competitors.json, gap_analysis.md, core_loop_competitors.md, paid_features_20rs.md, pivot_memo.md, and ../../market_insights/.
