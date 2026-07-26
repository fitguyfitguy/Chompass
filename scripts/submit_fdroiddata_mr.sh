#!/usr/bin/env bash
# Refresh (or open once) the fdroiddata merge request for Chompass.
#
# Requires:
#   - glab authenticated for gitlab.com (glab auth login)
#   - OR GITLAB_TOKEN with api scope
#   - Prefer GITLAB_HOST=gitlab.com when other GitLab hosts are configured
#
# Usage:
#   ./scripts/submit_fdroiddata_mr.sh
#   BRANCH=org.codeberg.fitguy.nofud ./scripts/submit_fdroiddata_mr.sh
#   GITLAB_TOKEN=glpat-... ./scripts/submit_fdroiddata_mr.sh
#
# NEVER open a second inclusion MR while one is already open. This script:
#   1. Prefers the canonical pre-inclusion MR (!42984) while it is open
#   2. Else discovers any open fork MR that touches Chompass / NoFUD metadata
#   3. Pushes metadata onto that MR's source_branch and exits (no new MR)
#   4. Creates a new MR only when no open inclusion MR exists
#
# Canonical inclusion MR (update IID/branch if GitLab replaces the MR):
#   https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984
#   source branch: org.codeberg.fitguy.nofud

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
METADATA_SRC="$ROOT/docs/fdroid/app.chompass.yml"
# Optional override; otherwise discovered from an open fdroiddata MR.
BRANCH="${BRANCH:-}"
WORKDIR="${TMPDIR:-/tmp}/fdroiddata-chompass-$$"
APP_ID="app.chompass"
METADATA_PATH="metadata/${APP_ID}.yml"

# Pre-inclusion MR from the NoFUD → Chompass rename era. Do not invent a new
# branch (e.g. app.chompass) while this MR is still open — that created !43940.
CANONICAL_INCLUSION_MR_IID="${CANONICAL_INCLUSION_MR_IID:-42984}"
CANONICAL_INCLUSION_BRANCH="${CANONICAL_INCLUSION_BRANCH:-org.codeberg.fitguy.nofud}"

# Avoid glab multi-host auth failures (e.g. a second broken GitLab host).
export GITLAB_HOST="${GITLAB_HOST:-gitlab.com}"

if [[ ! -f "$METADATA_SRC" ]]; then
  echo "Missing $METADATA_SRC" >&2
  exit 1
fi

if ! command -v glab >/dev/null 2>&1; then
  echo "glab not found. Install GitLab CLI or open the MR manually — see docs/FDROID_SUBMISSION.md" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
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

FORK_PATH="$(glab api user 2>/dev/null | jq -r '.username // empty')"
if [[ -z "$FORK_PATH" ]]; then
  echo "gitlab.com authentication failed." >&2
  echo "Run: glab auth login --hostname gitlab.com" >&2
  echo "Or: GITLAB_TOKEN=glpat-... $0" >&2
  echo "If multiple GitLab hosts are configured, ensure GITLAB_HOST=gitlab.com" >&2
  exit 1
fi

echo "Using GitLab user: $FORK_PATH"

SOURCE_PROJECT_ID="$(glab api "projects/${FORK_PATH}%2Ffdroiddata" | jq -r '.id // empty')"
TARGET_PROJECT_ID="$(glab api "projects/fdroid%2Ffdroiddata" | jq -r '.id // empty')"

if [[ -z "$SOURCE_PROJECT_ID" || -z "$TARGET_PROJECT_ID" ]]; then
  echo "Could not resolve GitLab project IDs for fork MR." >&2
  exit 1
fi

EXISTING_MR_URL=""
EXISTING_MR_IID=""

use_mr() {
  local iid="$1"
  local src_branch="$2"
  local web_url="$3"
  EXISTING_MR_IID="$iid"
  EXISTING_MR_URL="$web_url"
  if [[ -z "$BRANCH" ]]; then
    BRANCH="$src_branch"
  fi
  echo "Found open inclusion MR !${iid} (source_branch=${src_branch})"
}

# 1) Canonical pre-inclusion MR while still open.
if [[ -n "$CANONICAL_INCLUSION_MR_IID" ]]; then
  canonical_json="$(glab api "projects/${TARGET_PROJECT_ID}/merge_requests/${CANONICAL_INCLUSION_MR_IID}" 2>/dev/null || true)"
  if [[ -n "$canonical_json" ]]; then
    c_state="$(printf '%s' "$canonical_json" | jq -r '.state // empty')"
    c_branch="$(printf '%s' "$canonical_json" | jq -r '.source_branch // empty')"
    c_url="$(printf '%s' "$canonical_json" | jq -r '.web_url // empty')"
    if [[ "$c_state" == "opened" && -n "$c_branch" ]]; then
      use_mr "$CANONICAL_INCLUSION_MR_IID" "$c_branch" "$c_url"
    fi
  fi
fi

# 2) Any other open author MR that clearly targets Chompass inclusion.
detect_existing_mr() {
  local mrs iid title desc src_branch web_url candidate changes
  mrs="$(glab api "projects/${TARGET_PROJECT_ID}/merge_requests?state=opened&author_username=${FORK_PATH}&per_page=50")"
  while IFS=$'\t' read -r iid title desc src_branch web_url; do
    [[ -z "$iid" || "$iid" == "null" ]] && continue
    if [[ -n "$EXISTING_MR_IID" && "$iid" == "$EXISTING_MR_IID" ]]; then
      continue
    fi
    candidate=""
    if printf '%s\n%s' "$title" "$desc" | grep -qiE 'chompass|nofud|app\.chompass|org\.codeberg\.fitguy\.nofud'; then
      candidate=1
    elif [[ "$src_branch" == "$APP_ID" || "$src_branch" == "$CANONICAL_INCLUSION_BRANCH" ]]; then
      candidate=1
    else
      changes="$(glab api "projects/${TARGET_PROJECT_ID}/merge_requests/${iid}/changes" 2>/dev/null || true)"
      if printf '%s' "$changes" | jq -e --arg p "$METADATA_PATH" '
          any(.changes[]?; .new_path == $p or .old_path == $p)
        ' >/dev/null 2>&1; then
        candidate=1
      fi
    fi
    if [[ -n "$candidate" ]]; then
      if [[ -n "$EXISTING_MR_IID" && "$iid" != "$EXISTING_MR_IID" ]]; then
        echo "ERROR: another open Chompass inclusion MR !${iid} exists alongside !${EXISTING_MR_IID}." >&2
        echo "  Keep only one MR. Close the duplicate, then re-run." >&2
        echo "  Canonical: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/${CANONICAL_INCLUSION_MR_IID}" >&2
        echo "  Extra:     ${web_url}" >&2
        exit 1
      fi
      use_mr "$iid" "$src_branch" "$web_url"
      return 0
    fi
  done < <(printf '%s' "$mrs" | jq -r '.[] | [.iid, .title, (.description // ""), .source_branch, .web_url] | @tsv')
  return 1
}

if [[ -z "$EXISTING_MR_IID" ]]; then
  detect_existing_mr || true
else
  # Still scan so a duplicate open MR fails loudly instead of being ignored.
  detect_existing_mr || true
fi

# While the app is not yet on fdroid master, default to the canonical branch
# rather than inventing app.chompass (that spawned duplicate !43940).
if [[ -z "$EXISTING_MR_IID" ]]; then
  on_master="$(glab api "projects/${TARGET_PROJECT_ID}/repository/files/${METADATA_PATH//\//%2F}?ref=master" 2>/dev/null | jq -r '.file_name // empty' || true)"
  if [[ -z "$on_master" ]]; then
    BRANCH="${BRANCH:-$CANONICAL_INCLUSION_BRANCH}"
    echo "App not on fdroid master yet; using pre-inclusion branch: $BRANCH"
    echo "If MR !${CANONICAL_INCLUSION_MR_IID} is still the review vehicle, open/update that MR — do not create a second one."
  else
    BRANCH="${BRANCH:-$APP_ID}"
  fi
else
  BRANCH="${BRANCH:-$CANONICAL_INCLUSION_BRANCH}"
fi

echo "Using branch: $BRANCH"

rm -rf "$WORKDIR"
git clone --depth=1 "https://gitlab.com/${FORK_PATH}/fdroiddata.git" "$WORKDIR"
cd "$WORKDIR"

# Prefer updating the existing MR source branch tip rather than recreating from default.
if git fetch origin "$BRANCH" --depth=1 2>/dev/null && git rev-parse --verify "origin/$BRANCH" >/dev/null 2>&1; then
  git checkout -B "$BRANCH" "origin/$BRANCH"
else
  git checkout -B "$BRANCH"
fi

cp "$METADATA_SRC" "$METADATA_PATH"
git add "$METADATA_PATH"

VERSION_NAME="$(awk '/^[[:space:]]*versionName:/{print $2; exit}' "$METADATA_SRC")"
if git diff --cached --quiet; then
  echo "${METADATA_PATH} unchanged in fork."
else
  if [[ -n "$EXISTING_MR_IID" && -n "$VERSION_NAME" ]]; then
    git commit -m "Update ${APP_ID} to ${VERSION_NAME}"
  else
    git commit -m "$(cat <<'EOF'
New App: Chompass (app.chompass)

Ad-free privacy-focused calorie tracker. MIT, Codeberg upstream.
EOF
)"
  fi
fi

git push -u origin "$BRANCH"

if [[ -n "$EXISTING_MR_URL" ]]; then
  echo "Updated existing merge request: $EXISTING_MR_URL"
  echo "Done."
  exit 0
fi

# Safety: never create a new MR if the canonical inclusion IID is still open
# (detection should have caught it; this is a last-resort guard).
if [[ -n "$CANONICAL_INCLUSION_MR_IID" ]]; then
  c_state="$(glab api "projects/${TARGET_PROJECT_ID}/merge_requests/${CANONICAL_INCLUSION_MR_IID}" 2>/dev/null | jq -r '.state // empty' || true)"
  if [[ "$c_state" == "opened" ]]; then
    echo "ERROR: refusing to open a new MR while !${CANONICAL_INCLUSION_MR_IID} is still open." >&2
    echo "  Push went to branch ${BRANCH}; update https://gitlab.com/fdroid/fdroiddata/-/merge_requests/${CANONICAL_INCLUSION_MR_IID}" >&2
    exit 1
  fi
fi

MR_BODY_FILE="$ROOT/docs/FDROID_SUBMISSION.md"
# Extract the fenced MR body block from FDROID_SUBMISSION.md
MR_BODY="$(awk '/^```markdown$/{flag=1;next} /^```$/{if(flag){exit}} flag' "$MR_BODY_FILE")"

# Use API to avoid glab's interactive "base repository" prompt when run inside the fork clone.
MR_JSON="$(glab api -X POST "projects/${SOURCE_PROJECT_ID}/merge_requests" \
  -f source_branch="$BRANCH" \
  -f target_branch=master \
  -f target_project_id="$TARGET_PROJECT_ID" \
  -f title='New App: app.chompass' \
  -f description="$MR_BODY")"

MR_URL="$(printf '%s' "$MR_JSON" | jq -r '.web_url // empty')"
if [[ -n "$MR_URL" ]]; then
  echo "Merge request: $MR_URL"
else
  echo "$MR_JSON"
fi

echo "Done."
