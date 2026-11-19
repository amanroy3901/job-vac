package push

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestDeadlinePushes(t *testing.T) {
	end := time.Date(2026, 7, 31, 0, 0, 0, 0, time.UTC)
	now := time.Date(2026, 7, 28, 20, 0, 0, 0, IST) // after the D-3 slot (19:00 on 28th)
	got := DeadlinePushes("p_01ABC", "n_01X", end, "23:00", DefaultSlots, now)
	if len(got) != 2 {
		t.Fatalf("want R1 + R-last only, got %d", len(got))
	}
	r1 := got[0]
	if r1.ID != "r:p_01ABC:d_minus_1:20260730" {
		t.Fatalf("id %q", r1.ID)
	}
	if r1.Payload["reminder_id"] != "r:m_01ABC:d_minus_1:20260730" || r1.Payload["type"] != "deadline" || r1.Payload["priority"] != "high" {
		t.Fatalf("r1 = %+v", r1)
	}
	if want := time.Date(2026, 7, 30, 19, 0, 0, 0, IST); !r1.DueAt.Equal(want) {
		t.Fatalf("due %s want %s", r1.DueAt, want)
	}
	last := got[1]
	if last.Payload["kind"] != "last_day" || !last.DueAt.Equal(time.Date(2026, 7, 31, 8, 0, 0, 0, IST)) || last.Payload["rank"] != "1" {
		t.Fatalf("last = %+v", last)
	}
	if last.Topic != "p_p_01ABC" {
		t.Fatalf("topic %q", last.Topic)
	}
}

func TestFCMSendAndClassify(t *testing.T) {
	var got map[string]any
	status := 200
	resp := `{"name":"projects/p/messages/1"}`
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/projects/p/messages:send" {
			http.NotFound(w, r)
			return
		}
		b, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(b, &got)
		w.WriteHeader(status)
		_, _ = w.Write([]byte(resp))
	}))
	defer srv.Close()
	f := &FCM{ProjectID: "p", Client: srv.Client(), Endpoint: srv.URL}
	name, err := f.Send(context.Background(), Message{Topic: "catalog", Data: map[string]string{"type": "sync"}, TTL: time.Hour})
	if err != nil || name != "projects/p/messages/1" {
		t.Fatalf("%s %v", name, err)
	}
	msg := got["message"].(map[string]any)
	if msg["topic"] != "catalog" || msg["android"].(map[string]any)["ttl"] != "3600s" || msg["notification"] != nil {
		t.Fatalf("payload %+v", msg)
	}
	status, resp = 404, `{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}`
	if _, err := f.Send(context.Background(), Message{Token: "t"}); !errors.Is(err, ErrUnregistered) {
		t.Fatalf("want unregistered, got %v", err)
	}
	status, resp = 503, `{"error":{"status":"UNAVAILABLE"}}`
	if _, err := f.Send(context.Background(), Message{Token: "t"}); !errors.Is(err, ErrRetryable) {
		t.Fatalf("want retryable, got %v", err)
	}
	status, resp = 400, `{"error":{"status":"INVALID_ARGUMENT"}}`
	if _, err := f.Send(context.Background(), Message{Token: "t"}); !errors.Is(err, ErrInvalid) {
		t.Fatalf("want invalid, got %v", err)
	}
}
