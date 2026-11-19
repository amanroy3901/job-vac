// Package admin is the curation console (ingestion-pipeline.md §7): a JSON API under /admin/api/ and a few
// server-rendered pages under /admin/ to create a notice draft, attach extracted rule JSON per post, review,
// approve (second reviewer for risk-flagged posts) and publish. In prod the service sits behind IAP and the
// assertion is verified again here (security-privacy.md §5); every action is audit-logged by the services.
package admin

import (
	"context"
	"embed"
	"encoding/json"
	"errors"
	"html/template"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"

	"github.com/amanroy3901/job-vac/backend/internal/auth"
	"github.com/amanroy3901/job-vac/backend/internal/catalog"
	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/ingest"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Principal is an authenticated admin.
type Principal struct {
	Email string
	Role  string // owner | reviewer
}

// Authenticator resolves the admin from a request.
type Authenticator interface {
	Authenticate(r *http.Request) (*Principal, error)
}

// IAPAuth verifies x-goog-iap-jwt-assertion and looks the email up in admin_user.
type IAPAuth struct {
	Verifier *auth.IAPVerifier
	Pool     *pgxpool.Pool
}

// Authenticate implements Authenticator.
func (a *IAPAuth) Authenticate(r *http.Request) (*Principal, error) {
	email, err := a.Verifier.Verify(r.Context(), r.Header.Get("X-Goog-IAP-JWT-Assertion"))
	if err != nil {
		return nil, err
	}
	return lookup(r.Context(), a.Pool, email)
}

// DevAuth trusts the X-Admin-Email header (ADMIN_AUTH_MODE=dev; refused in prod by config validation).
type DevAuth struct{ Pool *pgxpool.Pool }

// Authenticate implements Authenticator.
func (a *DevAuth) Authenticate(r *http.Request) (*Principal, error) {
	email := r.Header.Get("X-Admin-Email")
	if email == "" {
		email = r.URL.Query().Get("as")
	}
	if email == "" {
		return nil, errors.New("X-Admin-Email header required in dev mode")
	}
	return lookup(r.Context(), a.Pool, email)
}

func lookup(ctx context.Context, pool *pgxpool.Pool, email string) (*Principal, error) {
	p := &Principal{Email: strings.ToLower(email)}
	err := pool.QueryRow(ctx, `SELECT role FROM admin_user WHERE email = $1`, p.Email).Scan(&p.Role)
	if db.IsNoRows(err) {
		return nil, errors.New("not an admin: " + p.Email)
	}
	return p, err
}

// Console serves /admin/.
type Console struct {
	Pool      *pgxpool.Pool
	Auth      Authenticator
	Curation  *ingest.Service
	Publisher *catalog.Publisher
	// OnConfigChange is called after app_config changes (cache invalidation).
	OnConfigChange func()
	APIBase        string
}

type principalKey struct{}

func principal(ctx context.Context) *Principal {
	p, _ := ctx.Value(principalKey{}).(*Principal)
	return p
}

// Handler builds the mux.
func (c *Console) Handler() http.Handler {
	mux := http.NewServeMux()
	api := func(role string, h func(w http.ResponseWriter, r *http.Request) error) http.Handler {
		return c.guard(role, true, h)
	}
	page := func(h func(w http.ResponseWriter, r *http.Request) error) http.Handler { return c.guard("", false, h) }

	mux.Handle("GET /admin/api/tasks", api("", c.apiListTasks))
	mux.Handle("POST /admin/api/notices", api("", c.apiCreateNotice))
	mux.Handle("GET /admin/api/tasks/{id}", api("", c.apiGetTask))
	mux.Handle("PUT /admin/api/tasks/{id}/posts/{code}/rule", api("", c.apiAttachRule))
	mux.Handle("POST /admin/api/rules/validate", api("", c.apiValidateRule))
	mux.Handle("POST /admin/api/tasks/{id}/approve", api("", c.apiApprove))
	mux.Handle("POST /admin/api/tasks/{id}/reject", api("", c.apiReject))
	mux.Handle("POST /admin/api/tasks/{id}/publish", api("", c.apiPublish))
	mux.Handle("POST /admin/api/notices/{id}/retract", api("owner", c.apiRetract))
	mux.Handle("PUT /admin/api/config", api("owner", c.apiPutConfig))
	mux.Handle("GET /admin/api/reports", api("", c.apiReports))
	// TODO: lifecycle quick-entry, reports inbox actions, source health dashboard, promo grants (owner).

	mux.Handle("GET /admin/{$}", page(c.pageIndex))
	mux.Handle("GET /admin/tasks/{id}", page(c.pageTask))
	mux.Handle("POST /admin/notices", page(c.formCreateNotice))
	mux.Handle("POST /admin/tasks/{id}/posts/{code}/rule", page(c.formAttachRule))
	mux.Handle("POST /admin/tasks/{id}/{action}", page(c.formAction))
	return mux
}

// guard authenticates, enforces the role and same-origin form posts, and renders errors.
func (c *Console) guard(role string, isAPI bool, h func(w http.ResponseWriter, r *http.Request) error) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'")
		w.Header().Set("X-Frame-Options", "DENY")
		p, err := c.Auth.Authenticate(r)
		if err != nil {
			slog.WarnContext(r.Context(), "admin auth failed", "err", err)
			c.fail(w, r, isAPI, problem.New(401, problem.Unauthenticated, ""))
			return
		}
		if role == "owner" && p.Role != "owner" {
			c.fail(w, r, isAPI, problem.New(403, problem.Forbidden, "owner role required"))
			return
		}
		// CSRF defence for cookie-less IAP sessions: browsers send Sec-Fetch-Site on form posts.
		// TODO: per-form CSRF tokens once the htmx console lands (security-privacy.md §5).
		if r.Method != http.MethodGet && r.Header.Get("Sec-Fetch-Site") == "cross-site" {
			c.fail(w, r, isAPI, problem.New(403, problem.Forbidden, "cross-site request"))
			return
		}
		ctx := context.WithValue(r.Context(), principalKey{}, p)
		if err := h(w, r.WithContext(ctx)); err != nil {
			c.fail(w, r, isAPI, err)
		}
	})
}

func (c *Console) fail(w http.ResponseWriter, r *http.Request, isAPI bool, err error) {
	p := problem.As(err)
	if p == nil {
		slog.ErrorContext(r.Context(), "admin error", "err", err)
		p = problem.New(500, problem.Internal, "")
	}
	if isAPI {
		problem.Write(w, p, "en", "", c.APIBase)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(p.Status)
	_ = tpl.ExecuteTemplate(w, "error.html", map[string]any{"P": p})
}

func writeJSON(w http.ResponseWriter, status int, v any) error {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	return enc.Encode(v)
}

func readJSON(r *http.Request, v any) error {
	b, err := io.ReadAll(r.Body)
	if err != nil {
		return problem.Validation("body too large")
	}
	if err := json.Unmarshal(b, v); err != nil {
		return problem.Validation("invalid JSON: " + err.Error())
	}
	return nil
}

// ---------------------------------------------------------------- JSON API

func (c *Console) apiListTasks(w http.ResponseWriter, r *http.Request) error {
	ts, err := c.Curation.List(r.Context())
	if err != nil {
		return err
	}
	return writeJSON(w, 200, map[string]any{"items": ts})
}

func (c *Console) apiCreateNotice(w http.ResponseWriter, r *http.Request) error {
	var d ingest.Draft
	if err := readJSON(r, &d); err != nil {
		return err
	}
	t, err := c.Curation.CreateDraft(r.Context(), principal(r.Context()).Email, d)
	if err != nil {
		return err
	}
	return writeJSON(w, 201, t)
}

func (c *Console) apiGetTask(w http.ResponseWriter, r *http.Request) error {
	t, err := c.Curation.Get(r.Context(), r.PathValue("id"))
	if err != nil {
		return err
	}
	return writeJSON(w, 200, t)
}

func (c *Console) apiAttachRule(w http.ResponseWriter, r *http.Request) error {
	raw, err := io.ReadAll(r.Body)
	if err != nil {
		return problem.Validation("body too large")
	}
	rep, err := c.Curation.AttachRule(r.Context(), principal(r.Context()).Email, r.PathValue("id"), r.PathValue("code"), raw)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, rep)
}

func (c *Console) apiValidateRule(w http.ResponseWriter, r *http.Request) error {
	raw, err := io.ReadAll(r.Body)
	if err != nil {
		return problem.Validation("body too large")
	}
	rep, err := c.Curation.ValidateRule(r.Context(), raw)
	if err != nil {
		return err
	}
	return writeJSON(w, 200, rep)
}

func (c *Console) apiApprove(w http.ResponseWriter, r *http.Request) error {
	t, err := c.Curation.Approve(r.Context(), principal(r.Context()).Email, r.PathValue("id"))
	if err != nil {
		return err
	}
	return writeJSON(w, 200, t)
}

func (c *Console) apiReject(w http.ResponseWriter, r *http.Request) error {
	var in struct {
		Reason string `json:"reason"`
	}
	_ = readJSON(r, &in)
	if err := c.Curation.Reject(r.Context(), principal(r.Context()).Email, r.PathValue("id"), in.Reason); err != nil {
		return err
	}
	w.WriteHeader(204)
	return nil
}

// publish publishes the task, then writes the catalog batch right away (the worker retries on failure).
func (c *Console) publish(ctx context.Context, taskID string) (map[string]any, error) {
	seq, err := c.Curation.Publish(ctx, principal(ctx).Email, taskID)
	if err != nil {
		return nil, err
	}
	out := map[string]any{"catalog_seq": seq, "built": true}
	if err := c.Publisher.BuildBatch(ctx, seq); err != nil {
		slog.WarnContext(ctx, "catalog build deferred to worker", "seq", seq, "err", err)
		out["built"] = false
	} else if due, _, err := catalog.SnapshotDue(ctx, c.Pool, 0); err == nil && due {
		if _, err := c.Publisher.BuildSnapshot(ctx); err != nil {
			slog.WarnContext(ctx, "snapshot build deferred", "err", err)
		}
	}
	return out, nil
}

func (c *Console) apiPublish(w http.ResponseWriter, r *http.Request) error {
	out, err := c.publish(r.Context(), r.PathValue("id"))
	if err != nil {
		return err
	}
	return writeJSON(w, 200, out)
}

func (c *Console) apiRetract(w http.ResponseWriter, r *http.Request) error {
	var in struct {
		Reason string `json:"reason"`
	}
	if err := readJSON(r, &in); err != nil {
		return err
	}
	seq, err := c.Curation.Retract(r.Context(), principal(r.Context()).Email, r.PathValue("id"), in.Reason)
	if err != nil {
		return err
	}
	if err := c.Publisher.BuildBatch(r.Context(), seq); err != nil {
		slog.WarnContext(r.Context(), "catalog build deferred to worker", "seq", seq, "err", err)
	}
	return writeJSON(w, 200, map[string]any{"catalog_seq": seq})
}

func (c *Console) apiPutConfig(w http.ResponseWriter, r *http.Request) error {
	var v map[string]any
	if err := readJSON(r, &v); err != nil {
		return err
	}
	ctx := r.Context()
	b, _ := json.Marshal(v)
	var before []byte
	_ = c.Pool.QueryRow(ctx, `SELECT value FROM app_config WHERE key = 'app'`).Scan(&before)
	if _, err := c.Pool.Exec(ctx, `INSERT INTO app_config (key, value, updated_by) VALUES ('app', $1, $2)
		ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_by = EXCLUDED.updated_by, updated_at = now()`, b, principal(ctx).Email); err != nil {
		return err
	}
	if err := db.Audit(ctx, c.Pool, principal(ctx).Email, "update_app_config", "app_config", "app", json.RawMessage(nonNil(before)), v); err != nil {
		return err
	}
	if c.OnConfigChange != nil {
		c.OnConfigChange()
	}
	return writeJSON(w, 200, v)
}

func nonNil(b []byte) []byte {
	if b == nil {
		return []byte("null")
	}
	return b
}

func (c *Console) apiReports(w http.ResponseWriter, r *http.Request) error {
	rows, err := c.Pool.Query(r.Context(), `SELECT post_id, count(*), max(created_at) FROM match_report WHERE state = 'received'
		GROUP BY post_id ORDER BY count(*) DESC LIMIT 200`)
	if err != nil {
		return err
	}
	defer rows.Close()
	out := []map[string]any{}
	for rows.Next() {
		var post string
		var n int
		var last any
		if err := rows.Scan(&post, &n, &last); err != nil {
			return err
		}
		out = append(out, map[string]any{"post_id": post, "open_reports": n, "last_at": last})
	}
	return writeJSON(w, 200, map[string]any{"items": out})
}

// ---------------------------------------------------------------- pages (minimal server-rendered console)

//go:embed templates/*.html
var tplFS embed.FS

var tpl = template.Must(template.New("").Funcs(template.FuncMap{
	"json": func(v any) string {
		if raw, ok := v.(json.RawMessage); ok {
			var x any
			if json.Unmarshal(raw, &x) == nil {
				b, _ := json.MarshalIndent(x, "", "  ")
				return string(b)
			}
			return string(raw)
		}
		b, _ := json.MarshalIndent(v, "", "  ")
		return string(b)
	},
	"pathEscape": url.PathEscape,
}).ParseFS(tplFS, "templates/*.html"))

func render(w http.ResponseWriter, name string, data any) error {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	return tpl.ExecuteTemplate(w, name, data)
}

func (c *Console) pageIndex(w http.ResponseWriter, r *http.Request) error {
	ts, err := c.Curation.List(r.Context())
	if err != nil {
		return err
	}
	return render(w, "index.html", map[string]any{"Me": principal(r.Context()), "Tasks": ts, "Example": exampleDraft})
}

func (c *Console) pageTask(w http.ResponseWriter, r *http.Request) error {
	t, err := c.Curation.Get(r.Context(), r.PathValue("id"))
	if err != nil {
		return err
	}
	return render(w, "task.html", map[string]any{"Me": principal(r.Context()), "T": t, "Flash": r.URL.Query().Get("msg")})
}

func (c *Console) formCreateNotice(w http.ResponseWriter, r *http.Request) error {
	var d ingest.Draft
	if err := json.Unmarshal([]byte(r.FormValue("draft")), &d); err != nil {
		return problem.Validation("draft is not valid JSON: " + err.Error())
	}
	t, err := c.Curation.CreateDraft(r.Context(), principal(r.Context()).Email, d)
	if err != nil {
		return err
	}
	http.Redirect(w, r, "/admin/tasks/"+t.ID, http.StatusSeeOther)
	return nil
}

func (c *Console) formAttachRule(w http.ResponseWriter, r *http.Request) error {
	rep, err := c.Curation.AttachRule(r.Context(), principal(r.Context()).Email, r.PathValue("id"), r.PathValue("code"), []byte(r.FormValue("rule")))
	if err != nil {
		return err
	}
	msg := "rule attached: no blocking findings"
	if !rep.OK() {
		msg = "rule attached WITH BLOCKING FINDINGS: fix and re-attach"
	}
	http.Redirect(w, r, "/admin/tasks/"+r.PathValue("id")+"?msg="+url.QueryEscape(msg), http.StatusSeeOther)
	return nil
}

func (c *Console) formAction(w http.ResponseWriter, r *http.Request) error {
	id := r.PathValue("id")
	ctx := r.Context()
	var msg string
	switch r.PathValue("action") {
	case "approve":
		t, err := c.Curation.Approve(ctx, principal(ctx).Email, id)
		if err != nil {
			return err
		}
		msg = "approved: state " + t.State
	case "publish":
		if r.FormValue("confirm") != "PUBLISH" { // typed confirmation (security-privacy.md §5)
			return problem.Validation("type PUBLISH to confirm")
		}
		out, err := c.publish(ctx, id)
		if err != nil {
			return err
		}
		msg = "published in catalog batch " + jsonString(out["catalog_seq"])
	case "reject":
		if err := c.Curation.Reject(ctx, principal(ctx).Email, id, r.FormValue("reason")); err != nil {
			return err
		}
		msg = "rejected"
	default:
		return problem.New(404, problem.NotFound, "action")
	}
	http.Redirect(w, r, "/admin/tasks/"+id+"?msg="+url.QueryEscape(msg), http.StatusSeeOther)
	return nil
}

func jsonString(v any) string { b, _ := json.Marshal(v); return string(b) }

const exampleDraft = `{
  "kind": "original",
  "org_code": "ssc",
  "advt_no": "HQ-C-3301/1/2025",
  "exam_type": "ssc",
  "scope": "all_india",
  "state": null,
  "status": "open",
  "content": {
    "title": {"en": "SSC Constable (GD) Examination 2026", "hi": "SSC कांस्टेबल (GD) परीक्षा 2026"},
    "short_title": {"en": "SSC GD 2026", "hi": "SSC GD 2026"},
    "org": {"en": "Staff Selection Commission", "hi": "कर्मचारी चयन आयोग"},
    "key_dates": [{"kind": "apply_start", "date": "2025-12-01"}, {"kind": "apply_end", "date": "2025-12-31", "time": "23:00"}],
    "links": [{"kind": "official_pdf", "url": "https://ssc.gov.in"}],
    "source": {"url": "https://ssc.gov.in"}
  },
  "posts": [{"code": "CAPF-CT-GD", "name": {"en": "Constable (GD)", "hi": "कांस्टेबल (GD)"}, "exam_tags": ["ssc", "police", "defence"]}]
}`
