package httpapi

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/account"
	"github.com/amanroy3901/job-vac/backend/internal/auth"
	"github.com/amanroy3901/job-vac/backend/internal/billing"
	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/family"
	"github.com/amanroy3901/job-vac/backend/internal/ids"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/amanroy3901/job-vac/backend/internal/rules"
)

// ---------------------------------------------------------------- session & account

func (s *Server) startSession(w http.ResponseWriter, r *http.Request) error {
	var req account.SessionRequest
	if err := decode(r, &req, false); err != nil {
		return err
	}
	ctx := r.Context()
	var merge *auth.Identity
	if req.MergeAnonymousIDToken != nil && *req.MergeAnonymousIDToken != "" {
		m, err := s.d.IDTokens.VerifyIDToken(ctx, *req.MergeAnonymousIDToken)
		if err != nil {
			return problem.Validation("merge_anonymous_id_token is not valid", problem.FieldError{Field: "merge_anonymous_id_token", Code: "invalid"})
		}
		if !m.IsAnonymous() {
			return problem.Validation("merge_anonymous_id_token must belong to an anonymous account",
				problem.FieldError{Field: "merge_anonymous_id_token", Code: "invalid"})
		}
		merge = m
	}
	// TODO(referral): SessionRequest.referral_code is accepted but not redeemed yet (redeemReferral is 501).
	res, err := s.d.Accounts.StartSession(ctx, identity(ctx), req, merge)
	if err != nil {
		return err
	}
	sum, err := s.d.Accounts.Summarize(ctx, res.User, identity(ctx).Email)
	if err != nil {
		return err
	}
	ent, err := s.d.Billing.Get(ctx, res.User.ID)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, map[string]any{"user": sum, "is_new_user": res.IsNew, "entitlement": ent,
		"has_backup": res.HasBackup, "backup_updated_at": res.BackupUpdated, "primary_installation_id": res.PrimaryInstall})
}

func (s *Server) getMe(w http.ResponseWriter, r *http.Request) error {
	sum, err := s.d.Accounts.Summarize(r.Context(), user(r.Context()), identity(r.Context()).Email)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, sum)
}

func (s *Server) deleteAccount(w http.ResponseWriter, r *http.Request) error {
	var body struct {
		Reason  string `json:"reason"`
		Comment string `json:"comment"`
	}
	if err := decode(r, &body, true); err != nil {
		return err
	}
	if body.Reason != "" && !slices.Contains([]string{"too_many_alerts", "wrong_matches", "got_job", "privacy", "other"}, body.Reason) {
		return problem.Validation("bad reason", problem.FieldError{Field: "reason", Code: "out_of_range"})
	}
	st, err := s.d.Accounts.RequestDeletion(r.Context(), user(r.Context()), body.Reason)
	if err != nil {
		return err
	}
	return writeJSON(w, http.StatusAccepted, st)
}

func (s *Server) cancelDeletion(w http.ResponseWriter, r *http.Request) error {
	st, err := s.d.Accounts.CancelDeletion(r.Context(), user(r.Context()))
	if err != nil {
		return err
	}
	return writeJSON(w, 200, st)
}

func (s *Server) listConsents(w http.ResponseWriter, r *http.Request) error {
	cs, err := s.d.Accounts.Consents(r.Context(), user(r.Context()).ID)
	if err != nil {
		return err
	}
	if cs == nil {
		cs = []account.Consent{}
	}
	return writeJSON(w, 200, map[string]any{"items": cs})
}

func (s *Server) recordConsent(w http.ResponseWriter, r *http.Request) error {
	var in struct {
		Purpose        string `json:"purpose"`
		Version        string `json:"version"`
		Granted        *bool  `json:"granted"`
		NoticeLanguage string `json:"notice_language"`
	}
	if err := decode(r, &in, false); err != nil {
		return err
	}
	if in.Granted == nil {
		return problem.Validation("granted is required", problem.FieldError{Field: "granted", Code: "required"})
	}
	if in.NoticeLanguage == "" {
		in.NoticeLanguage = lang(r.Context())
	}
	c, err := s.d.Accounts.RecordConsent(r.Context(), user(r.Context()), in.Purpose, in.Version, *in.Granted, in.NoticeLanguage)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, c)
}

// ---------------------------------------------------------------- devices

func (s *Server) listDevices(w http.ResponseWriter, r *http.Request) error {
	ds, err := s.d.Accounts.Devices(r.Context(), user(r.Context()).ID)
	if err != nil {
		return err
	}
	if ds == nil {
		ds = []account.Device{}
	}
	return writeJSON(w, 200, map[string]any{"items": ds})
}

func (s *Server) upsertDevice(w http.ResponseWriter, r *http.Request) error {
	var in account.DeviceInput
	if err := decode(r, &in, false); err != nil {
		return err
	}
	d, err := s.d.Accounts.UpsertDevice(r.Context(), user(r.Context()).ID, r.PathValue("installation_id"), in)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, d)
}

func (s *Server) deleteDevice(w http.ResponseWriter, r *http.Request) error {
	if err := s.d.Accounts.DeleteDevice(r.Context(), user(r.Context()).ID, r.PathValue("installation_id")); err != nil {
		return err
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

func (s *Server) makePrimary(w http.ResponseWriter, r *http.Request) error {
	d, err := s.d.Accounts.MakePrimary(r.Context(), user(r.Context()).ID, r.PathValue("installation_id"))
	if err != nil {
		return err
	}
	return writeJSON(w, 200, d)
}

// ---------------------------------------------------------------- backup

func (s *Server) getBackup(w http.ResponseWriter, r *http.Request) error {
	b, err := s.d.Accounts.GetBackup(r.Context(), user(r.Context()))
	if err != nil {
		return err
	}
	w.Header().Set("ETag", strconv.Quote(strconv.FormatInt(b.Version, 10)))
	return writeJSON(w, 200, b)
}

func (s *Server) putBackup(w http.ResponseWriter, r *http.Request) error {
	var in account.BackupInput
	if err := decode(r, &in, false); err != nil {
		return err
	}
	ifMatch := int64(-1)
	if h := strings.Trim(strings.TrimPrefix(r.Header.Get("If-Match"), "W/"), `"`); h != "" {
		v, err := strconv.ParseInt(h, 10, 64)
		if err != nil {
			return problem.New(412, problem.BackupConflict, "If-Match must be a backup version")
		}
		ifMatch = v
	}
	b, err := s.d.Accounts.PutBackup(r.Context(), user(r.Context()), in, ifMatch)
	if err != nil {
		return err
	}
	w.Header().Set("ETag", strconv.Quote(strconv.FormatInt(b.Version, 10)))
	return writeJSON(w, 200, b)
}

func (s *Server) deleteBackup(w http.ResponseWriter, r *http.Request) error {
	if err := s.d.Accounts.DeleteBackup(r.Context(), user(r.Context())); err != nil {
		return err
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

// ---------------------------------------------------------------- entitlements

func (s *Server) getEntitlement(w http.ResponseWriter, r *http.Request) error {
	e, err := s.d.Billing.Get(r.Context(), user(r.Context()).ID)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, e)
}

func (s *Server) startTrial(w http.ResponseWriter, r *http.Request) error {
	e, err := s.d.Billing.StartTrial(r.Context(), user(r.Context()).ID)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, e)
}

func (s *Server) listPurchases(w http.ResponseWriter, r *http.Request) error {
	ps, err := s.d.Billing.ListPurchases(r.Context(), user(r.Context()).ID)
	if err != nil {
		return err
	}
	if ps == nil {
		ps = []billing.PurchaseRecord{}
	}
	return writeJSON(w, 200, map[string]any{"items": ps})
}

func (s *Server) verifyPlayPurchase(w http.ResponseWriter, r *http.Request) error {
	var in billing.PlayPurchaseInput
	if err := decode(r, &in, false); err != nil {
		return err
	}
	u := user(r.Context())
	e, err := s.d.Billing.VerifyPlay(r.Context(), billing.Account{ID: u.ID, BillingAccountRef: u.BillingAccountRef}, in)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, e)
}

func (s *Server) getReferral(w http.ResponseWriter, r *http.Request) error {
	u := user(r.Context())
	var joined, rewarded int
	var redeemed *string
	err := s.d.Pool.QueryRow(r.Context(), `SELECT
		(SELECT count(*) FROM referral_redemption WHERE referrer_user_id = $1),
		(SELECT count(*) FROM referral_redemption WHERE referrer_user_id = $1 AND rewarded_at IS NOT NULL),
		(SELECT a.referral_code FROM referral_redemption rr JOIN app_user a ON a.id = rr.referrer_user_id WHERE rr.referee_user_id = $1)`, u.ID).
		Scan(&joined, &rewarded, &redeemed)
	if err != nil {
		return err
	}
	link := s.d.Cfg.PublicBaseURL + "/r/" + u.ReferralCode
	return writeJSON(w, 200, map[string]any{"code": u.ReferralCode, "share_url": link,
		"share_text": map[string]string{
			"en": "Get alerts only for government jobs you are eligible for. Use my code " + u.ReferralCode + ": " + link,
			"hi": "सिर्फ़ वही सरकारी नौकरियाँ जिनके आप योग्य हैं। मेरा कोड " + u.ReferralCode + ": " + link},
		"friends_joined": joined, "pro_days_earned": rewarded * 30, "redeemed_code": redeemed})
}

// ---------------------------------------------------------------- reports & telemetry

var reportReasons = []string{"not_eligible_age", "not_eligible_qualification", "not_eligible_domicile", "not_eligible_gender",
	"not_eligible_category", "wrong_dates", "wrong_fee", "wrong_link", "duplicate", "other"}

// ReportPauseThreshold: this many open reports on one post auto-pause new-match notifications (matching-engine §7.5).
const ReportPauseThreshold = 3

func (s *Server) reportMatch(w http.ResponseWriter, r *http.Request) error {
	var in struct {
		PostID       string         `json:"post_id"`
		RuleVersion  *int           `json:"rule_version"`
		Reason       string         `json:"reason"`
		Rule         *string        `json:"rule"`
		VerdictShown *string        `json:"verdict_shown"`
		Comment      *string        `json:"comment"`
		Attributes   map[string]any `json:"attributes"`
	}
	if err := decode(r, &in, false); err != nil {
		return err
	}
	var fe []problem.FieldError
	if in.PostID == "" {
		fe = append(fe, problem.FieldError{Field: "post_id", Code: "required"})
	}
	if in.RuleVersion == nil {
		fe = append(fe, problem.FieldError{Field: "rule_version", Code: "required"})
	}
	if !slices.Contains(reportReasons, in.Reason) {
		fe = append(fe, problem.FieldError{Field: "reason", Code: "out_of_range"})
	}
	if in.Comment != nil && len([]rune(*in.Comment)) > 500 {
		fe = append(fe, problem.FieldError{Field: "comment", Code: "too_long"})
	}
	if len(fe) > 0 {
		return problem.Validation("invalid report", fe...)
	}
	ctx := r.Context()
	var noticeID string
	if err := s.d.Pool.QueryRow(ctx, `SELECT recruitment_id FROM post WHERE id = $1`, in.PostID).Scan(&noticeID); err != nil {
		if db.IsNoRows(err) {
			return problem.Validation("unknown post", problem.FieldError{Field: "post_id", Code: "unknown_code"})
		}
		return err
	}
	var attrs any
	if in.Attributes != nil {
		attrs, _ = json.Marshal(in.Attributes)
	}
	id := ids.New("mr")
	var open int
	err := db.InTx(ctx, s.d.Pool, func(tx db.Tx) error {
		if _, err := tx.Exec(ctx, `INSERT INTO match_report (id, post_id, rule_version, reason, rule, comment, attributes, app_version)
			VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`, id, in.PostID, *in.RuleVersion, in.Reason, in.Rule, in.Comment, attrs,
			atoiOrNil(r.Header.Get("X-App-Version"))); err != nil {
			return err
		}
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM match_report WHERE post_id = $1 AND state = 'received'`, in.PostID).Scan(&open); err != nil {
			return err
		}
		if open < ReportPauseThreshold {
			return nil
		}
		tag, err := tx.Exec(ctx, `UPDATE recruitment SET new_match_paused = true WHERE id = $1 AND NOT new_match_paused`, noticeID)
		if err != nil || tag.RowsAffected() == 0 {
			return err
		}
		if err := db.Audit(ctx, tx, "system", "auto_pause_new_match", "recruitment", noticeID, nil, map[string]any{"post_id": in.PostID, "open_reports": open}); err != nil {
			return err
		}
		return db.EnqueueJob(ctx, tx, "republish_notice", map[string]string{"notice_id": noticeID, "reason": "publish"}, "", time.Time{})
	})
	if err != nil {
		return err
	}
	if open >= ReportPauseThreshold {
		slog.ErrorContext(ctx, "wrong-match reports threshold reached: new-match notifications paused", "post_id", in.PostID, "alert", true)
	}
	return writeJSON(w, http.StatusCreated, map[string]string{"id": id, "state": "received"})
}

func atoiOrNil(s string) any {
	if n, err := strconv.Atoi(s); err == nil {
		return n
	}
	return nil
}

var counterRe = regexp.MustCompile(`^[a-z0-9_]{1,64}$`)

func (s *Server) postTelemetry(w http.ResponseWriter, r *http.Request) error {
	var in struct {
		ISTDate    string         `json:"ist_date"`
		AppVersion int            `json:"app_version"`
		Locale     string         `json:"locale"`
		Counters   map[string]int `json:"counters"`
	}
	if err := decode(r, &in, false); err != nil {
		return err
	}
	d, err := time.Parse(time.DateOnly, in.ISTDate)
	today := s.d.Now().In(push.IST)
	todayDate := time.Date(today.Year(), today.Month(), today.Day(), 0, 0, 0, 0, time.UTC)
	var fe []problem.FieldError
	if err != nil || d.After(todayDate.AddDate(0, 0, 1)) || d.Before(todayDate.AddDate(0, 0, -7)) {
		fe = append(fe, problem.FieldError{Field: "ist_date", Code: "out_of_range"})
	}
	if in.AppVersion <= 0 {
		fe = append(fe, problem.FieldError{Field: "app_version", Code: "required"})
	}
	if !rules.KnownLocales[in.Locale] {
		fe = append(fe, problem.FieldError{Field: "locale", Code: "unknown_code"})
	}
	if len(in.Counters) == 0 || len(in.Counters) > 64 {
		fe = append(fe, problem.FieldError{Field: "counters", Code: "out_of_range"})
	}
	for k, v := range in.Counters {
		if !counterRe.MatchString(k) || v < 0 || v > 1000 {
			fe = append(fe, problem.FieldError{Field: "counters." + k, Code: "out_of_range"})
		}
	}
	if len(fe) > 0 {
		return problem.Validation("invalid usage counters", fe...)
	}
	ctx := r.Context()
	err = db.InTx(ctx, s.d.Pool, func(tx db.Tx) error {
		for k, v := range in.Counters {
			if _, err := tx.Exec(ctx, `INSERT INTO usage_counter (ist_date, app_version, locale, counter, value) VALUES ($1,$2,$3,$4,$5)
				ON CONFLICT (ist_date, app_version, locale, counter) DO UPDATE SET value = usage_counter.value + EXCLUDED.value`,
				d, in.AppVersion, in.Locale, k, v); err != nil {
				return err
			}
		}
		return nil
	})
	if err != nil {
		return err
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

// ---------------------------------------------------------------- self-test

// SelfTestTimeout is when a sent test turns into `timeout`.
const SelfTestTimeout = 120 * time.Second

type pushTest struct {
	TestID     string     `json:"test_id"`
	Status     string     `json:"status"`
	SentAt     time.Time  `json:"sent_at"`
	ReceivedAt *time.Time `json:"received_at"`
	LatencyMS  *int64     `json:"latency_ms"`
	FCMError   *string    `json:"fcm_error"`
}

func (s *Server) startPushTest(w http.ResponseWriter, r *http.Request) error {
	var in struct {
		FCMToken     string `json:"fcm_token"`
		Kind         string `json:"kind"`
		DelaySeconds int    `json:"delay_seconds"`
	}
	if err := decode(r, &in, false); err != nil {
		return err
	}
	if in.FCMToken == "" || len(in.FCMToken) > 4096 {
		return problem.Validation("fcm_token is required", problem.FieldError{Field: "fcm_token", Code: "required"})
	}
	if in.DelaySeconds < 0 || in.DelaySeconds > 120 {
		return problem.Validation("delay_seconds out of range", problem.FieldError{Field: "delay_seconds", Code: "out_of_range"})
	}
	ctx := r.Context()
	t := pushTest{TestID: ids.New("t"), Status: "sent", SentAt: s.d.Now()}
	if _, err := s.d.Pool.Exec(ctx, `INSERT INTO push_test (id, fcm_token_enc, status, sent_at) VALUES ($1,$2,'sent',$3)`,
		t.TestID, s.d.Seal.Seal([]byte(in.FCMToken)), t.SentAt); err != nil {
		return err
	}
	if in.DelaySeconds > 0 {
		if err := db.EnqueueJob(ctx, s.d.Pool, "push_test_send", map[string]string{"test_id": t.TestID}, "pt:"+t.TestID,
			s.d.Now().Add(time.Duration(in.DelaySeconds)*time.Second)); err != nil {
			return err
		}
	} else if err := push.SendSelfTest(ctx, s.d.Pool, s.d.Sender, in.FCMToken, t.TestID, SelfTestTimeout); err != nil {
		msg := err.Error()
		t.Status, t.FCMError = "fcm_error", &msg
	}
	return writeJSON(w, http.StatusAccepted, t)
}

func (s *Server) loadPushTest(ctx context.Context, id string) (*pushTest, error) {
	var t pushTest
	err := s.d.Pool.QueryRow(ctx, `SELECT id, status, sent_at, received_at, fcm_error FROM push_test WHERE id = $1`, id).
		Scan(&t.TestID, &t.Status, &t.SentAt, &t.ReceivedAt, &t.FCMError)
	if db.IsNoRows(err) {
		return nil, problem.New(404, problem.NotFound, "push test")
	}
	if err != nil {
		return nil, err
	}
	if t.Status == "sent" && s.d.Now().Sub(t.SentAt) > SelfTestTimeout {
		t.Status = "timeout"
		_, _ = s.d.Pool.Exec(ctx, `UPDATE push_test SET status = 'timeout', fcm_token_enc = NULL WHERE id = $1 AND status = 'sent'`, id)
	}
	if t.ReceivedAt != nil {
		ms := t.ReceivedAt.Sub(t.SentAt).Milliseconds()
		t.LatencyMS = &ms
	}
	return &t, nil
}

func (s *Server) getPushTest(w http.ResponseWriter, r *http.Request) error {
	t, err := s.loadPushTest(r.Context(), r.PathValue("test_id"))
	if err != nil {
		return err
	}
	return writeJSON(w, 200, t)
}

func (s *Server) ackPushTest(w http.ResponseWriter, r *http.Request) error {
	tag, err := s.d.Pool.Exec(r.Context(), `UPDATE push_test SET status = 'received', received_at = $2, fcm_token_enc = NULL
		WHERE id = $1 AND status IN ('sent','timeout')`, r.PathValue("test_id"), s.d.Now())
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		if _, err := s.loadPushTest(r.Context(), r.PathValue("test_id")); err != nil {
			return err
		}
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

// ---------------------------------------------------------------- family

func (s *Server) getFamilyInvite(w http.ResponseWriter, r *http.Request) error {
	v, err := s.d.Family.GetInvite(r.Context(), r.PathValue("token"))
	if err != nil {
		return err
	}
	return writeJSON(w, 200, v)
}

func (s *Server) acceptFamilyInvite(w http.ResponseWriter, r *http.Request) error {
	var in struct {
		ConsentVersion string `json:"consent_version"`
	}
	if err := decode(r, &in, false); err != nil {
		return err
	}
	f, err := s.d.Family.Accept(r.Context(), user(r.Context()).ID, r.PathValue("token"), in.ConsentVersion)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, f)
}

func (s *Server) declineFamilyInvite(w http.ResponseWriter, r *http.Request) error {
	if err := s.d.Family.Decline(r.Context(), r.PathValue("token")); err != nil {
		return err
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

func (s *Server) listFamily(w http.ResponseWriter, r *http.Request) error {
	ls, err := s.d.Family.List(r.Context(), user(r.Context()).ID)
	if err != nil {
		return err
	}
	if ls == nil {
		ls = []family.Link{}
	}
	return writeJSON(w, 200, map[string]any{"items": ls, "max": family.MaxLinks})
}

func (s *Server) inviteFamily(w http.ResponseWriter, r *http.Request) error {
	var in struct {
		Relation *string `json:"relation"`
	}
	if err := decode(r, &in, true); err != nil {
		return err
	}
	l, err := s.d.Family.Invite(r.Context(), user(r.Context()).ID, in.Relation)
	if err != nil {
		return err
	}
	return writeJSON(w, http.StatusCreated, l)
}

func (s *Server) removeFamily(w http.ResponseWriter, r *http.Request) error {
	if err := s.d.Family.Remove(r.Context(), user(r.Context()).ID, r.PathValue("link_id")); err != nil {
		return err
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

func (s *Server) putFamilyWatchlist(w http.ResponseWriter, r *http.Request) error {
	var in family.Watchlist
	if err := decode(r, &in, false); err != nil {
		return err
	}
	if err := s.d.Family.PutWatchlist(r.Context(), user(r.Context()).ID, r.PathValue("link_id"), in); err != nil {
		return err
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

func (s *Server) listFollowing(w http.ResponseWriter, r *http.Request) error {
	fs, err := s.d.Family.Following(r.Context(), user(r.Context()).ID)
	if err != nil {
		return err
	}
	if fs == nil {
		fs = []family.Following{}
	}
	return writeJSON(w, 200, map[string]any{"items": fs})
}

func (s *Server) stopFollowing(w http.ResponseWriter, r *http.Request) error {
	if err := s.d.Family.StopFollowing(r.Context(), user(r.Context()).ID, r.PathValue("link_id")); err != nil {
		return err
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

func (s *Server) getFamilyWatchlist(w http.ResponseWriter, r *http.Request) error {
	wl, err := s.d.Family.GetWatchlist(r.Context(), user(r.Context()).ID, r.PathValue("link_id"))
	if err != nil {
		return err
	}
	etag := strconv.Quote(strconv.FormatInt(wl.Version, 10))
	w.Header().Set("ETag", etag)
	if r.Header.Get("If-None-Match") == etag {
		w.WriteHeader(http.StatusNotModified)
		return nil
	}
	return writeJSON(w, 200, wl)
}

// ---------------------------------------------------------------- hooks & ticks

func (s *Server) bearer(r *http.Request) string {
	raw, _ := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
	return strings.TrimSpace(raw)
}

func (s *Server) playRTDN(w http.ResponseWriter, r *http.Request) error {
	if s.d.RTDNAuth != nil {
		if _, err := s.d.RTDNAuth.Verify(r.Context(), s.bearer(r)); err != nil {
			return problem.New(401, problem.Unauthenticated, "")
		}
	}
	var env billing.PubSubPush
	if err := decode(r, &env, false); err != nil {
		return err
	}
	if err := s.d.Billing.HandleRTDN(r.Context(), env); err != nil {
		if p := problem.As(err); p != nil && p.Status < 500 {
			slog.WarnContext(r.Context(), "rtdn rejected", "code", p.Code)
			w.WriteHeader(http.StatusNoContent) // ack: retrying a malformed message never helps
			return nil
		}
		return err // 5xx → Pub/Sub retries, dead-letter after 10 attempts
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

func (s *Server) tick(w http.ResponseWriter, r *http.Request) error {
	if s.d.TickAuth != nil {
		if _, err := s.d.TickAuth.Verify(r.Context(), s.bearer(r)); err != nil {
			return problem.New(401, problem.Unauthenticated, "")
		}
	}
	if s.d.Ticker == nil {
		return problem.New(501, problem.NotImplemented, "no ticker")
	}
	out, err := s.d.Ticker.Tick(r.Context(), r.PathValue("name"))
	if err != nil {
		return err
	}
	return writeJSON(w, 200, out)
}
