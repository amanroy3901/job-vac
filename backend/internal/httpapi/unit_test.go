package httpapi

import (
	"net/http/httptest"
	"testing"
	"time"
)

func TestLimiter(t *testing.T) {
	now := time.Unix(0, 0)
	l := newLimiter(func() time.Time { return now })
	r := rate{per: time.Minute, n: 60, burst: 2}
	if l.allow("k", r) != 0 || l.allow("k", r) != 0 {
		t.Fatal("burst denied")
	}
	if l.allow("k", r) == 0 {
		t.Fatal("over burst allowed")
	}
	now = now.Add(time.Second)
	if l.allow("k", r) != 0 {
		t.Fatal("refill failed")
	}
}

func TestParseLangAndVerb(t *testing.T) {
	for in, want := range map[string]string{"ta-IN,en;q=0.8": "ta", "": "hi", "fr,en": "en", "ur": "ur"} {
		if got := parseLang(in); got != want {
			t.Errorf("%q → %q", in, got)
		}
	}
	if id, v := splitVerb("abc:make-primary"); id != "abc" || v != "make-primary" {
		t.Errorf("%s %s", id, v)
	}
	r := httptest.NewRequest("GET", "/", nil)
	r.RemoteAddr = "203.0.113.77:5555"
	if got := ipPrefix(r); got != "203.0.113" {
		t.Errorf("ip prefix %q", got)
	}
}
