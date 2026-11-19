package auth

import (
	"context"
	"crypto/ecdsa"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// AppleTokens is the Sign in with Apple REST surface used for account deletion. App Store Review Guideline
// 5.1.1(v) requires apps that offer account creation to offer account deletion, and Apple's account-deletion
// guidance requires apps using Sign in with Apple to revoke the user's tokens with the REST API when the account
// is deleted. The app sends the Sign in with Apple `authorizationCode` once (POST /v1/session) or at deletion
// (DELETE /v1/me); the server exchanges it for a refresh token (stored sealed) and revokes that on deletion.
//
// Sources: https://developer.apple.com/documentation/sign_in_with_apple/revoke_tokens ,
// https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens ,
// https://developer.apple.com/support/offering-account-deletion-in-your-app/ ,
// https://developer.apple.com/app-store/review/guidelines/#5.1.1
type AppleTokens interface {
	// Exchange trades a one-time authorization code (valid 5 minutes) for a refresh token.
	Exchange(ctx context.Context, authorizationCode string) (refreshToken string, err error)
	// Revoke invalidates a refresh token (the app disappears from Settings → Apple ID → Sign in with Apple).
	Revoke(ctx context.Context, refreshToken string) error
}

// Apple token endpoint errors.
var (
	ErrAppleGrant       = errors.New("sign in with apple: invalid or expired authorization code")
	ErrAppleUnavailable = errors.New("sign in with apple: endpoint unavailable")
)

// AppleIDClient calls https://appleid.apple.com/auth/{token,revoke}. The client_secret is an ES256 JWT signed with
// the Sign in with Apple key (.p8): header kid = key id; claims iss = team id, iat, exp (≤ 6 months; we mint a
// 5-minute secret per call), aud = "https://appleid.apple.com", sub = client id. For a native iOS sign-in the
// client id is the app's bundle id (the App ID the authorization code was issued to).
type AppleIDClient struct {
	TeamID   string
	KeyID    string
	ClientID string
	Key      *ecdsa.PrivateKey
	Endpoint string // default https://appleid.apple.com
	HTTP     *http.Client
	Now      func() time.Time
}

// NewAppleIDClient parses the .p8 PEM key.
func NewAppleIDClient(teamID, keyID, clientID, keyPEM string) (*AppleIDClient, error) {
	block, _ := pem.Decode([]byte(keyPEM))
	if block == nil {
		return nil, errors.New("APPLE_SIWA_KEY_PEM: no PEM block")
	}
	k, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("APPLE_SIWA_KEY_PEM: %w", err)
	}
	ek, ok := k.(*ecdsa.PrivateKey)
	if !ok {
		return nil, errors.New("APPLE_SIWA_KEY_PEM: not an EC key")
	}
	if teamID == "" || keyID == "" || clientID == "" {
		return nil, errors.New("APPLE_TEAM_ID, APPLE_SIWA_KEY_ID and the client id are required")
	}
	return &AppleIDClient{TeamID: teamID, KeyID: keyID, ClientID: clientID, Key: ek, HTTP: &http.Client{Timeout: 15 * time.Second}}, nil
}

// ClientSecret mints the client_secret JWT.
func (c *AppleIDClient) ClientSecret() (string, error) {
	now := time.Now()
	if c.Now != nil {
		now = c.Now()
	}
	t := jwt.NewWithClaims(jwt.SigningMethodES256, jwt.MapClaims{"iss": c.TeamID, "iat": now.Unix(),
		"exp": now.Add(5 * time.Minute).Unix(), "aud": "https://appleid.apple.com", "sub": c.ClientID})
	t.Header["kid"] = c.KeyID
	return t.SignedString(c.Key)
}

func (c *AppleIDClient) post(ctx context.Context, path string, form url.Values) (int, []byte, error) {
	secret, err := c.ClientSecret()
	if err != nil {
		return 0, nil, err
	}
	form.Set("client_id", c.ClientID)
	form.Set("client_secret", secret)
	ep := c.Endpoint
	if ep == "" {
		ep = "https://appleid.apple.com"
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(ep, "/")+path, strings.NewReader(form.Encode()))
	if err != nil {
		return 0, nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	hc := c.HTTP
	if hc == nil {
		hc = http.DefaultClient
	}
	resp, err := hc.Do(req)
	if err != nil {
		return 0, nil, fmt.Errorf("%w: %v", ErrAppleUnavailable, err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	return resp.StatusCode, body, nil
}

// Exchange implements AppleTokens (grant_type=authorization_code).
func (c *AppleIDClient) Exchange(ctx context.Context, code string) (string, error) {
	status, body, err := c.post(ctx, "/auth/token", url.Values{"grant_type": {"authorization_code"}, "code": {code}})
	if err != nil {
		return "", err
	}
	if status >= 500 || status == 429 {
		return "", fmt.Errorf("%w: HTTP %d", ErrAppleUnavailable, status)
	}
	var out struct {
		RefreshToken string `json:"refresh_token"`
		Error        string `json:"error"`
	}
	_ = json.Unmarshal(body, &out)
	if status != http.StatusOK || out.RefreshToken == "" {
		return "", fmt.Errorf("%w (HTTP %d %s)", ErrAppleGrant, status, out.Error)
	}
	return out.RefreshToken, nil
}

// Revoke implements AppleTokens (token_type_hint=refresh_token). Note Apple answers 200 even for some bad
// client ids, so a configuration error shows up only as the app staying listed in the user's Apple ID settings;
// ops verifies revocation once per release with a test Apple ID (operations notes in backend/README.md).
func (c *AppleIDClient) Revoke(ctx context.Context, refreshToken string) error {
	status, body, err := c.post(ctx, "/auth/revoke", url.Values{"token": {refreshToken}, "token_type_hint": {"refresh_token"}})
	if err != nil {
		return err
	}
	if status != http.StatusOK {
		return fmt.Errorf("%w: revoke HTTP %d %.200s", ErrAppleUnavailable, status, body)
	}
	return nil
}

// LogAppleTokens is the dev implementation (APPLE_SIGNIN_MODE=log): codes become fake refresh tokens, revocations
// are logged.
type LogAppleTokens struct{}

// Exchange implements AppleTokens.
func (LogAppleTokens) Exchange(_ context.Context, code string) (string, error) {
	return "dev-refresh:" + code, nil
}

// Revoke implements AppleTokens.
func (LogAppleTokens) Revoke(ctx context.Context, _ string) error {
	slog.InfoContext(ctx, "sign in with apple token revoked (log only)")
	return nil
}

// FakeAppleTokens records calls (tests).
type FakeAppleTokens struct {
	mu      sync.Mutex
	Revoked []string
	Fail    error
}

// Exchange implements AppleTokens.
func (f *FakeAppleTokens) Exchange(_ context.Context, code string) (string, error) {
	if code == "bad" {
		return "", ErrAppleGrant
	}
	return "refresh:" + code, nil
}

// Revoke implements AppleTokens.
func (f *FakeAppleTokens) Revoke(_ context.Context, tok string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.Fail != nil {
		return f.Fail
	}
	f.Revoked = append(f.Revoked, tok)
	return nil
}

// RevokedTokens returns a copy of the revoked tokens.
func (f *FakeAppleTokens) RevokedTokens() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]string(nil), f.Revoked...)
}
