package catalog

import (
	"context"
	"slices"
	"sort"
	"strings"

	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/rules"
)

// NormalizeLocales turns ?locales= (or, when empty, the Accept-Language primary tag + its fallback chain) into
// the canonical set key: known codes only, `en` always added, sorted, comma-joined (matching-engine.md §4.1).
func NormalizeLocales(param string, fallbackChain []string) string {
	set := map[string]bool{"en": true}
	src := strings.Split(param, ",")
	if strings.TrimSpace(param) == "" {
		src = fallbackChain
	}
	for _, s := range src {
		s = strings.ToLower(strings.TrimSpace(s))
		if rules.KnownLocales[s] {
			set[s] = true
		}
	}
	if len(set) == 1 && strings.TrimSpace(param) == "" && len(fallbackChain) == 0 {
		set["hi"] = true // default hi,en
	}
	out := make([]string, 0, len(set))
	for k := range set {
		out = append(out, k)
	}
	sort.Strings(out)
	return strings.Join(out, ",")
}

// Locale is one row of the locale table.
type Locale struct {
	Code     string
	Name     string
	Script   string
	RTL      bool
	Status   string
	Fallback []string
}

// LoadLocales reads the locale registry ordered by sort.
func LoadLocales(ctx context.Context, q db.Querier) ([]Locale, error) {
	rows, err := q.Query(ctx, `SELECT code, name_native, script, rtl, status, fallback FROM locale ORDER BY sort`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Locale
	for rows.Next() {
		var l Locale
		if err := rows.Scan(&l.Code, &l.Name, &l.Script, &l.RTL, &l.Status, &l.Fallback); err != nil {
			return nil, err
		}
		out = append(out, l)
	}
	return out, rows.Err()
}

// Chain returns code followed by its fallback chain (unknown code → hi chain).
func Chain(locales []Locale, code string) []string {
	for _, l := range locales {
		if l.Code == code {
			return append([]string{code}, l.Fallback...)
		}
	}
	return []string{"hi", "en"}
}

// PrebuiltSets are the locale sets rendered at publish: one per locale (itself + fallbacks + en).
func PrebuiltSets(locales []Locale) []string {
	var out []string
	for _, l := range locales {
		s := NormalizeLocales(strings.Join(Chain(locales, l.Code), ","), nil)
		if !slices.Contains(out, s) {
			out = append(out, s)
		}
	}
	sort.Strings(out)
	return out
}

// FilterLocales deep-copies v keeping only the requested locales in every LocalizedText map.
func FilterLocales(v any, keep map[string]bool) any {
	switch t := v.(type) {
	case map[string]any:
		if rules.IsLocalizedText(t) {
			out := make(map[string]any, len(keep))
			for k, s := range t {
				if keep[k] {
					out[k] = s
				}
			}
			return out
		}
		out := make(map[string]any, len(t))
		for k, c := range t {
			out[k] = FilterLocales(c, keep)
		}
		return out
	case []any:
		out := make([]any, len(t))
		for i, c := range t {
			out[i] = FilterLocales(c, keep)
		}
		return out
	default:
		return v
	}
}

// SetMap parses a canonical set key into a lookup map.
func SetMap(set string) map[string]bool {
	m := map[string]bool{}
	for _, s := range strings.Split(set, ",") {
		m[s] = true
	}
	return m
}
