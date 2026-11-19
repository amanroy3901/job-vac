# Operations (solo developer)

Design target: **one person can run this in about 2 hours a week of ops** on top of curation. Everything is managed;
alerts page only when a user-facing promise is at risk.

## 1. Environments

| Env | GCP/Firebase project | DB | Integrations | Purpose |
|---|---|---|---|---|
| local | none (emulators) | Postgres 16 in docker compose | Firebase Auth emulator; WireMock stubs for FCM, Play Developer API, Anthropic (recorded fixtures) | dev + integration tests |
| staging | `sarkarimatch-stg` | Cloud SQL db-f1-micro | real FCM (staging app id), Play internal test track + licence testers, Anthropic with low spend limit | end-to-end, restore drills, catalog load tests |
| prod | `sarkarimatch-prod` | Cloud SQL (see scaling) | live | — |

Config comes from env vars + Secret Manager. Feature flags and kill switches (`AppConfig.features`,
`kill_switches`, `ads`) live in a `config` table edited in the admin console, served with an ETag, and cached 5 min
server-side.

## 2. Repository and CI/CD

Go backend (ADR-13; `matching-core`/`reminder-core` are KMP modules of the Android repo, not of the server):

```
backend/
  cmd/sarkari/        # one binary: serve (ROLE=api|worker|admin|all), migrate, tick, catalog rebuild, admin add
  migrations/         # goose SQL (00001 = docs/backend/schema.sql verbatim, 00002 = Go-backend deltas + seeds)
  internal/httpapi/   # /v1 REST (openapi.yaml), problem+json, idempotency, rate limits, RTDN hook, ticks
  internal/auth/      # Firebase ID token, App Check, Google OIDC, IAP verification (cached key sets)
  internal/catalog/   # catalog batch/snapshot builder, locale sets, head; internal/blob = local dir | GCS
  internal/rules/     # rule.schema.json validation + printed-DOB-window and semantic validators
  internal/ingest/    # curation workflow (draft → rules → approve → publish); extractor interface
  internal/billing/   # Play verification, RTDN, trial, entitlement + ES256 token
  internal/push/      # FCM HTTP v1 sender, topic_send dispatcher, account pushes
  internal/{account,family,admin,worker,config,db}/
```

**GitHub Actions:**
- **PR** (`.github/workflows/backend.yml`, implemented): gofmt → go vet → staticcheck → `go test -race` (unit tests;
  integration tests against a Postgres 16 service container: migrations, session/devices/entitlements/family,
  curation publish → catalog build, topic scheduling; contract tests validating responses against `openapi.yaml`)
  → migrations on a clean DB → distroless Docker build. To add: catalog compatibility test (current app evaluator
  versions parse the new batch), golden evaluator fixtures shared with the app.
- **main:** deploy to staging (`sarkari migrate up` → `gcloud run deploy --no-traffic` → smoke tests → 100%).
- **tag `v*` (manual approval):** prod migrate (expand/contract: migrations must be compatible with the previous
  revision) → deploy api/worker/admin revisions with no traffic → smoke → 10% for 15 min with a 5xx-rate check →
  100%. **Rollback** = shift traffic to the previous revision (seconds). Destructive "contract" migrations ship one
  release later.
- Auth: Workload Identity Federation (no JSON keys). The Artifact Registry image is scanned.
- **Never deploy between 07:30–09:30 IST** (`last_day` topic backups go out at 08:30): a pipeline guard enforces it.

## 3. Observability

**Logs:** structured JSON (Cloud Logging) with `trace_id`, `user_id` (pseudonymous), `route` and `job`, plus a PII
deny-list filter. Retention 30 days. Exclusion filters drop health-check and 2xx access logs for `/v1/catalog/*`; client IPs are
dropped from logs of the unauthenticated endpoints.

**Errors:** Sentry (free developer tier) with PII scrubbing and the server-name/IP disabled.

**Metrics** (OpenTelemetry → Cloud Monitoring custom metrics):

| Area | Metrics |
|---|---|
| API | requests, 5xx rate, p50/p95 latency per route, App Check reject rate (by OEM), 429s |
| Pipeline | `source_freshness_minutes{source}`, extraction queue age, review queue age, curation minutes/notice, LLM spend MTD |
| Catalog | publish→batch-on-CDN latency, batch size, snapshot size, CDN hit ratio, `catalog/head` requests (≈ active installs) |
| Delivery | `tick_lag_seconds{tick}`, topic sends/errors by code, `last_day` backups sent by 08:35, self-test pass rate (by OEM, from `push_test`) |
| Business (opt-in counters + Play Console) | installs, retention, profile completed, matches shown, Applied rate, notification-blocked rate, wrong-match reports per 1K matches shown, trial starts, conversions, entitlement counts, ad-enabled share |
| Infra | Cloud SQL CPU/memory/connections/storage/replication lag, Cloud Run instances, cold starts |

**SLOs** (monthly):

| SLO | Target |
|---|---|
| API availability (non-5xx on `/v1/**`) | 99.5% |
| Catalog batch on the CDN and `catalog` topic push sent ≤ 5 min after publish | 99% |
| `last_day` topic backups sent by 08:35 IST | 99.9% |
| Self-test pass rate (onboarding, after the OEM guide) | ≥ 90% (watch per OEM) |
| Every enabled source fetched OK within 2 h (08:00–22:00 IST) | 99% |

**Paging** (Cloud Monitoring → mobile app push + Telegram bot webhook), only for: `last_day` backups at risk (tick lag
> 10 min between 08:00–09:00), catalog publish failing, API 5xx > 5% for 10 min, DB down/CPU > 90% for 15 min, any
tick stalled > 10 min, a source down for 6 h in business hours, RTDN dead-letter > 0. Everything else
goes in a **daily ops email** (queue ages, failures, spend, report counts).

## 4. Runbooks

| Situation | Steps |
|---|---|
| **Source site down / layout changed** | Dashboard shows the failing source → check the site manually → if layout changed, update the `source.extractor` config in admin (no deploy) → re-run watch → if a notice was missed, create a review task from the official URL. If down for days, rely on Employment News + coverage monitor; post a status note in app config (`maintenance.message` for that org) |
| **LLM wrong / mass wrong verdict** | Reports spike or a reviewer spots it → admin "pause new-match alerts" for the post (automatic at 3 reports; a catalog flag) → fix the rule → publish a new version → devices re-evaluate and show withdrawn/corrected notifications → post-mortem: add a validator or prompt rule + a regression fixture |
| **LLM API down** | Extraction jobs retry with backoff; reviewer uses the manual-entry form; alerts may be late, never wrong |
| **FCM outage** | `alerts_delayed=true` in `/v1/health` (auto when FCM error rate > 20%) → sender keeps retrying (2 h) → on-device alarms fire for every scheduled reminder regardless → devices' daily reconcile fetches the catalog over HTTPS → after recovery, re-send `last_day`/`d_minus_1` topic rows still relevant |
| **Bad catalog batch** (wrong rule shipped) | Publish a corrective batch (corrigendum/retract) at once — batches are immutable and applied in order; never edit a published file. If a batch is malformed, the app rejects it (schema/sha256 check) and stays on the previous seq; publish a fixed batch with the next seq |
| **RTDN backlog / dead letters** | Check push subscription errors → fix → replay the dead-letter topic → run the "reconcile active purchases" job |
| **DB storage/CPU high** | Check top queries (Query Insights) → scale the tier up (a few minutes of downtime outside 07:30–09:30 IST); the app keeps working offline from its catalog |
| **Bad deploy** | Shift traffic to the previous revision; if a migration is involved, the expand/contract rule means the old code still works |
| **Data breach suspected** | Rotate secrets (KMS key version, entitlement signing key, Anthropic key) → revoke admin sessions → assess scope from audit/access logs → notify affected users without delay and the Data Protection Board (detailed report within 72 h per DPDP Rules; verify) → post-mortem |
| **Restore drill (quarterly)** | PITR clone of prod into staging → run the smoke suite → record RTO → delete the clone |
| **Deletion backlog** | Daily `hard_delete` job metrics; a job failure alerts in the daily email; it must clear within 30 days of `hard_delete_at` |

## 5. Scaling plan

Per-user work happens on the phones, so server load grows with accounts and catalog downloads, not with matches.

| Installs | DB | Cloud Run | Other | Ops changes |
|---|---|---|---|---|
| Play closed test (≤ 1K) | db-f1-micro, 10 GB | api min 0, worker tick-only | | manual publishes allowed |
| ≤ 100K | db-g1-small, 20 GB, PITR | api min 1 (1 vCPU/1 GiB, concurrency 80), worker tick-driven | CDN for catalog | weekly ops review |
| 100K–1M | custom 2 vCPU / 7.5 GB, HA from ~500K | api max 10; worker request-billed | snapshot rebuilt nightly; CDN hit ratio watched | quarterly restore drill |
| > 1M | 4 vCPU | | split snapshots per locale set (already per `locales`) | revisit only if `catalog/head` origin traffic grows (it is CDN-cached 60 s) |

**Peak load estimate (1M installs, one big all-India notice):** one batch (≈ 5 KB × locale sets) pushed to topic
`catalog`; ≈ 300K active phones fetch `head` (CDN, 60 s TTL) and the batch (CDN, immutable) within the next hour —
origin sees one request per edge per file. The API sees nothing unless users open account features.

## 6. Cost guardrails

- GCP billing budgets: alerts at $50, $150, $500/month (raise with scale); a Pub/Sub budget notification → Slack/
  Telegram.
- Anthropic Console spend limit (monthly) at 2× expected.
- No per-message costs exist (no WhatsApp/SMS/OTP); FCM is free.
- Cloud Logging exclusion filters; metrics cardinality capped (no per-user labels).
- Cloud Run: `min-instances=1` only on `api`; startup CPU boost; worker request-billed.
- Monthly right-sizing review of Cloud SQL (CPU < 40% for 30 days → consider stepping down).

## 7. Security operations

Dependabot/Renovate weekly; image scanning; a quarterly access review (`admin_user`, IAM); secret rotation every 90
days; App Check enforcement metrics reviewed monthly.

## 8. On-device matching benchmark

The earlier Postgres signature-bucket benchmark is obsolete (ADR-11). Acceptance target for `:matching-core` on the
reference low-end phone (Galaxy A03-class, 2 GB): evaluate 300 open posts × the profile + explanations for the 20
newest matches in **< 50 ms** on a cold process, and apply a 1 MB snapshot (parse + Room upsert + evaluate) in
**< 1.5 s** in a background worker. Measured with a Macrobenchmark in the app repo (frontend testing-ci.md).

## 9. Build plan (solo developer)

**Roadmap change (decision 8):** the validation test is now a **Play closed-testing build with Play Billing**, so a
thin app must exist before the test. The PRD's "weeks 1–2 test → weeks 3–10 P0" becomes **weeks 1–5 validation
build → weeks 6–7 closed test (14 days, ≥ 12 opted-in testers — also Play's production-access requirement for new
personal accounts) → go/stop → weeks 8–13 rest of P0**.

Lead-time items to start in **week 1**: Play Console developer account (verify personal vs organisation), merchant
account + the `pro` subscription with both base plans (check `P4M`), closed-test tester group (Google Group) and a
recruiting plan in aspirant Telegram groups; domain; GCP/Firebase projects (Google sign-in SHA-1/SHA-256, App Check);
legal review of S47, minor mode and the ads notice.

| Phase | Weeks | Backend deliverables | Exit criteria |
|---|---|---|---|
| **Validation build** | 1–5 | Schema V1; `matching-core` v1 + golden tests; extraction script + minimal review page; catalog builder + CDN; `catalog` topic sends; `/config`, `/reference`, `/health`; Play verify/ack/RTDN + trial + entitlement token; `/reports`, `/telemetry`, `/push-tests` | ~20–40 real notices for SSC/RRB/UP in the catalog; a licence-tester purchase grants Pro |
| **Closed test** | 6–7 | operate: curate, publish, watch counters and Play Console | PRD gates: ≥ 300 profiles, ≥ 100 Applied, ≥ 8% of those pay (≥ 10 payments), < 50% disable notifications/uninstall, ≤ 30 min curation, 0 wrong verdicts in 20 |
| **P0 build** | 8 | Watchers for SSC, IBPS, UPSC, 3 RRBs, UP boards; text layer; classify/extract/verify; validators | real notices land in the review queue |
| | 9 | Admin console: review screen, boundary tests, synthetic impact preview, publish, corrigendum diff, retract | publish → batch on CDN in < 5 min |
| | 10 | `p_{post}` deadline backups, lifecycle events + `n_{notice}` sends, account pushes (`notification_text`), Google-linked accounts, deletion | lifecycle alert reaches a Pro tester |
| | 11 | Encrypted backup endpoints; referral; localisation pipeline (locale table, `translation_task`, per-locale catalog files) | restore on a second phone works |
| | 12–13 | Load test of catalog origin, dashboards/alerts, runbooks, restore drill, App Check monitor→enforce | SLO dashboards green for 7 days |
| **P1** | months 3–6 | Daily digest; clash and certificate alerts (on device); family links + encrypted watch-list relay; second launch state; first non-launch locale pilot; coverage monitor | PRD P1 list |
| **P2** | month 6+ | Early alerts from exam calendars; age-limit countdown (attempt rules); payment-status watch; monthly ₹20 plan (if demand); ad mediation if ad revenue justifies it. SMS backup dropped | PRD P2 list |

Rough split: about 35–45% of the work is backend; the on-device matching moves effort to the Android app.
