package db

import (
	"context"
	"encoding/json"
	"time"
)

// EnqueueJob inserts a durable job (ADR-04). A non-empty dedupeKey makes the insert idempotent.
func EnqueueJob(ctx context.Context, q Querier, kind string, payload any, dedupeKey string, runAt time.Time) error {
	b, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	var dk any
	if dedupeKey != "" {
		dk = dedupeKey
	}
	if runAt.IsZero() {
		runAt = time.Now()
	}
	_, err = q.Exec(ctx, `INSERT INTO job (kind, payload, dedupe_key, run_at) VALUES ($1,$2,$3,$4)
		ON CONFLICT (dedupe_key) DO NOTHING`, kind, b, dk, runAt)
	return err
}

// Audit appends an audit_log row.
func Audit(ctx context.Context, q Querier, actor, action, entity, entityID string, before, after any) error {
	var b, a []byte
	if before != nil {
		b, _ = json.Marshal(before)
	}
	if after != nil {
		a, _ = json.Marshal(after)
	}
	_, err := q.Exec(ctx, `INSERT INTO audit_log (actor, action, entity, entity_id, before, after)
		VALUES ($1,$2,NULLIF($3,''),NULLIF($4,''),$5,$6)`, actor, action, entity, entityID, b, a)
	return err
}
