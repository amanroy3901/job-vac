// Package account manages the minimal server account (ADR-11): Firebase uid, auth kind, locale, devices for
// account pushes, the consent log, the end-to-end-encrypted backup blob and deletion. It never stores a profile,
// phone number, name or email.
package account

import (
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"log/slog"
	"slices"
	"strconv"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/auth"
	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/ids"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/amanroy3901/job-vac/backend/internal/rules"
	"github.com/amanroy3901/job-vac/backend/internal/seal"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// DeletionGrace is the grace period before hard delete (data-model.md §5).
const DeletionGrace = 7 * 24 * time.Hour

// User is an app_user row.
type User struct {
	ID                string
	FirebaseUID       string
	AuthKind          string // anonymous | google | apple
	State             string // active | pending_deletion | deleted
	Locale            string
	BillingAccountRef string
	ReferralCode      string
	CreatedAt         time.Time
	HardDeleteAt      *time.Time
}

// Service is the account store.
type Service struct {
	Pool *pgxpool.Pool
	Now  func() time.Time
	// FirebaseAdmin disables/deletes Firebase users on deletion (nil = log only).
	FirebaseAdmin FirebaseAdmin
	// Apple exchanges Sign in with Apple authorization codes for refresh tokens (revoked on deletion, guideline
	// 5.1.1(v)). nil = codes are ignored.
	Apple auth.AppleTokens
	Seal  *seal.Sealer
}

// Linked reports a Google- or Apple-linked account (backup and cross-device restore need one).
func (u *User) Linked() bool { return u.AuthKind == "google" || u.AuthKind == "apple" }

// FirebaseAdmin is the Admin SDK surface used for erasure.
// TODO: REST implementation (identitytoolkit accounts:update disableUser / accounts:delete) with ADC.
type FirebaseAdmin interface {
	DisableUser(ctx context.Context, uid string) error
	DeleteUser(ctx context.Context, uid string) error
}

func (s *Service) now() time.Time {
	if s.Now != nil {
		return s.Now()
	}
	return time.Now()
}

const userCols = `id, firebase_uid, auth_kind::text, state::text, locale, billing_account_ref::text, referral_code, created_at, hard_delete_at`

func scanUser(row pgx.Row) (*User, error) {
	var u User
	err := row.Scan(&u.ID, &u.FirebaseUID, &u.AuthKind, &u.State, &u.Locale, &u.BillingAccountRef, &u.ReferralCode, &u.CreatedAt, &u.HardDeleteAt)
	if err != nil {
		return nil, err
	}
	return &u, nil
}

// ByFirebaseUID loads the account of a Firebase user (nil, nil when none).
func (s *Service) ByFirebaseUID(ctx context.Context, uid string) (*User, error) {
	u, err := scanUser(s.Pool.QueryRow(ctx, `SELECT `+userCols+` FROM app_user WHERE firebase_uid = $1`, uid))
	if db.IsNoRows(err) {
		return nil, nil
	}
	return u, err
}

// Touch updates last_seen_at at most once per hour.
func (s *Service) Touch(ctx context.Context, userID string) {
	_, _ = s.Pool.Exec(ctx, `UPDATE app_user SET last_seen_at = now() WHERE id = $1 AND (last_seen_at IS NULL OR last_seen_at < now() - interval '1 hour')`, userID)
}

// DeviceInput is the API DeviceInput.
type DeviceInput struct {
	Platform    string  `json:"platform"` // android | ios ("" = android, for app versions before iOS)
	FCMToken    string  `json:"fcm_token"`
	AppVersion  int     `json:"app_version"`
	Locale      string  `json:"locale"`
	DeviceLabel *string `json:"device_label"`
}

// Validate checks a DeviceInput.
func (d DeviceInput) Validate(prefix string) error {
	var fe []problem.FieldError
	if d.Platform != "" && d.Platform != "android" && d.Platform != "ios" {
		fe = append(fe, problem.FieldError{Field: prefix + "platform", Code: "unknown_code"})
	}
	if d.FCMToken == "" || len(d.FCMToken) > 4096 {
		fe = append(fe, problem.FieldError{Field: prefix + "fcm_token", Code: "required"})
	}
	if d.AppVersion <= 0 {
		fe = append(fe, problem.FieldError{Field: prefix + "app_version", Code: "required"})
	}
	if !rules.KnownLocales[d.Locale] {
		fe = append(fe, problem.FieldError{Field: prefix + "locale", Code: "unknown_code"})
	}
	if d.DeviceLabel != nil && len(*d.DeviceLabel) > 64 {
		fe = append(fe, problem.FieldError{Field: prefix + "device_label", Code: "too_long"})
	}
	if len(fe) > 0 {
		return problem.Validation("invalid device", fe...)
	}
	return nil
}

// SessionRequest is the API SessionRequest.
type SessionRequest struct {
	InstallationID        string       `json:"installation_id"`
	Locale                string       `json:"locale"`
	PrivacyNoticeVersion  *string      `json:"privacy_notice_version"`
	ReferralCode          *string      `json:"referral_code"`
	MergeAnonymousIDToken *string      `json:"merge_anonymous_id_token"`
	Device                *DeviceInput `json:"device"`
	// AppleAuthorizationCode is ASAuthorizationAppleIDCredential.authorizationCode (iOS, after Sign in with Apple);
	// exchanged for a refresh token that is revoked when the account is deleted.
	AppleAuthorizationCode *string `json:"apple_authorization_code"`
}

// SessionResult is what startSession needs to render (the entitlement is added by the caller).
type SessionResult struct {
	User           *User
	IsNew          bool
	HasBackup      bool
	BackupUpdated  *time.Time
	PrimaryInstall *string
}

// StartSession creates or loads the account for a verified Firebase identity (idempotent per uid), upgrades
// anonymous → google on link, records the privacy-notice consent once per version, registers the device as
// the primary alert install and merges a collided anonymous account.
func (s *Service) StartSession(ctx context.Context, id *auth.Identity, req SessionRequest, merge *auth.Identity) (*SessionResult, error) {
	if req.InstallationID == "" || len(req.InstallationID) > 256 {
		return nil, problem.Validation("installation_id is required", problem.FieldError{Field: "installation_id", Code: "required"})
	}
	if !rules.KnownLocales[req.Locale] {
		return nil, problem.Validation("unknown locale", problem.FieldError{Field: "locale", Code: "unknown_code"})
	}
	if req.Device != nil {
		if err := req.Device.Validate("device."); err != nil {
			return nil, err
		}
	}
	kind := id.Kind()
	if kind == "" {
		return nil, problem.New(401, problem.Unauthenticated, "sign-in provider not accepted")
	}
	res := &SessionResult{}
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		u, err := scanUser(tx.QueryRow(ctx, `SELECT `+userCols+` FROM app_user WHERE firebase_uid = $1 FOR UPDATE`, id.UID))
		switch {
		case db.IsNoRows(err):
			u, err = scanUser(tx.QueryRow(ctx, `INSERT INTO app_user (id, firebase_uid, auth_kind, locale, referral_code, last_seen_at)
				VALUES ($1,$2,$3::auth_kind_t,$4,$5,now()) RETURNING `+userCols, ids.New("u"), id.UID, kind, req.Locale, ids.ReferralCode()))
			if err != nil {
				return err
			}
			res.IsNew = true
			if err := db.Audit(ctx, tx, u.ID, "account_created", "app_user", u.ID, nil, map[string]any{"auth_kind": kind}); err != nil {
				return err
			}
		case err != nil:
			return err
		default:
			if u.AuthKind == "anonymous" && kind != "anonymous" { // linkWithCredential keeps the uid (Google or Apple)
				if _, err := tx.Exec(ctx, `UPDATE app_user SET auth_kind = $2::auth_kind_t WHERE id = $1`, u.ID, kind); err != nil {
					return err
				}
				u.AuthKind = kind
			}
			if _, err := tx.Exec(ctx, `UPDATE app_user SET locale = $2, last_seen_at = now() WHERE id = $1`, u.ID, req.Locale); err != nil {
				return err
			}
			u.Locale = req.Locale
		}
		res.User = u
		if req.PrivacyNoticeVersion != nil && *req.PrivacyNoticeVersion != "" {
			if err := recordConsentOnce(ctx, tx, u.ID, "privacy_notice", *req.PrivacyNoticeVersion, req.Locale); err != nil {
				return err
			}
		}
		if merge != nil && merge.UID != id.UID {
			if err := s.mergeAnonymous(ctx, tx, u.ID, merge.UID); err != nil {
				return err
			}
		}
		if req.Device != nil && u.State == "active" {
			if _, err := upsertDevice(ctx, tx, u.ID, req.InstallationID, *req.Device); err != nil {
				return err
			}
			// The newest signed-in install becomes the alert device (B15/N-47).
			if err := makePrimary(ctx, tx, u.ID, req.InstallationID, req.Device.DeviceLabel); err != nil {
				return err
			}
		}
		return tx.QueryRow(ctx, `SELECT (SELECT updated_at FROM backup_blob WHERE user_id = $1),
			(SELECT installation_id FROM device WHERE user_id = $1 AND is_primary)`, u.ID).Scan(&res.BackupUpdated, &res.PrimaryInstall)
	})
	if err != nil {
		return nil, err
	}
	res.HasBackup = res.BackupUpdated != nil
	if req.AppleAuthorizationCode != nil && *req.AppleAuthorizationCode != "" && kind != "anonymous" {
		// Best effort: a failed exchange never fails sign-in; DELETE /v1/me accepts a fresh code instead.
		if err := s.StoreAppleCode(ctx, res.User.ID, *req.AppleAuthorizationCode); err != nil {
			slog.WarnContext(ctx, "sign in with apple code exchange failed", "err", err)
		}
	}
	return res, nil
}

// StoreAppleCode exchanges a Sign in with Apple authorization code and stores the sealed refresh token.
func (s *Service) StoreAppleCode(ctx context.Context, userID, code string) error {
	if s.Apple == nil || s.Seal == nil {
		return errors.New("sign in with apple is not configured")
	}
	rt, err := s.Apple.Exchange(ctx, code)
	if err != nil {
		return err
	}
	_, err = s.Pool.Exec(ctx, `INSERT INTO apple_credential (user_id, refresh_token_enc) VALUES ($1,$2)
		ON CONFLICT (user_id) DO UPDATE SET refresh_token_enc = EXCLUDED.refresh_token_enc, updated_at = now()`,
		userID, s.Seal.Seal([]byte(rt)))
	return err
}

// mergeAnonymous moves grants, purchases and family links of the anonymous account (firebase uid anonUID) to
// userID and deletes it (FirebaseAuthUserCollisionException path, api/README.md §3).
func (s *Service) mergeAnonymous(ctx context.Context, tx pgx.Tx, userID, anonUID string) error {
	var anonID, kind string
	err := tx.QueryRow(ctx, `SELECT id, auth_kind::text FROM app_user WHERE firebase_uid = $1 FOR UPDATE`, anonUID).Scan(&anonID, &kind)
	if db.IsNoRows(err) {
		return nil
	}
	if err != nil {
		return err
	}
	if kind != "anonymous" {
		return problem.New(403, problem.Forbidden, "only an anonymous account can be merged")
	}
	stmts := []string{
		`UPDATE purchase SET user_id = $2 WHERE user_id = $1`,
		// keep at most one trial: drop the anonymous trial when the target already has one
		`DELETE FROM entitlement_grant WHERE user_id = $1 AND source = 'trial' AND EXISTS (SELECT 1 FROM entitlement_grant WHERE user_id = $2 AND source = 'trial')`,
		`UPDATE entitlement_grant SET user_id = $2 WHERE user_id = $1`,
		`UPDATE family_link SET owner_user_id = $2 WHERE owner_user_id = $1`,
		`UPDATE family_link SET member_user_id = $2 WHERE member_user_id = $1`,
		`DELETE FROM app_user WHERE id = $1`,
	}
	for _, q := range stmts {
		if _, err := tx.Exec(ctx, q, anonID, userID); err != nil {
			return fmt.Errorf("merge: %w", err)
		}
	}
	return db.Audit(ctx, tx, userID, "merge_anonymous", "app_user", userID, map[string]any{"from": anonID}, nil)
}

func recordConsentOnce(ctx context.Context, q db.Querier, userID, purpose, version, locale string) error {
	_, err := q.Exec(ctx, `INSERT INTO consent_log (user_id, purpose, notice_version, granted, locale)
		SELECT $1,$2,$3,true,$4 WHERE NOT EXISTS (SELECT 1 FROM consent_log WHERE user_id=$1 AND purpose=$2 AND notice_version=$3 AND granted)`,
		userID, purpose, version, locale)
	return err
}

// Device is the API Device.
type Device struct {
	InstallationID string    `json:"installation_id"`
	Platform       string    `json:"platform"`
	FCMToken       string    `json:"fcm_token"`
	AppVersion     int       `json:"app_version"`
	Locale         string    `json:"locale"`
	DeviceLabel    *string   `json:"device_label"`
	Primary        bool      `json:"primary"`
	UpdatedAt      time.Time `json:"updated_at"`
}

const deviceCols = `installation_id, platform, fcm_token, app_version, locale, device_label, is_primary, updated_at`

func scanDevice(r pgx.Row) (Device, error) {
	var d Device
	err := r.Scan(&d.InstallationID, &d.Platform, &d.FCMToken, &d.AppVersion, &d.Locale, &d.DeviceLabel, &d.Primary, &d.UpdatedAt)
	return d, err
}

func upsertDevice(ctx context.Context, q db.Querier, userID, instID string, in DeviceInput) (Device, error) {
	// An installation id moves to the latest account that registers it (sign-out → sign-in as someone else).
	platform := in.Platform
	if platform == "" {
		platform = "android"
	}
	return scanDevice(q.QueryRow(ctx, `INSERT INTO device (installation_id, user_id, fcm_token, locale, app_version, device_label, platform, updated_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7,now())
		ON CONFLICT (installation_id) DO UPDATE SET user_id = EXCLUDED.user_id, fcm_token = EXCLUDED.fcm_token,
		  platform = EXCLUDED.platform,
		  locale = EXCLUDED.locale, app_version = EXCLUDED.app_version, device_label = EXCLUDED.device_label,
		  token_invalid_at = NULL, updated_at = now(),
		  is_primary = CASE WHEN device.user_id = EXCLUDED.user_id THEN device.is_primary ELSE false END
		RETURNING `+deviceCols, instID, userID, in.FCMToken, in.Locale, in.AppVersion, in.DeviceLabel, platform))
}

// UpsertDevice registers/refreshes an install. The first device of an account becomes primary.
func (s *Service) UpsertDevice(ctx context.Context, userID, instID string, in DeviceInput) (Device, error) {
	if err := in.Validate(""); err != nil {
		return Device{}, err
	}
	if instID == "" || len(instID) > 256 {
		return Device{}, problem.Validation("bad installation id")
	}
	var d Device
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		var err error
		if d, err = upsertDevice(ctx, tx, userID, instID, in); err != nil {
			return err
		}
		var hasPrimary bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM device WHERE user_id = $1 AND is_primary)`, userID).Scan(&hasPrimary); err != nil {
			return err
		}
		if !hasPrimary {
			if _, err := tx.Exec(ctx, `UPDATE device SET is_primary = true WHERE installation_id = $1`, instID); err != nil {
				return err
			}
			d.Primary = true
		}
		return nil
	})
	return d, err
}

func makePrimary(ctx context.Context, tx pgx.Tx, userID, instID string, label *string) error {
	var was bool
	err := tx.QueryRow(ctx, `SELECT is_primary FROM device WHERE installation_id = $1 AND user_id = $2`, instID, userID).Scan(&was)
	if db.IsNoRows(err) {
		return problem.New(404, problem.NotFound, "device")
	}
	if err != nil || was {
		return err
	}
	var others int
	if err := tx.QueryRow(ctx, `SELECT count(*) FROM device WHERE user_id = $1 AND installation_id <> $2`, userID, instID).Scan(&others); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `UPDATE device SET is_primary = false WHERE user_id = $1 AND is_primary`, userID); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `UPDATE device SET is_primary = true WHERE installation_id = $1`, instID); err != nil {
		return err
	}
	if others == 0 {
		return nil
	}
	l := ""
	if label != nil {
		l = *label
	} else {
		_ = tx.QueryRow(ctx, `SELECT COALESCE(device_label,'') FROM device WHERE installation_id = $1`, instID).Scan(&l)
	}
	return push.EnqueueAccountPush(ctx, tx, push.AccountPush{UserID: userID, Type: "account", Template: "N-47",
		ExceptInstallation: instID, Data: map[string]string{"device_label": l}})
}

// MakePrimary switches the alert install (B15); the others get N-47.
func (s *Service) MakePrimary(ctx context.Context, userID, instID string) (Device, error) {
	var d Device
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		if err := makePrimary(ctx, tx, userID, instID, nil); err != nil {
			return err
		}
		var err error
		d, err = scanDevice(tx.QueryRow(ctx, `SELECT `+deviceCols+` FROM device WHERE installation_id = $1`, instID))
		return err
	})
	return d, err
}

// Devices lists the account's installs.
func (s *Service) Devices(ctx context.Context, userID string) ([]Device, error) {
	rows, err := s.Pool.Query(ctx, `SELECT `+deviceCols+` FROM device WHERE user_id = $1 ORDER BY updated_at DESC`, userID)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (Device, error) { return scanDevice(r) })
}

// DeleteDevice unregisters an install (sign-out).
func (s *Service) DeleteDevice(ctx context.Context, userID, instID string) error {
	_, err := s.Pool.Exec(ctx, `DELETE FROM device WHERE installation_id = $1 AND user_id = $2`, instID, userID)
	return err
}

// Consent is the API Consent.
type Consent struct {
	Purpose    string    `json:"purpose"`
	Version    string    `json:"version"`
	Granted    bool      `json:"granted"`
	RecordedAt time.Time `json:"recorded_at"`
}

// ConsentPurposes are the account-level purposes (data-model §4).
var ConsentPurposes = []string{"privacy_notice", "cloud_backup", "family_link_owner", "family_link_member"}

// Consents returns the latest record per purpose.
func (s *Service) Consents(ctx context.Context, userID string) ([]Consent, error) {
	rows, err := s.Pool.Query(ctx, `SELECT DISTINCT ON (purpose) purpose, notice_version, granted, recorded_at FROM consent_log
		WHERE user_id = $1 AND purpose <> 'account_deleted' ORDER BY purpose, recorded_at DESC, id DESC`, userID)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (Consent, error) {
		var c Consent
		return c, r.Scan(&c.Purpose, &c.Version, &c.Granted, &c.RecordedAt)
	})
}

// RecordConsent appends to the consent log.
func (s *Service) RecordConsent(ctx context.Context, u *User, purpose, version string, granted bool, lang string) (Consent, error) {
	if !slices.Contains(ConsentPurposes, purpose) {
		return Consent{}, problem.Validation("unknown purpose", problem.FieldError{Field: "purpose", Code: "unknown_code"})
	}
	if version == "" || len(version) > 32 {
		return Consent{}, problem.Validation("version required", problem.FieldError{Field: "version", Code: "required"})
	}
	if !rules.KnownLocales[lang] {
		lang = u.Locale
	}
	var c Consent
	err := s.Pool.QueryRow(ctx, `INSERT INTO consent_log (user_id, purpose, notice_version, granted, locale) VALUES ($1,$2,$3,$4,$5)
		RETURNING purpose, notice_version, granted, recorded_at`, u.ID, purpose, version, granted, lang).
		Scan(&c.Purpose, &c.Version, &c.Granted, &c.RecordedAt)
	return c, err
}

// Backup is the API Backup.
type Backup struct {
	Ciphertext    string    `json:"ciphertext"`
	KeyID         string    `json:"key_id"`
	FormatVersion int       `json:"format_version"`
	Version       int64     `json:"version"`
	UpdatedAt     time.Time `json:"updated_at"`
}

// BackupInput is the API BackupInput.
type BackupInput struct {
	Ciphertext    string `json:"ciphertext"`
	KeyID         string `json:"key_id"`
	FormatVersion int    `json:"format_version"`
}

// GetBackup returns the blob or 404. format_version is stored as the first byte-free prefix in key_id
// (schema has no column): key_id is "<format_version>:<key id>".
func (s *Service) GetBackup(ctx context.Context, u *User) (*Backup, error) {
	if !u.Linked() { // code name kept for compatibility: "Google or Apple sign-in required"
		return nil, problem.New(403, problem.GoogleLinkRequired, "")
	}
	var b Backup
	var ct []byte
	var keyID string
	err := s.Pool.QueryRow(ctx, `SELECT version, ciphertext, key_id, updated_at FROM backup_blob WHERE user_id = $1`, u.ID).
		Scan(&b.Version, &ct, &keyID, &b.UpdatedAt)
	if db.IsNoRows(err) {
		return nil, problem.New(404, problem.NotFound, "no backup")
	}
	if err != nil {
		return nil, err
	}
	b.Ciphertext = base64.StdEncoding.EncodeToString(ct)
	b.FormatVersion, b.KeyID = splitKeyID(keyID)
	return &b, nil
}

func splitKeyID(s string) (int, string) {
	for i := 0; i < len(s); i++ {
		if s[i] == ':' {
			if n, err := strconv.Atoi(s[:i]); err == nil {
				return n, s[i+1:]
			}
			break
		}
	}
	return 1, s
}

// PutBackup stores the blob with compare-and-swap on version (If-Match). ifMatch < 0 means "no If-Match"
// (allowed only for the first write).
func (s *Service) PutBackup(ctx context.Context, u *User, in BackupInput, ifMatch int64) (*Backup, error) {
	if !u.Linked() { // code name kept for compatibility: "Google or Apple sign-in required"
		return nil, problem.New(403, problem.GoogleLinkRequired, "")
	}
	ct, err := base64.StdEncoding.DecodeString(in.Ciphertext)
	if err != nil || len(ct) == 0 {
		return nil, problem.Validation("ciphertext must be base64", problem.FieldError{Field: "ciphertext", Code: "invalid"})
	}
	if len(ct) > 65536 {
		return nil, problem.Validation("backup larger than 64 KB", problem.FieldError{Field: "ciphertext", Code: "too_large"})
	}
	if in.KeyID == "" || in.FormatVersion < 1 {
		return nil, problem.Validation("key_id and format_version are required")
	}
	keyID := fmt.Sprintf("%d:%s", in.FormatVersion, in.KeyID)
	var b Backup
	err = db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		var cur int64
		err := tx.QueryRow(ctx, `SELECT version FROM backup_blob WHERE user_id = $1 FOR UPDATE`, u.ID).Scan(&cur)
		exists := err == nil
		if err != nil && !db.IsNoRows(err) {
			return err
		}
		if (exists && ifMatch != cur) || (!exists && ifMatch >= 0) {
			p := problem.New(412, problem.BackupConflict, "")
			p.Details = map[string]any{"current_version": cur}
			return p
		}
		err = tx.QueryRow(ctx, `INSERT INTO backup_blob (user_id, version, ciphertext, key_id, updated_at) VALUES ($1,1,$2,$3,now())
			ON CONFLICT (user_id) DO UPDATE SET version = backup_blob.version + 1, ciphertext = EXCLUDED.ciphertext,
			  key_id = EXCLUDED.key_id, updated_at = now()
			RETURNING version, updated_at`, u.ID, ct, keyID).Scan(&b.Version, &b.UpdatedAt)
		return err
	})
	if err != nil {
		return nil, err
	}
	b.Ciphertext, b.KeyID, b.FormatVersion = in.Ciphertext, in.KeyID, in.FormatVersion
	return &b, nil
}

// DeleteBackup erases the blob.
func (s *Service) DeleteBackup(ctx context.Context, u *User) error {
	_, err := s.Pool.Exec(ctx, `DELETE FROM backup_blob WHERE user_id = $1`, u.ID)
	return err
}

// DeletionStatus is the API DeletionStatus.
type DeletionStatus struct {
	State        string     `json:"state"`
	HardDeleteAt *time.Time `json:"hard_delete_at"`
	// AppleRevocation (DELETE /v1/me only): queued = the Sign in with Apple refresh token is being revoked;
	// token_missing = Apple-linked but the server holds no token and no apple_authorization_code was sent (the
	// app should run Sign in with Apple again and repeat DELETE with the code); omitted otherwise.
	AppleRevocation string `json:"apple_revocation,omitempty"`
}

// Status returns the deletion status of u.
func Status(u *User) DeletionStatus {
	switch u.State {
	case "pending_deletion":
		return DeletionStatus{State: "pending", HardDeleteAt: u.HardDeleteAt}
	case "deleted":
		return DeletionStatus{State: "deleted"}
	}
	return DeletionStatus{State: "none"}
}

// DeletionInput carries the optional Sign in with Apple code for token revocation.
type DeletionInput struct {
	Reason                 string
	AppleAuthorizationCode string
	// AppleIdentity: the caller's current token is a Sign in with Apple token (sign_in_provider apple.com).
	AppleIdentity bool
}

// RequestDeletion: pending_deletion + hard_delete_at = now + 7 d, family links removed (the other side is told),
// devices and backup removed, Firebase user disabled, Sign in with Apple tokens revoked (job apple_revoke, App
// Store guideline 5.1.1(v)). Play and App Store subscriptions are not cancelled here (only the user can, in the
// store).
func (s *Service) RequestDeletion(ctx context.Context, u *User, in DeletionInput) (DeletionStatus, error) {
	at := s.now().Add(DeletionGrace)
	revocation := ""
	var fresh []byte // sealed refresh token from a code sent with this request
	if in.AppleAuthorizationCode != "" && s.Apple != nil && s.Seal != nil {
		rt, err := s.Apple.Exchange(ctx, in.AppleAuthorizationCode)
		if err != nil {
			slog.WarnContext(ctx, "sign in with apple code exchange at deletion failed", "err", err)
		} else {
			fresh = s.Seal.Seal([]byte(rt))
		}
	}
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		var stored []byte
		err := tx.QueryRow(ctx, `DELETE FROM apple_credential WHERE user_id = $1 RETURNING refresh_token_enc`, u.ID).Scan(&stored)
		if err != nil && !db.IsNoRows(err) {
			return err
		}
		for i, enc := range [][]byte{stored, fresh} {
			if enc == nil {
				continue
			}
			revocation = "queued"
			if err := db.EnqueueJob(ctx, tx, "apple_revoke", map[string]string{"token_enc": base64.StdEncoding.EncodeToString(enc)},
				fmt.Sprintf("apple_revoke:%s:%d:%d", u.ID, s.now().UnixNano(), i), time.Time{}); err != nil {
				return err
			}
		}
		if revocation == "" && (u.AuthKind == "apple" || in.AppleIdentity) {
			revocation = "token_missing"
		}
		if u.State == "pending_deletion" {
			at = *u.HardDeleteAt
			return nil
		}
		if _, err := tx.Exec(ctx, `UPDATE app_user SET state = 'pending_deletion', deletion_requested_at = now(), hard_delete_at = $2 WHERE id = $1`, u.ID, at); err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `UPDATE family_link SET state = 'removed', ended_at = now()
			WHERE (owner_user_id = $1 OR member_user_id = $1) AND state IN ('invited','active')
			RETURNING id, CASE WHEN owner_user_id = $1 THEN member_user_id ELSE owner_user_id END`, u.ID)
		if err != nil {
			return err
		}
		type ended struct {
			id    string
			other *string
		}
		links, err := pgx.CollectRows(rows, func(r pgx.CollectableRow) (ended, error) {
			var e ended
			return e, r.Scan(&e.id, &e.other)
		})
		if err != nil {
			return err
		}
		for _, l := range links {
			if _, err := tx.Exec(ctx, `DELETE FROM family_watchlist WHERE link_id = $1`, l.id); err != nil {
				return err
			}
			if l.other != nil {
				if err := push.EnqueueAccountPush(ctx, tx, push.AccountPush{UserID: *l.other, Type: "family", Template: "family_removed",
					Data: map[string]string{"family_link_id": l.id, "family_event": "removed"}, MsgID: "family_removed:" + l.id}); err != nil {
					return err
				}
			}
		}
		for _, q := range []string{`DELETE FROM device WHERE user_id = $1`, `DELETE FROM backup_blob WHERE user_id = $1`} {
			if _, err := tx.Exec(ctx, q, u.ID); err != nil {
				return err
			}
		}
		return db.Audit(ctx, tx, u.ID, "delete_account_requested", "app_user", u.ID, nil,
			map[string]any{"reason": in.Reason, "hard_delete_at": at, "apple_revocation": revocation})
	})
	if err != nil {
		return DeletionStatus{}, err
	}
	if s.FirebaseAdmin != nil {
		if err := s.FirebaseAdmin.DisableUser(ctx, u.FirebaseUID); err != nil {
			return DeletionStatus{}, err
		}
	}
	return DeletionStatus{State: "pending", HardDeleteAt: &at, AppleRevocation: revocation}, nil
}

// CancelDeletion restores a pending account.
func (s *Service) CancelDeletion(ctx context.Context, u *User) (DeletionStatus, error) {
	if u.State != "pending_deletion" {
		return Status(u), nil
	}
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `UPDATE app_user SET state = 'active', deletion_requested_at = NULL, hard_delete_at = NULL WHERE id = $1`, u.ID); err != nil {
			return err
		}
		return db.Audit(ctx, tx, u.ID, "delete_account_cancelled", "app_user", u.ID, nil, nil)
	})
	return DeletionStatus{State: "none"}, err
}

// HardDeleteDue deletes accounts whose grace period ended (cascades devices, backup, family, grants;
// purchases are unlinked) and writes the account_deleted consent row. Returns how many were deleted.
func (s *Service) HardDeleteDue(ctx context.Context) (int, error) {
	rows, err := s.Pool.Query(ctx, `SELECT id, firebase_uid, locale FROM app_user WHERE state = 'pending_deletion' AND hard_delete_at <= $1 LIMIT 100`, s.now())
	if err != nil {
		return 0, err
	}
	type due struct{ id, uid, locale string }
	list, err := pgx.CollectRows(rows, func(r pgx.CollectableRow) (due, error) {
		var d due
		return d, r.Scan(&d.id, &d.uid, &d.locale)
	})
	if err != nil {
		return 0, err
	}
	n := 0
	for _, d := range list {
		err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
			if _, err := tx.Exec(ctx, `DELETE FROM app_user WHERE id = $1 AND state = 'pending_deletion'`, d.id); err != nil {
				return err
			}
			if _, err := tx.Exec(ctx, `INSERT INTO consent_log (user_id, purpose, notice_version, granted, locale) VALUES ($1,'account_deleted','-',false,$2)`, d.id, d.locale); err != nil {
				return err
			}
			return db.Audit(ctx, tx, "system", "delete_account_hard", "app_user", d.id, nil, nil)
		})
		if err != nil {
			return n, err
		}
		if s.FirebaseAdmin != nil {
			_ = s.FirebaseAdmin.DeleteUser(ctx, d.uid)
		}
		n++
	}
	return n, nil
}

// Summary is the GET /v1/me body (DPDP access summary).
type Summary struct {
	ID                string         `json:"id"`
	CreatedAt         time.Time      `json:"created_at"`
	Locale            string         `json:"locale"`
	AuthProvider      string         `json:"auth_provider"`
	GoogleEmail       *string        `json:"google_email"`
	BillingAccountRef string         `json:"billing_account_ref"`
	DevicesCount      int            `json:"devices_count"`
	Backup            *BackupSummary `json:"backup"`
	FamilyLinksCount  int            `json:"family_links_count"`
	Consents          []Consent      `json:"consents"`
	Deletion          DeletionStatus `json:"deletion"`
}

// BackupSummary is User.backup.
type BackupSummary struct {
	Version   int64     `json:"version"`
	UpdatedAt time.Time `json:"updated_at"`
	SizeBytes int       `json:"size_bytes"`
}

// Summarize builds the User object; email comes from the verified token, never from the database.
func (s *Service) Summarize(ctx context.Context, u *User, email string) (*Summary, error) {
	sum := &Summary{ID: u.ID, CreatedAt: u.CreatedAt, Locale: u.Locale, AuthProvider: u.AuthKind,
		BillingAccountRef: u.BillingAccountRef, Deletion: Status(u)}
	if email != "" && u.AuthKind == "google" {
		sum.GoogleEmail = &email
	}
	var bv *int64
	var bu *time.Time
	var bs *int
	err := s.Pool.QueryRow(ctx, `SELECT
		(SELECT count(*) FROM device WHERE user_id = $1),
		(SELECT count(*) FROM family_link WHERE (owner_user_id = $1 OR member_user_id = $1) AND state IN ('invited','active')),
		b.version, b.updated_at, octet_length(b.ciphertext)
		FROM (SELECT 1) one LEFT JOIN backup_blob b ON b.user_id = $1`, u.ID).Scan(&sum.DevicesCount, &sum.FamilyLinksCount, &bv, &bu, &bs)
	if err != nil {
		return nil, err
	}
	if bv != nil {
		sum.Backup = &BackupSummary{Version: *bv, UpdatedAt: *bu, SizeBytes: *bs}
	}
	if sum.Consents, err = s.Consents(ctx, u.ID); err != nil {
		return nil, err
	}
	return sum, nil
}
