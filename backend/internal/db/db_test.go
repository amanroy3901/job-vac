package db_test

import (
	"context"
	"strings"
	"testing"

	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/testutil"
	"github.com/amanroy3901/job-vac/backend/migrations"
)

// The baseline migration must stay docs/backend/schema.sql verbatim (plus goose markers).
func TestBaselineMatchesDesignSchema(t *testing.T) {
	b, err := migrations.FS.ReadFile("00001_baseline.sql")
	if err != nil {
		t.Fatal(err)
	}
	s := string(b)
	s = s[strings.Index(s, "-- Sarkari Match"):strings.Index(s, "\n-- +goose Down")]
	s = strings.ReplaceAll(s, "-- +goose StatementBegin\n", "")
	s = strings.ReplaceAll(s, "\n-- +goose StatementEnd", "")
	want := string(testutil.ReadDoc(t, "docs/backend/schema.sql"))
	if strings.TrimSpace(s) != strings.TrimSpace(want) {
		t.Fatal("migrations/00001_baseline.sql drifted from docs/backend/schema.sql; regenerate it (see backend/README.md)")
	}
}

func TestMigrateUpDownUp(t *testing.T) {
	pool := testutil.DB(t)
	ctx := context.Background()
	dsn := testutil.DSN(pool)
	pool.Close()
	for _, cmd := range []string{"down", "down", "up"} {
		if err := db.Migrate(ctx, dsn, cmd); err != nil {
			t.Fatalf("%s: %v", cmd, err)
		}
	}
	p2, err := db.Open(ctx, dsn, 2)
	if err != nil {
		t.Fatal(err)
	}
	defer p2.Close()
	var n int
	if err := p2.QueryRow(ctx, `SELECT count(*) FROM product`).Scan(&n); err != nil || n != 3 {
		t.Fatalf("seed after re-up: %d %v", n, err)
	}
	// No profile-like column anywhere (ADR-11): the server stores no DOB/category/PwD/phone.
	var bad []string
	rows, err := p2.Query(ctx, `SELECT table_name || '.' || column_name FROM information_schema.columns WHERE table_schema = 'public'
		AND table_name <> 'admin_user' AND column_name ~ '(dob|birth|category|pwd|phone|aadhaar|email)'`) // admin_user.email = staff allowlist
	if err != nil {
		t.Fatal(err)
	}
	for rows.Next() {
		var c string
		_ = rows.Scan(&c)
		bad = append(bad, c)
	}
	if len(bad) > 0 {
		t.Errorf("personal-data columns found: %v", bad)
	}
}
