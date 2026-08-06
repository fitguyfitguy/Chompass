#!/usr/bin/env bash
set -euo pipefail

# Captures short on-device screen recordings of Chompass for the marketing site
# usage video. Uses `adb shell screenrecord` (device-side mp4) while driving the
# app with `adb shell input` taps. Clips land raw in android/build/usage-video/raw/
# (gitignored) for scripts/compose_usage_video.sh to turn into the 16:9 site video.
#
# Story segments (shot -> driver):
#   ai      FAB -> Note -> typed text -> demo AI fills macros -> Log -> ring rises
#   barcode FAB -> Barcode -> live camera scan (fixture on a monitor) -> Log
#   trend   Progress tab -> 2-year weight/body-fat charts -> scroll
#   diary   FAB -> Recents -> relog two saved meals -> log grows -> ring rises
#
# The `demo_ai` extra makes food analysis replay a scripted response (see
# services/ai/DemoFoodAnalysis.kt) so the AI shot is deterministic. The barcode
# segment needs the phone pointed at android/build/usage-video/barcode.png
# (scripts/generate_barcode_fixture.py) full-screen on a monitor.
#
# The USB device is reachable from the Windows adb server, not WSL adb — see
# scripts/_adb_resolve.sh. Run with ADB_BIN set if auto-detection misses it.
#
# Usage:
#   scripts/capture_usage_video.sh [package]
#   ADB_BIN=/path/to/adb.exe scripts/capture_usage_video.sh [package]
#   scripts/capture_usage_video.sh --no-record   # drive the app, no clips
#   scripts/capture_usage_video.sh --calibrate   # dump UI hierarchy for tap coords
#
# Default package is the debug flavor. Tap coordinates default to a Pixel 9a
# (1080 x 2400) with the stock 4-tab bottom bar; override via env vars.
# The app must be installed with common seed data first:
#   ./scripts/install_debug.sh
#   ./scripts/install_debug.sh --reseed

# Package is only a positional when it does not start with "--".
if [ "$#" -gt 0 ] && [[ "$1" != --* ]]; then
  PACKAGE="$1"
else
  PACKAGE="${PACKAGE:-app.chompass.debug}"
fi
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
NO_RECORD=0
CALIBRATE=0
for arg in "$@"; do
  case "$arg" in
    --no-record) NO_RECORD=1 ;;
    --calibrate) CALIBRATE=1 ;;
  esac
done

ADB_BIN="${ADB_BIN:-adb}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW_DIR="${RAW_DIR:-${ROOT}/android/build/usage-video/raw}"
BITRATE="${BITRATE:-12M}"
HOME_SETTLE_SECONDS="${HOME_SETTLE_SECONDS:-5.5}"
TAP_SETTLE_SECONDS="${TAP_SETTLE_SECONDS:-2}"
DENSITY_X="${DENSITY_X:-1080}"
DENSITY_Y="${DENSITY_Y:-2400}"
TAB_Y="${TAB_Y:-2298}"
TAB_HOME="${TAB_HOME:-127}"     # 4-tab bar: centers at 127/402/678/953
TAB_PROGRESS="${TAB_PROGRESS:-402}"
FAB_X="${FAB_X:-943}"
FAB_Y="${FAB_Y:-1993}"

# Add Food sheet tiles (calibrate with --calibrate if layout differs).
ADD_NOTE_X="${ADD_NOTE_X:-569}"       # hero row: Photo / Note / Recents
ADD_NOTE_Y="${ADD_NOTE_Y:-1420}"
ADD_RECENTS_X="${ADD_RECENTS_X:-883}"
ADD_RECENTS_Y="${ADD_RECENTS_Y:-1420}"
ADD_BARCODE_X="${ADD_BARCODE_X:-415}" # row: Voice / Barcode / Manual / Copy
ADD_BARCODE_Y="${ADD_BARCODE_Y:-2005}"
ANALYZE_X="${ANALYZE_X:-540}"         # TextInputSheet "Analyze" (keyboard open)
ANALYZE_Y="${ANALYZE_Y:-1407}"
LOG_BUTTON_X="${LOG_BUTTON_X:-540}"   # FoodResultSheet "Log" (fallback)
LOG_BUTTON_Y="${LOG_BUTTON_Y:-2266}"
RECENT_LOG_X="${RECENT_LOG_X:-960}"   # Saved meals: per-row Log buttons
RECENT_LOG_1_Y="${RECENT_LOG_1_Y:-1354}"
RECENT_LOG_2_Y="${RECENT_LOG_2_Y:-1648}"
TREND_RANGE_ALL_X="${TREND_RANGE_ALL_X:-961}"  # Progress: "Alle" (all-time)
TREND_RANGE_ALL_Y="${TREND_RANGE_ALL_Y:-257}"

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
  # Cold start with a clean state every segment: force-stop + drop the pending
  # analysis draft first, so onNewIntent leftovers / restored result sheets
  # can't pollute the shot.
  "${ADB_BIN}" shell am force-stop "${PACKAGE}"
  # Test data + 2y body metrics + deterministic demo AI for the entry segments.
  "${ADB_BIN}" shell am start -W -n "${ACTIVITY}" --ez seed_test_data true \
    --ez seed_body_metrics_2y true --ez demo_ai true \
    --ez seed_active_calories true --ei active_today_override 700 \
    --ez clear_pending_draft true >/dev/null
  sleep "${HOME_SETTLE_SECONDS}"
}

record_segment() {
  local name="$1" seconds="$2"; shift 2
  local device_mp4="/sdcard/chompass_usage_${name}_${STAMP}.mp4"
  local target="${RAW_DIR}/${name}-${STAMP}.mp4"
  "${ADB_BIN}" shell rm -f "${device_mp4}" 2>/dev/null || true
  # record in the background; input taps run while it captures
  "${ADB_BIN}" shell screenrecord --bit-rate "${BITRATE}" --time-limit "${seconds}" \
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
type_text() { "${ADB_BIN}" shell input text "$1"; }


# Driver functions run while screenrecord captures; keep each under its
# segment's time limit so the recording doesn't cut the final tap short.

# AI fills in the macros, then the ring rises after logging.
drive_ai() {
  tap "${FAB_X}" "${FAB_Y}"
  sleep 3
  tap "${ADD_NOTE_X}" "${ADD_NOTE_Y}"
  sleep 3
  type_text "Chicken%srice%sbowl"
  sleep 1.5
  tap "${ANALYZE_X}" "${ANALYZE_Y}"     # keyboard is open -> button sits high
  sleep 6                                # demo streams phases + fills fields
  tap "${LOG_BUTTON_X}" "${LOG_BUTTON_Y}"
  sleep 4                                # save; home ring animates upward
  sleep 2                                # hold the fresh state
}

# Live barcode scan against a monitor-displayed fixture; product card appears
# (no log — the scan + resolved card is the shot; keeps the ring in budget).
drive_barcode() {
  tap "${FAB_X}" "${FAB_Y}"
  sleep 3
  tap "${ADD_BARCODE_X}" "${ADD_BARCODE_Y}"
  sleep 4                                # camera viewfinder starts
  sleep 5                                # hold the phone over the fixture
  sleep 3                                # OFF lookup + product card resolves
  sleep 2                                # hold the card
}

# Progress tab: all-time view, then scroll to the calorie chart.
drive_trend() {
  tap "${TAB_PROGRESS}" "${TAB_Y}"
  sleep 4
  tap "${TREND_RANGE_ALL_X}" "${TREND_RANGE_ALL_Y}"
  sleep 2.5
  swipe "${DENSITY_X}" 1800 "${DENSITY_X}" 900 600
  sleep 2
  swipe "${DENSITY_X}" 1800 "${DENSITY_X}" 1000 600
  sleep 3
}

# Recents: relog two saved meals so the diary grows during the shot.
drive_diary() {
  tap "${FAB_X}" "${FAB_Y}"
  sleep 2.5
  tap "${ADD_RECENTS_X}" "${ADD_RECENTS_Y}"
  sleep 2.5
  tap "${RECENT_LOG_X}" "${RECENT_LOG_1_Y}"
  sleep 2.5
  tap "${FAB_X}" "${FAB_Y}"
  sleep 2
  tap "${ADD_RECENTS_X}" "${ADD_RECENTS_Y}"
  sleep 2
  tap "${RECENT_LOG_X}" "${RECENT_LOG_2_Y}"
  sleep 2.5
}

if [ "${CALIBRATE}" = "1" ]; then
  echo "Dumping current UI hierarchy for calibration..."
  seed
  "${ADB_BIN}" shell uiautomator dump /sdcard/ui.xml >/dev/null
  "${ADB_BIN}" shell cat /sdcard/ui.xml
  exit 0
fi

# Open on the Home tab with seeded data so every segment starts in a known place.
seed

if [ "${NO_RECORD}" = "1" ]; then
  echo "Dry-driving the app (no clips)..."
  drive_ai
  seed
  drive_barcode
  seed
  drive_trend
  seed
  drive_diary
  echo "Dry run finished."
  exit 0
fi

echo "Recording segments..."
echo "  [1/4] ai      — demo AI fills macros + ring rises (20s)"
record_segment ai 20 drive_ai

echo "  [2/4] barcode — camera scan against monitor fixture (18s)"
record_segment barcode 18 drive_barcode

echo "  [3/4] trend   — weight and body-fat charts (14s)"
record_segment trend 14 drive_trend

echo "  [4/4] diary   — relogging saved meals (14s)"
record_segment diary 14 drive_diary

echo "Done. Compose the site video with:"
echo "  ./scripts/compose_usage_video.sh"
