package push

import (
	"context"
	"errors"
	"log/slog"
	"strings"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/jackc/pgx/v5/pgxpool"
)

// AccountPush is the payload of a job(kind=account_push): one data message to every valid device of a user.
type AccountPush struct {
	UserID   string            `json:"user_id"`
	Type     string            `json:"type"`     // account | family
	Template string            `json:"template"` // N-31, N-33, N-47, entitlement, family_accepted, family_removed ...
	Data     map[string]string `json:"data"`
	// ExceptInstallation skips one install (N-47 goes to the installs that lose primary).
	ExceptInstallation string `json:"except_installation,omitempty"`
	MsgID              string `json:"msg_id"`
}

// EnqueueAccountPush schedules an account push inside the caller's transaction (sent by the worker).
func EnqueueAccountPush(ctx context.Context, q db.Querier, p AccountPush) error {
	if p.MsgID == "" {
		p.MsgID = p.Template + ":" + p.UserID + ":" + time.Now().UTC().Format("20060102T150405.000")
	}
	return db.EnqueueJob(ctx, q, "account_push", p, "push:"+p.MsgID, time.Time{})
}

// AccountSender delivers AccountPush jobs.
type AccountSender struct {
	Pool   *pgxpool.Pool
	Sender Sender
}

// Deliver sends p to the user's devices. Dead tokens are marked token_invalid_at; any retryable failure
// returns an error so the job is retried.
func (a *AccountSender) Deliver(ctx context.Context, p AccountPush) error {
	rows, err := a.Pool.Query(ctx, `SELECT installation_id, fcm_token, locale FROM device
		WHERE user_id = $1 AND token_invalid_at IS NULL AND installation_id <> $2`, p.UserID, p.ExceptInstallation)
	if err != nil {
		return err
	}
	type dev struct{ id, token, locale string }
	var devs []dev
	for rows.Next() {
		var d dev
		if err := rows.Scan(&d.id, &d.token, &d.locale); err != nil {
			return err
		}
		devs = append(devs, d)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	var retry error
	for _, d := range devs {
		data := map[string]string{"type": p.Type, "msg_id": p.MsgID, "template": p.Template, "channel": p.Type}
		if p.Type == "family" {
			data["channel"] = "family"
		} else {
			data["channel"] = "account"
		}
		for k, v := range p.Data {
			data[k] = v
		}
		if title, body, ok := a.text(ctx, p.Template, d.locale); ok {
			for k, v := range p.Data {
				body = strings.ReplaceAll(body, "{{"+k+"}}", v)
			}
			data["fallback_title"], data["fallback_body"] = title, body
		}
		_, err := a.Sender.Send(ctx, Message{Token: d.token, Data: data, TTL: 7 * 24 * time.Hour})
		switch {
		case err == nil:
		case errors.Is(err, ErrUnregistered):
			_, _ = a.Pool.Exec(ctx, `UPDATE device SET token_invalid_at = now() WHERE installation_id = $1`, d.id)
		case errors.Is(err, ErrRetryable):
			retry = err
		default:
			slog.WarnContext(ctx, "account push dropped", "template", p.Template, "err", err)
		}
	}
	return retry
}

// text resolves notification_text in the device locale, walking locale.fallback, then en.
func (a *AccountSender) text(ctx context.Context, template, locale string) (string, string, bool) {
	var title, body string
	err := a.Pool.QueryRow(ctx, `
		WITH chain AS (
		  SELECT $2::text AS code, 0 AS ord
		  UNION ALL SELECT f, o::int FROM locale l, unnest(l.fallback) WITH ORDINALITY AS u(f, o) WHERE l.code = $2
		  UNION ALL SELECT 'en', 99)
		SELECT t.title, t.body FROM chain c JOIN notification_text t ON t.locale = c.code AND t.template = $1
		ORDER BY c.ord LIMIT 1`, template, locale).Scan(&title, &body)
	return title, body, err == nil
}
