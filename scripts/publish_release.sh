#!/usr/bin/env bash
# Publish a tagged release to Codeberg with APK assets.
# Requires: tea CLI logged in (tea login add --name codeberg --url https://codeberg.org)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-1.0.0}"
TAG="v${VERSION}"
APK="$ROOT/NoFUD-${VERSION}.apk"
CHECKSUMS="$ROOT/SHA256SUMS"

if [[ ! -f "$APK" ]]; then
  echo "Missing $APK — run: devenv tasks run build:release" >&2
  exit 1
fi

if ! command -v tea >/dev/null 2>&1; then
  echo "Install tea (e.g. nix shell nixpkgs#tea) and run: tea login add --name codeberg --url https://codeberg.org" >&2
  exit 1
fi

NOTES="$(awk '/^## \['"${VERSION}"'\]/{flag=1; next} /^## \[/{flag=0} flag' "$ROOT/CHANGELOG.md")"

tea releases create \
  --repo fitguy/nofud \
  --tag "$TAG" \
  --title "NoFUD ${VERSION}" \
  --notes "$NOTES" \
  --asset "$APK" \
  --asset "$CHECKSUMS"

echo "Published: https://codeberg.org/fitguy/nofud/releases/tag/${TAG}"
