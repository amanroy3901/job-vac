package rules

import (
	"bytes"
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/testutil"
)

func TestEmbeddedSchemaMatchesDocs(t *testing.T) {
	if !bytes.Equal(SchemaJSON, testutil.ReadDoc(t, "docs/backend/rule.schema.json")) {
		t.Fatal("internal/rules/schema/rule.schema.json drifted from docs/backend/rule.schema.json: copy it again")
	}
}

func codes(r Report, sev Severity) []string {
	var out []string
	for _, f := range r.Findings {
		if f.Severity == sev {
			out = append(out, f.Code)
		}
	}
	return out
}

func TestExamplesValidate(t *testing.T) {
	for _, name := range []string{"ssc-gd-2026", "up-police-constable-2025"} {
		raw := testutil.ExampleRule(t, name)
		rep, r := Validate(raw, Options{})
		if r == nil {
			t.Fatalf("%s: schema invalid: %+v", name, rep.Findings)
		}
		for _, f := range rep.Findings {
			if f.Code == "printed_window_mismatch" || f.Code == "schema" {
				t.Errorf("%s: %+v", name, f)
			}
		}
		// The worked examples deliberately leave unverified rows without a page: publish must be blocked.
		if rep.OK() {
			t.Errorf("%s: expected source_page_missing to block publish", name)
		}
		rep2, _ := Validate(testutil.Publishable(t, raw), Options{})
		if !rep2.OK() {
			t.Errorf("%s publishable: %+v", name, rep2.Findings)
		}
	}
}

func TestRiskFlags(t *testing.T) {
	rep, _ := Validate(testutil.Publishable(t, testutil.ExampleRule(t, "up-police-constable-2025")), Options{})
	want := []string{"esm_relaxation", "gender_specific_limits", "pwd_not_eligible", "state_conditional_relaxation"}
	if strings.Join(rep.RiskFlags, ",") != strings.Join(want, ",") || !rep.NeedsSecondReview {
		t.Fatalf("flags = %v second=%v", rep.RiskFlags, rep.NeedsSecondReview)
	}
}

func mutate(t *testing.T, raw []byte, fn func(m map[string]any)) []byte {
	t.Helper()
	var m map[string]any
	if err := json.Unmarshal(raw, &m); err != nil {
		t.Fatal(err)
	}
	fn(m)
	b, _ := json.Marshal(m)
	return b
}

func TestPrintedWindowOffByOne(t *testing.T) {
	raw := testutil.Publishable(t, testutil.ExampleRule(t, "ssc-gd-2026"))
	// Extractor copied "born not before 01-01-2003" (completed-years reading): must block, with a hint.
	bad := mutate(t, raw, func(m map[string]any) {
		m["age"].(map[string]any)["printed_dob_window"].(map[string]any)["born_on_or_after"] = "2002-01-02"
	})
	rep, _ := Validate(bad, Options{})
	if rep.OK() || !strings.Contains(strings.Join(codes(rep, Blocking), ","), "printed_window_mismatch") {
		t.Fatalf("expected mismatch: %+v", rep.Findings)
	}
	for _, f := range rep.Findings {
		if f.Code == "printed_window_mismatch" && !strings.Contains(f.Message, `max_convention="completed_years"`) {
			t.Errorf("missing convention hint: %s", f.Message)
		}
	}
}

func TestSchemaErrorsAndLocales(t *testing.T) {
	raw := testutil.Publishable(t, testutil.ExampleRule(t, "ssc-gd-2026"))
	rep, r := Validate(mutate(t, raw, func(m map[string]any) { m["exam_tags"] = []string{"cricket"} }), Options{})
	if r != nil || rep.OK() {
		t.Fatal("bad enum accepted")
	}
	rep, _ = Validate(mutate(t, raw, func(m map[string]any) {
		m["post"].(map[string]any)["name"] = map[string]any{"en": "Constable"}
	}), Options{})
	if !strings.Contains(strings.Join(codes(rep, Blocking), ","), "launch_locale_missing") {
		t.Fatalf("missing hi not caught: %+v", rep.Findings)
	}
}

type tax struct{}

func (tax) HasQualification(c string) bool { return c == "class10" || c == "class12" }
func (tax) HasRef(kind, c string) bool     { return kind == "state" && c == "UP" }

func TestTaxonomy(t *testing.T) {
	raw := testutil.Publishable(t, testutil.ExampleRule(t, "ssc-gd-2026"))
	bad := mutate(t, raw, func(m map[string]any) {
		m["qualification"].(map[string]any)["any_of"] = []any{map[string]any{"codes": []string{"class11"}}}
	})
	rep, _ := Validate(bad, Options{Taxonomy: tax{}})
	if rep.OK() {
		t.Fatal("unknown code accepted")
	}
	rep, _ = Validate(raw, Options{Taxonomy: tax{}})
	if !rep.OK() {
		t.Fatalf("%+v", rep.Findings)
	}
}

func TestWindowFormula(t *testing.T) {
	c := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	e, l := Window(c, Limit{MinYears: 18, MaxYears: 23}, "not_attained", 0)
	if e.Format(time.DateOnly) != "2003-01-02" || l.Format(time.DateOnly) != "2008-01-01" {
		t.Fatalf("%s %s", e, l)
	}
	e, _ = Window(c, Limit{MinYears: 18, MaxYears: 23}, "completed_years", 0)
	if e.Format(time.DateOnly) != "2002-01-02" {
		t.Fatalf("completed: %s", e)
	}
	leap := time.Date(2024, 2, 29, 0, 0, 0, 0, time.UTC)
	if got := subYears(leap, 1).Format(time.DateOnly); got != "2023-02-28" {
		t.Fatalf("leap: %s", got)
	}
}

func TestPublicRuleStrips(t *testing.T) {
	out, err := PublicRule(testutil.ExampleRule(t, "ssc-gd-2026"), 1)
	if err != nil {
		t.Fatal(err)
	}
	s := string(out)
	if strings.Contains(s, `"review"`) || strings.Contains(s, `"quote"`) || !strings.Contains(s, `"evaluator_min_version":1`) {
		t.Fatalf("not stripped: %s", s[:200])
	}
}
