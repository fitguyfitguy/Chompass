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
#   ./scripts/publish_release.sh 1.0.0 --assets-only   # resume partial upload
#
# or persist a login:
#   nix shell nixpkgs#tea -c tea logins add -n codeberg -u https://codeberg.org -t "$CODEBERG_TOKEN"
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CODEBERG_REPO="${CODEBERG_REPO:-fitguy/nofud}"
LOGIN="${CODEBERG_LOGIN:-codeberg}"
WITH_SCREENSHOTS=0
ASSETS_ONLY=0
POSITIONAL=()
for arg in "$@"; do
  case "$arg" in
    --with-screenshots)
      WITH_SCREENSHOTS=1
      ;;
    --assets-only)
      ASSETS_ONLY=1
      ;;
    -h|--help)
      cat <<'EOF'
Usage: publish_release.sh <version> [options]

Options:
  --with-screenshots   Also attach release-screenshots/*.png (needs extra quota)
  --assets-only        Skip release creation; upload missing assets to an existing tag

Uploads APKs in batches (fdroid, checksums) so a quota error mid-run can be
resumed with --assets-only after freeing space:
  ./scripts/manage_release_assets.sh list
  ./scripts/manage_release_assets.sh prune-abi-splits --before v1.6.0
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

FDROID_ASSETS=(
  "$ROOT/NoFUD-fdroid-${VERSION}.apk"
  "$ROOT/NoFUD-fdroid-${VERSION}-arm64-v8a.apk"
  "$ROOT/NoFUD-fdroid-${VERSION}-armeabi-v7a.apk"
  "$ROOT/NoFUD-fdroid-${VERSION}-x86_64.apk"
)
CHECKSUM_ASSETS=("$CHECKSUMS")
SCREENSHOT_ASSETS=()

if [[ "$WITH_SCREENSHOTS" -eq 1 ]]; then
  shopt -s nullglob
  SCREENSHOT_ASSETS=("$SCREENSHOT_DIR"/*.png)
  shopt -u nullglob
  if [[ ${#SCREENSHOT_ASSETS[@]} -eq 0 ]]; then
    echo "No PNGs in $SCREENSHOT_DIR — run devenv tasks run release:screenshots first." >&2
    exit 1
  fi
fi

run_tea() {
  if command -v tea >/dev/null 2>&1; then
    tea "$@"
  else
    nix shell nixpkgs#tea -c tea "$@"
  fi
}

quota_help() {
  cat >&2 <<'EOF'

Codeberg release attachment quota exceeded.

1. Inspect current attachments:
     ./scripts/manage_release_assets.sh list

2. Free space by removing old per-ABI splits (universal APKs + SHA256SUMS stay):
     ./scripts/manage_release_assets.sh prune-abi-splits --before v1.6.0

3. Resume this publish (uploads only missing assets):
     ./scripts/publish_release.sh VERSION --assets-only

Request a quota increase (free for libre projects):
  https://codeberg.org/Codeberg-e.V./requests
EOF
}

release_exists() {
  run_tea releases list \
    --login "$LOGIN" \
    --repo "$CODEBERG_REPO" \
    -o simple 2>/dev/null \
    | rg -q "^[[:space:]]*${TAG}[[:space:]]"
}

list_uploaded_names() {
  run_tea releases assets list \
    --login "$LOGIN" \
    --repo "$CODEBERG_REPO" \
    "$TAG" \
    -o simple 2>/dev/null \
    | awk '{print $1}'
}

upload_assets() {
  local label="$1"
  shift
  local -a files=("$@")
  local -a missing=()
  local uploaded
  uploaded="$(list_uploaded_names || true)"

  for file in "${files[@]}"; do
    local base
    base="$(basename "$file")"
    if ! echo "$uploaded" | rg -qx "$base"; then
      missing+=("$file")
    fi
  done

  if [[ ${#missing[@]} -eq 0 ]]; then
    echo "==> $label (already complete)"
    return 0
  fi

  echo "==> Uploading $label (${#missing[@]} file(s))"
  local err
  if ! err="$(run_tea releases assets create \
    --login "$LOGIN" \
    --repo "$CODEBERG_REPO" \
    "$TAG" \
    "${missing[@]}" 2>&1)"; then
    if echo "$err" | rg -qi 'quota exceeded'; then
      quota_help
    else
      echo "$err" >&2
    fi
    return 1
  fi

  for file in "${missing[@]}"; do
    echo "  uploaded $(basename "$file")"
  done
}

MISSING=0
for ASSET in "${PLAY_ASSETS[@]}" "${FDROID_ASSETS[@]}" "${CHECKSUM_ASSETS[@]}" "${SCREENSHOT_ASSETS[@]}"; do
  if [[ ! -f "$ASSET" ]]; then
    echo "Missing $ASSET — build/package release artifacts first." >&2
    MISSING=1
  fi
done
if [[ "$MISSING" -ne 0 ]]; then
  exit 1
fi

if [[ -n "$TOKEN" ]] && ! run_tea logins list 2>/dev/null | rg -q "$LOGIN"; then
  run_tea logins add -n "$LOGIN" -u https://codeberg.org -t "$TOKEN"
fi

if ! run_tea logins list 2>/dev/null | rg -q "$LOGIN"; then
  cat >&2 <<EOF
No Codeberg login configured.

1. Create a token: https://codeberg.org/user/settings/applications
   Scopes: read:user (required by tea) + write:repository (releases/assets)

2. Export it and re-run:
   export CODEBERG_TOKEN='paste-token-here'
   ./scripts/publish_release.sh 1.0.0

Or persist the login once:
   nix shell nixpkgs#tea -c tea logins add -n codeberg -u https://codeberg.org -t "\$CODEBERG_TOKEN"
EOF
  exit 1
fi

NOTES="$(awk '/^## \['"${VERSION}"'\]/{flag=1; next} /^## \[/{flag=0} flag' "$ROOT/CHANGELOG.md")"

if [[ "$ASSETS_ONLY" -eq 0 ]]; then
  if release_exists; then
    echo "Release $TAG already exists — uploading any missing assets."
  else
    echo "==> Creating release $TAG"
    err=""
    if ! err="$(run_tea releases create \
      --login "$LOGIN" \
      --repo "$CODEBERG_REPO" \
      --tag "$TAG" \
      --title "NoFUD ${VERSION}" \
      --note "$NOTES" 2>&1)"; then
      if echo "$err" | rg -qi 'already a release'; then
        echo "Release $TAG already exists — uploading any missing assets."
      elif echo "$err" | rg -qi 'quota exceeded'; then
        quota_help
        exit 1
      else
        echo "$err" >&2
        echo >&2
        echo "If you see 'target couldn't be found', enable Releases in the repo:" >&2
        echo "  https://codeberg.org/fitguy/NoFUD/settings  → Features → Releases" >&2
        exit 1
      fi
    fi
  fi
else
  if ! release_exists; then
    echo "Release $TAG does not exist. Run without --assets-only first." >&2
    exit 1
  fi
  echo "==> Resuming asset upload for $TAG"
fi

upload_assets "release APKs" "${FDROID_ASSETS[@]}"
upload_assets "SHA256SUMS" "${CHECKSUM_ASSETS[@]}"
if [[ ${#SCREENSHOT_ASSETS[@]} -gt 0 ]]; then
  upload_assets "screenshots" "${SCREENSHOT_ASSETS[@]}"
fi

echo "Published: https://codeberg.org/fitguy/NoFUD/releases/tag/${TAG}"
