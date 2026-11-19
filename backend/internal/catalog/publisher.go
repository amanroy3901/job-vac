package catalog

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/blob"
	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Cache-Control values (api/README.md §6, security-privacy.md §1).
const (
	CacheImmutable = "public, max-age=31536000, immutable"
	CacheHead      = "public, max-age=60, stale-while-revalidate=300"
	// MaxHeadBatches bounds the batch list in head; older devices load the snapshot.
	MaxHeadBatches = 200
	// SnapshotEvery: a new snapshot is built once this many batches accumulated after the last one.
	SnapshotEvery = 50
)

// Valid batch reasons (catalog_batch.reason, CatalogHead.batches[].reason).
var Reasons = map[string]bool{"publish": true, "corrigendum": true, "extension": true, "cancel": true,
	"lifecycle": true, "retract": true, "translation": true}

// FileInfo is the per-locale-set digest stored in catalog_batch.files / catalog_snapshot.files.
type FileInfo struct {
	SHA256 string `json:"sha256"`
	Size   int    `json:"size"`
}

// Publisher writes catalog files to the blob store.
type Publisher struct {
	Pool  *pgxpool.Pool
	Store blob.Store
	Now   func() time.Time
}

func (p *Publisher) now() time.Time {
	if p.Now != nil {
		return p.Now()
	}
	return time.Now()
}

// Keys of catalog objects.
func BatchKey(seq int64, set string) string    { return fmt.Sprintf("catalog/batches/%d/%s.json", seq, set) }
func SnapshotKey(seq int64, set string) string { return fmt.Sprintf("catalog/snapshots/%d/%s.json", seq, set) }

const allSet = "all"

// CreateBatch records a batch inside the publishing transaction and enqueues its build. The files are written
// after commit by BuildBatch (synchronously by the admin publish handler, retried by the worker).
func CreateBatch(ctx context.Context, tx pgx.Tx, noticeIDs, removed []string, reason string) (int64, error) {
	if !Reasons[reason] {
		return 0, fmt.Errorf("bad batch reason %q", reason)
	}
	if noticeIDs == nil {
		noticeIDs = []string{}
	}
	if removed == nil {
		removed = []string{}
	}
	var seq int64
	if err := tx.QueryRow(ctx, `INSERT INTO catalog_batch (notice_ids, removed_notice_ids, reason, gcs_prefix)
		VALUES ($1, $2, $3, '') RETURNING seq`, noticeIDs, removed, reason).Scan(&seq); err != nil {
		return 0, err
	}
	if _, err := tx.Exec(ctx, `UPDATE catalog_batch SET gcs_prefix = $2 WHERE seq = $1`, seq,
		fmt.Sprintf("catalog/batches/%d/", seq)); err != nil {
		return 0, err
	}
	if _, err := tx.Exec(ctx, `UPDATE recruitment SET catalog_seq = $2 WHERE id = ANY($1)`,
		append(append([]string{}, noticeIDs...), removed...), seq); err != nil {
		return 0, err
	}
	return seq, db.EnqueueJob(ctx, tx, "catalog_build", map[string]int64{"seq": seq}, fmt.Sprintf("catalog_build:%d", seq), time.Time{})
}

// BuildBatch renders batch seq for every prebuilt locale set, stores the digests, marks it built and schedules
// the `catalog` sync topic push. Idempotent.
func (p *Publisher) BuildBatch(ctx context.Context, seq int64) error {
	var (
		noticeIDs, removed []string
		published          time.Time
		built              *time.Time
	)
	err := p.Pool.QueryRow(ctx, `SELECT notice_ids, removed_notice_ids, published_at, built_at FROM catalog_batch WHERE seq = $1`, seq).
		Scan(&noticeIDs, &removed, &published, &built)
	if err != nil {
		return fmt.Errorf("batch %d: %w", seq, err)
	}
	if built != nil {
		return nil
	}
	notices, err := LoadNotices(ctx, p.Pool, noticeIDs)
	if err != nil {
		return err
	}
	locales, err := LoadLocales(ctx, p.Pool)
	if err != nil {
		return err
	}
	doc := map[string]any{"seq": seq, "published_at": published.UTC().Format(time.RFC3339),
		"notices": toAny(notices), "removed_notice_ids": removed}
	files, err := p.render(ctx, doc, func(set string) string { return BatchKey(seq, set) }, PrebuiltSets(locales))
	if err != nil {
		return err
	}
	fb, _ := json.Marshal(files)
	return db.InTx(ctx, p.Pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `UPDATE catalog_batch SET files = $2, built_at = now() WHERE seq = $1 AND built_at IS NULL`, seq, fb); err != nil {
			return err
		}
		if err := push.ScheduleTopic(ctx, tx, push.SyncPush(seq, p.now())); err != nil {
			return err
		}
		return p.writeHead(ctx, tx)
	})
}

// render writes the unfiltered "all" document plus one file per locale set; returns digests per set.
func (p *Publisher) render(ctx context.Context, doc map[string]any, key func(string) string, sets []string) (map[string]FileInfo, error) {
	all, err := json.Marshal(doc)
	if err != nil {
		return nil, err
	}
	if err := p.Store.Put(ctx, key(allSet), all, blob.Meta{ContentType: "application/json", CacheControl: "private, no-store"}); err != nil {
		return nil, err
	}
	files := map[string]FileInfo{}
	for _, set := range sets {
		b, err := RenderSet(all, set)
		if err != nil {
			return nil, err
		}
		if err := p.Store.Put(ctx, key(set), b, blob.Meta{ContentType: "application/json", CacheControl: CacheImmutable}); err != nil {
			return nil, err
		}
		files[set] = FileInfo{SHA256: sha(b), Size: len(b)}
	}
	return files, nil
}

// RenderSet filters an unfiltered catalog document to one locale set and adds `locales`.
func RenderSet(all []byte, set string) ([]byte, error) {
	var doc map[string]any
	if err := json.Unmarshal(all, &doc); err != nil {
		return nil, err
	}
	out := FilterLocales(doc, SetMap(set)).(map[string]any)
	out["locales"] = strings.Split(set, ",")
	return json.Marshal(out)
}

// File returns the rendered file for (kind, seq, set), rendering and caching a non-prebuilt set from "all".
// kind is "batches" or "snapshots".
func (p *Publisher) File(ctx context.Context, kind string, seq int64, set string) ([]byte, error) {
	key := BatchKey
	if kind == "snapshots" {
		key = SnapshotKey
	}
	b, _, err := p.Store.Get(ctx, key(seq, set))
	if err == nil {
		return b, nil
	}
	if err != blob.ErrNotFound {
		return nil, err
	}
	all, _, err := p.Store.Get(ctx, key(seq, allSet))
	if err != nil {
		return nil, err
	}
	b, err = RenderSet(all, set)
	if err != nil {
		return nil, err
	}
	if err := p.Store.Put(ctx, key(seq, set), b, blob.Meta{ContentType: "application/json", CacheControl: CacheImmutable}); err != nil {
		slog.WarnContext(ctx, "cache rendered catalog file", "err", err)
	}
	return b, nil
}

// BuiltHead returns the highest seq such that every batch ≤ seq is built (the catalog is applied in order).
func BuiltHead(ctx context.Context, q db.Querier) (int64, error) {
	var seq int64
	err := q.QueryRow(ctx, `SELECT COALESCE(
		(SELECT min(seq) - 1 FROM catalog_batch WHERE built_at IS NULL),
		(SELECT max(seq) FROM catalog_batch), 0)`).Scan(&seq)
	return seq, err
}

// SnapshotDue reports whether a new snapshot should be built (none yet, or ≥ SnapshotEvery batches since, or
// older than maxAge).
func SnapshotDue(ctx context.Context, q db.Querier, maxAge time.Duration) (bool, int64, error) {
	head, err := BuiltHead(ctx, q)
	if err != nil || head == 0 {
		return false, head, err
	}
	var last int64
	var builtAt *time.Time
	if err := q.QueryRow(ctx, `SELECT COALESCE(max(seq),0), max(built_at) FROM catalog_snapshot`).Scan(&last, &builtAt); err != nil {
		return false, head, err
	}
	if last == head {
		return false, head, nil
	}
	return last == 0 || head-last >= SnapshotEvery || builtAt == nil || time.Since(*builtAt) > maxAge, head, nil
}

// BuildSnapshot renders a snapshot at the current built head: notices that are open or upcoming, closed or
// cancelled less than 90 days ago, or with lifecycle events in the last 12 months.
func (p *Publisher) BuildSnapshot(ctx context.Context) (int64, error) {
	seq, err := BuiltHead(ctx, p.Pool)
	if err != nil || seq == 0 {
		return 0, err
	}
	var exists bool
	if err := p.Pool.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM catalog_snapshot WHERE seq = $1)`, seq).Scan(&exists); err != nil || exists {
		return seq, err
	}
	rows, err := p.Pool.Query(ctx, `SELECT r.id FROM recruitment r WHERE r.current_version > 0 AND r.status <> 'draft' AND
		  r.catalog_seq <= $1 AND NOT EXISTS (SELECT 1 FROM catalog_batch b WHERE b.seq <= $1 AND b.seq >= r.catalog_seq AND r.id = ANY(b.removed_notice_ids)) AND (
		  r.status IN ('open','upcoming') OR r.apply_end >= (now() AT TIME ZONE 'Asia/Kolkata')::date - 90
		  OR EXISTS (SELECT 1 FROM lifecycle_event e WHERE e.recruitment_id = r.id AND e.published_at > now() - interval '12 months'))
		ORDER BY r.id`, seq)
	if err != nil {
		return 0, err
	}
	ids, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return 0, err
	}
	notices, err := LoadNotices(ctx, p.Pool, ids)
	if err != nil {
		return 0, err
	}
	locales, err := LoadLocales(ctx, p.Pool)
	if err != nil {
		return 0, err
	}
	doc := map[string]any{"seq": seq, "built_at": p.now().UTC().Format(time.RFC3339), "notices": toAny(notices)}
	files, err := p.render(ctx, doc, func(set string) string { return SnapshotKey(seq, set) }, PrebuiltSets(locales))
	if err != nil {
		return 0, err
	}
	fb, _ := json.Marshal(files)
	return seq, db.InTx(ctx, p.Pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `INSERT INTO catalog_snapshot (seq, gcs_prefix, notice_count, files) VALUES ($1,$2,$3,$4)
			ON CONFLICT (seq) DO NOTHING`, seq, fmt.Sprintf("catalog/snapshots/%d/", seq), len(notices), fb); err != nil {
			return err
		}
		return p.writeHead(ctx, tx)
	})
}

// writeHead stores catalog/head.json for CDN-only deployments (the API also serves head live from the DB).
func (p *Publisher) writeHead(ctx context.Context, q db.Querier) error {
	h, err := LoadHead(ctx, q, p.now())
	if err != nil {
		return err
	}
	b, _ := json.Marshal(h)
	return p.Store.Put(ctx, "catalog/head.json", b, blob.Meta{ContentType: "application/json", CacheControl: CacheHead})
}

// Head is the CatalogHead document.
type Head struct {
	Seq                    int64         `json:"seq"`
	ServerTime             string        `json:"server_time"`
	Snapshot               HeadSnapshot  `json:"snapshot"`
	Batches                []HeadBatch   `json:"batches"`
	EvaluatorMinVersionAny int           `json:"evaluator_min_version_any"`
}

// HeadSnapshot is CatalogHead.snapshot.
type HeadSnapshot struct {
	Seq       int64             `json:"seq"`
	URL       string            `json:"url"`
	SHA256    map[string]string `json:"sha256"`
	SizeBytes int               `json:"size_bytes,omitempty"`
}

// HeadBatch is one CatalogHead.batches entry.
type HeadBatch struct {
	Seq    int64             `json:"seq"`
	URL    string            `json:"url"`
	SHA256 map[string]string `json:"sha256"`
	Reason string            `json:"reason"`
}

// LoadHead computes the head from the database (only built batches, in order, after the latest snapshot).
func LoadHead(ctx context.Context, q db.Querier, now time.Time) (*Head, error) {
	head, err := BuiltHead(ctx, q)
	if err != nil {
		return nil, err
	}
	h := &Head{Seq: head, ServerTime: now.UTC().Format(time.RFC3339), Batches: []HeadBatch{},
		Snapshot: HeadSnapshot{SHA256: map[string]string{}}, EvaluatorMinVersionAny: 1}
	var snapFiles []byte
	err = q.QueryRow(ctx, `SELECT seq, files FROM catalog_snapshot WHERE seq <= $1 ORDER BY seq DESC LIMIT 1`, head).
		Scan(&h.Snapshot.Seq, &snapFiles)
	if err != nil && !db.IsNoRows(err) {
		return nil, err
	}
	if h.Snapshot.Seq > 0 {
		h.Snapshot.URL = fmt.Sprintf("/v1/catalog/snapshots/%d", h.Snapshot.Seq)
		h.Snapshot.SHA256, h.Snapshot.SizeBytes = digests(snapFiles, "en,hi")
	}
	rows, err := q.Query(ctx, `SELECT seq, reason, files FROM catalog_batch WHERE seq > $1 AND seq <= $2 ORDER BY seq LIMIT $3`,
		h.Snapshot.Seq, head, MaxHeadBatches)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var b HeadBatch
		var files []byte
		if err := rows.Scan(&b.Seq, &b.Reason, &files); err != nil {
			return nil, err
		}
		b.URL = fmt.Sprintf("/v1/catalog/batches/%d", b.Seq)
		b.SHA256, _ = digests(files, "")
		h.Batches = append(h.Batches, b)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	err = q.QueryRow(ctx, `SELECT COALESCE(max(pr.evaluator_min_version), 1) FROM recruitment r
		JOIN post p ON p.recruitment_id = r.id AND p.active
		JOIN post_rule pr ON pr.post_id = p.id AND pr.version = p.current_rule_version
		WHERE r.status IN ('open','upcoming')`).Scan(&h.EvaluatorMinVersionAny)
	return h, err
}

func digests(raw []byte, sizeSet string) (map[string]string, int) {
	var files map[string]FileInfo
	_ = json.Unmarshal(raw, &files)
	out := map[string]string{}
	size := 0
	for set, fi := range files {
		out[set] = fi.SHA256
		if set == sizeSet {
			size = fi.Size
		}
	}
	return out, size
}

func sha(b []byte) string {
	h := sha256.Sum256(b)
	return hex.EncodeToString(h[:])
}

func toAny(ns []Notice) []any {
	out := make([]any, len(ns))
	for i, n := range ns {
		out[i] = n
	}
	return out
}
