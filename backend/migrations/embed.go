// Package migrations embeds the goose SQL migrations (00001 = docs/backend/schema.sql baseline).
package migrations

import "embed"

// FS holds every *.sql migration.
//
//go:embed *.sql
var FS embed.FS
