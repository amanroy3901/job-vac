package httpapi

import (
	"bytes"
	"compress/gzip"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log/slog"
	"maps"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/blob"
	"github.com/amanroy3901/job-vac/backend/internal/catalog"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// AppConfig is the API AppConfig. Code defaults are overridden by the app_config row 'app' (admin-edited).
type AppConfig struct {
	MinSupportedVersion  int               `json:"min_supported_version"`
	LatestVersion        int               `json:"latest_version"`
	Update               string            `json:"update"`
	ReferenceVersion     string            `json:"reference_version"`
	Features             map[string]bool   `json:"features"`
	KillSwitches         map[string]bool   `json:"kill_switches"`
	Ads                  map[string]any    `json:"ads"`
	Maintenance          any               `json:"maintenance"`
	Support              map[string]string `json:"support"`
	Disclaimer           map[string]string `json:"disclaimer"`
	OEMGuides            map[string]any    `json:"oem_guides,omitempty"`
	SelfTestIntervalDays int               `json:"self_test_interval_days"`
	PrivacyNoticeVersion string            `json:"privacy_notice_version"`
	EntitlementJWKS      map[string]any    `json:"entitlement_token_jwks"`
	ReminderBackup       map[string]any    `json:"reminder_backup"`
	PinnedStates         []string          `json:"-"`
}

func defaultConfig() AppConfig {
	return AppConfig{
		MinSupportedVersion: 1, LatestVersion: 1, Update: "none",
		Features: map[string]bool{"paywall_enabled": true, "family_alerts": false, "clash_alerts": false, "digest": true,
			"ads": true, "cloud_backup": true, "usage_counters_prompt": true},
		KillSwitches: map[string]bool{},
		Ads: map[string]any{"enabled": true, "placements": []string{"feed_bottom", "digest"}, "min_app_version": nil,
			"frequency":          map[string]int{"max_per_screen": 1, "hide_on_first_sessions": 3},
			"blocked_categories": []string{"gambling", "dating", "loans", "job_placement_guarantees"}},
		Support: map[string]string{"email": "support@sarkarimatch.in", "grievance_officer": "grievance@sarkarimatch.in",
			"privacy_policy_url": "https://sarkarimatch.in/privacy", "terms_url": "https://sarkarimatch.in/terms"},
		Disclaimer: map[string]string{"en": "Not an official government app. Always check the official notice.",
			"hi": "यह सरकारी ऐप नहीं है। हमेशा आधिकारिक नोटिस देखें।"},
		SelfTestIntervalDays: 7, PrivacyNoticeVersion: "2026-11-19",
		ReminderBackup: map[string]any{"d_minus_3": "19:00", "d_minus_1": "19:00", "last_day": "08:00",
			"backup_delay_minutes": map[string]int{"d_minus_1": 30, "last_day": 15}},
		PinnedStates: []string{"UP", "BR"},
	}
}

type configCache struct {
	pool *pgxpool.Pool
	now  func() time.Time
	mu   sync.Mutex
	val  AppConfig
	at   time.Time
}

func newConfigCache(pool *pgxpool.Pool, now func() time.Time) *configCache {
	return &configCache{pool: pool, now: now}
}

// get returns the merged config, cached 5 minutes (operations.md §1).
func (c *configCache) get(ctx context.Context) AppConfig {
	c.mu.Lock()
	defer c.mu.Unlock()
	if !c.at.IsZero() && c.now().Sub(c.at) < 5*time.Minute {
		return c.val
	}
	cfg := defaultConfig()
	var raw []byte
	err := c.pool.QueryRow(ctx, `SELECT value FROM app_config WHERE key = 'app'`).Scan(&raw)
	if err == nil {
		var over map[string]json.RawMessage
		if json.Unmarshal(raw, &over) == nil {
			base, _ := json.Marshal(cfg)
			var merged map[string]json.RawMessage
			_ = json.Unmarshal(base, &merged)
			maps.Copy(merged, over)
			mb, _ := json.Marshal(merged)
			_ = json.Unmarshal(mb, &cfg)
			if ps, ok := over["pinned_states"]; ok {
				_ = json.Unmarshal(ps, &cfg.PinnedStates)
			}
		}
	} else if !errors.Is(err, pgx.ErrNoRows) {
		slog.WarnContext(ctx, "app_config read failed; using defaults", "err", err)
		return cfg
	}
	if rv, err := referenceVersion(ctx, c.pool); err == nil {
		cfg.ReferenceVersion = rv
	}
	c.val, c.at = cfg, c.now()
	return cfg
}

func (c *configCache) invalidate() {
	c.mu.Lock()
	c.at = time.Time{}
	c.mu.Unlock()
}

// ---------------------------------------------------------------- helpers for public, cacheable files

func etagOf(b []byte) string {
	h := sha256.Sum256(b)
	return `"` + hex.EncodeToString(h[:16]) + `"`
}

// writeCached writes a public JSON body with ETag/304, Cache-Control and gzip when accepted.
func writeCached(w http.ResponseWriter, r *http.Request, body []byte, etag, cacheControl string) error {
	h := w.Header()
	h.Set("Content-Type", "application/json")
	h.Set("Cache-Control", cacheControl)
	h.Set("Vary", "Accept-Encoding, Accept-Language")
	if etag != "" {
		h.Set("ETag", etag)
		for _, t := range strings.Split(r.Header.Get("If-None-Match"), ",") {
			if strings.TrimSpace(t) == etag || strings.TrimSpace(t) == "W/"+etag {
				w.WriteHeader(http.StatusNotModified)
				return nil
			}
		}
	}
	if len(body) > 1024 && strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
		var buf bytes.Buffer
		zw, _ := gzip.NewWriterLevel(&buf, gzip.BestCompression)
		_, _ = zw.Write(body)
		_ = zw.Close()
		h.Set("Content-Encoding", "gzip")
		body = buf.Bytes()
	}
	h.Set("Content-Length", strconv.Itoa(len(body)))
	w.WriteHeader(http.StatusOK)
	_, err := w.Write(body)
	return err
}

func (s *Server) getConfig(w http.ResponseWriter, r *http.Request) error {
	cfg := s.cfg.get(r.Context())
	cfg.EntitlementJWKS = s.d.Billing.Signer.JWKS()
	if v, err := strconv.Atoi(r.Header.Get("X-App-Version")); err == nil {
		switch {
		case v < cfg.MinSupportedVersion:
			cfg.Update = "force"
		case v < cfg.LatestVersion && cfg.Update == "none":
			cfg.Update = "soft"
		}
	}
	b, _ := json.Marshal(cfg)
	return writeCached(w, r, b, etagOf(b), "public, max-age=300")
}

func (s *Server) getHealth(w http.ResponseWriter, r *http.Request) error {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	status, delayed := "up", false
	var overdue int
	if err := s.d.Pool.QueryRow(ctx, `SELECT
		(SELECT count(*) FROM topic_send WHERE state = 'queued' AND due_at < now() - interval '10 minutes') +
		(SELECT count(*) FROM catalog_batch WHERE built_at IS NULL AND published_at < now() - interval '10 minutes')`).Scan(&overdue); err != nil {
		status, delayed = "degraded", true
	} else if overdue > 0 {
		status, delayed = "degraded", true
	}
	body := map[string]any{"status": status, "alerts_delayed": delayed}
	if cfg := s.cfg.get(ctx); cfg.Maintenance != nil {
		body["status"] = "down"
	}
	b, _ := json.Marshal(body)
	return writeCached(w, r, b, "", "public, max-age=30")
}

func (s *Server) localeSet(r *http.Request) (string, error) {
	locales, err := catalog.LoadLocales(r.Context(), s.d.Pool)
	if err != nil {
		return "", err
	}
	return catalog.NormalizeLocales(r.URL.Query().Get("locales"), catalog.Chain(locales, lang(r.Context()))), nil
}

func (s *Server) catalogHead(w http.ResponseWriter, r *http.Request) error {
	h, err := catalog.LoadHead(r.Context(), s.d.Pool, s.d.Now())
	if err != nil {
		return err
	}
	st := h.ServerTime
	h.ServerTime = ""
	stable, _ := json.Marshal(h)
	h.ServerTime = st
	b, _ := json.Marshal(h)
	return writeCached(w, r, b, etagOf(stable), catalog.CacheHead)
}

func (s *Server) catalogFile(kind string) handler {
	return func(w http.ResponseWriter, r *http.Request) error {
		seq, err := strconv.ParseInt(r.PathValue("seq"), 10, 64)
		if err != nil || seq < 1 {
			return problem.Validation("seq must be a positive integer", problem.FieldError{Field: "seq", Code: "out_of_range"})
		}
		ctx := r.Context()
		var ok bool
		if kind == "snapshots" {
			err = s.d.Pool.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM catalog_snapshot WHERE seq = $1)`, seq).Scan(&ok)
		} else {
			err = s.d.Pool.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM catalog_batch WHERE seq = $1 AND built_at IS NOT NULL)`, seq).Scan(&ok)
		}
		if err != nil {
			return err
		}
		if !ok {
			return problem.New(404, problem.NotFound, kind+" "+strconv.FormatInt(seq, 10))
		}
		set, err := s.localeSet(r)
		if err != nil {
			return err
		}
		b, err := s.d.Publisher.File(ctx, kind, seq, set)
		if errors.Is(err, blob.ErrNotFound) {
			return problem.New(404, problem.NotFound, "catalog file")
		}
		if err != nil {
			return err
		}
		h := sha256.Sum256(b)
		return writeCached(w, r, b, `"`+hex.EncodeToString(h[:])+`"`, catalog.CacheImmutable)
	}
}

func (s *Server) getNotice(w http.ResponseWriter, r *http.Request) error {
	n, err := catalog.LoadNotice(r.Context(), s.d.Pool, r.PathValue("notice_id"))
	if err != nil {
		return err
	}
	if n == nil || n["status"] == "draft" {
		return problem.New(404, problem.NotFound, "notice")
	}
	set, err := s.localeSet(r)
	if err != nil {
		return err
	}
	out := catalog.FilterLocales(n, catalog.SetMap(set))
	b, _ := json.Marshal(out)
	return writeCached(w, r, b, etagOf(b), "public, max-age=300")
}

// referenceVersion is a digest over the reference tables (AppConfig.reference_version).
func referenceVersion(ctx context.Context, q *pgxpool.Pool) (string, error) {
	var v string
	err := q.QueryRow(ctx, `SELECT left(md5(
		coalesce((SELECT string_agg(kind||code||name::text||sort||deprecated, '|' ORDER BY kind, code) FROM ref_item),'') ||
		coalesce((SELECT string_agg(code||coalesce(parent,'')||level||name::text||selectable, '|' ORDER BY code) FROM qualification),'') ||
		coalesce((SELECT string_agg(code||status||array_to_string(fallback,','), '|' ORDER BY code) FROM locale),'')), 12)`).Scan(&v)
	return v, err
}

func (s *Server) getReference(w http.ResponseWriter, r *http.Request) error {
	ctx := r.Context()
	locales, err := catalog.LoadLocales(ctx, s.d.Pool)
	if err != nil {
		return err
	}
	langs := make([]map[string]any, 0, len(locales))
	for _, l := range locales {
		status := "coming_soon"
		if l.Status == "launch" {
			status = "enabled"
		}
		langs = append(langs, map[string]any{"code": l.Code, "name_native": l.Name, "script": l.Script, "rtl": l.RTL,
			"status": status, "fallback": l.Fallback})
	}
	rows, err := s.d.Pool.Query(ctx, `SELECT kind, code, name, sort, deprecated FROM ref_item ORDER BY kind, sort, code`)
	if err != nil {
		return err
	}
	byKind := map[string][]any{}
	for rows.Next() {
		var kind, code string
		var name []byte
		var sort int
		var dep bool
		if err := rows.Scan(&kind, &code, &name, &sort, &dep); err != nil {
			return err
		}
		byKind[kind] = append(byKind[kind], map[string]any{"code": code, "name": json.RawMessage(name), "sort": sort, "deprecated": dep})
	}
	if err := rows.Err(); err != nil {
		return err
	}
	rows, err = s.d.Pool.Query(ctx, `SELECT code, parent, level, name, aliases, selectable FROM qualification ORDER BY level, code`)
	if err != nil {
		return err
	}
	quals := []any{}
	for rows.Next() {
		var code string
		var parent *string
		var level int
		var name []byte
		var aliases []string
		var sel bool
		if err := rows.Scan(&code, &parent, &level, &name, &aliases, &sel); err != nil {
			return err
		}
		quals = append(quals, map[string]any{"code": code, "parent": parent, "level": level, "name": json.RawMessage(name),
			"aliases": aliases, "selectable": sel})
	}
	if err := rows.Err(); err != nil {
		return err
	}
	list := func(k string) []any {
		if v := byKind[k]; v != nil {
			return v
		}
		return []any{}
	}
	cfg := s.cfg.get(ctx)
	rv, err := referenceVersion(ctx, s.d.Pool)
	if err != nil {
		return err
	}
	body := map[string]any{"version": rv, "languages": langs, "code_map": map[string]string{}, "qualifications": quals,
		"categories": list("category"), "states": list("state"), "pinned_states": cfg.PinnedStates,
		"exam_types": list("exam_type"), "pwd_types": list("pwd_type"), "extra_attributes": list("extra"),
		"organizations": list("organization")}
	b, _ := json.Marshal(body)
	return writeCached(w, r, b, `"`+rv+`"`, "public, max-age=3600")
}

func (s *Server) listProducts(w http.ResponseWriter, r *http.Request) error {
	ps, err := s.d.Billing.Products(r.Context())
	if err != nil {
		return err
	}
	b, _ := json.Marshal(map[string]any{"items": ps})
	return writeCached(w, r, b, etagOf(b), "public, max-age=3600")
}
