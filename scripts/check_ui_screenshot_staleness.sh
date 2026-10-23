#!/usr/bin/env bash
# Release gate: published screenshots (docs/screenshots/) must be regenerated
# when the Compose UI changes. This is a cheap git-history check, not a pixel
# diff — the device capture lives in scripts/capture_device_screenshots.sh and
# the pixel regression harness in scripts/export_release_screenshots.sh.
#
# Stale when:
#   - UI sources have uncommitted changes and docs/screenshots/ has none
#     (a fresh capture leaves docs/screenshots/ dirty until it is committed), or
#   - the newest UI commit is newer than the newest docs/screenshots/ commit.
#
# Usage:
#   ./scripts/check_ui_screenshot_staleness.sh            # explain, exit 0/1
#   ./scripts/check_ui_screenshot_staleness.sh --quiet     # exit code only
#
# Env:
#   SKIP_SCREENSHOT_CHECK=1   bypass (emergency release; record why in the PR/commit)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

QUIET=0
for arg in "$@"; do
  case "$arg" in
    --quiet) QUIET=1 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

say() { [ "${QUIET}" -eq 1 ] || printf '%s\n' "$*"; }

if [ "${SKIP_SCREENSHOT_CHECK:-0}" = "1" ]; then
  say "Screenshot check skipped (SKIP_SCREENSHOT_CHECK=1)."
  exit 0
fi

UI_PATHS=(
  "android/app/src/main/java/app/chompass/ui"
  "android/app/src/main/java/app/chompass/MainActivity.kt"
  "android/app/src/main/res"
)
SHOTS_PATH="docs/screenshots"

DIRTY_UI="$(git status --porcelain -- "${UI_PATHS[@]}" || true)"
DIRTY_SHOTS="$(git status --porcelain -- "${SHOTS_PATH}" || true)"
LAST_UI="$(git log -1 --format=%ct -- "${UI_PATHS[@]}" 2>/dev/null || true)"
LAST_SHOTS="$(git log -1 --format=%ct -- "${SHOTS_PATH}" 2>/dev/null || true)"
LAST_UI="${LAST_UI:-0}"
LAST_SHOTS="${LAST_SHOTS:-0}"

REASON=""
if [ -n "${DIRTY_UI}" ] && [ -z "${DIRTY_SHOTS}" ]; then
  REASON="uncommitted UI changes with no screenshots captured"
elif [ -z "${DIRTY_UI}" ] && [ "${LAST_UI}" -gt "${LAST_SHOTS}" ]; then
  REASON="UI commit is newer than the last screenshot commit"
fi

if [ -z "${REASON}" ]; then
  say "Screenshots are fresh (UI ${LAST_UI:-?} <= screenshots ${LAST_SHOTS:-?})."
  exit 0
fi

if [ "${LAST_UI}" -gt 0 ]; then
  say "UI last changed:      $(git log -1 --format='%h %cs %s' -- "${UI_PATHS[@]}")"
fi
if [ "${LAST_SHOTS}" -gt 0 ]; then
  say "Screenshots last made: $(git log -1 --format='%h %cs %s' -- "${SHOTS_PATH}")"
fi
if [ -n "${DIRTY_UI}" ]; then
  say "Uncommitted UI files:"
  printf '%s\n' "${DIRTY_UI}" | sed 's/^/  /'
fi

cat >&2 <<EOF

STALE screenshots: ${REASON}.
Capture the real app on a device and commit docs/screenshots/:

  ./scripts/capture_device_screenshots.sh        # or: devenv tasks run release:screenshots

Emergency bypass: SKIP_SCREENSHOT_CHECK=1.
EOF
exit 1
