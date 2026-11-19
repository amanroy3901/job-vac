-- Sarkari Match — PostgreSQL 16 schema (design draft, 2026-11-19)
-- Conventions
--   * IDs are prefixed ULIDs generated in the app ("u_", "m_", "n_", ...) stored as text.
--   * timestamptz everywhere; calendar dates that are "IST dates" are plain `date`.
--   * Syncable rows carry change_seq (from global sequence sync_seq) for delta sync.
--   * PII columns: *_enc = AES-256-GCM ciphertext (envelope key in Cloud KMS);
--     *_hmac = HMAC-SHA256(pepper, normalized value) for equality lookup. No plaintext phones.
--   * Migrations via Flyway; this file is the V1 baseline.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SEQUENCE sync_seq;

-- ---------------------------------------------------------------- enums
CREATE TYPE user_state      AS ENUM ('active','pending_deletion','deleted');
CREATE TYPE gender_t        AS ENUM ('male','female','transgender');
CREATE TYPE category_t      AS ENUM ('gen','ews','obc','sc','st');
CREATE TYPE verdict_t       AS ENUM ('eligible','not_eligible','check_yourself');
CREATE TYPE match_status_t  AS ENUM ('new','seen','reminded','snoozed','applied','not_interested','expired','withdrawn');
CREATE TYPE channel_t       AS ENUM ('push','whatsapp','sms');
CREATE TYPE delivery_status AS ENUM ('queued','sent','delivered','read','failed','skipped','suppressed');
CREATE TYPE notice_status_t AS ENUM ('draft','upcoming','open','closed','cancelled');
CREATE TYPE ent_source_t    AS ENUM ('trial','play_season','play_year','play_month','upi','referral','promo');

-- ---------------------------------------------------------------- users & profile
CREATE TABLE app_user (
  id                   text PRIMARY KEY,                    -- u_...
  firebase_uid         text NOT NULL UNIQUE,
  state                user_state NOT NULL DEFAULT 'active',
  locale               text NOT NULL DEFAULT 'hi' CHECK (locale IN ('hi','en')),
  phone_hmac           bytea UNIQUE,                        -- verified phone (optional)
  phone_enc            bytea,
  phone_last2          text,                                -- for masked display only
  phone_verified_at    timestamptz,
  google_linked        boolean NOT NULL DEFAULT false,
  billing_account_ref  uuid NOT NULL DEFAULT gen_random_uuid(),  -- Play obfuscatedAccountId
  referral_code        text NOT NULL UNIQUE,
  install_referrer     text,
  created_at           timestamptz NOT NULL DEFAULT now(),
  last_seen_at         timestamptz,
  deletion_requested_at timestamptz,
  hard_delete_at       timestamptz
);
CREATE INDEX app_user_hard_delete ON app_user (hard_delete_at) WHERE state = 'pending_deletion';

CREATE TABLE profile (
  user_id              text PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  version              int  NOT NULL DEFAULT 1,
  dob                  date NOT NULL,
  gender               gender_t NOT NULL,
  category             category_t NOT NULL,
  obc_non_creamy_layer boolean,
  domicile_state       text NOT NULL,                       -- 'UP'
  pwd                  boolean NOT NULL DEFAULT false,
  pwd_type             text,
  esm                  boolean NOT NULL DEFAULT false,
  esm_service_months   int,
  esm_release_date     date,
  govt_employee        jsonb,
  marital_status       text,
  qualifications       jsonb NOT NULL,                      -- [{code,status,result_date,percentage_band}]
  extra_attributes     text[] NOT NULL DEFAULT '{}',
  extras_declared      boolean NOT NULL DEFAULT false,      -- user answered "and more qualifications" step
  certificates         jsonb,
  interest_exam_types  text[] NOT NULL,
  job_states           text[] NOT NULL DEFAULT '{}',
  include_all_india    boolean NOT NULL DEFAULT true,
  elig_sig             bytea NOT NULL,                      -- sha256 of canonical non-DOB eligibility attrs
  updated_at           timestamptz NOT NULL DEFAULT now(),
  change_seq           bigint NOT NULL DEFAULT nextval('sync_seq')
);
-- The matching join: equality on signature, range on DOB.
CREATE INDEX profile_sig_dob ON profile (elig_sig, dob);
CREATE INDEX profile_interests ON profile USING gin (interest_exam_types);

-- Distinct signatures with their decoded attributes (maintained in the profile write transaction).
CREATE TABLE elig_signature (
  sig        bytea PRIMARY KEY,
  attrs      jsonb NOT NULL,                                -- canonical attrs used by the evaluator
  user_count int   NOT NULL DEFAULT 0
);

CREATE TABLE notification_prefs (
  user_id         text PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  quiet_start     time NOT NULL DEFAULT '22:00',
  quiet_end       time NOT NULL DEFAULT '07:00',
  digest_enabled  boolean NOT NULL DEFAULT true,
  digest_time     time NOT NULL DEFAULT '19:00',
  push_enabled    boolean NOT NULL DEFAULT true,
  whatsapp_enabled boolean NOT NULL DEFAULT false,
  lifecycle_alerts boolean NOT NULL DEFAULT true,
  family_alerts   boolean NOT NULL DEFAULT true,
  change_seq      bigint NOT NULL DEFAULT nextval('sync_seq')
);

CREATE TABLE device (
  installation_id      text PRIMARY KEY,
  user_id              text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  fcm_token            text NOT NULL,
  is_primary           boolean NOT NULL DEFAULT false,
  app_version          int NOT NULL,
  os_api_level         int,
  manufacturer         text,
  model                text,
  notifications_enabled boolean,
  channels_blocked     text[] NOT NULL DEFAULT '{}',
  battery_opt_ignored  boolean,
  autostart_confirmed  boolean,
  delivery_health      text NOT NULL DEFAULT 'unknown',     -- good|degraded|failing|unknown
  last_receipt_at      timestamptz,
  token_invalid_at     timestamptz,                         -- FCM UNREGISTERED
  updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX device_one_primary ON device (user_id) WHERE is_primary;
CREATE INDEX device_user ON device (user_id);

CREATE TABLE self_test (
  id            text PRIMARY KEY,
  installation_id text NOT NULL REFERENCES device(installation_id) ON DELETE CASCADE,
  status        text NOT NULL,                              -- sent|received|displayed|timeout|fcm_error
  sent_at       timestamptz NOT NULL DEFAULT now(),
  received_at   timestamptz,
  fcm_error     text
);

CREATE TABLE consent_log (                                  -- append-only (DPDP proof of consent)
  id          bigserial PRIMARY KEY,
  user_id     text NOT NULL,                                -- no FK: survives as pseudonymous proof until retention ends
  purpose     text NOT NULL,
  version     text NOT NULL,
  granted     boolean NOT NULL,
  language    text NOT NULL,
  source      text NOT NULL,                                -- app|whatsapp|family_invite
  recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX consent_user ON consent_log (user_id, purpose, recorded_at DESC);

CREATE TABLE otp_session (
  id              text PRIMARY KEY,
  phone_hmac      bytea NOT NULL,
  purpose         text NOT NULL,
  code_hash       bytea NOT NULL,                           -- HMAC(code, session id)
  channel         channel_t NOT NULL,
  attempts        int NOT NULL DEFAULT 0,
  installation_id text,
  firebase_uid    text NOT NULL,
  expires_at      timestamptz NOT NULL,
  verified_at     timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX otp_phone_recent ON otp_session (phone_hmac, created_at DESC);

-- ---------------------------------------------------------------- reference data
CREATE TABLE ref_item (                                     -- categories, states, exam types, pwd types, extras, orgs
  kind      text NOT NULL,
  code      text NOT NULL,
  name_hi   text NOT NULL,
  name_en   text NOT NULL,
  sort      int NOT NULL DEFAULT 0,
  deprecated boolean NOT NULL DEFAULT false,
  PRIMARY KEY (kind, code)
);
CREATE TABLE qualification (                                -- taxonomy tree
  code       text PRIMARY KEY,                              -- grad.ba, class12.science, diploma.engg.civil
  parent     text REFERENCES qualification(code),
  level      int  NOT NULL,                                 -- 10,20,25,30,40,50,60
  name_hi    text NOT NULL,
  name_en    text NOT NULL,
  aliases    text[] NOT NULL DEFAULT '{}',
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
  language      text,                                       -- hi|en|bilingual
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
  etag          text,
  created_at    timestamptz NOT NULL DEFAULT now(),
  change_seq    bigint NOT NULL DEFAULT nextval('sync_seq'),
  UNIQUE (org_code, advt_no)
);
CREATE INDEX recruitment_open ON recruitment (status, apply_end);

CREATE TABLE notice_version (                               -- immutable snapshots
  recruitment_id text NOT NULL REFERENCES recruitment(id),
  version        int  NOT NULL,
  kind           text NOT NULL,                             -- original|corrigendum|extension|edit|cancellation
  source_document_id text REFERENCES source_document(id),
  content        jsonb NOT NULL,                            -- titles, key_dates, fees, documents, specs, links (API Notice shape minus posts)
  provenance     jsonb NOT NULL,                            -- field path -> {doc, page}
  change_summary jsonb,                                     -- [{field, before, after, text{hi,en}}]
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
  name_hi        text NOT NULL,
  name_en        text NOT NULL,
  exam_tags      text[] NOT NULL,                           -- matched against profile.interest_exam_types
  vacancies      jsonb,                                     -- {total, by_category{}, by_state{}}
  current_rule_version int NOT NULL DEFAULT 0,
  active         boolean NOT NULL DEFAULT true,
  UNIQUE (recruitment_id, code)
);

CREATE TABLE post_rule (                                    -- immutable; rule = rule.schema.json document
  post_id        text NOT NULL REFERENCES post(id),
  version        int  NOT NULL,
  notice_version int  NOT NULL,
  rule           jsonb NOT NULL,
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
  title          jsonb NOT NULL,
  body           jsonb,
  starts_at      timestamptz,
  ends_at        timestamptz,
  expected_window text,
  links          jsonb NOT NULL DEFAULT '[]',
  critical       boolean NOT NULL DEFAULT false,
  supersedes     text REFERENCES lifecycle_event(id),       -- reschedule chain
  source_document_id text REFERENCES source_document(id),
  published_by   text,
  published_at   timestamptz,
  change_seq     bigint NOT NULL DEFAULT nextval('sync_seq')
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

-- ---------------------------------------------------------------- matches, reminders, applications
CREATE TABLE match (
  id             text PRIMARY KEY,                          -- m_...
  user_id        text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  post_id        text NOT NULL REFERENCES post(id),
  recruitment_id text NOT NULL REFERENCES recruitment(id),
  verdict        verdict_t NOT NULL,                        -- eligible|check_yourself (not_eligible never stored)
  status         match_status_t NOT NULL DEFAULT 'new',
  rule_version   int NOT NULL,
  reason_codes   smallint[] NOT NULL DEFAULT '{}',          -- compact codes for analytics; text is rendered on read
  priority       text NOT NULL DEFAULT 'normal',
  silent         boolean NOT NULL DEFAULT false,            -- created by profile edit: no "new match" push
  not_interested_reason text,
  matched_at     timestamptz NOT NULL DEFAULT now(),
  seen_at        timestamptz,
  acted_at       timestamptz,
  last_action_at timestamptz,                               -- occurred_at of last applied action (ordering)
  updated_at     timestamptz NOT NULL DEFAULT now(),
  change_seq     bigint NOT NULL DEFAULT nextval('sync_seq'),
  UNIQUE (user_id, post_id)
);
CREATE INDEX match_user_sync ON match (user_id, change_seq);
CREATE INDEX match_post_status ON match (post_id, status);

CREATE TABLE match_action (                                 -- idempotent action log (offline queue replay)
  action_id    uuid PRIMARY KEY,
  match_id     text NOT NULL REFERENCES match(id) ON DELETE CASCADE,
  user_id      text NOT NULL,
  type         text NOT NULL,
  occurred_at  timestamptz NOT NULL,
  received_at  timestamptz NOT NULL DEFAULT now(),
  applied      boolean NOT NULL,
  payload      jsonb
);

CREATE TABLE reminder_state (                               -- one per open match; drives the scheduler
  match_id      text PRIMARY KEY REFERENCES match(id) ON DELETE CASCADE,
  user_id       text NOT NULL,
  next_due_at   timestamptz,                                -- NULL = nothing scheduled (terminal)
  next_kind     text,                                       -- ReminderKind
  snoozed_until timestamptz,
  last_sent_at  timestamptz,
  sent_count    int NOT NULL DEFAULT 0,
  wa_sent_count int NOT NULL DEFAULT 0,
  lease_until   timestamptz                                 -- crash-safe claim by a worker
);
CREATE INDEX reminder_due ON reminder_state (next_due_at) WHERE next_due_at IS NOT NULL;

CREATE TABLE application (
  id             text PRIMARY KEY,                          -- a_...
  user_id        text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  match_id       text NOT NULL UNIQUE REFERENCES match(id) ON DELETE CASCADE,
  recruitment_id text NOT NULL REFERENCES recruitment(id),
  post_id        text NOT NULL REFERENCES post(id),
  applied_at     timestamptz NOT NULL,
  fee_paid       boolean,
  tracking       text NOT NULL DEFAULT 'active',            -- active|stopped|completed
  current_stage  text,
  progress       jsonb NOT NULL DEFAULT '[]',               -- [{stage,outcome,at}]
  updated_at     timestamptz NOT NULL DEFAULT now(),
  change_seq     bigint NOT NULL DEFAULT nextval('sync_seq')
);
CREATE INDEX application_by_recruitment ON application (recruitment_id) WHERE tracking = 'active';
CREATE INDEX application_user_sync ON application (user_id, change_seq);

-- ---------------------------------------------------------------- alerts & deliveries
-- alert = one logical message to one person (inbox row); delivery = one channel attempt.
CREATE TABLE alert (
  id             text NOT NULL,                             -- deterministic msg_id, e.g. r:m_..:d_minus_1:20260731
  created_at     timestamptz NOT NULL DEFAULT now(),
  user_id        text NOT NULL,
  recipient      text NOT NULL DEFAULT 'self',              -- self | family:<family_member.id>
  kind           text NOT NULL,                             -- match_new|reminder|deadline|extension|corrigendum|lifecycle|digest|clash|certificate|system|self_test
  match_id       text,
  application_id text,
  lifecycle_event_id text,
  priority       text NOT NULL,                             -- high|normal|low
  critical       boolean NOT NULL DEFAULT false,
  title          jsonb NOT NULL,
  body           jsonb NOT NULL,
  not_before     timestamptz NOT NULL DEFAULT now(),        -- quiet hours push-back
  escalate_at    timestamptz,                               -- check receipts, then WhatsApp (Pro)
  opened_at      timestamptz,
  change_seq     bigint NOT NULL DEFAULT nextval('sync_seq'),
  PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);                           -- monthly partitions; drop after 90 days
CREATE INDEX alert_user ON alert (user_id, created_at DESC);
CREATE INDEX alert_escalate ON alert (escalate_at) WHERE escalate_at IS NOT NULL;

CREATE TABLE alert_dedupe (                                 -- global uniqueness across partitions (TTL 120 d)
  id         text PRIMARY KEY,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE delivery (
  id             bigserial,
  alert_id       text NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now(),
  user_id        text NOT NULL,
  channel        channel_t NOT NULL,
  target_ref     text,                                      -- installation_id | whatsapp_link | family_member id
  status         delivery_status NOT NULL DEFAULT 'queued',
  run_after      timestamptz NOT NULL DEFAULT now(),
  attempts       int NOT NULL DEFAULT 0,
  provider_msg_id text,
  error          text,
  sent_at        timestamptz,
  delivered_at   timestamptz,                               -- push: app receipt "received"; WA: status webhook
  read_at        timestamptz,
  cost_paise     int,
  PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
CREATE INDEX delivery_queue ON delivery (run_after) WHERE status = 'queued';
CREATE INDEX delivery_alert ON delivery (alert_id);
CREATE INDEX delivery_provider ON delivery (provider_msg_id) WHERE provider_msg_id IS NOT NULL;

CREATE TABLE daily_counter (                                -- caps: 3 pushes/day, per-IST-day
  user_id   text NOT NULL,
  ist_date  date NOT NULL,
  pushes    int NOT NULL DEFAULT 0,
  whatsapp  int NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, ist_date)
);

-- ---------------------------------------------------------------- WhatsApp & family
CREATE TABLE whatsapp_link (
  user_id        text PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  state          text NOT NULL,                             -- pending|active|opted_out|blocked
  phone_hmac     bytea,
  phone_enc      bytea,
  phone_last2    text,
  opt_in_code    text,
  code_expires_at timestamptz,
  opted_in_at    timestamptz,
  opted_out_at   timestamptz,
  last_inbound_at timestamptz,
  season_quota_used int NOT NULL DEFAULT 0,
  quota_period_end  timestamptz
);
CREATE UNIQUE INDEX whatsapp_code ON whatsapp_link (opt_in_code) WHERE state = 'pending';

CREATE TABLE family_member (
  id              text PRIMARY KEY,                         -- f_...
  user_id         text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  display_name    text NOT NULL,
  relation        text NOT NULL,
  state           text NOT NULL DEFAULT 'invited',          -- invited|active|declined|opted_out|expired
  invite_token_hash bytea NOT NULL UNIQUE,
  invite_expires_at timestamptz NOT NULL,
  invitee_user_id text REFERENCES app_user(id) ON DELETE SET NULL,
  phone_hmac      bytea,
  phone_enc       bytea,
  phone_last2     text,
  channels        text[] NOT NULL DEFAULT '{whatsapp}',
  alert_kinds     text[] NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  accepted_at     timestamptz
);
CREATE INDEX family_user ON family_member (user_id);

-- ---------------------------------------------------------------- payments & entitlements
CREATE TABLE product (
  id              text PRIMARY KEY,                         -- pro_season, pro_year
  kind            text NOT NULL,
  play_product_id text NOT NULL,
  play_base_plan_id text NOT NULL,
  duration_days   int NOT NULL,
  upi_price_paise int,
  active          boolean NOT NULL DEFAULT true
);

CREATE TABLE purchase (
  id                  text PRIMARY KEY,
  user_id             text REFERENCES app_user(id) ON DELETE SET NULL,  -- kept (financial record) after deletion, unlinked
  provider            text NOT NULL,                        -- play|razorpay
  product_id          text NOT NULL REFERENCES product(id),
  token_hash          bytea NOT NULL UNIQUE,                -- sha256(purchaseToken | payment_link id)
  token_enc           bytea,
  linked_token_hash   bytea,                                -- Play linkedPurchaseToken (upgrade/resubscribe)
  order_id            text,
  state               text NOT NULL,                        -- pending|active|in_grace|on_hold|paused|canceled|expired|revoked|refunded
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
  updated_at    timestamptz NOT NULL DEFAULT now(),
  change_seq    bigint NOT NULL DEFAULT nextval('sync_seq')
);
CREATE INDEX entitlement_expiry ON user_entitlement (expires_at) WHERE plan = 'pro';

CREATE TABLE referral_redemption (
  referee_user_id  text PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  referrer_user_id text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  redeemed_at      timestamptz NOT NULL DEFAULT now(),
  rewarded_at      timestamptz                              -- when referee completed profile / first Applied
);

-- ---------------------------------------------------------------- quality, sync, ops
CREATE TABLE match_report (
  id           text PRIMARY KEY,
  match_id     text REFERENCES match(id) ON DELETE SET NULL,
  user_id      text,                                        -- nulled on account deletion
  post_id      text NOT NULL REFERENCES post(id),
  rule_version int NOT NULL,
  reason       text NOT NULL,
  rule         text,
  comment      text,
  profile_snapshot jsonb,                                   -- minimal attrs needed to reproduce (no phone)
  state        text NOT NULL DEFAULT 'received',
  resolution   text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  resolved_at  timestamptz
);
CREATE INDEX report_open ON match_report (post_id) WHERE state = 'received';

CREATE TABLE sync_tombstone (
  user_id    text NOT NULL,
  entity     text NOT NULL,                                 -- match|application|inbox
  entity_id  text NOT NULL,
  change_seq bigint NOT NULL DEFAULT nextval('sync_seq'),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, change_seq)
);

CREATE TABLE idempotency_key (
  user_id        text NOT NULL,
  key            uuid NOT NULL,
  request_hash   bytea NOT NULL,
  status         int,
  response       jsonb,
  created_at     timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, key)
);

CREATE TABLE job (                                          -- generic durable jobs (extract, match, export, rematch)
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

CREATE TABLE export_job (
  id         text PRIMARY KEY,
  user_id    text NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  state      text NOT NULL,
  gcs_path   text,
  expires_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE admin_user (
  email   text PRIMARY KEY,
  role    text NOT NULL CHECK (role IN ('owner','reviewer'))
);

CREATE TABLE audit_log (                                    -- append-only; admin + security-relevant user events
  id         bigserial PRIMARY KEY,
  actor      text NOT NULL,                                 -- admin email | user id | 'system'
  action     text NOT NULL,                                 -- publish_notice, retract_match, grant_promo, delete_account...
  entity     text,
  entity_id  text,
  before     jsonb,
  after      jsonb,
  at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_entity ON audit_log (entity, entity_id);
