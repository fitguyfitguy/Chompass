#!/usr/bin/env bash
# Build, package, and checksum release APKs for Codeberg publishing.
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

echo "==> NoFUD release packaging for version ${VERSION}"

echo "==> Running unit tests"
run_in_devenv 'cd android && ./gradlew test'

echo "==> Checking exercise image budget"
run_in_devenv 'uv run --with pillow python scripts/optimize_exercise_images.py --check-only'

if [[ "$CHECK_ONLY" -eq 0 ]]; then
  echo "==> Updating README and release screenshots"
  "$ROOT/scripts/export_release_screenshots.sh"
fi

if [[ "$CHECK_ONLY" -eq 1 ]]; then
  echo "Check-only mode complete."
  exit 0
fi

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  echo "==> Building play + fdroid release APKs"
  run_in_devenv 'cd android && ./gradlew :app:assemblePlayRelease :app:assembleFdroidRelease'
fi

declare -a ASSETS=(
  "play|universal|android/app/build/outputs/apk/play/release/app-play-universal-release.apk|NoFUD-play-${VERSION}.apk"
  "play|arm64-v8a|android/app/build/outputs/apk/play/release/app-play-arm64-v8a-release.apk|NoFUD-play-${VERSION}-arm64-v8a.apk"
  "play|armeabi-v7a|android/app/build/outputs/apk/play/release/app-play-armeabi-v7a-release.apk|NoFUD-play-${VERSION}-armeabi-v7a.apk"
  "play|x86_64|android/app/build/outputs/apk/play/release/app-play-x86_64-release.apk|NoFUD-play-${VERSION}-x86_64.apk"
  "fdroid|universal|android/app/build/outputs/apk/fdroid/release/app-fdroid-universal-release.apk|NoFUD-fdroid-${VERSION}.apk"
  "fdroid|arm64-v8a|android/app/build/outputs/apk/fdroid/release/app-fdroid-arm64-v8a-release.apk|NoFUD-fdroid-${VERSION}-arm64-v8a.apk"
  "fdroid|armeabi-v7a|android/app/build/outputs/apk/fdroid/release/app-fdroid-armeabi-v7a-release.apk|NoFUD-fdroid-${VERSION}-armeabi-v7a.apk"
  "fdroid|x86_64|android/app/build/outputs/apk/fdroid/release/app-fdroid-x86_64-release.apk|NoFUD-fdroid-${VERSION}-x86_64.apk"
)

declare -a DEST_FILES=()
echo "==> Copying APKs to repo root"
MISSING=0
for entry in "${ASSETS[@]}"; do
  IFS='|' read -r _flavor _abi src rel <<<"$entry"
  src_path="$ROOT/$src"
  dest_path="$ROOT/$rel"
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
  echo "One or more APK outputs are missing. For a single-ABI smoke test, rebuild with:" >&2
  echo "  cd android && ./gradlew -PreleaseAbi=arm64-v8a :app:assemblePlayRelease :app:assembleFdroidRelease" >&2
  exit 1
fi

CHECKSUMS="$ROOT/SHA256SUMS"
echo "==> Writing SHA256SUMS"
sha256sum "${DEST_FILES[@]}" | sed "s|$ROOT/||" > "$CHECKSUMS"
cat "$CHECKSUMS"

if [[ "$CHECK_METADATA" -eq 1 ]]; then
  echo "==> Checking release metadata consistency"
  "$ROOT/scripts/check_release_metadata.sh"
fi

cat <<EOF

Packaging complete for NoFUD ${VERSION}.

Next steps:
  1. Bump versionCode / versionName in android/app/build.gradle.kts (if not done yet)
  2. Update CHANGELOG.md
  3. Commit (include docs/screenshots/ if UI changed), tag, and push:
       git tag -a v${VERSION} -m "NoFUD ${VERSION}"
       git push origin v${VERSION}
  4. Publish to Codeberg:
       export CODEBERG_TOKEN='...'
       ./scripts/publish_release.sh ${VERSION}

Optional metadata guard before tagging:
  devenv tasks run release:check-metadata
EOF
