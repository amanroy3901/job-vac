package billing

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

// SubscriptionV2 is the subset of purchases.subscriptionsv2 we read.
type SubscriptionV2 struct {
	SubscriptionState          string       `json:"subscriptionState"`    // SUBSCRIPTION_STATE_ACTIVE, _IN_GRACE_PERIOD, _ON_HOLD, _PAUSED, _CANCELED, _EXPIRED, _PENDING, _PENDING_PURCHASE_CANCELED
	AcknowledgementState       string       `json:"acknowledgementState"` // ACKNOWLEDGEMENT_STATE_PENDING | _ACKNOWLEDGED
	LatestOrderID              string       `json:"latestOrderId"`
	LinkedPurchaseToken        string       `json:"linkedPurchaseToken"`
	StartTime                  string       `json:"startTime"`
	TestPurchase               *struct{}    `json:"testPurchase"`
	ExternalAccountIdentifiers *ExternalIDs `json:"externalAccountIdentifiers"`
	LineItems                  []LineItem   `json:"lineItems"`
}

// ExternalIDs carries the obfuscated account id set with BillingFlowParams.setObfuscatedAccountId.
type ExternalIDs struct {
	ObfuscatedExternalAccountID string `json:"obfuscatedExternalAccountId"`
}

// LineItem is one subscriptionsv2 line item.
type LineItem struct {
	ProductID        string            `json:"productId"`
	ExpiryTime       string            `json:"expiryTime"`
	AutoRenewingPlan *AutoRenewingPlan `json:"autoRenewingPlan"`
	PrepaidPlan      *struct{}         `json:"prepaidPlan"`
	OfferDetails     *OfferDetails     `json:"offerDetails"`
}

// AutoRenewingPlan is lineItems[].autoRenewingPlan.
type AutoRenewingPlan struct {
	AutoRenewEnabled bool `json:"autoRenewEnabled"`
}

// OfferDetails is lineItems[].offerDetails.
type OfferDetails struct {
	BasePlanID string `json:"basePlanId"`
}

// PlayClient is the Android Publisher API surface we use.
type PlayClient interface {
	GetSubscription(ctx context.Context, packageName, token string) (*SubscriptionV2, error)
	Acknowledge(ctx context.Context, packageName, subscriptionID, token string) error
}

// ErrPlayUnavailable means the Play Developer API failed transiently (→ 503 retryable).
var ErrPlayUnavailable = errors.New("play developer api unavailable")

// ErrPlayTokenInvalid means Play does not know the token (→ 402 purchase_invalid).
var ErrPlayTokenInvalid = errors.New("play purchase token invalid")

// GooglePlay calls androidpublisher v3 over REST with Application Default Credentials.
type GooglePlay struct {
	Client   *http.Client
	Endpoint string
}

// NewGooglePlay builds the client (scope androidpublisher).
func NewGooglePlay(ctx context.Context) (*GooglePlay, error) {
	ts, err := google.DefaultTokenSource(ctx, "https://www.googleapis.com/auth/androidpublisher")
	if err != nil {
		return nil, err
	}
	return &GooglePlay{Client: oauth2.NewClient(ctx, ts)}, nil
}

func (g *GooglePlay) base() string {
	if g.Endpoint != "" {
		return strings.TrimRight(g.Endpoint, "/")
	}
	return "https://androidpublisher.googleapis.com"
}

// GetSubscription implements PlayClient.
func (g *GooglePlay) GetSubscription(ctx context.Context, pkg, token string) (*SubscriptionV2, error) {
	u := fmt.Sprintf("%s/androidpublisher/v3/applications/%s/purchases/subscriptionsv2/tokens/%s", g.base(),
		url.PathEscape(pkg), url.PathEscape(token))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return nil, err
	}
	resp, err := g.Client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrPlayUnavailable, err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	switch {
	case resp.StatusCode == http.StatusOK:
		var s SubscriptionV2
		return &s, json.Unmarshal(body, &s)
	case resp.StatusCode == 400 || resp.StatusCode == 404 || resp.StatusCode == 410:
		return nil, fmt.Errorf("%w: HTTP %d", ErrPlayTokenInvalid, resp.StatusCode)
	default:
		return nil, fmt.Errorf("%w: HTTP %d", ErrPlayUnavailable, resp.StatusCode)
	}
}

// Acknowledge implements PlayClient.
func (g *GooglePlay) Acknowledge(ctx context.Context, pkg, subID, token string) error {
	u := fmt.Sprintf("%s/androidpublisher/v3/applications/%s/purchases/subscriptions/%s/tokens/%s:acknowledge", g.base(),
		url.PathEscape(pkg), url.PathEscape(subID), url.PathEscape(token))
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, u, strings.NewReader("{}"))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := g.Client.Do(req)
	if err != nil {
		return fmt.Errorf("%w: %v", ErrPlayUnavailable, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return fmt.Errorf("%w: acknowledge HTTP %d", ErrPlayUnavailable, resp.StatusCode)
	}
	return nil
}

// FakePlay is an in-memory PlayClient (PLAY_MODE=fake for local runs, and tests). Unknown tokens of the form
// "fake:<base_plan_id>" are created on first lookup as ACTIVE purchases of product "pro".
type FakePlay struct {
	mu    sync.Mutex
	Subs  map[string]*SubscriptionV2
	Acked map[string]bool
	Fail  error
}

// NewFakePlay returns an empty fake.
func NewFakePlay() *FakePlay {
	return &FakePlay{Subs: map[string]*SubscriptionV2{}, Acked: map[string]bool{}}
}

// Put registers a subscription for token.
func (f *FakePlay) Put(token string, s *SubscriptionV2) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.Subs[token] = s
}

// FakeSub builds a subscription response.
func FakeSub(state, basePlan, obfuscated string, expiry time.Time, autoRenew bool) *SubscriptionV2 {
	s := &SubscriptionV2{SubscriptionState: state, AcknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
		LatestOrderID: "GPA.0000-" + basePlan, StartTime: time.Now().UTC().Format(time.RFC3339)}
	if obfuscated != "" {
		s.ExternalAccountIdentifiers = &ExternalIDs{ObfuscatedExternalAccountID: obfuscated}
	}
	li := LineItem{ProductID: "pro", ExpiryTime: expiry.UTC().Format(time.RFC3339), OfferDetails: &OfferDetails{BasePlanID: basePlan}}
	if autoRenew {
		li.AutoRenewingPlan = &AutoRenewingPlan{AutoRenewEnabled: true}
	} else {
		li.PrepaidPlan = &struct{}{}
	}
	s.LineItems = append(s.LineItems, li)
	return s
}

// GetSubscription implements PlayClient.
func (f *FakePlay) GetSubscription(_ context.Context, _ string, token string) (*SubscriptionV2, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.Fail != nil {
		return nil, f.Fail
	}
	if s, ok := f.Subs[token]; ok {
		cp := *s
		if f.Acked[token] {
			cp.AcknowledgementState = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
		}
		return &cp, nil
	}
	if bp, ok := strings.CutPrefix(token, "fake:"); ok {
		days := 120
		if bp == "yearly-autorenew" {
			days = 365
		}
		f.Subs[token] = FakeSub("SUBSCRIPTION_STATE_ACTIVE", bp, "", time.Now().AddDate(0, 0, days), bp == "yearly-autorenew")
		cp := *f.Subs[token]
		return &cp, nil
	}
	return nil, ErrPlayTokenInvalid
}

// Acknowledge implements PlayClient.
func (f *FakePlay) Acknowledge(_ context.Context, _, _, token string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.Acked[token] = true
	return nil
}
