package billing

import (
	"testing"
	"time"

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
