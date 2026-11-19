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

func apnsOf(t *testing.T, m Message, now time.Time) (map[string]string, map[string]any) {
	t.Helper()
	b, _ := json.Marshal(BuildFCM(m, now)) // round-trip: what FCM receives
	var out struct {
		Message struct {
			Android map[string]any `json:"android"`
			APNs    struct {
				Headers map[string]string `json:"headers"`
				Payload struct {
					Aps map[string]any `json:"aps"`
				} `json:"payload"`
			} `json:"apns"`
		} `json:"message"`
	}
	if err := json.Unmarshal(b, &out); err != nil {
		t.Fatal(err)
	}
	if out.Message.Android == nil {
		t.Fatal("android block missing")
	}
	return out.Message.APNs.Headers, out.Message.APNs.Payload.Aps
}

func TestBuildFCMAndroidUnchanged(t *testing.T) {
	now := time.Date(2026, 7, 31, 2, 30, 0, 0, time.UTC)
	ts := DeadlinePushes("p_01ABC", "n_01X", time.Date(2026, 7, 31, 0, 0, 0, 0, time.UTC), "", DefaultSlots, now.Add(-72*time.Hour))
	last := ts[len(ts)-1]
	m := Message{Topic: last.Topic, Data: last.Payload, High: true, TTL: 24 * time.Hour, Collapse: "p_01ABC"}
	msg := BuildFCM(m, now)["message"].(map[string]any)
	a := msg["android"].(map[string]any)
	if len(a) != 3 || a["priority"] != "HIGH" || a["ttl"] != "86400s" || a["collapse_key"] != "p_01ABC" ||
		msg["notification"] != nil || msg["topic"] != "p_p_01ABC" {
		t.Fatalf("android block changed: %+v", msg)
	}
}

func TestAPNsLastDayDeadline(t *testing.T) {
	now := time.Date(2026, 7, 31, 2, 30, 0, 0, time.UTC)
	ts := DeadlinePushes("p_01ABC", "n_01X", time.Date(2026, 7, 31, 0, 0, 0, 0, time.UTC), "", DefaultSlots, now.Add(-72*time.Hour))
	last := ts[len(ts)-1]
	h, aps := apnsOf(t, Message{Topic: last.Topic, Data: last.Payload, High: true, TTL: 24 * time.Hour, Collapse: "p_01ABC"}, now)
	if h["apns-push-type"] != "alert" || h["apns-priority"] != "10" || h["apns-collapse-id"] != "p_01ABC" ||
		h["apns-expiration"] != "1785551400" {
		t.Fatalf("headers %v", h)
	}
	alert := aps["alert"].(map[string]any)
	if aps["interruption-level"] != "time-sensitive" || aps["mutable-content"] != float64(1) || aps["category"] != CategoryMatchReminder ||
		aps["thread-id"] != "p_01ABC" || alert["loc-key"] != "push.deadline.last_day.body" || alert["title-loc-key"] != "push.deadline.last_day.title" ||
		alert["body"] != nil || aps["content-available"] != nil {
		t.Fatalf("aps %v", aps)
	}
}

func TestAPNsReminderAndLifecycle(t *testing.T) {
	now := time.Now()
	_, aps := apnsOf(t, Message{Topic: "p_p_1", Data: map[string]string{"type": "reminder", "kind": "d_minus_3", "post_id": "p_1"}}, now)
	if aps["interruption-level"] != "active" || aps["category"] != CategoryMatchReminder {
		t.Fatalf("d-3 %v", aps)
	}
	h, aps := apnsOf(t, Message{Topic: "n_n_1", Data: map[string]string{"type": "lifecycle", "notice_id": "n_1", "critical": "1"}, High: true}, now)
	if aps["category"] != CategoryLifecycle || aps["thread-id"] != "n_1" || h["apns-priority"] != "10" {
		t.Fatalf("lifecycle %v %v", h, aps)
	}
	_, aps = apnsOf(t, Message{Token: "t", Data: map[string]string{"type": "self_test", "test_id": "x"}, High: true}, now)
	if aps["category"] != CategorySelfTest {
		t.Fatalf("self test %v", aps)
	}
}

func TestAPNsBackgroundAndAccount(t *testing.T) {
	now := time.Now()
	for _, d := range []map[string]string{{"type": "sync", "seq": "4"}, {"type": "account", "template": "entitlement", "fallback_title": "x"},
		{"type": "family", "family_event": "sync"}} {
		h, aps := apnsOf(t, Message{Topic: "catalog", Data: d, TTL: time.Hour}, now)
		if h["apns-push-type"] != "background" || h["apns-priority"] != "5" || aps["content-available"] != float64(1) || aps["alert"] != nil {
			t.Errorf("%v: %v %v", d, h, aps)
		}
	}
	h, aps := apnsOf(t, Message{Token: "t", Data: map[string]string{"type": "account", "template": "N-31",
		"fallback_title": "Pro ट्रायल 2 दिन में ख़त्म", "fallback_body": "प्लान चुनें"}}, now)
	alert := aps["alert"].(map[string]any)
	if h["apns-push-type"] != "alert" || h["apns-priority"] != "5" || alert["title"] != "Pro ट्रायल 2 दिन में ख़त्म" ||
		aps["category"] != nil || aps["thread-id"] != "account" {
		t.Fatalf("N-31 %v %v", h, aps)
	}
}
