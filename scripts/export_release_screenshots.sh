#!/usr/bin/env bash
# Render JVM Compose screenshot previews and export friendly PNGs for Codeberg releases.
#
# Usage:
#   ./scripts/export_release_screenshots.sh
#   ./scripts/export_release_screenshots.sh --validate   # compare only; do not update references
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="$ROOT/android"
REF_DIR="$ANDROID/app/src/screenshotTestPlayDebug/reference/org/codeberg/fitguy/nofud/ReleaseScreenshotPreviewsKt"
OUT_DIR="$ROOT/release-screenshots"
README_DIR="$ROOT/docs/screenshots"
VALIDATE_ONLY=0

usage() {
  cat <<'EOF'
Usage: export_release_screenshots.sh [options]

Options:
  --validate   Run validatePlayDebugScreenshotTest instead of updating references
  -h, --help   Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --validate)
      VALIDATE_ONLY=1
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

if [[ "$VALIDATE_ONLY" -eq 1 ]]; then
  echo "==> Validating screenshot previews"
  run_in_devenv 'cd android && ./gradlew :app:validatePlayDebugScreenshotTest'
else
  echo "==> Updating screenshot preview references"
  run_in_devenv 'cd android && ./gradlew :app:updatePlayDebugScreenshotTest'
fi

if [[ ! -d "$REF_DIR" ]]; then
  echo "Reference directory not found: $REF_DIR" >&2
  exit 1
fi

# prefix|release-screenshots filename
declare -a RELEASE_EXPORTS=(
  "HomeLightScreenshot_01-home-light|01-home-light.png"
  "ProgressLightScreenshot_02-progress-light|02-progress-light.png"
  "CoachLightScreenshot_03-coach-light|03-coach-light.png"
  "SettingsLightScreenshot_04-settings-light|04-settings-light.png"
  "HomeDarkScreenshot_05-home-dark|05-home-dark.png"
  "ProgressDarkScreenshot_06-progress-dark|06-progress-dark.png"
  "AddFoodLightScreenshot_07-add-food-light|07-add-food-light.png"
  "CoachDarkScreenshot_08-coach-dark|08-coach-dark.png"
  "SettingsDarkScreenshot_09-settings-dark|09-settings-dark.png"
  "AddFoodDarkScreenshot_10-add-food-dark|10-add-food-dark.png"
)

# Dark-mode previews only — embedded in README.md
declare -a README_EXPORTS=(
  "HomeDarkScreenshot_05-home-dark|home.png"
  "ProgressDarkScreenshot_06-progress-dark|progress.png"
  "AddFoodDarkScreenshot_10-add-food-dark|add-food.png"
  "CoachDarkScreenshot_08-coach-dark|coach.png"
  "SettingsDarkScreenshot_09-settings-dark|settings.png"
)

find_newest_ref() {
  local prefix="$1"
  find "$REF_DIR" -maxdepth 1 -name "${prefix}_*_0.png" -printf '%T@ %p\n' 2>/dev/null \
    | sort -rn | head -1 | cut -d' ' -f2- || true
}

mkdir -p "$OUT_DIR" "$README_DIR"
rm -f "$OUT_DIR"/*.png "$README_DIR"/*.png

echo "==> Exporting release screenshots to release-screenshots/ and docs/screenshots/"
MISSING=0
for entry in "${RELEASE_EXPORTS[@]}"; do
  IFS='|' read -r prefix release_name <<<"$entry"
  src="$(find_newest_ref "$prefix")"
  if [[ -z "$src" ]]; then
    echo "Missing reference for $prefix" >&2
    MISSING=1
    continue
  fi
  cp "$src" "$OUT_DIR/$release_name"
  echo "  $release_name"
done

for entry in "${README_EXPORTS[@]}"; do
  IFS='|' read -r prefix readme_name <<<"$entry"
  src="$(find_newest_ref "$prefix")"
  if [[ -z "$src" ]]; then
    echo "Missing reference for $prefix" >&2
    MISSING=1
    continue
  fi
  cp "$src" "$README_DIR/$readme_name"
  echo "  docs/screenshots/$readme_name"
done

if [[ "$MISSING" -ne 0 ]]; then
  exit 1
fi

cat <<EOF

Screenshot export complete.

Artifacts:
  $OUT_DIR/          (gitignored; attach on Codeberg with --with-screenshots)
  $README_DIR/       (committed; dark-theme README gallery)

Attach on publish:
  ./scripts/publish_release.sh <version> --with-screenshots
EOF
