#!/usr/bin/env bash
# Build the Hugo project site, copy the web/app/ PWA into public/app/, and
# force-push website/public to the orphan `pages` branch for Codeberg Pages
# (git-pages webhook deploy — no Actions runner). Lands the PWA at
# fitguy.codeberg.page/NoFUD/app/ alongside the marketing site.
#
# One-time Codeberg setup (repo Settings → Webhooks → Add webhook):
#   Type: Forgejo
#   Target URL: https://fitguy.codeberg.page/NoFUD/
#   Branch filter: pages
# Do not use "Test delivery" (it fails by design). Push this branch instead.
#
# SSH: uses Host alias codeberg-fitguy from ~/.ssh/config so the agent does not
# pick the KewLE key for bare codeberg.org. Override with PAGES_SSH_HOST or
# PAGES_PUSH_URL if needed.
#
# Usage (from repo root, with hugo on PATH — e.g. devenv shell):
#   ./scripts/deploy_pages.sh
#   ./scripts/deploy_pages.sh --dry-run
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASE_URL="${PAGES_BASE_URL:-https://fitguy.codeberg.page/NoFUD/}"
REMOTE="${PAGES_REMOTE:-origin}"
BRANCH="${PAGES_BRANCH:-pages}"
# Prefer fitguy SSH host alias (see ~/.ssh/config Host codeberg-fitguy).
SSH_HOST="${PAGES_SSH_HOST:-codeberg-fitguy}"
DRY_RUN=0

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -h|--help)
      sed -n '2,20p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

if ! command -v hugo >/dev/null 2>&1; then
  echo "hugo not found. Run inside devenv shell, or: nix-shell -p hugo --run './scripts/deploy_pages.sh'" >&2
  exit 1
fi

if ! command -v rsync >/dev/null 2>&1; then
  echo "rsync not found (needed to copy web/app/ into the pages tree). Run inside devenv shell, or: nix-shell -p rsync --run './scripts/deploy_pages.sh'" >&2
  exit 1
fi

echo "==> Building site (baseURL=$BASE_URL)"
rm -rf website/public website/resources
hugo --minify -s website --baseURL "$BASE_URL"

PUBLIC="$ROOT/website/public"
if [[ ! -f "$PUBLIC/index.html" ]]; then
  echo "Build missing $PUBLIC/index.html" >&2
  exit 1
fi

if [[ ! -f "$ROOT/web/app/index.html" ]]; then
  echo "Missing $ROOT/web/app/index.html — refusing to deploy without the PWA app shell" >&2
  exit 1
fi

echo "==> Copying PWA (web/app/) into $PUBLIC/app/"
mkdir -p "$PUBLIC/app"
rsync -a --delete "$ROOT/web/app/" "$PUBLIC/app/"

REMOTE_URL="$(git remote get-url "$REMOTE")"
# Rewrite bare codeberg.org → fitguy host alias so ssh-agent does not auth as KewLE.
if [[ -n "${PAGES_PUSH_URL:-}" ]]; then
  PUSH_URL="$PAGES_PUSH_URL"
elif [[ "$REMOTE_URL" =~ ^(ssh://git@|git@)codeberg\.org[/:] ]]; then
  PUSH_URL="$(printf '%s\n' "$REMOTE_URL" | sed -E "s#(ssh://git@|git@)codeberg\\.org#\\1${SSH_HOST}#")"
elif [[ "$REMOTE_URL" =~ codeberg-(kewl|alge) ]]; then
  PUSH_URL="$(printf '%s\n' "$REMOTE_URL" | sed -E "s#codeberg-(kewl|alge)#${SSH_HOST}#")"
else
  PUSH_URL="$REMOTE_URL"
fi

COMMIT_MSG="Deploy site $(date -u +%Y-%m-%dT%H:%M:%SZ)"

WORKDIR="$(mktemp -d)"
cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT

echo "==> Preparing orphan $BRANCH branch"
git init -q -b "$BRANCH" "$WORKDIR"
cp -a "$PUBLIC"/. "$WORKDIR"/
rm -f "$WORKDIR/.hugo_build.lock" 2>/dev/null || true

git -C "$WORKDIR" config user.name "$(git config user.name || echo 'NoFUD Pages')"
git -C "$WORKDIR" config user.email "$(git config user.email || echo 'pages@localhost')"
git -C "$WORKDIR" add -A
if git -C "$WORKDIR" diff --cached --quiet; then
  echo "Nothing to deploy (empty tree)." >&2
  exit 1
fi
git -C "$WORKDIR" commit -q -m "$COMMIT_MSG"
git -C "$WORKDIR" remote add origin "$PUSH_URL"

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "==> Dry run: would force-push $BRANCH via $PUSH_URL"
  git -C "$WORKDIR" log -1 --oneline
  find "$WORKDIR" -maxdepth 2 -type f ! -path '*/.git/*' | head -30
  exit 0
fi

echo "==> Force-pushing $BRANCH as fitguy via $PUSH_URL"
git -C "$WORKDIR" push -f origin "HEAD:refs/heads/$BRANCH"

echo "Done. Site: $BASE_URL"
echo "If this is the first deploy, ensure the Forgejo webhook (branch filter: pages) is configured."
