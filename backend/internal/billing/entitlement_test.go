package billing

import (
	"testing"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/appstore"
	"github.com/golang-jwt/jwt/v5"
)

func TestCompute(t *testing.T) {
	now := time.Date(2026, 11, 19, 12, 0, 0, 0, time.UTC)
	d := func(days int) time.Time { return now.AddDate(0, 0, days) }
	revoked := d(-1)
	cases := []struct {
		name              string
		grants            []Grant
		latest            *PlayState
		plan, status, src string
		trialUsed         bool
		expires           time.Time
	}{
		{name: "none", plan: "free", status: "none"},
		{name: "trial active", grants: []Grant{{Source: "trial", StartsAt: d(-1), EndsAt: d(13)}},
			plan: "pro", status: "active", src: "trial", trialUsed: true, expires: d(13)},
		{name: "trial expired", grants: []Grant{{Source: "trial", StartsAt: d(-20), EndsAt: d(-6)}},
			plan: "free", status: "expired", trialUsed: true},
		{name: "season beats trial, max expiry wins", grants: []Grant{
			{Source: "trial", StartsAt: d(-1), EndsAt: d(30)}, {Source: "play_season", StartsAt: d(-1), EndsAt: d(20)}},
			plan: "pro", status: "active", src: "play_season", trialUsed: true, expires: d(30)},
		{name: "revoked ignored", grants: []Grant{{Source: "play_year", StartsAt: d(-1), EndsAt: d(300), RevokedAt: &revoked}},
			plan: "free", status: "none"},
		{name: "grace", grants: []Grant{{Source: "play_year", StartsAt: d(-365), EndsAt: d(2)}},
			latest: &PlayState{State: "in_grace", AutoRenewing: true}, plan: "pro", status: "in_grace", src: "play_year", expires: d(2)},
		{name: "on hold", grants: []Grant{{Source: "play_year", StartsAt: d(-400), EndsAt: d(-5)}},
			latest: &PlayState{State: "on_hold"}, plan: "free", status: "on_hold"},
		// Cross-store (decision 9): one account, grants from both stores; year beats season whatever the store.
		{name: "app store season + play year → play year", grants: []Grant{
			{Source: "appstore_season", StartsAt: d(-1), EndsAt: d(119)}, {Source: "play_year", StartsAt: d(-10), EndsAt: d(355)}},
			plan: "pro", status: "active", src: "play_year", expires: d(355)},
		{name: "equal precedence → later end wins", grants: []Grant{
			{Source: "play_year", StartsAt: d(-300), EndsAt: d(65)}, {Source: "appstore_year", StartsAt: d(-1), EndsAt: d(364)}},
			plan: "pro", status: "active", src: "appstore_year", expires: d(364)},
		{name: "stacked season top-up extends expiry", grants: []Grant{
			{Source: "appstore_season", StartsAt: d(-10), EndsAt: d(110)}, {Source: "appstore_season", StartsAt: d(110), EndsAt: d(233)},
			{Source: "promo", StartsAt: d(300), EndsAt: d(330)}},
			plan: "pro", status: "active", src: "appstore_season", expires: d(233)},
		{name: "app store grace", grants: []Grant{{Source: "appstore_year", StartsAt: d(-365), EndsAt: d(10)}},
			latest: &PlayState{Provider: "app_store", State: "in_grace", AutoRenewing: true}, plan: "pro", status: "in_grace", src: "appstore_year"},
		{name: "app store billing retry → on hold", grants: []Grant{{Source: "appstore_year", StartsAt: d(-400), EndsAt: d(-5)}},
			latest: &PlayState{Provider: "app_store", State: "on_hold"}, plan: "free", status: "on_hold"},
	}
	for _, c := range cases {
		st := Compute(c.grants, c.latest, now)
		if st.Plan != c.plan || st.Status != c.status || st.TrialUsed != c.trialUsed {
			t.Errorf("%s: got %+v", c.name, st)
			continue
		}
		if c.src != "" && (st.Source == nil || *st.Source != c.src) {
			t.Errorf("%s: source %v", c.name, st.Source)
		}
		if !c.expires.IsZero() && (st.ExpiresAt == nil || !st.ExpiresAt.Equal(c.expires)) {
			t.Errorf("%s: expires %v", c.name, st.ExpiresAt)
		}
	}
}

func TestComputePlatformAndDuplicate(t *testing.T) {
	now := time.Date(2026, 11, 19, 12, 0, 0, 0, time.UTC)
	st := Compute([]Grant{{Source: "appstore_year", StartsAt: now.Add(-time.Hour), EndsAt: now.AddDate(1, 0, 0)},
		{Source: "play_season", StartsAt: now.Add(-time.Hour), EndsAt: now.AddDate(0, 4, 0)}}, nil, now)
	if st.Platform == nil || *st.Platform != "ios" || !st.DuplicateStore {
		t.Fatalf("%+v", st)
	}
	st = Compute([]Grant{{Source: "trial", StartsAt: now.Add(-time.Hour), EndsAt: now.AddDate(0, 0, 13)}}, nil, now)
	if st.Platform != nil || st.DuplicateStore {
		t.Fatalf("trial has no store: %+v", st)
	}
	st = Compute(nil, &PlayState{Provider: "app_store", State: "on_hold"}, now)
	if st.Platform == nil || *st.Platform != "ios" || st.Status != "on_hold" {
		t.Fatalf("on hold platform: %+v", st)
	}
}

func TestAppStoreState(t *testing.T) {
	now := time.Date(2026, 11, 19, 12, 0, 0, 0, time.UTC)
	ms := func(d time.Duration) int64 { return now.Add(d).UnixMilli() }
	yes, no := true, false
	day := 24 * time.Hour
	cases := []struct {
		name      string
		ev        AppStoreEvent
		state     string
		autoRenew bool
		expiry    int64
	}{
		{"active", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(300 * day)},
			Renewal: &appstore.RenewalInfo{AutoRenewStatus: 1}}, "active", true, ms(300 * day)},
		{"intro free trial is active", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(14 * day), OfferType: 1, OfferDiscountType: "FREE_TRIAL"},
			Status: appstore.StatusActive}, "active", true, ms(14 * day)},
		{"auto-renew off (DID_CHANGE_RENEWAL_STATUS) → canceled", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(30 * day)},
			Renewal: &appstore.RenewalInfo{AutoRenewStatus: 0}, Type: "DID_CHANGE_RENEWAL_STATUS"}, "canceled", false, ms(30 * day)},
		{"DID_FAIL_TO_RENEW/GRACE_PERIOD", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(-day)},
			Renewal: &appstore.RenewalInfo{AutoRenewStatus: 1, IsInBillingRetryPeriod: &yes, GracePeriodExpiresDate: ms(5 * day)},
			Status:  appstore.StatusGracePeriod, Type: "DID_FAIL_TO_RENEW", Subtype: "GRACE_PERIOD"}, "in_grace", true, ms(5 * day)},
		{"GRACE_PERIOD_EXPIRED → billing retry → on_hold", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(-7 * day)},
			Renewal: &appstore.RenewalInfo{AutoRenewStatus: 1, IsInBillingRetryPeriod: &yes, GracePeriodExpiresDate: ms(-day)},
			Status:  appstore.StatusBillingRetry, Type: "GRACE_PERIOD_EXPIRED"}, "on_hold", true, ms(-7 * day)},
		{"retry without status, no grace → on_hold", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(-day)},
			Renewal: &appstore.RenewalInfo{AutoRenewStatus: 1, IsInBillingRetryPeriod: &yes}}, "on_hold", true, ms(-day)},
		{"EXPIRED", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(-day)},
			Renewal: &appstore.RenewalInfo{AutoRenewStatus: 0, IsInBillingRetryPeriod: &no}, Status: appstore.StatusExpired, Type: "EXPIRED"},
			"expired", false, ms(-day)},
		{"DID_RENEW", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(365 * day), TransactionReason: "RENEWAL"},
			Status: appstore.StatusActive, Type: "DID_RENEW"}, "active", true, ms(365 * day)},
		{"REFUND", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(200 * day), RevocationDate: ms(-time.Hour), RevocationType: "REFUND_FULL"},
			Status: appstore.StatusRevoked, Type: "REFUND"}, "refunded", false, ms(200 * day)},
		{"REVOKE (family sharing)", AppStoreEvent{Tx: &appstore.Transaction{ExpiresDate: ms(200 * day), RevocationDate: ms(-time.Hour), RevocationType: "FAMILY_REVOKE"},
			Type: "REVOKE"}, "revoked", false, ms(200 * day)},
	}
	for _, c := range cases {
		st, exp, ar := AppStoreState(c.ev, now)
		if st != c.state || ar != c.autoRenew || exp.UnixMilli() != c.expiry {
			t.Errorf("%s: got %s %v %v", c.name, st, ar, exp)
		}
	}
}

func TestSignerToken(t *testing.T) {
	s, err := NewSigner("", "k1")
	if err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	exp := now.Add(48 * time.Hour)
	src := "trial"
	tok, err := s.Sign("u_1", State{Plan: "pro", Status: "active", Source: &src, ExpiresAt: &exp}, false, now)
	if err != nil {
		t.Fatal(err)
	}
	claims := jwt.MapClaims{}
	if _, err := jwt.ParseWithClaims(tok, claims, func(*jwt.Token) (any, error) { return s.PublicKey(), nil },
		jwt.WithValidMethods([]string{"ES256"})); err != nil {
		t.Fatal(err)
	}
	if claims["plan"] != "pro" || claims["ads_enabled"] != false || int64(claims["exp"].(float64)) != exp.Unix() {
		t.Fatalf("claims %v", claims)
	}
	if k := s.JWKS()["keys"].([]map[string]any)[0]; k["kid"] != "k1" || k["crv"] != "P-256" {
		t.Fatalf("jwks %v", k)
	}
}
