// Package problem implements RFC 9457 application/problem+json errors with the stable machine codes of
// docs/api/openapi.yaml (components.schemas.Problem.code). Domain packages return *Error; the HTTP layer writes it.
package problem

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
)

// Stable codes (openapi Problem.code). Clients branch on these, never on title.
const (
	Unauthenticated              = "unauthenticated"
	TokenExpired                 = "token_expired"
	AppCheckFailed               = "app_check_failed"
	UpgradeRequired              = "upgrade_required"
	Forbidden                    = "forbidden"
	ProRequired                  = "pro_required"
	GoogleLinkRequired           = "google_link_required"
	NotFound                     = "not_found"
	ValidationFailed             = "validation_failed"
	IdempotencyKeyReused         = "idempotency_key_reused"
	RateLimited                  = "rate_limited"
	BackupConflict               = "backup_conflict"
	TrialAlreadyUsed             = "trial_already_used"
	PurchaseInvalid              = "purchase_invalid"
	PurchasePending              = "purchase_pending"
	PurchaseOwnedByOtherAccount  = "purchase_owned_by_other_account"
	FamilyLimitReached           = "family_limit_reached"
	InviteExpired                = "invite_expired"
	AccountPendingDeletion       = "account_pending_deletion"
	Maintenance                  = "maintenance"
	Internal                     = "internal"
	NotImplemented               = "not_implemented"
	Conflict                     = "conflict"
	MethodNotAllowed             = "method_not_allowed"
)

// FieldError is one entry of Problem.errors.
type FieldError struct {
	Field string `json:"field"`
	Code  string `json:"code"`
}

// Error is a problem response. It implements error.
type Error struct {
	Status     int            `json:"status"`
	Code       string         `json:"code"`
	Title      string         `json:"title"`
	Detail     string         `json:"detail,omitempty"`
	Retryable  bool           `json:"retryable"`
	Errors     []FieldError   `json:"errors,omitempty"`
	Details    map[string]any `json:"details,omitempty"`
	RetryAfter int            `json:"-"`
}

func (e *Error) Error() string {
	if e.Detail != "" {
		return e.Code + ": " + e.Detail
	}
	return e.Code
}

// New builds a problem with the default title for code.
func New(status int, code, detail string) *Error {
	return &Error{Status: status, Code: code, Title: titleFor(code), Detail: detail,
		Retryable: status == 429 || status == 502 || status == 503 || status == 504}
}

// Validation builds a 422 validation_failed with field errors.
func Validation(detail string, fields ...FieldError) *Error {
	e := New(http.StatusUnprocessableEntity, ValidationFailed, detail)
	e.Errors = fields
	return e
}

// As extracts a *Error from err (nil when err is not a problem).
func As(err error) *Error {
	var p *Error
	if errors.As(err, &p) {
		return p
	}
	return nil
}

func titleFor(code string) string {
	if m, ok := messages[code]; ok {
		return m["en"]
	}
	return strings.ReplaceAll(code, "_", " ")
}

// messages are the user-facing texts per code (Problem.message), en mandatory, hi for launch.
var messages = map[string]map[string]string{
	Unauthenticated:             {"en": "Please sign in again", "hi": "कृपया फिर से साइन इन करें"},
	TokenExpired:                {"en": "Session expired, retrying", "hi": "सत्र समाप्त, दोबारा कोशिश"},
	AppCheckFailed:              {"en": "This app copy could not be verified", "hi": "ऐप की पुष्टि नहीं हो सकी"},
	UpgradeRequired:             {"en": "Please update the app", "hi": "कृपया ऐप अपडेट करें"},
	Forbidden:                   {"en": "Not allowed", "hi": "अनुमति नहीं है"},
	ProRequired:                 {"en": "This is a Pro feature", "hi": "यह Pro सुविधा है"},
	GoogleLinkRequired:          {"en": "Sign in with Google to use this", "hi": "इसके लिए Google से साइन इन करें"},
	NotFound:                    {"en": "Not found", "hi": "नहीं मिला"},
	ValidationFailed:            {"en": "Some details are not valid", "hi": "कुछ जानकारी सही नहीं है"},
	IdempotencyKeyReused:        {"en": "Request conflict, please retry", "hi": "अनुरोध टकराव, फिर कोशिश करें"},
	RateLimited:                 {"en": "Too many requests, try again later", "hi": "बहुत अनुरोध, थोड़ी देर बाद कोशिश करें"},
	BackupConflict:              {"en": "Backup changed on another phone", "hi": "बैकअप दूसरे फ़ोन पर बदला"},
	TrialAlreadyUsed:            {"en": "Trial already used", "hi": "ट्रायल पहले ही इस्तेमाल हो चुका"},
	PurchaseInvalid:             {"en": "Purchase could not be verified", "hi": "ख़रीद की पुष्टि नहीं हुई"},
	PurchasePending:             {"en": "Payment is pending in Google Play", "hi": "Google Play में भुगतान बाकी है"},
	PurchaseOwnedByOtherAccount: {"en": "This purchase belongs to another account. Sign in with the Google account you used then", "hi": "यह ख़रीद दूसरे अकाउंट की है। उसी Google अकाउंट से साइन इन करें"},
	FamilyLimitReached:          {"en": "You can link at most 2 family members", "hi": "अधिकतम 2 परिवार सदस्य जोड़ सकते हैं"},
	InviteExpired:               {"en": "This invite has expired", "hi": "यह निमंत्रण समाप्त हो गया"},
	AccountPendingDeletion:      {"en": "This account is being deleted", "hi": "यह अकाउंट हटाया जा रहा है"},
	Maintenance:                 {"en": "Under maintenance", "hi": "रखरखाव चल रहा है"},
	Internal:                    {"en": "Something went wrong", "hi": "कुछ गड़बड़ हुई"},
	NotImplemented:              {"en": "Not available yet", "hi": "अभी उपलब्ध नहीं"},
	Conflict:                    {"en": "Conflict, please retry", "hi": "टकराव, फिर कोशिश करें"},
	MethodNotAllowed:            {"en": "Method not allowed", "hi": "अनुमति नहीं"},
}

// Message returns the LocalizedText for code in lang (+ en).
func Message(code, lang string) map[string]string {
	m, ok := messages[code]
	if !ok {
		m = messages[Internal]
	}
	out := map[string]string{"en": m["en"]}
	if v, ok := m[lang]; ok {
		out[lang] = v
	} else if v, ok := m["hi"]; ok && lang != "en" {
		out["hi"] = v // fallback chain ends at hi for unknown launch-pending locales
	}
	return out
}

// Write serialises p as application/problem+json.
func Write(w http.ResponseWriter, p *Error, lang, requestID, apiBase string) {
	body := map[string]any{
		"type":       apiBase + "/problems/" + p.Code,
		"title":      p.Title,
		"status":     p.Status,
		"code":       p.Code,
		"message":    Message(p.Code, lang),
		"retryable":  p.Retryable,
		"request_id": requestID,
	}
	if p.Detail != "" {
		body["detail"] = p.Detail
	}
	if len(p.Errors) > 0 {
		errs := make([]map[string]any, 0, len(p.Errors))
		for _, fe := range p.Errors {
			errs = append(errs, map[string]any{"field": fe.Field, "code": fe.Code})
		}
		body["errors"] = errs
	}
	if len(p.Details) > 0 {
		body["details"] = p.Details
	}
	w.Header().Set("Content-Type", "application/problem+json")
	w.Header().Set("Cache-Control", "no-store")
	if p.RetryAfter > 0 {
		w.Header().Set("Retry-After", strconv.Itoa(p.RetryAfter))
	}
	w.WriteHeader(p.Status)
	_ = json.NewEncoder(w).Encode(body)
}
