# Security and privacy

Threat model in one line: after the founder decisions of 2026-11-19 the server holds **no eligibility profile and no
phone numbers** — DOB, category, PwD/ex-serviceman status and qualifications stay on the phone (ADR-11). We hold
accounts (Firebase uid), purchases, encrypted blobs we cannot read, and the public catalog. No feature sends a message
that costs money. The main risks are now **admin-console compromise or a wrong publish (every device would act on
it), catalog tampering, abuse of the few unauthenticated endpoints (reports, telemetry, self-test), account takeover
of a Google-linked account, leakage of on-device data (lost phone, backups), and DPDP non-compliance for the data we
still hold.**

## 1. Client and API protection

| Control | Detail |
|---|---|
| **App Check (Play Integrity provider)** | Required on every non-catalog `/v1/**` call (catalog, config, reference and health are public CDN files). Verified in a Ktor plugin with the Firebase Admin SDK (JWKS cached). Limited-use (replay-protected) tokens on `/v1/reports`, `/v1/telemetry`, `/v1/push-tests`, `/v1/me/trial`, `/v1/me/purchases/*`, referral redeem, family accept. Debug provider tokens only in staging. Rollout: monitor-only for 2 weeks, then enforce (track the reject rate by OEM) |
| **Firebase ID token** | `Authorization: Bearer` on `/v1/me/**` and `/v1/family/**`, verified locally (RS256, `aud`/`iss`, `exp`, `auth_time`); revocation checked on delete account and on Google account switch (D11) |
| **Rate limits** | Token buckets in the API process, keyed by user, installation and IP /24 (the IP is used in memory only, never logged). Limits in [api/README.md §7](../api/README.md#7-pagination--limits). Cloud Armor rate rules only if abuse appears at scale |
| **Input validation** | Generated DTO validation from OpenAPI; size limits (64 KB body; backup ≤ 64 KB; watch-list ≤ 32 KB; report comment ≤ 500 chars); enums enforced |
| **Catalog integrity** | Catalog files are written only by the worker service account to a bucket with no public write; each batch carries `sha256` of its body in `catalog/head`, and the app verifies it before applying (defends against a poisoned cache/CDN object). Batches are immutable |
| **Transport** | TLS 1.2+ (Firebase Hosting / Cloud Run managed certs); HSTS; no cleartext. App: network security config with no user CAs in release (*certificate pinning not used*: Google rotates certs; App Check covers the intent) |
| **Output** | `Cache-Control: private, no-store` on all `/v1/me/**` and `/v1/family/**`; `public` only for catalog/reference/config/health. CDN never caches authorized responses |
| **Webhooks** | Only `/hooks/play-rtdn`: Pub/Sub OIDC JWT (audience + service account email). `/internal/tick/*`: Cloud Scheduler OIDC JWT, served only by the worker service with ingress `internal-and-cloud-load-balancing` + IAM `roles/run.invoker` |

## 2. Accounts and abuse

There is no OTP, SMS or WhatsApp anywhere, so SMS pumping and per-message cost abuse no longer exist.

- **Sign-in** is Google via Android Credential Manager → Firebase Auth (Google provider); guests use Firebase
  anonymous auth, created lazily. Account takeover = Google account takeover, which Google's own protections cover.
  Switching the Google account on a phone (D11) re-authenticates and clears local account state.
- **Unauthenticated endpoints** (`/v1/reports`, `/v1/telemetry`, `/v1/push-tests`) need a limited-use App Check token
  and are rate-limited per installation and IP /24. Telemetry values are bounded (per counter per day) and outliers
  are dropped, so a modified client cannot meaningfully skew metrics.
- **Self-test** can only target the FCM token in the request; it sends one fixed message and stores the token
  encrypted until the test ends.
- **Family links** need the invite token (128-bit, 7-day expiry, single use) and Pro on the owner side; the watch-list
  is end-to-end encrypted, so a leaked token without the URL fragment reveals nothing.

## 3. Secrets and keys

- Secret Manager: FCM/Admin SDK runs via the workload service account (no JSON keys). Other secrets: the Anthropic API
  key and the entitlement-token signing key (ES256, rotated yearly; public key in `AppConfig`).
- Cloud KMS: key ring `sm-prod`, key `pii-v1` (90-day rotation) for envelope encryption of Play purchase tokens and
  the transient self-test FCM token. DEKs are cached in memory for 10 min.
- Service accounts per role (`api`, `worker`, `admin`, `rtdn-pusher`, `scheduler`) with least privilege. CI deploys via
  Workload Identity Federation from GitHub (no long-lived keys).

## 4. Data protection in storage and logs

- Cloud SQL: private IP + Cloud SQL connector, IAM DB auth for humans, `sslmode=verify-full`, encryption at rest
  (Google-managed; CMEK optional later). Automated backups encrypted.
- There are no profile columns to protect. Backup blobs and family watch-lists are end-to-end encrypted on the device
  (AES-256-GCM; the backup key lives in Google Block Store with `shouldBackupToCloud`, which Google encrypts
  end-to-end when the phone has a screen lock — the app offers backup only then). **Never logged**: request bodies,
  `purchase_token`, report `attributes`, client IPs on the unauthenticated endpoints.
- Analytics: no Firebase/Google Analytics SDK. Product metrics come from the opt-in, identifier-free
  `usage_counter` aggregates and Play Console statistics.
- **On the device**: Room in app-private storage under Android file-based encryption; `android:allowBackup` rules
  exclude Room (restores go through the user's own encrypted backup, not Android Auto Backup). No profile field is
  ever passed to the ads SDK, Crashlytics keys or logs.
- GCS buckets: uniform access, no public buckets (the CDN serves notice JSON from the API, not from a public bucket),
  exports bucket with 7-day lifecycle.

## 5. Admin console

- Separate Cloud Run service `admin`, **behind Identity-Aware Proxy** (Google accounts allowlist in `admin_user`;
  Google account 2-Step Verification enforced). No public route; IAP JWT verified in-app too (defence in depth).
- Roles: `owner` (publish, retract, promos, sources, user support lookups) and `reviewer` (draft and review; publish
  only if not the extractor-run initiator *and* not the first reviewer for second-review tasks).
- **Every admin action is in `audit_log`** with before/after JSON. Publishing and retraction need a typed confirmation;
  bulk actions (retract with alerts to > 10K users) need a second click after a 60 s cooldown.
- User support lookups show account id, entitlement and purchases only (there is no profile or phone to show).
- CSRF tokens on all forms (htmx), strict CSP (pdf.js self-hosted), no third-party scripts.

## 6. DPDP Act 2023 and DPDP Rules 2025

The Rules were notified in Nov 2025 with phased commencement: Board provisions immediately, consent managers about
Nov 2026, most data-fiduciary obligations from **13 May 2027** ([Mondaq](https://www.mondaq.com/india/data-protection/1708164/digital-personal-data-protection-rules-2025-notified),
[AZB](https://www.azbpartners.com/bank/update-indias-digital-personal-data-protection-framework-comes-into-effect/)).
We build to the full obligations from day one, because they are cheap at our scale. *Not legal advice; get a
review before launch.*

| Obligation | Implementation |
|---|---|
| Notice (itemised, plain language, in the app language) | **S47 privacy notice** in onboarding (after S03) and from S38/S39: what stays on the phone, what the server holds and why, ads, crash reports, rights, grievance contact, Board complaint route. Version shown is recorded on the device; for accounts also as `consent_log(privacy_notice)` |
| Consent: free, specific, informed, unambiguous; per purpose | Core matching and reminders process personal data **only on the device**, so no server-side consent is needed for them. Server consents (minimal `consent_log`): `privacy_notice` (at account creation), `cloud_backup`, `family_link_owner`, `family_link_member`. Device-only choices: crash reports (opt-in), usage counters (opt-in), notification permission explained by our own pre-prompt (S13), ads notice (B17). Withdrawal as easy as giving (S28/S38 toggles; `POST /v1/me/consents` for account purposes) |
| Purpose limitation and minimisation | Only eligibility attributes, on the device; no phone, name, Aadhaar, documents; optional fields marked optional; PwD asked with a "why we ask" line and "stays on your phone" |
| Accuracy | The user edits the profile at any time; re-match is local and instant |
| Security safeguards | §1–§5 |
| **Breach notification** | Runbook (operations.md): affected users and the Data Protection Board (detailed report within 72 h per Rule 7 as commonly read; *verify*). Scope is small: account ids, purchases, ciphertexts |
| Retention and erasure | data-model.md §4–5: local wipe; account delete with 7-day grace; inactive accounts; logs 30 days |
| Rights: access, correction, erasure, grievance, nomination | Access = on-device JSON export + `GET /v1/me` summary of server data; correction = profile edit; erasure = local wipe + `DELETE /v1/me`; in-app grievance contact (email, response ≤ 30 days *verify*); nomination via support |
| **Children (< 18)** | Govt-job aspirants can be 17 (Agniveer, NDA, 10th-pass posts). Instead of an 18+ gate (excludes them) or verifiable parental consent (heavy), **minor mode**: when the DOB on the device is < 18, the app works fully for matching and local/topic reminders but never creates a server account, never offers Google sign-in, purchase, trial, backup or family-owner links, sends no reports with attributes, no usage counters, no crash reports, and shows **no ads** (no tracking or targeted advertising of children, s.9(3)). We therefore receive no personal data of the child. FCM topic subscriptions and Play services remain Google's processing. At 18 the device unlocks these features with a note. *Legal check: confirm that an install-level FCM token handled by Google as processor, with no identity linkage on our side, is acceptable for under-18 users* |
| Cross-border transfer | Server data stays in India (asia-south1/asia-south2). Exceptions: FCM/Firebase Auth/Crashlytics/AdMob (Google global infra), Anthropic (only **public notice PDFs**). Listed in S47 |
| Processors | Google Cloud/Firebase (Auth, FCM, App Check, Crashlytics, Hosting), Google AdMob, Google Play, Anthropic (notices only). DPAs/terms recorded |

### 6.1 Play Data safety form (draft)

| Data type (Play category) | Collected? | Shared? | Purpose | Optional? |
|---|---|---|---|---|
| Profile: DOB, category, PwD, qualifications, state | **No** — processed on the device only | No | — | — |
| Email address / user IDs (Google sign-in → Firebase Auth uid) | Yes, if the user signs in | No | Account management | Yes |
| Purchase history | Yes, if the user buys | No | App functionality (entitlement) | Yes |
| Device or other IDs (Firebase installation id, FCM token for account pushes; AdMob app-instance id) | Yes | AdMob: per SDK disclosure | App functionality; advertising (non-personalised) | Ads: free tier, adults only |
| Crash logs, diagnostics | Yes (Crashlytics) | No | Analytics | Yes (opt-in) |
| App interactions | Aggregated, identifier-free counters | No | Analytics | Yes (opt-in) |
| Encrypted backup (profile + state) | Declare as "Other user-generated content", collected, encrypted in transit, **unless** Play confirms end-to-end encrypted data we cannot decrypt is out of scope | No | App functionality (restore) | Yes |

Encryption in transit: yes. Deletion request: yes (in-app, and a web form for uninstalled users).

## 7. Ads

PRD change of 2026-11-19: light native ads in the free tier only.

- Ads are rendered client-side by the GMA **Next-Gen** SDK (AdMob native). The backend stores no ad identifiers or
  impressions and sends no profile data to ad networks. **Profile attributes (category, PwD, DOB, domicile) are never
  passed as ad targeting keys.** Every request is **non-personalised**: a Google extras bundle with `npa=1`
  (`setGoogleExtrasBundle`, per the [Next-Gen migration guide](https://developers.google.com/admob/android/next-gen/migration/migrate-ad-requests)).
  There is no personalised-ads option in v1.
- **UMP** (User Messaging Platform) is integrated and called before the first ad request; it shows a form only where
  Google requires one (EEA/UK/CH, some US states — the app is distributed in India only, so normally
  `NOT_REQUIRED`). Ads load only when `canRequestAds()` is true. Our own disclosure is S47 + B17 ("Why do I see an
  ad?"); no extra consent purpose is stored.
- **No ads in minor mode** (DOB < 18 on the device).
- The app removes the `AD_ID` permission (frontend ads.md), so the advertising ID is not read. The SDK still
  collects app-instance identifiers and diagnostics; declare those in the Play Data safety form and in the privacy
  notice under processors (Google AdMob).
- Content safety: block categories that mimic official notices or prey on aspirants (job-placement "guarantees",
  paid "sarkari form" agents, loans, gambling, dating) in the AdMob console. The server-side kill switch
  (`AppConfig.ads.enabled`) removes all ads within about 15 min if an abusive ad slips through.
- Ads never appear in notifications, the what-you-need card or why-you-match (PRD), and are disabled for
  any Pro source including trial.

## 8. Backups and DR

- Cloud SQL automated daily backups + PITR (7 days). Weekly `pg_dump` (logical) to GCS in **asia-south2 (Delhi)**,
  CMEK, 35-day retention. A quarterly restore drill into staging is a runbook item.
- GCS: `sm-catalog` (public catalog files) is fully rebuildable from the database by the `catalog build` job.
- GCS: `sm-docs` (notice PDFs) dual-region or with a nightly copy to asia-south2. Losing PDFs is recoverable (the
  sources are public) but slow.
- RPO: ≤ 5 min (PITR); RTO: ≤ 2 h (restore to a new instance + Cloud Run env swap). HA (regional) instance at ≥ 300K
  users.
- Erasure vs backups: deleted users disappear from backups within 35 days. Restores replay the
  `deletion_requested_at` list (kept as hashed ids in a separate tiny table) to re-delete.
