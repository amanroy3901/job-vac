#!/usr/bin/env bash
# Seeds a LOCAL backend with realistic current notices through the real curation flow (admin JSON API):
# create draft → attach rule per post → approve (second reviewer when the validator asks for one) → publish
# (catalog batch + snapshot). DEV SEED DATA ONLY: the notices are modelled on real 2026/27 recruitments, but dates,
# vacancies and the PDF page numbers in `source.page` are placeholders, not checked against official PDFs.
#
# Usage (backend running with ADMIN_AUTH_MODE=dev, e.g. `make run`):
#   scripts/seed-dev.sh                    # BASE=http://localhost:8080 OWNER=founder@example.com REVIEWER=rev@example.com
#   BASE=http://localhost:8080 scripts/seed-dev.sh
#
# Idempotent-ish: a notice whose (org_code, advt_no) already exists is skipped (the API answers 409); an
# unpublished review task left over from an interrupted run is resumed. Both admins must exist; the script adds them
# with `bin/sarkari admin add` when the binary and .env are present.
set -euo pipefail

BASE=${BASE:-http://localhost:8080}
OWNER=${OWNER:-founder@example.com}
REVIEWER=${REVIEWER:-rev@example.com}
DIR="$(cd "$(dirname "$0")" && pwd)"
SEED="$DIR/seed"
BACKEND="$(cd "$DIR/.." && pwd)"

command -v jq >/dev/null || { echo "seed-dev: jq is required" >&2; exit 1; }
curl -fsS "$BASE/v1/health" >/dev/null || { echo "seed-dev: backend not reachable at $BASE" >&2; exit 1; }

# Make sure both dev admins exist (owner + a second reviewer for risk-flagged rules).
if [[ -x "$BACKEND/bin/sarkari" && -f "$BACKEND/.env" ]]; then
  (cd "$BACKEND" && set -a && . ./.env && set +a &&
    ./bin/sarkari admin add "$OWNER" owner >/dev/null 2>&1 || true
    ./bin/sarkari admin add "$REVIEWER" reviewer >/dev/null 2>&1 || true)
fi

admin() { # admin <email> <method> <path> [curl args...] → body on stdout, fails on HTTP >= 400
  local who=$1 method=$2 path=$3; shift 3
  local out code
  out=$(curl -sS -X "$method" "$BASE$path" -H "X-Admin-Email: $who" -H 'Content-Type: application/json' -w '\n%{http_code}' "$@")
  code=${out##*$'\n'}; out=${out%$'\n'*}
  if (( code >= 400 )); then echo "$out"; return 22; fi
  echo "$out"
}

# Open task for a notice draft (resume after an interrupted run).
open_task_for() { # <org_code> <advt_no>
  admin "$OWNER" GET /admin/api/tasks | jq -r --arg o "$1" --arg a "$2" \
    '.items[] | select(.draft.org_code == $o and .draft.advt_no == $a) | .id' | head -n1
}

published=0 skipped=0
for notice in "$SEED"/*.notice.json; do
  slug=$(basename "$notice" .notice.json)
  rule="$SEED/$slug.rule.json"
  org=$(jq -r .org_code "$notice"); advt=$(jq -r .advt_no "$notice")
  code=$(jq -r '.posts[0].code' "$notice")

  task=$(open_task_for "$org" "$advt")
  if [[ -z "$task" ]]; then
    if ! body=$(admin "$OWNER" POST /admin/api/notices --data-binary @"$notice"); then
      if jq -e '.status == 409' >/dev/null 2>&1 <<<"$body"; then
        echo "= $slug: already published, skipped"; skipped=$((skipped + 1)); continue
      fi
      echo "! $slug: create failed: $body" >&2; exit 1
    fi
    task=$(jq -r .id <<<"$body")
  fi

  rep=$(admin "$OWNER" PUT "/admin/api/tasks/$task/posts/$code/rule" --data-binary @"$rule")
  if ! jq -e '[.findings[] | select(.severity == "blocking")] | length == 0' >/dev/null <<<"$rep"; then
    echo "! $slug: rule has blocking findings:" >&2; jq '.findings' <<<"$rep" >&2; exit 1
  fi

  state=$(admin "$OWNER" POST "/admin/api/tasks/$task/approve" | jq -r .state)
  if [[ "$state" == "awaiting_second" ]]; then
    state=$(admin "$REVIEWER" POST "/admin/api/tasks/$task/approve" | jq -r .state)
  fi
  [[ "$state" == "approved" ]] || { echo "! $slug: unexpected state $state" >&2; exit 1; }

  seq=$(admin "$OWNER" POST "/admin/api/tasks/$task/publish" | jq -r .catalog_seq)
  echo "+ $slug: published ($org $advt) in catalog batch $seq"
  published=$((published + 1))
done

echo "seed-dev: $published published, $skipped skipped"
curl -fsS "$BASE/v1/catalog/head" | jq -c '{seq, snapshot: .snapshot.seq, batches: [.batches[].seq]}'
