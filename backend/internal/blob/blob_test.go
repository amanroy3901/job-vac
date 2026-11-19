package blob

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestLocal(t *testing.T) {
	s := Local{Dir: t.TempDir()}
	ctx := context.Background()
	if _, _, err := s.Get(ctx, "catalog/x.json"); !errors.Is(err, ErrNotFound) {
		t.Fatalf("want not found, got %v", err)
	}
	if err := s.Put(ctx, "catalog/x.json", []byte(`{}`), Meta{ContentType: "application/json", CacheControl: "public"}); err != nil {
		t.Fatal(err)
	}
	b, m, err := s.Get(ctx, "catalog/x.json")
	if err != nil || string(b) != "{}" || m.CacheControl != "public" || m.ETag != ETag([]byte("{}")) {
		t.Fatalf("%s %+v %v", b, m, err)
	}
	if err := s.Put(ctx, "../evil", nil, Meta{}); err == nil {
		t.Fatal("path traversal accepted")
	}
}

func TestGCSAgainstFake(t *testing.T) {
	objs := map[string]string{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && strings.HasPrefix(r.URL.Path, "/upload/storage/v1/b/bkt/o"):
			body, _ := io.ReadAll(r.Body)
			if !strings.Contains(string(body), `"cacheControl":"public, max-age=60"`) {
				http.Error(w, "no cache control", 400)
				return
			}
			objs["k"] = string(body)
			w.WriteHeader(200)
		case r.Method == http.MethodGet && r.URL.Path == "/storage/v1/b/bkt/o/catalog/head.json":
			if _, ok := objs["k"]; !ok {
				http.NotFound(w, r)
				return
			}
			_, _ = w.Write([]byte(`{"seq":1}`))
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()
	g := &GCS{Bucket: "bkt", Endpoint: srv.URL, Client: srv.Client()}
	ctx := context.Background()
	if _, _, err := g.Get(ctx, "catalog/head.json"); !errors.Is(err, ErrNotFound) {
		t.Fatalf("want not found: %v", err)
	}
	if err := g.Put(ctx, "catalog/head.json", []byte(`{"seq":1}`), Meta{ContentType: "application/json", CacheControl: "public, max-age=60"}); err != nil {
		t.Fatal(err)
	}
	if b, _, err := g.Get(ctx, "catalog/head.json"); err != nil || string(b) != `{"seq":1}` {
		t.Fatalf("%s %v", b, err)
	}
}
