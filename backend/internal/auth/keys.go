// Package auth verifies the tokens the backend accepts:
//   - Firebase ID tokens (Google or anonymous provider) on account calls (RS256, x509 certs of
//     securetoken@system.gserviceaccount.com, aud = project id, iss = https://securetoken.google.com/<project>);
//   - Firebase App Check tokens (RS256, JWKS https://firebaseappcheck.googleapis.com/v1/jwks);
//   - Google-signed OIDC tokens for Pub/Sub push (Play RTDN) and Cloud Scheduler ticks;
//   - IAP assertions for the admin console (ES256, https://www.gstatic.com/iap/verify/public_key-jwk).
//
// Keys are fetched lazily, cached per the response's Cache-Control max-age and refreshed on an unknown kid.
package auth

import (
	"context"
	"crypto"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"regexp"
	"strconv"
	"sync"
	"time"
)

// Well-known key endpoints.
const (
	FirebaseCertsURL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
	AppCheckJWKSURL  = "https://firebaseappcheck.googleapis.com/v1/jwks"
	GoogleOIDCJWKS   = "https://www.googleapis.com/oauth2/v3/certs"
	IAPJWKSURL       = "https://www.gstatic.com/iap/verify/public_key-jwk"
)

// KeySet resolves a signing key by kid.
type KeySet interface {
	Key(ctx context.Context, kid string) (crypto.PublicKey, error)
}

// RemoteKeySet fetches either a JWKS document ({"keys":[...]}) or a Google x509 map ({kid: PEM}).
type RemoteKeySet struct {
	URL    string
	Client *http.Client
	// MinRefresh bounds refetches on unknown kids (default 1 min).
	MinRefresh time.Duration

	mu        sync.Mutex
	keys      map[string]crypto.PublicKey
	expires   time.Time
	lastFetch time.Time
}

// NewRemoteKeySet builds a cached key set for url.
func NewRemoteKeySet(url string, client *http.Client) *RemoteKeySet {
	if client == nil {
		client = &http.Client{Timeout: 10 * time.Second}
	}
	return &RemoteKeySet{URL: url, Client: client, MinRefresh: time.Minute}
}

// ErrUnknownKey means no key with the token's kid exists.
var ErrUnknownKey = errors.New("unknown signing key")

// Key returns the public key for kid, refreshing when expired or unknown.
func (r *RemoteKeySet) Key(ctx context.Context, kid string) (crypto.PublicKey, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	now := time.Now()
	if k, ok := r.keys[kid]; ok && now.Before(r.expires) {
		return k, nil
	}
	if r.keys == nil || now.After(r.expires) || now.Sub(r.lastFetch) > r.MinRefresh {
		if err := r.fetch(ctx); err != nil {
			if k, ok := r.keys[kid]; ok { // serve stale on fetch failure
				return k, nil
			}
			return nil, err
		}
	}
	if k, ok := r.keys[kid]; ok {
		return k, nil
	}
	return nil, ErrUnknownKey
}

var maxAgeRe = regexp.MustCompile(`max-age=(\d+)`)

func (r *RemoteKeySet) fetch(ctx context.Context) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, r.URL, nil)
	if err != nil {
		return err
	}
	resp, err := r.Client.Do(req)
	if err != nil {
		return fmt.Errorf("fetch keys: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("fetch keys: HTTP %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return err
	}
	keys, err := ParseKeys(body)
	if err != nil {
		return err
	}
	ttl := time.Hour
	if m := maxAgeRe.FindStringSubmatch(resp.Header.Get("Cache-Control")); m != nil {
		if s, err := strconv.Atoi(m[1]); err == nil {
			ttl = time.Duration(s) * time.Second
		}
	}
	r.keys, r.expires, r.lastFetch = keys, time.Now().Add(ttl), time.Now()
	return nil
}

type jwk struct {
	Kid string `json:"kid"`
	Kty string `json:"kty"`
	N   string `json:"n"`
	E   string `json:"e"`
	Crv string `json:"crv"`
	X   string `json:"x"`
	Y   string `json:"y"`
}

// ParseKeys parses a JWKS document or a {kid: PEM certificate} map.
func ParseKeys(body []byte) (map[string]crypto.PublicKey, error) {
	var set struct {
		Keys []jwk `json:"keys"`
	}
	if err := json.Unmarshal(body, &set); err == nil && set.Keys != nil {
		out := map[string]crypto.PublicKey{}
		for _, k := range set.Keys {
			pk, err := k.publicKey()
			if err != nil {
				continue // skip unsupported key types
			}
			out[k.Kid] = pk
		}
		return out, nil
	}
	var certs map[string]string
	if err := json.Unmarshal(body, &certs); err != nil {
		return nil, fmt.Errorf("parse keys: %w", err)
	}
	out := map[string]crypto.PublicKey{}
	for kid, p := range certs {
		block, _ := pem.Decode([]byte(p))
		if block == nil {
			continue
		}
		cert, err := x509.ParseCertificate(block.Bytes)
		if err != nil {
			continue
		}
		out[kid] = cert.PublicKey
	}
	return out, nil
}

func (k jwk) publicKey() (crypto.PublicKey, error) {
	dec := base64.RawURLEncoding.DecodeString
	switch k.Kty {
	case "RSA":
		n, err := dec(k.N)
		if err != nil {
			return nil, err
		}
		e, err := dec(k.E)
		if err != nil {
			return nil, err
		}
		return &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: int(new(big.Int).SetBytes(e).Int64())}, nil
	case "EC":
		if k.Crv != "P-256" {
			return nil, errors.New("unsupported curve")
		}
		x, err := dec(k.X)
		if err != nil {
			return nil, err
		}
		y, err := dec(k.Y)
		if err != nil {
			return nil, err
		}
		return &ecdsa.PublicKey{Curve: elliptic.P256(), X: new(big.Int).SetBytes(x), Y: new(big.Int).SetBytes(y)}, nil
	}
	return nil, errors.New("unsupported key type")
}

// StaticKeySet is a fixed kid → key map (tests, local emulators).
type StaticKeySet map[string]crypto.PublicKey

// Key implements KeySet.
func (s StaticKeySet) Key(_ context.Context, kid string) (crypto.PublicKey, error) {
	if k, ok := s[kid]; ok {
		return k, nil
	}
	return nil, ErrUnknownKey
}
