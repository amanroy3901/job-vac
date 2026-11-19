// Package billing implements Google Play Billing (Android) and App Store / StoreKit 2 (iOS) verification, RTDN and
// App Store Server Notifications V2 handling, the server-granted trial and the entitlement as the single source of
// truth across both stores (payments-and-entitlements.md). Entitlements belong to the account (Firebase UID), not to
// a device or store: a user who moves between Android and iOS keeps Pro. The app gates Pro only on
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

// PlayState is the state of the user's most recent store purchase (Play or App Store; for grace / hold handling).
type PlayState struct {
	Provider     string // play | app_store
	State        string // purchase.state
	AutoRenewing bool
	ExpiryAt     *time.Time
}

// State is the materialized user_entitlement row.
type State struct {
	Plan         string     `json:"plan"`   // free|pro
	Status       string     `json:"status"` // active|in_grace|on_hold|expired|none
	Source       *string    `json:"source"`
	Platform     *string    `json:"platform"` // android | ios: the store behind Source (nil for trial/referral/promo)
	StartsAt     *time.Time `json:"starts_at"`
	ExpiresAt    *time.Time `json:"expires_at"`
	AutoRenewing bool       `json:"auto_renewing"`
	TrialUsed    bool       `json:"trial_used"`
	// DuplicateStore: active paid grants from BOTH stores (e.g. a yearly sub on Play and another on the App Store);
	// the app suggests cancelling one. Not persisted.
	DuplicateStore bool `json:"-"`
}

// precedence: year (either store) > season (either store) > play_month > referral > promo > trial (payments §4).
// Equal precedence across stores is broken by the later ends_at.
var precedence = map[string]int{"play_year": 6, "appstore_year": 6, "play_season": 5, "appstore_season": 5,
	"play_month": 4, "referral": 3, "promo": 2, "trial": 1}

// Compute derives the entitlement from grants (pure; unit-tested).
func Compute(grants []Grant, latest *PlayState, now time.Time) State {
	st := State{Plan: "free", Status: "none"}
	var best *Grant
	var maxEnd, minStart time.Time
	everHad := false
	var play, apple bool
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
		play = play || isPlay(g.Source)
		apple = apple || isAppStore(g.Source)
		if best == nil || precedence[g.Source] > precedence[best.Source] ||
			(precedence[g.Source] == precedence[best.Source] && g.EndsAt.After(best.EndsAt)) {
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
		// A stacked top-up (App Store season bought while a season is running) starts in the future: extend the
		// expiry across grants that start before the current coverage ends, so access never has a gap.
		for extended := true; extended; {
			extended = false
			for i := range grants {
				g := &grants[i]
				if g.RevokedAt == nil && !g.StartsAt.After(maxEnd) && g.EndsAt.After(maxEnd) {
					maxEnd, extended = g.EndsAt, true
				}
			}
		}
		src := best.Source
		st.Plan, st.Status, st.Source = "pro", "active", &src
		st.StartsAt, st.ExpiresAt = &minStart, &maxEnd
		st.Platform = PlatformOf(src)
		st.DuplicateStore = play && apple
		if latest != nil && isStore(src) && PlatformOf(src) != nil && latest.platform() == *PlatformOf(src) {
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
			// Grace without a covering grant (grant ends at expiry): the store still grants access.
			src := "play_year"
			if latest.Provider == "app_store" {
				src = "appstore_year"
			}
			st.Plan, st.Status, st.Source, st.ExpiresAt, st.AutoRenewing = "pro", "in_grace", &src, latest.ExpiresAtOrNil(), latest.AutoRenewing
			st.Platform = PlatformOf(src)
			return st
		case "on_hold", "paused":
			st.Status = "on_hold"
			p := latest.platform()
			st.Platform = &p
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

func (p *PlayState) platform() string {
	if p.Provider == "app_store" {
		return "ios"
	}
	return "android"
}

func isPlay(src string) bool {
	return src == "play_year" || src == "play_season" || src == "play_month"
}

func isAppStore(src string) bool { return src == "appstore_year" || src == "appstore_season" }

func isStore(src string) bool { return isPlay(src) || isAppStore(src) }

// PlatformOf maps a grant source to the store platform (nil for trial/referral/promo).
func PlatformOf(src string) *string {
	var p string
	switch {
	case isPlay(src):
		p = "android"
	case isAppStore(src):
		p = "ios"
	default:
		return nil
	}
	return &p
}

// Features returns Entitlement.features for a plan.
func Features(pro bool) map[string]bool {
	return map[string]bool{"ad_free": pro, "lifecycle_alerts": pro, "family_alerts": pro, "clash_alerts": pro,
		"certificate_alerts": pro}
}
