#!/usr/bin/env bash
set -euo pipefail

# Captures short on-device screen recordings of Chompass for the marketing site
# usage video. Uses `adb shell screenrecord` (device-side mp4) while driving the
# app with `adb shell input` taps. Clips land raw in android/build/usage-video/raw/
# (gitignored) for scripts/compose_usage_video.sh to turn into the 16:9 site video.
#
# The USB device is reachable from the Windows adb server, not WSL adb — see
# scripts/_adb_resolve.sh. Run with ADB_BIN set if auto-detection misses it.
#
# Usage:
#   scripts/capture_usage_video.sh [package]
#   ADB_BIN=/path/to/adb.exe scripts/capture_usage_video.sh [package]
#   scripts/capture_usage_video.sh --no-record   # drive the app, no clips
#
# Default package is the debug flavor. Tap coordinates default to a Pixel 9a
# (1080 x 2400) with the stock 4-tab bottom bar; override via env vars.
# The app must be installed with common seed data first:
#   ./scripts/install_debug.sh
#   ./scripts/install_debug.sh --reseed

PACKAGE="${1:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
RECORD_ONLY_MISSING=0
NO_RECORD=0
for arg in "$@"; do
  case "$arg" in
    --no-record) NO_RECORD=1 ;;
  esac
done

ADB_BIN="${ADB_BIN:-adb}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW_DIR="${RAW_DIR:-${ROOT}/android/build/usage-video/raw}"
BITRATE="${BITRATE:-12M}"
SEGMENT_SECONDS="${SEGMENT_SECONDS:-12}"
HOME_SETTLE_SECONDS="${HOME_SETTLE_SECONDS:-4}"
TAP_SETTLE_SECONDS="${TAP_SETTLE_SECONDS:-2}"
DENSITY_X="${DENSITY_X:-1080}"
DENSITY_Y="${DENSITY_Y:-2400}"
TAB_Y="${TAB_Y:-2260}"
TAB_HOME="${TAB_HOME:-135}"     # 4-tab bar: centers at 135/405/675/945
TAB_PROGRESS="${TAB_PROGRESS:-405}"
TAB_COACH="${TAB_COACH:-675}"
TAB_SETTINGS="${TAB_SETTINGS:-945}"
ADD_FOOD_Y="${ADD_FOOD_Y:-2050}"   # FAB on Home
FAB_X="${FAB_X:-900}"
FAB_Y="${FAB_Y:-2150}"

# Resolve a Windows adb.exe like the perf script (unset ANDROID_ADB_SERVER_PORT
# for .exe so it talks to the 5037 Windows server).
if [ -z "${ADB_BIN:-}" ] || [ "${ADB_BIN}" = "adb" ]; then
  if [ -n "${ZSH_VERSION:-}" ]; then
    setopt NULL_GLOB 2>/dev/null || true
  fi
  for cand in \
    /mnt/c/Users/*/Downloads/platform-tools*/platform-tools/adb.exe \
    /mnt/c/Users/*/AppData/Local/Android/Sdk/platform-tools/adb.exe \
    /mnt/c/Users/*/AppData/Local/Microsoft/WinGet/Packages/Google.PlatformTools_*/platform-tools/adb.exe \
    /mnt/c/Android/platform-tools/adb.exe \
    /mnt/c/platform-tools/adb.exe; do
    if [ -f "$cand" ]; then ADB_BIN="$cand"; break; fi
  done
fi
case "$ADB_BIN" in
  *.exe) unset ANDROID_ADB_SERVER_PORT ;;
esac

mkdir -p "${RAW_DIR}"
STAMP="$(date +%Y%m%d_%H%M%S)"

echo "Package:   ${PACKAGE}"
echo "Adb:       ${ADB_BIN}"
echo "Raw clips: ${RAW_DIR}"

"${ADB_BIN}" get-state >/dev/null

seed() {
  "${ADB_BIN}" shell am start -W -n "${ACTIVITY}" --ez seed_test_data true \
    --ez seed_body_metrics true --ez seed_active_calories true >/dev/null
  sleep "${HOME_SETTLE_SECONDS}"
}

record_segment() {
  local name="$1"; shift
  local device_mp4="/sdcard/chompass_usage_${name}_${STAMP}.mp4"
  local target="${RAW_DIR}/${name}-${STAMP}.mp4"
  "${ADB_BIN}" shell rm -f "${device_mp4}" 2>/dev/null || true
  # record in the background; input taps run while it captures
  "${ADB_BIN}" shell screenrecord --bit-rate "${BITRATE}" --time-limit "${SEGMENT_SECONDS}" \
    "${device_mp4}" >/dev/null 2>&1 &
  local rec_pid=$!
  sleep 1
  "$@"                    # drive the segment
  wait "${rec_pid}" || true
  "${ADB_BIN}" pull "${device_mp4}" "${target}" >/dev/null
  "${ADB_BIN}" shell rm -f "${device_mp4}" 2>/dev/null || true
  echo "  -> ${target}"
}

tap() { "${ADB_BIN}" shell input tap "$1" "$2"; }
swipe() { "${ADB_BIN}" shell input swipe "$1" "$2" "$3" "$4" "$5"; }

# Driver functions run while screenrecord captures; keep them under
# SEGMENT_SECONDS so the recording doesn't cut the final tap short.
drive_home() {
  tap "${TAB_HOME}" "${TAB_Y}"
  sleep 2
  tap "${TAB_HOME}" "${TAB_Y}"
  sleep "${TAP_SETTLE_SECONDS}"
}

drive_add_food() {
  tap "${FAB_X}" "${FAB_Y}"
  sleep 3
  swipe "${DENSITY_X}" 1600 "${DENSITY_X}" 800 500
  sleep "${TAP_SETTLE_SECONDS}"
}

drive_progress() {
  tap "${TAB_PROGRESS}" "${TAB_Y}"
  sleep 3
  swipe "${DENSITY_X}" 1800 "${DENSITY_X}" 900 600
  sleep "${TAP_SETTLE_SECONDS}"
}

drive_coach() {
  tap "${TAB_COACH}" "${TAB_Y}"
  sleep 3
  swipe "${DENSITY_X}" 1800 "${DENSITY_X}" 1000 600
  sleep "${TAP_SETTLE_SECONDS}"
}

# Open on the Home tab with seeded data so every segment starts in a known place.
seed

if [ "${NO_RECORD}" = "1" ]; then
  echo "Dry-driving the app (no clips)."
  exit 0
fi

echo "Recording segments (${SEGMENT_SECONDS}s each)..."
echo "  [1/4] home — calorie ring + today's log"
record_segment home drive_home

echo "  [2/4] add-food — manual entry sheet"
record_segment add-food drive_add_food

echo "  [3/4] progress — weight and body-fat charts"
record_segment progress drive_progress

echo "  [4/4] coach — chat"
record_segment coach drive_coach

echo "Done. Compose the site video with:"
echo "  ./scripts/compose_usage_video.sh"
