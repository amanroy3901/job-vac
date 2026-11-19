// Package testutil holds fixtures shared by unit and integration tests.
package testutil

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"testing"

	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/jackc/pgx/v5/pgxpool"
)

// RepoRoot returns the job-vac repository root (backend/..).
func RepoRoot() string {
	_, file, _, _ := runtime.Caller(0)
	return filepath.Clean(filepath.Join(filepath.Dir(file), "..", "..", ".."))
}

// ReadDoc reads a file relative to the repository root.
func ReadDoc(t testing.TB, rel string) []byte {
	t.Helper()
	b, err := os.ReadFile(filepath.Join(RepoRoot(), rel))
	if err != nil {
		t.Fatal(err)
	}
	return b
}

// ExampleRule loads docs/backend/examples/<name>.rule.json.
func ExampleRule(t testing.TB, name string) []byte {
	return ReadDoc(t, filepath.Join("docs", "backend", "examples", name+".rule.json"))
}

// Publishable fills every missing source.page with 1 so a worked example passes the blocking
// "every relaxation row has source.page" validator (the examples mark unverified rows on purpose).
func Publishable(t testing.TB, raw []byte) []byte {
	t.Helper()
	var doc any
	if err := json.Unmarshal(raw, &doc); err != nil {
		t.Fatal(err)
	}
	var walk func(v any)
	walk = func(v any) {
		switch x := v.(type) {
		case map[string]any:
			for k, c := range x {
				if k == "source" {
					if s, ok := c.(map[string]any); ok {
						if p, ok := s["page"]; !ok || p == nil {
							s["page"] = 1
						}
					}
				}
				walk(c)
			}
			if age, ok := x["age"].(map[string]any); ok {
				if _, has := age["source"]; !has {
					age["source"] = map[string]any{"doc": "test", "page": 1}
				}
				if rx, ok := age["relaxations"].([]any); ok {
					for _, r := range rx {
						if m, ok := r.(map[string]any); ok {
							if _, has := m["source"]; !has {
								m["source"] = map[string]any{"doc": "test", "page": 1}
							}
						}
					}
				}
			}
		case []any:
			for _, c := range x {
				walk(c)
			}
		}
	}
	walk(doc)
	out, _ := json.Marshal(doc)
	return out
}

// DB returns a migrated pool on a fresh schema of TEST_DATABASE_URL (or DATABASE_URL), skipping the test when
// neither is set. Each call resets the database: integration tests in one package run sequentially.
func DB(t testing.TB) *pgxpool.Pool {
	t.Helper()
	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		url = os.Getenv("DATABASE_URL")
	}
	if url == "" {
		t.Skip("TEST_DATABASE_URL/DATABASE_URL not set: skipping Postgres integration test")
	}
	ctx := context.Background()
	if err := db.Migrate(ctx, url, "reset"); err != nil {
		t.Fatalf("reset: %v", err)
	}
	if err := db.Migrate(ctx, url, "up"); err != nil {
		t.Fatalf("migrate: %v", err)
	}
	pool, err := db.Open(ctx, url, 5)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(pool.Close)
	return pool
}
