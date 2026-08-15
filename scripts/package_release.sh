#!/usr/bin/env bash
# Build, package, and checksum release APKs for Codeberg publishing.
# Always runs Android unit tests and cross-app parity (./scripts/check_parity.sh).
#
# Usage:
#   ./scripts/package_release.sh
#   ./scripts/package_release.sh --version 1.8.0
#   ./scripts/package_release.sh --check-only
#   ./scripts/package_release.sh --skip-build   # package existing Gradle outputs
#   ./scripts/package_release.sh --check-metadata
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="$ROOT/android"
CHECK_ONLY=0
SKIP_BUILD=0
CHECK_METADATA=0
VERSION=""

usage() {
  cat <<'EOF'
Usage: package_release.sh [options]

Options:
  --version <ver>   Override version read from android/app/build.gradle.kts
  --check-only      Run tests and asset checks only; do not build or package
  --skip-build      Skip Gradle builds; copy existing APK outputs only
  --check-metadata  Run scripts/check_release_metadata.sh after packaging
  -h, --help        Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="${2:?--version requires a value}"
      shift 2
      ;;
    --check-only)
      CHECK_ONLY=1
      shift
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --check-metadata)
      CHECK_METADATA=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

read_version_from_gradle() {
  sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$ANDROID/app/build.gradle.kts" | head -1
}

if [[ -z "$VERSION" ]]; then
  VERSION="$(read_version_from_gradle)"
fi
if [[ -z "$VERSION" ]]; then
  echo "Could not read versionName from android/app/build.gradle.kts" >&2
  exit 1
fi

run_in_devenv() {
  local cmd="$1"
  if [[ -n "${DEVENV_IN_SHELL:-}" ]]; then
    bash -lc "$cmd"
  elif command -v devenv >/dev/null 2>&1; then
    devenv shell bash -lc "$cmd"
  else
    bash -lc "$cmd"
  fi
}

echo "==> Chompass release packaging for version ${VERSION}"

echo "==> Running unit tests"
run_in_devenv 'cd android && ./gradlew test'

echo "==> Cross-app parity (PWA tests, typecheck, contract schemas)"
"$ROOT/scripts/check_parity.sh"

if [[ "$CHECK_ONLY" -eq 0 ]]; then
  echo "==> Updating README and release screenshots"
  "$ROOT/scripts/export_release_screenshots.sh"
fi

if [[ "$CHECK_ONLY" -eq 1 ]]; then
  echo "Check-only mode complete."
  exit 0
fi

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  echo "==> Building release APKs"
  run_in_devenv 'cd android && ./gradlew :app:assembleRelease'
fi

# Codeberg quota policy: ship only the universal APK (+ SHA256SUMS).
# Per-ABI splits may still be produced by Gradle; they are not packaged/uploaded.
declare -a ASSETS=(
  "universal|android/app/build/outputs/apk/release/app-universal-release.apk|Chompass-fdroid-${VERSION}.apk"
)

declare -a DEST_FILES=()
echo "==> Copying APKs to releases/"
mkdir -p "$ROOT/releases"
MISSING=0
for entry in "${ASSETS[@]}"; do
  IFS='|' read -r _abi src rel <<<"$entry"
  src_path="$ROOT/$src"
  dest_path="$ROOT/releases/$rel"
  if [[ ! -f "$src_path" ]]; then
    echo "Missing $src_path" >&2
    MISSING=1
    continue
  fi
  cp "$src_path" "$dest_path"
  DEST_FILES+=("$dest_path")
  echo "  $rel"
done
if [[ "$MISSING" -ne 0 ]]; then
  echo "Universal release APK missing. Rebuild with:" >&2
  echo "  cd android && ./gradlew :app:assembleRelease" >&2
  echo "  # local single-ABI smoke test only: -PreleaseAbi=arm64-v8a" >&2
  exit 1
fi

CHECKSUMS="$ROOT/releases/SHA256SUMS"
echo "==> Writing SHA256SUMS"
sha256sum "${DEST_FILES[@]}" | sed "s|$ROOT/releases/||" > "$CHECKSUMS"
cat "$CHECKSUMS"

if [[ "$CHECK_METADATA" -eq 1 ]]; then
  echo "==> Checking release metadata consistency"
  "$ROOT/scripts/check_release_metadata.sh"
fi

cat <<EOF

Packaging complete for Chompass ${VERSION}.

Next steps:
  1. Bump versionCode / versionName in android/app/build.gradle.kts (if not done yet)
  2. Update docs/CHANGELOG.md
  3. Commit (include docs/screenshots/ if UI changed), tag, and push:
       git tag -a v${VERSION} -m "Chompass ${VERSION}"
       git push origin v${VERSION}
  4. Publish to Codeberg (also redeploys Codeberg Pages):
       export CODEBERG_TOKEN='...'
       ./scripts/manage_release_assets.sh list
       ./scripts/manage_release_assets.sh keep-latest -y   # delete older releases
       ./scripts/publish_release.sh ${VERSION}
       # skip site redeploy: ./scripts/publish_release.sh ${VERSION} --skip-pages

Optional metadata guard before tagging:
  devenv tasks run release:check-metadata
EOF
