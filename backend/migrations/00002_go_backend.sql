-- +goose Up
-- Deltas the Go backend needs on top of the design baseline (docs/backend/schema.sql).
-- Each one is listed in backend/README.md "Schema deltas"; fold them into schema.sql at the next design pass.

-- 1. Remote config (operations.md §1: flags/kill switches live in a `config` table edited in the admin console).
CREATE TABLE app_config (
  key         text PRIMARY KEY,                 -- 'app' = AppConfig overrides merged over code defaults
  value       jsonb NOT NULL,
  updated_by  text NOT NULL DEFAULT 'system',
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- 2. Manual-entry review tasks (ingestion-pipeline.md §10 "manual-entry form, empty draft") have no source document.
ALTER TABLE review_task ALTER COLUMN source_document_id DROP NOT NULL;

-- 3. Family: FamilyInviteInput.relation is shown to the owner (S34); invites can be declined (FamilyInviteView.state).
ALTER TABLE family_link ADD COLUMN relation text CHECK (relation IN ('parent','sibling','spouse','other'));
ALTER TABLE family_link DROP CONSTRAINT family_link_state_check;
ALTER TABLE family_link ADD CONSTRAINT family_link_state_check
  CHECK (state IN ('invited','active','removed','expired','declined'));

-- 4. Catalog files: per-locale-set sha256/size (CatalogHead.sha256) and a "files written" marker so that
--    /v1/catalog/head never lists a batch whose files are not on the blob store yet.
ALTER TABLE catalog_batch ADD COLUMN files jsonb NOT NULL DEFAULT '{}';
ALTER TABLE catalog_batch ADD COLUMN built_at timestamptz;
ALTER TABLE catalog_snapshot ADD COLUMN files jsonb NOT NULL DEFAULT '{}';

-- 5. Purchases: remember the base plan and the Play test flag for the closed-test metrics.
ALTER TABLE purchase ADD COLUMN base_plan_id text;
ALTER TABLE purchase ADD COLUMN test_purchase boolean NOT NULL DEFAULT false;
CREATE INDEX purchase_user ON purchase (user_id);
CREATE UNIQUE INDEX one_grant_per_purchase ON entitlement_grant (purchase_id) WHERE purchase_id IS NOT NULL;

-- 6. Job queue: lease-based claiming helper index.
CREATE INDEX job_kind_state ON job (kind, state);

-- ---------------------------------------------------------------- seed data
INSERT INTO product (id, kind, play_product_id, play_base_plan_id, duration_days, active) VALUES
  ('pro_season', 'prepaid_season',  'pro', 'season-4m-prepaid', 120, true),
  ('pro_year',   'auto_renew_year', 'pro', 'yearly-autorenew',  365, true),
  ('pro_month',  'auto_renew_month','pro', 'monthly-autorenew', 30,  false);

INSERT INTO ref_item (kind, code, name, sort) VALUES
  ('category','gen','{"en":"General (UR)","hi":"सामान्य (UR)"}',1),
  ('category','ews','{"en":"EWS","hi":"EWS"}',2),
  ('category','obc','{"en":"OBC","hi":"OBC"}',3),
  ('category','sc','{"en":"SC","hi":"SC"}',4),
  ('category','st','{"en":"ST","hi":"ST"}',5),
  ('state','UP','{"en":"Uttar Pradesh","hi":"उत्तर प्रदेश"}',1),
  ('state','BR','{"en":"Bihar","hi":"बिहार"}',2),
  ('state','RJ','{"en":"Rajasthan","hi":"राजस्थान"}',3),
  ('state','MP','{"en":"Madhya Pradesh","hi":"मध्य प्रदेश"}',4),
  ('state','DL','{"en":"Delhi","hi":"दिल्ली"}',5),
  ('state','HR','{"en":"Haryana","hi":"हरियाणा"}',6),
  ('state','UK','{"en":"Uttarakhand","hi":"उत्तराखंड"}',7),
  ('state','JH','{"en":"Jharkhand","hi":"झारखंड"}',8),
  ('state','CG','{"en":"Chhattisgarh","hi":"छत्तीसगढ़"}',9),
  ('state','HP','{"en":"Himachal Pradesh","hi":"हिमाचल प्रदेश"}',10),
  ('exam_type','ssc','{"en":"SSC","hi":"SSC"}',1),
  ('exam_type','railway','{"en":"Railway","hi":"रेलवे"}',2),
  ('exam_type','banking','{"en":"Banking","hi":"बैंकिंग"}',3),
  ('exam_type','police','{"en":"Police","hi":"पुलिस"}',4),
  ('exam_type','defence','{"en":"Defence","hi":"रक्षा"}',5),
  ('exam_type','teaching','{"en":"Teaching","hi":"शिक्षण"}',6),
  ('exam_type','upsc','{"en":"UPSC","hi":"UPSC"}',7),
  ('exam_type','state_psc','{"en":"State PSC","hi":"राज्य PSC"}',8),
  ('exam_type','other','{"en":"Other","hi":"अन्य"}',9),
  ('pwd_type','vi','{"en":"Visual impairment","hi":"दृष्टि बाधित"}',1),
  ('pwd_type','hh','{"en":"Hearing impairment","hi":"श्रवण बाधित"}',2),
  ('pwd_type','ld','{"en":"Locomotor disability","hi":"चलन दिव्यांगता"}',3),
  ('pwd_type','id_mi','{"en":"Autism, ID, SLD, mental illness","hi":"ऑटिज़्म, बौद्धिक, SLD, मानसिक"}',4),
  ('pwd_type','md','{"en":"Multiple disabilities","hi":"बहु-दिव्यांगता"}',5),
  ('extra','ncc_b','{"en":"NCC B certificate","hi":"NCC B प्रमाणपत्र"}',1),
  ('extra','ncc_c','{"en":"NCC C certificate","hi":"NCC C प्रमाणपत्र"}',2),
  ('extra','ccc','{"en":"CCC (computer)","hi":"CCC (कंप्यूटर)"}',3),
  ('extra','typing_hi','{"en":"Hindi typing","hi":"हिंदी टाइपिंग"}',4),
  ('extra','typing_en','{"en":"English typing","hi":"अंग्रेज़ी टाइपिंग"}',5),
  ('extra','driving_licence_lmv','{"en":"Driving licence (LMV)","hi":"ड्राइविंग लाइसेंस (LMV)"}',6),
  ('extra','ctet','{"en":"CTET","hi":"CTET"}',7),
  ('extra','uptet','{"en":"UPTET","hi":"UPTET"}',8),
  ('organization','ssc','{"en":"Staff Selection Commission","hi":"कर्मचारी चयन आयोग"}',1),
  ('organization','rrb','{"en":"Railway Recruitment Boards","hi":"रेलवे भर्ती बोर्ड"}',2),
  ('organization','ibps','{"en":"IBPS","hi":"IBPS"}',3),
  ('organization','upsc','{"en":"UPSC","hi":"संघ लोक सेवा आयोग"}',4),
  ('organization','uppbpb','{"en":"UP Police Recruitment and Promotion Board","hi":"उ.प्र. पुलिस भर्ती एवं प्रोन्नति बोर्ड"}',5),
  ('organization','upsssc','{"en":"UPSSSC","hi":"UPSSSC"}',6),
  ('organization','bpsc','{"en":"BPSC","hi":"BPSC"}',7);

INSERT INTO qualification (code, parent, level, name, aliases, selectable) VALUES
  ('class10', NULL, 10, '{"en":"10th pass","hi":"10वीं पास"}', '{10th,matric,मैट्रिक,हाईस्कूल}', true),
  ('iti', NULL, 20, '{"en":"ITI (any trade)","hi":"ITI (कोई भी ट्रेड)"}', '{ITI,आईटीआई}', false),
  ('iti.electrician', 'iti', 20, '{"en":"ITI Electrician","hi":"ITI इलेक्ट्रीशियन"}', '{}', true),
  ('iti.fitter', 'iti', 20, '{"en":"ITI Fitter","hi":"ITI फिटर"}', '{}', true),
  ('class12', NULL, 25, '{"en":"12th pass","hi":"12वीं पास"}', '{12th,intermediate,इंटर}', true),
  ('class12.science', 'class12', 25, '{"en":"12th (Science)","hi":"12वीं (विज्ञान)"}', '{}', true),
  ('class12.commerce', 'class12', 25, '{"en":"12th (Commerce)","hi":"12वीं (वाणिज्य)"}', '{}', true),
  ('class12.arts', 'class12', 25, '{"en":"12th (Arts)","hi":"12वीं (कला)"}', '{}', true),
  ('diploma', NULL, 30, '{"en":"Diploma (any)","hi":"डिप्लोमा (कोई भी)"}', '{}', false),
  ('diploma.engg.civil', 'diploma', 30, '{"en":"Diploma Civil Engg.","hi":"डिप्लोमा सिविल इंजी."}', '{}', true),
  ('grad.any', NULL, 40, '{"en":"Graduate (any discipline)","hi":"स्नातक (कोई भी विषय)"}', '{graduate,स्नातक}', false),
  ('grad.ba', 'grad.any', 40, '{"en":"B.A.","hi":"बी.ए."}', '{B.A.,BA,बीए}', true),
  ('grad.bsc', 'grad.any', 40, '{"en":"B.Sc.","hi":"बी.एससी."}', '{B.Sc,BSc}', true),
  ('grad.bcom', 'grad.any', 40, '{"en":"B.Com.","hi":"बी.कॉम."}', '{B.Com,BCom}', true),
  ('grad.btech', 'grad.any', 40, '{"en":"B.Tech / B.E.","hi":"बी.टेक / बी.ई."}', '{BTech,BE}', true),
  ('pg.any', NULL, 50, '{"en":"Postgraduate (any)","hi":"स्नातकोत्तर (कोई भी)"}', '{}', false),
  ('pg.ma', 'pg.any', 50, '{"en":"M.A.","hi":"एम.ए."}', '{MA}', true),
  ('pg.msc', 'pg.any', 50, '{"en":"M.Sc.","hi":"एम.एससी."}', '{MSc}', true),
  ('phd', NULL, 60, '{"en":"Ph.D.","hi":"पीएच.डी."}', '{PhD}', true);

INSERT INTO notification_text (template, locale, title, body) VALUES
  ('N-31','en','Pro trial ends in 2 days','Keep lifecycle alerts for your forms: choose a plan.'),
  ('N-31','hi','Pro ट्रायल 2 दिन में ख़त्म','अपने फ़ॉर्म की अगली सूचनाएँ पाते रहें: प्लान चुनें।'),
  ('N-33','en','Pro has ended','Lifecycle alerts are paused. Renew any time.'),
  ('N-33','hi','Pro ख़त्म हो गया','आगे की सूचनाएँ रुकी हैं। कभी भी फिर से लें।'),
  ('N-47','en','Alerts moved to another phone','Reminders now go to {{device_label}}. This phone stays quiet.'),
  ('N-47','hi','सूचनाएँ दूसरे फ़ोन पर','अब रिमाइंडर {{device_label}} पर आएँगे। यह फ़ोन शांत रहेगा।'),
  ('entitlement','en','Your plan changed','Open the app to refresh your plan.'),
  ('entitlement','hi','आपका प्लान बदला','प्लान ताज़ा करने के लिए ऐप खोलें।'),
  ('family_accepted','en','Family member joined','They will now get alerts for the jobs you share.'),
  ('family_accepted','hi','परिवार जुड़ गया','अब उन्हें आपकी चुनी नौकरियों की सूचनाएँ मिलेंगी।'),
  ('family_removed','en','Family link ended','You will no longer get these alerts.'),
  ('family_removed','hi','परिवार लिंक ख़त्म','अब ये सूचनाएँ नहीं आएँगी।');

-- +goose Down
DELETE FROM notification_text; DELETE FROM qualification; DELETE FROM ref_item; DELETE FROM product;
DROP INDEX job_kind_state;
DROP INDEX one_grant_per_purchase;
DROP INDEX purchase_user;
ALTER TABLE purchase DROP COLUMN test_purchase;
ALTER TABLE purchase DROP COLUMN base_plan_id;
ALTER TABLE catalog_snapshot DROP COLUMN files;
ALTER TABLE catalog_batch DROP COLUMN built_at;
ALTER TABLE catalog_batch DROP COLUMN files;
ALTER TABLE family_link DROP CONSTRAINT family_link_state_check;
ALTER TABLE family_link ADD CONSTRAINT family_link_state_check CHECK (state IN ('invited','active','removed','expired'));
ALTER TABLE family_link DROP COLUMN relation;
ALTER TABLE review_task ALTER COLUMN source_document_id SET NOT NULL;
DROP TABLE app_config;
