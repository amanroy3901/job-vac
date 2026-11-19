package httpapi_test

import (
	"context"
	"encoding/json"
	"sync"
	"testing"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/appstore"
)

const (
	testBundle     = "in.sarkarimatch.app"
	testAppAppleID = 6700000001
	yearlyID       = "in.sarkarimatch.pro.yearly"
	seasonID       = "in.sarkarimatch.pro.season4m"
)

var notifSeq int

// fakeServerAPI is an in-memory App Store Server API (statuses keyed by originalTransactionId).
type fakeServerAPI struct {
	mu       sync.Mutex
	statuses map[string][]appstore.LastTransaction
}

func (f *fakeServerAPI) put(otid string, lt appstore.LastTransaction) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.statuses[otid] = []appstore.LastTransaction{lt}
}

func (f *fakeServerAPI) GetTransactionInfo(context.Context, string) (string, error) {
	return "", appstore.ErrNotFound
}

func (f *fakeServerAPI) GetSubscriptionStatuses(_ context.Context, id string) ([]appstore.LastTransaction, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if lts, ok := f.statuses[id]; ok {
		return lts, nil
	}
	return nil, appstore.ErrNotFound
}

func (f *fakeServerAPI) GetTransactionHistory(context.Context, string, string) ([]string, string, bool, error) {
	return nil, "", false, nil
}

// asTx builds a JWSTransaction payload.
func asTx(otid, txid, product, typ, token string, purchase, expires time.Time) appstore.Transaction {
	t := appstore.Transaction{TransactionID: txid, OriginalTransactionID: otid, BundleID: testBundle, ProductID: product,
		Type: typ, AppAccountToken: token, PurchaseDate: purchase.UnixMilli(), OriginalPurchaseDate: purchase.UnixMilli(),
		SignedDate: time.Now().UnixMilli(), Environment: appstore.EnvProduction, InAppOwnershipType: "PURCHASED",
		TransactionReason: "PURCHASE", Storefront: "IND", Currency: "INR"}
	if !expires.IsZero() {
		t.ExpiresDate = expires.UnixMilli()
	}
	return t
}

// notify posts a signed App Store Server Notification V2 and returns the response.
func (e *env) notify(typ, subtype string, status int, tx appstore.Transaction, ri *appstore.RenewalInfo, signedAt time.Time) resp {
	e.t.Helper()
	notifSeq++
	data := &appstore.NotificationData{AppAppleID: testAppAppleID, BundleID: testBundle, Environment: appstore.EnvProduction,
		SignedTransactionInfo: e.asign.Sign(tx), Status: status}
	if ri != nil {
		data.SignedRenewalInfo = e.asign.Sign(ri)
	}
	n := appstore.Notification{NotificationType: typ, Subtype: subtype, Version: "2.0", SignedDate: signedAt.UnixMilli(),
		NotificationUUID: "uuid-" + typ + "-" + time.Now().Format("150405.000000") + "-" + string(rune('a'+notifSeq%26)), Data: data}
	return e.call("POST", "/hooks/app-store", "", map[string]any{"signedPayload": e.asign.Sign(n)})
}

func TestIOS(t *testing.T) {
	e := newEnv(t)
	ctx := context.Background()

	// Sign in with Apple on an iPhone; the authorization code is exchanged and kept for revocation.
	aTok, aReq := session("ap1", "apple", "ios-1")
	aReq["device"].(map[string]any)["platform"] = "ios"
	aReq["apple_authorization_code"] = "code-ap1"
	r := e.must(e.call("POST", "/v1/session", aTok, aReq), 200)
	validateSchema(t, "SessionResponse", r.Body)
	u := r.json()["user"].(map[string]any)
	ref := u["billing_account_ref"].(string)
	if u["auth_provider"] != "apple" || u["google_email"] != nil {
		t.Fatalf("user %v", u)
	}
	var stored int
	_ = e.pool.QueryRow(ctx, `SELECT count(*) FROM apple_credential`).Scan(&stored)
	if stored != 1 {
		t.Fatalf("apple credential not stored")
	}
	devs := e.must(e.call("GET", "/v1/me/devices", aTok, nil), 200).json()["items"].([]any)
	if devs[0].(map[string]any)["platform"] != "ios" {
		t.Errorf("device platform %v", devs)
	}
	b, _ := json.Marshal(devs[0])
	validateSchema(t, "Device", b)
	if r := e.call("PUT", "/v1/me/devices/ios-1", aTok, map[string]any{"platform": "windows", "fcm_token": "x", "app_version": 10, "locale": "hi"}); r.Status != 422 {
		t.Errorf("bad platform: %d", r.Status)
	}
	// Apple-linked accounts may back up (google_link_required means "Google or Apple").
	e.must(e.call("PUT", "/v1/me/backup", aTok, map[string]any{"ciphertext": "AAEC", "key_id": "keychain-1", "format_version": 1}), 200)

	t.Run("products carry App Store ids", func(t *testing.T) {
		for _, p := range e.must(e.call("GET", "/v1/products", "", nil), 200).json()["items"].([]any) {
			m := p.(map[string]any)
			want := map[string]string{"pro_year": yearlyID, "pro_season": seasonID}[m["id"].(string)]
			if m["appstore_product_id"] != want {
				t.Errorf("%v", m)
			}
		}
	})

	now := time.Now()
	yearTx := asTx("1000", "1000", yearlyID, appstore.TypeAutoRenewable, ref, now, now.AddDate(0, 0, 14))
	yearTx.OfferType, yearTx.OfferDiscountType = 1, "FREE_TRIAL"

	t.Run("verify yearly with intro free trial", func(t *testing.T) {
		if r := e.call("POST", "/v1/me/purchases/app-store", aTok, map[string]any{"signed_transaction": "not.a.jws"}); r.Status != 402 || code(r) != "purchase_invalid" {
			t.Errorf("garbage: %d %s", r.Status, r.Body)
		}
		wrong := yearTx
		wrong.BundleID = "com.attacker"
		if r := e.call("POST", "/v1/me/purchases/app-store", aTok, map[string]any{"signed_transaction": e.asign.Sign(wrong)}); r.Status != 402 {
			t.Errorf("other bundle: %d", r.Status)
		}
		ri := appstore.RenewalInfo{OriginalTransactionID: "1000", ProductID: yearlyID, AutoRenewProductID: yearlyID,
			AutoRenewStatus: 1, SignedDate: now.UnixMilli(), Environment: appstore.EnvProduction}
		r := e.must(e.call("POST", "/v1/me/purchases/app-store", aTok, map[string]any{"signed_transaction": e.asign.Sign(yearTx),
			"signed_renewal_info": e.asign.Sign(ri)}), 200)
		validateSchema(t, "Entitlement", r.Body)
		m := r.json()
		if m["plan"] != "pro" || m["source"] != "appstore_year" || m["platform"] != "ios" || m["ads_enabled"] != false ||
			m["manage_url"] != "https://apps.apple.com/account/subscriptions" || m["auto_renewing"] != true || m["trial_available"] != false {
			t.Fatalf("%s", r.Body)
		}
		// Idempotent re-verify (restore / Transaction.updates replay).
		e.must(e.call("POST", "/v1/me/purchases/app-store", aTok, map[string]any{"signed_transaction": e.asign.Sign(yearTx)}), 200)
		var n int
		_ = e.pool.QueryRow(ctx, `SELECT count(*) FROM purchase WHERE provider = 'app_store'`).Scan(&n)
		if n != 1 {
			t.Errorf("duplicate purchase rows: %d", n)
		}
		items := e.must(e.call("GET", "/v1/me/purchases", aTok, nil), 200).json()["items"].([]any)
		pb, _ := json.Marshal(items[0])
		validateSchema(t, "PurchaseRecord", pb)
		if items[0].(map[string]any)["provider"] != "app_store" {
			t.Errorf("%s", pb)
		}
		if r := e.call("POST", "/v1/me/trial", aTok, nil); code(r) != "trial_already_used" {
			t.Errorf("server trial after the App Store free trial: %d %s", r.Status, r.Body)
		}
	})

	t.Run("same Firebase uid on Android keeps Pro", func(t *testing.T) {
		_, andReq := session("ap1", "apple", "and-1") // Sign in with Apple on Android (web flow) → same uid
		r := e.must(e.call("POST", "/v1/session", aTok, andReq), 200).json()
		if ent := r["entitlement"].(map[string]any); ent["plan"] != "pro" || ent["source"] != "appstore_year" {
			t.Fatalf("cross-platform entitlement %v", ent)
		}
		var plat string
		_ = e.pool.QueryRow(ctx, `SELECT platform FROM device WHERE installation_id = 'and-1'`).Scan(&plat)
		if plat != "android" {
			t.Errorf("android device platform %q", plat)
		}
	})

	t.Run("another active account cannot claim the subscription", func(t *testing.T) {
		oTok, oReq := session("other1", "google", "o-1")
		e.must(e.call("POST", "/v1/session", oTok, oReq), 200)
		if r := e.call("POST", "/v1/me/purchases/app-store", oTok, map[string]any{"signed_transaction": e.asign.Sign(yearTx)}); r.Status != 409 || code(r) != "purchase_owned_by_other_account" {
			t.Errorf("ownership: %d %s", r.Status, r.Body)
		}
	})

	ent := func() map[string]any {
		return e.must(e.call("GET", "/v1/me/entitlement", aTok, nil), 200).json()
	}
	yes := true
	t.Run("server notifications drive the state", func(t *testing.T) {
		at := time.Now()
		past := yearTx
		past.ExpiresDate = at.Add(-time.Hour).UnixMilli()
		past.OfferType, past.OfferDiscountType = 0, ""
		grace := &appstore.RenewalInfo{OriginalTransactionID: "1000", ProductID: yearlyID, AutoRenewStatus: 1, IsInBillingRetryPeriod: &yes,
			GracePeriodExpiresDate: at.AddDate(0, 0, 6).UnixMilli(), SignedDate: at.UnixMilli(), Environment: appstore.EnvProduction}
		r := e.must(e.notify("DID_FAIL_TO_RENEW", "GRACE_PERIOD", appstore.StatusGracePeriod, past, grace, at), 200)
		if m := ent(); m["plan"] != "pro" || m["status"] != "in_grace" {
			t.Fatalf("grace: %v (%s)", m, r.Body)
		}
		// Older observation arriving late is ignored.
		stale := yearTx
		e.must(e.notify("SUBSCRIBED", "INITIAL_BUY", appstore.StatusActive, stale, nil, at.Add(-time.Hour)), 200)
		if m := ent(); m["status"] != "in_grace" {
			t.Fatalf("stale notification applied: %v", m)
		}
		retry := *grace
		retry.GracePeriodExpiresDate = at.Add(-time.Minute).UnixMilli()
		e.must(e.notify("GRACE_PERIOD_EXPIRED", "", appstore.StatusBillingRetry, past, &retry, at.Add(time.Second)), 200)
		if m := ent(); m["plan"] != "free" || m["status"] != "on_hold" || m["platform"] != "ios" || m["ads_enabled"] != true {
			t.Fatalf("billing retry: %v", m)
		}
		renewed := asTx("1000", "1001", yearlyID, appstore.TypeAutoRenewable, ref, at, at.AddDate(1, 0, 0))
		renewed.TransactionReason = "RENEWAL"
		ri := &appstore.RenewalInfo{OriginalTransactionID: "1000", ProductID: yearlyID, AutoRenewStatus: 1, SignedDate: at.UnixMilli(), Environment: appstore.EnvProduction}
		e.must(e.notify("DID_RENEW", "BILLING_RECOVERY", appstore.StatusActive, renewed, ri, at.Add(2*time.Second)), 200)
		if m := ent(); m["plan"] != "pro" || m["status"] != "active" {
			t.Fatalf("renewed: %v", m)
		}
		off := *ri
		off.AutoRenewStatus = 0
		e.must(e.notify("DID_CHANGE_RENEWAL_STATUS", "AUTO_RENEW_DISABLED", appstore.StatusActive, renewed, &off, at.Add(3*time.Second)), 200)
		if m := ent(); m["plan"] != "pro" || m["auto_renewing"] != false {
			t.Fatalf("auto renew off: %v", m)
		}
		refunded := renewed
		refunded.RevocationDate, refunded.RevocationType = at.UnixMilli(), "REFUND_FULL"
		e.must(e.notify("REFUND", "", appstore.StatusRevoked, refunded, &off, at.Add(4*time.Second)), 200)
		if m := ent(); m["plan"] != "free" {
			t.Fatalf("refund: %v", m)
		}
		var st string
		_ = e.pool.QueryRow(ctx, `SELECT state FROM purchase WHERE original_transaction_id = '1000'`).Scan(&st)
		if st != "refunded" {
			t.Errorf("purchase state %q", st)
		}
		e.runJobs()
		var pushed bool
		for _, m := range e.sent.Sent() {
			pushed = pushed || (m.Data["template"] == "entitlement" && m.Token == "fcm-ios-1")
		}
		if !pushed {
			t.Error("entitlement account push not sent to the iPhone")
		}
	})

	t.Run("notification dedupe and signature", func(t *testing.T) {
		n := appstore.Notification{NotificationType: "TEST", NotificationUUID: "fixed-uuid", SignedDate: time.Now().UnixMilli(),
			Data: &appstore.NotificationData{AppAppleID: testAppAppleID, BundleID: testBundle, Environment: appstore.EnvProduction}}
		body := map[string]any{"signedPayload": e.asign.Sign(n)}
		e.must(e.call("POST", "/hooks/app-store", "", body), 200)
		e.must(e.call("POST", "/hooks/app-store", "", body), 200)
		var c int
		_ = e.pool.QueryRow(ctx, `SELECT count(*) FROM app_store_notification WHERE notification_uuid = 'fixed-uuid'`).Scan(&c)
		if c != 1 {
			t.Errorf("dedupe rows %d", c)
		}
		other, _ := appstore.NewTestSigner() // not trusted
		if r := e.call("POST", "/hooks/app-store", "", map[string]any{"signedPayload": other.Sign(n)}); r.Status != 401 {
			t.Errorf("untrusted chain: %d", r.Status)
		}
		n.Data.AppAppleID = 1
		if r := e.call("POST", "/hooks/app-store", "", map[string]any{"signedPayload": e.asign.Sign(n)}); r.Status != 401 {
			t.Errorf("other app: %d", r.Status)
		}
	})

	t.Run("non-renewing season: notification first, then stacking top-up", func(t *testing.T) {
		at := time.Now()
		s1 := asTx("2000", "2000", seasonID, appstore.TypeNonRenewing, ref, at, time.Time{})
		e.must(e.notify("ONE_TIME_CHARGE", "", 0, s1, nil, at), 200) // owner found by appAccountToken
		m := ent()
		if m["plan"] != "pro" || m["source"] != "appstore_season" {
			t.Fatalf("season: %v", m)
		}
		end1, _ := time.Parse(time.RFC3339, m["expires_at"].(string))
		if want := at.AddDate(0, 4, 0); end1.Sub(want).Abs() > time.Second {
			t.Errorf("season end %v want %v", end1, want)
		}
		s2 := asTx("2001", "2001", seasonID, appstore.TypeNonRenewing, ref, at.Add(time.Minute), time.Time{})
		r := e.must(e.call("POST", "/v1/me/purchases/app-store", aTok, map[string]any{"signed_transaction": e.asign.Sign(s2)}), 200).json()
		end2, _ := time.Parse(time.RFC3339, r["expires_at"].(string))
		if want := end1.AddDate(0, 4, 0); end2.Sub(want).Abs() > time.Second {
			t.Errorf("top-up end %v want %v (stacked)", end2, want)
		}
		// Replays never move the dates.
		e.must(e.call("POST", "/v1/me/purchases/app-store", aTok, map[string]any{"signed_transaction": e.asign.Sign(s2)}), 200)
		if again, _ := time.Parse(time.RFC3339, ent()["expires_at"].(string)); !again.Equal(end2) {
			t.Errorf("replay moved expiry %v → %v", end2, again)
		}
		wrongType := s2
		wrongType.TransactionID, wrongType.OriginalTransactionID, wrongType.Type = "2002", "2002", appstore.TypeAutoRenewable
		if r := e.call("POST", "/v1/me/purchases/app-store", aTok, map[string]any{"signed_transaction": e.asign.Sign(wrongType)}); r.Status != 402 {
			t.Errorf("type mismatch: %d", r.Status)
		}
	})

	t.Run("server api supplies the latest state when the app sends an old transaction", func(t *testing.T) {
		cTok, cReq := session("ap3", "apple", "ios-3")
		u := e.must(e.call("POST", "/v1/session", cTok, cReq), 200).json()["user"].(map[string]any)
		cref := u["billing_account_ref"].(string)
		at := time.Now()
		old := asTx("3000", "3000", yearlyID, appstore.TypeAutoRenewable, cref, at.AddDate(-1, 0, 0), at.Add(-time.Hour))
		latest := asTx("3000", "3005", yearlyID, appstore.TypeAutoRenewable, cref, at.AddDate(-1, 0, 0), at.Add(-time.Hour))
		ri := appstore.RenewalInfo{OriginalTransactionID: "3000", ProductID: yearlyID, AutoRenewStatus: 1, IsInBillingRetryPeriod: &yes,
			GracePeriodExpiresDate: at.AddDate(0, 0, 3).UnixMilli(), SignedDate: at.UnixMilli(), Environment: appstore.EnvProduction}
		e.asapi.put("3000", appstore.LastTransaction{OriginalTransactionID: "3000", Status: appstore.StatusGracePeriod,
			SignedTransactionInfo: e.asign.Sign(latest), SignedRenewalInfo: e.asign.Sign(ri)})
		m := e.must(e.call("POST", "/v1/me/purchases/app-store", cTok, map[string]any{"signed_transaction": e.asign.Sign(old)}), 200).json()
		if m["plan"] != "pro" || m["status"] != "in_grace" {
			t.Fatalf("%v", m)
		}
	})

	t.Run("deletion revokes Sign in with Apple tokens", func(t *testing.T) {
		d := e.must(e.call("DELETE", "/v1/me", aTok, map[string]any{"reason": "privacy"}), 202)
		validateSchema(t, "DeletionStatus", d.Body)
		if d.json()["apple_revocation"] != "queued" {
			t.Fatalf("%s", d.Body)
		}
		e.runJobs()
		if got := e.apple.RevokedTokens(); len(got) != 1 || got[0] != "refresh:code-ap1" {
			t.Fatalf("revoked %v", got)
		}
		// An Apple user whose code never reached us gets token_missing, and a fresh code fixes it.
		bTok, bReq := session("ap2", "apple", "ios-2")
		e.must(e.call("POST", "/v1/session", bTok, bReq), 200)
		if st := e.must(e.call("DELETE", "/v1/me", bTok, nil), 202).json()["apple_revocation"]; st != "token_missing" {
			t.Fatalf("want token_missing, got %v", st)
		}
		e.must(e.call("POST", "/v1/me/deletion:cancel", bTok, nil), 200)
		if st := e.must(e.call("DELETE", "/v1/me", bTok, map[string]any{"apple_authorization_code": "code-ap2"}), 202).json()["apple_revocation"]; st != "queued" {
			t.Fatalf("with code: %v", st)
		}
		e.runJobs()
		if got := e.apple.RevokedTokens(); len(got) != 2 || got[1] != "refresh:code-ap2" {
			t.Fatalf("revoked %v", got)
		}
		// Google users are unaffected.
		gTok, gReq := session("g9", "google", "g-9")
		e.must(e.call("POST", "/v1/session", gTok, gReq), 200)
		if v, ok := e.must(e.call("DELETE", "/v1/me", gTok, nil), 202).json()["apple_revocation"]; ok {
			t.Errorf("google user got apple_revocation=%v", v)
		}
	})
}
