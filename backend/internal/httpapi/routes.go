package httpapi

import (
	"net/http"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/problem"
)

// routesV1 registers every path of docs/api/openapi.yaml (a contract test asserts the set matches).
func (s *Server) routesV1(mux *http.ServeMux) {
	pub := opts{public: true}
	acct := opts{auth: authAccount, appCheck: true}
	acctIdem := opts{auth: authAccount, appCheck: true, idem: true}
	ac := opts{appCheck: true}

	// config / catalog / reference: public CDN files
	mux.Handle("GET /v1/config", s.wrap(pub, s.getConfig))
	mux.Handle("GET /v1/health", s.wrap(pub, s.getHealth))
	mux.Handle("GET /v1/catalog/head", s.wrap(pub, s.catalogHead))
	mux.Handle("GET /v1/catalog/snapshots/{seq}", s.wrap(pub, s.catalogFile("snapshots")))
	mux.Handle("GET /v1/catalog/batches/{seq}", s.wrap(pub, s.catalogFile("batches")))
	mux.Handle("GET /v1/notices/{notice_id}", s.wrap(pub, s.getNotice))
	mux.Handle("GET /v1/reference", s.wrap(pub, s.getReference))
	mux.Handle("GET /v1/products", s.wrap(pub, s.listProducts))

	// session & account
	mux.Handle("POST /v1/session", s.wrap(opts{auth: authIdentity, appCheck: true, idem: true}, s.startSession))
	mux.Handle("GET /v1/me", s.wrap(opts{auth: authAccount, appCheck: true, allowPending: true}, s.getMe))
	mux.Handle("DELETE /v1/me", s.wrap(opts{auth: authAccount, appCheck: true, idem: true}, s.deleteAccount))
	mux.Handle("POST /v1/me/deletion:cancel", s.wrap(opts{auth: authAccount, appCheck: true, idem: true, allowPending: true}, s.cancelDeletion))
	mux.Handle("GET /v1/me/consents", s.wrap(acct, s.listConsents))
	mux.Handle("POST /v1/me/consents", s.wrap(acctIdem, s.recordConsent))

	// quality (no account, no installation id)
	mux.Handle("POST /v1/reports", s.wrap(opts{appCheck: true, idem: true, limit: &rate{per: 24 * time.Hour, n: 50, burst: 20}}, s.reportMatch))
	mux.Handle("POST /v1/telemetry", s.wrap(opts{appCheck: true, idem: true, limit: &rate{per: 24 * time.Hour, n: 50, burst: 10}}, s.postTelemetry))

	// self-test
	mux.Handle("POST /v1/push-tests", s.wrap(opts{appCheck: true, idem: true, limit: &rate{per: time.Hour, n: 20, burst: 5}}, s.startPushTest))
	mux.Handle("GET /v1/push-tests/{test_id}", s.wrap(ac, s.getPushTest))
	mux.Handle("POST /v1/push-tests/{test_id}", s.wrap(ac, s.verbDispatch(map[string]handler{"received": s.ackPushTest})))

	// devices
	mux.Handle("GET /v1/me/devices", s.wrap(acct, s.listDevices))
	mux.Handle("PUT /v1/me/devices/{installation_id}", s.wrap(acctIdem, s.upsertDevice))
	mux.Handle("DELETE /v1/me/devices/{installation_id}", s.wrap(acct, s.deleteDevice))
	mux.Handle("POST /v1/me/devices/{installation_id}", s.wrap(acctIdem, s.verbDispatch(map[string]handler{"make-primary": s.makePrimary})))

	// backup
	mux.Handle("GET /v1/me/backup", s.wrap(acct, s.getBackup))
	mux.Handle("PUT /v1/me/backup", s.wrap(opts{auth: authAccount, appCheck: true, idem: true, maxBody: 128 << 10,
		limit: &rate{per: 24 * time.Hour, n: 30, burst: 30}}, s.putBackup))
	mux.Handle("DELETE /v1/me/backup", s.wrap(acct, s.deleteBackup))

	// entitlements & payments
	mux.Handle("GET /v1/me/entitlement", s.wrap(acct, s.getEntitlement))
	mux.Handle("POST /v1/me/trial", s.wrap(acctIdem, s.startTrial))
	mux.Handle("GET /v1/me/purchases", s.wrap(acct, s.listPurchases))
	mux.Handle("POST /v1/me/purchases/google-play", s.wrap(acctIdem, s.verifyPlayPurchase))
	mux.Handle("POST /v1/me/purchases/app-store", s.wrap(acctIdem, s.verifyAppStorePurchase))

	// referral
	mux.Handle("GET /v1/me/referral", s.wrap(acct, s.getReferral))
	mux.Handle("POST /v1/me/referral:redeem", s.wrap(acctIdem, notImplemented("redeemReferral")))

	// family
	mux.Handle("GET /v1/family-invites/{token}", s.wrap(ac, s.getFamilyInvite))
	mux.Handle("POST /v1/family-invites/{token}", s.wrap(opts{appCheck: true}, s.verbDispatch(map[string]handler{
		"accept": s.authed(opts{auth: authAccount, idem: true}, s.acceptFamilyInvite), "decline": s.declineFamilyInvite})))
	mux.Handle("GET /v1/me/family", s.wrap(acct, s.listFamily))
	mux.Handle("POST /v1/me/family", s.wrap(acctIdem, s.inviteFamily))
	mux.Handle("DELETE /v1/me/family/{link_id}", s.wrap(acct, s.removeFamily))
	mux.Handle("PUT /v1/me/family/{link_id}/watchlist", s.wrap(opts{auth: authAccount, appCheck: true, idem: true,
		limit: &rate{per: 24 * time.Hour, n: 60, burst: 30}}, s.putFamilyWatchlist))
	mux.Handle("GET /v1/family/following", s.wrap(acct, s.listFollowing))
	mux.Handle("DELETE /v1/family/following/{link_id}", s.wrap(acct, s.stopFollowing))
	mux.Handle("GET /v1/family/following/{link_id}/watchlist", s.wrap(acct, s.getFamilyWatchlist))
}

// verbDispatch routes "{id}:verb" segments (the last path value) to per-verb handlers.
func (s *Server) verbDispatch(verbs map[string]handler) handler {
	return func(w http.ResponseWriter, r *http.Request) error {
		name := lastWildcard(r)
		id, verb := splitVerb(r.PathValue(name))
		h, ok := verbs[verb]
		if !ok || id == "" {
			return problem.New(404, problem.NotFound, "no route "+r.Method+" "+r.URL.Path)
		}
		r.SetPathValue(name, id)
		return h(w, r)
	}
}

func lastWildcard(r *http.Request) string {
	for _, n := range []string{"installation_id", "test_id", "token"} {
		if r.PathValue(n) != "" {
			return n
		}
	}
	return ""
}

// authed applies an extra auth/idempotency layer inside a verb dispatcher (accept needs an account; decline not).
func (s *Server) authed(o opts, h handler) handler {
	return func(w http.ResponseWriter, r *http.Request) error {
		return s.pre(w, r, o, h)
	}
}
