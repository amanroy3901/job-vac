// Package catalog builds the public, versioned rule catalog (matching-engine.md §4): immutable batches (one per
// publish) and snapshots of every relevant notice, rendered per locale set to the blob store (GCS behind the CDN),
// plus the head document that lists them. Devices evaluate the catalog locally; it holds no user data.
package catalog

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/db"
)

// Notice is the API Notice shape (openapi components.schemas.Notice) as a JSON object.
type Notice = map[string]any

// LoadNotices assembles the current published version of each notice id (missing/draft ids are skipped).
func LoadNotices(ctx context.Context, q db.Querier, ids []string) ([]Notice, error) {
	out := make([]Notice, 0, len(ids))
	for _, id := range ids {
		n, err := LoadNotice(ctx, q, id)
		if err != nil {
			return nil, err
		}
		if n != nil {
			out = append(out, n)
		}
	}
	return out, nil
}

// LoadNotice returns nil, nil when the notice is unknown or was never published.
func LoadNotice(ctx context.Context, q db.Querier, id string) (Notice, error) {
	var (
		orgCode, examType, scope, status string
		advtNo, state                    *string
		version                          int
		seq                              *int64
		paused                           bool
		content                          []byte
		publishedAt                      time.Time
	)
	err := q.QueryRow(ctx, `SELECT r.org_code, r.advt_no, r.exam_type, r.scope, r.state, r.status::text, r.current_version,
			r.catalog_seq, r.new_match_paused, nv.content, nv.published_at
		FROM recruitment r JOIN notice_version nv ON nv.recruitment_id = r.id AND nv.version = r.current_version
		WHERE r.id = $1 AND r.current_version > 0`, id).
		Scan(&orgCode, &advtNo, &examType, &scope, &state, &status, &version, &seq, &paused, &content, &publishedAt)
	if db.IsNoRows(err) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("load notice %s: %w", id, err)
	}
	n := Notice{}
	if err := json.Unmarshal(content, &n); err != nil {
		return nil, err
	}
	n["id"] = id
	n["version"] = version
	if seq != nil {
		n["catalog_seq"] = *seq
	}
	n["new_match_paused"] = paused
	n["advt_no"] = advtNo
	n["exam_type"] = examType
	n["scope"] = scope
	n["state"] = state
	n["status"] = status
	n["updated_at"] = publishedAt.UTC().Format(time.RFC3339)
	if _, ok := n["org"]; !ok {
		n["org"] = map[string]any{"en": orgCode}
	}
	src, _ := n["source"].(map[string]any)
	if src == nil {
		src = map[string]any{}
	}
	src["reviewed_at"] = publishedAt.UTC().Format(time.RFC3339)
	n["source"] = src
	for _, k := range []string{"key_dates", "links"} {
		if _, ok := n[k]; !ok {
			n[k] = []any{}
		}
	}
	posts, err := loadPosts(ctx, q, id)
	if err != nil {
		return nil, err
	}
	n["posts"] = posts
	corr, err := loadCorrigenda(ctx, q, id)
	if err != nil {
		return nil, err
	}
	n["corrigenda"] = corr
	life, err := loadLifecycle(ctx, q, id)
	if err != nil {
		return nil, err
	}
	n["lifecycle"] = life
	return n, nil
}

func loadPosts(ctx context.Context, q db.Querier, noticeID string) ([]any, error) {
	rows, err := q.Query(ctx, `SELECT p.id, p.name, p.short_name, p.exam_tags, p.vacancies, p.current_rule_version, p.active,
			pr.public_rule, pr.evaluator_min_version
		FROM post p JOIN post_rule pr ON pr.post_id = p.id AND pr.version = p.current_rule_version
		WHERE p.recruitment_id = $1 ORDER BY p.code`, noticeID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []any{}
	for rows.Next() {
		var (
			pid                   string
			name, short, vac, pub []byte
			tags                  []string
			ruleVersion, evMin    int
			active                bool
		)
		if err := rows.Scan(&pid, &name, &short, &tags, &vac, &ruleVersion, &active, &pub, &evMin); err != nil {
			return nil, err
		}
		p := map[string]any{"id": pid, "exam_tags": tags, "rule_version": ruleVersion,
			"status": map[bool]string{true: "active", false: "retracted"}[active]}
		var rule map[string]any
		_ = json.Unmarshal(pub, &rule)
		p["rule"] = rule
		p["name"] = jsonOrNil(name)
		if s := jsonOrNil(short); s != nil {
			p["short_name"] = s
		}
		if v, ok := jsonOrNil(vac).(map[string]any); ok {
			if t, ok := v["total"]; ok {
				p["vacancies_total"] = t
			}
			if bc, ok := v["by_category"]; ok {
				p["vacancies_by_category"] = bc
			}
		}
		if post, ok := rule["post"].(map[string]any); ok {
			if pl, ok := post["pay_level"]; ok {
				p["pay_level"] = pl
			}
		}
		if age, ok := rule["age"].(map[string]any); ok {
			p["age_cutoff_date"] = age["cutoff_date"]
			if lims, ok := age["limits"].([]any); ok && len(lims) > 0 {
				def := lims[0].(map[string]any)
				for _, l := range lims {
					if m := l.(map[string]any); m["when"] == nil {
						def = m
						break
					}
				}
				p["age_min"], p["age_max"] = def["min_years"], def["max_years"]
			}
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

func loadCorrigenda(ctx context.Context, q db.Querier, noticeID string) ([]any, error) {
	rows, err := q.Query(ctx, `SELECT version, published_at, change_summary FROM notice_version
		WHERE recruitment_id = $1 AND kind IN ('corrigendum','extension','cancellation') ORDER BY version`, noticeID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []any{}
	for rows.Next() {
		var (
			v   int
			at  time.Time
			raw []byte
		)
		if err := rows.Scan(&v, &at, &raw); err != nil {
			return nil, err
		}
		summary := any(map[string]any{"en": fmt.Sprintf("Update %d", v)})
		var cs any
		_ = json.Unmarshal(raw, &cs)
		switch t := cs.(type) {
		case map[string]any:
			if s, ok := t["summary"]; ok {
				summary = s
			}
		case []any:
			if len(t) > 0 {
				if m, ok := t[0].(map[string]any); ok && m["text"] != nil {
					summary = m["text"]
				}
			}
		}
		out = append(out, map[string]any{"id": fmt.Sprintf("%s:v%d", noticeID, v),
			"published_on": at.In(ist).Format(time.DateOnly), "summary": summary})
	}
	return out, rows.Err()
}

var ist = time.FixedZone("IST", 5*3600+1800)

func loadLifecycle(ctx context.Context, q db.Querier, noticeID string) ([]any, error) {
	rows, err := q.Query(ctx, `SELECT id, type, stage, title, body, status, starts_at, ends_at, expected_window, links,
			published_at, critical, post_ids, regions
		FROM lifecycle_event WHERE recruitment_id = $1 AND published_at IS NOT NULL ORDER BY starts_at NULLS LAST, id`, noticeID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []any{}
	for rows.Next() {
		var (
			id, typ, status         string
			stage, window           *string
			title, body, links      []byte
			starts, ends, published *time.Time
			critical                bool
			postIDs, regions        []string
		)
		if err := rows.Scan(&id, &typ, &stage, &title, &body, &status, &starts, &ends, &window, &links, &published,
			&critical, &postIDs, &regions); err != nil {
			return nil, err
		}
		e := map[string]any{"id": id, "type": typ, "stage": stage, "title": jsonOrNil(title), "status": status,
			"starts_at": ts(starts), "ends_at": ts(ends), "expected_window": window, "links": jsonOrNil(links),
			"published_at": ts(published), "critical": critical, "post_ids": nilIfEmpty(postIDs), "regions": nilIfEmpty(regions)}
		if b := jsonOrNil(body); b != nil {
			e["body"] = b
		}
		out = append(out, e)
	}
	return out, rows.Err()
}

func ts(t *time.Time) any {
	if t == nil {
		return nil
	}
	return t.UTC().Format(time.RFC3339)
}

func nilIfEmpty(s []string) any {
	if s == nil {
		return nil
	}
	return s
}

func jsonOrNil(b []byte) any {
	if len(b) == 0 {
		return nil
	}
	var v any
	if err := json.Unmarshal(b, &v); err != nil {
		return nil
	}
	return v
}
