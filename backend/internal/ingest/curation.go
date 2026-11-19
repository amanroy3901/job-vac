// Package ingest holds the curation workflow (ingestion-pipeline.md §7–8): a reviewer creates a notice draft
// (manually or from extractor output), attaches one rule document per post, approves it (second reviewer for
// risk-flagged posts) and publishes it. Publishing writes immutable notice_version/post_rule rows, a catalog
// batch and the deadline topic pushes in one transaction.
//
// Source watchers, PDF text layer and LLM extraction (§2–5) are interfaces only for now (see Extractor).
package ingest

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"slices"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/catalog"
	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/ids"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/amanroy3901/job-vac/backend/internal/rules"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Extractor turns a source document into a draft (LLM structured output against rule.schema.json).
// TODO(P0 week 3): Claude API implementation with native PDF input + verify pass (ingestion-pipeline.md §5).
type Extractor interface {
	Extract(ctx context.Context, sourceDocumentID string) (*Draft, error)
}

// Draft is review_task.draft: the notice-level fields plus one entry per post.
type Draft struct {
	RecruitmentID string          `json:"recruitment_id,omitempty"` // set for corrigenda/extensions of a published notice
	Kind          string          `json:"kind"`                     // original|corrigendum|extension|edit|cancellation
	OrgCode       string          `json:"org_code"`
	AdvtNo        *string         `json:"advt_no"`
	ExamType      string          `json:"exam_type"`
	Scope         string          `json:"scope"` // all_india|state
	State         *string         `json:"state"`
	Status        string          `json:"status"` // upcoming|open|closed|cancelled
	Content       map[string]any  `json:"content"`
	Provenance    map[string]any  `json:"provenance"`
	ChangeSummary json.RawMessage `json:"change_summary,omitempty"`
	Posts         []PostDraft     `json:"posts"`
	Approvals     []string        `json:"approvals,omitempty"`
}

// PostDraft is one post in a draft.
type PostDraft struct {
	Code      string          `json:"code"`
	Name      map[string]any  `json:"name"`
	ShortName map[string]any  `json:"short_name,omitempty"`
	ExamTags  []string        `json:"exam_tags"`
	Vacancies map[string]any  `json:"vacancies,omitempty"`
	Active    *bool           `json:"active,omitempty"`
	Rule      json.RawMessage `json:"rule,omitempty"`
	Report    *rules.Report   `json:"report,omitempty"`
}

// Task is a review_task row.
type Task struct {
	ID                string    `json:"id"`
	RecruitmentID     string    `json:"recruitment_id"`
	State             string    `json:"state"`
	RiskFlags         []string  `json:"risk_flags"`
	NeedsSecondReview bool      `json:"needs_second_review"`
	Draft             Draft     `json:"draft"`
	OpenedAt          time.Time `json:"opened_at"`
}

// Service implements curation.
type Service struct {
	Pool  *pgxpool.Pool
	Slots push.ReminderSlots
	Now   func() time.Time
}

func (s *Service) now() time.Time {
	if s.Now != nil {
		return s.Now()
	}
	return time.Now()
}

// CreateDraft validates the notice-level fields and opens a review task. A new notice gets a recruitment row in
// state draft; a corrigendum references an existing published one.
func (s *Service) CreateDraft(ctx context.Context, actor string, d Draft) (*Task, error) {
	if d.Kind == "" {
		d.Kind = "original"
	}
	if err := validateNotice(&d); err != nil {
		return nil, err
	}
	taskID := ids.New("rt")
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		if d.RecruitmentID == "" {
			if d.Kind != "original" {
				return problem.Validation("recruitment_id is required for "+d.Kind, problem.FieldError{Field: "recruitment_id", Code: "required"})
			}
			d.RecruitmentID = ids.New("n")
			_, err := tx.Exec(ctx, `INSERT INTO recruitment (id, org_code, advt_no, exam_type, scope, state, status)
				VALUES ($1,$2,$3,$4,$5,$6,'draft')`, d.RecruitmentID, d.OrgCode, d.AdvtNo, d.ExamType, d.Scope, d.State)
			if db.IsUniqueViolation(err, "") {
				return problem.New(409, problem.Conflict, "a recruitment with this org_code and advt_no exists; create a corrigendum instead")
			}
			if err != nil {
				return err
			}
		} else {
			var cur int
			if err := tx.QueryRow(ctx, `SELECT current_version FROM recruitment WHERE id = $1`, d.RecruitmentID).Scan(&cur); err != nil {
				if db.IsNoRows(err) {
					return problem.New(404, problem.NotFound, "recruitment "+d.RecruitmentID)
				}
				return err
			}
		}
		b, _ := json.Marshal(d)
		if _, err := tx.Exec(ctx, `INSERT INTO review_task (id, recruitment_id, draft, state, assignee) VALUES ($1,$2,$3,'open',$4)`,
			taskID, d.RecruitmentID, b, actor); err != nil {
			return err
		}
		return db.Audit(ctx, tx, actor, "create_review_task", "review_task", taskID, nil, map[string]any{"recruitment_id": d.RecruitmentID})
	})
	if err != nil {
		return nil, err
	}
	return s.Get(ctx, taskID)
}

func validateNotice(d *Draft) error {
	var fe []problem.FieldError
	req := func(ok bool, field string) {
		if !ok {
			fe = append(fe, problem.FieldError{Field: field, Code: "required"})
		}
	}
	req(d.OrgCode != "", "org_code")
	req(d.ExamType != "", "exam_type")
	if d.Scope != "all_india" && d.Scope != "state" {
		fe = append(fe, problem.FieldError{Field: "scope", Code: "out_of_range"})
	}
	if d.Scope == "state" && (d.State == nil || *d.State == "") {
		fe = append(fe, problem.FieldError{Field: "state", Code: "required"})
	}
	if !slices.Contains([]string{"upcoming", "open", "closed", "cancelled"}, d.Status) {
		fe = append(fe, problem.FieldError{Field: "status", Code: "out_of_range"})
	}
	if !slices.Contains([]string{"original", "corrigendum", "extension", "edit", "cancellation"}, d.Kind) {
		fe = append(fe, problem.FieldError{Field: "kind", Code: "out_of_range"})
	}
	if d.Content == nil {
		d.Content = map[string]any{}
	}
	if t, ok := d.Content["title"].(map[string]any); !ok || !rules.IsLocalizedText(t) {
		fe = append(fe, problem.FieldError{Field: "content.title", Code: "required"})
	}
	src, _ := d.Content["source"].(map[string]any)
	if src == nil || src["url"] == nil {
		fe = append(fe, problem.FieldError{Field: "content.source.url", Code: "required"})
	} else if src["fetched_at"] == nil {
		src["fetched_at"] = time.Now().UTC().Format(time.RFC3339)
	}
	if d.Provenance == nil {
		d.Provenance = map[string]any{}
	}
	if len(d.Posts) == 0 {
		fe = append(fe, problem.FieldError{Field: "posts", Code: "required"})
	}
	codes := map[string]bool{}
	for i, p := range d.Posts {
		if p.Code == "" || codes[p.Code] {
			fe = append(fe, problem.FieldError{Field: fmt.Sprintf("posts[%d].code", i), Code: "invalid"})
		}
		codes[p.Code] = true
		if !rules.IsLocalizedText(p.Name) {
			fe = append(fe, problem.FieldError{Field: fmt.Sprintf("posts[%d].name", i), Code: "required"})
		}
		if len(p.ExamTags) == 0 {
			fe = append(fe, problem.FieldError{Field: fmt.Sprintf("posts[%d].exam_tags", i), Code: "required"})
		}
	}
	if len(fe) > 0 {
		return problem.Validation("notice draft is incomplete", fe...)
	}
	return nil
}

// Get loads a task.
func (s *Service) Get(ctx context.Context, id string) (*Task, error) {
	var t Task
	var rid *string
	var draft []byte
	err := s.Pool.QueryRow(ctx, `SELECT id, recruitment_id, state, risk_flags, needs_second_review, draft, opened_at
		FROM review_task WHERE id = $1`, id).Scan(&t.ID, &rid, &t.State, &t.RiskFlags, &t.NeedsSecondReview, &draft, &t.OpenedAt)
	if db.IsNoRows(err) {
		return nil, problem.New(404, problem.NotFound, "review task "+id)
	}
	if err != nil {
		return nil, err
	}
	if rid != nil {
		t.RecruitmentID = *rid
	}
	if err := json.Unmarshal(draft, &t.Draft); err != nil {
		return nil, err
	}
	return &t, nil
}

// List returns open tasks, most urgent first.
func (s *Service) List(ctx context.Context) ([]Task, error) {
	rows, err := s.Pool.Query(ctx, `SELECT id FROM review_task WHERE state NOT IN ('published','rejected') ORDER BY opened_at`)
	if err != nil {
		return nil, err
	}
	idsList, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return nil, err
	}
	out := []Task{}
	for _, id := range idsList {
		t, err := s.Get(ctx, id)
		if err != nil {
			return nil, err
		}
		out = append(out, *t)
	}
	return out, nil
}

// dbTaxonomy checks codes against the reference tables.
type dbTaxonomy struct {
	quals map[string]bool
	refs  map[string]bool
}

func (t dbTaxonomy) HasQualification(c string) bool { return t.quals[c] }
func (t dbTaxonomy) HasRef(kind, c string) bool     { return t.refs[kind+"/"+c] }

// LoadTaxonomy reads qualification and ref_item codes.
func LoadTaxonomy(ctx context.Context, q db.Querier) (rules.Taxonomy, error) {
	t := dbTaxonomy{quals: map[string]bool{}, refs: map[string]bool{}}
	rows, err := q.Query(ctx, `SELECT code FROM qualification`)
	if err != nil {
		return nil, err
	}
	qs, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return nil, err
	}
	for _, c := range qs {
		t.quals[c] = true
	}
	rows, err = q.Query(ctx, `SELECT kind || '/' || code FROM ref_item WHERE NOT deprecated`)
	if err != nil {
		return nil, err
	}
	rs, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return nil, err
	}
	for _, c := range rs {
		t.refs[c] = true
	}
	return t, nil
}

func (s *Service) validateRule(ctx context.Context, raw []byte) (rules.Report, error) {
	tax, err := LoadTaxonomy(ctx, s.Pool)
	if err != nil {
		return rules.Report{}, err
	}
	rows, err := s.Pool.Query(ctx, `SELECT code FROM locale WHERE status = 'launch'`)
	if err != nil {
		return rules.Report{}, err
	}
	launch, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return rules.Report{}, err
	}
	rep, _ := rules.Validate(raw, rules.Options{Taxonomy: tax, LaunchLocales: launch})
	return rep, nil
}

// ValidateRule runs the validators without storing anything (console "check" button).
func (s *Service) ValidateRule(ctx context.Context, raw []byte) (rules.Report, error) {
	return s.validateRule(ctx, raw)
}

// AttachRule validates and stores the rule document for one post of a task; the report is returned even when
// it has blocking findings (the reviewer fixes and re-attaches).
func (s *Service) AttachRule(ctx context.Context, actor, taskID, postCode string, raw []byte) (*rules.Report, error) {
	rep, err := s.validateRule(ctx, raw)
	if err != nil {
		return nil, err
	}
	err = db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		t, err := s.lockTask(ctx, tx, taskID)
		if err != nil {
			return err
		}
		if t.State == "published" || t.State == "rejected" {
			return problem.New(409, problem.Conflict, "task is "+t.State)
		}
		i := slices.IndexFunc(t.Draft.Posts, func(p PostDraft) bool { return p.Code == postCode })
		if i < 0 {
			return problem.New(404, problem.NotFound, "post code "+postCode)
		}
		t.Draft.Posts[i].Rule = json.RawMessage(raw)
		t.Draft.Posts[i].Report = &rep
		t.Draft.Approvals = nil // any edit invalidates approvals
		flags, second := aggregateFlags(t.Draft)
		b, _ := json.Marshal(t.Draft)
		_, err = tx.Exec(ctx, `UPDATE review_task SET draft = $2, risk_flags = $3, needs_second_review = $4, state = 'in_progress'
			WHERE id = $1`, taskID, b, flags, second)
		if err != nil {
			return err
		}
		return db.Audit(ctx, tx, actor, "attach_rule", "review_task", taskID, nil, map[string]any{"post": postCode, "ok": rep.OK()})
	})
	if err != nil {
		return nil, err
	}
	return &rep, nil
}

func aggregateFlags(d Draft) ([]string, bool) {
	set := map[string]bool{}
	second := false
	for _, p := range d.Posts {
		if p.Report == nil {
			continue
		}
		for _, f := range p.Report.RiskFlags {
			set[f] = true
		}
		second = second || p.Report.NeedsSecondReview
	}
	out := []string{}
	for f := range set {
		out = append(out, f)
	}
	slices.Sort(out)
	return out, second
}

func (s *Service) lockTask(ctx context.Context, tx pgx.Tx, id string) (*Task, error) {
	var t Task
	var draft []byte
	err := tx.QueryRow(ctx, `SELECT id, state, needs_second_review, draft FROM review_task WHERE id = $1 FOR UPDATE`, id).
		Scan(&t.ID, &t.State, &t.NeedsSecondReview, &draft)
	if db.IsNoRows(err) {
		return nil, problem.New(404, problem.NotFound, "review task "+id)
	}
	if err != nil {
		return nil, err
	}
	return &t, json.Unmarshal(draft, &t.Draft)
}

// Approve records a reviewer's approval. Every post needs a rule without blocking findings. Risk-flagged tasks
// need a second, different reviewer (matching-engine.md §7.4) and move through awaiting_second.
func (s *Service) Approve(ctx context.Context, reviewer, taskID string) (*Task, error) {
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		t, err := s.lockTask(ctx, tx, taskID)
		if err != nil {
			return err
		}
		if t.State == "published" || t.State == "rejected" || t.State == "approved" {
			return problem.New(409, problem.Conflict, "task is "+t.State)
		}
		var missing []problem.FieldError
		for i, p := range t.Draft.Posts {
			if len(p.Rule) == 0 || p.Report == nil {
				missing = append(missing, problem.FieldError{Field: fmt.Sprintf("posts[%d].rule", i), Code: "required"})
				continue
			}
			rep, err := s.validateRule(ctx, p.Rule) // re-run: taxonomy may have changed since attach
			if err != nil {
				return err
			}
			if !rep.OK() {
				missing = append(missing, problem.FieldError{Field: fmt.Sprintf("posts[%d].rule", i), Code: "blocking_findings"})
			}
		}
		if len(missing) > 0 {
			return problem.Validation("every post needs a rule without blocking findings", missing...)
		}
		if slices.Contains(t.Draft.Approvals, reviewer) {
			return problem.New(409, problem.Conflict, "the second review must be done by a different reviewer")
		}
		t.Draft.Approvals = append(t.Draft.Approvals, reviewer)
		state := "approved"
		if t.NeedsSecondReview && len(t.Draft.Approvals) < 2 {
			state = "awaiting_second"
		}
		b, _ := json.Marshal(t.Draft)
		if _, err := tx.Exec(ctx, `UPDATE review_task SET draft = $2, state = $3 WHERE id = $1`, taskID, b, state); err != nil {
			return err
		}
		return db.Audit(ctx, tx, reviewer, "approve_review_task", "review_task", taskID, nil, map[string]any{"state": state})
	})
	if err != nil {
		return nil, err
	}
	return s.Get(ctx, taskID)
}

// Reject closes a task without publishing.
func (s *Service) Reject(ctx context.Context, actor, taskID, reason string) error {
	return db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `UPDATE review_task SET state = 'rejected' WHERE id = $1 AND state NOT IN ('published','rejected')`, taskID)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problem.New(409, problem.Conflict, "task not open")
		}
		return db.Audit(ctx, tx, actor, "reject_review_task", "review_task", taskID, nil, map[string]any{"reason": reason})
	})
}

// ErrNotApproved is returned when publishing an unapproved task.
var ErrNotApproved = errors.New("task is not approved")

// Publish writes the next notice_version, posts and post_rule versions (only for changed rules), updates the
// recruitment, records a catalog batch and (re)schedules the R3/R1/R-last topic pushes. Returns the batch seq.
func (s *Service) Publish(ctx context.Context, actor, taskID string) (int64, error) {
	var seq int64
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		t, err := s.lockTask(ctx, tx, taskID)
		if err != nil {
			return err
		}
		if t.State != "approved" {
			return problem.New(409, problem.Conflict, "task must be approved before publish (state "+t.State+")")
		}
		d := t.Draft
		rid := d.RecruitmentID
		var curVersion int
		var oldStatus string
		if err := tx.QueryRow(ctx, `SELECT current_version, status::text FROM recruitment WHERE id = $1 FOR UPDATE`, rid).
			Scan(&curVersion, &oldStatus); err != nil {
			return err
		}
		version := curVersion + 1
		kind := d.Kind
		if curVersion == 0 {
			kind = "original"
		}
		contentB, _ := json.Marshal(d.Content)
		provB, _ := json.Marshal(d.Provenance)
		var cs any
		if len(d.ChangeSummary) > 0 {
			cs = []byte(d.ChangeSummary)
		}
		second := ""
		if len(d.Approvals) > 1 {
			second = d.Approvals[1]
		}
		var firstReviewer string
		if len(d.Approvals) > 0 {
			firstReviewer = d.Approvals[0]
		}
		if _, err := tx.Exec(ctx, `INSERT INTO notice_version (recruitment_id, version, kind, content, provenance, change_summary,
				reviewed_by, second_reviewer) VALUES ($1,$2,$3,$4,$5,$6,$7,NULLIF($8,''))`,
			rid, version, kind, contentB, provB, cs, firstReviewer, second); err != nil {
			return err
		}
		applyEnd, lastTime := applyEndOf(d.Content)
		status := d.Status
		if _, err := tx.Exec(ctx, `UPDATE recruitment SET current_version = $2, status = $3::notice_status_t, apply_end = $4,
				advt_no = $5, exam_type = $6, scope = $7, state = $8 WHERE id = $1`,
			rid, version, status, applyEnd, d.AdvtNo, d.ExamType, d.Scope, d.State); err != nil {
			return err
		}
		for _, p := range d.Posts {
			postID, err := s.upsertPost(ctx, tx, rid, version, actor, p)
			if err != nil {
				return err
			}
			active := p.Active == nil || *p.Active
			prefix := "r:" + postID + ":"
			if status == "open" && active && applyEnd != nil {
				pushes := push.DeadlinePushes(postID, rid, *applyEnd, lastTime, s.Slots, s.now())
				keep := make([]string, 0, len(pushes))
				for _, ps := range pushes {
					keep = append(keep, ps.ID)
					if err := push.ScheduleTopic(ctx, tx, ps); err != nil {
						return err
					}
				}
				if err := push.CancelTopicPrefix(ctx, tx, prefix, keep); err != nil {
					return err
				}
			} else if err := push.CancelTopicPrefix(ctx, tx, prefix, nil); err != nil {
				return err
			}
		}
		reason := map[string]string{"original": "publish", "corrigendum": "corrigendum", "extension": "extension",
			"edit": "publish", "cancellation": "cancel"}[kind]
		if seq, err = catalog.CreateBatch(ctx, tx, []string{rid}, nil, reason); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE review_task SET state = 'published', published_at = now() WHERE id = $1`, taskID); err != nil {
			return err
		}
		return db.Audit(ctx, tx, actor, "publish_notice", "recruitment", rid,
			map[string]any{"version": curVersion, "status": oldStatus}, map[string]any{"version": version, "status": status, "catalog_seq": seq})
	})
	return seq, err
}

func (s *Service) upsertPost(ctx context.Context, tx pgx.Tx, rid string, noticeVersion int, actor string, p PostDraft) (string, error) {
	name, _ := json.Marshal(p.Name)
	var short, vac any
	if p.ShortName != nil {
		short, _ = json.Marshal(p.ShortName)
	}
	if p.Vacancies != nil {
		vac, _ = json.Marshal(p.Vacancies)
	}
	active := p.Active == nil || *p.Active
	var postID string
	var curRule int
	err := tx.QueryRow(ctx, `SELECT id, current_rule_version FROM post WHERE recruitment_id = $1 AND code = $2`, rid, p.Code).Scan(&postID, &curRule)
	if db.IsNoRows(err) {
		postID = ids.New("p")
		_, err = tx.Exec(ctx, `INSERT INTO post (id, recruitment_id, code, name, short_name, exam_tags, vacancies, active)
			VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`, postID, rid, p.Code, name, short, p.ExamTags, vac, active)
	} else if err == nil {
		_, err = tx.Exec(ctx, `UPDATE post SET name=$2, short_name=$3, exam_tags=$4, vacancies=$5, active=$6 WHERE id=$1`,
			postID, name, short, p.ExamTags, vac, active)
	}
	if err != nil {
		return "", err
	}
	// New rule version only when the document changed (immutable versions; devices re-evaluate on change).
	if curRule > 0 {
		var same bool
		if err := tx.QueryRow(ctx, `SELECT rule = $3::jsonb FROM post_rule WHERE post_id = $1 AND version = $2`, postID, curRule, []byte(p.Rule)).Scan(&same); err != nil {
			return "", err
		}
		if same {
			return postID, nil
		}
	}
	rep := p.Report
	if rep == nil {
		rep = &rules.Report{EvaluatorMinVersion: 1}
	}
	pub, err := rules.PublicRule(p.Rule, rep.EvaluatorMinVersion)
	if err != nil {
		return "", err
	}
	flags := rep.RiskFlags
	if flags == nil {
		flags = []string{}
	}
	if _, err := tx.Exec(ctx, `INSERT INTO post_rule (post_id, version, notice_version, rule, public_rule, evaluator_min_version, risk_flags, published_by)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`, postID, curRule+1, noticeVersion, []byte(p.Rule), []byte(pub), rep.EvaluatorMinVersion, flags, actor); err != nil {
		return "", err
	}
	_, err = tx.Exec(ctx, `UPDATE post SET current_rule_version = $2 WHERE id = $1`, postID, curRule+1)
	return postID, err
}

// applyEndOf reads key_dates[kind=apply_end] (IST date, optional HH:MM time).
func applyEndOf(content map[string]any) (*time.Time, string) {
	kds, _ := content["key_dates"].([]any)
	for _, kd := range kds {
		m, _ := kd.(map[string]any)
		if m["kind"] != "apply_end" {
			continue
		}
		ds, _ := m["date"].(string)
		d, err := time.Parse(time.DateOnly, ds)
		if err != nil {
			return nil, ""
		}
		tm, _ := m["time"].(string)
		return &d, tm
	}
	return nil, ""
}

// Retract withdraws a published notice: every post becomes `retracted`, queued reminders are cancelled and a
// `retract` batch tells devices to withdraw their matches with a correction notification (matching-engine.md §7.6).
func (s *Service) Retract(ctx context.Context, actor, noticeID, reason string) (int64, error) {
	var seq int64
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `UPDATE recruitment SET status = 'cancelled' WHERE id = $1 AND current_version > 0`, noticeID)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problem.New(404, problem.NotFound, "published notice "+noticeID)
		}
		if _, err := tx.Exec(ctx, `UPDATE post SET active = false WHERE recruitment_id = $1`, noticeID); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE topic_send SET state = 'cancelled' WHERE state = 'queued' AND payload->>'notice_id' = $1`, noticeID); err != nil {
			return err
		}
		if seq, err = catalog.CreateBatch(ctx, tx, []string{noticeID}, nil, "retract"); err != nil {
			return err
		}
		return db.Audit(ctx, tx, actor, "retract_notice", "recruitment", noticeID, nil, map[string]any{"reason": reason, "catalog_seq": seq})
	})
	return seq, err
}

// RepublishNotice publishes the current state of a notice again (e.g. new_match_paused toggled by reports).
func RepublishNotice(ctx context.Context, pool *pgxpool.Pool, noticeID, reason string) (int64, error) {
	var seq int64
	err := db.InTx(ctx, pool, func(tx pgx.Tx) (err error) {
		seq, err = catalog.CreateBatch(ctx, tx, []string{noticeID}, nil, reason)
		return err
	})
	return seq, err
}
