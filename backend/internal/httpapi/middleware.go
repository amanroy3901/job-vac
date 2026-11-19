package httpapi

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"io"
	"math"
	"net/http"
	"regexp"
	"sync"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
)

var uuidRe = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

type storedResponse struct {
	ContentType string `json:"ct,omitempty"`
	ETag        string `json:"etag,omitempty"`
	Body        []byte `json:"b,omitempty"` // base64 in jsonb: replays must be byte-identical (jsonb normalises JSON)
}

// idempotent implements the Idempotency-Key contract (api/README.md §5): a replay within 24 h returns the stored
// response with Idempotent-Replayed: true; the same key with a different body → 422 idempotency_key_reused; a
// concurrent duplicate → 409 conflict (retryable). 5xx responses are not stored, so a retry re-executes.
func (s *Server) idempotent(w http.ResponseWriter, r *http.Request, h handler) error {
	key := r.Header.Get("Idempotency-Key")
	if !uuidRe.MatchString(key) {
		return problem.Validation("Idempotency-Key (UUID) header is required", problem.FieldError{Field: "Idempotency-Key", Code: "required"})
	}
	body, err := io.ReadAll(r.Body)
	if err != nil {
		return err
	}
	r.Body = io.NopCloser(bytes.NewReader(body))
	sum := sha256.Sum256(append([]byte(r.Method+" "+r.URL.Path+"\n"), body...))
	scope := "anon"
	if u := user(r.Context()); u != nil {
		scope = u.ID
	} else if id := identity(r.Context()); id != nil {
		scope = "fb:" + id.UID
	}
	ctx := r.Context()
	// Expired keys (> 24 h) are purged by housekeeping; treat them as absent here too.
	_, _ = s.d.Pool.Exec(ctx, `DELETE FROM idempotency_key WHERE user_id = $1 AND key = $2 AND created_at < now() - interval '24 hours'`, scope, key)
	tag, err := s.d.Pool.Exec(ctx, `INSERT INTO idempotency_key (user_id, key, request_hash) VALUES ($1,$2,$3) ON CONFLICT DO NOTHING`, scope, key, sum[:])
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		var hash []byte
		var status *int
		var resp []byte
		if err := s.d.Pool.QueryRow(ctx, `SELECT request_hash, status, response FROM idempotency_key WHERE user_id = $1 AND key = $2`, scope, key).
			Scan(&hash, &status, &resp); err != nil {
			if db.IsNoRows(err) {
				return problem.New(409, problem.Conflict, "retry")
			}
			return err
		}
		if !bytes.Equal(hash, sum[:]) {
			return problem.New(422, problem.IdempotencyKeyReused, "")
		}
		if status == nil {
			p := problem.New(409, problem.Conflict, "the original request is still in progress")
			p.Retryable, p.RetryAfter = true, 1
			return p
		}
		var sr storedResponse
		_ = json.Unmarshal(resp, &sr)
		if sr.ContentType != "" {
			w.Header().Set("Content-Type", sr.ContentType)
		}
		if sr.ETag != "" {
			w.Header().Set("ETag", sr.ETag)
		}
		w.Header().Set("Idempotent-Replayed", "true")
		w.WriteHeader(*status)
		_, _ = w.Write(sr.Body)
		return nil
	}
	rec := &recorder{ResponseWriter: w, status: 200}
	herr := h(rec, r)
	if herr != nil {
		// Render the problem through the recorder so 4xx outcomes are replayed too.
		s.writeErr(rec, r, herr)
	}
	if rec.status >= 500 {
		_, _ = s.d.Pool.Exec(ctx, `DELETE FROM idempotency_key WHERE user_id = $1 AND key = $2`, scope, key)
		return nil
	}
	sr := storedResponse{ContentType: rec.Header().Get("Content-Type"), ETag: rec.Header().Get("ETag")}
	sr.Body = rec.buf.Bytes()
	rb, _ := json.Marshal(sr)
	_, _ = s.d.Pool.Exec(ctx, `UPDATE idempotency_key SET status = $3, response = $4 WHERE user_id = $1 AND key = $2`, scope, key, rec.status, rb)
	return nil
}

type recorder struct {
	http.ResponseWriter
	status int
	buf    bytes.Buffer
}

func (r *recorder) WriteHeader(c int) { r.status = c; r.ResponseWriter.WriteHeader(c) }
func (r *recorder) Write(b []byte) (int, error) {
	r.buf.Write(b)
	return r.ResponseWriter.Write(b)
}

// ---------------------------------------------------------------- rate limiting (in-memory token buckets)

type rate struct {
	per   time.Duration
	n     float64
	burst float64
}

type bucket struct {
	tokens float64
	last   time.Time
}

type limiter struct {
	mu      sync.Mutex
	buckets map[string]*bucket
	now     func() time.Time
	sweep   time.Time
}

func newLimiter(now func() time.Time) *limiter {
	return &limiter{buckets: map[string]*bucket{}, now: now}
}

// allow takes one token; it returns 0 when allowed, else the wait until the next token.
func (l *limiter) allow(key string, r rate) time.Duration {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	if now.Sub(l.sweep) > 10*time.Minute {
		for k, b := range l.buckets {
			if now.Sub(b.last) > 24*time.Hour {
				delete(l.buckets, k)
			}
		}
		l.sweep = now
	}
	capacity := math.Max(r.burst, 1)
	b, ok := l.buckets[key]
	if !ok {
		b = &bucket{tokens: capacity, last: now}
		l.buckets[key] = b
	}
	perToken := r.per / time.Duration(math.Max(r.n, 1))
	b.tokens = math.Min(capacity, b.tokens+float64(now.Sub(b.last))/float64(perToken))
	b.last = now
	if b.tokens >= 1 {
		b.tokens--
		return 0
	}
	return time.Duration((1 - b.tokens) * float64(perToken))
}
