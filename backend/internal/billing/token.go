package billing

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// Signer issues Entitlement.token (ES256 JWT; exp ≤ min(expires_at, now + 7 d)) verified offline by the app with
// AppConfig.entitlement_token_jwks.
type Signer struct {
	key *ecdsa.PrivateKey
	kid string
}

// NewSigner loads a PKCS8/SEC1 PEM key, or generates an ephemeral one when pemKey is empty (non-prod only).
func NewSigner(pemKey, kid string) (*Signer, error) {
	if pemKey == "" {
		k, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		if err != nil {
			return nil, err
		}
		slog.Warn("ENTITLEMENT_KEY_PEM not set: using an ephemeral entitlement signing key (dev only)")
		return &Signer{key: k, kid: kid}, nil
	}
	block, _ := pem.Decode([]byte(pemKey))
	if block == nil {
		return nil, errors.New("ENTITLEMENT_KEY_PEM: no PEM block")
	}
	if k, err := x509.ParsePKCS8PrivateKey(block.Bytes); err == nil {
		ek, ok := k.(*ecdsa.PrivateKey)
		if !ok || ek.Curve != elliptic.P256() {
			return nil, errors.New("ENTITLEMENT_KEY_PEM must be a P-256 key")
		}
		return &Signer{key: ek, kid: kid}, nil
	}
	ek, err := x509.ParseECPrivateKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("ENTITLEMENT_KEY_PEM: %w", err)
	}
	return &Signer{key: ek, kid: kid}, nil
}

// Sign returns the token for an entitlement of userID.
func (s *Signer) Sign(userID string, st State, adsEnabled bool, now time.Time) (string, error) {
	exp := now.Add(7 * 24 * time.Hour)
	if st.Plan == "pro" && st.ExpiresAt != nil && st.ExpiresAt.Before(exp) {
		exp = *st.ExpiresAt
	}
	claims := jwt.MapClaims{
		"iss": "sarkarimatch", "sub": userID, "iat": now.Unix(), "exp": exp.Unix(),
		"plan": st.Plan, "status": st.Status, "ads_enabled": adsEnabled, "features": Features(st.Plan == "pro"),
	}
	tok := jwt.NewWithClaims(jwt.SigningMethodES256, claims)
	tok.Header["kid"] = s.kid
	return tok.SignedString(s.key)
}

// JWKS is the public key set for AppConfig.entitlement_token_jwks.
func (s *Signer) JWKS() map[string]any {
	pub := s.key.PublicKey
	enc := func(b []byte) string {
		p := make([]byte, 32)
		copy(p[32-len(b):], b)
		return base64.RawURLEncoding.EncodeToString(p)
	}
	return map[string]any{"keys": []map[string]any{{"kty": "EC", "crv": "P-256", "alg": "ES256", "use": "sig",
		"kid": s.kid, "x": enc(pub.X.Bytes()), "y": enc(pub.Y.Bytes())}}}
}

// PublicKey exposes the verification key (tests).
func (s *Signer) PublicKey() *ecdsa.PublicKey { return &s.key.PublicKey }
