// Package config loads process configuration from environment variables (12-factor; Secret Manager values are
// injected as env vars by Cloud Run).
package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config is the whole process configuration. Every field maps to one env var (see .env.example).
type Config struct {
	Env      string // local | staging | prod
	Role     string // api | worker | admin | all
	Addr     string // listen address, e.g. :8080 (PORT on Cloud Run)
	LogLevel string
	LogJSON  bool

	DatabaseURL string
	DBMaxConns  int32

	// Firebase / Google
	FirebaseProjectID     string // aud/iss of Firebase ID tokens
	FirebaseProjectNumber string // App Check aud/iss
	AuthMode              string // firebase | dev (dev accepts "Bearer dev:<uid>[:google]"; refused in prod)
	AppCheckMode          string // enforce | monitor | off
	AppCheckAppIDs        []string

	// Admin console
	AdminAuthMode string // iap | dev
	IAPAudience   string

	// Pub/Sub push (RTDN) and Cloud Scheduler OIDC
	PushAuthMode       string // oidc | off (local only)
	RTDNAudience       string
	RTDNServiceAccount string
	TickAudience       string
	TickServiceAccount string

	// Play
	PlayPackageName string
	PlayMode        string // google | fake

	// FCM
	FCMMode string // fcm | log

	// Catalog blob store
	BlobMode      string // local | gcs
	BlobDir       string
	GCSBucket     string
	PublicBaseURL string // https://sarkarimatch.in (invite/share links)
	APIBaseURL    string // https://api.sarkarimatch.in (problem type URIs)

	// Keys
	EntitlementKeyPEM string // ES256 private key (PKCS8 PEM); generated per process when empty (non-prod only)
	EntitlementKeyID  string
	SealKeyB64        string // 32-byte AES key for purchase/self-test tokens (KMS envelope later)

	// Worker
	TickInterval time.Duration // ROLE=all/worker local loop; Cloud Scheduler drives prod
	ShutdownWait time.Duration
}

// Load reads the environment. It fails fast on settings that would be unsafe in prod.
func Load() (*Config, error) {
	c := &Config{
		Env:                   get("APP_ENV", "local"),
		Role:                  get("ROLE", "all"),
		Addr:                  get("ADDR", ":"+get("PORT", "8080")),
		LogLevel:              get("LOG_LEVEL", "info"),
		LogJSON:               getBool("LOG_JSON", true),
		DatabaseURL:           os.Getenv("DATABASE_URL"),
		DBMaxConns:            int32(getInt("DB_MAX_CONNS", 10)),
		FirebaseProjectID:     get("FIREBASE_PROJECT_ID", "sarkarimatch-local"),
		FirebaseProjectNumber: get("FIREBASE_PROJECT_NUMBER", "0"),
		AuthMode:              get("AUTH_MODE", "firebase"),
		AppCheckMode:          get("APP_CHECK_MODE", "monitor"),
		AppCheckAppIDs:        getList("APP_CHECK_APP_IDS"),
		AdminAuthMode:         get("ADMIN_AUTH_MODE", "iap"),
		IAPAudience:           os.Getenv("IAP_AUDIENCE"),
		PushAuthMode:          get("PUSH_AUTH_MODE", "oidc"),
		RTDNAudience:          os.Getenv("RTDN_AUDIENCE"),
		RTDNServiceAccount:    os.Getenv("RTDN_SERVICE_ACCOUNT"),
		TickAudience:          os.Getenv("TICK_AUDIENCE"),
		TickServiceAccount:    os.Getenv("TICK_SERVICE_ACCOUNT"),
		PlayPackageName:       get("PLAY_PACKAGE_NAME", "in.sarkarimatch.app"),
		PlayMode:              get("PLAY_MODE", "google"),
		FCMMode:               get("FCM_MODE", "fcm"),
		BlobMode:              get("BLOB_MODE", "local"),
		BlobDir:               get("BLOB_DIR", "./var/blob"),
		GCSBucket:             os.Getenv("GCS_CATALOG_BUCKET"),
		PublicBaseURL:         strings.TrimRight(get("PUBLIC_BASE_URL", "https://sarkarimatch.in"), "/"),
		APIBaseURL:            strings.TrimRight(get("API_BASE_URL", "https://api.sarkarimatch.in"), "/"),
		EntitlementKeyPEM:     os.Getenv("ENTITLEMENT_KEY_PEM"),
		EntitlementKeyID:      get("ENTITLEMENT_KEY_ID", "ent-1"),
		SealKeyB64:            os.Getenv("SEAL_KEY_B64"),
		TickInterval:          getDur("TICK_INTERVAL", 15*time.Second),
		ShutdownWait:          getDur("SHUTDOWN_WAIT", 20*time.Second),
	}
	return c, c.validate()
}

// IsProd reports whether this is the production environment.
func (c *Config) IsProd() bool { return c.Env == "prod" }

func (c *Config) validate() error {
	var errs []error
	if c.DatabaseURL == "" {
		errs = append(errs, errors.New("DATABASE_URL is required"))
	}
	switch c.Role {
	case "api", "worker", "admin", "all":
	default:
		errs = append(errs, fmt.Errorf("ROLE must be api|worker|admin|all, got %q", c.Role))
	}
	if c.IsProd() {
		if c.AuthMode != "firebase" {
			errs = append(errs, errors.New("AUTH_MODE must be firebase in prod"))
		}
		if c.AdminAuthMode != "iap" {
			errs = append(errs, errors.New("ADMIN_AUTH_MODE must be iap in prod"))
		}
		if c.PushAuthMode != "oidc" {
			errs = append(errs, errors.New("PUSH_AUTH_MODE must be oidc in prod"))
		}
		if c.PlayMode != "google" || c.FCMMode != "fcm" || c.BlobMode != "gcs" {
			errs = append(errs, errors.New("PLAY_MODE=google, FCM_MODE=fcm and BLOB_MODE=gcs are required in prod"))
		}
		if c.EntitlementKeyPEM == "" || c.SealKeyB64 == "" {
			errs = append(errs, errors.New("ENTITLEMENT_KEY_PEM and SEAL_KEY_B64 are required in prod"))
		}
		if c.Role == "all" {
			errs = append(errs, errors.New("ROLE=all is for local development only"))
		}
	}
	return errors.Join(errs...)
}

func get(k, def string) string {
	if v, ok := os.LookupEnv(k); ok && v != "" {
		return v
	}
	return def
}

func getInt(k string, def int) int {
	if v := os.Getenv(k); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func getBool(k string, def bool) bool {
	if v := os.Getenv(k); v != "" {
		if b, err := strconv.ParseBool(v); err == nil {
			return b
		}
	}
	return def
}

func getDur(k string, def time.Duration) time.Duration {
	if v := os.Getenv(k); v != "" {
		if d, err := time.ParseDuration(v); err == nil {
			return d
		}
	}
	return def
}

func getList(k string) []string {
	var out []string
	for _, s := range strings.Split(os.Getenv(k), ",") {
		if s = strings.TrimSpace(s); s != "" {
			out = append(out, s)
		}
	}
	return out
}
