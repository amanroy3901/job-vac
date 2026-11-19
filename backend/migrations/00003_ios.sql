-- +goose Up
-- iOS (founder decision 9, 2026-11-19): platform-aware devices and entitlements, App Store purchases (StoreKit 2),
-- App Store Server Notifications V2 dedupe, Sign in with Apple accounts and their refresh tokens (revoked on
-- deletion). The same DDL is folded into docs/backend/schema.sql (and so into 00001_baseline.sql), so on a fresh
-- database every statement here is a no-op; on a database created before this change it adds the iOS shape.
-- Every statement is therefore idempotent (IF NOT EXISTS / DROP ... IF EXISTS).
-- Note: ALTER TYPE ... ADD VALUE is allowed in a transaction on PG ≥ 12 as long as the new values are not used in
-- the same transaction (they are not: the seed below only touches text columns).

ALTER TYPE auth_kind_t ADD VALUE IF NOT EXISTS 'apple';
ALTER TYPE ent_source_t ADD VALUE IF NOT EXISTS 'appstore_season';
ALTER TYPE ent_source_t ADD VALUE IF NOT EXISTS 'appstore_year';

ALTER TABLE device ADD COLUMN IF NOT EXISTS platform text NOT NULL DEFAULT 'android';
ALTER TABLE device DROP CONSTRAINT IF EXISTS device_platform_check;
ALTER TABLE device ADD CONSTRAINT device_platform_check CHECK (platform IN ('android','ios'));

CREATE TABLE IF NOT EXISTS apple_credential (
  user_id           text PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  refresh_token_enc bytea NOT NULL,
  updated_at        timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE product ADD COLUMN IF NOT EXISTS appstore_product_id text;
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_appstore_product_id_key;
ALTER TABLE product ADD CONSTRAINT product_appstore_product_id_key UNIQUE (appstore_product_id);

ALTER TABLE purchase DROP CONSTRAINT IF EXISTS purchase_provider_check;
ALTER TABLE purchase ADD CONSTRAINT purchase_provider_check CHECK (provider IN ('play','app_store'));
ALTER TABLE purchase ADD COLUMN IF NOT EXISTS original_transaction_id text;
ALTER TABLE purchase ADD COLUMN IF NOT EXISTS environment text;
ALTER TABLE purchase ADD COLUMN IF NOT EXISTS last_event_at timestamptz;
CREATE UNIQUE INDEX IF NOT EXISTS purchase_appstore_otid ON purchase (original_transaction_id) WHERE provider = 'app_store';

CREATE TABLE IF NOT EXISTS app_store_notification (
  notification_uuid text PRIMARY KEY,
  notification_type text NOT NULL,
  subtype           text,
  received_at       timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE user_entitlement ADD COLUMN IF NOT EXISTS platform text;
ALTER TABLE user_entitlement DROP CONSTRAINT IF EXISTS user_entitlement_platform_check;
ALTER TABLE user_entitlement ADD CONSTRAINT user_entitlement_platform_check CHECK (platform IN ('android','ios'));

-- App Store product ids (App Store Connect → Subscriptions / In-App Purchases; payments-and-entitlements.md §1).
UPDATE product SET appstore_product_id = 'in.sarkarimatch.pro.season4m' WHERE id = 'pro_season';
UPDATE product SET appstore_product_id = 'in.sarkarimatch.pro.yearly'   WHERE id = 'pro_year';

-- +goose Down
-- Enum values cannot be dropped in PostgreSQL; 'apple', 'appstore_season' and 'appstore_year' stay (unused).
UPDATE product SET appstore_product_id = NULL;
ALTER TABLE user_entitlement DROP CONSTRAINT IF EXISTS user_entitlement_platform_check;
ALTER TABLE user_entitlement DROP COLUMN IF EXISTS platform;
DROP TABLE IF EXISTS app_store_notification;
DROP INDEX IF EXISTS purchase_appstore_otid;
DELETE FROM entitlement_grant WHERE purchase_id IN (SELECT id FROM purchase WHERE provider = 'app_store');
DELETE FROM purchase WHERE provider = 'app_store';
ALTER TABLE purchase DROP COLUMN IF EXISTS last_event_at;
ALTER TABLE purchase DROP COLUMN IF EXISTS environment;
ALTER TABLE purchase DROP COLUMN IF EXISTS original_transaction_id;
ALTER TABLE purchase DROP CONSTRAINT IF EXISTS purchase_provider_check;
ALTER TABLE purchase ADD CONSTRAINT purchase_provider_check CHECK (provider = 'play');
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_appstore_product_id_key;
ALTER TABLE product DROP COLUMN IF EXISTS appstore_product_id;
DROP TABLE IF EXISTS apple_credential;
ALTER TABLE device DROP CONSTRAINT IF EXISTS device_platform_check;
ALTER TABLE device DROP COLUMN IF EXISTS platform;
