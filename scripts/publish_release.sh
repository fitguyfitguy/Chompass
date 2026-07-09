#!/usr/bin/env bash
# Publish a tagged release to Codeberg with APK assets.
#
# One-time setup — create a token at:
#   https://codeberg.org/user/settings/applications
# (scopes: read:user + write:repository), then either:
#
#   export CODEBERG_TOKEN='your-token'
#   ./scripts/publish_release.sh 1.0.0
#   ./scripts/publish_release.sh 1.0.0 --with-screenshots
#
# or persist a login:
#   nix shell nixpkgs#tea -c tea logins add -n codeberg -u https://codeberg.org -t "$CODEBERG_TOKEN"
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WITH_SCREENSHOTS=0
POSITIONAL=()
for arg in "$@"; do
  case "$arg" in
    --with-screenshots)
      WITH_SCREENSHOTS=1
      ;;
    -h|--help)
      cat <<'EOF'
Usage: publish_release.sh <version> [--with-screenshots]

Publish APK assets (and optionally release-screenshots/*.png) to Codeberg.
EOF
      exit 0
      ;;
    *)
      POSITIONAL+=("$arg")
      ;;
  esac
done

VERSION="${POSITIONAL[0]:-1.0.0}"
TAG="v${VERSION}"
CHECKSUMS="$ROOT/SHA256SUMS"
SCREENSHOT_DIR="$ROOT/release-screenshots"
TOKEN="${CODEBERG_TOKEN:-${GITEA_SERVER_TOKEN:-}}"
ASSETS=(
  # Play flavor
  "$ROOT/NoFUD-play-${VERSION}.apk"
  "$ROOT/NoFUD-play-${VERSION}-arm64-v8a.apk"
  "$ROOT/NoFUD-play-${VERSION}-armeabi-v7a.apk"
  "$ROOT/NoFUD-play-${VERSION}-x86_64.apk"

  # F-Droid flavor (no proprietary Play Core)
  "$ROOT/NoFUD-fdroid-${VERSION}.apk"
  "$ROOT/NoFUD-fdroid-${VERSION}-arm64-v8a.apk"
  "$ROOT/NoFUD-fdroid-${VERSION}-armeabi-v7a.apk"
  "$ROOT/NoFUD-fdroid-${VERSION}-x86_64.apk"

  "$CHECKSUMS"
)

if [[ "$WITH_SCREENSHOTS" -eq 1 ]]; then
  shopt -s nullglob
  SCREENSHOTS=("$SCREENSHOT_DIR"/*.png)
  shopt -u nullglob
  if [[ ${#SCREENSHOTS[@]} -eq 0 ]]; then
    echo "No PNGs in $SCREENSHOT_DIR — run devenv tasks run release:screenshots first." >&2
    exit 1
  fi
  ASSETS+=("${SCREENSHOTS[@]}")
fi

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
