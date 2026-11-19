// Package app wires configuration into services (shared by cmd/sarkari and the integration tests).
package app

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/account"
	"github.com/amanroy3901/job-vac/backend/internal/admin"
	"github.com/amanroy3901/job-vac/backend/internal/auth"
	"github.com/amanroy3901/job-vac/backend/internal/billing"
	"github.com/amanroy3901/job-vac/backend/internal/blob"
	"github.com/amanroy3901/job-vac/backend/internal/catalog"
	"github.com/amanroy3901/job-vac/backend/internal/config"
	"github.com/amanroy3901/job-vac/backend/internal/family"
	"github.com/amanroy3901/job-vac/backend/internal/httpapi"
	"github.com/amanroy3901/job-vac/backend/internal/ingest"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/amanroy3901/job-vac/backend/internal/seal"
	"github.com/amanroy3901/job-vac/backend/internal/worker"
	"github.com/jackc/pgx/v5/pgxpool"
)

// App holds the wired services.
type App struct {
	Cfg       *config.Config
	Pool      *pgxpool.Pool
	Server    *httpapi.Server
	Worker    *worker.Worker
	Publisher *catalog.Publisher
	Curation  *ingest.Service
	Billing   *billing.Service
	Handler   http.Handler
}

// Overrides replace external integrations (tests).
type Overrides struct {
	IDTokens auth.IDTokenVerifier
	AppCheck auth.AppCheckVerifier
	Play     billing.PlayClient
	Sender   push.Sender
	Store    blob.Store
	Admin    admin.Authenticator
	Now      func() time.Time
}

// New wires everything for cfg.
func New(ctx context.Context, cfg *config.Config, pool *pgxpool.Pool, o Overrides) (*App, error) {
	now := o.Now
	if now == nil {
		now = time.Now
	}
	sealer, err := seal.New(cfg.SealKeyB64)
	if err != nil {
		return nil, err
	}
	signer, err := billing.NewSigner(cfg.EntitlementKeyPEM, cfg.EntitlementKeyID)
	if err != nil {
		return nil, err
	}

	idTokens := o.IDTokens
	if idTokens == nil {
		switch cfg.AuthMode {
		case "dev":
			idTokens = auth.DevVerifier{}
		case "firebase":
			idTokens = &auth.FirebaseVerifier{ProjectID: cfg.FirebaseProjectID, Keys: auth.NewRemoteKeySet(auth.FirebaseCertsURL, nil)}
		default:
			return nil, fmt.Errorf("AUTH_MODE %q", cfg.AuthMode)
		}
	}
	appCheck := o.AppCheck
	if appCheck == nil {
		if cfg.AppCheckMode == "off" {
			appCheck = auth.AllowAppCheck{}
		} else {
			appCheck = &auth.FirebaseAppCheck{ProjectNumber: cfg.FirebaseProjectNumber, AppIDs: cfg.AppCheckAppIDs,
				Keys: auth.NewRemoteKeySet(auth.AppCheckJWKSURL, nil)}
		}
	}
	play := o.Play
	if play == nil {
		if cfg.PlayMode == "fake" {
			play = billing.NewFakePlay()
		} else if play, err = billing.NewGooglePlay(ctx); err != nil {
			return nil, fmt.Errorf("play client: %w", err)
		}
	}
	sender := o.Sender
	if sender == nil {
		if cfg.FCMMode == "log" {
			sender = push.LogSender{}
		} else if sender, err = push.NewFCM(ctx, cfg.FirebaseProjectID); err != nil {
			return nil, fmt.Errorf("fcm: %w", err)
		}
	}
	store := o.Store
	if store == nil {
		if cfg.BlobMode == "gcs" {
			if store, err = blob.NewGCS(ctx, cfg.GCSBucket); err != nil {
				return nil, fmt.Errorf("gcs: %w", err)
			}
		} else {
			store = blob.Local{Dir: cfg.BlobDir}
		}
	}
	var rtdnAuth, tickAuth *auth.OIDCVerifier
	if cfg.PushAuthMode == "oidc" {
		keys := auth.NewRemoteKeySet(auth.GoogleOIDCJWKS, nil)
		rtdnAuth = &auth.OIDCVerifier{Audience: cfg.RTDNAudience, Email: cfg.RTDNServiceAccount, Keys: keys}
		tickAuth = &auth.OIDCVerifier{Audience: cfg.TickAudience, Email: cfg.TickServiceAccount, Keys: keys}
	}

	accounts := &account.Service{Pool: pool, Now: now}
	bill := &billing.Service{Pool: pool, Play: play, PackageName: cfg.PlayPackageName, Seal: sealer, Signer: signer, Now: now}
	fam := &family.Service{Pool: pool, PublicBaseURL: cfg.PublicBaseURL, Now: now}
	pub := &catalog.Publisher{Pool: pool, Store: store, Now: now}
	cur := &ingest.Service{Pool: pool, Slots: push.DefaultSlots, Now: now}
	wk := &worker.Worker{Pool: pool, Publisher: pub, Topics: &push.Dispatcher{Pool: pool, Sender: sender, Now: now},
		Accounts: accounts, AccountTx: &push.AccountSender{Pool: pool, Sender: sender}, Billing: bill, Sender: sender,
		Seal: sealer, Now: now}

	adminAuth := o.Admin
	if adminAuth == nil {
		if cfg.AdminAuthMode == "dev" {
			adminAuth = &admin.DevAuth{Pool: pool}
		} else {
			adminAuth = &admin.IAPAuth{Pool: pool, Verifier: &auth.IAPVerifier{Audience: cfg.IAPAudience,
				Keys: auth.NewRemoteKeySet(auth.IAPJWKSURL, nil)}}
		}
	}
	srv := httpapi.New(httpapi.Deps{Cfg: cfg, Pool: pool, IDTokens: idTokens, AppCheck: appCheck, RTDNAuth: rtdnAuth,
		TickAuth: tickAuth, Accounts: accounts, Billing: bill, Family: fam, Publisher: pub, Sender: sender, Seal: sealer,
		Ticker: wk, Now: now})
	console := &admin.Console{Pool: pool, Auth: adminAuth, Curation: cur, Publisher: pub, APIBase: cfg.APIBaseURL,
		OnConfigChange: srv.InvalidateConfig}
	srv.SetAdmin(console.Handler())
	return &App{Cfg: cfg, Pool: pool, Server: srv, Worker: wk, Publisher: pub, Curation: cur, Billing: bill,
		Handler: srv.Handler()}, nil
}
