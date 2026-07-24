#!/usr/bin/env bash
# Open (or refresh) the fdroiddata merge request for Chompass.
#
# Requires:
#   - glab authenticated for gitlab.com (glab auth login)
#   - OR GITLAB_TOKEN with api scope
#
# Usage:
#   ./scripts/submit_fdroiddata_mr.sh
#   GITLAB_TOKEN=glpat-... ./scripts/submit_fdroiddata_mr.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
METADATA_SRC="$ROOT/docs/fdroid/app.chompass.yml"
BRANCH="app.chompass"
WORKDIR="${TMPDIR:-/tmp}/fdroiddata-chompass-$$"

if [[ ! -f "$METADATA_SRC" ]]; then
  echo "Missing $METADATA_SRC" >&2
  exit 1
fi

if ! command -v glab >/dev/null 2>&1; then
  echo "glab not found. Install GitLab CLI or open the MR manually — see docs/FDROID_SUBMISSION.md" >&2
  exit 1
fi

# glab insists on 600 for config files
for f in "$HOME/.config/glab-cli/config.yml" "$HOME/.config/glab-cli/aliases.yml"; do
  if [[ -f "$f" ]]; then
    chmod 600 "$f"
  fi
done

if [[ -n "${GITLAB_TOKEN:-}" ]]; then
  export GITLAB_TOKEN
fi

echo "Forking fdroid/fdroiddata (no-op if fork already exists)..."
glab repo fork fdroid/fdroiddata --clone=false 2>/dev/null || true

FORK_PATH="$(glab api user 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin)["username"])' 2>/dev/null || true)"
if [[ -z "$FORK_PATH" ]]; then
  echo "gitlab.com authentication failed." >&2
  echo "Run: glab auth login --hostname gitlab.com" >&2
  echo "Or: GITLAB_TOKEN=glpat-... $0" >&2
  exit 1
fi

echo "Using GitLab user: $FORK_PATH"
rm -rf "$WORKDIR"
git clone --depth=1 "https://gitlab.com/${FORK_PATH}/fdroiddata.git" "$WORKDIR"
cd "$WORKDIR"

git checkout -B "$BRANCH"
cp "$METADATA_SRC" "metadata/app.chompass.yml"
git add metadata/app.chompass.yml

if git diff --cached --quiet; then
  echo "metadata/app.chompass.yml unchanged in fork."
else
  git commit -m "$(cat <<'EOF'
New App: Chompass (app.chompass)

Ad-free privacy-focused calorie tracker. MIT, Codeberg upstream.
EOF
)"
fi

git push -u origin "$BRANCH"

MR_BODY_FILE="$ROOT/docs/FDROID_SUBMISSION.md"
# Extract the fenced MR body block from FDROID_SUBMISSION.md
MR_BODY="$(awk '/^```markdown$/{flag=1;next} /^```$/{if(flag){exit}} flag' "$MR_BODY_FILE")"

SOURCE_PROJECT_ID="$(glab api "projects/${FORK_PATH}%2Ffdroiddata" 2>/dev/null | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)"
TARGET_PROJECT_ID="$(glab api "projects/fdroid%2Ffdroiddata" 2>/dev/null | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)"

if [[ -z "$SOURCE_PROJECT_ID" || -z "$TARGET_PROJECT_ID" ]]; then
  echo "Could not resolve GitLab project IDs for fork MR." >&2
  echo "Open the MR manually:" >&2
  echo "  https://gitlab.com/${FORK_PATH}/fdroiddata/-/merge_requests/new?merge_request%5Bsource_branch%5D=${BRANCH}" >&2
  exit 1
fi

# Use API to avoid glab's interactive "base repository" prompt when run inside the fork clone.
MR_JSON="$(glab api -X POST "projects/${SOURCE_PROJECT_ID}/merge_requests" \
  -f source_branch="$BRANCH" \
  -f target_branch=master \
  -f target_project_id="$TARGET_PROJECT_ID" \
  -f title='New App: app.chompass' \
  -f description="$MR_BODY")"

MR_URL="$(printf '%s' "$MR_JSON" | sed -n 's/.*"web_url":"\([^"]*\/merge_requests\/[0-9]*\)".*/\1/p' | head -1)"
if [[ -n "$MR_URL" ]]; then
  echo "Merge request: $MR_URL"
else
  echo "$MR_JSON"
fi

echo "Done."
