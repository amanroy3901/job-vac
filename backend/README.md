# Sarkari Match backend (Go + PostgreSQL)

The server side of [docs/backend](../docs/backend/README.md): a public, versioned **rule catalog** that phones evaluate
locally (ADR-11), plus minimal accounts, Play Billing and App Store entitlements (one per account across both
stores), family relay, FCM topic pushes (with an APNs block for iOS) and the curation console. The apps are Android and
iOS from one Kotlin Multiplatform codebase (founder decision 9). **The server stores no DOB, category, PwD, phone, name or email** (a test asserts no such column exists).
Contract: [`docs/api/openapi.yaml`](../docs/api/openapi.yaml). Language decision and libraries: backend ADR-13.

## Layout

One binary, `cmd/sarkari`, because ADR-09 already ships one image as three Cloud Run services that differ only by
`ROLE`; subcommands cover the one-off tasks, so there is nothing else to build or version.

| Path | What |
|---|---|
| `cmd/sarkari` | `serve` (ROLE `api`/`worker`/`admin`/`all`), `migrate up\|down\|status\|reset`, `tick <topics\|jobs\|housekeep>`, `catalog rebuild`, `admin add <email> <owner\|reviewer>`; graceful shutdown on SIGTERM |
| `migrations/` | goose SQL embedded in the binary. `00001_baseline.sql` = `docs/backend/schema.sql` verbatim (test fails on drift); `00002_go_backend.sql` = deltas + seed data; `00003_ios.sql` = the iOS shape (already folded into schema.sql; idempotent, so a no-op on fresh databases and an upgrade for older ones) + App Store product ids |
| `internal/app` | Wiring from config (also used by the integration tests) |
| `internal/config` | Env config; refuses dev modes when `APP_ENV=prod` |
| `internal/httpapi` | `/v1/**` handlers, problem+json, request ids, slog access logs, App Check, auth, rate limits, `Idempotency-Key`, version gate (426), `/hooks/play-rtdn`, `/hooks/app-store`, `/internal/tick/{name}` |
| `internal/auth` | Firebase ID tokens (securetoken x509, aud/iss/exp/auth_time, `sign_in_provider` ∈ anonymous/google.com/apple.com), App Check (JWKS), Google OIDC (Pub/Sub, Scheduler), IAP (ES256); cached key sets; dev verifier; Sign in with Apple token exchange/revocation (`AppleTokens`, `AppleIDClient` with ES256 client secret) |
| `internal/appstore` | StoreKit 2 / App Store Server Notifications V2 JWS verification (x5c → embedded Apple Root CA - G3, marker OIDs, ES256, bundle id/environment/appAppleId), payload types, App Store Server API client (ES256 JWT; transaction info, subscription statuses, history), `TestSigner` PKI for tests/dev |
| `internal/rules` | Embedded `rule.schema.json` (draft 2020-12), printed-DOB-window check (blocking), semantic validators, launch-locale check, risk flags, public-rule stripping |
| `internal/catalog`, `internal/blob` | Notice assembly, per-locale-set rendering, batches, snapshots, head; blob store = local dir or GCS (JSON API) |
| `internal/ingest` | Curation: draft → attach rule per post → approve (2nd reviewer for risk flags) → publish (notice_version, post_rule, catalog batch, R3/R1/R-last topic pushes) → retract |
| `internal/billing` | Entitlement computation over both stores (single source of truth, `platform`, duplicate-store flag), ES256 entitlement token + JWKS, trial, Play subscriptionsv2 verify + acknowledge, RTDN, App Store verify + notifications + state mapping + season stacking, ownership rules |
| `internal/push` | FCM HTTP v1 payload builder (`android` block unchanged; `apns` block: alert loc-keys, mutable-content, categories `MATCH_REMINDER` (APPLIED/LATER), time-sensitive last day, background sync) + sender (+ log/recorder fakes), `topic_send` dispatcher (SKIP LOCKED, backoff), account pushes with `notification_text` fallback, self-test |
| `internal/account`, `internal/family` | Session bootstrap/merge, devices + primary install (N-47), consents, E2E backup (CAS), deletion; family invites/accept/watch-list |
| `internal/worker` | Job queue (catalog builds, account pushes, delayed self-tests, Play re-ack, Sign in with Apple revocation, republish), housekeeping |
| `internal/admin` | Curation JSON API `/admin/api/*` + minimal pages `/admin/`, IAP or dev auth |

## Run locally

Needs Go 1.24 and PostgreSQL 16. Either `docker compose up -d postgres` (see `compose.yaml`) or a local cluster:

```sh
initdb -D /tmp/pg -U postgres --auth=trust && pg_ctl -D /tmp/pg -o "-p 5432" -l /tmp/pg.log start
createdb -h 127.0.0.1 -U postgres sarkari
cp .env.example .env          # dev modes: AUTH_MODE=dev, PLAY_MODE=fake, APPSTORE_MODE=fake, APPLE_SIGNIN_MODE=log, FCM_MODE=log, BLOB_MODE=local
make run                      # migrate + serve ROLE=all on :8080 (api + admin + a worker tick loop)
make admin EMAIL=you@example.com ROLE=owner
```

Try it (dev auth = `Bearer dev:<uid>[:google[:email]|:apple]`):

```sh
curl localhost:8080/v1/config
curl -XPOST localhost:8080/v1/session -H 'Authorization: Bearer dev:u1:google:a@b.c' \
  -H "Idempotency-Key: $(uuidgen)" -d '{"installation_id":"i1","locale":"hi","device":{"fcm_token":"t","app_version":1,"locale":"hi"}}'
open http://localhost:8080/admin/?as=you@example.com      # create a draft, paste a rule, approve, publish
curl localhost:8080/v1/catalog/head; curl 'localhost:8080/v1/catalog/batches/1?locales=hi'
curl -XPOST localhost:8080/v1/me/purchases/google-play -H 'Authorization: Bearer dev:u1:google' \
  -H "Idempotency-Key: $(uuidgen)" -d '{"product_id":"pro","purchase_token":"fake:season-4m-prepaid"}'
```

### Dev seed data (`scripts/seed-dev.sh`)

`scripts/seed-dev.sh` fills a local backend with six current notices through the real curation flow (admin JSON API:
draft → rule per post → approve, with the second reviewer `rev@example.com` when the validator flags risk → publish →
catalog batch + snapshot). Needs `jq`, `ADMIN_AUTH_MODE=dev` and a running server; it adds the two dev admins with
`bin/sarkari admin add` when `bin/` and `.env` exist. Re-running skips notices that already exist (409 on
`org_code`+`advt_no`) and resumes an unpublished task.

```sh
make run &                        # or ./bin/sarkari serve with .env loaded
scripts/seed-dev.sh               # BASE=http://localhost:8080 OWNER=founder@example.com REVIEWER=rev@example.com
```

| File (`scripts/seed/`) | Notice | Scope | Expected for a 23-year-old OBC male BA graduate from UP (mobile e2e) |
|---|---|---|---|
| `ssc-gd-2027` | SSC GD Constable 2027 | all India | eligible (18–23 + OBC 3) |
| `up-police-2026` | UP Police Constable 2026 | UP | eligible (male 18–22 + UP OBC 5) |
| `rrb-ntpc-grad-2026` | RRB NTPC Graduate 2026 | all India | eligible |
| `ibps-clerk-2026` | IBPS Clerk 2026 | all India | eligible |
| `upsssc-ja-2026` | UPSSSC Junior Assistant 2026 | UP | check yourself (CCC + Hindi typing, PET scorecard) |
| `upsc-nda-1-2027` | UPSC NDA & NA (I) 2027 | all India | not eligible (16–19) |

**DEV SEED DATA:** modelled on real recruitments but not checked against official PDFs. `advt_no` starts with
`DEV-SEED/`, `provenance.dev_seed` is true, rules say so in `review.notes`, and every `source.page` (required by the
publish check for age limits and relaxation rows) is a placeholder. Apply windows are fixed dates in Nov–Dec 2026;
move them forward in the `*.notice.json` files (and the rules' `must_complete_by`) when they pass. Never run it against
staging or prod.

iOS locally: with `APPSTORE_MODE=fake` the verifier also accepts StoreKit-configuration (Xcode / LocalTesting)
transactions from the simulator without a chain check (Apple's library does the same; refused in prod), so
`POST /v1/me/purchases/app-store {"signed_transaction": "<jwsRepresentation>"}` works against a local StoreKit
configuration file with the product ids `in.sarkarimatch.pro.yearly` / `in.sarkarimatch.pro.season4m`.

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
delete, and **iOS** (`TestIOS`): Sign in with Apple session + iOS device, App Store yearly with intro trial
(bundle/garbage rejection, idempotent replays, server trial marked used), the same uid on Android keeping Pro,
cross-account 409, notifications (grace → billing retry → renew → auto-renew off → refund, stale ordering ignored,
dedupe, untrusted chain / other app → 401), non-renewing season via `ONE_TIME_CHARGE` + stacked top-up, Server API
refresh, Apple token revocation on deletion (`queued` / `token_missing` / fresh code). Unit tests verify a **real
Apple-signed sandbox JWS** against the embedded Apple root, the state mapping and the APNs payloads. Every response is
validated against the `openapi.yaml` component schemas, and every one of the 37 paths must be routed. CI:
`.github/workflows/backend.yml`.

## Implemented vs stubbed

| Area | Status |
|---|---|
| Public: config (ETag, force/soft update, JWKS), health, reference, products, notices, catalog head/snapshots/batches | Done |
| Session bootstrap (create/link/merge anonymous, privacy consent, primary device), me, consents, deletion + cancel + hard delete | Done |
| Devices (upsert with `platform`, list, delete, make-primary with N-47 push), backup (CAS via If-Match, Google- or Apple-linked) | Done |
| Entitlement (both stores), trial (N-31 at day 12), purchases list, Play verify + acknowledge, RTDN (Pub/Sub push, OIDC), voided purchases | Done |
| App Store: StoreKit 2 JWS verify, Server Notifications V2 (all types, dedupe, ordering), non-renewing season stacking, App Store Server API client (statuses used on verify; lookup/history available) | Done |
| Sign in with Apple: provider accepted, code → refresh token (sealed), revocation job on deletion | Done (real client needs the SIWA key in prod) |
| APNs via FCM (`apns` block per message type) | Done (client must ship the loc-keys and categories, api/README.md §10) |
| Family invite/accept/decline/list/remove/watch-list/following | Done |
| Reports (auto-pause at 3 → republish), telemetry, push self-tests | Done |
| Catalog publisher (batches, snapshots, head, 12 locale sets, GCS or local), topic sends (`catalog`, `p_…` R3/R1/R-last) | Done |
| Curation console: create draft, attach/validate rule, approve (2nd reviewer), publish, retract, app_config edit | Done (minimal pages) |
| `POST /v1/me/referral:redeem` | **501** (needs Play Integrity verdict + anti-abuse rules) |
| Firebase Admin disable/delete on account deletion | Interface only (`account.FirebaseAdmin`, nil = skipped) |
| App Check limited-use replay protection | TODO (tokens are verified, not consumed) |
| Lifecycle events quick-entry + `n_{notice}` pushes, reports inbox actions, source watchers, PDF/OCR, LLM extraction (`ingest.Extractor`), translation queue, server-side evaluator for previews | Not started |
| KMS envelope for sealed tokens (`internal/seal` uses an env AES key) | TODO |

## iOS operations (founder decision 9)

- **App Store Connect:** products `in.sarkarimatch.pro.yearly` (auto-renewable, group `pro`, 1 year, intro offer
  14-day free trial, Billing Grace Period on) and `in.sarkarimatch.pro.season4m` (non-renewing subscription); Family
  Sharing off; **App Store Server Notifications V2** URL `https://api.sarkarimatch.in/hooks/app-store` for both
  Production and Sandbox; then *Request a Test Notification* (`TEST`, logged) from App Store Connect. Set
  `APPSTORE_APP_APPLE_ID` (App Information → Apple ID) — Production notifications are refused without it.
- **Secrets (Secret Manager):** `APPLE_SIWA_KEY_PEM` (Sign in with Apple key, Certificates → Keys), optional
  `APPSTORE_API_KEY_PEM` (In-App Purchase key, App Store Connect → Users and Access → Integrations); ids
  `APPLE_TEAM_ID`, `APPLE_SIWA_KEY_ID`, `APPSTORE_API_KEY_ID`, `APPSTORE_API_ISSUER_ID`. The APNs auth key goes to
  the Firebase console (Cloud Messaging → Apple app configuration), not to this service.
- **Alerts:** a dead `apple_revoke` job (10 attempts) means a deleted Apple user's token is still valid: fix the
  key/ids, then re-queue the job (its payload holds only the sealed token) so it runs again; App Store notifications failing
  signature checks (log `app store notification rejected`) more than a few per day; Sandbox purchases in production
  are expected from App Review (`purchase.test_purchase`).
- **Apple Root CA - G3** is embedded (`internal/appstore/AppleRootCA-G3.pem`, valid to 2039, fingerprint pinned by a
  test). If Apple ever moves signing to a new root, add it to `appstore.AppleRoots()` and release.
- **Costs:** Apple Developer Program $99/year; no per-user infrastructure cost (JWS verification is local CPU,
  notifications are a few per subscriber per year, APNs is free through FCM). Store commission (15% with the Small
  Business Program) comes out of revenue.

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
4. Firebase Admin REST client, App Check token consumption (Play Integrity + App Attest), KMS envelope, referral redeem.
5. iOS: daily App Store reconciliation via the Server API (transaction history for subscriptions without a recent
   notification), and answering `CONSUMPTION_REQUEST` if refund abuse appears.
6. Decide the topic-name question (`p_{post_id}` with already-prefixed ids, docs/api/README.md §17).
7. Deploy: Cloud Run services + Scheduler jobs (`/internal/tick/topics|jobs` every minute, `housekeep` every 10 min), Pub/Sub push subscription for RTDN, Firebase Hosting rewrites for `/v1/catalog/**` to GCS.
