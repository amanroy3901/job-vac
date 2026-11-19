# Matching engine

Goal: for every published post version, decide for every profile one of **eligible / not eligible / check yourself**,
with one human-readable line per rule ("Age 24 on 01-01-2026, limit below 26 with OBC +3 ✓"). It must be fast
enough that a new post fans out to 1M users in seconds, and cheap enough to re-run on every profile edit and every
corrigendum.

Rule format: [`rule.schema.json`](./rule.schema.json). Worked examples:
[`examples/ssc-gd-2026.rule.json`](./examples/ssc-gd-2026.rule.json) and
[`examples/up-police-constable-2025.rule.json`](./examples/up-police-constable-2025.rule.json). Both validate against
the schema, and their printed DOB windows reproduce from the formulas in §3.

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
5. **Explanations are rendered on read, not stored.** A match stores `verdict`, `rule_version` and compact
   `reason_codes`. `GET /v1/me/matches/{id}` re-evaluates that one (user, post) in microseconds, so the text always
   reflects the current rule and profile.

## 2. Inputs

### Profile → evaluation attributes

| Attribute | Derivation |
|---|---|
| `gender` | as entered |
| `cat_eff` | `category`, except `obc` with `obc_non_creamy_layer=false` → `gen` for relaxations and fees. Unknown NCL → treated as OBC, with an NCL advisory line |
| `domicile` | state code |
| `pwd`, `pwd_type` | as entered |
| `esm`, `esm_service_months` | null service months while `esm=true` → ESM rows are `check_yourself` |
| `quals` | list of `(code, status, result_date?, pct_band?)`; codes expanded to ancestors via the taxonomy (so `grad.ba` also satisfies `grad.any` and `level ≥ 40`) |
| `extras` | set of attribute codes; `extras_declared` says whether "none" really means none |
| `marital`, `govt_employee` | optional |
| `dob` | **the only continuous attribute**, kept out of the signature |

### Targeting (not eligibility)

A post is considered for a user only if `post.exam_tags ∩ profile.interest_exam_types ≠ ∅` and the scope fits:
`all_india`, or `state ∈ profile.job_states ∪ {profile.domicile}`. This is a SQL filter; it does not change verdicts
shown on demand ("browse all jobs" still evaluates any post).

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
| `gender.allowed` | not listed | — | listed |
| `category.allowed` | `cat_eff` not listed | — | listed |
| `pwd.eligible=false` | user `pwd` | — | not PwD |
| `pwd.suitable_types` | user type not listed | type unknown | listed |
| `ex_serviceman.eligible=false` | user ESM | — | not ESM |
| `marital_status.allowed` | known and not listed | unknown | listed |
| `other_conditions` (`check_yourself` severity, `when` matches) | — | always | — |
| `physical`, `other_conditions` (advisory), `required_attributes` (non-application stage) | — | advisory line only | — |

**Verdict** = `not_eligible` if any hard rule fails; else `check_yourself` if any hard rule is `check_yourself`; else
`eligible`. Only `eligible` and `check_yourself` are stored as matches.

**Fee for you:** the first `fees` row whose `when` matches.

## 4. Fast fan-out: signature buckets + DOB range join

### 4.1 Idea

Every rule except age depends only on discrete attributes, and age depends on `dob` only through **one interval**
once the discrete attributes are fixed. So:

1. **Eligibility signature** `elig_sig = sha256(canonical JSON of all non-DOB evaluation attributes)`, computed on
   every profile write. Interests and job states are *not* in it.
2. Table `elig_signature(sig, attrs, user_count)` holds the distinct signatures. Typical cardinality is about 5–50K
   at 1M users, because attributes are heavily skewed (category × domicile × a few common qualification sets).
3. For a post, evaluate the rule **once per signature**, which yields `fail`, or `(verdict_without_age, dob_lo,
   dob_hi, [ext_lo, ext_hi))`.
4. One set-based SQL statement joins profiles to windows using the `(elig_sig, dob)` index.

### 4.2 Algorithm

```text
matchPost(post, ruleVersion):
  sigs   = SELECT sig, attrs FROM elig_signature WHERE user_count > 0          -- S rows
  windows = []
  for (sig, attrs) in sigs:                                                    -- O(S · R)
     r = evaluateWithoutDob(rule, attrs)       -- all non-age rules + chosen relaxation rows
     if r.verdict == FAIL: continue
     windows += (sig, r.dobLo, r.dobHi, r.verdict)                             -- firm window
     if r.extLo < r.dobLo:                                                     -- e.g. ESM with unknown service
        windows += (sig, r.extLo, r.dobLo - 1 day, CHECK_YOURSELF)
  COPY windows INTO temp table w                                               -- ≤ 2S rows
  -- one statement; idempotent; runs inside a job with dedupe_key = post:version
  INSERT INTO match (id, user_id, post_id, recruitment_id, verdict, rule_version, reason_codes, ...)
  SELECT gen_id(), p.user_id, :post, :rec, w.verdict, :ver, w.codes, ...
  FROM w JOIN profile p ON p.elig_sig = w.sig AND p.dob BETWEEN w.lo AND w.hi
  JOIN app_user u ON u.id = p.user_id AND u.state = 'active'
  WHERE p.interest_exam_types && :exam_tags
    AND (:scope = 'all_india' OR :state = p.domicile_state OR :state = ANY(p.job_states))
  ON CONFLICT (user_id, post_id) DO UPDATE
     SET verdict = EXCLUDED.verdict, rule_version = EXCLUDED.rule_version, ...
     WHERE match.verdict IS DISTINCT FROM EXCLUDED.verdict OR match.rule_version <> EXCLUDED.rule_version
  RETURNING user_id, (xmax = 0) AS inserted                                    -- feeds reminder_state + alerts
```

Then, in the same job, the existing matches of this post that were **not** returned (no longer eligible) become
`status = withdrawn` (corrigendum case), and `reminder_state` is (re)computed for every affected match
([reminders-and-delivery.md](./reminders-and-delivery.md)).

**Complexity.** Rule evaluation is O(S·R), with S ≤ 50K signatures and R ≈ 10 rules, which is about 500K cheap
evaluations, well under 1 s on one vCPU. The SQL part is O(N_f + M·log N) for N_f profiles passing the interest
filter and M inserted matches (hash join or index range scans; the planner picks). Memory is O(S).

**Measured** (local Postgres 16, 4 vCPU, 1M synthetic profiles, 20K skewed signatures, GIN interest filter; see
`operations.md` §8 for the script):

| Case | Matches written | Time |
|---|---|---|
| Typical post (~10% of users) | 101,226 | **0.75 s** |
| Re-run after corrigendum, no verdict changes (upsert no-op) | 0 changed | **0.54 s** |
| Very broad post (60% of users) | 600,122 | **3.3 s** |

Budget for Cloud SQL (network disk, more indexes, `reminder_state` and `alert` rows written by the follow-up step):
about 3–5× slower, so **≈ 3–15 s per post at 1M users**. Fan-out of the pushes is then rate-limited on purpose (§5 of
the reminders doc).

### 4.3 Why not bitsets?

Roaring bitmaps per attribute value plus a DOB-sorted array would run in about 100 ms, but they need a resident,
warmed, invalidated cache in the worker, plus a second source of truth. The SQL approach is fast enough by two
orders of magnitude and has zero cache. Revisit only beyond about 10M profiles.

## 5. Re-matching triggers

| Trigger | What runs | User-visible effect |
|---|---|---|
| New post published | `matchPost` | new matches → "new match" alerts (respecting quiet hours and caps) |
| Corrigendum or new rule version | `matchPost` with new version + diff | **newly eligible** → new match alert ("age limit raised, you can now apply"); **no longer eligible** and not yet applied → `withdrawn` + one alert ("after the corrigendum you're no longer eligible: reason"); still eligible → only date changes trigger an `extension`/`corrigendum` alert; reminders recomputed |
| Profile edit (`PUT /v1/me/profile`) | `matchUser(user)`: evaluate all open posts (≈100–300) for one user, O(P·R), < 5 ms; synchronous in the request | added matches are `silent=true` (no push; shown in the B10 re-match sheet); removed open matches → `withdrawn`; applied ones are never withdrawn |
| Post closes (`apply_end` passed) | housekeeping | open matches → `expired` |
| Reference taxonomy change (code merged/renamed) | migration maps codes; signatures recomputed in batch | none |
| Wrong-match report confirmed | fix the rule → new version → as corrigendum | affected users get a correction alert |

The signature for a profile edit is recomputed in the same transaction (`elig_signature.user_count` ±1).

## 6. Explanations ("Why you match")

`explain(rule, profile, now)` returns an ordered list of `RuleExplanation {rule, result, code, params, text{hi,en}, source}`.
Text comes from versioned templates in both languages; the parameters are computed values. Examples (Ravi:
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

`reason_codes` (smallint) are stored per match only for analytics, e.g. which rule most often produces
`check_yourself`, so we know which optional profile field to ask for.

## 7. Correctness safeguards

1. **Schema + semantic validators at publish:** dates ordered; `printed_dob_window` reproduced (blocking); relaxation
   years ≤ 20; every relaxation row has a `source.page`; every qualification code exists in the taxonomy; fee table
   has a default row; at least one gender; `pwd.eligible=false` flagged for review.
2. **Boundary test set generated per post:** for each limits/relaxation row, synthesize profiles at `lo − 1d`, `lo`,
   `hi`, `hi + 1d`, plus one per qualification alternative and status. The console shows the verdict table; the
   reviewer ticks it against the PDF. It is stored with the rule version as a regression fixture.
3. **Impact preview before publish:** "will match 12,340 users (11,900 eligible, 440 check yourself); 85 existing
   matches would be withdrawn". Large unexpected deltas on a corrigendum need a second reviewer.
4. **Second reviewer** required when `risk_flags` include state-conditional relaxations, gender-specific limits,
   `pwd`/ESM rows, an extractor/verifier disagreement, or any confirmed wrong-match report on the organization in the
   last 90 days.
5. **Wrong-match reports** (`POST …/reports`) snapshot the minimal profile attributes. Three or more reports on one
   post auto-pause *new-match* alerts for that post (reminders to existing matches continue) and page the founder.
6. **Kill switch per post:** "retract" sets every open match to `withdrawn` with a correction alert, and is audited.
7. **Shared evaluator:** the evaluator is a pure Kotlin module (`:matching-core`, KMP-ready). The same code runs in
   the API, the worker, the admin preview and, optionally, the app's offline "why" rendering. Property-based tests
   (kotest) assert monotonicity: adding a relaxation never removes eligibility, and a higher qualification never
   flips pass to fail when `include_higher`.

## 8. Known limitations (v1)

- Experience requirements ("2 years' experience") → `other_conditions` check_yourself. Fine for the launch exams.
- Per-zone or per-region eligibility differences (rare; e.g. some RRB posts with regional language) →
  `other_conditions` with `when.domicile_states`.
- Departmental (in-service) candidates → `govt_employee` relaxations, P1.
- Attempts-left counting (P2 age countdown) needs exam-level attempt rules; out of the post rule schema.
