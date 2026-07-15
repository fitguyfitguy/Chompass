#!/usr/bin/env bash
# Open (or refresh) the fdroiddata merge request for NoFUD.
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
METADATA_SRC="$ROOT/fdroid/org.codeberg.fitguy.nofud.yml"
BRANCH="org.codeberg.fitguy.nofud"
WORKDIR="${TMPDIR:-/tmp}/fdroiddata-nofud-$$"

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
cp "$METADATA_SRC" "metadata/org.codeberg.fitguy.nofud.yml"
git add metadata/org.codeberg.fitguy.nofud.yml

if git diff --cached --quiet; then
  echo "metadata/org.codeberg.fitguy.nofud.yml unchanged in fork."
else
  git commit -m "$(cat <<'EOF'
New App: NoFUD (org.codeberg.fitguy.nofud)

Ad-free privacy-focused calorie tracker. MIT, Codeberg upstream.
EOF
)"
fi

git push -u origin "$BRANCH"

MR_BODY_FILE="$ROOT/docs/FDROID_SUBMISSION.md"
# Extract the fenced MR body block from FDROID_SUBMISSION.md
MR_BODY="$(awk '/^```markdown$/{flag=1;next} /^```$/{if(flag){exit}} flag' "$MR_BODY_FILE")"

glab mr create \
  --repo "fdroid/fdroiddata" \
  --source-branch "$BRANCH" \
  --target-branch master \
  --title "New App: org.codeberg.fitguy.nofud" \
  --description "$MR_BODY" \
  --yes

echo "Done. MR URL should appear above."
