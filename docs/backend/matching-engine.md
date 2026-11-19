# Matching engine

Goal: for every published post version, decide for the user one of **eligible / not eligible / check yourself**,
with one human-readable line per rule ("Age 24 on 01-01-2026, limit below 26 with OBC +3 ✓"). It must be cheap
enough to re-run on every profile edit and every corrigendum.

**Where it runs (founder decision 2026-11-19, ADR-11 in [README.md](./README.md)): on the user's phone.** The
detailed profile (DOB, category, PwD, ex-serviceman, qualifications, domicile) never leaves the device. The server
publishes the public rules in a versioned **catalog** (§4); the app evaluates them with the same pure Kotlin module the
server uses for review previews and tests, stores matches in Room and schedules reminders locally
([reminders-and-delivery.md](./reminders-and-delivery.md)). §0 records why.

Rule format: [`rule.schema.json`](./rule.schema.json). Worked examples:
[`examples/ssc-gd-2026.rule.json`](./examples/ssc-gd-2026.rule.json) and
[`examples/up-police-constable-2025.rule.json`](./examples/up-police-constable-2025.rule.json). Both validate against
the schema, and their printed DOB windows reproduce from the formulas in §3.

## 0. Decision: on-device matching vs server-side signature matching

| Concern | A. Server-side signature buckets (previous design) | **B. On-device profile + matching (chosen)** |
|---|---|---|
| Data that leaves the phone | DOB, category (caste), PwD type, ESM, qualifications, domicile, interests, every action | None of the profile. Only what a server feature needs: Google account id (if signed in), purchases, an encrypted backup blob (opt-in), family link ids |
| DPDP Act 2023 / Rules 2025 | Fiduciary holds caste + disability data for up to 1M people: notice + consent per purpose, breach duty, erasure, retention, export all apply to it | The core service processes no personal data on our servers; obligations shrink to accounts, purchases and family links (security-privacy.md §6) |
| Children (17-year-old Agniveer/NDA/10th-pass aspirants) | DOB on the server = we process a child's data → verifiable parental consent (s.9) or an 18+ gate that excludes a real segment | **Minor mode** (DOB < 18 on the device): full matching and local reminders with no server account, no ads, no crash/usage telemetry, no sign-in, no purchase → we receive nothing about the child (legal check still listed) |
| Play Data safety form | Declares personal info incl. "race and ethnicity"-type and health-type fields | Profile is processed on device only → not "collected" under Play's definition |
| Breach impact | Caste + disability list of 1M people | Catalog is public; accounts table holds no profile |
| New-match latency | ~2–15 min after publish | Catalog push (normal-priority topic) + sync: minutes when the phone is active, ≤ one Doze maintenance window otherwise; still far inside the 6 h NFR |
| Reminders when the app is killed | FCM per user (fails if OEM blocks FCM) + local mirror | Local alarms/WorkManager are primary; server **topic** pushes for R1/R-last (`p_{post_id}`) and lifecycle (`n_{notice_id}`) are the backup; both fail only if the app is force-stopped, same as before |
| Lifecycle (Pro), family | Server knows applications and family phones | Device subscribes to topic `n_{notice_id}` after Applied; family = an end-to-end-encrypted watch-list relayed to the family member's own install (reminders-and-delivery.md §5) |
| Multi-device / reinstall | Server profile restores everything | Opt-in encrypted backup blob (key in Google Block Store, not readable by us); otherwise re-enter the 2-minute profile |
| Analytics / curation metrics | Exact (match counts, impact preview, per-user funnels) | Coarse: opt-in, identifier-free daily counters + Play Console; impact preview uses a synthetic population (§7) |
| Correctness | One evaluator version | Old app versions may run an older evaluator → `evaluator_min_version` per rule; older apps show `check_yourself` + "update the app" instead of guessing |
| Cost at 1M users | ~$650/mo DB + planner/outbox at scale | Catalog is static files on the CDN; DB holds accounts only (README §5: total ≈ $330–700 vs ≈ $1,450) |
| Battery / data | One push per alert | ~40 catalog batches a month (a few KB each) + a daily WorkManager reconcile + a handful of alarms; evaluating ≈ 300 open posts takes < 10 ms |

**Why B.** It is the only option where "minimise what leaves the device" (decision 5) and "no special consent" hold
for the sensitive fields we must use (category, PwD). It removes the parental-consent problem for 17-year-olds
without excluding them, makes the Data safety form short, and cuts the largest infra lines. The costs (coarser
analytics, evaluator versioning, a catalog format to maintain, E2E-encrypted family relay) are engineering, not
recurring money, and the rule evaluator was already a pure KMP-ready module.

## 1. Principles

1. **The verdict is deterministic code.** The LLM only *fills* the rule document. A human publishes it, and a pure
   Kotlin function evaluates it. The same inputs always give the same output, and the evaluator is unit-tested
   against boundary cases.
2. **Never guess.** If the profile cannot answer a rule (marks band, ESM service length, an "or equivalent" degree,
   final-year status before a must-pass-by date), the rule yields `check_yourself`. `check_yourself` is never shown
   as eligible.
3. **Copy tables as printed.** Relaxation combinations such as OBC+PwD or SC+ESM are their own rows, exactly as the
   notice prints them. The engine applies **the single most beneficial matching row**. This mirrors the standard
   notice clause and avoids having to encode stacking law.
4. **Physical standards never decide.** They are advisory lines ("check yourself: height 168 cm…") and do not change
   the verdict. Otherwise every police post would become `check_yourself` and the feed would lose its signal.
5. **Explanations are rendered on read, not stored.** A local match row stores `verdict`, `rule_version` and compact
   `reason_codes`. Opening S21 re-evaluates that one (profile, post) on the device in microseconds, so the text
   always reflects the current rule and profile, in the current app language.
6. **The rule is public, the profile is private.** Everything the evaluator needs from us is in the public catalog;
   nothing about the user is needed by the server to compute a verdict.

## 2. Inputs

### Profile → evaluation attributes

| Attribute | Derivation |
|---|---|
| `gender` | as entered; `null` = skipped in S09 (design "छोड़ें") |
| `cat_eff` | `category`, except `obc` with `obc_non_creamy_layer=false` → `gen` for relaxations and fees. Unknown NCL → treated as OBC, with an NCL advisory line. `category_unsure=true` (S07 "पता नहीं") → `gen`, and a post that fails only because of the general limit/category but would pass for OBC/SC/ST becomes `check_yourself` ("? वर्ग पता नहीं — OBC/SC/ST हैं तो उम्र में छूट मिल सकती है", design SC-02) |
| `domicile` | state code |
| `pwd`, `pwd_type` | as entered |
| `esm`, `esm_service_months` | null service months while `esm=true` → ESM rows are `check_yourself` |
| `quals` | list of `(code, status, result_date?, pct_band?)`; codes expanded to ancestors via the taxonomy (so `grad.ba` also satisfies `grad.any` and `level ≥ 40`) |
| `extras` | set of attribute codes; `extras_declared` says whether "none" really means none |
| `marital`, `govt_employee` | optional |
| `dob` | **the only continuous attribute**; stays on the device like every other attribute |

### Targeting (not eligibility)

A post is considered for a user only if `post.exam_tags ∩ profile.interest_exam_types ≠ ∅` and the scope fits:
`all_india`, or `state ∈ profile.job_states ∪ {profile.domicile}`. This is a filter in the app's catalog query; it does
not change verdicts shown on demand ("browse all jobs" still evaluates any post).

## 3. Rule semantics

### 3.1 Age

Let `c` = `cutoff_date`. Pick the first `limits` row whose `when` matches → `(m, M)`. Let `g` = `general_extension_years`.

*Upper bound with `max_convention = not_attained`* (SSC/UPSC "must not have attained M"):
`dob ≥ c − (M + g + r) years − s + 1 day`, where `r` = relaxation years and `s` = military service when the row's mode is
`after_service_deduction`, else 0.

*Upper bound with `completed_years`*: `dob ≥ c − (M + g + r + 1) years − s + 1 day`.

*Lower bound*: `dob ≤ c − m years`.

Check with SSC GD 2026: `c = 2026-01-01`, `M = 23`, `m = 18` → `2003-01-02 ≤ dob ≤ 2008-01-01`. That equals the
printed "born not before 02-01-2003 and not later than 01-01-2008". The publish-time validator recomputes
`printed_dob_window` and **blocks publish on mismatch**. That one check catches the most common extraction error
(off-by-one conventions).

**Relaxation choice:** take every row whose `when` matches the profile and compute the resulting lower DOB bound
for each. The **earliest** bound (most beneficial) wins and is recorded for the explanation. Rows with
`needs_profile`:

- Facts we collect (`esm_service_months`, `govt_employee`) but the user left empty → the row contributes a
  **check_yourself extension** (see §4.2).
- Facts we never collect (`riot_victim_family`, `widow_divorced`, `other`) → **excluded from matching** (no alert,
  which avoids noise). They appear only in on-demand "why not" explanations ("If you are a dependant of 1984/2002
  riot victims, +5 years may apply").

### 3.2 Qualification (`any_of` alternatives; pass if any alternative passes)

For each alternative, filtered by `when`:

| Profile has | Result |
|---|---|
| a qualification whose code (or an ancestor) is in `codes`, or whose `level ≥ min_level`, or a higher level when `include_higher`; with `status=completed` | `pass` (but `fail` if `result_date > must_complete_by`) |
| the same, with `status ∈ {final_year, appearing}` | `appearing_policy`: `allowed` → pass ("provisional"); `not_allowed` → fail; `if_passed_by_date` → pass if `result_date ≤ must_complete_by`, else `check_yourself` |
| `min_percentage_band` set and the user's band unknown | `check_yourself`; known and below → fail |
| no listed code, but a selectable qualification at the **same level** and `equivalence=check_yourself_same_level` | `check_yourself` ("'or equivalent': check whether your degree counts, PDF p.7") |
| nothing at that level or higher | `fail` |

The best result across alternatives wins (pass > check_yourself > fail).

### 3.3 Other rules

| Rule | fail | check_yourself | pass |
|---|---|---|---|
| `required_attributes` (stage `at_application`) | `extras_declared` and none of the codes held | not declared | held |
| `domicile.required_states` | domicile not listed | — | listed / null |
| `gender.allowed` | not listed | gender `null` (skipped) | listed |
| `category.allowed` | `cat_eff` not listed | — | listed |
| `pwd.eligible=false` | user `pwd` | — | not PwD |
| `pwd.suitable_types` | user type not listed | type unknown | listed |
| `ex_serviceman.eligible=false` | user ESM | — | not ESM |
| `marital_status.allowed` | known and not listed | unknown | listed |
| `other_conditions` (`check_yourself` severity, `when` matches) | — | always | — |
| `physical`, `other_conditions` (advisory), `required_attributes` (non-application stage) | — | advisory line only (`result=info`) | — |
| `other_conditions` code `cert_fy` (P1, certificate validity) | — | certificate FY unknown | advisory `rule=certificate, result=info` when the FY differs |

**Verdict** = `not_eligible` if any hard rule fails; else `check_yourself` if any hard rule is `check_yourself`; else
`eligible`. Only `eligible` and `check_yourself` are stored as matches.

**Fee for you:** the first `fees` row whose `when` matches.

## 4. On-device evaluation and the catalog

### 4.1 What the server publishes

On every publish (new notice, corrigendum, extension, cancellation, lifecycle event, retraction) the worker writes one
immutable **catalog batch** and advances the catalog sequence. Catalog files are public, identical for every user and
served from the CDN (Firebase Hosting in front of GCS), so they cost almost nothing at any scale
([`openapi.yaml`](../api/openapi.yaml) tag `catalog`):

| File | Content | Cache |
|---|---|---|
| `GET /v1/catalog/head` | current `seq`, `server_time`, latest snapshot id, the ids of batches after it, `evaluator_min_version_any`, available locales | public, 60 s |
| `GET /v1/catalog/snapshots/{seq}?locales=hi,en` | every notice that is open, upcoming, closed < 90 days ago, or has lifecycle events in the last 12 months, with posts + published rules + lifecycle events | immutable |
| `GET /v1/catalog/batches/{seq}?locales=hi,en` | the notices changed by one publish (full new version) + `removed_notice_ids` | immutable |

`locales` is normalised by the server (sorted, `en` always added), so the CDN holds at most one copy per locale.
Published rules are `rule.schema.json` documents with `review` and `source.quote` stripped; they carry
`evaluator_min_version`. Expected sizes at launch coverage: a snapshot ≈ 0.5–1 MB gzipped (≈ 300 open notices), a
batch 2–10 KB.

### 4.2 Device algorithm

```text
onCatalogSync():                                     -- FCM topic "catalog" (type=sync), daily reconcile worker, app start
  head = GET catalog/head
  if local.seq < head.snapshot_seq - window or local.seq == 0: apply(GET snapshot)  -- first run / far behind
  for b in head.batches where b.seq > local.seq: apply(GET batch b)                   -- in order, each in one Room tx
  matchChanged(changedPosts)

matchChanged(posts):                                  -- O(P·R), P ≤ a few hundred, R ≈ 10: < 10 ms
  for post in posts where targeted(post, profile):   -- §2 targeting
     if post.rule.evaluator_min_version > EVALUATOR_VERSION: verdict = check_yourself("update app")
     else verdict = evaluate(post.rule, profile, todayIst)
     upsert local match (deterministic id m_{post suffix}) → new / withdrawn / unchanged (§5)
     reminderPlanner.recompute(match)                 -- reminders-and-delivery.md §2
     topics.sync(match)                               -- subscribe p_{post_id} while open, n_{notice_id} after Applied (Pro)

onProfileEdit():  matchChanged(allOpenPosts)          -- synchronous on save; S10/B10 show the diff immediately
```

- `match_id` is **derived**, not issued: `m_` + the post id without its `p_` prefix. Links, routes and reminder ids
  (`r:{match_id}:{kind}:{YYYYMMDD}`) therefore mean the same on every device and in server topic pushes, without the
  server knowing who matched.
- "Today" is the IST date from the device clock, checked against `head.server_time`; with a skew > 10 minutes the app
  evaluates and schedules with the server date and shows a "phone time is wrong" banner.
- Not-eligible results are not stored, as before; browse/"why not" evaluates on demand.

### 4.3 Server-side use of the same evaluator

`:matching-core` is a Kotlin Multiplatform module (JVM + Android) with no I/O. The server uses it only for review
tooling: the boundary-test table, the publish-time validators, the synthetic impact preview (§7) and to check a
wrong-match report against the snapshot the user chose to send. It never stores or receives user profiles.

**Evaluator versioning.** `EVALUATOR_VERSION` is bumped when the semantics or the rule schema grow (new `Condition`
field, new rule kind). The publish validator computes the lowest version that understands a rule and writes it into
`evaluator_min_version`. `AppConfig.min_supported_version` is raised when old evaluators would show too many
`check_yourself` lines.

## 5. Re-matching triggers

| Trigger | What runs | User-visible effect |
|---|---|---|
| New post published (catalog batch) | `matchChanged` on the device | new matches → "new match" notification composed on the device (quiet hours and caps apply) |
| Corrigendum or new rule version (catalog batch) | `matchChanged` with the new version + diff | **newly eligible** → new match alert ("age limit raised, you can now apply"); **no longer eligible** and not yet applied → `withdrawn` + one alert ("after the corrigendum you're no longer eligible: reason"); still eligible → only date changes trigger an `extension`/`corrigendum` alert; reminders recomputed |
| Profile edit (local save, S10/S27) | `matchChanged(allOpenPosts)`: evaluate all open posts (≈100–300), O(P·R), < 10 ms on a 2 GB phone; synchronous | added matches are `silent=true` (no push; shown in the B10 re-match sheet); removed open matches → `withdrawn`; applied ones are never withdrawn |
| Post closes (`apply_end` passed) | daily reconcile worker on the device | open matches → `expired` |
| User crosses an exam family's upper age (P1, SC-21) | daily reconcile worker compares the local DOB with followed exam families' general limits (cutoffs of open posts are fixed, so open matches never change) | one local `age` notification: which exams are still open with relaxations (B14) |
| Reference taxonomy change (code merged/renamed) | reference bundle carries a code map; the app migrates the stored profile on next start | none |
| Wrong-match report confirmed | fix the rule → new version → as corrigendum | affected devices re-evaluate on the next batch and show a correction notification |
| User turns 18 (minor mode ends) | reconcile worker | one local note: sign-in, Pro and ads notice become available (security-privacy.md §6) |

## 6. Explanations ("Why you match")

`explain(rule, profile, now, locale)` returns an ordered list of `RuleExplanation {rule, result, code, params, text, source}`.
Text comes from versioned templates in the app's string resources (one per supported locale, falling back per
`locale.fallback`); the parameters are computed values. Rule-provided text (`qualification.text`, `physical.text`,
`other_conditions[].text`, fee `note`) comes from the catalog's `LocalizedText`. Examples (Ravi:
male, OBC-NCL, UP, DOB 2001-05-10, B.A. completed):

| Post | Line (en) | Line (hi) |
|---|---|---|
| SSC GD 2026 | ✓ Age 24 on 01-01-2026 (must be under 26: limit 23 + OBC 3) | ✓ 01-01-2026 को आयु 24 वर्ष (सीमा 23 + OBC 3 = 26 से कम) |
| | ✓ 10th pass (B.A. is higher, allowed) | ✓ 10वीं पास (B.A. उच्च योग्यता, मान्य) |
| | ✓ Men and women both can apply | ✓ पुरुष व महिला दोनों आवेदन कर सकते हैं |
| | ? Physical test: height/chest/race, check the table (PDF p.x) | ? शारीरिक मानक: नोटिस की तालिका देखें |
| | ✓ Fee for you: ₹100 | ✓ आपकी फ़ीस: ₹100 |

Corrigendum example (UP Police constable 2025): Amit, male, General, UP, DOB 2001-09-15, 12th pass. Age on
2025-07-01 is 23. **v1** (max 22): ✗ "Age 23 on 01-07-2025 (must be under 22)" → no match. **v2** (+3 one-time):
"must be under 25" ✓ → new match alert *"Age limit raised by 3 years, you can now apply for UP Police Constable"*.
Pooja (female, General, Bihar, DOB 1999-02-01) with UP in her job states: v1 ✗ (age 26, must be under 25); v2 ✓
(under 28) as Unreserved, with an advisory line that the women's 20% horizontal reservation is UP-domicile only.

ESM example (SSC GD): male, OBC, ex-serviceman with 96 months of service, DOB 1996-06-01. Matching rows are `obc`
(+3) and `esm_obc` (+6 after deduction); the most beneficial is `esm_obc`. Line: "✓ Age 29 on 01-01-2026 − 8 years of
service = 21, under 29 (23 + 6 ESM-OBC)". If service months are missing → "? Ex-serviceman relaxation depends on your
service length: add it in your profile" (`check_yourself`, with a profile-fix chip, S27).

`reason_codes` (smallint) are stored per local match. With the user's opt-in, the daily usage counters include
counts per reason code (no post, no profile), so we still learn which rule most often produces `check_yourself`
and which optional profile field to ask for.

## 7. Correctness safeguards

1. **Schema + semantic validators at publish:** dates ordered; `printed_dob_window` reproduced (blocking); relaxation
   years ≤ 20; every relaxation row has a `source.page`; every qualification code exists in the taxonomy; fee table
   has a default row; at least one gender; `pwd.eligible=false` flagged for review.
2. **Boundary test set generated per post:** for each limits/relaxation row, synthesize profiles at `lo − 1d`, `lo`,
   `hi`, `hi + 1d`, plus one per qualification alternative and status. The console shows the verdict table; the
   reviewer ticks it against the PDF. It is stored with the rule version as a regression fixture.
3. **Impact preview before publish:** the server has no profiles, so the console evaluates the rule against a
   **synthetic population** (≈ 50K profiles drawn from public applicant statistics and the opt-in aggregate counters:
   category, gender, state, qualification and age distributions) and shows "≈ 9% of a typical audience eligible, 0.4%
   check yourself; on the corrigendum ≈ 0.7% would lose eligibility". Large unexpected deltas on a corrigendum need a
   second reviewer.
4. **Second reviewer** required when `risk_flags` include state-conditional relaxations, gender-specific limits,
   `pwd`/ESM rows, an extractor/verifier disagreement, or any confirmed wrong-match report on the organization in the
   last 90 days.
5. **Wrong-match reports** (`POST /v1/reports`, no account needed) carry the post, rule version and rule line; the
   minimal profile attributes are attached **only** if the user ticks "send the details needed to check this" (never
   in minor mode). Three or more reports on one post auto-pause *new-match* notifications for that post (a catalog
   flag `new_match_paused`; reminders to existing matches continue) and page the founder.
6. **Kill switch per post:** "retract" publishes a batch that marks the post `retracted`; every device sets its open
   match to `withdrawn` with a correction notification. Audited.
7. **Shared evaluator:** the evaluator is a pure Kotlin Multiplatform module (`:matching-core`). The same code runs in
   the app (the only place verdicts are made for users) and in the admin console's previews. Property-based tests
   (kotest) assert monotonicity: adding a relaxation never removes eligibility, and a higher qualification never
   flips pass to fail when `include_higher`.

## 8. Known limitations (v1)

- Experience requirements ("2 years' experience") → `other_conditions` check_yourself. Fine for the launch exams.
- Per-zone or per-region eligibility differences (rare; e.g. some RRB posts with regional language) →
  `other_conditions` with `when.domicile_states`.
- Departmental (in-service) candidates → `govt_employee` relaxations, P1.
- Attempts-left counting (P2 age countdown) needs exam-level attempt rules; out of the post rule schema.
