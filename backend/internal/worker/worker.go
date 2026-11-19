// Package worker runs the tick handlers (ADR-04): Cloud Scheduler calls POST /internal/tick/{name} every minute
// in prod; locally (ROLE=all|worker, APP_ENV=local) Run drives the same ticks on a timer.
//
// Ticks: topics (topic_send → FCM), jobs (the durable job table: catalog builds, account pushes, delayed
// self-tests, Play re-acknowledgement, republish), housekeep (hard deletes, entitlement expiry, idempotency and
// self-test cleanup, stale tokens, nightly snapshot).
package worker

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/account"
	"github.com/amanroy3901/job-vac/backend/internal/billing"
	"github.com/amanroy3901/job-vac/backend/internal/catalog"
	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/ingest"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/amanroy3901/job-vac/backend/internal/seal"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// MaxAttempts before a job is marked dead (alert).
const MaxAttempts = 10

// Worker owns background processing.
type Worker struct {
	Pool      *pgxpool.Pool
	Publisher *catalog.Publisher
	Topics    *push.Dispatcher
	Accounts  *account.Service
	AccountTx *push.AccountSender
	Billing   *billing.Service
	Sender    push.Sender
	Seal      *seal.Sealer
	Now       func() time.Time
}

func (w *Worker) now() time.Time {
	if w.Now != nil {
		return w.Now()
	}
	return time.Now()
}

// Tick runs one named tick and returns counters for the response/logs.
func (w *Worker) Tick(ctx context.Context, name string) (map[string]any, error) {
	switch name {
	case "topics":
		n, err := w.Topics.Tick(ctx, 500)
		return map[string]any{"sent": n}, err
	case "jobs":
		n, err := w.RunJobs(ctx, 100)
		return map[string]any{"jobs": n}, err
	case "housekeep":
		return w.Housekeep(ctx)
	case "all":
		out := map[string]any{}
		for _, t := range []string{"jobs", "topics"} {
			r, err := w.Tick(ctx, t)
			if err != nil {
				return out, err
			}
			out[t] = r
		}
		return out, nil
	}
	return nil, problem.New(404, problem.NotFound, "unknown tick "+name)
}

// Run drives ticks locally until ctx ends (prod uses Cloud Scheduler instead).
func (w *Worker) Run(ctx context.Context, every time.Duration) {
	t := time.NewTicker(every)
	defer t.Stop()
	lastHousekeep := time.Time{}
	for {
		if _, err := w.Tick(ctx, "all"); err != nil && ctx.Err() == nil {
			slog.ErrorContext(ctx, "tick failed", "err", err)
		}
		if time.Since(lastHousekeep) > 10*time.Minute {
			if _, err := w.Housekeep(ctx); err != nil && ctx.Err() == nil {
				slog.ErrorContext(ctx, "housekeep failed", "err", err)
			}
			lastHousekeep = time.Now()
		}
		select {
		case <-ctx.Done():
			return
		case <-t.C:
		}
	}
}

type job struct {
	ID       int64
	Kind     string
	Payload  []byte
	Attempts int
}

// RunJobs claims and executes up to limit due jobs (lease-based; a crashed worker's lease expires after 5 min).
func (w *Worker) RunJobs(ctx context.Context, limit int) (int, error) {
	n := 0
	for ; n < limit; n++ {
		var j job
		err := w.Pool.QueryRow(ctx, `UPDATE job SET state = 'running', attempts = attempts + 1, lease_until = now() + interval '5 minutes'
			WHERE id = (SELECT id FROM job WHERE (state = 'queued' AND run_at <= $1) OR (state = 'running' AND lease_until < now())
			            ORDER BY run_at LIMIT 1 FOR UPDATE SKIP LOCKED)
			RETURNING id, kind, payload, attempts`, w.now()).Scan(&j.ID, &j.Kind, &j.Payload, &j.Attempts)
		if db.IsNoRows(err) {
			return n, nil
		}
		if err != nil {
			return n, err
		}
		runErr := w.execute(ctx, j)
		switch {
		case runErr == nil:
			_, err = w.Pool.Exec(ctx, `UPDATE job SET state = 'done', lease_until = NULL, last_error = NULL WHERE id = $1`, j.ID)
		case j.Attempts >= MaxAttempts:
			slog.ErrorContext(ctx, "job dead", "kind", j.Kind, "id", j.ID, "err", runErr, "alert", true)
			_, err = w.Pool.Exec(ctx, `UPDATE job SET state = 'dead', lease_until = NULL, last_error = $2 WHERE id = $1`, j.ID, runErr.Error())
		default:
			backoff := time.Duration(1<<min(j.Attempts, 8)) * 15 * time.Second
			slog.WarnContext(ctx, "job failed, retrying", "kind", j.Kind, "id", j.ID, "attempt", j.Attempts, "err", runErr)
			_, err = w.Pool.Exec(ctx, `UPDATE job SET state = 'queued', lease_until = NULL, last_error = $2, run_at = $3 WHERE id = $1`,
				j.ID, runErr.Error(), w.now().Add(backoff))
		}
		if err != nil {
			return n, err
		}
	}
	return n, nil
}

func (w *Worker) execute(ctx context.Context, j job) error {
	switch j.Kind {
	case "catalog_build":
		var p struct {
			Seq int64 `json:"seq"`
		}
		if err := json.Unmarshal(j.Payload, &p); err != nil {
			return err
		}
		if err := w.Publisher.BuildBatch(ctx, p.Seq); err != nil {
			return err
		}
		if due, _, err := catalog.SnapshotDue(ctx, w.Pool, 24*time.Hour); err == nil && due {
			_, err = w.Publisher.BuildSnapshot(ctx)
			return err
		}
		return nil
	case "account_push":
		var p push.AccountPush
		if err := json.Unmarshal(j.Payload, &p); err != nil {
			return err
		}
		return w.AccountTx.Deliver(ctx, p)
	case "republish_notice":
		var p struct {
			NoticeID string `json:"notice_id"`
			Reason   string `json:"reason"`
		}
		if err := json.Unmarshal(j.Payload, &p); err != nil {
			return err
		}
		seq, err := ingest.RepublishNotice(ctx, w.Pool, p.NoticeID, p.Reason)
		if err != nil {
			return err
		}
		return w.Publisher.BuildBatch(ctx, seq)
	case "push_test_send":
		var p struct {
			TestID string `json:"test_id"`
		}
		if err := json.Unmarshal(j.Payload, &p); err != nil {
			return err
		}
		var enc []byte
		err := w.Pool.QueryRow(ctx, `SELECT fcm_token_enc FROM push_test WHERE id = $1 AND status = 'sent'`, p.TestID).Scan(&enc)
		if db.IsNoRows(err) || (err == nil && enc == nil) {
			return nil
		}
		if err != nil {
			return err
		}
		tok, err := w.Seal.Open(enc)
		if err != nil {
			return err
		}
		_, _ = w.Pool.Exec(ctx, `UPDATE push_test SET sent_at = now() WHERE id = $1`, p.TestID) // killed_app: clock starts at send
		_ = push.SendSelfTest(ctx, w.Pool, w.Sender, string(tok), p.TestID, 120*time.Second)
		return nil
	case "play_ack":
		var p struct {
			TokenHash string `json:"token_hash"`
		}
		if err := json.Unmarshal(j.Payload, &p); err != nil {
			return err
		}
		hash, err := base64.StdEncoding.DecodeString(p.TokenHash)
		if err != nil {
			return err
		}
		var enc []byte
		var playProduct string
		err = w.Pool.QueryRow(ctx, `SELECT p.token_enc, pr.play_product_id FROM purchase p JOIN product pr ON pr.id = p.product_id
			WHERE p.token_hash = $1 AND p.acknowledged_at IS NULL`, hash).Scan(&enc, &playProduct)
		if db.IsNoRows(err) {
			return nil
		}
		if err != nil {
			return err
		}
		tok, err := w.Seal.Open(enc)
		if err != nil {
			return err
		}
		if err := w.Billing.Play.Acknowledge(ctx, w.Billing.PackageName, playProduct, string(tok)); err != nil {
			return err
		}
		_, err = w.Pool.Exec(ctx, `UPDATE purchase SET acknowledged_at = now() WHERE token_hash = $1`, hash)
		return err
	case "noop":
		return nil
	}
	return fmt.Errorf("unknown job kind %q", j.Kind)
}

// Housekeep runs the periodic maintenance (call every few minutes; each step is idempotent).
func (w *Worker) Housekeep(ctx context.Context) (map[string]any, error) {
	out := map[string]any{}
	n, err := w.Accounts.HardDeleteDue(ctx)
	if err != nil {
		return out, err
	}
	out["hard_deleted"] = n
	// Entitlements that passed expires_at: recompute (sends the `entitlement` account push).
	rows, err := w.Pool.Query(ctx, `SELECT user_id FROM user_entitlement WHERE plan = 'pro' AND expires_at <= $1 LIMIT 500`, w.now())
	if err != nil {
		return out, err
	}
	expired, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return out, err
	}
	for _, uid := range expired {
		if err := db.InTx(ctx, w.Pool, func(tx pgx.Tx) error {
			_, err := w.Billing.Recompute(ctx, tx, uid)
			return err
		}); err != nil {
			return out, err
		}
	}
	out["entitlements_expired"] = len(expired)
	steps := map[string]string{
		"idempotency_purged":   `DELETE FROM idempotency_key WHERE created_at < now() - interval '24 hours'`,
		"push_tests_timed_out": `UPDATE push_test SET status = 'timeout', fcm_token_enc = NULL WHERE status = 'sent' AND sent_at < now() - interval '120 seconds'`,
		"push_tests_purged":    `DELETE FROM push_test WHERE sent_at < now() - interval '1 day'`,
		"stale_devices":        `DELETE FROM device WHERE token_invalid_at < now() - interval '60 days'`,
		"jobs_purged":          `DELETE FROM job WHERE state = 'done' AND created_at < now() - interval '14 days'`,
		"reports_scrubbed":     `UPDATE match_report SET attributes = NULL WHERE attributes IS NOT NULL AND created_at < now() - interval '12 months'`,
		"invites_expired":      `UPDATE family_link SET state = 'expired', ended_at = now() WHERE state = 'invited' AND invite_expires_at < now()`,
	}
	for k, q := range steps {
		tag, err := w.Pool.Exec(ctx, q)
		if err != nil {
			return out, fmt.Errorf("%s: %w", k, err)
		}
		out[k] = tag.RowsAffected()
	}
	// Builds that failed synchronously are retried by catalog_build jobs; the snapshot is rebuilt nightly.
	if due, _, err := catalog.SnapshotDue(ctx, w.Pool, 24*time.Hour); err != nil {
		return out, err
	} else if due {
		seq, err := w.Publisher.BuildSnapshot(ctx)
		if err != nil {
			return out, err
		}
		out["snapshot_seq"] = seq
	}
	return out, nil
}
