# Operations (solo developer)

Design target: **one person can run this in about 2 hours a week of ops** on top of curation. Everything is managed;
alerts page only when a user-facing promise is at risk.

## 1. Environments

| Env | GCP/Firebase project | DB | Integrations | Purpose |
|---|---|---|---|---|
| local | none (emulators) | Postgres 16 in docker compose | Firebase Auth emulator; WireMock stubs for FCM, Meta, Razorpay, Play Developer API, SMS, Anthropic (recorded fixtures) | dev + integration tests |
| staging | `sarkarimatch-stg` | Cloud SQL db-f1-micro | real FCM (staging app id), Meta test number, Razorpay test mode, Play internal test track + license testers, Anthropic with low spend limit | end-to-end, restore drills, load tests |
| prod | `sarkarimatch-prod` | Cloud SQL (see scaling) | live | — |

Config comes from env vars + Secret Manager. Feature flags and kill switches (`AppConfig.features`,
`kill_switches`, `ads`) live in a `config` table edited in the admin console, served with an ETag, and cached 5 min
server-side.

## 2. Repository and CI/CD

```
backend/
  app/            # Ktor: ROLE=api|worker|admin entrypoints
  matching-core/  # pure Kotlin rule evaluator + explain templates (KMP-ready)
  ingestion/      # watchers, PDF text layer, LLM clients, validators
  delivery/       # planner, FCM/WA/SMS senders
  db/             # Flyway migrations (V1 = docs/backend/schema.sql)
  contract/       # openapi.yaml copy + generated DTOs + contract tests
```

**GitHub Actions:**
- **PR:** ktlint/detekt → unit tests (matching-core golden tests: SSC GD 2026, UP Police 2025 v1/v2, boundary
  generators, property tests) → Testcontainers Postgres integration tests (migrations, matchPost SQL, planner) →
  OpenAPI lint (spectral) + response validation of integration tests against `openapi.yaml` → Jib image build.
- **main:** deploy to staging (Flyway migrate → `gcloud run deploy --no-traffic` → smoke tests → 100%).
- **tag `v*` (manual approval):** prod migrate (expand/contract: migrations must be compatible with the previous
  revision) → deploy api/worker/admin revisions with no traffic → smoke → 10% for 15 min with a 5xx-rate check →
  100%. **Rollback** = shift traffic to the previous revision (seconds). Destructive "contract" migrations ship one
  release later.
- Auth: Workload Identity Federation (no JSON keys). The Artifact Registry image is scanned.
- **Never deploy between 07:30–10:30 IST** (morning reminder peak and `last_day` sends): a pipeline guard enforces it.

## 3. Observability

**Logs:** structured JSON (Cloud Logging) with `trace_id`, `user_id` (pseudonymous), `route` and `job`, plus a PII
deny-list filter. Retention 30 days. Exclusion filters drop health-check and 2xx access logs for `/v1/notices/*`.

**Errors:** Sentry (free developer tier) with PII scrubbing and the server-name/IP disabled.

**Metrics** (OpenTelemetry → Cloud Monitoring custom metrics):

| Area | Metrics |
|---|---|
| API | requests, 5xx rate, p50/p95 latency per route, App Check reject rate (by OEM), 429s |
| Pipeline | `source_freshness_minutes{source}`, extraction queue age, review queue age, curation minutes/notice, LLM spend MTD |
| Matching | matchPost duration, matches created/withdrawn per publish, wrong-match reports per 1K matches |
| Delivery | `tick_lag_seconds{tick}` (now − oldest due item), delivery queue depth, FCM sends/errors by code, **push receipt rate by OEM**, publish→first push latency, `last_day` sent-by-08:15 ratio, WA sent/delivered/failed/cost, OTP sends/verify ratio/cost |
| Business | DAU/MAU, profile completion, Applied rate, notification-disable rate (device channel state), trial starts, conversions, entitlement counts, ad-enabled MAU |
| Infra | Cloud SQL CPU/memory/connections/storage/replication lag, Cloud Run instances, cold starts |

**SLOs** (monthly):

| SLO | Target |
|---|---|
| API availability (non-5xx on `/v1/**`) | 99.5% |
| `/v1/me/sync` p95 latency (server) | < 300 ms |
| New-match pushes sent ≤ 30 min after publish (outside quiet hours) | 99% |
| `last_day` reminders sent by 08:15 IST | 99.9% |
| Every enabled source fetched OK within 2 h (08:00–22:00 IST) | 99% |

**Paging** (Cloud Monitoring → mobile app push + Telegram bot webhook), only for: `last_day` at risk (tick lag > 10
min between 07:30–09:00), API 5xx > 5% for 10 min, DB down/CPU > 90% for 15 min, any tick stalled > 10 min, OTP or
WhatsApp spend > 80% of the daily cap, a source down for 6 h in business hours, RTDN dead-letter > 0. Everything else
goes in a **daily ops email** (queue ages, failures, spend, report counts).

## 4. Runbooks

| Situation | Steps |
|---|---|
| **Source site down / layout changed** | Dashboard shows the failing source → check the site manually → if layout changed, update the `source.extractor` config in admin (no deploy) → re-run watch → if a notice was missed, create a review task from the official URL. If down for days, rely on Employment News + coverage monitor; post a status note in app config (`maintenance.message` for that org) |
| **LLM wrong / mass wrong verdict** | Reports spike or a reviewer spots it → admin "pause new-match alerts" for the post (automatic at 3 reports) → fix the rule → publish a new version → affected users get withdrawn/corrected alerts → post-mortem: add a validator or prompt rule + a regression fixture |
| **LLM API down** | Extraction jobs retry with backoff; reviewer uses the manual-entry form; alerts may be late, never wrong |
| **FCM outage** | `alerts_delayed=true` in `/v1/health` (auto when FCM error rate > 20%) → sender keeps retrying (2 h) → Pro escalations to WhatsApp happen on timeout → on-device mirror alarms fire for scheduled reminders → after recovery, check the `failed` deliveries report; re-queue `last_day`/`d_minus_1` still relevant |
| **WhatsApp template rejected/paused or quality "low"** | Webhook alert → sender auto-switches to the variant/generic template → edit the template text (remove anything promotional) and resubmit → if number quality drops: reduce escalations to `last_day` + critical lifecycle until it recovers |
| **WhatsApp/OTP spend spike** | Check OTP verify ratio (pumping shows < 10% verified) and top prefixes/IPs → tighten limits or block the prefix → the global cap stops further spend automatically |
| **RTDN backlog / dead letters** | Check push subscription errors → fix → replay the dead-letter topic → run the "reconcile active purchases" job |
| **DB storage/CPU high** | Check top queries (Query Insights) → partition drop job ran? → scale the tier up (a few minutes of downtime outside the 07:30–10:30 / 18:00–20:00 IST windows) |
| **Bad deploy** | Shift traffic to the previous revision; if a migration is involved, the expand/contract rule means the old code still works |
| **Data breach suspected** | Rotate secrets (Meta, Razorpay, SMS, KMS key version, pepper only with a re-HMAC plan) → revoke admin sessions → assess scope from audit/access logs → notify affected users without delay and the Data Protection Board (detailed report within 72 h per DPDP Rules; verify) → post-mortem |
| **Restore drill (quarterly)** | PITR clone of prod into staging → run the smoke suite → record RTO → delete the clone |
| **Deletion backlog** | Daily `hard_delete` job metrics; a job failure alerts in the daily email; it must clear within 30 days of `hard_delete_at` |

## 5. Scaling plan

| Users (registered) | DB | Cloud Run | Other | Ops changes |
|---|---|---|---|---|
| WhatsApp test (≤ 1K) | db-f1-micro, 10 GB | api min 0, worker tick-only | BSP or Cloud API, Razorpay | manual sends allowed |
| ≤ 20K | db-g1-small, 20 GB, PITR | api min 1 (1 vCPU/1 GiB, concurrency 80), worker tick-driven | | weekly ops review |
| 20K–300K | custom 2 vCPU / 7.5 GB, 50–100 GB | api max 20; worker max 5 | Cloud Armor + external LB at ~100K if abuse appears | monthly partitions; matchPost via `job` |
| 300K–1M | custom 4 vCPU / 16 GB **HA**, 200–300 GB; optional read replica for sync/feed reads | worker **always-on** (min 1, CPU always allocated) for continuous draining | CDN essential; consider a 2nd WA number | quarterly restore drill; partition retention verified |
| > 1M | 8 vCPU; split `alert/delivery` writes; consider moving delivery to Pub/Sub + push workers | | | revisit bitset matcher only if matchPost > 60 s |

**Peak load estimate (1M users, one big all-India notice matching 400K):**
- Fan-out at 20K alerts/min → 20 min.
- Opens: about 30% open within 1 h → 120K users × ~3 origin requests (sync, match detail; notice JSON from CDN) ≈
  360K requests/h, average ~100 rps, burst ~300–400 rps → 2–5 Cloud Run instances, about 1–2K DB queries/s of simple
  indexed reads → OK on 4 vCPU.
- Daily reminder peaks (08:00 last-day, 10:00 D-1/D-3, 18:30 periodic) are jittered by user-hash ±10 min, except
  `last_day` (08:00–08:10).

## 6. Cost guardrails

- GCP billing budgets: alerts at $50, $150, $500/month (raise with scale); a Pub/Sub budget notification → Slack/
  Telegram.
- Anthropic Console spend limit (monthly) at 2× expected.
- WhatsApp daily ceiling and per-user season quota; OTP global daily cap (security-privacy.md §2).
- Cloud Logging exclusion filters; metrics cardinality capped (no per-user labels).
- Cloud Run: `min-instances=1` only on `api`; startup CPU boost; worker request-billed until about 300K users.
- Monthly right-sizing review of Cloud SQL (CPU < 40% for 30 days → consider stepping down).

## 7. Security operations

Dependabot/Renovate weekly; image scanning; a quarterly access review (`admin_user`, IAM); secret rotation every 90
days (Meta token is long-lived system-user token: rotate on staff change); App Check enforcement metrics reviewed
monthly.

## 8. Matching benchmark (reproducible)

Run on local Postgres 16 (4 vCPU, 15 GB RAM), 2026-11-19. Synthetic: 1M profiles, signatures drawn from 20K values
with a cubic skew, 60% with SSC/railway/police interests.

```sql
CREATE TABLE profile(user_id text primary key, elig_sig int not null, dob date not null, interests text[] not null);
INSERT INTO profile SELECT 'u_'||g, floor(power(random(),3)*20000)::int,
  date '1990-01-01' + (random()*6000)::int,
  CASE WHEN random()<0.6 THEN '{ssc,railway,police}'::text[] ELSE '{banking,teaching}'::text[] END
FROM generate_series(1,1000000) g;
CREATE INDEX ON profile(elig_sig, dob); CREATE INDEX ON profile USING gin(interests); ANALYZE profile;
-- windows from evaluating the rule per signature (8,000 of 20,000 pass)
CREATE TEMP TABLE win AS SELECT s AS sig,
  date '2003-01-02' - (CASE WHEN s%5=0 THEN 365*5 WHEN s%3=0 THEN 365*3 ELSE 0 END) AS lo,
  date '2008-01-01' AS hi, CASE WHEN s%7=0 THEN 'check_yourself' ELSE 'eligible' END AS verdict
FROM generate_series(0,19999) s WHERE s%5 IN (0,1);
CREATE TABLE match(user_id text, post_id text, verdict text, primary key(user_id, post_id));
INSERT INTO match SELECT p.user_id, 'p_1', w.verdict FROM win w
  JOIN profile p ON p.elig_sig = w.sig AND p.dob BETWEEN w.lo AND w.hi
 WHERE p.interests && '{ssc,police}'::text[]
ON CONFLICT (user_id, post_id) DO UPDATE SET verdict = EXCLUDED.verdict;
```

Results: 101,226 matches in **0.75 s**; re-run with no changes **0.54 s**; broad post (600,122 matches) **3.3 s**.
Planner: hash join on `elig_sig` + filter on the DOB range. Expect 3–5× on Cloud SQL with the real table width and
follow-up writes.

## 9. Build plan (solo developer, matching the PRD roadmap)

Lead-time items to start in **week 1**: Meta Business verification + WhatsApp number; DLT entity, header and
templates; Play Console merchant account + subscription products; Razorpay KYC; domain; GCP/Firebase projects; legal
review of the privacy notice and the under-18 question.

| Phase | Weeks | Backend deliverables | Exit criteria |
|---|---|---|---|
| **WhatsApp test** | 1–2 | Schema subset (recruitment/post/post_rule, test users); `matching-core` v0 + golden tests; extraction script (Claude + schema) + a minimal admin review page; profile intake via a WhatsApp/Google Form → import; matching CLI → WhatsApp template sends (BSP or Cloud API); Razorpay payment links + webhook → sheet | PRD gates: ≥ 300 profiles, ≥ 100 APPLIED, ≥ 8% pay, ≤ 30 min curation, 0 wrong verdicts in 20 |
| **P0 build** | 3 | Repo, CI/CD, staging/prod, Flyway V1, Ktor skeleton, Firebase token + App Check, `/config`, `/session`, `/reference`, `/health` | app can sign in anonymously |
| | 4 | Profile + signatures, `matching-core` full (age/qual/relaxations/explain), `/eligibility/check`, `PUT profile` re-match | onboarding works end to end with seeded rules |
| | 5 | Watchers for SSC, IBPS, UPSC, 3 RRBs, UP boards; GCS; text layer; classify/extract/verify; validators | real notices land in the review queue |
| | 6 | Admin console: review screen, boundary tests, impact preview, publish, corrigendum diff; `matchPost` job | publish → matches in < 1 min |
| | 7 | Planner/sender/receipts, FCM, self-test, prefs + quiet hours, `/sync`, notices via CDN, inbox | reminders fire on schedule in staging |
| | 8 | Actions + batch, applications, lifecycle events + quick entry, OTP (WhatsApp/SMS) + custom token, primary device | full free flow |
| | 9 | Play Billing verify/ack/RTDN, entitlement + trial + ads flags, WhatsApp Cloud API + opt-in/out + escalation + quota, consents, export, deletion | Pro flow end to end with license testers |
| | 10 | k6 load test (1M synthetic profiles in staging), dashboards/alerts, runbooks, restore drill, App Check monitor→enforce plan, closed beta | SLO dashboards green for 7 days in beta |
| **P1** | months 3–6 | Daily digest; extension alerts polish; clash and certificate alerts; family alerts + invitee flow; second launch state; coverage monitor; region field for RRB lifecycle; Cloud Armor if needed; nightly batch re-verification | PRD P1 list |
| **P2** | month 6+ | SMS backup; early alerts from exam calendars; age-limit countdown (attempt rules); payment-status watch; monthly ₹20 UPI AutoPay (if demand); on-device KMP evaluator; ad mediation if ad revenue justifies it | PRD P2 list |

Rough split: about 40–50% of the 8 P0 weeks is backend. The remainder is the Android app and curation, which is why
the design avoids anything that needs ongoing care.
