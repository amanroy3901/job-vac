package auth

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// Errors returned by verifiers. The HTTP layer maps ErrExpired → 401 token_expired, others → 401 unauthenticated.
var (
	ErrNoToken = errors.New("missing token")
	ErrExpired = errors.New("token expired")
	ErrInvalid = errors.New("invalid token")
)

// Identity is a verified Firebase user.
type Identity struct {
	UID      string
	Provider string // "anonymous" | "google.com" | ...
	Email    string // Google sign-in only; never stored in our DB (User.google_email is echoed from the token)
	AuthTime time.Time
}

// IsAnonymous reports a Firebase anonymous account.
func (i Identity) IsAnonymous() bool { return i.Provider == "anonymous" }

// IDTokenVerifier verifies Firebase ID tokens.
type IDTokenVerifier interface {
	VerifyIDToken(ctx context.Context, raw string) (*Identity, error)
}

// FirebaseVerifier validates RS256 Firebase ID tokens locally (security-privacy.md §1).
type FirebaseVerifier struct {
	ProjectID string
	Keys      KeySet
	Now       func() time.Time
	Leeway    time.Duration
}

type firebaseClaims struct {
	jwt.RegisteredClaims
	AuthTime int64  `json:"auth_time"`
	Email    string `json:"email"`
	Firebase struct {
		SignInProvider string `json:"sign_in_provider"`
	} `json:"firebase"`
}

// VerifyIDToken checks signature, alg, kid, iss, aud, exp, iat, sub and auth_time.
func (v *FirebaseVerifier) VerifyIDToken(ctx context.Context, raw string) (*Identity, error) {
	if raw == "" {
		return nil, ErrNoToken
	}
	var c firebaseClaims
	if err := parseRS(ctx, raw, &c, v.Keys, v.now, v.leeway(),
		"https://securetoken.google.com/"+v.ProjectID, v.ProjectID); err != nil {
		return nil, err
	}
	if c.Subject == "" || len(c.Subject) > 128 {
		return nil, fmt.Errorf("%w: bad sub", ErrInvalid)
	}
	if c.AuthTime == 0 || time.Unix(c.AuthTime, 0).After(v.now().Add(v.leeway())) {
		return nil, fmt.Errorf("%w: bad auth_time", ErrInvalid)
	}
	return &Identity{UID: c.Subject, Provider: c.Firebase.SignInProvider, Email: c.Email,
		AuthTime: time.Unix(c.AuthTime, 0)}, nil
}

func (v *FirebaseVerifier) now() time.Time {
	if v.Now != nil {
		return v.Now()
	}
	return time.Now()
}

func (v *FirebaseVerifier) leeway() time.Duration {
	if v.Leeway == 0 {
		return 30 * time.Second
	}
	return v.Leeway
}

// DevVerifier accepts "dev:<uid>" or "dev:<uid>:google[:email]" (local development and tests only; config refuses
// it in prod).
type DevVerifier struct{}

// VerifyIDToken implements IDTokenVerifier.
func (DevVerifier) VerifyIDToken(_ context.Context, raw string) (*Identity, error) {
	if raw == "" {
		return nil, ErrNoToken
	}
	parts := strings.Split(raw, ":")
	if len(parts) < 2 || parts[0] != "dev" || parts[1] == "" {
		return nil, ErrInvalid
	}
	id := &Identity{UID: parts[1], Provider: "anonymous", AuthTime: time.Now()}
	if len(parts) >= 3 && parts[2] == "google" {
		id.Provider = "google.com"
		if len(parts) >= 4 {
			id.Email = parts[3]
		}
	}
	return id, nil
}

// AppCheckVerifier verifies X-Firebase-AppCheck tokens.
type AppCheckVerifier interface {
	VerifyAppCheck(ctx context.Context, raw string) (appID string, err error)
}

// FirebaseAppCheck validates App Check JWTs: RS256, iss https://firebaseappcheck.googleapis.com/<number>,
// aud contains projects/<number>, sub = an allowed app id.
//
// TODO(limited-use): replay protection for limited-use tokens needs the App Check `verifyAppCheckToken` REST call
// with consume=true; not wired yet (security-privacy.md §1).
type FirebaseAppCheck struct {
	ProjectNumber string
	AppIDs        []string
	Keys          KeySet
	Now           func() time.Time
}

// VerifyAppCheck implements AppCheckVerifier.
func (v *FirebaseAppCheck) VerifyAppCheck(ctx context.Context, raw string) (string, error) {
	if raw == "" {
		return "", ErrNoToken
	}
	now := v.Now
	if now == nil {
		now = time.Now
	}
	var c jwt.RegisteredClaims
	if err := parseRS(ctx, raw, &c, v.Keys, now, 30*time.Second,
		"https://firebaseappcheck.googleapis.com/"+v.ProjectNumber, "projects/"+v.ProjectNumber); err != nil {
		return "", err
	}
	if len(v.AppIDs) > 0 && !slices.Contains(v.AppIDs, c.Subject) {
		return "", fmt.Errorf("%w: app id not allowed", ErrInvalid)
	}
	return c.Subject, nil
}

// AllowAppCheck accepts everything (APP_CHECK_MODE=off and tests).
type AllowAppCheck struct{}

// VerifyAppCheck implements AppCheckVerifier.
func (AllowAppCheck) VerifyAppCheck(context.Context, string) (string, error) { return "dev", nil }

// OIDCVerifier validates Google-signed OIDC ID tokens (Pub/Sub push, Cloud Scheduler) for one audience and
// service-account email.
type OIDCVerifier struct {
	Audience string
	Email    string
	Keys     KeySet
	Now      func() time.Time
}

type oidcClaims struct {
	jwt.RegisteredClaims
	Email         string `json:"email"`
	EmailVerified bool   `json:"email_verified"`
}

// Verify checks the token and returns the caller email.
func (v *OIDCVerifier) Verify(ctx context.Context, raw string) (string, error) {
	if raw == "" {
		return "", ErrNoToken
	}
	now := v.Now
	if now == nil {
		now = time.Now
	}
	var c oidcClaims
	err := parseRS(ctx, raw, &c, v.Keys, now, 30*time.Second, "", v.Audience)
	if err != nil {
		return "", err
	}
	if iss, _ := c.GetIssuer(); iss != "https://accounts.google.com" && iss != "accounts.google.com" {
		return "", fmt.Errorf("%w: issuer", ErrInvalid)
	}
	if v.Email != "" && (c.Email != v.Email || !c.EmailVerified) {
		return "", fmt.Errorf("%w: caller %q not allowed", ErrInvalid, c.Email)
	}
	return c.Email, nil
}

// IAPVerifier validates the x-goog-iap-jwt-assertion header (ES256).
type IAPVerifier struct {
	Audience string
	Keys     KeySet
}

// Verify returns the authenticated Google account email.
func (v *IAPVerifier) Verify(ctx context.Context, raw string) (string, error) {
	if raw == "" {
		return "", ErrNoToken
	}
	var c oidcClaims
	tok, err := jwt.ParseWithClaims(raw, &c, func(t *jwt.Token) (any, error) {
		kid, _ := t.Header["kid"].(string)
		return v.Keys.Key(ctx, kid)
	}, jwt.WithValidMethods([]string{"ES256"}), jwt.WithIssuer("https://cloud.google.com/iap"),
		jwt.WithAudience(v.Audience), jwt.WithExpirationRequired(), jwt.WithLeeway(30*time.Second))
	if err != nil || !tok.Valid {
		return "", mapJWTErr(err)
	}
	if c.Email == "" {
		return "", fmt.Errorf("%w: no email", ErrInvalid)
	}
	return strings.TrimPrefix(c.Email, "accounts.google.com:"), nil
}

func parseRS(ctx context.Context, raw string, claims jwt.Claims, keys KeySet, now func() time.Time,
	leeway time.Duration, issuer, audience string) error {
	opts := []jwt.ParserOption{jwt.WithValidMethods([]string{"RS256"}), jwt.WithExpirationRequired(),
		jwt.WithIssuedAt(), jwt.WithLeeway(leeway), jwt.WithTimeFunc(now)}
	if issuer != "" {
		opts = append(opts, jwt.WithIssuer(issuer))
	}
	if audience != "" {
		opts = append(opts, jwt.WithAudience(audience))
	}
	tok, err := jwt.ParseWithClaims(raw, claims, func(t *jwt.Token) (any, error) {
		kid, _ := t.Header["kid"].(string)
		if kid == "" {
			return nil, fmt.Errorf("%w: no kid", ErrInvalid)
		}
		return keys.Key(ctx, kid)
	}, opts...)
	if err != nil || !tok.Valid {
		return mapJWTErr(err)
	}
	return nil
}

func mapJWTErr(err error) error {
	if errors.Is(err, jwt.ErrTokenExpired) {
		return ErrExpired
	}
	if err == nil {
		return ErrInvalid
	}
	return fmt.Errorf("%w: %v", ErrInvalid, err)
}
