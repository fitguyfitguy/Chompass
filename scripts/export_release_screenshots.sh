#!/usr/bin/env bash
# Release screenshot pipeline.
#
# Two lanes, different jobs:
#   1. DEVICE (published): scripts/capture_device_screenshots.sh drives the real
#      debug app on a phone and writes release-screenshots/ + docs/screenshots/
#      at native panel resolution. scripts/build_feature_shots.py then composes
#      docs/screenshots/features/ (hero collage + framed panels) for the README.
#      These are the images the README, chompass.app and optional Codeberg
#      attachments use.
#   2. JVM PREVIEWS (regression): `updateDebugScreenshotTest` re-renders the
#      @PreviewTest reference PNGs. Deterministic, no device — the pixel gate.
#
# Usage:
#   ./scripts/export_release_screenshots.sh               # device capture + JVM refs
#   ./scripts/export_release_screenshots.sh --previews-only  # JVM refs only (no device)
#   ./scripts/export_release_screenshots.sh --validate    # no writes: JVM validate + freshness
#   ./scripts/export_release_screenshots.sh --check       # freshness gate only
#   ./scripts/export_release_screenshots.sh --skip-previews  # device capture only
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VALIDATE_ONLY=0
CHECK_ONLY=0
SKIP_DEVICE=0
SKIP_PREVIEWS=0

usage() { sed -n '2,20p' "$0"; exit "${1:-0}"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --validate) VALIDATE_ONLY=1 ;;
    --check) CHECK_ONLY=1 ;;
    --previews-only) SKIP_DEVICE=1 ;;
    --skip-previews) SKIP_PREVIEWS=1 ;;
    -h|--help) usage 0 ;;
    *) echo "Unknown option: $1" >&2; usage 2 ;;
  esac
  shift
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

if [[ "${CHECK_ONLY}" -eq 1 ]]; then
  exec "${ROOT}/scripts/check_ui_screenshot_staleness.sh"
fi

if [[ "${VALIDATE_ONLY}" -eq 1 ]]; then
  echo "==> Validating JVM preview references"
  run_in_devenv 'cd android && ./gradlew :app:validateDebugScreenshotTest'
  "${ROOT}/scripts/check_ui_screenshot_staleness.sh"
  exit 0
fi

# Published images: real app on a real device.
if [[ "${SKIP_DEVICE}" -eq 0 ]]; then
  "${ROOT}/scripts/check_ui_screenshot_staleness.sh" || true
  echo ""
  echo "==> Capturing device screenshots"
  "${ROOT}/scripts/capture_device_screenshots.sh"
else
  echo "==> Skipping device capture; published PNGs are untouched"
fi

# Regression references.
if [[ "${SKIP_PREVIEWS}" -eq 0 ]]; then
  echo ""
  echo "==> Updating JVM screenshot preview references"
  run_in_devenv 'cd android && ./gradlew :app:updateDebugScreenshotTest'
fi

# README feature shots (hero collage + framed panels), composed from the full
# captures. Device lane only: a previews-only run must not touch published PNGs.
if [[ "${SKIP_DEVICE}" -eq 0 ]]; then
  echo ""
  echo "==> Composing README feature shots"
  uv run --with pillow python "${ROOT}/scripts/build_feature_shots.py"
fi

# After a capture docs/screenshots/ is dirty until committed — that counts as fresh.
# --previews-only leaves published PNGs untouched, so a stale UI still fails here.
"${ROOT}/scripts/check_ui_screenshot_staleness.sh"

cat <<'EOF'

Done.
  docs/screenshots/           committed README + site gallery (device captures)
  docs/screenshots/features/  composed README feature shots (hero + panels)
  release-screenshots/        optional Codeberg attachments (gitignored)
  android/app/src/screenshotTestDebug/reference/   JVM pixel-regression refs

Commit docs/screenshots/ (and the preview refs) with the release.
EOF
