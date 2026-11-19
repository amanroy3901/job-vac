// Package testutil holds fixtures shared by unit and integration tests.
package testutil

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"net/url"
	"os"
	"path/filepath"
	"runtime"
	"testing"

	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/jackc/pgx/v5"
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

// DB returns a pool on a fresh, migrated database created next to TEST_DATABASE_URL (or DATABASE_URL) and
// dropped after the test, so packages can run in parallel. Skips when neither variable is set.
func DB(t testing.TB) *pgxpool.Pool {
	t.Helper()
	base := os.Getenv("TEST_DATABASE_URL")
	if base == "" {
		base = os.Getenv("DATABASE_URL")
	}
	if base == "" {
		t.Skip("TEST_DATABASE_URL/DATABASE_URL not set: skipping Postgres integration test")
	}
	ctx := context.Background()
	u, err := url.Parse(base)
	if err != nil {
		t.Fatal(err)
	}
	admin, err := pgx.Connect(ctx, base)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	defer admin.Close(ctx)
	b := make([]byte, 6)
	_, _ = rand.Read(b)
	name := "sm_test_" + hex.EncodeToString(b)
	if _, err := admin.Exec(ctx, "CREATE DATABASE "+name); err != nil {
		t.Fatalf("create database (needs CREATEDB): %v", err)
	}
	u.Path = "/" + name
	dsn := u.String()
	if err := db.Migrate(ctx, dsn, "up"); err != nil {
		t.Fatalf("migrate: %v", err)
	}
	pool, err := db.Open(ctx, dsn, 5)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		pool.Close()
		if c, err := pgx.Connect(context.Background(), base); err == nil {
			_, _ = c.Exec(context.Background(), "DROP DATABASE IF EXISTS "+name+" WITH (FORCE)")
			_ = c.Close(context.Background())
		}
	})
	return pool
}

// DSN returns the connection string of a pool created by DB.
func DSN(p *pgxpool.Pool) string { return p.Config().ConnString() }
