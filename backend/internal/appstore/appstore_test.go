package appstore

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func TestEmbeddedRootIsAppleRootCAG3(t *testing.T) {
	block, _ := pem.Decode(AppleRootPEM())
	sum := sha256.Sum256(block.Bytes)
	if hex.EncodeToString(sum[:]) != AppleRootCAG3SHA256 {
		t.Fatalf("embedded root fingerprint %x", sum)
	}
	c, err := x509.ParseCertificate(block.Bytes)
	if err != nil || c.Subject.CommonName != "Apple Root CA - G3" || c.NotAfter.Year() != 2039 {
		t.Fatalf("%v %v", c.Subject, err)
	}
}

// A real Apple-signed JWSRenewalInfo (Sandbox, signed 2023-05-23) from the public test data of
// github.com/awa/go-iap v1.32.0 (appstore/validator_test.go). Its x5c chain ends at the real Apple Root CA - G3,
// so this proves the verifier accepts Apple's production PKI (leaf/intermediate OIDs, P-256 leaf, signedDate time).
func TestRealAppleChainVerifies(t *testing.T) {
	raw, err := os.ReadFile("testdata/sandbox-renewal-info.jws")
	if err != nil {
		t.Fatal(err)
	}
	v := &Verifier{BundleID: "co.ringalarm.swtich", Environments: []string{EnvSandbox}}
	ri, err := v.VerifyRenewalInfo(strings.TrimSpace(string(raw)))
	if err != nil {
		t.Fatal(err)
	}
	if ri.OriginalTransactionID != "2000000335310644" || ri.AutoRenewStatus != 1 || ri.Environment != EnvSandbox {
		t.Fatalf("%+v", ri)
	}
	// Production-only verifier refuses the Sandbox payload.
	if _, err := (&Verifier{Environments: []string{EnvProduction}}).VerifyRenewalInfo(string(raw)); !errors.Is(err, ErrEnvironment) {
		t.Fatalf("want environment error, got %v", err)
	}
	// Tampering with the payload breaks the signature.
	parts := strings.Split(strings.TrimSpace(string(raw)), ".")
	parts[1] = parts[1][:len(parts[1])-4] + "AAAA"
	if _, err := v.VerifyRenewalInfo(strings.Join(parts, ".")); !errors.Is(err, ErrInvalid) {
		t.Fatalf("tampered payload accepted: %v", err)
	}
	// A test chain is not trusted by the Apple root.
	ts, _ := NewTestSigner()
	fake := ts.Sign(map[string]any{"originalTransactionId": "1", "environment": EnvSandbox, "signedDate": time.Now().UnixMilli()})
	if _, err := v.VerifyRenewalInfo(fake); !errors.Is(err, ErrInvalid) {
		t.Fatalf("self-signed chain accepted against Apple root: %v", err)
	}
}

func TestVerifyTransactionChecks(t *testing.T) {
	ts, err := NewTestSigner()
	if err != nil {
		t.Fatal(err)
	}
	v := &Verifier{Roots: ts.Roots(), BundleID: "in.sarkarimatch.app", AppAppleID: 123, Environments: []string{EnvProduction, EnvSandbox}}
	now := time.Now().UnixMilli()
	tx := Transaction{TransactionID: "2", OriginalTransactionID: "1", BundleID: "in.sarkarimatch.app", ProductID: "p",
		Type: TypeAutoRenewable, PurchaseDate: now, ExpiresDate: now + 1000, SignedDate: now, Environment: EnvProduction,
		OfferType: 1, OfferDiscountType: "FREE_TRIAL"}
	got, err := v.VerifyTransaction(ts.Sign(tx))
	if err != nil || got.OriginalTransactionID != "1" || !got.IsFreeTrial() {
		t.Fatalf("%+v %v", got, err)
	}
	bad := tx
	bad.BundleID = "com.other"
	if _, err := v.VerifyTransaction(ts.Sign(bad)); !errors.Is(err, ErrWrongApp) {
		t.Errorf("bundle: %v", err)
	}
	bad = tx
	bad.Environment = EnvXcode
	if _, err := v.VerifyTransaction(ts.Sign(bad)); !errors.Is(err, ErrEnvironment) {
		t.Errorf("xcode: %v", err)
	}
	// alg none / HS256 / missing x5c are rejected.
	none := jwt.NewWithClaims(jwt.SigningMethodNone, jwt.MapClaims{"bundleId": "in.sarkarimatch.app"})
	s, _ := none.SignedString(jwt.UnsafeAllowNoneSignatureType)
	if _, err := v.VerifyTransaction(s); !errors.Is(err, ErrInvalid) {
		t.Errorf("alg none: %v", err)
	}
	k, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	plain, _ := jwt.NewWithClaims(jwt.SigningMethodES256, jwt.MapClaims{"bundleId": "in.sarkarimatch.app"}).SignedString(k)
	if _, err := v.VerifyTransaction(plain); !errors.Is(err, ErrInvalid) {
		t.Errorf("no x5c: %v", err)
	}
	// Local testing payloads are decoded without a chain only when allowed.
	lt := tx
	lt.Environment = EnvLocalTesting
	local := jwt.NewWithClaims(jwt.SigningMethodES256, jwt.MapClaims{"transactionId": "9", "originalTransactionId": "9",
		"productId": "p", "bundleId": "in.sarkarimatch.app", "environment": EnvLocalTesting})
	ls, _ := local.SignedString(k)
	if _, err := v.VerifyTransaction(ls); err == nil {
		t.Error("local testing accepted without AllowLocalTesting")
	}
	v.AllowLocalTesting = true
	if _, err := v.VerifyTransaction(ls); err != nil {
		t.Errorf("local testing: %v", err)
	}
}

func TestVerifyNotificationAppID(t *testing.T) {
	ts, _ := NewTestSigner()
	v := &Verifier{Roots: ts.Roots(), BundleID: "b", AppAppleID: 42, Environments: []string{EnvProduction, EnvSandbox}}
	n := Notification{NotificationType: "DID_RENEW", NotificationUUID: "u1", SignedDate: time.Now().UnixMilli(),
		Data: &NotificationData{BundleID: "b", AppAppleID: 42, Environment: EnvProduction, Status: StatusActive}}
	if got, err := v.VerifyNotification(ts.Sign(n)); err != nil || got.Data.Status != StatusActive {
		t.Fatalf("%+v %v", got, err)
	}
	n.Data.AppAppleID = 7
	if _, err := v.VerifyNotification(ts.Sign(n)); !errors.Is(err, ErrWrongApp) {
		t.Errorf("app id: %v", err)
	}
	n.Data.Environment = EnvSandbox // sandbox notifications carry no appAppleId check (Apple library behaviour)
	if _, err := v.VerifyNotification(ts.Sign(n)); err != nil {
		t.Errorf("sandbox: %v", err)
	}
}

func TestServerAPIClient(t *testing.T) {
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	var gotAuth, gotPath, gotQuery string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth, gotPath, gotQuery = r.Header.Get("Authorization"), r.URL.Path, r.URL.RawQuery
		switch {
		case strings.HasPrefix(r.URL.Path, "/inApps/v1/transactions/404"):
			w.WriteHeader(404)
		case strings.HasPrefix(r.URL.Path, "/inApps/v1/transactions/"):
			_, _ = w.Write([]byte(`{"signedTransactionInfo":"jws-1"}`))
		case strings.HasPrefix(r.URL.Path, "/inApps/v1/subscriptions/"):
			_, _ = w.Write([]byte(`{"data":[{"subscriptionGroupIdentifier":"g","lastTransactions":[{"originalTransactionId":"1","status":4,"signedTransactionInfo":"t","signedRenewalInfo":"r"}]}]}`))
		case strings.HasPrefix(r.URL.Path, "/inApps/v2/history/"):
			_, _ = w.Write([]byte(`{"revision":"rev2","hasMore":true,"signedTransactions":["a","b"]}`))
		default:
			w.WriteHeader(503)
		}
	}))
	defer srv.Close()
	c := &Client{KeyID: "KID1", IssuerID: "iss-uuid", BundleID: "in.sarkarimatch.app", Key: key, BaseURL: srv.URL, HTTP: srv.Client()}
	s, err := c.GetTransactionInfo(context.Background(), "100")
	if err != nil || s != "jws-1" || gotPath != "/inApps/v1/transactions/100" {
		t.Fatalf("%q %v %s", s, err, gotPath)
	}
	claims := jwt.MapClaims{}
	tok, err := jwt.ParseWithClaims(strings.TrimPrefix(gotAuth, "Bearer "), claims, func(*jwt.Token) (any, error) { return &key.PublicKey, nil },
		jwt.WithValidMethods([]string{"ES256"}), jwt.WithAudience("appstoreconnect-v1"), jwt.WithIssuer("iss-uuid"))
	if err != nil || tok.Header["kid"] != "KID1" || claims["bid"] != "in.sarkarimatch.app" {
		t.Fatalf("token %v %v %v", err, tok.Header, claims)
	}
	if exp, _ := claims.GetExpirationTime(); exp == nil || time.Until(exp.Time) > 6*time.Minute {
		t.Errorf("exp %v", exp)
	}
	lts, err := c.GetSubscriptionStatuses(context.Background(), "1")
	if err != nil || len(lts) != 1 || lts[0].Status != StatusGracePeriod {
		t.Fatalf("%+v %v", lts, err)
	}
	h, next, more, err := c.GetTransactionHistory(context.Background(), "1", "rev1")
	if err != nil || len(h) != 2 || next != "rev2" || !more || gotQuery != "revision=rev1" || gotPath != "/inApps/v2/history/1" {
		t.Fatalf("%v %s %v %v %s", h, next, more, err, gotQuery)
	}
	if _, err := c.GetTransactionInfo(context.Background(), "404"); !errors.Is(err, ErrNotFound) {
		t.Errorf("404: %v", err)
	}
	c.BaseURL = srv.URL + "/down"
	if _, err := c.GetSubscriptionStatuses(context.Background(), "x"); !errors.Is(err, ErrUnavailable) {
		t.Errorf("503: %v", err)
	}
}
