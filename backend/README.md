# Sarkari Match backend (Go + PostgreSQL)

The server side of [docs/backend](../docs/backend/README.md): a public, versioned **rule catalog** that phones evaluate
locally (ADR-11), plus minimal accounts, Play Billing entitlements, family relay, FCM topic pushes and the curation
console. **The server stores no DOB, category, PwD, phone, name or email** (a test asserts no such column exists).
Contract: [`docs/api/openapi.yaml`](../docs/api/openapi.yaml). Language decision and libraries: backend ADR-13.

## Layout

One binary, `cmd/sarkari`, because ADR-09 already ships one image as three Cloud Run services that differ only by
`ROLE`; subcommands cover the one-off tasks, so there is nothing else to build or version.

| Path | What |
|---|---|
| `cmd/sarkari` | `serve` (ROLE `api`/`worker`/`admin`/`all`), `migrate up\|down\|status\|reset`, `tick <topics\|jobs\|housekeep>`, `catalog rebuild`, `admin add <email> <owner\|reviewer>`; graceful shutdown on SIGTERM |
| `migrations/` | goose SQL embedded in the binary. `00001_baseline.sql` = `docs/backend/schema.sql` verbatim (test fails on drift); `00002_go_backend.sql` = deltas + seed data |
| `internal/app` | Wiring from config (also used by the integration tests) |
| `internal/config` | Env config; refuses dev modes when `APP_ENV=prod` |
| `internal/httpapi` | `/v1/**` handlers, problem+json, request ids, slog access logs, App Check, auth, rate limits, `Idempotency-Key`, version gate (426), `/hooks/play-rtdn`, `/internal/tick/{name}` |
| `internal/auth` | Firebase ID tokens (securetoken x509, aud/iss/exp/auth_time), App Check (JWKS), Google OIDC (Pub/Sub, Scheduler), IAP (ES256); cached key sets; dev verifier |
| `internal/rules` | Embedded `rule.schema.json` (draft 2020-12), printed-DOB-window check (blocking), semantic validators, launch-locale check, risk flags, public-rule stripping |
| `internal/catalog`, `internal/blob` | Notice assembly, per-locale-set rendering, batches, snapshots, head; blob store = local dir or GCS (JSON API) |
| `internal/ingest` | Curation: draft → attach rule per post → approve (2nd reviewer for risk flags) → publish (notice_version, post_rule, catalog batch, R3/R1/R-last topic pushes) → retract |
| `internal/billing` | Entitlement computation (single source of truth), ES256 entitlement token + JWKS, trial, Play subscriptionsv2 verify + acknowledge, RTDN, ownership rules |
| `internal/push` | FCM HTTP v1 sender (+ log/recorder fakes), `topic_send` dispatcher (SKIP LOCKED, backoff), account pushes with `notification_text` fallback, self-test |
| `internal/account`, `internal/family` | Session bootstrap/merge, devices + primary install (N-47), consents, E2E backup (CAS), deletion; family invites/accept/watch-list |
| `internal/worker` | Job queue (catalog builds, account pushes, delayed self-tests, Play re-ack, republish), housekeeping |
| `internal/admin` | Curation JSON API `/admin/api/*` + minimal pages `/admin/`, IAP or dev auth |

## Run locally

Needs Go 1.24 and PostgreSQL 16. Either `docker compose up -d postgres` (see `compose.yaml`) or a local cluster:

```sh
initdb -D /tmp/pg -U postgres --auth=trust && pg_ctl -D /tmp/pg -o "-p 5432" -l /tmp/pg.log start
createdb -h 127.0.0.1 -U postgres sarkari
cp .env.example .env          # dev modes: AUTH_MODE=dev, PLAY_MODE=fake, FCM_MODE=log, BLOB_MODE=local
make run                      # migrate + serve ROLE=all on :8080 (api + admin + a worker tick loop)
make admin EMAIL=you@example.com ROLE=owner
```

Try it (dev auth = `Bearer dev:<uid>[:google[:email]]`):

```sh
curl localhost:8080/v1/config
curl -XPOST localhost:8080/v1/session -H 'Authorization: Bearer dev:u1:google:a@b.c' \
  -H "Idempotency-Key: $(uuidgen)" -d '{"installation_id":"i1","locale":"hi","device":{"fcm_token":"t","app_version":1,"locale":"hi"}}'
open http://localhost:8080/admin/?as=you@example.com      # create a draft, paste a rule, approve, publish
curl localhost:8080/v1/catalog/head; curl 'localhost:8080/v1/catalog/batches/1?locales=hi'
curl -XPOST localhost:8080/v1/me/purchases/google-play -H 'Authorization: Bearer dev:u1:google' \
  -H "Idempotency-Key: $(uuidgen)" -d '{"product_id":"pro","purchase_token":"fake:season-4m-prepaid"}'
```

## Tests

```sh
make lint                     # gofmt + vet (+ staticcheck if installed)
make test                     # unit tests; integration tests run when TEST_DATABASE_URL is set
TEST_DATABASE_URL=postgres://postgres@127.0.0.1:5432/postgres?sslmode=disable make test-race
```

Integration tests create a throwaway database per test (role needs CREATEDB) and drop it afterwards. They cover the
session/idempotency/devices/N-47 flow, trial, Play verify/ownership/pending/RTDN, family invite→accept→watch-list→stop,
curation publish → catalog head/batch/snapshot/notice (sha256 = ETag, locale filtering, no review notes or quotes in
the catalog), wrong-match auto-pause → republish, self-test, telemetry clamps, backup CAS, consents, deletion → hard
delete. Every response is validated against the `openapi.yaml` component schemas, and every one of the 36 paths
must be routed. CI: `.github/workflows/backend.yml`.

## Implemented vs stubbed

| Area | Status |
|---|---|
| Public: config (ETag, force/soft update, JWKS), health, reference, products, notices, catalog head/snapshots/batches | Done |
| Session bootstrap (create/link/merge anonymous, privacy consent, primary device), me, consents, deletion + cancel + hard delete | Done |
| Devices (upsert, list, delete, make-primary with N-47 push), backup (CAS via If-Match, Google-only) | Done |
| Entitlement, trial (N-31 at day 12), purchases list, Play verify + acknowledge, RTDN (Pub/Sub push, OIDC), voided purchases | Done |
| Family invite/accept/decline/list/remove/watch-list/following | Done |
| Reports (auto-pause at 3 → republish), telemetry, push self-tests | Done |
| Catalog publisher (batches, snapshots, head, 12 locale sets, GCS or local), topic sends (`catalog`, `p_…` R3/R1/R-last) | Done |
| Curation console: create draft, attach/validate rule, approve (2nd reviewer), publish, retract, app_config edit | Done (minimal pages) |
| `POST /v1/me/referral:redeem` | **501** (needs Play Integrity verdict + anti-abuse rules) |
| Firebase Admin disable/delete on account deletion | Interface only (`account.FirebaseAdmin`, nil = skipped) |
| App Check limited-use replay protection | TODO (tokens are verified, not consumed) |
| Lifecycle events quick-entry + `n_{notice}` pushes, reports inbox actions, source watchers, PDF/OCR, LLM extraction (`ingest.Extractor`), translation queue, server-side evaluator for previews | Not started |
| KMS envelope for sealed tokens (`internal/seal` uses an env AES key) | TODO |

## Schema deltas (migration 00002) to fold into docs/backend/schema.sql

`app_config` table (remote config, operations.md §1) · `review_task.source_document_id` nullable (manual entry) ·
`family_link.relation` + state `declined` · `catalog_batch.files/built_at`, `catalog_snapshot.files` (per-locale
sha256; head lists only built batches) · `purchase.base_plan_id/test_purchase` · unique grant per purchase · seeds for
`product`, `ref_item`, `qualification`, `notification_text`. The backup's `format_version` is stored inside
`backup_blob.key_id` as `<format_version>:<key id>` (no column for it in the design schema).

## Next steps

1. Lifecycle quick-entry → `lifecycle` batches + `n_{notice}` topic pushes; reports inbox.
2. Extraction pipeline: watchers (`source` table), PDF store in GCS, Claude extraction + verify pass behind `ingest.Extractor`.
3. Go evaluator port + golden fixtures shared with `:matching-core` (boundary tables, impact preview).
4. Firebase Admin REST client, App Check token consumption, KMS envelope, referral redeem.
5. Decide the topic-name question (`p_{post_id}` with already-prefixed ids, docs/api/README.md §17).
6. Deploy: Cloud Run services + Scheduler jobs (`/internal/tick/topics|jobs` every minute, `housekeep` every 10 min), Pub/Sub push subscription for RTDN, Firebase Hosting rewrites for `/v1/catalog/**` to GCS.
