# Sarkari Match backend. `make help` lists targets. Needs Go 1.24+ and PostgreSQL 16 at DATABASE_URL.
SHELL := /bin/bash
GO ?= go
BIN := bin/sarkari
ENV_FILE ?= .env
-include $(ENV_FILE)
export

.PHONY: help build run migrate migrate-down migrate-status admin test test-unit test-race lint fmt tidy schema-sync docker

help: ## list targets
	@grep -E '^[a-z-]+:.*##' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-16s %s\n", $$1, $$2}'

build: ## build the single binary
	CGO_ENABLED=0 $(GO) build -trimpath -ldflags="-s -w" -o $(BIN) ./cmd/sarkari

run: build ## migrate and serve ROLE=all locally (api + admin + worker tick loop)
	$(BIN) migrate up && $(BIN) serve

migrate: build ## apply migrations
	$(BIN) migrate up

migrate-down: build ## roll back one migration
	$(BIN) migrate down

migrate-status: build ## show migration status
	$(BIN) migrate status

admin: build ## add an admin: make admin EMAIL=you@example.com ROLE=owner
	$(BIN) admin add $(EMAIL) $(or $(ROLE),reviewer)

test: ## unit + integration tests (integration tests need TEST_DATABASE_URL, else they skip)
	$(GO) test -count=1 ./...

test-unit: ## unit tests only (integration tests skip)
	TEST_DATABASE_URL= DATABASE_URL= $(GO) test -count=1 ./...

test-race: ## tests with the race detector
	$(GO) test -race -count=1 ./...

lint: ## gofmt check + go vet (+ staticcheck when installed)
	@test -z "$$(gofmt -l .)" || (gofmt -l . && echo "gofmt: files need formatting" && exit 1)
	$(GO) vet ./...
	@if command -v staticcheck >/dev/null; then staticcheck ./...; else echo "staticcheck not installed: go install honnef.co/go/tools/cmd/staticcheck@2025.1.1"; fi

fmt: ## format code
	gofmt -w .

tidy: ## go mod tidy
	$(GO) mod tidy

schema-sync: ## re-copy rule.schema.json from docs (a test fails on drift)
	cp ../docs/backend/rule.schema.json internal/rules/schema/rule.schema.json

docker: ## build the distroless image
	docker build -t sarkari-match-backend:dev .
