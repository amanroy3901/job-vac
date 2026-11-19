package appstore

// Transaction is JWSTransactionDecodedPayload (fields we read). Dates are epoch milliseconds.
// https://developer.apple.com/documentation/appstoreserverapi/jwstransactiondecodedpayload
type Transaction struct {
	TransactionID               string `json:"transactionId"`
	OriginalTransactionID       string `json:"originalTransactionId"`
	WebOrderLineItemID          string `json:"webOrderLineItemId,omitempty"`
	BundleID                    string `json:"bundleId"`
	ProductID                   string `json:"productId"`
	SubscriptionGroupIdentifier string `json:"subscriptionGroupIdentifier,omitempty"`
	PurchaseDate                int64  `json:"purchaseDate"`
	OriginalPurchaseDate        int64  `json:"originalPurchaseDate,omitempty"`
	ExpiresDate                 int64  `json:"expiresDate,omitempty"`
	Quantity                    int    `json:"quantity,omitempty"`
	Type                        string `json:"type"` // TypeAutoRenewable | TypeNonRenewing | Consumable | Non-Consumable
	AppAccountToken             string `json:"appAccountToken,omitempty"`
	InAppOwnershipType          string `json:"inAppOwnershipType,omitempty"` // PURCHASED | FAMILY_SHARED
	SignedDate                  int64  `json:"signedDate"`
	RevocationReason            *int   `json:"revocationReason,omitempty"`
	RevocationDate              int64  `json:"revocationDate,omitempty"`
	RevocationType              string `json:"revocationType,omitempty"` // REFUND_FULL | REFUND_PRORATED | FAMILY_REVOKE
	IsUpgraded                  bool   `json:"isUpgraded,omitempty"`
	OfferType                   int    `json:"offerType,omitempty"` // 1 introductory, 2 promotional, 3 offer code, 4 win-back
	OfferIdentifier             string `json:"offerIdentifier,omitempty"`
	OfferDiscountType           string `json:"offerDiscountType,omitempty"` // FREE_TRIAL | PAY_AS_YOU_GO | PAY_UP_FRONT | ONE_TIME
	Environment                 string `json:"environment"`
	TransactionReason           string `json:"transactionReason,omitempty"` // PURCHASE | RENEWAL
	Storefront                  string `json:"storefront,omitempty"`
	Price                       int64  `json:"price,omitempty"` // milli-units of Currency
	Currency                    string `json:"currency,omitempty"`
}

// Transaction types.
const (
	TypeAutoRenewable = "Auto-Renewable Subscription"
	TypeNonRenewing   = "Non-Renewing Subscription"
)

// IsFreeTrial reports an introductory free-trial period.
func (t *Transaction) IsFreeTrial() bool {
	return t.OfferType == 1 && t.OfferDiscountType == "FREE_TRIAL"
}

// RenewalInfo is JWSRenewalInfoDecodedPayload (fields we read).
// https://developer.apple.com/documentation/appstoreserverapi/jwsrenewalinfodecodedpayload
type RenewalInfo struct {
	OriginalTransactionID       string `json:"originalTransactionId"`
	AutoRenewProductID          string `json:"autoRenewProductId,omitempty"`
	ProductID                   string `json:"productId"`
	AutoRenewStatus             int    `json:"autoRenewStatus"` // 0 off, 1 on
	ExpirationIntent            int    `json:"expirationIntent,omitempty"`
	GracePeriodExpiresDate      int64  `json:"gracePeriodExpiresDate,omitempty"`
	IsInBillingRetryPeriod      *bool  `json:"isInBillingRetryPeriod,omitempty"`
	SignedDate                  int64  `json:"signedDate"`
	Environment                 string `json:"environment"`
	RenewalDate                 int64  `json:"renewalDate,omitempty"`
	RecentSubscriptionStartDate int64  `json:"recentSubscriptionStartDate,omitempty"`
}

// InBillingRetry reports isInBillingRetryPeriod.
func (r *RenewalInfo) InBillingRetry() bool {
	return r != nil && r.IsInBillingRetryPeriod != nil && *r.IsInBillingRetryPeriod
}

// Notification is responseBodyV2DecodedPayload.
// https://developer.apple.com/documentation/appstoreservernotifications/responsebodyv2decodedpayload
type Notification struct {
	NotificationType string            `json:"notificationType"`
	Subtype          string            `json:"subtype,omitempty"`
	NotificationUUID string            `json:"notificationUUID"`
	Version          string            `json:"version"`
	SignedDate       int64             `json:"signedDate"`
	Data             *NotificationData `json:"data,omitempty"`
	Summary          *Summary          `json:"summary,omitempty"`
}

// NotificationData is the notification's `data` object.
type NotificationData struct {
	AppAppleID            int64  `json:"appAppleId,omitempty"`
	BundleID              string `json:"bundleId"`
	BundleVersion         string `json:"bundleVersion,omitempty"`
	Environment           string `json:"environment"`
	SignedTransactionInfo string `json:"signedTransactionInfo,omitempty"`
	SignedRenewalInfo     string `json:"signedRenewalInfo,omitempty"`
	// Status of an auto-renewable subscription as of signedDate: 1 active, 2 expired, 3 billing retry,
	// 4 billing grace period, 5 revoked (0 = absent).
	Status int `json:"status,omitempty"`
}

// Summary is the `summary` object of RENEWAL_EXTENSION summary notifications.
type Summary struct {
	AppAppleID  int64  `json:"appAppleId,omitempty"`
	BundleID    string `json:"bundleId"`
	Environment string `json:"environment"`
}

// Subscription statuses (data.status / Server API lastTransactions[].status).
const (
	StatusActive       = 1
	StatusExpired      = 2
	StatusBillingRetry = 3
	StatusGracePeriod  = 4
	StatusRevoked      = 5
)
