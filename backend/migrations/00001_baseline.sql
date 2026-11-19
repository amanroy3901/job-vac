-- +goose Up
-- Baseline = docs/backend/schema.sql verbatim (goose markers added around the function).
-- internal/db tests assert this file stays identical to the doc once the markers are stripped.
-- Sarkari Match — PostgreSQL 16 schema (design draft, 2026-11-19, revised for the founder decisions of 2026-11-19)
-- Conventions
--   * IDs are prefixed ULIDs generated in the app ("u_", "n_", "p_", ...) stored as text.
--   * timestamptz everywhere; calendar dates that are "IST dates" are plain `date`.
--   * User-visible content is a LocalizedText jsonb map {"hi": "...", "en": "...", ...}; keys must be rows of `locale`
--     (CHECK via locale_keys_ok()). `en` is always present; publish requires every `launch` locale.
--   * PRIVACY MODEL (ADR-11): the eligibility profile, matches, reminders, actions, applications and the inbox live
--     ONLY on the user's phone. This database holds the public catalog, and for users who use a server feature
--     (Google sign-in, Pro/trial, family link, encrypted backup) a minimal account. No DOB, category, PwD, phone,
--     name, Aadhaar or documents are stored anywhere in it.
--   * Migrations via Flyway; this file is the V1 baseline.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SEQUENCE catalog_seq;                                 -- one value per published catalog batch

-- ---------------------------------------------------------------- enums
CREATE TYPE user_state      AS ENUM ('active','pending_deletion','deleted');
CREATE TYPE auth_kind_t     AS ENUM ('anonymous','google');
CREATE TYPE notice_status_t AS ENUM ('draft','upcoming','open','closed','cancelled');
CREATE TYPE ent_source_t    AS ENUM ('trial','play_season','play_year','play_month','referral','promo');

-- ---------------------------------------------------------------- locales (all major Indian languages; launch hi + en)
CREATE TABLE locale (
  code         text PRIMARY KEY,                              -- BCP-47 language: hi, en, bn, mr, te, ta, gu, kn, or, ml, pa, as, ur
  name_native  text NOT NULL,                                 -- हिन्दी, English, বাংলা, मराठी, తెలుగు, தமிழ், ગુજરાતી, ಕನ್ನಡ, ଓଡ଼ିଆ, മലയാളം, ਪੰਜਾਬੀ, অসমীয়া, اردو
  script       text NOT NULL,                                 -- ISO 15924: Deva, Latn, Beng, Telu, Taml, Gujr, Knda, Orya, Mlym, Guru, Arab
  rtl          boolean NOT NULL DEFAULT false,                -- ur = true
  status       text NOT NULL DEFAULT 'planned' CHECK (status IN ('launch','beta','planned')),
  fallback     text[] NOT NULL,                               -- resolution order after the locale itself, e.g. mr → {hi,en}; ta → {en,hi}
  sort         int NOT NULL DEFAULT 0
);
INSERT INTO locale (code, name_native, script, rtl, status, fallback, sort) VALUES
  ('hi','हिन्दी','Deva',false,'launch','{en}',1),
  ('en','English','Latn',false,'launch','{hi}',2),
  ('bn','বাংলা','Beng',false,'planned','{en,hi}',3),
  ('mr','मराठी','Deva',false,'planned','{hi,en}',4),
  ('te','తెలుగు','Telu',false,'planned','{en,hi}',5),
  ('ta','தமிழ்','Taml',false,'planned','{en,hi}',6),
  ('gu','ગુજરાતી','Gujr',false,'planned','{hi,en}',7),
  ('kn','ಕನ್ನಡ','Knda',false,'planned','{en,hi}',8),
  ('or','ଓଡ଼ିଆ','Orya',false,'planned','{en,hi}',9),
  ('ml','മലയാളം','Mlym',false,'planned','{en,hi}',10),
  ('pa','ਪੰਜਾਬੀ','Guru',false,'planned','{hi,en}',11),
  ('as','অসমীয়া','Beng',false,'planned','{bn,en,hi}',12),
  ('ur','اردو','Arab',true,'planned','{en,hi}',13);

-- true when every key of a LocalizedText map is a known locale and `en` is present (STABLE: reads `locale`)
-- +goose StatementBegin
CREATE FUNCTION locale_keys_ok(t jsonb) RETURNS boolean LANGUAGE sql STABLE AS $$
  SELECT t IS NULL OR (jsonb_typeof(t) = 'object' AND t ? 'en'
         AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(t) k WHERE k NOT IN (SELECT code FROM locale)))
$$;
-- +goose StatementEnd

-- Translation work for non-launch locales (LLM draft + human review), per catalog text field.
CREATE TABLE translation_task (
  id           bigserial PRIMARY KEY,
  entity       text NOT NULL,                                 -- notice_version | post | lifecycle_event | ref_item | notification_text
  entity_id    text NOT NULL,
  field_path   text NOT NULL,                                 -- e.g. content.title, rule.qualification.text
  locale       text NOT NULL REFERENCES locale(code),
  draft        text,
  state        text NOT NULL DEFAULT 'queued' CHECK (state IN ('queued','drafted','reviewed','published','skipped')),
  reviewer     text,
  updated_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (entity, entity_id, field_path, locale)
);

-- Server-composed notification text (only for pushes sent to one account's devices: trial ending, Pro expiry,
-- payment hold, family link events). Match/reminder/lifecycle pushes are composed on the device from app resources.
CREATE TABLE notification_text (
  template   text NOT NULL,                                   -- design id, e.g. N-31
  locale     text NOT NULL REFERENCES locale(code),
  title      text NOT NULL,
  body       text NOT NULL,                                   -- {{placeholders}}
  version    int  NOT NULL DEFAULT 1,
  PRIMARY KEY (template, locale)
);

-- ---------------------------------------------------------------- accounts (only for users of a server feature)
CREATE TABLE app_user (
  id                   text PRIMARY KEY,                    -- u_...
  firebase_uid         text NOT NULL UNIQUE,
  auth_kind            auth_kind_t NOT NULL,                -- anonymous (guest who bought/joined) | google (linked)
  state                user_state NOT NULL DEFAULT 'active',
  locale               text NOT NULL DEFAULT 'hi' REFERENCES locale(code),   -- for server-composed pushes + Problem.message
  billing_account_ref  uuid NOT NULL DEFAULT gen_random_uuid(),  -- Play obfuscatedAccountId (no PII)
  referral_code        text NOT NULL UNIQUE,
  created_at           timestamptz NOT NULL DEFAULT now(),
  last_seen_at         timestamptz,
  deletion_requested_at timestamptz,
  hard_delete_at       timestamptz
);
CREATE INDEX app_user_hard_delete ON app_user (hard_delete_at) WHERE state = 'pending_deletion';
-- No email is copied here: Firebase Auth keeps the Google identity; we keep only the uid.

CREATE TABLE device (                                       -- installs of an account (not of guests without an account)
  installation_id      text PRIMARY KEY,                    -- Firebase Installations ID
  user_id              text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  fcm_token            text NOT NULL,
  locale               text NOT NULL REFERENCES locale(code),
  app_version          int NOT NULL,
  device_label         text,                                -- "Redmi 12", for N-47 / S38 (owner-visible only)
  is_primary           boolean NOT NULL DEFAULT false,      -- the account's alert install (B15); others go quiet
  token_invalid_at     timestamptz,                         -- FCM UNREGISTERED
  updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX device_user ON device (user_id);
CREATE UNIQUE INDEX device_one_primary ON device (user_id) WHERE is_primary;

CREATE TABLE consent_log (                                  -- append-only, minimal (DPDP s.6 proof for account features)
  id          bigserial PRIMARY KEY,
  user_id     text NOT NULL,                                -- no FK: survives deletion as pseudonymous proof until retention ends
  purpose     text NOT NULL CHECK (purpose IN ('privacy_notice','cloud_backup','family_link_owner',
                                               'family_link_member','account_deleted')),
  notice_version text NOT NULL,                             -- privacy notice / S47 version shown
  granted     boolean NOT NULL,
  locale      text NOT NULL REFERENCES locale(code),
  recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX consent_user ON consent_log (user_id, purpose, recorded_at DESC);
-- Device-only choices (notification explanation seen, crash reports, usage counters, ads notice) are recorded
-- on the device and never sent here.

CREATE TABLE backup_blob (                                  -- opt-in, end-to-end encrypted; we cannot read it
  user_id      text PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  version      bigint NOT NULL DEFAULT 1,                   -- compare-and-swap via If-Match
  ciphertext   bytea NOT NULL CHECK (octet_length(ciphertext) <= 65536),
  key_id       text NOT NULL,                               -- id of the device-held key (Block Store), not the key
  updated_at   timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- reference data
CREATE TABLE ref_item (                                     -- categories, states, exam types, pwd types, extras, orgs
  kind      text NOT NULL,
  code      text NOT NULL,
  name      jsonb NOT NULL CHECK (locale_keys_ok(name)),     -- LocalizedText
  sort      int NOT NULL DEFAULT 0,
  deprecated boolean NOT NULL DEFAULT false,
  PRIMARY KEY (kind, code)
);
CREATE TABLE qualification (                                -- taxonomy tree
  code       text PRIMARY KEY,                              -- grad.ba, class12.science, diploma.engg.civil
  parent     text REFERENCES qualification(code),
  level      int  NOT NULL,                                 -- 10,20,25,30,40,50,60
  name       jsonb NOT NULL CHECK (locale_keys_ok(name)),    -- LocalizedText
  aliases    text[] NOT NULL DEFAULT '{}',                  -- search aliases in any script (B.A., बीए, বি.এ.)
  selectable boolean NOT NULL DEFAULT true
);

-- ---------------------------------------------------------------- sources & ingestion
CREATE TABLE source (
  id               text PRIMARY KEY,                        -- ssc_latest, rrb_ald_notices, upprpb_notices
  org_code         text NOT NULL,
  url              text NOT NULL,
  kind             text NOT NULL,                           -- html_list|rss|pdf_index|json_api
  extractor        jsonb NOT NULL,                          -- css selector / regex config for links
  interval_minutes int NOT NULL DEFAULT 30,
  enabled          boolean NOT NULL DEFAULT true,
  etag             text,
  last_modified    text,
  content_hash     bytea,
  last_fetch_at    timestamptz,
  last_ok_at       timestamptz,
  consecutive_failures int NOT NULL DEFAULT 0
);

CREATE TABLE source_document (
  id            text PRIMARY KEY,                           -- d_...
  source_id     text REFERENCES source(id),
  url           text NOT NULL,
  sha256        bytea NOT NULL UNIQUE,                      -- content addressed; same PDF twice = one row
  gcs_path      text NOT NULL,
  content_type  text NOT NULL,
  pages         int,
  text_quality  text,                                       -- unicode_ok|legacy_font|scanned|mixed
  language      text,                                       -- source language(s): hi|en|bilingual|<locale code>
  doc_class     text,                                       -- new_notice|corrigendum|extension|admit_card|result|answer_key|exam_date|irrelevant
  recruitment_id text,
  status        text NOT NULL DEFAULT 'new',                -- new|classified|extracted|in_review|published|ignored
  first_seen_at timestamptz NOT NULL DEFAULT now(),
  link_text     text
);

CREATE TABLE extraction_run (
  id              text PRIMARY KEY,
  source_document_id text NOT NULL REFERENCES source_document(id),
  stage           text NOT NULL,                            -- classify|extract|verify
  model           text NOT NULL,
  prompt_version  text NOT NULL,
  input_tokens    int, output_tokens int,
  cost_usd        numeric(8,4),
  output          jsonb,
  validation      jsonb,                                    -- validator findings
  status          text NOT NULL,                            -- ok|invalid|error
  created_at      timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- notices, posts, rules
CREATE TABLE recruitment (                                  -- "notice" in the API
  id            text PRIMARY KEY,                           -- n_...
  org_code      text NOT NULL,
  advt_no       text,
  exam_type     text NOT NULL,
  scope         text NOT NULL CHECK (scope IN ('all_india','state')),
  state         text,
  status        notice_status_t NOT NULL DEFAULT 'draft',
  current_version int NOT NULL DEFAULT 0,
  apply_end     date,                                       -- denormalized from current version
  catalog_seq   bigint,                                     -- batch that last changed this notice
  new_match_paused boolean NOT NULL DEFAULT false,          -- 3+ wrong-match reports → devices don't notify new matches
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (org_code, advt_no)
);
CREATE INDEX recruitment_open ON recruitment (status, apply_end);

CREATE TABLE notice_version (                               -- immutable snapshots
  recruitment_id text NOT NULL REFERENCES recruitment(id),
  version        int  NOT NULL,
  kind           text NOT NULL,                             -- original|corrigendum|extension|edit|cancellation
  source_document_id text REFERENCES source_document(id),
  content        jsonb NOT NULL,                            -- API Notice shape minus posts; every text a LocalizedText map
                                                            -- (title, short_title, fee notes, documents, specs, links labels)
  provenance     jsonb NOT NULL,                            -- field path -> {doc, page}
  change_summary jsonb,                                     -- [{field, before, after, text: LocalizedText}]
  reviewed_by    text NOT NULL,
  second_reviewer text,
  review_minutes int,
  published_at   timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (recruitment_id, version)
);

CREATE TABLE post (
  id             text PRIMARY KEY,                          -- p_...
  recruitment_id text NOT NULL REFERENCES recruitment(id),
  code           text NOT NULL,                             -- notice's own post code if any
  name           jsonb NOT NULL CHECK (locale_keys_ok(name)),        -- LocalizedText
  short_name     jsonb CHECK (locale_keys_ok(short_name)),            -- ≤ 32 chars per locale, notification titles
  exam_tags      text[] NOT NULL,                           -- matched on the device against the local profile
  vacancies      jsonb,                                     -- {total, by_category{}, by_state{}}
  current_rule_version int NOT NULL DEFAULT 0,
  active         boolean NOT NULL DEFAULT true,
  UNIQUE (recruitment_id, code)
);

CREATE TABLE post_rule (                                    -- immutable; rule = rule.schema.json document
  post_id        text NOT NULL REFERENCES post(id),
  version        int  NOT NULL,
  notice_version int  NOT NULL,
  rule           jsonb NOT NULL,                            -- full document incl. review/quotes (admin only)
  public_rule    jsonb NOT NULL,                            -- what the catalog ships: review + source.quote stripped
  evaluator_min_version int NOT NULL DEFAULT 1,
  risk_flags     text[] NOT NULL DEFAULT '{}',
  published_by   text NOT NULL,
  published_at   timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (post_id, version)
);

CREATE TABLE lifecycle_event (
  id             text PRIMARY KEY,                          -- e_...
  recruitment_id text NOT NULL REFERENCES recruitment(id),
  post_ids       text[],                                    -- null = all posts
  regions        text[],                                    -- RRB/zone codes when region-specific
  stage          text,                                      -- tier1, cbt1, pet, dv
  type           text NOT NULL,                             -- LifecycleEventType
  status         text NOT NULL,                             -- expected|announced|live|past|cancelled
  title          jsonb NOT NULL CHECK (locale_keys_ok(title)),
  body           jsonb CHECK (locale_keys_ok(body)),
  starts_at      timestamptz,
  ends_at        timestamptz,
  expected_window text,
  links          jsonb NOT NULL DEFAULT '[]',
  critical       boolean NOT NULL DEFAULT false,
  supersedes     text REFERENCES lifecycle_event(id),       -- reschedule chain
  source_document_id text REFERENCES source_document(id),
  published_by   text,
  published_at   timestamptz,
  catalog_seq    bigint
);
CREATE INDEX lifecycle_by_recruitment ON lifecycle_event (recruitment_id, starts_at);

CREATE TABLE review_task (
  id             text PRIMARY KEY,
  recruitment_id text REFERENCES recruitment(id),
  source_document_id text NOT NULL REFERENCES source_document(id),
  draft          jsonb NOT NULL,                            -- extractor output being edited
  risk_flags     text[] NOT NULL DEFAULT '{}',
  needs_second_review boolean NOT NULL DEFAULT false,
  state          text NOT NULL DEFAULT 'open',              -- open|in_progress|awaiting_second|published|rejected
  assignee       text,
  opened_at      timestamptz NOT NULL DEFAULT now(),
  published_at   timestamptz,
  active_seconds int NOT NULL DEFAULT 0                     -- curation-time metric (≤30 min target)
);

-- ---------------------------------------------------------------- public catalog (CDN files; ADR-11)
CREATE TABLE catalog_batch (                                -- one per publish; immutable once written
  seq           bigint PRIMARY KEY DEFAULT nextval('catalog_seq'),
  notice_ids    text[] NOT NULL,                            -- notices whose new full version is in this batch
  removed_notice_ids text[] NOT NULL DEFAULT '{}',
  reason        text NOT NULL,                              -- publish|corrigendum|extension|cancel|lifecycle|retract|translation
  gcs_prefix    text NOT NULL,                              -- gs://…-catalog/batches/{seq}/ (one object per locale set)
  published_at  timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE catalog_snapshot (                             -- rebuilt nightly and after large batches
  seq           bigint PRIMARY KEY REFERENCES catalog_batch(seq),
  gcs_prefix    text NOT NULL,
  notice_count  int NOT NULL,
  built_at      timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- family links (P1; no phone numbers)
-- The aspirant shares an invite link / QR: https://sarkarimatch.in/f/{token}#k={key}. The key is in the URL fragment
-- and never reaches us; the watch-list is encrypted with it on the aspirant's phone.
CREATE TABLE family_link (
  id                text PRIMARY KEY,                       -- f_...
  owner_user_id     text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  member_user_id    text REFERENCES app_user(id) ON DELETE CASCADE,
  state             text NOT NULL DEFAULT 'invited' CHECK (state IN ('invited','active','removed','expired')),
  invite_token_hash bytea NOT NULL UNIQUE,                  -- sha256(token)
  invite_expires_at timestamptz NOT NULL,
  created_at        timestamptz NOT NULL DEFAULT now(),
  accepted_at       timestamptz,
  ended_at          timestamptz
);
CREATE INDEX family_owner ON family_link (owner_user_id) WHERE state IN ('invited','active');
CREATE INDEX family_member ON family_link (member_user_id) WHERE state = 'active';

CREATE TABLE family_watchlist (                             -- latest encrypted watch-list per link (overwritten)
  link_id     text PRIMARY KEY REFERENCES family_link(id) ON DELETE CASCADE,
  version     bigint NOT NULL,
  ciphertext  bytea NOT NULL CHECK (octet_length(ciphertext) <= 32768),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- push broadcasts (topic sends; no recipients known)
CREATE TABLE topic_send (
  id          text PRIMARY KEY,                             -- deterministic: sync:{seq} | r:{post}:{kind}:{YYYYMMDD} | l:{event}
  topic       text NOT NULL,                                -- catalog | p_{post_id} | n_{notice_id}
  kind        text NOT NULL,                                -- PushData.type
  due_at      timestamptz NOT NULL,
  state       text NOT NULL DEFAULT 'queued' CHECK (state IN ('queued','sent','failed','cancelled')),
  attempts    int NOT NULL DEFAULT 0,
  fcm_message_id text,
  payload     jsonb NOT NULL,
  sent_at     timestamptz
);
CREATE INDEX topic_send_due ON topic_send (due_at) WHERE state = 'queued';

CREATE TABLE push_test (                                    -- delivery self-test; token kept only until the test ends
  id           text PRIMARY KEY,                            -- t_...
  fcm_token_enc bytea,                                      -- nulled when status leaves 'sent'
  status       text NOT NULL,                               -- sent|received|timeout|fcm_error
  sent_at      timestamptz NOT NULL DEFAULT now(),
  received_at  timestamptz,
  fcm_error    text
);

-- ---------------------------------------------------------------- payments & entitlements
CREATE TABLE product (
  id              text PRIMARY KEY,                         -- pro_season, pro_year
  kind            text NOT NULL,
  play_product_id text NOT NULL,
  play_base_plan_id text NOT NULL,
  duration_days   int NOT NULL,
  active          boolean NOT NULL DEFAULT true
);

CREATE TABLE purchase (
  id                  text PRIMARY KEY,
  user_id             text REFERENCES app_user(id) ON DELETE SET NULL,  -- kept (financial record) after deletion, unlinked
  provider            text NOT NULL DEFAULT 'play' CHECK (provider = 'play'),
  product_id          text NOT NULL REFERENCES product(id),
  token_hash          bytea NOT NULL UNIQUE,                -- sha256(purchaseToken)
  token_enc           bytea,
  linked_token_hash   bytea,                                -- Play linkedPurchaseToken (upgrade/resubscribe)
  order_id            text,
  state               text NOT NULL,                        -- pending|active|in_grace|on_hold|paused|canceled|expired|revoked|refunded
  track               text,                                 -- Play track at purchase (closed|production) — validation test metric
  expiry_at           timestamptz,
  auto_renewing       boolean,
  acknowledged_at     timestamptz,
  last_verified_at    timestamptz,
  raw                 jsonb,                                -- last provider response (no PII)
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE entitlement_grant (
  id          bigserial PRIMARY KEY,
  user_id     text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  source      ent_source_t NOT NULL,
  starts_at   timestamptz NOT NULL,
  ends_at     timestamptz NOT NULL,
  purchase_id text REFERENCES purchase(id),
  revoked_at  timestamptz,
  note        text,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX grant_user ON entitlement_grant (user_id, ends_at DESC);
CREATE UNIQUE INDEX one_trial_per_user ON entitlement_grant (user_id) WHERE source = 'trial';

CREATE TABLE user_entitlement (                             -- materialized from grants; the ONLY thing features read
  user_id       text PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  plan          text NOT NULL,                              -- free|pro
  status        text NOT NULL,                              -- active|in_grace|on_hold|expired|none
  source        ent_source_t,
  starts_at     timestamptz,
  expires_at    timestamptz,
  auto_renewing boolean NOT NULL DEFAULT false,
  trial_used    boolean NOT NULL DEFAULT false,
  updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX entitlement_expiry ON user_entitlement (expires_at) WHERE plan = 'pro';

CREATE TABLE referral_redemption (
  referee_user_id  text PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  referrer_user_id text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  redeemed_at      timestamptz NOT NULL DEFAULT now(),
  rewarded_at      timestamptz                              -- when referee completed profile / first Applied
);

-- ---------------------------------------------------------------- quality, sync, ops
CREATE TABLE match_report (                                -- POST /v1/reports; no account needed, no user id stored
  id           text PRIMARY KEY,
  post_id      text NOT NULL REFERENCES post(id),
  rule_version int NOT NULL,
  reason       text NOT NULL,
  rule         text,
  comment      text CHECK (char_length(comment) <= 500),
  attributes   jsonb,                                       -- ONLY if the user ticked "send the details needed to check this"
  app_version  int,
  state        text NOT NULL DEFAULT 'received',
  resolution   text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  resolved_at  timestamptz
);
CREATE INDEX report_open ON match_report (post_id) WHERE state = 'received';

CREATE TABLE usage_counter (                                -- opt-in, identifier-free daily aggregates (no install/user id)
  ist_date     date NOT NULL,
  app_version  int  NOT NULL,
  locale       text NOT NULL REFERENCES locale(code),
  counter      text NOT NULL,                               -- onboarding_completed, match_shown, applied, notif_blocked, ...
  value        bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (ist_date, app_version, locale, counter)
);

CREATE TABLE idempotency_key (
  user_id        text NOT NULL,                             -- account id, or 'inst:'||installation id for unauthenticated calls
  key            uuid NOT NULL,
  request_hash   bytea NOT NULL,
  status         int,
  response       jsonb,
  created_at     timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, key)
);

CREATE TABLE job (                                          -- generic durable jobs (extract, catalog build, translate, deletion)
  id           bigserial PRIMARY KEY,
  kind         text NOT NULL,
  payload      jsonb NOT NULL,
  dedupe_key   text UNIQUE,
  run_at       timestamptz NOT NULL DEFAULT now(),
  state        text NOT NULL DEFAULT 'queued',              -- queued|running|done|failed|dead
  attempts     int NOT NULL DEFAULT 0,
  lease_until  timestamptz,
  last_error   text,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX job_ready ON job (run_at) WHERE state = 'queued';

CREATE TABLE admin_user (
  email   text PRIMARY KEY,
  role    text NOT NULL CHECK (role IN ('owner','reviewer'))
);

CREATE TABLE audit_log (                                    -- append-only; admin + security-relevant user events
  id         bigserial PRIMARY KEY,
  actor      text NOT NULL,                                 -- admin email | user id | 'system'
  action     text NOT NULL,                                 -- publish_notice, retract_post, grant_promo, delete_account...
  entity     text,
  entity_id  text,
  before     jsonb,
  after      jsonb,
  at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_entity ON audit_log (entity, entity_id);

-- +goose Down
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
