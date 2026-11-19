package auth

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"encoding/pem"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/golang-jwt/jwt/v5"
)

func TestAppleIDClient(t *testing.T) {
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	der, _ := x509.MarshalPKCS8PrivateKey(key)
	c, err := NewAppleIDClient("TEAM123", "KEY456", "in.sarkarimatch.app", string(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})))
	if err != nil {
		t.Fatal(err)
	}
	var forms []url.Values
	var paths []string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = r.ParseForm()
		forms, paths = append(forms, r.PostForm), append(paths, r.URL.Path)
		switch {
		case r.URL.Path == "/auth/token" && r.PostForm.Get("code") == "good":
			_, _ = w.Write([]byte(`{"access_token":"a","refresh_token":"r-1","id_token":"x","token_type":"Bearer"}`))
		case r.URL.Path == "/auth/token":
			w.WriteHeader(400)
			_, _ = w.Write([]byte(`{"error":"invalid_grant"}`))
		case r.URL.Path == "/auth/revoke" && r.PostForm.Get("token") == "down":
			w.WriteHeader(503)
		default:
			w.WriteHeader(200)
		}
	}))
	defer srv.Close()
	c.Endpoint, c.HTTP = srv.URL, srv.Client()
	rt, err := c.Exchange(context.Background(), "good")
	if err != nil || rt != "r-1" {
		t.Fatalf("%q %v", rt, err)
	}
	f := forms[0]
	if f.Get("grant_type") != "authorization_code" || f.Get("client_id") != "in.sarkarimatch.app" {
		t.Fatalf("form %v", f)
	}
	claims := jwt.MapClaims{}
	tok, err := jwt.ParseWithClaims(f.Get("client_secret"), claims, func(*jwt.Token) (any, error) { return &key.PublicKey, nil },
		jwt.WithValidMethods([]string{"ES256"}), jwt.WithAudience("https://appleid.apple.com"), jwt.WithIssuer("TEAM123"),
		jwt.WithSubject("in.sarkarimatch.app"))
	if err != nil || tok.Header["kid"] != "KEY456" {
		t.Fatalf("client secret: %v %v", err, tok.Header)
	}
	if _, err := c.Exchange(context.Background(), "expired"); !errors.Is(err, ErrAppleGrant) {
		t.Errorf("bad code: %v", err)
	}
	if err := c.Revoke(context.Background(), "r-1"); err != nil {
		t.Fatal(err)
	}
	last := forms[len(forms)-1]
	if paths[len(paths)-1] != "/auth/revoke" || last.Get("token") != "r-1" || last.Get("token_type_hint") != "refresh_token" {
		t.Fatalf("revoke form %v", last)
	}
	if err := c.Revoke(context.Background(), "down"); !errors.Is(err, ErrAppleUnavailable) {
		t.Errorf("503: %v", err)
	}
}
