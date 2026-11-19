package httpapi_test

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"regexp"
	"strings"
	"testing"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/admin"
	"github.com/amanroy3901/job-vac/backend/internal/app"
	"github.com/amanroy3901/job-vac/backend/internal/appstore"
	"github.com/amanroy3901/job-vac/backend/internal/auth"
	"github.com/amanroy3901/job-vac/backend/internal/billing"
	"github.com/amanroy3901/job-vac/backend/internal/blob"
	"github.com/amanroy3901/job-vac/backend/internal/config"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/amanroy3901/job-vac/backend/internal/testutil"
	"github.com/jackc/pgx/v5/pgxpool"
)

type env struct {
	t     *testing.T
	srv   *httptest.Server
	app   *app.App
	pool  *pgxpool.Pool
	play  *billing.FakePlay
	sent  *push.Recorder
	apple *auth.FakeAppleTokens
	asign *appstore.TestSigner
	asapi *fakeServerAPI
}

func newEnv(t *testing.T) *env {
	pool := testutil.DB(t)
	cfg := &config.Config{Env: "test", Role: "all", AuthMode: "dev", AppCheckMode: "enforce", AdminAuthMode: "dev",
		PushAuthMode: "off", PlayPackageName: "in.sarkarimatch.app", PlayMode: "fake", FCMMode: "log", BlobMode: "local",
		PublicBaseURL: "https://sarkarimatch.in", APIBaseURL: "https://api.sarkarimatch.in", EntitlementKeyID: "k1"}
	signer, err := appstore.NewTestSigner()
	if err != nil {
		t.Fatal(err)
	}
	e := &env{t: t, pool: pool, play: billing.NewFakePlay(), sent: &push.Recorder{}, apple: &auth.FakeAppleTokens{}, asign: signer,
		asapi: &fakeServerAPI{statuses: map[string][]appstore.LastTransaction{}}}
	a, err := app.New(context.Background(), cfg, pool, app.Overrides{IDTokens: auth.DevVerifier{}, AppCheck: auth.AllowAppCheck{},
		Play: e.play, Sender: e.sent, Store: blob.Local{Dir: t.TempDir()}, Admin: &admin.DevAuth{Pool: pool}, Apple: e.apple,
		AppStoreAPI: e.asapi, AppStore: &appstore.Verifier{Roots: signer.Roots(), BundleID: testBundle, AppAppleID: testAppAppleID,
			Environments: []string{appstore.EnvProduction, appstore.EnvSandbox}}})
	if err != nil {
		t.Fatal(err)
	}
	e.app = a
	e.srv = httptest.NewServer(a.Handler)
	t.Cleanup(e.srv.Close)
	for email, role := range map[string]string{"owner@example.com": "owner", "rev@example.com": "reviewer"} {
		if _, err := pool.Exec(context.Background(), `INSERT INTO admin_user VALUES ($1,$2)`, email, role); err != nil {
			t.Fatal(err)
		}
	}
	return e
}

type resp struct {
	Status int
	Header http.Header
	Body   []byte
}

func (r resp) json() map[string]any {
	var m map[string]any
	_ = json.Unmarshal(r.Body, &m)
	return m
}

func uuid() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

// call issues a request. token "" = no Authorization; mutating calls get a fresh Idempotency-Key unless given.
func (e *env) call(method, path, token string, body any, hdr ...string) resp {
	e.t.Helper()
	var rd io.Reader
	switch b := body.(type) {
	case nil:
	case []byte:
		rd = strings.NewReader(string(b))
	case string:
		rd = strings.NewReader(b)
	default:
		j, _ := json.Marshal(b)
		rd = strings.NewReader(string(j))
	}
	req, _ := http.NewRequest(method, e.srv.URL+path, rd)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-App-Version", "10")
	req.Header.Set("X-Firebase-AppCheck", "test")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	if method != http.MethodGet {
		req.Header.Set("Idempotency-Key", uuid())
	}
	for i := 0; i+1 < len(hdr); i += 2 {
		req.Header.Set(hdr[i], hdr[i+1])
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		e.t.Fatal(err)
	}
	defer res.Body.Close()
	b, _ := io.ReadAll(res.Body)
	return resp{Status: res.StatusCode, Header: res.Header, Body: b}
}

func (e *env) must(r resp, status int) resp {
	e.t.Helper()
	if r.Status != status {
		e.t.Fatalf("status %d, want %d: %s", r.Status, status, r.Body)
	}
	return r
}

func code(r resp) string { c, _ := r.json()["code"].(string); return c }

func (e *env) runJobs() {
	e.t.Helper()
	if _, err := e.app.Worker.Tick(context.Background(), "all"); err != nil {
		e.t.Fatal(err)
	}
}

func session(uid, provider, inst string) (string, map[string]any) {
	tok := "dev:" + uid
	if provider == "google" {
		tok += ":google:" + uid + "@example.com"
	}
	if provider == "apple" {
		tok += ":apple"
	}
	return tok, map[string]any{"installation_id": inst, "locale": "hi", "privacy_notice_version": "2026-11-19",
		"device": map[string]any{"fcm_token": "fcm-" + inst, "app_version": 10, "locale": "hi", "device_label": "Phone " + inst}}
}

func TestAPI(t *testing.T) {
	e := newEnv(t)

	t.Run("public config, reference, health, products", func(t *testing.T) {
		r := e.must(e.call("GET", "/v1/config", "", nil), 200)
		validateSchema(t, "AppConfig", r.Body)
		if r.Header.Get("Cache-Control") != "public, max-age=300" || r.Header.Get("ETag") == "" {
			t.Errorf("headers %v", r.Header)
		}
		e.must(e.call("GET", "/v1/config", "", nil, "If-None-Match", r.Header.Get("ETag")), 304)
		validateSchema(t, "ReferenceBundle", e.must(e.call("GET", "/v1/reference", "", nil), 200).Body)
		h := e.must(e.call("GET", "/v1/health", "", nil), 200).json()
		if h["status"] != "up" {
			t.Errorf("health %v", h)
		}
		ps := e.must(e.call("GET", "/v1/products", "", nil), 200).json()["items"].([]any)
		if len(ps) != 2 {
			t.Fatalf("products %v", ps)
		}
		for _, p := range ps {
			b, _ := json.Marshal(p)
			validateSchema(t, "Product", b)
		}
		r = e.call("GET", "/v1/config", "", nil, "X-App-Version", "0")
		if r.json()["update"] != "force" {
			t.Errorf("old app must get update=force: %s", r.Body)
		}
	})

	t.Run("auth errors", func(t *testing.T) {
		r := e.must(e.call("GET", "/v1/me", "", nil), 401)
		validateSchema(t, "Problem", r.Body)
		if r.Header.Get("Content-Type") != "application/problem+json" || code(r) != "unauthenticated" {
			t.Errorf("%v %s", r.Header, r.Body)
		}
		if r := e.call("GET", "/v1/me", "dev:nobody", nil); r.Status != 403 {
			t.Errorf("no account: %d", r.Status)
		}
	})

	gTok, gReq := session("g1", "google", "i1")
	var userID, billingRef string
	t.Run("session bootstrap + idempotency", func(t *testing.T) {
		key := uuid()
		r := e.must(e.call("POST", "/v1/session", gTok, gReq, "Idempotency-Key", key), 200)
		validateSchema(t, "SessionResponse", r.Body)
		m := r.json()
		if m["is_new_user"] != true || m["primary_installation_id"] != "i1" || m["has_backup"] != false {
			t.Fatalf("%s", r.Body)
		}
		u := m["user"].(map[string]any)
		userID, billingRef = u["id"].(string), u["billing_account_ref"].(string)
		if u["auth_provider"] != "google" || u["google_email"] != "g1@example.com" {
			t.Errorf("user %v", u)
		}
		replay := e.must(e.call("POST", "/v1/session", gTok, gReq, "Idempotency-Key", key), 200)
		if replay.Header.Get("Idempotent-Replayed") != "true" || string(replay.Body) != string(r.Body) {
			t.Errorf("replay mismatch")
		}
		other := map[string]any{"installation_id": "i1", "locale": "en"}
		if r := e.call("POST", "/v1/session", gTok, other, "Idempotency-Key", key); r.Status != 422 || code(r) != "idempotency_key_reused" {
			t.Errorf("reuse: %d %s", r.Status, r.Body)
		}
		r2 := e.must(e.call("POST", "/v1/session", gTok, gReq), 200).json()
		if r2["is_new_user"] != false {
			t.Errorf("second session is_new_user=%v", r2["is_new_user"])
		}
		if r := e.call("POST", "/v1/session", gTok, gReq, "Idempotency-Key", ""); r.Status != 422 {
			t.Errorf("missing key: %d", r.Status)
		}
		me := e.must(e.call("GET", "/v1/me", gTok, nil), 200)
		validateSchema(t, "User", me.Body)
		if me.Header.Get("Cache-Control") != "private, no-store" {
			t.Errorf("account responses must be private: %v", me.Header.Get("Cache-Control"))
		}
	})

	t.Run("devices and primary install", func(t *testing.T) {
		d := e.must(e.call("PUT", "/v1/me/devices/i2", gTok, map[string]any{"fcm_token": "fcm-i2", "app_version": 11, "locale": "en"}), 200)
		validateSchema(t, "Device", d.Body)
		if d.json()["primary"] != false {
			t.Errorf("second device must not steal primary")
		}
		r := e.must(e.call("POST", "/v1/me/devices/i2:make-primary", gTok, nil), 200)
		if r.json()["primary"] != true {
			t.Fatalf("%s", r.Body)
		}
		e.runJobs()
		var n47 bool
		for _, m := range e.sent.Sent() {
			if m.Data["template"] == "N-47" && m.Token == "fcm-i1" && m.Data["fallback_title"] != "" {
				n47 = true
			}
		}
		if !n47 {
			t.Errorf("N-47 not sent to the old primary: %+v", e.sent.Sent())
		}
		list := e.must(e.call("GET", "/v1/me/devices", gTok, nil), 200).json()["items"].([]any)
		if len(list) != 2 {
			t.Errorf("devices %v", list)
		}
		e.must(e.call("DELETE", "/v1/me/devices/i2", gTok, nil), 204)
		if r := e.call("POST", "/v1/me/devices/i1:nope", gTok, nil); r.Status != 404 {
			t.Errorf("unknown verb: %d", r.Status)
		}
	})

	t.Run("trial and entitlement", func(t *testing.T) {
		r := e.must(e.call("GET", "/v1/me/entitlement", gTok, nil), 200)
		validateSchema(t, "Entitlement", r.Body)
		if m := r.json(); m["plan"] != "free" || m["ads_enabled"] != true || m["trial_available"] != true {
			t.Fatalf("%s", r.Body)
		}
		r = e.must(e.call("POST", "/v1/me/trial", gTok, nil), 200)
		validateSchema(t, "Entitlement", r.Body)
		if m := r.json(); m["plan"] != "pro" || m["source"] != "trial" || m["ads_enabled"] != false || m["trial_available"] != false {
			t.Fatalf("%s", r.Body)
		}
		if r := e.call("POST", "/v1/me/trial", gTok, nil); r.Status != 409 || code(r) != "trial_already_used" {
			t.Errorf("second trial: %d %s", r.Status, r.Body)
		}
	})

	t.Run("play purchase verify, ownership, RTDN", func(t *testing.T) {
		exp := time.Now().Add(120 * 24 * time.Hour)
		e.play.Put("tok-season", billing.FakeSub("SUBSCRIPTION_STATE_ACTIVE", "season-4m-prepaid", billingRef, exp, false))
		in := map[string]any{"product_id": "pro", "base_plan_id": "season-4m-prepaid", "purchase_token": "tok-season"}
		r := e.must(e.call("POST", "/v1/me/purchases/google-play", gTok, in), 200)
		validateSchema(t, "Entitlement", r.Body)
		if m := r.json(); m["source"] != "play_season" || m["plan"] != "pro" {
			t.Fatalf("%s", r.Body)
		}
		if !e.play.Acked["tok-season"] {
			t.Error("server must acknowledge")
		}
		items := e.must(e.call("GET", "/v1/me/purchases", gTok, nil), 200).json()["items"].([]any)
		b, _ := json.Marshal(items[0])
		validateSchema(t, "PurchaseRecord", b)
		if strings.Contains(string(b), "tok-season") {
			t.Error("purchase token leaked")
		}
		// Another active account cannot take it.
		oTok, oReq := session("g2", "google", "j1")
		e.must(e.call("POST", "/v1/session", oTok, oReq), 200)
		if r := e.call("POST", "/v1/me/purchases/google-play", oTok, in); r.Status != 409 || code(r) != "purchase_owned_by_other_account" {
			t.Errorf("ownership: %d %s", r.Status, r.Body)
		}
		e.play.Put("tok-pending", billing.FakeSub("SUBSCRIPTION_STATE_PENDING", "yearly-autorenew", billingRef, exp, true))
		in2 := map[string]any{"product_id": "pro", "purchase_token": "tok-pending"}
		if r := e.call("POST", "/v1/me/purchases/google-play", gTok, in2); r.Status != 402 || code(r) != "purchase_pending" {
			t.Errorf("pending: %d %s", r.Status, r.Body)
		}
		// RTDN: Play says the season was revoked → fresh subscriptionsv2.get → grant ended.
		e.play.Put("tok-season", billing.FakeSub("SUBSCRIPTION_STATE_EXPIRED", "season-4m-prepaid", billingRef, time.Now().Add(-time.Minute), false))
		note, _ := json.Marshal(map[string]any{"packageName": "in.sarkarimatch.app",
			"subscriptionNotification": map[string]any{"notificationType": 13, "purchaseToken": "tok-season", "subscriptionId": "pro"}})
		env := map[string]any{"message": map[string]any{"data": base64.StdEncoding.EncodeToString(note), "messageId": "1"}, "subscription": "s"}
		e.must(e.call("POST", "/hooks/play-rtdn", "", env), 204)
		if m := e.must(e.call("GET", "/v1/me/entitlement", gTok, nil), 200).json(); m["source"] != "trial" {
			t.Errorf("after expiry the trial grant remains: %v", m)
		}
		var st string
		_ = e.pool.QueryRow(context.Background(), `SELECT state FROM purchase WHERE order_id LIKE '%season%'`).Scan(&st)
		if st != "expired" {
			t.Errorf("purchase state %q", st)
		}
	})

	t.Run("family invite, accept, watchlist, stop", func(t *testing.T) {
		mTok, mReq := session("m1", "anonymous", "k1")
		e.must(e.call("POST", "/v1/session", mTok, mReq), 200)
		if r := e.call("POST", "/v1/me/family", mTok, map[string]any{}); r.Status != 403 || code(r) != "pro_required" {
			t.Errorf("free owner: %d %s", r.Status, r.Body)
		}
		r := e.must(e.call("POST", "/v1/me/family", gTok, map[string]any{"relation": "parent"}), 201)
		validateSchema(t, "FamilyLink", r.Body)
		inviteURL := r.json()["invite_url"].(string)
		token := inviteURL[strings.LastIndex(inviteURL, "/")+1:]
		v := e.must(e.call("GET", "/v1/family-invites/"+token, "", nil), 200)
		validateSchema(t, "FamilyInviteView", v.Body)
		if r := e.call("POST", "/v1/family-invites/"+token+":accept", "", map[string]any{"consent_version": "2026-11-19"}); r.Status != 401 {
			t.Errorf("accept without account: %d", r.Status)
		}
		f := e.must(e.call("POST", "/v1/family-invites/"+token+":accept", mTok, map[string]any{"consent_version": "2026-11-19"}), 200)
		validateSchema(t, "FamilyFollowing", f.Body)
		linkID := f.json()["link_id"].(string)
		if r := e.call("POST", "/v1/family-invites/"+token+":accept", mTok, map[string]any{"consent_version": "x"}); r.Status != 410 {
			t.Errorf("single use: %d", r.Status)
		}
		ct := base64.StdEncoding.EncodeToString([]byte("opaque-ciphertext"))
		e.must(e.call("PUT", "/v1/me/family/"+linkID+"/watchlist", gTok, map[string]any{"version": 1, "ciphertext": ct}), 204)
		if r := e.call("PUT", "/v1/me/family/"+linkID+"/watchlist", gTok, map[string]any{"version": 1, "ciphertext": ct}); r.Status != 409 {
			t.Errorf("stale version: %d", r.Status)
		}
		w := e.must(e.call("GET", "/v1/family/following/"+linkID+"/watchlist", mTok, nil), 200)
		validateSchema(t, "EncryptedWatchlist", w.Body)
		e.must(e.call("GET", "/v1/family/following/"+linkID+"/watchlist", mTok, nil, "If-None-Match", w.Header.Get("ETag")), 304)
		fl := e.must(e.call("GET", "/v1/family/following", mTok, nil), 200).json()["items"].([]any)
		if len(fl) != 1 {
			t.Errorf("following %v", fl)
		}
		e.must(e.call("DELETE", "/v1/family/following/"+linkID, mTok, nil), 204)
		if items := e.must(e.call("GET", "/v1/me/family", gTok, nil), 200).json()["items"].([]any); len(items) != 0 {
			t.Errorf("owner still lists the link: %v", items)
		}
		e.runJobs()
		var accepted, removed bool
		for _, m := range e.sent.Sent() {
			accepted = accepted || (m.Data["family_event"] == "accepted" && m.Token == "fcm-i1")
			removed = removed || (m.Data["family_event"] == "removed" && m.Token == "fcm-i1")
		}
		if !accepted || !removed {
			t.Errorf("family pushes accepted=%v removed=%v", accepted, removed)
		}
	})

	var noticeID, postID string
	t.Run("curation publish → catalog", func(t *testing.T) {
		admin := func(email, method, path string, body any) resp {
			return e.call(method, path, "", body, "X-Admin-Email", email)
		}
		draft := map[string]any{"org_code": "ssc", "advt_no": "GD-2026", "exam_type": "ssc", "scope": "all_india", "status": "open",
			"content": map[string]any{"title": map[string]string{"en": "SSC GD 2026", "hi": "SSC GD 2026"},
				"key_dates": []any{map[string]any{"kind": "apply_end", "date": time.Now().AddDate(0, 0, 10).Format(time.DateOnly), "time": "23:00"}},
				"links":     []any{map[string]any{"kind": "official_pdf", "url": "https://ssc.gov.in"}},
				"source":    map[string]any{"url": "https://ssc.gov.in"}},
			"posts": []any{map[string]any{"code": "CAPF-CT-GD", "name": map[string]string{"en": "Constable (GD)", "hi": "कांस्टेबल (GD)"},
				"exam_tags": []string{"ssc", "police"}}}}
		if r := e.call("POST", "/admin/api/notices", "", draft); r.Status != 401 {
			t.Errorf("admin without auth: %d", r.Status)
		}
		task := e.must(admin("rev@example.com", "POST", "/admin/api/notices", draft), 201).json()
		taskID := task["id"].(string)
		noticeID = task["recruitment_id"].(string)
		// The raw worked example is blocked (unverified rows have no page)…
		rep := e.must(admin("rev@example.com", "PUT", "/admin/api/tasks/"+taskID+"/posts/CAPF-CT-GD/rule", testutil.ExampleRule(t, "ssc-gd-2026")), 200).json()
		if !strings.Contains(fmt.Sprint(rep["findings"]), "source_page_missing") {
			t.Errorf("findings %v", rep["findings"])
		}
		if r := admin("rev@example.com", "POST", "/admin/api/tasks/"+taskID+"/approve", nil); r.Status != 422 {
			t.Errorf("approve with blocking findings: %d", r.Status)
		}
		// …the publishable version passes; ESM rows force a second reviewer.
		e.must(admin("rev@example.com", "PUT", "/admin/api/tasks/"+taskID+"/posts/CAPF-CT-GD/rule",
			testutil.Publishable(t, testutil.ExampleRule(t, "ssc-gd-2026"))), 200)
		if st := e.must(admin("rev@example.com", "POST", "/admin/api/tasks/"+taskID+"/approve", nil), 200).json()["state"]; st != "awaiting_second" {
			t.Fatalf("state %v", st)
		}
		if r := admin("rev@example.com", "POST", "/admin/api/tasks/"+taskID+"/approve", nil); r.Status != 409 {
			t.Errorf("same reviewer twice: %d", r.Status)
		}
		e.must(admin("owner@example.com", "POST", "/admin/api/tasks/"+taskID+"/approve", nil), 200)
		out := e.must(admin("owner@example.com", "POST", "/admin/api/tasks/"+taskID+"/publish", nil), 200).json()
		if out["catalog_seq"] != float64(1) || out["built"] != true {
			t.Fatalf("publish %v", out)
		}

		head := e.must(e.call("GET", "/v1/catalog/head", "", nil), 200)
		validateSchema(t, "CatalogHead", head.Body)
		h := head.json()
		if h["seq"] != float64(1) || h["snapshot"].(map[string]any)["seq"] != float64(1) {
			t.Fatalf("head %s", head.Body)
		}
		e.must(e.call("GET", "/v1/catalog/head", "", nil, "If-None-Match", head.Header.Get("ETag")), 304)
		b := e.must(e.call("GET", "/v1/catalog/batches/1?locales=hi", "", nil), 200)
		validateSchema(t, "CatalogBatch", b.Body)
		if b.Header.Get("Cache-Control") != "public, max-age=31536000, immutable" {
			t.Errorf("batch cache %q", b.Header.Get("Cache-Control"))
		}
		shaWant := h["snapshot"].(map[string]any)["sha256"].(map[string]any)["en,hi"]
		snap := e.must(e.call("GET", "/v1/catalog/snapshots/1?locales=en,hi", "", nil), 200)
		validateSchema(t, "CatalogSnapshot", snap.Body)
		if got := strings.Trim(snap.Header.Get("ETag"), `"`); got != shaWant {
			t.Errorf("snapshot sha %s != head %v", got, shaWant)
		}
		// Unusual locale set rendered on demand; Tamil requested → only en (no ta content) + filtered.
		ta := e.must(e.call("GET", "/v1/catalog/batches/1?locales=ta", "", nil), 200)
		if strings.Contains(string(ta.Body), `"hi":`) {
			t.Error("hi text leaked into the en,ta set")
		}
		if strings.Contains(string(b.Body), `"quote"`) || strings.Contains(string(b.Body), `"review"`) {
			t.Error("catalog must not ship review notes or source quotes")
		}
		nb := e.must(e.call("GET", "/v1/notices/"+noticeID, "", nil), 200)
		validateSchema(t, "Notice", nb.Body)
		postID = nb.json()["posts"].([]any)[0].(map[string]any)["id"].(string)
		e.must(e.call("GET", "/v1/catalog/batches/99", "", nil), 404)

		var deadlines int
		_ = e.pool.QueryRow(context.Background(), `SELECT count(*) FROM topic_send WHERE topic = $1`, "p_"+postID).Scan(&deadlines)
		if deadlines != 3 {
			t.Errorf("R3/R1/R-last topic pushes scheduled: %d", deadlines)
		}
		e.runJobs()
		var sync bool
		for _, m := range e.sent.Sent() {
			sync = sync || (m.Topic == "catalog" && m.Data["type"] == "sync" && m.Data["seq"] == "1")
		}
		if !sync {
			t.Error("catalog sync topic push not sent")
		}
	})

	t.Run("wrong-match reports auto-pause", func(t *testing.T) {
		for i := 0; i < 3; i++ {
			r := e.must(e.call("POST", "/v1/reports", "", map[string]any{"post_id": postID, "rule_version": 1, "reason": "not_eligible_age"}), 201)
			validateSchema(t, "MatchReport", r.Body)
		}
		if r := e.call("POST", "/v1/reports", "", map[string]any{"post_id": "p_nope", "rule_version": 1, "reason": "other"}); r.Status != 422 {
			t.Errorf("unknown post: %d", r.Status)
		}
		e.runJobs()
		h := e.must(e.call("GET", "/v1/catalog/head", "", nil), 200).json()
		if h["seq"] != float64(2) {
			t.Fatalf("republish batch missing: %v", h)
		}
		if !strings.Contains(string(e.must(e.call("GET", "/v1/catalog/batches/2", "", nil), 200).Body), `"new_match_paused":true`) {
			t.Error("new_match_paused not in batch 2")
		}
	})

	t.Run("self-test and telemetry", func(t *testing.T) {
		r := e.must(e.call("POST", "/v1/push-tests", "", map[string]any{"fcm_token": "fcm-self"}), 202)
		validateSchema(t, "PushTest", r.Body)
		id := r.json()["test_id"].(string)
		e.must(e.call("POST", "/v1/push-tests/"+id+":received", "", nil), 204)
		g := e.must(e.call("GET", "/v1/push-tests/"+id, "", nil), 200)
		validateSchema(t, "PushTest", g.Body)
		if g.json()["status"] != "received" {
			t.Errorf("%s", g.Body)
		}
		today := time.Now().In(push.IST).Format(time.DateOnly)
		e.must(e.call("POST", "/v1/telemetry", "", map[string]any{"ist_date": today, "app_version": 10, "locale": "hi",
			"counters": map[string]int{"applied": 1, "matches_shown": 4}}), 204)
		if r := e.call("POST", "/v1/telemetry", "", map[string]any{"ist_date": today, "app_version": 10, "locale": "hi",
			"counters": map[string]int{"applied": 5000}}); r.Status != 422 {
			t.Errorf("clamp: %d", r.Status)
		}
	})

	t.Run("backup CAS and google requirement", func(t *testing.T) {
		mTok := "dev:m1"
		if r := e.call("PUT", "/v1/me/backup", mTok, map[string]any{"ciphertext": "AA==", "key_id": "k", "format_version": 1}); code(r) != "google_link_required" {
			t.Errorf("anonymous backup: %s", r.Body)
		}
		e.must(e.call("GET", "/v1/me/backup", gTok, nil), 404)
		in := map[string]any{"ciphertext": base64.StdEncoding.EncodeToString([]byte("c1")), "key_id": "blockstore-1", "format_version": 1}
		r := e.must(e.call("PUT", "/v1/me/backup", gTok, in), 200)
		validateSchema(t, "Backup", r.Body)
		e.must(e.call("PUT", "/v1/me/backup", gTok, in, "If-Match", r.Header.Get("ETag")), 200)
		if r := e.call("PUT", "/v1/me/backup", gTok, in, "If-Match", `"1"`); r.Status != 412 || code(r) != "backup_conflict" {
			t.Errorf("stale write: %d", r.Status)
		}
		g := e.must(e.call("GET", "/v1/me/backup", gTok, nil), 200).json()
		if g["version"] != float64(2) || g["key_id"] != "blockstore-1" {
			t.Errorf("%v", g)
		}
		s := e.must(e.call("POST", "/v1/session", gTok, gReq), 200).json()
		if s["has_backup"] != true {
			t.Error("has_backup should be true")
		}
	})

	t.Run("consents", func(t *testing.T) {
		c := e.must(e.call("POST", "/v1/me/consents", gTok, map[string]any{"purpose": "cloud_backup", "version": "2026-11-19", "granted": true}), 200)
		validateSchema(t, "Consent", c.Body)
		items := e.must(e.call("GET", "/v1/me/consents", gTok, nil), 200).json()["items"].([]any)
		if len(items) < 2 {
			t.Errorf("consents %v", items)
		}
	})

	t.Run("not implemented returns 501", func(t *testing.T) {
		if r := e.call("POST", "/v1/me/referral:redeem", gTok, map[string]any{"code": "ABCDEFGH"}); r.Status != 501 {
			t.Errorf("%d", r.Status)
		}
		validateSchema(t, "Referral", e.must(e.call("GET", "/v1/me/referral", gTok, nil), 200).Body)
	})

	t.Run("account deletion and hard delete", func(t *testing.T) {
		d := e.must(e.call("DELETE", "/v1/me", gTok, map[string]any{"reason": "privacy"}), 202)
		validateSchema(t, "DeletionStatus", d.Body)
		if r := e.call("GET", "/v1/me/entitlement", gTok, nil); code(r) != "account_pending_deletion" {
			t.Errorf("pending: %s", r.Body)
		}
		if m := e.must(e.call("GET", "/v1/me", gTok, nil), 200).json(); m["deletion"].(map[string]any)["state"] != "pending" || m["devices_count"] != float64(0) {
			t.Errorf("%v", m)
		}
		if st := e.must(e.call("POST", "/v1/me/deletion:cancel", gTok, nil), 200).json()["state"]; st != "none" {
			t.Errorf("cancel %v", st)
		}
		e.must(e.call("DELETE", "/v1/me", gTok, nil), 202)
		ctx := context.Background()
		if _, err := e.pool.Exec(ctx, `UPDATE app_user SET hard_delete_at = now() - interval '1 minute' WHERE id = $1`, userID); err != nil {
			t.Fatal(err)
		}
		out, err := e.app.Worker.Tick(ctx, "housekeep")
		if err != nil || out["hard_deleted"] != 1 {
			t.Fatalf("housekeep %v %v", out, err)
		}
		var users, linked, grants int
		_ = e.pool.QueryRow(ctx, `SELECT (SELECT count(*) FROM app_user WHERE id = $1),
			(SELECT count(*) FROM purchase WHERE user_id = $1), (SELECT count(*) FROM entitlement_grant WHERE user_id = $1)`, userID).
			Scan(&users, &linked, &grants)
		var purchases int
		_ = e.pool.QueryRow(ctx, `SELECT count(*) FROM purchase WHERE user_id IS NULL`).Scan(&purchases)
		if users != 0 || linked != 0 || grants != 0 || purchases == 0 {
			t.Errorf("hard delete: users=%d linked=%d grants=%d unlinked purchases=%d", users, linked, grants, purchases)
		}
	})

	t.Run("every openapi operation is routed", func(t *testing.T) {
		param := regexp.MustCompile(`\{[a-z_]+\}`)
		for _, op := range specOperations(t) {
			method, path, _ := strings.Cut(op, " ")
			r := e.call(method, param.ReplaceAllString(path, "x1"), "", map[string]any{})
			if strings.Contains(string(r.Body), "no route") {
				t.Errorf("%s is not routed (%d)", op, r.Status)
			}
		}
	})
}
