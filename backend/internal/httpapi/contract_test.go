package httpapi_test

import (
	"bytes"
	"encoding/json"
	"strings"
	"sync"
	"testing"

	"github.com/amanroy3901/job-vac/backend/internal/testutil"
	"github.com/santhosh-tekuri/jsonschema/v6"
	"gopkg.in/yaml.v3"
)

// The contract: docs/api/openapi.yaml. Responses in the integration tests are validated against its component
// schemas (OpenAPI 3.1 schemas are JSON Schema 2020-12), and every documented operation must be routed.

const specURL = "https://api.sarkarimatch.in/openapi.json"

var (
	specOnce sync.Once
	specDoc  map[string]any
	specComp *jsonschema.Compiler
	specErr  error
	schemas  sync.Map
)

func loadSpec(t *testing.T) map[string]any {
	t.Helper()
	specOnce.Do(func() {
		var y any
		if specErr = yaml.Unmarshal(testutil.ReadDoc(t, "docs/api/openapi.yaml"), &y); specErr != nil {
			return
		}
		b, err := json.Marshal(y)
		if err != nil {
			specErr = err
			return
		}
		_ = json.Unmarshal(b, &specDoc)
		doc, err := jsonschema.UnmarshalJSON(bytes.NewReader(b))
		if err != nil {
			specErr = err
			return
		}
		specComp = jsonschema.NewCompiler()
		specErr = specComp.AddResource(specURL, doc)
	})
	if specErr != nil {
		t.Fatalf("load openapi: %v", specErr)
	}
	return specDoc
}

// validateSchema checks body against components.schemas.<name>.
func validateSchema(t *testing.T, name string, body []byte) {
	t.Helper()
	loadSpec(t)
	s, ok := schemas.Load(name)
	if !ok {
		c, err := specComp.Compile(specURL + "#/components/schemas/" + name)
		if err != nil {
			t.Fatalf("compile %s: %v", name, err)
		}
		schemas.Store(name, c)
		s = c
	}
	inst, err := jsonschema.UnmarshalJSON(bytes.NewReader(body))
	if err != nil {
		t.Fatalf("%s: response is not JSON: %v\n%s", name, err, body)
	}
	if err := s.(*jsonschema.Schema).Validate(inst); err != nil {
		t.Errorf("response does not match %s: %v\nbody: %.1500s", name, err, body)
	}
}

// specOperations lists "METHOD /path" for every operation in the contract.
func specOperations(t *testing.T) []string {
	doc := loadSpec(t)
	var ops []string
	for path, item := range doc["paths"].(map[string]any) {
		for method := range item.(map[string]any) {
			switch method {
			case "get", "post", "put", "patch", "delete":
				ops = append(ops, strings.ToUpper(method)+" "+path)
			}
		}
	}
	return ops
}

func TestSpecLoadsAndSchemasCompile(t *testing.T) {
	loadSpec(t)
	if n := len(specDoc["paths"].(map[string]any)); n != 36 {
		t.Errorf("expected 36 paths in openapi.yaml, got %d (update the route table and this test together)", n)
	}
	for _, name := range []string{"AppConfig", "CatalogHead", "CatalogBatch", "CatalogSnapshot", "Notice", "SessionResponse",
		"User", "Entitlement", "Device", "Problem", "FamilyLink", "ReferenceBundle"} {
		if _, err := specComp.Compile(specURL + "#/components/schemas/" + name); err != nil {
			t.Errorf("%s: %v", name, err)
		}
	}
}
