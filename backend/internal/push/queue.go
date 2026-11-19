package push

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// IST is India Standard Time (all reminder slots are IST wall-clock times).
var IST = time.FixedZone("IST", 5*3600+1800)

// TopicSend is one row of topic_send.
type TopicSend struct {
	ID      string
	Topic   string
	Kind    string // PushData.type
	DueAt   time.Time
	Payload map[string]string
}

// ScheduleTopic inserts a topic_send row; the deterministic id makes it idempotent. A row that exists but was
// cancelled is re-queued with the new due time (e.g. an extension restored a cancelled reminder).
func ScheduleTopic(ctx context.Context, q db.Querier, s TopicSend) error {
	p, err := json.Marshal(s.Payload)
	if err != nil {
		return err
	}
	_, err = q.Exec(ctx, `INSERT INTO topic_send (id, topic, kind, due_at, payload) VALUES ($1,$2,$3,$4,$5)
		ON CONFLICT (id) DO UPDATE SET due_at = EXCLUDED.due_at, payload = EXCLUDED.payload, state = 'queued', attempts = 0
		WHERE topic_send.state = 'cancelled'`, s.ID, s.Topic, s.Kind, s.DueAt, p)
	return err
}

// CancelTopicPrefix cancels queued rows whose id starts with prefix except the ids in keep.
func CancelTopicPrefix(ctx context.Context, q db.Querier, prefix string, keep []string) error {
	if keep == nil {
		keep = []string{}
	}
	_, err := q.Exec(ctx, `UPDATE topic_send SET state = 'cancelled' WHERE state = 'queued' AND id LIKE $1 || '%'
		AND NOT (id = ANY($2))`, prefix, keep)
	return err
}

// SyncPush is the `catalog` topic message for a catalog batch (type=sync, normal priority, 24 h TTL).
func SyncPush(seq int64, now time.Time) TopicSend {
	id := fmt.Sprintf("sync:%d", seq)
	return TopicSend{ID: id, Topic: "catalog", Kind: "sync", DueAt: now,
		Payload: map[string]string{"type": "sync", "msg_id": id, "seq": fmt.Sprint(seq)}}
}

// ReminderSlots are the server topic-push times (AppConfig.reminder_backup defaults, IST).
type ReminderSlots struct {
	DMinus3, DMinus1, LastDay string // "19:00", "19:00", "08:00"
}

// DefaultSlots mirrors AppConfig.reminder_backup defaults.
var DefaultSlots = ReminderSlots{DMinus3: "19:00", DMinus1: "19:00", LastDay: "08:00"}

// DeadlinePushes returns the R3/R1/R-last topic pushes on p_{postID} for an apply_end date (IST), skipping slots
// already in the past. reminder_id = r:{match_id}:{kind}:{YYYYMMDD} (api/README.md §10).
func DeadlinePushes(postID, noticeID string, applyEnd time.Time, lastDateTime string, slots ReminderSlots, now time.Time) []TopicSend {
	matchID := "m_" + strings.TrimPrefix(postID, "p_")
	type slot struct {
		kind, typ, at, rank string
		days               int
	}
	ss := []slot{{"d_minus_3", "reminder", slots.DMinus3, "5", 3}, {"d_minus_1", "deadline", slots.DMinus1, "2", 1},
		{"last_day", "deadline", slots.LastDay, "1", 0}}
	var out []TopicSend
	for _, s := range ss {
		day := time.Date(applyEnd.Year(), applyEnd.Month(), applyEnd.Day(), 0, 0, 0, 0, IST).AddDate(0, 0, -s.days)
		hh, mm := 19, 0
		_, _ = fmt.Sscanf(s.at, "%d:%d", &hh, &mm)
		due := day.Add(time.Duration(hh)*time.Hour + time.Duration(mm)*time.Minute)
		if !due.After(now) {
			continue
		}
		date := day.Format("20060102")
		rid := fmt.Sprintf("r:%s:%s:%s", matchID, s.kind, date)
		p := map[string]string{"type": s.typ, "msg_id": rid, "reminder_id": rid, "kind": s.kind, "rank": s.rank,
			"post_id": postID, "notice_id": noticeID, "last_date": applyEnd.Format(time.DateOnly),
			"priority": map[bool]string{true: "high", false: "normal"}[s.typ == "deadline"],
			"channel":  map[bool]string{true: "critical", false: "reminders"}[s.typ == "deadline"],
			"collapse": postID, "link": "sarkarimatch://job/" + matchID + "?src=push"}
		if lastDateTime != "" {
			p["last_date_time"] = lastDateTime
		}
		out = append(out, TopicSend{ID: fmt.Sprintf("r:%s:%s:%s", postID, s.kind, date), Topic: "p_" + postID,
			Kind: s.typ, DueAt: due, Payload: p})
	}
	return out
}

// Dispatcher drains due topic_send rows (Cloud Scheduler → POST /internal/tick/topics every minute).
type Dispatcher struct {
	Pool   *pgxpool.Pool
	Sender Sender
	Now    func() time.Time
	// GiveUpAfter: retryable failures are retried with backoff until due_at + GiveUpAfter (default 2 h).
	GiveUpAfter time.Duration
}

// Tick sends up to limit due rows and returns how many were sent.
func (d *Dispatcher) Tick(ctx context.Context, limit int) (int, error) {
	now := time.Now
	if d.Now != nil {
		now = d.Now
	}
	giveUp := d.GiveUpAfter
	if giveUp == 0 {
		giveUp = 2 * time.Hour
	}
	sent := 0
	for i := 0; i < limit; i++ {
		done, err := d.one(ctx, now(), giveUp)
		if err != nil {
			return sent, err
		}
		if !done {
			break
		}
		sent++
	}
	return sent, nil
}

func (d *Dispatcher) one(ctx context.Context, now time.Time, giveUp time.Duration) (bool, error) {
	var found bool
	err := db.InTx(ctx, d.Pool, func(tx pgx.Tx) error {
		var (
			id, topic, kind string
			due             time.Time
			attempts        int
			payload         map[string]string
		)
		err := tx.QueryRow(ctx, `SELECT id, topic, kind, due_at, attempts, payload FROM topic_send
			WHERE state = 'queued' AND due_at <= $1 ORDER BY due_at LIMIT 1 FOR UPDATE SKIP LOCKED`, now).
			Scan(&id, &topic, &kind, &due, &attempts, &payload)
		if db.IsNoRows(err) {
			return nil
		}
		if err != nil {
			return err
		}
		found = true
		m := Message{Topic: topic, Data: payload, High: payload["priority"] == "high", Collapse: payload["collapse"]}
		switch kind {
		case "sync":
			m.TTL = 24 * time.Hour
		case "lifecycle":
			m.TTL = 28 * 24 * time.Hour
		default:
			m.TTL = 24 * time.Hour
		}
		name, sendErr := d.Sender.Send(ctx, m)
		switch {
		case sendErr == nil:
			_, err = tx.Exec(ctx, `UPDATE topic_send SET state='sent', sent_at=$2, fcm_message_id=$3, attempts=attempts+1 WHERE id=$1`, id, now, name)
		case errors.Is(sendErr, ErrRetryable) && now.Before(due.Add(giveUp)):
			backoff := time.Duration(1<<min(attempts, 6)) * 30 * time.Second
			_, err = tx.Exec(ctx, `UPDATE topic_send SET attempts=attempts+1, due_at=$2 WHERE id=$1`, id, now.Add(backoff))
			slog.WarnContext(ctx, "topic send retry", "id", id, "err", sendErr)
		default:
			_, err = tx.Exec(ctx, `UPDATE topic_send SET state='failed', attempts=attempts+1 WHERE id=$1`, id)
			slog.ErrorContext(ctx, "topic send failed", "id", id, "err", sendErr, "alert", true)
		}
		return err
	})
	return found, err
}
