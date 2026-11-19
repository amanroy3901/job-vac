package auth

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"errors"
	"math/big"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func signRS(t *testing.T, key *rsa.PrivateKey, kid string, claims jwt.MapClaims) string {
	t.Helper()
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	tok.Header["kid"] = kid
	s, err := tok.SignedString(key)
	if err != nil {
		t.Fatal(err)
	}
	return s
}

func certServer(t *testing.T, key *rsa.PrivateKey, kid string) *httptest.Server {
	t.Helper()
	tmpl := &x509.Certificate{SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "test"},
		NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour)}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	pemCert := string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Cache-Control", "public, max-age=600")
		_ = json.NewEncoder(w).Encode(map[string]string{kid: pemCert})
	}))
}

func TestFirebaseVerifier(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	srv := certServer(t, key, "k1")
	defer srv.Close()
	v := &FirebaseVerifier{ProjectID: "proj", Keys: NewRemoteKeySet(srv.URL, srv.Client())}
	now := time.Now()
	base := func() jwt.MapClaims {
		return jwt.MapClaims{"iss": "https://securetoken.google.com/proj", "aud": "proj", "sub": "uid1",
			"iat": now.Unix(), "exp": now.Add(time.Hour).Unix(), "auth_time": now.Unix(),
			"email": "a@example.com", "firebase": map[string]any{"sign_in_provider": "google.com"}}
	}
	id, err := v.VerifyIDToken(context.Background(), signRS(t, key, "k1", base()))
	if err != nil {
		t.Fatalf("valid token rejected: %v", err)
	}
	if id.UID != "uid1" || id.Provider != "google.com" || id.IsAnonymous() {
		t.Fatalf("identity = %+v", id)
	}

	cases := map[string]func(jwt.MapClaims){
		"wrong aud":        func(c jwt.MapClaims) { c["aud"] = "other" },
		"wrong iss":        func(c jwt.MapClaims) { c["iss"] = "https://securetoken.google.com/other" },
		"no sub":           func(c jwt.MapClaims) { c["sub"] = "" },
		"future auth_time": func(c jwt.MapClaims) { c["auth_time"] = now.Add(time.Hour).Unix() },
	}
	for name, mut := range cases {
		c := base()
		mut(c)
		if _, err := v.VerifyIDToken(context.Background(), signRS(t, key, "k1", c)); err == nil {
			t.Errorf("%s: accepted", name)
		}
	}
	c := base()
	c["exp"] = now.Add(-time.Hour).Unix()
	if _, err := v.VerifyIDToken(context.Background(), signRS(t, key, "k1", c)); !errors.Is(err, ErrExpired) {
		t.Errorf("expired: got %v", err)
	}
	other, _ := rsa.GenerateKey(rand.Reader, 2048)
	if _, err := v.VerifyIDToken(context.Background(), signRS(t, other, "k1", base())); err == nil {
		t.Error("wrong key accepted")
	}
	if _, err := v.VerifyIDToken(context.Background(), signRS(t, key, "nope", base())); err == nil {
		t.Error("unknown kid accepted")
	}
}

func TestAppCheckAndOIDC(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	keys := StaticKeySet{"a": &key.PublicKey}
	now := time.Now()
	ac := &FirebaseAppCheck{ProjectNumber: "123", AppIDs: []string{"1:123:android:abc"}, Keys: keys}
	tok := signRS(t, key, "a", jwt.MapClaims{"iss": "https://firebaseappcheck.googleapis.com/123",
		"aud": []string{"projects/123", "projects/proj"}, "sub": "1:123:android:abc",
		"iat": now.Unix(), "exp": now.Add(time.Hour).Unix()})
	if app, err := ac.VerifyAppCheck(context.Background(), tok); err != nil || app != "1:123:android:abc" {
		t.Fatalf("app check: %v %v", app, err)
	}
	ac.AppIDs = []string{"other"}
	if _, err := ac.VerifyAppCheck(context.Background(), tok); err == nil {
		t.Fatal("foreign app id accepted")
	}

	ov := &OIDCVerifier{Audience: "https://api/hooks/play-rtdn", Email: "rtdn@x.iam.gserviceaccount.com", Keys: keys}
	good := jwt.MapClaims{"iss": "https://accounts.google.com", "aud": "https://api/hooks/play-rtdn",
		"email": "rtdn@x.iam.gserviceaccount.com", "email_verified": true, "iat": now.Unix(), "exp": now.Add(time.Hour).Unix()}
	if _, err := ov.Verify(context.Background(), signRS(t, key, "a", good)); err != nil {
		t.Fatalf("oidc: %v", err)
	}
	good["email"] = "evil@x.com"
	if _, err := ov.Verify(context.Background(), signRS(t, key, "a", good)); err == nil {
		t.Fatal("wrong caller accepted")
	}
}

func TestDevVerifier(t *testing.T) {
	id, err := DevVerifier{}.VerifyIDToken(context.Background(), "dev:u1:google:a@b.c")
	if err != nil || id.UID != "u1" || id.Provider != "google.com" || id.Email != "a@b.c" {
		t.Fatalf("%+v %v", id, err)
	}
	if _, err := (DevVerifier{}).VerifyIDToken(context.Background(), "garbage"); err == nil {
		t.Fatal("garbage accepted")
	}
}
