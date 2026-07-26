#!/usr/bin/env bash
# Open (or refresh) the fdroiddata merge request for Chompass.
#
# Requires:
#   - glab authenticated for gitlab.com (glab auth login)
#   - OR GITLAB_TOKEN with api scope
#
# Usage:
#   ./scripts/submit_fdroiddata_mr.sh
#   BRANCH=org.codeberg.fitguy.nofud ./scripts/submit_fdroiddata_mr.sh
#   GITLAB_TOKEN=glpat-... ./scripts/submit_fdroiddata_mr.sh
#
# BRANCH defaults to the source_branch of an existing open inclusion MR
# (title/description mentions chompass, or changes touch metadata/app.chompass.yml).
# Falls back to app.chompass only when no such MR exists.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
METADATA_SRC="$ROOT/docs/fdroid/app.chompass.yml"
# Optional override; otherwise discovered from an open fdroiddata MR.
BRANCH="${BRANCH:-}"
WORKDIR="${TMPDIR:-/tmp}/fdroiddata-chompass-$$"
APP_ID="app.chompass"
METADATA_PATH="metadata/${APP_ID}.yml"

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

FORK_PATH="$(glab api user 2>/dev/null | jq -r '.username // empty')"
if [[ -z "$FORK_PATH" ]]; then
  echo "gitlab.com authentication failed." >&2
  echo "Run: glab auth login --hostname gitlab.com" >&2
  echo "Or: GITLAB_TOKEN=glpat-... $0" >&2
  exit 1
fi

echo "Using GitLab user: $FORK_PATH"

SOURCE_PROJECT_ID="$(glab api "projects/${FORK_PATH}%2Ffdroiddata" | jq -r '.id // empty')"
TARGET_PROJECT_ID="$(glab api "projects/fdroid%2Ffdroiddata" | jq -r '.id // empty')"

if [[ -z "$SOURCE_PROJECT_ID" || -z "$TARGET_PROJECT_ID" ]]; then
  echo "Could not resolve GitLab project IDs for fork MR." >&2
  exit 1
fi

# Find an existing open MR from this fork into fdroid/fdroiddata for Chompass.
EXISTING_MR_URL=""
EXISTING_MR_IID=""
detect_existing_mr() {
  local mrs candidate iid title desc src_branch changes
  mrs="$(glab api "projects/${TARGET_PROJECT_ID}/merge_requests?state=opened&author_username=${FORK_PATH}&per_page=50")"
  while IFS=$'\t' read -r iid title desc src_branch; do
    [[ -z "$iid" || "$iid" == "null" ]] && continue
    candidate=""
    if printf '%s\n%s' "$title" "$desc" | grep -qiE 'chompass|nofud|app\.chompass|org\.codeberg\.fitguy\.nofud'; then
      candidate=1
    elif [[ "$src_branch" == "$APP_ID" || "$src_branch" == "org.codeberg.fitguy.nofud" ]]; then
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
      EXISTING_MR_IID="$iid"
      EXISTING_MR_URL="$(printf '%s' "$mrs" | jq -r --argjson iid "$iid" '.[] | select(.iid == $iid) | .web_url')"
      if [[ -z "$BRANCH" ]]; then
        BRANCH="$src_branch"
      fi
      echo "Found open inclusion MR !${iid} (source_branch=${src_branch})"
      return 0
    fi
  done < <(printf '%s' "$mrs" | jq -r '.[] | [.iid, .title, (.description // ""), .source_branch] | @tsv')
  return 1
}

detect_existing_mr || true
BRANCH="${BRANCH:-$APP_ID}"
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
