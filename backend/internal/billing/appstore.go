package billing

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/appstore"
	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/ids"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/amanroy3901/job-vac/backend/internal/seal"
	"github.com/jackc/pgx/v5"
)

// SeasonMonths is the length of the 4-month season (Play P4M prepaid plan; App Store non-renewing subscription,
// whose duration the developer applies — Apple stores no expiresDate for non-renewing subscriptions).
const SeasonMonths = 4

// appStoreTypeOf maps product.kind to the App Store product type.
func appStoreTypeOf(kind string) string {
	if strings.HasPrefix(kind, "prepaid") {
		return "non_renewing"
	}
	return "auto_renewable"
}

// AppStorePurchaseInput is the verifyAppStorePurchase body.
type AppStorePurchaseInput struct {
	// SignedTransaction is StoreKit 2 `VerificationResult<Transaction>.jwsRepresentation`.
	SignedTransaction string `json:"signed_transaction"`
	// SignedRenewalInfo is `Product.SubscriptionInfo.Status.renewalInfo.jwsRepresentation` (auto-renewable only;
	// optional — without it the server asks the App Store Server API when configured).
	SignedRenewalInfo *string `json:"signed_renewal_info"`
}

// AppStoreEvent is one verified App Store observation (client verify or notification).
type AppStoreEvent struct {
	Tx       *appstore.Transaction
	Renewal  *appstore.RenewalInfo
	Status   int    // notification data.status / Server API status (0 = unknown)
	Type     string // notificationType ("" for a client verify)
	Subtype  string
	SignedAt time.Time // ordering key: newest of the notification / transaction / renewal signedDate
}

// AppStoreState derives purchase.state, the access expiry and auto-renew for an auto-renewable subscription
// (pure; unit-tested). Mapping (payments-and-entitlements.md §2b):
//
//	revocationDate set           → refunded (REFUND) | revoked (REVOKE / FAMILY_REVOKE / status 5)
//	status 4 or retry+grace>now  → in_grace, access until gracePeriodExpiresDate
//	status 3 or retry, no grace  → on_hold (billing retry without access; Play "account hold")
//	status 2 or expiresDate past → expired
//	active, autoRenewStatus 0    → canceled (access to expiresDate, auto_renewing=false)
//	active                       → active
func AppStoreState(ev AppStoreEvent, now time.Time) (state string, expiry time.Time, autoRenew bool) {
	tx, r := ev.Tx, ev.Renewal
	expiry = appstore.Millis(tx.ExpiresDate)
	autoRenew = r == nil || r.AutoRenewStatus == 1
	if tx.RevocationDate != 0 || ev.Status == appstore.StatusRevoked {
		if ev.Type == "REVOKE" || tx.RevocationType == "FAMILY_REVOKE" || (tx.RevocationDate == 0 && ev.Status == appstore.StatusRevoked) {
			return "revoked", expiry, false
		}
		return "refunded", expiry, false
	}
	grace := time.Time{}
	if r != nil {
		grace = appstore.Millis(r.GracePeriodExpiresDate)
	}
	switch {
	case ev.Status == appstore.StatusGracePeriod || (r.InBillingRetry() && grace.After(now)):
		if grace.After(expiry) {
			expiry = grace
		}
		return "in_grace", expiry, true
	case ev.Status == appstore.StatusBillingRetry || (ev.Status == 0 && r.InBillingRetry() && !expiry.After(now)):
		return "on_hold", expiry, true
	case ev.Status == appstore.StatusExpired || !expiry.After(now):
		return "expired", expiry, false
	case !autoRenew:
		return "canceled", expiry, false
	}
	return "active", expiry, true
}

func appStoreErr(err error) error {
	switch {
	case errors.Is(err, appstore.ErrWrongApp), errors.Is(err, appstore.ErrEnvironment):
		return problem.New(402, problem.PurchaseInvalid, err.Error())
	default:
		return problem.New(402, problem.PurchaseInvalid, "signed transaction does not verify")
	}
}

// VerifyAppStore verifies a StoreKit 2 transaction JWS for the caller, links it, grants and returns the
// entitlement. The JWS itself is Apple-signed (x5c → Apple Root CA G3), so no network call is needed; when the
// App Store Server API is configured and the client sent no renewal info, the server fetches the latest
// transaction and renewal info for auto-renewable subscriptions (fresher than an old client JWS).
func (s *Service) VerifyAppStore(ctx context.Context, acct Account, in AppStorePurchaseInput) (*Entitlement, error) {
	if s.AppStore == nil {
		p := problem.New(503, problem.Internal, "App Store purchases are not configured")
		p.Retryable, p.RetryAfter = true, 300
		return nil, p
	}
	if in.SignedTransaction == "" || len(in.SignedTransaction) > 64<<10 {
		return nil, problem.Validation("signed_transaction is required",
			problem.FieldError{Field: "signed_transaction", Code: "required"})
	}
	tx, err := s.AppStore.VerifyTransaction(in.SignedTransaction)
	if err != nil {
		slog.WarnContext(ctx, "app store transaction rejected", "err", err)
		return nil, appStoreErr(err)
	}
	ev := AppStoreEvent{Tx: tx, SignedAt: appstore.Millis(tx.SignedDate)}
	if in.SignedRenewalInfo != nil && *in.SignedRenewalInfo != "" {
		r, err := s.AppStore.VerifyRenewalInfo(*in.SignedRenewalInfo)
		if err != nil {
			return nil, appStoreErr(err)
		}
		if r.OriginalTransactionID != tx.OriginalTransactionID {
			return nil, problem.Validation("signed_renewal_info belongs to another subscription",
				problem.FieldError{Field: "signed_renewal_info", Code: "invalid"})
		}
		ev.Renewal = r
	} else if s.AppStoreAPI != nil && tx.Type == appstore.TypeAutoRenewable {
		s.refreshFromAPI(ctx, &ev)
	}
	if err := s.applyAppStore(ctx, &acct, ev); err != nil {
		return nil, err
	}
	return s.Get(ctx, acct.ID)
}

// refreshFromAPI replaces ev with the Server API's latest transaction/renewal for the same subscription. Failures
// are logged and ignored: the client's Apple-signed JWS is still valid evidence.
func (s *Service) refreshFromAPI(ctx context.Context, ev *AppStoreEvent) {
	lts, err := s.AppStoreAPI.GetSubscriptionStatuses(ctx, ev.Tx.OriginalTransactionID)
	if err != nil {
		slog.WarnContext(ctx, "app store server api unavailable; using the client transaction", "err", err)
		return
	}
	for _, lt := range lts {
		if lt.OriginalTransactionID != ev.Tx.OriginalTransactionID {
			continue
		}
		tx, err := s.AppStore.VerifyTransaction(lt.SignedTransactionInfo)
		if err != nil {
			slog.WarnContext(ctx, "server api transaction does not verify", "err", err)
			return
		}
		ev.Tx, ev.Status = tx, lt.Status
		if lt.SignedRenewalInfo != "" {
			if r, err := s.AppStore.VerifyRenewalInfo(lt.SignedRenewalInfo); err == nil {
				ev.Renewal = r
			}
		}
		ev.SignedAt = laterOf(appstore.Millis(tx.SignedDate), ev.SignedAt)
		return
	}
}

func laterOf(a, b time.Time) time.Time {
	if a.After(b) {
		return a
	}
	return b
}

// HandleAppStoreNotification processes one App Store Server Notifications V2 POST body {"signedPayload"}.
// The signed payload (and the transaction/renewal JWS inside it) is verified; state is derived from the signed
// transaction, renewal info and data.status, never from the notification type alone. Duplicate deliveries
// (Apple retries on any non-2xx) are deduped by notificationUUID. Returning nil means "respond 200".
func (s *Service) HandleAppStoreNotification(ctx context.Context, signedPayload string) error {
	if s.AppStore == nil {
		return problem.New(503, problem.Internal, "App Store notifications are not configured")
	}
	n, err := s.AppStore.VerifyNotification(signedPayload)
	if err != nil {
		slog.WarnContext(ctx, "app store notification rejected", "err", err)
		return problem.New(401, problem.Unauthenticated, "signedPayload does not verify")
	}
	log := slog.With("type", n.NotificationType, "subtype", n.Subtype, "uuid", n.NotificationUUID)
	var seen bool
	if err := s.Pool.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM app_store_notification WHERE notification_uuid = $1)`,
		n.NotificationUUID).Scan(&seen); err != nil {
		return err
	}
	if seen {
		log.InfoContext(ctx, "app store notification already processed")
		return nil
	}
	switch n.NotificationType {
	case "TEST":
		log.InfoContext(ctx, "app store test notification")
	case "CONSUMPTION_REQUEST":
		// We do not send consumption information (optional; Apple then decides the refund on its own data).
		log.InfoContext(ctx, "app store consumption request (not answered)")
	}
	if n.Data != nil && n.Data.SignedTransactionInfo != "" {
		tx, err := s.AppStore.VerifyTransaction(n.Data.SignedTransactionInfo)
		if err != nil {
			log.WarnContext(ctx, "notification transaction rejected", "err", err)
			return problem.New(401, problem.Unauthenticated, "signedTransactionInfo does not verify")
		}
		ev := AppStoreEvent{Tx: tx, Status: n.Data.Status, Type: n.NotificationType, Subtype: n.Subtype,
			SignedAt: laterOf(appstore.Millis(n.SignedDate), appstore.Millis(tx.SignedDate))}
		if n.Data.SignedRenewalInfo != "" {
			if ev.Renewal, err = s.AppStore.VerifyRenewalInfo(n.Data.SignedRenewalInfo); err != nil {
				return problem.New(401, problem.Unauthenticated, "signedRenewalInfo does not verify")
			}
		}
		err = s.applyAppStore(ctx, nil, ev)
		if p := problem.As(err); p != nil && p.Status < 500 {
			log.WarnContext(ctx, "app store notification not applied", "code", p.Code, "detail", p.Detail)
			err = nil
		}
		if err != nil {
			return err // 5xx → Apple retries (1, 12, 24, 48, 72 h)
		}
	}
	_, err = s.Pool.Exec(ctx, `INSERT INTO app_store_notification (notification_uuid, notification_type, subtype)
		VALUES ($1,$2,NULLIF($3,'')) ON CONFLICT DO NOTHING`, n.NotificationUUID, n.NotificationType, n.Subtype)
	return err
}

// applyAppStore upserts the purchase (one row per originalTransactionId) and its grant, then recomputes the
// owner's entitlement. acct is the verifying caller (nil for notifications: the owner is the linked user, else
// the account whose billing_account_ref equals the transaction's appAccountToken).
func (s *Service) applyAppStore(ctx context.Context, acct *Account, ev AppStoreEvent) error {
	tx := ev.Tx
	var productID, kind string
	err := s.Pool.QueryRow(ctx, `SELECT id, kind FROM product WHERE appstore_product_id = $1`, tx.ProductID).Scan(&productID, &kind)
	if db.IsNoRows(err) {
		return problem.New(402, problem.PurchaseInvalid, "unknown App Store product "+tx.ProductID)
	}
	if err != nil {
		return err
	}
	nonRenewing := appStoreTypeOf(kind) == "non_renewing"
	if (nonRenewing && tx.Type != appstore.TypeNonRenewing) || (!nonRenewing && tx.Type != appstore.TypeAutoRenewable) {
		return problem.New(402, problem.PurchaseInvalid, fmt.Sprintf("product %s has type %q", tx.ProductID, tx.Type))
	}
	now := s.now()
	var state string
	var expiry time.Time
	var autoRenew bool
	if nonRenewing {
		state = "active"
		if tx.RevocationDate != 0 {
			state = "refunded"
		}
	} else {
		state, expiry, autoRenew = AppStoreState(ev, now)
	}
	src := map[string]string{"prepaid_season": "appstore_season", "auto_renew_year": "appstore_year"}[kind]
	if src == "" {
		return problem.New(402, problem.PurchaseInvalid, "product kind "+kind+" is not sold on the App Store")
	}
	hash := seal.Hash("appstore:" + tx.OriginalTransactionID)
	raw, _ := json.Marshal(map[string]any{"transaction": tx, "renewal": ev.Renewal, "status": ev.Status,
		"notification_type": ev.Type, "subtype": ev.Subtype})
	var userID string
	err = db.InTx(ctx, s.Pool, func(q pgx.Tx) error {
		var purchaseID string
		var curUser *string
		var lastEvent, curExpiry *time.Time
		err := q.QueryRow(ctx, `SELECT id, user_id, last_event_at, expiry_at FROM purchase WHERE token_hash = $1 FOR UPDATE`, hash).
			Scan(&purchaseID, &curUser, &lastEvent, &curExpiry)
		if err != nil && !db.IsNoRows(err) {
			return err
		}
		owner, err := s.resolveOwner(ctx, q, acct, curUser, strings.ToLower(tx.AppAccountToken))
		if err != nil {
			return err
		}
		userID = owner
		var uid any
		if userID != "" {
			uid = userID
		} else {
			slog.WarnContext(ctx, "app store purchase without a known owner; stored unlinked", "state", state)
		}
		// Out-of-order delivery: an older signed observation never overwrites a newer one.
		stale := lastEvent != nil && !ev.SignedAt.IsZero() && ev.SignedAt.Before(*lastEvent)
		switch {
		case purchaseID == "":
			purchaseID = ids.New("pur")
			_, err = q.Exec(ctx, `INSERT INTO purchase (id, user_id, provider, product_id, token_hash, order_id, state, expiry_at,
				auto_renewing, last_verified_at, raw, test_purchase, original_transaction_id, environment, last_event_at)
				VALUES ($1,$2,'app_store',$3,$4,$5,$6,$7,$8,now(),$9,$10,$11,$12,$13)`,
				purchaseID, uid, productID, hash, tx.TransactionID, state, nullTime(expiry), autoRenew, raw,
				tx.Environment != appstore.EnvProduction, tx.OriginalTransactionID, tx.Environment, nullTime(ev.SignedAt))
		case stale:
			slog.InfoContext(ctx, "stale app store observation ignored", "type", ev.Type)
			_, err = q.Exec(ctx, `UPDATE purchase SET user_id = COALESCE($2, user_id) WHERE id = $1`, purchaseID, uid)
		default:
			if nonRenewing && curExpiry != nil {
				expiry = *curExpiry
			}
			_, err = q.Exec(ctx, `UPDATE purchase SET user_id = COALESCE($2, user_id), state=$3, expiry_at=COALESCE($4, expiry_at),
				auto_renewing=$5, order_id=$6, last_verified_at=now(), raw=$7, last_event_at=$8, updated_at=now() WHERE id=$1`,
				purchaseID, uid, state, nullTime(expiry), autoRenew, tx.TransactionID, raw, nullTime(ev.SignedAt))
		}
		if err != nil {
			return err
		}
		if userID == "" {
			return nil
		}
		if stale {
			_, err := s.Recompute(ctx, q, userID)
			return err
		}
		switch state {
		case "active", "in_grace", "canceled":
			if nonRenewing {
				if expiry, err = s.seasonGrant(ctx, q, userID, purchaseID, src, tx); err != nil {
					return err
				}
				if _, err := q.Exec(ctx, `UPDATE purchase SET expiry_at = $2, state = CASE WHEN $2 <= now() THEN 'expired' ELSE state END
					WHERE id = $1`, purchaseID, expiry); err != nil {
					return err
				}
				if !expiry.After(now) {
					state = "expired"
				}
				break
			}
			start := appstore.Millis(tx.PurchaseDate)
			if tx.OriginalPurchaseDate != 0 {
				start = appstore.Millis(tx.OriginalPurchaseDate)
			}
			_, err = q.Exec(ctx, `INSERT INTO entitlement_grant (user_id, source, starts_at, ends_at, purchase_id, note)
				VALUES ($1,$2::ent_source_t,$3,$4,$5,$6)
				ON CONFLICT (purchase_id) WHERE purchase_id IS NOT NULL DO UPDATE SET user_id = EXCLUDED.user_id,
				  ends_at = EXCLUDED.ends_at, revoked_at = NULL, note = EXCLUDED.note`,
				userID, src, start, expiry, purchaseID, grantNote(tx))
		case "expired", "on_hold":
			_, err = q.Exec(ctx, `UPDATE entitlement_grant SET ends_at = LEAST(ends_at, now()) WHERE purchase_id = $1`, purchaseID)
		case "refunded", "revoked":
			_, err = q.Exec(ctx, `UPDATE entitlement_grant SET revoked_at = now() WHERE purchase_id = $1 AND revoked_at IS NULL`, purchaseID)
		}
		if err != nil {
			return err
		}
		if tx.IsFreeTrial() { // one free trial per account across the server trial and the App Store intro offer
			if _, err := q.Exec(ctx, `INSERT INTO entitlement_grant (user_id, source, starts_at, ends_at, note)
				SELECT $1, 'trial', $2, $2, 'app store intro free trial (marks trial used)'
				WHERE NOT EXISTS (SELECT 1 FROM entitlement_grant WHERE user_id = $1 AND source = 'trial')`,
				userID, appstore.Millis(tx.PurchaseDate)); err != nil {
				return err
			}
		}
		_, err = s.Recompute(ctx, q, userID)
		return err
	})
	if err != nil {
		return err
	}
	if acct != nil && state != "active" && state != "in_grace" && state != "canceled" {
		return problem.New(402, problem.PurchaseInvalid, "subscription state "+state)
	}
	return nil
}

func grantNote(tx *appstore.Transaction) string {
	if tx.IsFreeTrial() {
		return "app store intro free trial"
	}
	return "app store " + strings.ToLower(tx.TransactionReason)
}

// seasonGrant creates (once) the grant of a non-renewing season: it starts at purchaseDate or, when the user
// already has season time left (either store), at the end of it — a top-up extends, it never overlaps (the same
// semantics as a Play prepaid top-up). An existing grant keeps its dates (retries and notifications are
// idempotent). Returns the grant's end.
func (s *Service) seasonGrant(ctx context.Context, q pgx.Tx, userID, purchaseID, src string, tx *appstore.Transaction) (time.Time, error) {
	var end time.Time
	err := q.QueryRow(ctx, `UPDATE entitlement_grant SET user_id = $2, revoked_at = NULL WHERE purchase_id = $1 RETURNING ends_at`,
		purchaseID, userID).Scan(&end)
	if err == nil {
		return end, nil
	}
	if !db.IsNoRows(err) {
		return end, err
	}
	start := appstore.Millis(tx.PurchaseDate)
	var seasonEnd *time.Time
	if err := q.QueryRow(ctx, `SELECT max(ends_at) FROM entitlement_grant WHERE user_id = $1 AND revoked_at IS NULL
		AND source IN ('play_season','appstore_season') AND ends_at > $2`, userID, start).Scan(&seasonEnd); err != nil {
		return end, err
	}
	if seasonEnd != nil {
		start = *seasonEnd
	}
	end = start.AddDate(0, SeasonMonths, 0)
	_, err = q.Exec(ctx, `INSERT INTO entitlement_grant (user_id, source, starts_at, ends_at, purchase_id, note)
		VALUES ($1,$2::ent_source_t,$3,$4,$5,'app store non-renewing season')`, userID, src, start, end, purchaseID)
	return end, err
}
