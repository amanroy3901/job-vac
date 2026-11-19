package catalog

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestNormalizeLocales(t *testing.T) {
	cases := map[string]string{"hi,en": "en,hi", "ta": "en,ta", " UR , hi,xx": "en,hi,ur", "": "en,hi"}
	for in, want := range cases {
		if got := NormalizeLocales(in, nil); got != want {
			t.Errorf("%q → %q want %q", in, got, want)
		}
	}
	if got := NormalizeLocales("", []string{"mr", "hi", "en"}); got != "en,hi,mr" {
		t.Errorf("chain → %q", got)
	}
}

func TestFilterAndRender(t *testing.T) {
	doc := map[string]any{"seq": 1, "notices": []any{map[string]any{
		"title":     map[string]any{"en": "T", "hi": "टी", "ta": "டி"},
		"posts":     []any{map[string]any{"rule": map[string]any{"fees": []any{map[string]any{"note": map[string]any{"en": "free", "hi": "मुफ़्त"}}}}}},
		"exam_tags": []any{"ssc"}, "not_text": map[string]any{"en": 1}}}}
	all, _ := json.Marshal(doc)
	out, err := RenderSet(all, "en,ta")
	if err != nil {
		t.Fatal(err)
	}
	s := string(out)
	if strings.Contains(s, "टी") || strings.Contains(s, "मुफ़्त") || !strings.Contains(s, "டி") || !strings.Contains(s, `"locales":["en","ta"]`) {
		t.Fatalf("%s", s)
	}
	if !strings.Contains(s, `"not_text":{"en":1}`) {
		t.Fatalf("non-text map must be untouched: %s", s)
	}
}

func TestPrebuiltSets(t *testing.T) {
	ls := []Locale{{Code: "hi", Fallback: []string{"en"}}, {Code: "en", Fallback: []string{"hi"}}, {Code: "as", Fallback: []string{"bn", "en", "hi"}}}
	got := strings.Join(PrebuiltSets(ls), "|")
	if got != "as,bn,en,hi|en,hi" {
		t.Fatalf("%s", got)
	}
}
