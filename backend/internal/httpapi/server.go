// Package httpapi is the REST layer for docs/api/openapi.yaml (/v1/**), the Play RTDN and App Store Server
// Notifications V2 hooks, the Cloud Scheduler ticks and the admin console mount. It uses the Go 1.22+ net/http
// pattern router; the AIP-style ":verb" paths (`/v1/me/devices/{id}:make-primary`) are routed by splitting the last
// segment on ':'.
package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"runtime/debug"
	"strconv"
	"strings"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/account"
	"github.com/amanroy3901/job-vac/backend/internal/auth"
	"github.com/amanroy3901/job-vac/backend/internal/billing"
	"github.com/amanroy3901/job-vac/backend/internal/catalog"
	"github.com/amanroy3901/job-vac/backend/internal/config"
	"github.com/amanroy3901/job-vac/backend/internal/family"
	"github.com/amanroy3901/job-vac/backend/internal/ids"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/amanroy3901/job-vac/backend/internal/seal"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Ticker runs the worker ticks (implemented by internal/worker).
type Ticker interface {
	Tick(ctx context.Context, name string) (map[string]any, error)
}

// Deps are the server's collaborators.
type Deps struct {
	Cfg       *config.Config
	Pool      *pgxpool.Pool
	IDTokens  auth.IDTokenVerifier
	AppCheck  auth.AppCheckVerifier
	RTDNAuth  *auth.OIDCVerifier // nil = no auth (PUSH_AUTH_MODE=off, local only)
	TickAuth  *auth.OIDCVerifier
	Accounts  *account.Service
	Billing   *billing.Service
	Family    *family.Service
	Publisher *catalog.Publisher
	Sender    push.Sender
	Seal      *seal.Sealer
	Ticker    Ticker
	Admin     http.Handler // mounted at /admin/ (role admin|all)
	Now       func() time.Time
}

// Server is the HTTP API.
type Server struct {
	d       Deps
	cfg     *configCache
	limiter *limiter
}

// New builds a server.
func New(d Deps) *Server {
	if d.Now == nil {
		d.Now = time.Now
	}
	return &Server{d: d, cfg: newConfigCache(d.Pool, d.Now), limiter: newLimiter(d.Now)}
}

type ctxKey int

const (
	keyReqID ctxKey = iota
	keyLang
	keyIdentity
	keyUser
	keyRoute
)

func reqID(ctx context.Context) string { s, _ := ctx.Value(keyReqID).(string); return s }
func lang(ctx context.Context) string {
	if s, ok := ctx.Value(keyLang).(string); ok {
		return s
	}
	return "hi"
}
func identity(ctx context.Context) *auth.Identity {
	i, _ := ctx.Value(keyIdentity).(*auth.Identity)
	return i
}
func user(ctx context.Context) *account.User { u, _ := ctx.Value(keyUser).(*account.User); return u }

// handler is an endpoint that may return an error (rendered as problem+json).
type handler func(w http.ResponseWriter, r *http.Request) error

type authLevel int

const (
	authNone     authLevel = iota
	authIdentity           // verified Firebase ID token, account optional (startSession)
	authAccount            // verified token + existing account
)

// opts configure one route.
type opts struct {
	auth         authLevel
	appCheck     bool // X-Firebase-AppCheck required (APP_CHECK_MODE)
	idem         bool // Idempotency-Key required (openapi IdempotencyKey parameter)
	allowPending bool // reachable while the account is pending deletion
	public       bool // public CDN-cacheable file (no version gate)
	maxBody      int64
	limit        *rate
}

// Handler returns the root handler for the configured role.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	role := s.d.Cfg.Role
	if role == "api" || role == "all" {
		s.routesV1(mux)
		mux.Handle("POST /hooks/play-rtdn", s.wrap(opts{maxBody: 64 << 10}, s.playRTDN))
		mux.Handle("POST /hooks/app-store", s.wrap(opts{maxBody: 256 << 10}, s.appStoreNotification))
	}
	if role == "worker" || role == "all" {
		mux.Handle("POST /internal/tick/{name}", s.wrap(opts{}, s.tick))
	}
	if (role == "admin" || role == "all") && s.d.Admin != nil {
		mux.Handle("/admin/", s.d.Admin)
	}
	mux.Handle("GET /healthz", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
		defer cancel()
		if err := s.d.Pool.Ping(ctx); err != nil {
			http.Error(w, "db down", http.StatusServiceUnavailable)
			return
		}
		_, _ = io.WriteString(w, "ok")
	}))
	mux.Handle("/", s.wrap(opts{}, func(w http.ResponseWriter, r *http.Request) error {
		return problem.New(404, problem.NotFound, "no route "+r.Method+" "+r.URL.Path)
	}))
	return s.base(mux)
}

// base adds request ids, language, structured access logs, panic recovery and security headers.
func (s *Server) base(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := s.d.Now()
		rid := r.Header.Get("X-Request-Id")
		if rid == "" || len(rid) > 64 {
			rid = strings.TrimPrefix(ids.New("r"), "r_")
		}
		ctx := context.WithValue(r.Context(), keyReqID, rid)
		ctx = context.WithValue(ctx, keyLang, parseLang(r.Header.Get("Accept-Language")))
		w.Header().Set("X-Request-Id", rid)
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Strict-Transport-Security", "max-age=31536000")
		sw := &statusWriter{ResponseWriter: w, status: 200}
		route := new(string)
		ctx = context.WithValue(ctx, keyRoute, route)
		defer func() {
			if p := recover(); p != nil {
				slog.ErrorContext(ctx, "panic", "panic", p, "stack", string(debug.Stack()))
				s.writeErr(sw, r.WithContext(ctx), problem.New(500, problem.Internal, ""))
			}
			// Never log bodies, tokens or client IPs (security-privacy.md §4).
			level := slog.LevelInfo
			if sw.status >= 500 {
				level = slog.LevelError
			}
			slog.Log(ctx, level, "request", "request_id", rid, "method", r.Method, "route", *route, "path", r.URL.Path,
				"status", sw.status, "duration_ms", s.d.Now().Sub(start).Milliseconds(), "app_version", r.Header.Get("X-App-Version"))
		}()
		next.ServeHTTP(sw, r.WithContext(ctx))
	})
}

type statusWriter struct {
	http.ResponseWriter
	status int
	wrote  bool
}

func (w *statusWriter) WriteHeader(code int) {
	if !w.wrote {
		w.status, w.wrote = code, true
	}
	w.ResponseWriter.WriteHeader(code)
}

func (w *statusWriter) Write(b []byte) (int, error) {
	w.wrote = true
	return w.ResponseWriter.Write(b)
}

func parseLang(h string) string {
	for _, part := range strings.Split(h, ",") {
		tag := strings.ToLower(strings.TrimSpace(strings.SplitN(part, ";", 2)[0]))
		tag = strings.SplitN(tag, "-", 2)[0]
		if len(tag) == 2 {
			switch tag {
			case "as", "bn", "en", "gu", "hi", "kn", "ml", "mr", "or", "pa", "ta", "te", "ur":
				return tag
			}
		}
	}
	return "hi"
}

// wrap applies per-route middleware: body limit, version gate, App Check, auth, rate limit, idempotency.
func (s *Server) wrap(o opts, h handler) http.Handler {
	if o.maxBody == 0 {
		o.maxBody = 64 << 10
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, o.maxBody)
		if err := s.pre(w, r, o, h); err != nil {
			s.writeErr(w, r, err)
		}
	})
}

func (s *Server) pre(w http.ResponseWriter, r *http.Request, o opts, h handler) error {
	ctx := r.Context()
	if p, ok := ctx.Value(keyRoute).(*string); ok && r.Pattern != "" {
		*p = r.Pattern
	}
	if !o.public && strings.HasPrefix(r.URL.Path, "/v1/") {
		if v, err := strconv.Atoi(r.Header.Get("X-App-Version")); err == nil {
			cfg := s.cfg.get(ctx)
			if v < cfg.MinSupportedVersion {
				return problem.New(http.StatusUpgradeRequired, problem.UpgradeRequired, "")
			}
		}
	}
	if o.appCheck {
		if err := s.checkAppCheck(r); err != nil {
			return err
		}
	}
	rateKey := "ip:" + ipPrefix(r)
	if o.auth != authNone {
		raw, ok := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		if !ok {
			return problem.New(401, problem.Unauthenticated, "missing bearer token")
		}
		id, err := s.d.IDTokens.VerifyIDToken(ctx, strings.TrimSpace(raw))
		if errors.Is(err, auth.ErrExpired) {
			return problem.New(401, problem.TokenExpired, "")
		}
		if err != nil {
			return problem.New(401, problem.Unauthenticated, "")
		}
		ctx = context.WithValue(ctx, keyIdentity, id)
		rateKey = "fb:" + id.UID
		if o.auth == authAccount {
			u, err := s.d.Accounts.ByFirebaseUID(ctx, id.UID)
			if err != nil {
				return err
			}
			if u == nil {
				return &problem.Error{Status: 403, Code: problem.Forbidden, Title: "Forbidden", Detail: "no account: call POST /v1/session first"}
			}
			if u.State != "active" && !o.allowPending {
				return problem.New(403, problem.AccountPendingDeletion, "")
			}
			ctx = context.WithValue(ctx, keyUser, u)
			rateKey = "u:" + u.ID
			s.d.Accounts.Touch(ctx, u.ID)
		}
		w.Header().Set("Cache-Control", "private, no-store")
	}
	lim := o.limit
	if lim == nil {
		lim = &rate{per: time.Minute, n: 120, burst: 30}
		if o.auth == authNone {
			lim = &rate{per: time.Minute, n: 300, burst: 60}
		}
	}
	if wait := s.limiter.allow(r.Pattern+"|"+rateKey, *lim); wait > 0 {
		p := problem.New(429, problem.RateLimited, "")
		p.RetryAfter = int(wait.Seconds()) + 1
		return p
	}
	r = r.WithContext(ctx)
	if o.idem {
		return s.idempotent(w, r, h)
	}
	return h(w, r)
}

func (s *Server) checkAppCheck(r *http.Request) error {
	mode := s.d.Cfg.AppCheckMode
	if mode == "off" || s.d.AppCheck == nil {
		return nil
	}
	_, err := s.d.AppCheck.VerifyAppCheck(r.Context(), r.Header.Get("X-Firebase-AppCheck"))
	if err == nil {
		return nil
	}
	if mode == "monitor" { // rollout: monitor-only for 2 weeks, then enforce (security-privacy.md §1)
		slog.WarnContext(r.Context(), "app check failed (monitor mode)", "err", err, "route", r.Pattern)
		return nil
	}
	return problem.New(401, problem.AppCheckFailed, "")
}

// ipPrefix returns the client /24 (IPv4) or /48-ish (IPv6) for in-memory rate limiting only; never logged.
func ipPrefix(r *http.Request) string {
	ip := r.Header.Get("X-Forwarded-For")
	if i := strings.IndexByte(ip, ','); i >= 0 {
		ip = ip[:i]
	}
	if ip == "" {
		ip = r.RemoteAddr
		if i := strings.LastIndexByte(ip, ':'); i > 0 && strings.Count(ip, ":") == 1 {
			ip = ip[:i]
		}
	}
	ip = strings.TrimSpace(ip)
	if strings.Count(ip, ".") == 3 {
		return ip[:strings.LastIndexByte(ip, '.')]
	}
	parts := strings.Split(ip, ":")
	if len(parts) > 3 {
		parts = parts[:3]
	}
	return strings.Join(parts, ":")
}

func (s *Server) writeErr(w http.ResponseWriter, r *http.Request, err error) {
	p := problem.As(err)
	var mbe *http.MaxBytesError
	switch {
	case p != nil:
	case errors.As(err, &mbe):
		p = problem.Validation("request body too large")
		p.Status = http.StatusRequestEntityTooLarge
	case errors.Is(err, context.Canceled):
		p = problem.New(499, problem.Internal, "client closed request")
	default:
		slog.ErrorContext(r.Context(), "internal error", "err", err, "route", r.Pattern)
		p = problem.New(500, problem.Internal, "")
		p.Retryable = true
	}
	problem.Write(w, p, lang(r.Context()), reqID(r.Context()), s.d.Cfg.APIBaseURL)
}

// ---------------------------------------------------------------- JSON helpers

func writeJSON(w http.ResponseWriter, status int, v any) error {
	w.Header().Set("Content-Type", "application/json")
	if w.Header().Get("Cache-Control") == "" {
		w.Header().Set("Cache-Control", "private, no-store")
	}
	w.WriteHeader(status)
	return json.NewEncoder(w).Encode(v)
}

func decode(r *http.Request, v any, optional bool) error {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		var mbe *http.MaxBytesError
		if errors.As(err, &mbe) {
			p := problem.Validation("request body too large")
			p.Status = http.StatusRequestEntityTooLarge
			return p
		}
		return err
	}
	if len(strings.TrimSpace(string(body))) == 0 {
		if optional {
			return nil
		}
		return problem.Validation("request body required")
	}
	if err := json.Unmarshal(body, v); err != nil {
		return problem.Validation("invalid JSON: " + err.Error())
	}
	return nil
}

// splitVerb splits "{id}:verb" path values.
func splitVerb(seg string) (string, string) {
	if i := strings.LastIndexByte(seg, ':'); i > 0 {
		return seg[:i], seg[i+1:]
	}
	return seg, ""
}

func notImplemented(op string) handler {
	return func(http.ResponseWriter, *http.Request) error {
		// TODO: implement (see backend/README.md "Implemented vs stubbed").
		return problem.New(501, problem.NotImplemented, op+" is not implemented yet")
	}
}

// SetAdmin mounts the admin console handler (built after the server so it can invalidate the config cache).
func (s *Server) SetAdmin(h http.Handler) { s.d.Admin = h }

// InvalidateConfig drops the cached AppConfig (after an admin edit).
func (s *Server) InvalidateConfig() { s.cfg.invalidate() }
