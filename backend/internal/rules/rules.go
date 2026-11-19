// Package rules validates post eligibility rule documents (docs/backend/rule.schema.json) before publish:
// JSON-schema validation, the blocking printed-DOB-window check of matching-engine.md §3.1, the semantic
// validators of ingestion-pipeline.md §6, risk flags for second review, the public (catalog) form and the
// evaluator_min_version computation. The verdict itself is computed on the phone (:matching-core), never here.
package rules

import (
	"bytes"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
	"slices"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/santhosh-tekuri/jsonschema/v6"
)

// SchemaJSON is docs/backend/rule.schema.json (copied; a test keeps the copy identical to the doc).
//
//go:embed schema/rule.schema.json
var SchemaJSON []byte

// EvaluatorVersion is the newest :matching-core EVALUATOR_VERSION this server knows how to target.
const EvaluatorVersion = 1

// Severity of a finding.
type Severity string

// Severities: blocking findings prevent publish.
const (
	Blocking Severity = "blocking"
	Warn     Severity = "warn"
	Info     Severity = "info"
)

// Finding is one validator result.
type Finding struct {
	Path     string   `json:"path"`
	Code     string   `json:"code"`
	Severity Severity `json:"severity"`
	Message  string   `json:"message"`
}

// Report is the outcome of Validate.
type Report struct {
	Findings            []Finding `json:"findings"`
	RiskFlags           []string  `json:"risk_flags"`
	NeedsSecondReview   bool      `json:"needs_second_review"`
	EvaluatorMinVersion int       `json:"evaluator_min_version"`
}

// OK reports whether no blocking finding exists.
func (r Report) OK() bool {
	for _, f := range r.Findings {
		if f.Severity == Blocking {
			return false
		}
	}
	return true
}

// Taxonomy answers whether a qualification / attribute / state / pwd code exists (reference tables).
type Taxonomy interface {
	HasQualification(code string) bool
	HasRef(kind, code string) bool
}

// Options configures Validate.
type Options struct {
	Taxonomy      Taxonomy // nil skips code checks
	LaunchLocales []string // every LocalizedText must carry these (default hi, en)
}

var (
	schemaOnce sync.Once
	schema     *jsonschema.Schema
	schemaErr  error
)

func compiled() (*jsonschema.Schema, error) {
	schemaOnce.Do(func() {
		doc, err := jsonschema.UnmarshalJSON(bytes.NewReader(SchemaJSON))
		if err != nil {
			schemaErr = err
			return
		}
		c := jsonschema.NewCompiler()
		c.AssertFormat()
		const url = "https://api.sarkarimatch.in/schemas/rule/v1.json"
		if err := c.AddResource(url, doc); err != nil {
			schemaErr = err
			return
		}
		schema, schemaErr = c.Compile(url)
	})
	return schema, schemaErr
}

// Validate runs every validator on a raw rule document.
func Validate(raw []byte, opt Options) (Report, *Rule) {
	rep := Report{EvaluatorMinVersion: 1}
	sch, err := compiled()
	if err != nil {
		rep.add("", "schema_unavailable", Blocking, err.Error())
		return rep, nil
	}
	inst, err := jsonschema.UnmarshalJSON(bytes.NewReader(raw))
	if err != nil {
		rep.add("", "invalid_json", Blocking, err.Error())
		return rep, nil
	}
	if err := sch.Validate(inst); err != nil {
		var ve *jsonschema.ValidationError
		if errors.As(err, &ve) {
			for _, leaf := range leaves(ve) {
				rep.add("/"+strings.Join(leaf.InstanceLocation, "/"), "schema", Blocking, leaf.Error())
			}
		} else {
			rep.add("", "schema", Blocking, err.Error())
		}
		return rep, nil
	}
	var r Rule
	if err := json.Unmarshal(raw, &r); err != nil {
		rep.add("", "invalid_json", Blocking, err.Error())
		return rep, nil
	}
	r.checkDOBWindow(&rep)
	r.checkSemantics(&rep, opt)
	r.checkLocales(raw, &rep, opt)
	rep.RiskFlags, rep.NeedsSecondReview = r.riskFlags()
	rep.EvaluatorMinVersion = r.evaluatorMinVersion()
	sort.SliceStable(rep.Findings, func(i, j int) bool { return sevRank(rep.Findings[i].Severity) < sevRank(rep.Findings[j].Severity) })
	return rep, &r
}

func sevRank(s Severity) int {
	switch s {
	case Blocking:
		return 0
	case Warn:
		return 1
	}
	return 2
}

func leaves(ve *jsonschema.ValidationError) []*jsonschema.ValidationError {
	if len(ve.Causes) == 0 {
		return []*jsonschema.ValidationError{ve}
	}
	var out []*jsonschema.ValidationError
	for _, c := range ve.Causes {
		out = append(out, leaves(c)...)
	}
	return out
}

func (r *Report) add(path, code string, sev Severity, msg string) {
	r.Findings = append(r.Findings, Finding{Path: path, Code: code, Severity: sev, Message: msg})
}

// ---------------------------------------------------------------- typed subset of the rule document

// Date is an ISO calendar date (IST).
type Date struct{ time.Time }

// UnmarshalJSON parses "2006-01-02".
func (d *Date) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	t, err := time.Parse(time.DateOnly, s)
	if err != nil {
		return err
	}
	d.Time = t
	return nil
}

func (d Date) String() string { return d.Format(time.DateOnly) }

// Condition is rule.schema.json $defs/Condition.
type Condition struct {
	Category          []string    `json:"category,omitempty"`
	Gender            []string    `json:"gender,omitempty"`
	DomicileStates    []string    `json:"domicile_states,omitempty"`
	NotDomicileStates []string    `json:"not_domicile_states,omitempty"`
	PwD               *bool       `json:"pwd,omitempty"`
	PwDTypes          []string    `json:"pwd_types,omitempty"`
	ESM               *bool       `json:"esm,omitempty"`
	GovtEmployee      string      `json:"govt_employee,omitempty"`
	MaritalStatus     []string    `json:"marital_status,omitempty"`
	HasAttribute      []string    `json:"has_attribute,omitempty"`
	Any               []Condition `json:"any,omitempty"`
}

// Source is $defs/Source.
type Source struct {
	Doc   string  `json:"doc"`
	Page  *int    `json:"page"`
	Para  *string `json:"para"`
	Quote *string `json:"quote"`
}

// Limit is one age.limits row.
type Limit struct {
	When     *Condition `json:"when"`
	MinYears int        `json:"min_years"`
	MaxYears int        `json:"max_years"`
}

// Relaxation is one age.relaxations row.
type Relaxation struct {
	ID           string     `json:"id"`
	When         *Condition `json:"when"`
	Years        int        `json:"years"`
	Mode         string     `json:"mode"`
	NeedsProfile []string   `json:"needs_profile"`
	Source       *Source    `json:"source"`
}

// DOBWindow is age.printed_dob_window.
type DOBWindow struct {
	When          *Condition `json:"when"`
	BornOnOrAfter Date       `json:"born_on_or_after"`
	BornOnOrBefore Date      `json:"born_on_or_before"`
}

// Age is the age block.
type Age struct {
	CutoffDate            Date         `json:"cutoff_date"`
	Limits                []Limit      `json:"limits"`
	MaxConvention         string       `json:"max_convention"`
	GeneralExtensionYears int          `json:"general_extension_years"`
	Relaxations           []Relaxation `json:"relaxations"`
	PrintedDOBWindow      *DOBWindow   `json:"printed_dob_window"`
	Source                *Source      `json:"source"`
}

// QualAlt is one qualification.any_of alternative.
type QualAlt struct {
	Codes    []string   `json:"codes"`
	MinLevel *int       `json:"min_level"`
	When     *Condition `json:"when"`
}

// Fee is one fees row.
type Fee struct {
	When        *Condition `json:"when"`
	AmountPaise int64      `json:"amount_paise"`
}

// Rule is the subset of the document the validators read.
type Rule struct {
	SchemaVersion string   `json:"schema_version"`
	ExamTags      []string `json:"exam_tags"`
	Post          struct {
		Code string `json:"code"`
	} `json:"post"`
	Scope *struct {
		Kind  string  `json:"kind"`
		State *string `json:"state"`
	} `json:"scope"`
	Age           Age `json:"age"`
	Qualification struct {
		AnyOf          []QualAlt `json:"any_of"`
		MustCompleteBy *Date     `json:"must_complete_by"`
	} `json:"qualification"`
	RequiredAttributes []struct {
		AnyOfCodes []string `json:"any_of_codes"`
	} `json:"required_attributes"`
	Domicile *struct {
		RequiredStates []string `json:"required_states"`
	} `json:"domicile"`
	PwD *struct {
		Eligible      *bool    `json:"eligible"`
		SuitableTypes []string `json:"suitable_types"`
	} `json:"pwd"`
	Fees []Fee `json:"fees"`
}

// ---------------------------------------------------------------- DOB window (matching-engine.md §3.1)

// subYears subtracts whole years keeping the calendar day (29 Feb → 28 Feb in non-leap years).
func subYears(d time.Time, years int) time.Time {
	y, m, day := d.Date()
	ty := y - years
	if m == time.February && day == 29 && !isLeap(ty) {
		day = 28
	}
	return time.Date(ty, m, day, 0, 0, 0, 0, time.UTC)
}

func isLeap(y int) bool { return y%4 == 0 && (y%100 != 0 || y%400 == 0) }

// Window computes the base (unrelaxed) DOB window for a limits row:
// not_attained:   dob ≥ c − (M+g) years + 1 day; completed_years: dob ≥ c − (M+g+1) years + 1 day; dob ≤ c − m years.
func Window(cutoff time.Time, l Limit, convention string, extension int) (earliest, latest time.Time) {
	max := l.MaxYears + extension
	if convention == "completed_years" {
		max++
	}
	earliest = subYears(cutoff, max).AddDate(0, 0, 1)
	latest = subYears(cutoff, l.MinYears)
	return
}

func (r *Rule) checkDOBWindow(rep *Report) {
	a := r.Age
	conv := a.MaxConvention
	if conv == "" {
		conv = "not_attained"
	}
	for i, l := range a.Limits {
		if l.MinYears >= l.MaxYears {
			rep.add(fmt.Sprintf("/age/limits/%d", i), "min_not_below_max", Blocking, "min_years must be below max_years")
		}
	}
	pw := a.PrintedDOBWindow
	if pw == nil {
		rep.add("/age/printed_dob_window", "printed_window_missing", Warn,
			"no printed DOB window: the off-by-one cross-check cannot run; copy it from the notice if printed")
		return
	}
	row, ok := limitFor(a.Limits, pw.When)
	if !ok {
		rep.add("/age/printed_dob_window/when", "printed_window_no_limit", Blocking,
			"no age.limits row matches printed_dob_window.when")
		return
	}
	want := func(conv string, ext int) (time.Time, time.Time) { return Window(a.CutoffDate.Time, row, conv, ext) }
	e, l := want(conv, 0)
	if e.Equal(pw.BornOnOrAfter.Time) && l.Equal(pw.BornOnOrBefore.Time) {
		return
	}
	// The window printed on a corrigendum may already include the one-time general extension.
	if a.GeneralExtensionYears > 0 {
		if e2, l2 := want(conv, a.GeneralExtensionYears); e2.Equal(pw.BornOnOrAfter.Time) && l2.Equal(pw.BornOnOrBefore.Time) {
			rep.add("/age/printed_dob_window", "printed_window_includes_extension", Info,
				"printed window matches the limits including general_extension_years")
			return
		}
	}
	other := "completed_years"
	if conv == "completed_years" {
		other = "not_attained"
	}
	msg := fmt.Sprintf("computed window %s..%s (%s) differs from printed %s..%s", e.Format(time.DateOnly),
		l.Format(time.DateOnly), conv, pw.BornOnOrAfter, pw.BornOnOrBefore)
	if e2, l2 := want(other, 0); e2.Equal(pw.BornOnOrAfter.Time) && l2.Equal(pw.BornOnOrBefore.Time) {
		msg += fmt.Sprintf("; the printed window matches max_convention=%q", other)
	}
	rep.add("/age/printed_dob_window", "printed_window_mismatch", Blocking, msg)
}

// limitFor picks the limits row the printed window describes: the row whose `when` equals the window's `when`,
// else (no when on the window) the default row (no when) or the first row.
func limitFor(limits []Limit, when *Condition) (Limit, bool) {
	if len(limits) == 0 {
		return Limit{}, false
	}
	if when == nil {
		for _, l := range limits {
			if l.When == nil {
				return l, true
			}
		}
		return limits[0], true
	}
	for _, l := range limits {
		if l.When != nil && reflect.DeepEqual(*l.When, *when) {
			return l, true
		}
	}
	return Limit{}, false
}

// ---------------------------------------------------------------- semantic validators (ingestion-pipeline.md §6)

func (r *Rule) checkSemantics(rep *Report, opt Options) {
	for i, rx := range r.Age.Relaxations {
		if rx.Source == nil || rx.Source.Page == nil {
			rep.add(fmt.Sprintf("/age/relaxations/%d/source/page", i), "source_page_missing", Blocking,
				fmt.Sprintf("relaxation %q has no source.page (every relaxation row needs its PDF page)", rx.ID))
		}
		if rx.Mode == "after_service_deduction" && !slices.Contains(rx.NeedsProfile, "esm_service_months") {
			rep.add(fmt.Sprintf("/age/relaxations/%d/needs_profile", i), "esm_needs_service", Warn,
				"after_service_deduction rows should list needs_profile esm_service_months")
		}
	}
	if r.Age.Source == nil || r.Age.Source.Page == nil {
		rep.add("/age/source/page", "source_page_missing", Blocking, "age limits have no source.page")
	}
	if r.Qualification.MustCompleteBy != nil && r.Qualification.MustCompleteBy.Before(r.Age.CutoffDate.AddDate(-2, 0, 0)) {
		rep.add("/qualification/must_complete_by", "date_order", Warn, "must_complete_by is far before the age cutoff date")
	}
	hasDefault := false
	for i, f := range r.Fees {
		if f.When == nil {
			hasDefault = true
		}
		if f.AmountPaise%100 != 0 || f.AmountPaise > 500000 {
			rep.add(fmt.Sprintf("/fees/%d/amount_paise", i), "fee_amount", Warn, "fee should be a whole rupee amount ≤ ₹5,000")
		}
	}
	if !hasDefault {
		rep.add("/fees", "fee_default_missing", Warn, "fee table has no default row (row without when)")
	}
	if opt.Taxonomy != nil {
		for i, alt := range r.Qualification.AnyOf {
			for _, c := range alt.Codes {
				if !opt.Taxonomy.HasQualification(c) {
					rep.add(fmt.Sprintf("/qualification/any_of/%d/codes", i), "unknown_code", Blocking, "unknown qualification code "+c)
				}
			}
		}
		for i, ra := range r.RequiredAttributes {
			for _, c := range ra.AnyOfCodes {
				if !opt.Taxonomy.HasRef("extra", c) {
					rep.add(fmt.Sprintf("/required_attributes/%d/any_of_codes", i), "unknown_code", Blocking, "unknown attribute code "+c)
				}
			}
		}
		states := map[string]bool{}
		if r.Scope != nil && r.Scope.State != nil {
			states[*r.Scope.State] = true
		}
		if r.Domicile != nil {
			for _, s := range r.Domicile.RequiredStates {
				states[s] = true
			}
		}
		for _, rx := range r.Age.Relaxations {
			collectStates(rx.When, states)
		}
		for s := range states {
			if !opt.Taxonomy.HasRef("state", s) {
				rep.add("/", "unknown_code", Blocking, "unknown state code "+s)
			}
		}
	}
	for i, alt := range r.Qualification.AnyOf {
		if len(alt.Codes) == 0 && alt.MinLevel == nil {
			rep.add(fmt.Sprintf("/qualification/any_of/%d", i), "qualification_empty", Blocking, "alternative needs codes or min_level")
		}
	}
}

func collectStates(c *Condition, into map[string]bool) {
	if c == nil {
		return
	}
	for _, s := range c.DomicileStates {
		into[s] = true
	}
	for _, s := range c.NotDomicileStates {
		into[s] = true
	}
	for i := range c.Any {
		collectStates(&c.Any[i], into)
	}
}

// checkLocales requires every LocalizedText (object with an "en" string and only locale keys) to carry the
// launch locales (ADR-12: en mandatory everywhere, hi + en at publish).
func (r *Rule) checkLocales(raw []byte, rep *Report, opt Options) {
	launch := opt.LaunchLocales
	if len(launch) == 0 {
		launch = []string{"hi", "en"}
	}
	var doc any
	if err := json.Unmarshal(raw, &doc); err != nil {
		return
	}
	walkLocalized(doc, "", func(path string, m map[string]any) {
		for _, l := range launch {
			if s, ok := m[l].(string); !ok || strings.TrimSpace(s) == "" {
				rep.add(path, "launch_locale_missing", Blocking, "missing launch locale "+l)
			}
		}
	})
}

// IsLocalizedText reports whether v looks like a LocalizedText map.
func IsLocalizedText(m map[string]any) bool {
	if _, ok := m["en"].(string); !ok {
		return false
	}
	for k, v := range m {
		if !KnownLocales[k] {
			return false
		}
		if _, ok := v.(string); !ok {
			return false
		}
	}
	return true
}

// KnownLocales are the 13 supported BCP-47 codes.
var KnownLocales = map[string]bool{"as": true, "bn": true, "en": true, "gu": true, "hi": true, "kn": true,
	"ml": true, "mr": true, "or": true, "pa": true, "ta": true, "te": true, "ur": true}

func walkLocalized(v any, path string, fn func(string, map[string]any)) {
	switch t := v.(type) {
	case map[string]any:
		if IsLocalizedText(t) {
			fn(path, t)
			return
		}
		for k, c := range t {
			walkLocalized(c, path+"/"+k, fn)
		}
	case []any:
		for i, c := range t {
			walkLocalized(c, fmt.Sprintf("%s/%d", path, i), fn)
		}
	}
}

// ---------------------------------------------------------------- risk flags (matching-engine.md §7.4)

func (r *Rule) riskFlags() ([]string, bool) {
	flags := map[string]bool{}
	for _, rx := range r.Age.Relaxations {
		if rx.When != nil && (len(rx.When.DomicileStates) > 0 || len(rx.When.NotDomicileStates) > 0) {
			flags["state_conditional_relaxation"] = true
		}
		if rx.When != nil && (rx.When.PwD != nil || len(rx.When.PwDTypes) > 0) {
			flags["pwd_relaxation"] = true
		}
		if rx.When != nil && rx.When.ESM != nil {
			flags["esm_relaxation"] = true
		}
	}
	for _, l := range r.Age.Limits {
		if l.When != nil && len(l.When.Gender) > 0 {
			flags["gender_specific_limits"] = true
		}
	}
	if r.PwD != nil && r.PwD.Eligible != nil && !*r.PwD.Eligible {
		flags["pwd_not_eligible"] = true
	}
	out := make([]string, 0, len(flags))
	for f := range flags {
		out = append(out, f)
	}
	sort.Strings(out)
	second := flags["state_conditional_relaxation"] || flags["gender_specific_limits"] || flags["pwd_relaxation"] || flags["esm_relaxation"]
	return out, second
}

// evaluatorMinVersion: every v1 schema field is understood by evaluator 1. When the schema grows, map the new
// fields to the evaluator version that introduced them here (matching-engine.md §4.3).
func (r *Rule) evaluatorMinVersion() int { return 1 }

// ---------------------------------------------------------------- public form

// PublicRule strips `review` and every `source.quote` (what ships in the catalog) and sets evaluator_min_version.
func PublicRule(raw []byte, evaluatorMin int) (json.RawMessage, error) {
	var doc map[string]any
	if err := json.Unmarshal(raw, &doc); err != nil {
		return nil, err
	}
	delete(doc, "review")
	stripQuotes(doc)
	if evaluatorMin < 1 {
		evaluatorMin = 1
	}
	doc["evaluator_min_version"] = evaluatorMin
	return json.Marshal(doc)
}

func stripQuotes(v any) {
	switch t := v.(type) {
	case map[string]any:
		for k, c := range t {
			if k == "source" {
				if s, ok := c.(map[string]any); ok {
					delete(s, "quote")
				}
			}
			stripQuotes(c)
		}
	case []any:
		for _, c := range t {
			stripQuotes(c)
		}
	}
}
