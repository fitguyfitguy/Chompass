#!/usr/bin/env bash
# Publish a tagged release to Codeberg with APK assets.
#
# One-time setup — create a token at:
#   https://codeberg.org/user/settings/applications
# (scopes: read:user + write:repository), then either:
#
#   export CODEBERG_TOKEN='your-token'
#   ./scripts/publish_release.sh 1.0.0
#
# or persist a login:
#   nix shell nixpkgs#tea -c tea logins add -n codeberg -u https://codeberg.org -t "$CODEBERG_TOKEN"
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-1.0.0}"
TAG="v${VERSION}"
CHECKSUMS="$ROOT/SHA256SUMS"
TOKEN="${CODEBERG_TOKEN:-${GITEA_SERVER_TOKEN:-}}"
ASSETS=(
  "$ROOT/NoFUD-${VERSION}.apk"
  "$ROOT/NoFUD-${VERSION}-arm64-v8a.apk"
  "$ROOT/NoFUD-${VERSION}-armeabi-v7a.apk"
  "$ROOT/NoFUD-${VERSION}-x86_64.apk"
  "$CHECKSUMS"
)

run_tea() {
  if command -v tea >/dev/null 2>&1; then
    tea "$@"
  else
    nix shell nixpkgs#tea -c tea "$@"
  fi
}

MISSING=0
for ASSET in "${ASSETS[@]}"; do
  if [[ ! -f "$ASSET" ]]; then
    echo "Missing $ASSET — build/package release artifacts first." >&2
    MISSING=1
  fi
done
if [[ "$MISSING" -ne 0 ]]; then
  exit 1
fi

if [[ -n "$TOKEN" ]] && ! run_tea logins list 2>/dev/null | rg -q 'codeberg'; then
  run_tea logins add -n codeberg -u https://codeberg.org -t "$TOKEN"
fi

if ! run_tea logins list 2>/dev/null | rg -q 'codeberg'; then
  cat >&2 <<'EOF'
No Codeberg login configured.

1. Create a token: https://codeberg.org/user/settings/applications
   Scopes: read:user (required by tea) + write:repository (releases/assets)

2. Export it and re-run:
   export CODEBERG_TOKEN='paste-token-here'
   ./scripts/publish_release.sh 1.0.0

Or persist the login once:
   nix shell nixpkgs#tea -c tea logins add -n codeberg -u https://codeberg.org -t "$CODEBERG_TOKEN"
EOF
  exit 1
fi

NOTES="$(awk '/^## \['"${VERSION}"'\]/{flag=1; next} /^## \[/{flag=0} flag' "$ROOT/CHANGELOG.md")"

CREATE_ARGS=(
  --login codeberg
  --repo fitguy/NoFUD
  --tag "$TAG"
  --title "NoFUD ${VERSION}"
  --note "$NOTES"
)
for ASSET in "${ASSETS[@]}"; do
  CREATE_ARGS+=(--asset "$ASSET")
done

run_tea releases create \
  "${CREATE_ARGS[@]}" \
  || {
    echo >&2
    echo "If you see 'target couldn't be found', enable Releases in the repo:" >&2
    echo "  https://codeberg.org/fitguy/NoFUD/settings  → Features → Releases" >&2
    exit 1
  }

echo "Published: https://codeberg.org/fitguy/NoFUD/releases/tag/${TAG}"
