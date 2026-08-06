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
#   scripts/capture_usage_video.sh --only barcode  # retake a single segment
#   scripts/capture_usage_video.sh --calibrate   # dump UI hierarchy for tap coords
#
# The script pins the device animation scales to ANIM_SCALE (default 2.0) so
# every capture has the same smooth transitions; disable with
# --no-set-anim-scale.
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
SET_ANIM_SCALE=1
ONLY=""
for arg in "$@"; do
  case "$arg" in
    --no-record) NO_RECORD=1 ;;
    --calibrate) CALIBRATE=1 ;;
    --no-set-anim-scale) SET_ANIM_SCALE=0 ;;
    --only) shift; ONLY="$1" ;;
  esac
done

ADB_BIN="${ADB_BIN:-adb}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW_DIR="${RAW_DIR:-${ROOT}/android/build/usage-video/raw}"
BITRATE="${BITRATE:-12M}"
HOME_SETTLE_SECONDS="${HOME_SETTLE_SECONDS:-5.5}"
TAP_SETTLE_SECONDS="${TAP_SETTLE_SECONDS:-2}"
ANIM_SCALE="${ANIM_SCALE:-2.0}"
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
LOG_TEXT="${LOG_TEXT:-Log}"
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

# Pin the device animation scales so every capture has the same smooth 2x
# transitions (sheets/scrims slide visibly; taps land after they settle).
anim_window=""; anim_transition=""; anim_animator=""
for kv in "window_animation_scale:anim_window" "transition_animation_scale:anim_transition" "animator_duration_scale:anim_animator"; do
  key="${kv%%:*}"; var="${kv##*:}"
  cur=$("${ADB_BIN}" shell settings get global "${key}" 2>/dev/null)
  if [ "${cur}" != "${ANIM_SCALE}" ]; then
    if [ "${SET_ANIM_SCALE}" = "1" ]; then
      "${ADB_BIN}" shell settings put global "${key}" "${ANIM_SCALE}" >/dev/null
      cur=${ANIM_SCALE}
    else
      echo "  WARN: ${key}=${cur} (expected ${ANIM_SCALE}); pass --set-anim-scale or set ANIM_SCALE" >&2
    fi
  fi
  eval "${var}=\"${cur}\""
done
echo "Anim scale: window=${anim_window} transition=${anim_transition} animator=${anim_animator}"

# Poll the UI hierarchy for a text/pattern; returns the match (or empty).
# Runs a uiautomator dump on the device each attempt (~1-2s).
ui_contains() {
  local pattern="$1"
  "${ADB_BIN}" shell uiautomator dump /sdcard/chompass_ui.xml >/dev/null 2>&1 || true
  "${ADB_BIN}" shell cat /sdcard/chompass_ui.xml 2>/dev/null \
    | grep -oE "${pattern}" | head -1 || true
}

# Cold-start the app with seed extras and wait until the seeded day is STABLE
# on screen. The seeding coroutine rewrites the whole year's blobs to DataStore;
# on this device those writes take tens of seconds and race the UI reads, so the
# home "N left" value keeps changing while the seed runs. Recording before the
# writes finish captures a half-seeded day (or the day gets wiped mid-clip).
# Poll "left" twice: when the same value persists across consecutive polls the
# seed is done. [stable_rounds] consecutive identical polls to require.
seed() {
  local extra_flags="${1:-}" stable_rounds="${2:-2}"
  local prev="" cur="" stable=0 tries=0
  "${ADB_BIN}" shell am force-stop "${PACKAGE}"
  "${ADB_BIN}" shell am start -W -n "${ACTIVITY}" --ez seed_test_data true \
    ${extra_flags} --ez demo_ai true \
    --ez seed_active_calories true --ei active_today_override 700 \
    --ez clear_pending_draft true >/dev/null
  while (( tries < 14 )); do
    sleep 8
    cur=$(ui_contains 'text="[0-9]{1,3} left"' | grep -o '[0-9]*' || true)
    if [ -n "${cur}" ] && [ "${cur}" = "${prev}" ]; then
      stable=$((stable + 1))
      if (( stable >= stable_rounds )); then
        sleep 5   # let any remaining weight/body-fat blobs finish
        return 0
      fi
    else
      stable=0
    fi
    prev="${cur}"
    tries=$((tries + 1))
  done
  echo "  seed: home never stabilised (last left='${cur}')" >&2
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

# This device's screenrecord stops ~1s after the display freezes (no frame
# updates). A 1px nudge every ~2s keeps the compositor alive without touching
# the UI. Call via sleep_nudge instead of plain sleep in the drivers.
NUDGE_X="${NUDGE_X:-100}"
NUDGE_Y="${NUDGE_Y:-1100}"
nudge() { swipe "${NUDGE_X}" "${NUDGE_Y}" "$((NUDGE_X + 1))" "$((NUDGE_Y + 1))" 60; }

# sleep in ~2s chunks, nudging between chunks (integer seconds only).
sleep_nudge() {
  local remaining="$1" chunk=2
  while (( remaining > 0 )); do
    if (( remaining >= chunk )); then
      sleep "${chunk}"
      remaining=$((remaining - chunk))
    else
      sleep "${remaining}"
      remaining=0
    fi
    nudge
  done
}

# Driver functions run while screenrecord captures; keep each under its
# segment's time limit so the recording doesn't cut the final tap short.

# Tap a spot repeatedly until the given text disappears from the UI (the sheet
# closed) or the timeout expires. Keeps the tap-driven recording alive too.
tap_until_gone() {
  local text="$1" fx="$2" fy="$3" timeout="${4:-8}" waited=0
  while (( waited < timeout )); do
    if [ -z "$(ui_contains "text=\"${text}\"")" ]; then return 0; fi
    tap "${fx}" "${fy}"
    sleep 1.5
    waited=$((waited + 2))
  done
  echo "  tap_until_gone '${text}': still visible after ${timeout}s" >&2
}

# Poll until the given text appears (e.g. the scanned product card), nudging
# every ~1.5s to keep the recording alive. Returns 0 when found, 1 on timeout.
wait_for_text() {
  local text="$1" timeout="${2:-8}" waited=0
  while (( waited < timeout )); do
    if [ -n "$(ui_contains "text=\"${text}\"")" ]; then return 0; fi
    sleep 1.5
    waited=$((waited + 2))
    nudge
  done
  return 1
}

# AI fills in the macros, then the ring rises after logging.
drive_ai() {
  tap "${FAB_X}" "${FAB_Y}"
  sleep 3
  tap "${ADD_NOTE_X}" "${ADD_NOTE_Y}"
  sleep 3
  type_text "Chicken%srice%sbowl"
  sleep 2.5                                # input text types char-by-char
  tap "${ANALYZE_X}" "${ANALYZE_Y}"        # keyboard is open -> button sits high
  sleep_nudge 7                            # demo streams phases + fills fields
  tap_until_gone "${LOG_TEXT}" "${LOG_BUTTON_X}" "${LOG_BUTTON_Y}" 6
  sleep_nudge 5                            # save; home ring animates upward
  nudge
}

# Live barcode scan against a monitor-displayed fixture; product card appears
# (no log — the scan + resolved card is the shot; keeps the ring in budget).
drive_barcode() {
  tap "${FAB_X}" "${FAB_Y}"
  sleep 3
  tap "${ADD_BARCODE_X}" "${ADD_BARCODE_Y}"
  sleep_nudge 4                            # camera viewfinder starts
  sleep_nudge 5                            # hold the phone over the fixture
  wait_for_text "${LOG_TEXT}" 7 || true    # OFF lookup -> product card (best effort)
  sleep_nudge 2                            # hold the card
  nudge
}

# Progress tab: all-time view, then scroll to the calorie chart.
drive_trend() {
  tap "${TAB_PROGRESS}" "${TAB_Y}"
  sleep_nudge 4
  tap "${TREND_RANGE_ALL_X}" "${TREND_RANGE_ALL_Y}"
  sleep_nudge 2
  swipe "${DENSITY_X}" 1800 "${DENSITY_X}" 900 600
  sleep_nudge 2
  swipe "${DENSITY_X}" 1800 "${DENSITY_X}" 1000 600
  sleep_nudge 3
  nudge
}

# Recents: relog two saved meals so the diary grows during the shot.
drive_diary() {
  tap "${FAB_X}" "${FAB_Y}"
  sleep 3
  tap "${ADD_RECENTS_X}" "${ADD_RECENTS_Y}"
  sleep 3
  tap "${RECENT_LOG_X}" "${RECENT_LOG_1_Y}"
  sleep 2.5
  tap "${FAB_X}" "${FAB_Y}"
  sleep 2.5
  tap "${ADD_RECENTS_X}" "${ADD_RECENTS_Y}"
  sleep 2.5
  tap "${RECENT_LOG_X}" "${RECENT_LOG_2_Y}"
  sleep_nudge 2
  sleep 0.5
  nudge
}

if [ "${CALIBRATE}" = "1" ]; then
  echo "Dumping current UI hierarchy for calibration..."
  seed "" 2
  "${ADB_BIN}" shell uiautomator dump /sdcard/ui.xml >/dev/null
  "${ADB_BIN}" shell cat /sdcard/ui.xml
  exit 0
fi

# Open on the Home tab with seeded data so every segment starts in a known place.
seed "" 2

if [ "${NO_RECORD}" = "1" ]; then
  echo "Dry-driving the app (no clips)..."
  seed "--ez seed_body_metrics_2y true" 2
  drive_trend
  seed "" 2
  drive_ai
  seed "" 2
  drive_barcode
  seed "" 2
  drive_diary
  echo "Dry run finished."
  exit 0
fi

# Trend goes first: its seed also writes the 2-year body metrics (the heaviest
# blobs). Recording it last used to start before those writes finished.
echo "Recording segments..."
if [ -n "${ONLY}" ]; then
  case "${ONLY}" in
    trend)  seed "--ez seed_body_metrics_2y true" 2; record_segment trend 18 drive_trend ;;
    ai)     seed "" 2; record_segment ai 30 drive_ai ;;
    barcode) seed "" 2; record_segment barcode 28 drive_barcode ;;
    diary)  seed "" 2; record_segment diary 22 drive_diary ;;
    *) echo "Unknown segment '${ONLY}' (trend|ai|barcode|diary)" >&2; exit 1 ;;
  esac
  echo "Done. Compose the site video with:"
  echo "  ./scripts/compose_usage_video.sh"
  exit 0
fi
echo "  [1/4] trend   — weight and body-fat charts (18s)"
seed "--ez seed_body_metrics_2y true" 2
record_segment trend 18 drive_trend

echo "  [2/4] ai      — demo AI fills macros + ring rises (30s)"
seed "" 2
record_segment ai 30 drive_ai

echo "  [3/4] barcode — camera scan against monitor fixture (28s)"
seed "" 2
record_segment barcode 28 drive_barcode

echo "  [4/4] diary   — relogging saved meals (22s)"
seed "" 2
record_segment diary 22 drive_diary

echo "Done. Compose the site video with:"
echo "  ./scripts/compose_usage_video.sh"
