// Package push sends data-only FCM HTTP v1 messages (api/README.md §10): topic pushes (`catalog` sync,
// `p_{post}` R3/R1/R-last backups, `n_{notice}` lifecycle) from the topic_send queue, and account pushes
// (`account`, `family`) to one account's devices. No WhatsApp, no SMS, no text in payloads except the
// fallback_title/body of account pushes.
package push

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

// Message is one FCM message. Exactly one of Token/Topic is set.
type Message struct {
	Token    string
	Topic    string
	Data     map[string]string
	High     bool
	TTL      time.Duration
	Collapse string
}

// Errors classified from FCM responses.
var (
	ErrUnregistered = errors.New("fcm: token unregistered")   // device token is dead → mark token_invalid_at
	ErrRetryable    = errors.New("fcm: retryable error")      // QUOTA_EXCEEDED / UNAVAILABLE / INTERNAL
	ErrInvalid      = errors.New("fcm: invalid message")      // INVALID_ARGUMENT etc.
)

// Sender sends one message and returns the FCM message name.
type Sender interface {
	Send(ctx context.Context, m Message) (string, error)
}

// FCM is the HTTP v1 sender (https://fcm.googleapis.com/v1/projects/{p}/messages:send).
type FCM struct {
	ProjectID string
	Client    *http.Client
	Endpoint  string // default https://fcm.googleapis.com
}

// NewFCM uses Application Default Credentials (the service account of the Cloud Run service).
func NewFCM(ctx context.Context, projectID string) (*FCM, error) {
	ts, err := google.DefaultTokenSource(ctx, "https://www.googleapis.com/auth/firebase.messaging")
	if err != nil {
		return nil, err
	}
	return &FCM{ProjectID: projectID, Client: oauth2.NewClient(ctx, ts)}, nil
}

// Send implements Sender.
func (f *FCM) Send(ctx context.Context, m Message) (string, error) {
	msg := map[string]any{"data": m.Data}
	if m.Token != "" {
		msg["token"] = m.Token
	} else {
		msg["topic"] = m.Topic
	}
	android := map[string]any{"priority": "NORMAL"}
	if m.High {
		android["priority"] = "HIGH"
	}
	if m.TTL > 0 {
		android["ttl"] = fmt.Sprintf("%ds", int(m.TTL.Seconds()))
	}
	if m.Collapse != "" {
		android["collapse_key"] = m.Collapse
	}
	msg["android"] = android
	body, _ := json.Marshal(map[string]any{"message": msg})
	ep := f.Endpoint
	if ep == "" {
		ep = "https://fcm.googleapis.com"
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		fmt.Sprintf("%s/v1/projects/%s/messages:send", strings.TrimRight(ep, "/"), f.ProjectID), bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := f.Client.Do(req)
	if err != nil {
		return "", fmt.Errorf("%w: %v", ErrRetryable, err)
	}
	defer resp.Body.Close()
	rb, _ := io.ReadAll(io.LimitReader(resp.Body, 8192))
	if resp.StatusCode == http.StatusOK {
		var out struct {
			Name string `json:"name"`
		}
		_ = json.Unmarshal(rb, &out)
		return out.Name, nil
	}
	return "", classify(resp.StatusCode, rb)
}

func classify(status int, body []byte) error {
	var e struct {
		Error struct {
			Status  string `json:"status"`
			Message string `json:"message"`
			Details []struct {
				ErrorCode string `json:"errorCode"`
			} `json:"details"`
		} `json:"error"`
	}
	_ = json.Unmarshal(body, &e)
	code := e.Error.Status
	for _, d := range e.Error.Details {
		if d.ErrorCode != "" {
			code = d.ErrorCode
		}
	}
	switch {
	case code == "UNREGISTERED" || status == http.StatusNotFound:
		return fmt.Errorf("%w (%s)", ErrUnregistered, code)
	case status == 429 || status >= 500 || code == "QUOTA_EXCEEDED" || code == "UNAVAILABLE" || code == "INTERNAL":
		return fmt.Errorf("%w (%d %s)", ErrRetryable, status, code)
	default:
		return fmt.Errorf("%w (%d %s: %s)", ErrInvalid, status, code, e.Error.Message)
	}
}

// LogSender logs instead of sending (FCM_MODE=log, local development).
type LogSender struct{}

// Send implements Sender.
func (LogSender) Send(ctx context.Context, m Message) (string, error) {
	slog.InfoContext(ctx, "push (log only)", "topic", m.Topic, "token_set", m.Token != "", "type", m.Data["type"],
		"msg_id", m.Data["msg_id"], "high", m.High)
	return "log/" + m.Data["msg_id"], nil
}

// Recorder records messages (tests). Fail, when set, decides the error per message.
type Recorder struct {
	mu   sync.Mutex
	Msgs []Message
	Fail func(Message) error
}

// Send implements Sender.
func (r *Recorder) Send(_ context.Context, m Message) (string, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.Fail != nil {
		if err := r.Fail(m); err != nil {
			return "", err
		}
	}
	r.Msgs = append(r.Msgs, m)
	return fmt.Sprintf("projects/test/messages/%d", len(r.Msgs)), nil
}

// Sent returns a copy of the recorded messages.
func (r *Recorder) Sent() []Message {
	r.mu.Lock()
	defer r.mu.Unlock()
	return append([]Message(nil), r.Msgs...)
}
