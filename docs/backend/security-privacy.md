# Security and privacy

Threat model in one line: we hold modest but real personal data (DOB, category, PwD and ex-serviceman status, phone
numbers) for up to 1M Indians. We send messages that cost money (OTP, WhatsApp). We sell a subscription. And we
publish eligibility claims people make life decisions on. The main risks are **OTP/SMS pumping and WhatsApp cost
abuse, account takeover via phone, scraping/abuse of the API, admin-console compromise publishing wrong rules, PII
leakage via logs or backups, and DPDP non-compliance.**

## 1. Client and API protection

| Control | Detail |
|---|---|
| **App Check (Play Integrity provider)** | Required on every `/v1/**` call. Verified in a Ktor plugin with the Firebase Admin SDK (JWKS cached). Replay protection (limited-use tokens) on `/v1/auth/otp:send`, `/v1/eligibility/check`, `/v1/me/purchases/*`, referral redeem. Debug provider tokens accepted only in staging. Rollout: monitor-only for 2 weeks, then enforce (old Play Services devices fail; track the reject rate by OEM) |
| **Firebase ID token** | `Authorization: Bearer`, verified locally (RS256, `aud`/`iss`, `exp`, `auth_time`), revocation checked on sensitive operations (delete account, phone change) |
| **Rate limits** | Token buckets in the API process, keyed by user, installation, IP /24 and phone HMAC (OTP). Limits are in [api/README.md §7](../api/README.md#7-pagination--limits). At scale (> 100K users) add Cloud Armor rate-based rules in front (needs an external HTTPS LB, ~$18+/mo). Abusive installations are blocked via a `blocked_installation` flag |
| **Input validation** | Generated DTO validation from OpenAPI; size limits (64 KB body; batch ≤ 50 actions); enums enforced |
| **Transport** | TLS 1.2+ (Firebase Hosting / Cloud Run managed certs); HSTS; no cleartext. App: network security config with no user CAs in release (*certificate pinning not used*: Google rotates certs; App Check covers the intent) |
| **Output** | `Cache-Control: private, no-store` on all `/v1/me/**`; `public` only for notice/reference/config/health. CDN never caches authorized responses |
| **Webhooks** | `/hooks/whatsapp`: `X-Hub-Signature-256` HMAC. `/hooks/razorpay`: `X-Razorpay-Signature`. `/hooks/play-rtdn`: Pub/Sub OIDC JWT (audience + service account email). `/internal/tick/*`: Cloud Scheduler OIDC JWT, and it is served only by the worker service with ingress `internal-and-cloud-load-balancing` + IAM `roles/run.invoker` |

## 2. OTP and messaging abuse

SMS pumping (attackers trigger OTPs to premium numbers) is the main cost risk of any OTP flow.

- WhatsApp authentication template first (no SMS-pumping revenue for fraudsters), SMS only as fallback.
- Only `+91[6-9]\d{9}`. App Check limited-use tokens required. Phone Number Hint API in the app reduces typos.
- Limits: 3 sends per number per 15 min, 5 per day; 10 per installation per day; 50 per IP /24 per hour; a global
  daily OTP budget (config, default 2,000/day at launch) with an alert at 50% and a hard stop at 100%. The hard stop
  degrades to "try again later"; users can still use the app anonymously.
- OTP: 6 digits, stored as HMAC, 10-min TTL, 5 attempts max, constant-time compare; sessions bound to `firebase_uid`
  + `installation_id`.
- **Account restore by phone** (custom token into an existing account) is the takeover vector. Mitigations: it needs
  OTP to the number; a notification is sent to the old primary device ("Your account was signed in on a new phone",
  B15); the old device is demoted; changing the phone number requires OTP to the *old* number, or a 7-day delay with
  notice to both.
- WhatsApp alert cost is bounded per phone HMAC per 120 days (30 messages), not per account.

## 3. Secrets and keys

- Secret Manager: FCM/Admin SDK runs via the workload service account (no JSON keys). Other secrets: Meta access
  token, Razorpay keys and webhook secret, SMS gateway key, the Anthropic API key, the phone HMAC pepper.
- Cloud KMS: key ring `sm-prod`, key `pii-v1` (90-day rotation) for envelope encryption of phone numbers and purchase
  tokens. DEKs are cached in memory for 10 min. Rotation re-wraps lazily; `*_enc` has a key-version prefix.
- Service accounts per role (`api`, `worker`, `admin`, `rtdn-pusher`, `scheduler`) with least privilege. CI deploys via
  Workload Identity Federation from GitHub (no long-lived keys).

## 4. Data protection in storage and logs

- Cloud SQL: private IP + Cloud SQL connector, IAM DB auth for humans, `sslmode=verify-full`, encryption at rest
  (Google-managed; CMEK optional later). Automated backups encrypted.
- PII columns: phones and family phones are encrypted (AES-GCM) + HMAC; DOB/category/PwD are plaintext in the DB
  (needed for indexed matching), protected by access control. **Never logged**: the logger has a deny-list
  (`dob`, `phone`, `pwd*`, `category`, `qualifications`, `purchase_token`, `code`) and request bodies are not logged.
- Analytics: no third-party analytics SDK receives profile attributes. Product metrics are computed in-DB or exported
  to BigQuery with pseudonymous ids and coarse attributes only.
- GCS buckets: uniform access, no public buckets (the CDN serves notice JSON from the API, not from a public bucket),
  exports bucket with 7-day lifecycle.

## 5. Admin console

- Separate Cloud Run service `admin`, **behind Identity-Aware Proxy** (Google accounts allowlist in `admin_user`;
  Google account 2-Step Verification enforced). No public route; IAP JWT verified in-app too (defence in depth).
- Roles: `owner` (publish, retract, promos, sources, user support lookups) and `reviewer` (draft and review; publish
  only if not the extractor-run initiator *and* not the first reviewer for second-review tasks).
- **Every admin action is in `audit_log`** with before/after JSON. Publishing and retraction need a typed confirmation;
  bulk actions (retract with alerts to > 10K users) need a second click after a 60 s cooldown.
- User support lookups show masked phone and profile only; no export from the console.
- CSRF tokens on all forms (htmx), strict CSP (pdf.js self-hosted), no third-party scripts.

## 6. DPDP Act 2023 and DPDP Rules 2025

The Rules were notified in Nov 2025 with phased commencement: Board provisions immediately, consent managers about
Nov 2026, most data-fiduciary obligations from **13 May 2027** ([Mondaq](https://www.mondaq.com/india/data-protection/1708164/digital-personal-data-protection-rules-2025-notified),
[AZB](https://www.azbpartners.com/bank/update-indias-digital-personal-data-protection-framework-comes-into-effect/)).
We build to the full obligations from day one, because they are cheap at our scale. *Not legal advice; get a
review before launch.*

| Obligation | Implementation |
|---|---|
| Notice (itemised, plain language, Hindi + English) | First-run privacy notice screen (S03) + `terms_privacy` consent version; lists data, purposes, rights, grievance contact, Board complaint route |
| Consent: free, specific, informed, unambiguous; per purpose | `consent_log` per purpose: `eligibility_processing` (required for the service), `push_alerts`, `whatsapp_alerts`, `family_alerts`, `analytics` (optional, off by default). Withdrawal as easy as giving: toggles in S28/S33/S38 → `POST /v1/me/consents` |
| Purpose limitation and minimisation | Only eligibility attributes; no name/Aadhaar/docs; optional fields marked optional; PwD collected only with a "why we ask" line |
| Accuracy | User edits profile anytime; re-match on save |
| Security safeguards | §1–§5; encryption, access control, logs, backups |
| **Breach notification** | Runbook (operations.md): inform affected users without delay and the Data Protection Board (detailed report within 72 h of awareness per the Rules' reading in most commentaries; *verify Rule 7*) |
| Retention and erasure | data-model.md §4–5: delete on request (7-day grace), inactive 24 months → delete, alerts 90 days, logs 30 days |
| Rights: access, correction, erasure, grievance, nomination | Export (`/v1/me/exports`), profile edit, delete, in-app grievance contact (support WhatsApp/email, response ≤ 30 days *verify*), nomination: a support-handled request (rare) |
| Children (< 18): verifiable parental consent | **Open question.** Proposal: the service is 18+; if DOB < 18, no account is created (eligibility preview only, nothing stored), and a gentle message suggests a parent create the account. Needs legal confirmation |
| Cross-border transfer | Data stays in India (asia-south1/asia-south2). Exceptions: FCM/Firebase Auth (Google global infra), Meta WhatsApp, the LLM provider (only **public notice PDFs**, never user data), Sentry (scrubbed errors; EU/US region; *consider self-hosting or disabling PII*). Listed in the privacy notice |
| Processors | Google Cloud/Firebase, Meta, Razorpay, SMS gateway, Anthropic (notices only), Sentry. DPAs/terms recorded |

## 7. Ads

PRD change of 2026-11-19: light native ads in the free tier only.

- Ads are rendered client-side by the Google Mobile Ads SDK (AdMob). The backend stores no ad identifiers or
  impressions and sends no profile data to ad networks. **Profile attributes (category, PwD, DOB, domicile) are never
  passed as ad targeting keys.** Request **non-personalised ads** by default (`npa=1`). Personalised ads only with an
  explicit `ads_personalisation` consent purpose, if we ever want it. Default is off.
- The app removes the `AD_ID` permission (frontend ads.md), so the advertising ID is not read. The SDK still
  collects app-instance identifiers and diagnostics; declare those in the Play Data safety form and in the privacy
  notice under processors (Google AdMob).
- Content safety: block categories that mimic official notices or prey on aspirants (job-placement "guarantees",
  paid "sarkari form" agents, loans, gambling, dating) in the AdMob console. The server-side kill switch
  (`AppConfig.ads.enabled`) removes all ads within about 15 min if an abusive ad slips through.
- Ads never appear in notifications, WhatsApp, the what-you-need card or why-you-match (PRD), and are disabled for
  any Pro source including trial.

## 8. Backups and DR

- Cloud SQL automated daily backups + PITR (7 days). Weekly `pg_dump` (logical) to GCS in **asia-south2 (Delhi)**,
  CMEK, 35-day retention. A quarterly restore drill into staging is a runbook item.
- GCS: `sm-docs` (notice PDFs) dual-region or with a nightly copy to asia-south2. Losing PDFs is recoverable (the
  sources are public) but slow.
- RPO: ≤ 5 min (PITR); RTO: ≤ 2 h (restore to a new instance + Cloud Run env swap). HA (regional) instance at ≥ 300K
  users.
- Erasure vs backups: deleted users disappear from backups within 35 days. Restores replay the
  `deletion_requested_at` list (kept as hashed ids in a separate tiny table) to re-delete.
