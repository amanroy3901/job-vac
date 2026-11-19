package billing

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/ids"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/amanroy3901/job-vac/backend/internal/seal"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// TrialDays is the server-granted trial length (payments §5).
const TrialDays = 14

// Service is the entitlement source of truth.
type Service struct {
	Pool        *pgxpool.Pool
	Play        PlayClient
	PackageName string
	Seal        *seal.Sealer
	Signer      *Signer
	Now         func() time.Time
}

func (s *Service) now() time.Time {
	if s.Now != nil {
		return s.Now()
	}
	return time.Now()
}

// Entitlement is the API Entitlement object.
type Entitlement struct {
	Plan           string          `json:"plan"`
	Status         string          `json:"status"`
	Source         *string         `json:"source"`
	StartsAt       *time.Time      `json:"starts_at"`
	ExpiresAt      *time.Time      `json:"expires_at"`
	AutoRenewing   bool            `json:"auto_renewing"`
	TrialAvailable bool            `json:"trial_available"`
	AdsEnabled     bool            `json:"ads_enabled"`
	Features       map[string]bool `json:"features"`
	Token          string          `json:"token,omitempty"`
	ManageURL      *string         `json:"manage_url"`
}

// Recompute derives user_entitlement from grants and the latest Play purchase, persists it and, when it changed,
// enqueues a silent `account` push (template `entitlement`) so the app refetches.
func (s *Service) Recompute(ctx context.Context, q db.Querier, userID string) (State, error) {
	rows, err := q.Query(ctx, `SELECT source::text, starts_at, ends_at, revoked_at FROM entitlement_grant WHERE user_id = $1`, userID)
	if err != nil {
		return State{}, err
	}
	grants, err := pgx.CollectRows(rows, func(r pgx.CollectableRow) (Grant, error) {
		var g Grant
		return g, r.Scan(&g.Source, &g.StartsAt, &g.EndsAt, &g.RevokedAt)
	})
	if err != nil {
		return State{}, err
	}
	var latest *PlayState
	var ps PlayState
	err = q.QueryRow(ctx, `SELECT state, COALESCE(auto_renewing,false), expiry_at FROM purchase WHERE user_id = $1
		ORDER BY updated_at DESC LIMIT 1`, userID).Scan(&ps.State, &ps.AutoRenewing, &ps.ExpiryAt)
	if err == nil {
		latest = &ps
	} else if !db.IsNoRows(err) {
		return State{}, err
	}
	st := Compute(grants, latest, s.now())
	var prev struct {
		plan, status string
		exp          *time.Time
	}
	perr := q.QueryRow(ctx, `SELECT plan, status, expires_at FROM user_entitlement WHERE user_id = $1`, userID).
		Scan(&prev.plan, &prev.status, &prev.exp)
	_, err = q.Exec(ctx, `INSERT INTO user_entitlement (user_id, plan, status, source, starts_at, expires_at, auto_renewing, trial_used, updated_at)
		VALUES ($1,$2,$3,$4::ent_source_t,$5,$6,$7,$8,now())
		ON CONFLICT (user_id) DO UPDATE SET plan=EXCLUDED.plan, status=EXCLUDED.status, source=EXCLUDED.source,
		  starts_at=EXCLUDED.starts_at, expires_at=EXCLUDED.expires_at, auto_renewing=EXCLUDED.auto_renewing,
		  trial_used=EXCLUDED.trial_used, updated_at=now()`,
		userID, st.Plan, st.Status, st.Source, st.StartsAt, st.ExpiresAt, st.AutoRenewing, st.TrialUsed)
	if err != nil {
		return State{}, err
	}
	changed := perr != nil || prev.plan != st.Plan || prev.status != st.Status || !timeEq(prev.exp, st.ExpiresAt)
	if changed && perr == nil {
		err = push.EnqueueAccountPush(ctx, q, push.AccountPush{UserID: userID, Type: "account", Template: "entitlement",
			Data: map[string]string{"sync": "0"}})
	}
	return st, err
}

func timeEq(a, b *time.Time) bool {
	if a == nil || b == nil {
		return a == b
	}
	return a.Equal(*b)
}

// Get returns the API entitlement with a fresh signed token.
func (s *Service) Get(ctx context.Context, userID string) (*Entitlement, error) {
	st, err := s.Recompute(ctx, s.Pool, userID)
	if err != nil {
		return nil, err
	}
	return s.view(userID, st)
}

func (s *Service) view(userID string, st State) (*Entitlement, error) {
	pro := st.Plan == "pro"
	e := &Entitlement{Plan: st.Plan, Status: st.Status, Source: st.Source, StartsAt: st.StartsAt, ExpiresAt: st.ExpiresAt,
		AutoRenewing: st.AutoRenewing, TrialAvailable: !st.TrialUsed && !pro, AdsEnabled: !pro, Features: Features(pro)}
	if st.Source != nil && isPlay(*st.Source) || st.Status == "on_hold" {
		u := "https://play.google.com/store/account/subscriptions?package=" + s.PackageName + "&sku=pro"
		e.ManageURL = &u
	}
	tok, err := s.Signer.Sign(userID, st, e.AdsEnabled, s.now())
	if err != nil {
		return nil, err
	}
	e.Token = tok
	return e, nil
}

// StartTrial grants 14 days of Pro from now (once per account; one_trial_per_user index).
func (s *Service) StartTrial(ctx context.Context, userID string) (*Entitlement, error) {
	var st State
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		now := s.now()
		_, err := tx.Exec(ctx, `INSERT INTO entitlement_grant (user_id, source, starts_at, ends_at, note) VALUES ($1,'trial',$2,$3,'B02 tap')`,
			userID, now, now.AddDate(0, 0, TrialDays))
		if db.IsUniqueViolation(err, "one_trial_per_user") {
			return problem.New(409, problem.TrialAlreadyUsed, "")
		}
		if err != nil {
			return err
		}
		if st, err = s.Recompute(ctx, tx, userID); err != nil {
			return err
		}
		// Day-12 trial-ending push N-31 (skipped by the worker if the trial is no longer the active source).
		return push.EnqueueAccountPush(ctx, tx, push.AccountPush{UserID: userID, Type: "account", Template: "N-31",
			MsgID: "N-31:" + userID, RunAt: now.AddDate(0, 0, TrialDays-2), OnlyIfSource: "trial"})
	})
	if err != nil {
		return nil, err
	}
	return s.view(userID, st)
}

// PlayPurchaseInput is the verifyPlayPurchase body.
type PlayPurchaseInput struct {
	ProductID     string  `json:"product_id"`
	BasePlanID    *string `json:"base_plan_id"`
	PurchaseToken string  `json:"purchase_token"`
	OrderID       *string `json:"order_id"`
}

// Account is the caller of VerifyPlay.
type Account struct {
	ID                string
	BillingAccountRef string
}

var stateMap = map[string]string{
	"SUBSCRIPTION_STATE_ACTIVE": "active", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD": "in_grace",
	"SUBSCRIPTION_STATE_ON_HOLD": "on_hold", "SUBSCRIPTION_STATE_PAUSED": "paused",
	"SUBSCRIPTION_STATE_CANCELED": "canceled", "SUBSCRIPTION_STATE_EXPIRED": "expired",
	"SUBSCRIPTION_STATE_PENDING": "pending", "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED": "canceled",
}

// VerifyPlay verifies a purchase token with the Play Developer API, links it to the caller, grants, acknowledges
// and returns the entitlement (payments §2 checks 1–5).
func (s *Service) VerifyPlay(ctx context.Context, acct Account, in PlayPurchaseInput) (*Entitlement, error) {
	if in.PurchaseToken == "" || in.ProductID == "" {
		return nil, problem.Validation("product_id and purchase_token are required")
	}
	sub, err := s.Play.GetSubscription(ctx, s.PackageName, in.PurchaseToken)
	if err != nil {
		return nil, playErr(err)
	}
	if err := s.applySubscription(ctx, &acct, in.PurchaseToken, sub); err != nil {
		return nil, err
	}
	return s.Get(ctx, acct.ID)
}

func playErr(err error) error {
	if errors.Is(err, ErrPlayTokenInvalid) {
		return problem.New(402, problem.PurchaseInvalid, "")
	}
	p := problem.New(503, problem.Internal, "Play Developer API unavailable")
	p.Retryable, p.RetryAfter = true, 30
	return p
}

// applySubscription upserts purchase + grant from a fresh subscriptionsv2 response. acct is the verifying caller
// (nil for RTDN, where the owner is found by token or by obfuscatedExternalAccountId).
func (s *Service) applySubscription(ctx context.Context, acct *Account, token string, sub *SubscriptionV2) error {
	if len(sub.LineItems) == 0 {
		return problem.New(402, problem.PurchaseInvalid, "no line items")
	}
	li := sub.LineItems[0]
	basePlan := ""
	if li.OfferDetails != nil {
		basePlan = li.OfferDetails.BasePlanID
	}
	var productID, kind string
	err := s.Pool.QueryRow(ctx, `SELECT id, kind FROM product WHERE play_product_id = $1 AND play_base_plan_id = $2`, li.ProductID, basePlan).
		Scan(&productID, &kind)
	if db.IsNoRows(err) {
		return problem.New(402, problem.PurchaseInvalid, fmt.Sprintf("unknown product %s/%s", li.ProductID, basePlan))
	}
	if err != nil {
		return err
	}
	state, ok := stateMap[sub.SubscriptionState]
	if !ok {
		state = "pending"
	}
	expiry, _ := time.Parse(time.RFC3339, li.ExpiryTime)
	start, err := time.Parse(time.RFC3339, sub.StartTime)
	if err != nil {
		start = s.now()
	}
	autoRenew := li.AutoRenewingPlan != nil && li.AutoRenewingPlan.AutoRenewEnabled
	obf := ""
	if sub.ExternalAccountIdentifiers != nil {
		obf = sub.ExternalAccountIdentifiers.ObfuscatedExternalAccountID
	}
	hash := seal.Hash(token)
	var userID string
	err = db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		var purchaseID string
		var curUser *string
		err := tx.QueryRow(ctx, `SELECT id, user_id FROM purchase WHERE token_hash = $1 FOR UPDATE`, hash).Scan(&purchaseID, &curUser)
		if err != nil && !db.IsNoRows(err) {
			return err
		}
		// Owner resolution (check 4 + 5).
		owner, err := s.resolveOwner(ctx, tx, acct, curUser, obf)
		if err != nil {
			return err
		}
		userID = owner
		if userID == "" {
			slog.WarnContext(ctx, "play purchase without a known owner; stored unlinked", "state", state)
		}
		raw, _ := json.Marshal(sub)
		var uid any
		if userID != "" {
			uid = userID
		}
		if purchaseID == "" {
			purchaseID = ids.New("pur")
			_, err = tx.Exec(ctx, `INSERT INTO purchase (id, user_id, product_id, token_hash, token_enc, order_id, state, expiry_at,
				auto_renewing, last_verified_at, raw, base_plan_id, test_purchase, track)
				VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,now(),$10,$11,$12,$13)`, purchaseID, uid, productID, hash, s.Seal.Seal([]byte(token)),
				sub.LatestOrderID, state, nullTime(expiry), autoRenew, raw, basePlan, sub.TestPurchase != nil, nil)
		} else {
			_, err = tx.Exec(ctx, `UPDATE purchase SET user_id = COALESCE($2, user_id), state=$3, expiry_at=$4, auto_renewing=$5,
				order_id=$6, last_verified_at=now(), raw=$7, updated_at=now() WHERE id=$1`,
				purchaseID, uid, state, nullTime(expiry), autoRenew, sub.LatestOrderID, raw)
		}
		if err != nil {
			return err
		}
		if sub.LinkedPurchaseToken != "" { // upgrade / resubscribe: supersede the old purchase's grant
			if _, err := tx.Exec(ctx, `UPDATE entitlement_grant SET ends_at = LEAST(ends_at, $2) WHERE purchase_id =
				(SELECT id FROM purchase WHERE token_hash = $1) AND revoked_at IS NULL`, seal.Hash(sub.LinkedPurchaseToken), start); err != nil {
				return err
			}
		}
		if userID == "" {
			return nil
		}
		src := map[string]string{"prepaid_season": "play_season", "auto_renew_year": "play_year", "auto_renew_month": "play_month"}[kind]
		grants := state == "active" || state == "in_grace" || state == "canceled"
		if grants && !expiry.IsZero() {
			_, err = tx.Exec(ctx, `INSERT INTO entitlement_grant (user_id, source, starts_at, ends_at, purchase_id)
				VALUES ($1,$2::ent_source_t,$3,$4,$5)
				ON CONFLICT (purchase_id) WHERE purchase_id IS NOT NULL DO UPDATE SET user_id = EXCLUDED.user_id, ends_at = EXCLUDED.ends_at, revoked_at = NULL`,
				userID, src, start, expiry, purchaseID)
		} else if state == "expired" || state == "on_hold" || state == "paused" {
			_, err = tx.Exec(ctx, `UPDATE entitlement_grant SET ends_at = LEAST(ends_at, now()) WHERE purchase_id = $1`, purchaseID)
		}
		if err != nil {
			return err
		}
		if _, err := s.Recompute(ctx, tx, userID); err != nil {
			return err
		}
		return nil
	})
	if err != nil {
		return err
	}
	if state == "pending" && acct != nil {
		return problem.New(402, problem.PurchasePending, "")
	}
	if acct != nil && state != "active" && state != "in_grace" && state != "canceled" {
		return problem.New(402, problem.PurchaseInvalid, "subscription state "+state)
	}
	// Acknowledge after granting (the client never acknowledges; Play refunds after 3 days otherwise).
	if sub.AcknowledgementState == "ACKNOWLEDGEMENT_STATE_PENDING" && (state == "active" || state == "in_grace") {
		if err := s.Play.Acknowledge(ctx, s.PackageName, li.ProductID, token); err != nil {
			// A daily job re-acknowledges; the grant stands.
			slog.WarnContext(ctx, "acknowledge failed; will retry", "err", err)
			_ = db.EnqueueJob(ctx, s.Pool, "play_ack", map[string]string{"token_hash": base64.StdEncoding.EncodeToString(hash)},
				"play_ack:"+base64.StdEncoding.EncodeToString(hash), s.now().Add(10*time.Minute))
		} else {
			_, _ = s.Pool.Exec(ctx, `UPDATE purchase SET acknowledged_at = now() WHERE token_hash = $1`, hash)
		}
	}
	return nil
}

// resolveOwner implements payments §2 check 4–5.
func (s *Service) resolveOwner(ctx context.Context, tx pgx.Tx, acct *Account, curUser *string, obf string) (string, error) {
	active := func(uid string) (bool, error) {
		var state, kind string
		var lastSeen *time.Time
		err := tx.QueryRow(ctx, `SELECT state::text, auth_kind::text, last_seen_at FROM app_user WHERE id = $1`, uid).Scan(&state, &kind, &lastSeen)
		if db.IsNoRows(err) {
			return false, nil
		}
		if err != nil {
			return false, err
		}
		if state != "active" {
			return false, nil
		}
		// An anonymous account inactive for > 30 days gives up its purchase (reinstall without Google link).
		if kind == "anonymous" && lastSeen != nil && s.now().Sub(*lastSeen) > 30*24*time.Hour {
			return false, nil
		}
		return true, nil
	}
	var obfOwner string
	if obf != "" {
		err := tx.QueryRow(ctx, `SELECT id FROM app_user WHERE billing_account_ref::text = $1`, obf).Scan(&obfOwner)
		if err != nil && !db.IsNoRows(err) {
			return "", err
		}
	}
	if acct == nil { // RTDN: keep the linked owner, else the obfuscated id owner
		if curUser != nil {
			return *curUser, nil
		}
		return obfOwner, nil
	}
	for _, other := range []string{deref(curUser), obfOwner} {
		if other == "" || other == acct.ID {
			continue
		}
		ok, err := active(other)
		if err != nil {
			return "", err
		}
		if ok {
			return "", problem.New(409, problem.PurchaseOwnedByOtherAccount, "")
		}
		if err := db.Audit(ctx, tx, acct.ID, "purchase_relinked", "app_user", other, nil, map[string]any{"to": acct.ID}); err != nil {
			return "", err
		}
	}
	return acct.ID, nil
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

func nullTime(t time.Time) any {
	if t.IsZero() {
		return nil
	}
	return t
}

// DeveloperNotification is the decoded RTDN message.
type DeveloperNotification struct {
	Version                  string `json:"version"`
	PackageName              string `json:"packageName"`
	EventTimeMillis          string `json:"eventTimeMillis"`
	SubscriptionNotification *struct {
		NotificationType int    `json:"notificationType"`
		PurchaseToken    string `json:"purchaseToken"`
		SubscriptionID   string `json:"subscriptionId"`
	} `json:"subscriptionNotification"`
	VoidedPurchaseNotification *struct {
		PurchaseToken string `json:"purchaseToken"`
		OrderID       string `json:"orderId"`
		RefundType    int    `json:"refundType"`
	} `json:"voidedPurchaseNotification"`
	TestNotification *struct {
		Version string `json:"version"`
	} `json:"testNotification"`
}

// PubSubPush is the Pub/Sub push envelope.
type PubSubPush struct {
	Message struct {
		Data      string `json:"data"`
		MessageID string `json:"messageId"`
	} `json:"message"`
	Subscription string `json:"subscription"`
}

// HandleRTDN processes one Pub/Sub push. Every subscription notification triggers a fresh subscriptionsv2.get;
// the notification body is never trusted alone. Returning nil acks the message.
func (s *Service) HandleRTDN(ctx context.Context, env PubSubPush) error {
	data, err := base64.StdEncoding.DecodeString(env.Message.Data)
	if err != nil {
		return problem.Validation("message.data is not base64")
	}
	var n DeveloperNotification
	if err := json.Unmarshal(data, &n); err != nil {
		return problem.Validation("message.data is not a DeveloperNotification")
	}
	if n.PackageName != "" && n.PackageName != s.PackageName {
		slog.WarnContext(ctx, "rtdn for another package ignored", "package", n.PackageName)
		return nil
	}
	switch {
	case n.TestNotification != nil:
		slog.InfoContext(ctx, "rtdn test notification", "message_id", env.Message.MessageID)
		return nil
	case n.SubscriptionNotification != nil:
		sn := n.SubscriptionNotification
		sub, err := s.Play.GetSubscription(ctx, s.PackageName, sn.PurchaseToken)
		if errors.Is(err, ErrPlayTokenInvalid) {
			slog.WarnContext(ctx, "rtdn token unknown to Play", "type", sn.NotificationType)
			return nil
		}
		if err != nil {
			return err // nack → Pub/Sub retries
		}
		err = s.applySubscription(ctx, nil, sn.PurchaseToken, sub)
		if p := problem.As(err); p != nil && p.Status < 500 {
			slog.WarnContext(ctx, "rtdn not applied", "code", p.Code, "detail", p.Detail)
			return nil
		}
		return err
	case n.VoidedPurchaseNotification != nil:
		return s.Revoke(ctx, n.VoidedPurchaseNotification.PurchaseToken, "refunded")
	}
	return nil
}

// Revoke marks a purchase refunded/revoked and revokes its grant.
func (s *Service) Revoke(ctx context.Context, token, state string) error {
	hash := seal.Hash(token)
	return db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		var pid string
		var uid *string
		err := tx.QueryRow(ctx, `UPDATE purchase SET state = $2, updated_at = now() WHERE token_hash = $1 RETURNING id, user_id`, hash, state).Scan(&pid, &uid)
		if db.IsNoRows(err) {
			return nil
		}
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE entitlement_grant SET revoked_at = now() WHERE purchase_id = $1 AND revoked_at IS NULL`, pid); err != nil {
			return err
		}
		if uid != nil {
			_, err = s.Recompute(ctx, tx, *uid)
		}
		return err
	})
}

// PurchaseRecord is the API receipt row (no tokens).
type PurchaseRecord struct {
	ID           string     `json:"id"`
	Provider     string     `json:"provider"`
	ProductID    string     `json:"product_id"`
	State        string     `json:"state"`
	OrderID      *string    `json:"order_id"`
	Amount       any        `json:"amount"`
	CreatedAt    time.Time  `json:"created_at"`
	ExpiryAt     *time.Time `json:"expiry_at"`
	AutoRenewing bool       `json:"auto_renewing"`
}

// ListPurchases returns the user's receipts.
func (s *Service) ListPurchases(ctx context.Context, userID string) ([]PurchaseRecord, error) {
	rows, err := s.Pool.Query(ctx, `SELECT id, provider, product_id, state, order_id, created_at, expiry_at, COALESCE(auto_renewing,false)
		FROM purchase WHERE user_id = $1 ORDER BY created_at DESC`, userID)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (PurchaseRecord, error) {
		var p PurchaseRecord
		err := r.Scan(&p.ID, &p.Provider, &p.ProductID, &p.State, &p.OrderID, &p.CreatedAt, &p.ExpiryAt, &p.AutoRenewing)
		return p, err
	})
}

// Product is the API Product.
type Product struct {
	ID             string `json:"id"`
	Kind           string `json:"kind"`
	PlayProductID  string `json:"play_product_id"`
	PlayBasePlanID string `json:"play_base_plan_id"`
	DurationDays   int    `json:"duration_days"`
	BillingPeriod  string `json:"billing_period"`
	Renews         bool   `json:"renews"`
	Highlight      bool   `json:"highlight"`
	Title          any    `json:"title"`
	Features       []any  `json:"features"`
}

var productText = map[string]struct {
	period string
	title  map[string]string
}{
	"prepaid_season":   {"P4M", map[string]string{"en": "Season pass (4 months)", "hi": "सीज़न पास (4 महीने)"}},
	"auto_renew_year":  {"P1Y", map[string]string{"en": "Yearly", "hi": "सालाना"}},
	"auto_renew_month": {"P1M", map[string]string{"en": "Monthly", "hi": "मासिक"}},
}

var proFeatures = []any{
	map[string]string{"en": "Alerts for every stage after you apply", "hi": "आवेदन के बाद हर चरण की सूचना"},
	map[string]string{"en": "No ads", "hi": "कोई विज्ञापन नहीं"},
	map[string]string{"en": "Family alerts", "hi": "परिवार को सूचना"},
}

// Products lists active products (prices come from Play ProductDetails on the device).
func (s *Service) Products(ctx context.Context) ([]Product, error) {
	rows, err := s.Pool.Query(ctx, `SELECT id, kind, play_product_id, play_base_plan_id, duration_days FROM product WHERE active ORDER BY duration_days`)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (Product, error) {
		var p Product
		if err := r.Scan(&p.ID, &p.Kind, &p.PlayProductID, &p.PlayBasePlanID, &p.DurationDays); err != nil {
			return p, err
		}
		t := productText[p.Kind]
		p.BillingPeriod, p.Title, p.Features = t.period, t.title, proFeatures
		p.Renews = !strings.HasPrefix(p.Kind, "prepaid")
		p.Highlight = p.Kind == "prepaid_season"
		return p, nil
	})
}
