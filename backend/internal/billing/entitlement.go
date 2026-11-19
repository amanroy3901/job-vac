// Package billing implements Google Play Billing verification, RTDN handling, the server-granted trial and the
// entitlement as the single source of truth (payments-and-entitlements.md). The app gates Pro only on
// GET /v1/me/entitlement and its signed token; ads_enabled is derived (plan == free).
package billing

import (
	"time"
)

// Grant is an entitlement_grant row.
type Grant struct {
	Source    string
	StartsAt  time.Time
	EndsAt    time.Time
	RevokedAt *time.Time
}

// PlayState is the state of the user's most recent Play purchase (for grace / hold handling).
type PlayState struct {
	State        string // purchase.state
	AutoRenewing bool
	ExpiryAt     *time.Time
}

// State is the materialized user_entitlement row.
type State struct {
	Plan         string     `json:"plan"`   // free|pro
	Status       string     `json:"status"` // active|in_grace|on_hold|expired|none
	Source       *string    `json:"source"`
	StartsAt     *time.Time `json:"starts_at"`
	ExpiresAt    *time.Time `json:"expires_at"`
	AutoRenewing bool       `json:"auto_renewing"`
	TrialUsed    bool       `json:"trial_used"`
}

// precedence: play_year > play_season > play_month > referral > promo > trial (payments §4).
var precedence = map[string]int{"play_year": 6, "play_season": 5, "play_month": 4, "referral": 3, "promo": 2, "trial": 1}

// Compute derives the entitlement from grants (pure; unit-tested).
func Compute(grants []Grant, latest *PlayState, now time.Time) State {
	st := State{Plan: "free", Status: "none"}
	var best *Grant
	var maxEnd, minStart time.Time
	everHad := false
	for i := range grants {
		g := &grants[i]
		if g.Source == "trial" {
			st.TrialUsed = true
		}
		if g.RevokedAt != nil {
			continue
		}
		everHad = true
		if g.StartsAt.After(now) || !now.Before(g.EndsAt) {
			continue
		}
		if best == nil || precedence[g.Source] > precedence[best.Source] {
			best = g
		}
		if g.EndsAt.After(maxEnd) {
			maxEnd = g.EndsAt
		}
		if minStart.IsZero() || g.StartsAt.Before(minStart) {
			minStart = g.StartsAt
		}
	}
	if best != nil {
		src := best.Source
		st.Plan, st.Status, st.Source = "pro", "active", &src
		st.StartsAt, st.ExpiresAt = &minStart, &maxEnd
		if latest != nil && isPlay(src) {
			st.AutoRenewing = latest.AutoRenewing
			if latest.State == "in_grace" {
				st.Status = "in_grace"
			}
		}
		return st
	}
	if latest != nil {
		switch latest.State {
		case "in_grace":
			// Grace without a covering grant (grant ends at expiry): Play still grants access.
			src := "play_year"
			st.Plan, st.Status, st.Source, st.ExpiresAt, st.AutoRenewing = "pro", "in_grace", &src, latest.ExpiresAtOrNil(), latest.AutoRenewing
			return st
		case "on_hold", "paused":
			st.Status = "on_hold"
			return st
		}
	}
	if everHad {
		st.Status = "expired"
	}
	return st
}

// ExpiresAtOrNil returns the expiry pointer.
func (p *PlayState) ExpiresAtOrNil() *time.Time { return p.ExpiryAt }

func isPlay(src string) bool {
	return src == "play_year" || src == "play_season" || src == "play_month"
}

// Features returns Entitlement.features for a plan.
func Features(pro bool) map[string]bool {
	return map[string]bool{"ad_free": pro, "lifecycle_alerts": pro, "family_alerts": pro, "clash_alerts": pro,
		"certificate_alerts": pro}
}
