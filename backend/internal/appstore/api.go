package appstore

import (
	"context"
	"crypto/ecdsa"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// Server API base URLs (App Store Server Library 3.x constants).
const (
	ProductionURL = "https://api.storekit.apple.com"
	SandboxURL    = "https://api.storekit-sandbox.apple.com"
)

// Server API errors.
var (
	ErrNotFound    = errors.New("appstore api: not found")
	ErrAuth        = errors.New("appstore api: unauthorized (check key id / issuer id / key)")
	ErrUnavailable = errors.New("appstore api: unavailable") // 429 / 5xx / network → retry
)

// LastTransaction is one entry of Get All Subscription Statuses (data[].lastTransactions[]).
type LastTransaction struct {
	OriginalTransactionID string `json:"originalTransactionId"`
	Status                int    `json:"status"`
	SignedTransactionInfo string `json:"signedTransactionInfo"`
	SignedRenewalInfo     string `json:"signedRenewalInfo"`
}

// ServerAPI is the App Store Server API surface we use. Every returned string is a JWS that the caller still
// verifies with Verifier (the API response is trusted for transport only).
type ServerAPI interface {
	// GetTransactionInfo: GET /inApps/v1/transactions/{transactionId} → signedTransactionInfo.
	GetTransactionInfo(ctx context.Context, transactionID string) (string, error)
	// GetSubscriptionStatuses: GET /inApps/v1/subscriptions/{transactionId} → every group's last transaction.
	GetSubscriptionStatuses(ctx context.Context, transactionID string) ([]LastTransaction, error)
	// GetTransactionHistory: GET /inApps/v2/history/{transactionId}?revision= → signedTransactions page.
	GetTransactionHistory(ctx context.Context, transactionID, revision string) (signed []string, next string, hasMore bool, err error)
}

// Client calls the App Store Server API with an ES256 JWT signed by an In-App Purchase key from App Store
// Connect (Users and Access → Integrations → In-App Purchase). Token claims per Apple ("Generating JSON Web Tokens
// for API requests"): header alg ES256, kid = key id, typ JWT; payload iss = issuer id, iat, exp (≤ 60 min; we use
// 5 min like Apple's library), aud = "appstoreconnect-v1", bid = bundle id.
type Client struct {
	KeyID    string
	IssuerID string
	BundleID string
	Key      *ecdsa.PrivateKey
	BaseURL  string // ProductionURL or SandboxURL (or a test server)
	HTTP     *http.Client
	Now      func() time.Time
}

// NewClient parses a PKCS#8 PEM (.p8) key.
func NewClient(keyPEM, keyID, issuerID, bundleID, baseURL string) (*Client, error) {
	k, err := ParseP8(keyPEM)
	if err != nil {
		return nil, err
	}
	if keyID == "" || issuerID == "" || bundleID == "" {
		return nil, errors.New("appstore api: key id, issuer id and bundle id are required")
	}
	return &Client{KeyID: keyID, IssuerID: issuerID, BundleID: bundleID, Key: k, BaseURL: baseURL,
		HTTP: &http.Client{Timeout: 15 * time.Second}}, nil
}

// ParseP8 parses an Apple .p8 (PKCS#8 P-256) private key.
func ParseP8(keyPEM string) (*ecdsa.PrivateKey, error) {
	block, _ := pem.Decode([]byte(keyPEM))
	if block == nil {
		return nil, errors.New("p8 key: no PEM block")
	}
	k, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("p8 key: %w", err)
	}
	ek, ok := k.(*ecdsa.PrivateKey)
	if !ok {
		return nil, errors.New("p8 key: not an EC key")
	}
	return ek, nil
}

func (c *Client) now() time.Time {
	if c.Now != nil {
		return c.Now()
	}
	return time.Now()
}

// Token returns the bearer JWT.
func (c *Client) Token() (string, error) {
	now := c.now()
	t := jwt.NewWithClaims(jwt.SigningMethodES256, jwt.MapClaims{"iss": c.IssuerID, "iat": now.Unix(),
		"exp": now.Add(5 * time.Minute).Unix(), "aud": "appstoreconnect-v1", "bid": c.BundleID})
	t.Header["kid"] = c.KeyID
	return t.SignedString(c.Key)
}

func (c *Client) get(ctx context.Context, path string, q url.Values, out any) error {
	tok, err := c.Token()
	if err != nil {
		return err
	}
	u := strings.TrimRight(c.BaseURL, "/") + path
	if len(q) > 0 {
		u += "?" + q.Encode()
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+tok)
	req.Header.Set("User-Agent", "sarkari-match-backend")
	hc := c.HTTP
	if hc == nil {
		hc = http.DefaultClient
	}
	resp, err := hc.Do(req)
	if err != nil {
		return fmt.Errorf("%w: %v", ErrUnavailable, err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	switch {
	case resp.StatusCode == http.StatusOK:
		return json.Unmarshal(body, out)
	case resp.StatusCode == http.StatusNotFound:
		return fmt.Errorf("%w: %s", ErrNotFound, path)
	case resp.StatusCode == http.StatusUnauthorized:
		return ErrAuth
	case resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode >= 500:
		return fmt.Errorf("%w: HTTP %d", ErrUnavailable, resp.StatusCode)
	default:
		return fmt.Errorf("appstore api: HTTP %d: %.200s", resp.StatusCode, body)
	}
}

// GetTransactionInfo implements ServerAPI.
func (c *Client) GetTransactionInfo(ctx context.Context, id string) (string, error) {
	var out struct {
		SignedTransactionInfo string `json:"signedTransactionInfo"`
	}
	err := c.get(ctx, "/inApps/v1/transactions/"+url.PathEscape(id), nil, &out)
	return out.SignedTransactionInfo, err
}

// GetSubscriptionStatuses implements ServerAPI.
func (c *Client) GetSubscriptionStatuses(ctx context.Context, id string) ([]LastTransaction, error) {
	var out struct {
		Data []struct {
			LastTransactions []LastTransaction `json:"lastTransactions"`
		} `json:"data"`
	}
	if err := c.get(ctx, "/inApps/v1/subscriptions/"+url.PathEscape(id), nil, &out); err != nil {
		return nil, err
	}
	var all []LastTransaction
	for _, g := range out.Data {
		all = append(all, g.LastTransactions...)
	}
	return all, nil
}

// GetTransactionHistory implements ServerAPI.
func (c *Client) GetTransactionHistory(ctx context.Context, id, revision string) ([]string, string, bool, error) {
	var out struct {
		Revision           string   `json:"revision"`
		HasMore            bool     `json:"hasMore"`
		SignedTransactions []string `json:"signedTransactions"`
	}
	q := url.Values{}
	if revision != "" {
		q.Set("revision", revision)
	}
	err := c.get(ctx, "/inApps/v2/history/"+url.PathEscape(id), q, &out)
	return out.SignedTransactions, out.Revision, out.HasMore, err
}
