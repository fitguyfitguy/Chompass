#!/usr/bin/env bash
set -euo pipefail

# Captures startup, frame stats, and memory snapshots for Chompass.
# Usage:
#   scripts/capture_android_perf_baseline.sh [package]
#   ADB_BIN=/path/to/adb scripts/capture_android_perf_baseline.sh [package]
# Default package is the debug flavor.

PACKAGE="${1:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
ADB_BIN="${ADB_BIN:-adb}"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="android/build/perf-baseline/${STAMP}"
HOME_SETTLE_SECONDS="${HOME_SETTLE_SECONDS:-4}"
PROGRESS_SETTLE_SECONDS="${PROGRESS_SETTLE_SECONDS:-4}"
TAB_SETTLE_SECONDS="${TAB_SETTLE_SECONDS:-2}"
TAB_Y="${TAB_Y:-2256}"
HOME_TAB_X="${HOME_TAB_X:-135}"
PROGRESS_TAB_X="${PROGRESS_TAB_X:-405}"
COACH_TAB_X="${COACH_TAB_X:-675}"
SETTINGS_TAB_X="${SETTINGS_TAB_X:-945}"
PROGRESS_TAB_Y="${PROGRESS_TAB_Y:-${TAB_Y}}"
# Pixel 9a 1080-wide: 6 Progress range chips (1W 1M 3M 6M 1Y All).
RANGE_CHIP_Y="${RANGE_CHIP_Y:-257}"
RANGE_CHIP_XS="${RANGE_CHIP_XS:-116 285 454 623 792 961}"
RANGE_CHIP_IDS="${RANGE_CHIP_IDS:-1W 1M 3M 6M 1Y All}"
# Home "+" FAB, above the docked tab bar.
ADD_FOOD_FAB_X="${ADD_FOOD_FAB_X:-960}"
ADD_FOOD_FAB_Y="${ADD_FOOD_FAB_Y:-2060}"
# First quick-relog chip in the Add Food sheet (approx, 1080-wide).
RELOG_CHIP_X="${RELOG_CHIP_X:-180}"
RELOG_CHIP_Y="${RELOG_CHIP_Y:-980}"
PROGRESS_DEEP_LINK="${PROGRESS_DEEP_LINK:-chompass://go/progress}"
PROGRESS_LOG_TAIL_LINES="${PROGRESS_LOG_TAIL_LINES:-2000}"

mkdir -p "${OUT_DIR}"

go_nav() {
  local dest="$1"
  "${ADB_BIN}" shell am start -W -n "${ACTIVITY}" -d "chompass://go/${dest}" \
    > "${OUT_DIR}/start_go_${dest//\//_}.txt"
}

echo "Using package: ${PACKAGE}"
echo "Using adb: ${ADB_BIN}"
echo "Writing artifacts to: ${OUT_DIR}"

"${ADB_BIN}" get-state >/dev/null

echo "Clearing logcat buffer..."
"${ADB_BIN}" logcat -c

echo "Cold-start run (force-stop + start)..."
"${ADB_BIN}" shell am force-stop "${PACKAGE}"
"${ADB_BIN}" shell am start -W -n "${ACTIVITY}" > "${OUT_DIR}/start_warmup.txt"

echo "Collecting startup timings (5 runs)..."
for i in 1 2 3 4 5; do
  "${ADB_BIN}" shell am force-stop "${PACKAGE}"
  "${ADB_BIN}" shell am start -W -n "${ACTIVITY}" > "${OUT_DIR}/start_run_${i}.txt"
done

echo "Collecting gfx frame stats..."
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats.txt"

echo "Collecting memory snapshots (Home settle)..."
sleep "${HOME_SETTLE_SECONDS}"
"${ADB_BIN}" shell dumpsys meminfo "${PACKAGE}" > "${OUT_DIR}/meminfo.txt"
"${ADB_BIN}" shell dumpsys meminfo "${PACKAGE}" > "${OUT_DIR}/meminfo_home.txt"
if "${ADB_BIN}" shell top -n 1 -o PID,CPU%,RES,ARGS > "${OUT_DIR}/top_snapshot.txt" 2>/dev/null; then
  :
else
  # Older/device-specific toybox top builds do not support `-o`.
  if "${ADB_BIN}" shell top -n 1 > "${OUT_DIR}/top_snapshot.txt" 2>/dev/null; then
    :
  else
    # Last-resort CPU snapshot that works broadly across Android builds.
    "${ADB_BIN}" shell dumpsys cpuinfo > "${OUT_DIR}/top_snapshot.txt"
  fi
fi

echo "Recent logcat startup window..."
"${ADB_BIN}" logcat -d > "${OUT_DIR}/logcat.txt"
APP_PID="$("${ADB_BIN}" shell pidof -s "${PACKAGE}" 2>/dev/null | tr -d '\r')"
if [ -n "${APP_PID}" ]; then
  "${ADB_BIN}" logcat -d --pid="${APP_PID}" > "${OUT_DIR}/logcat_app.txt" || true
fi

echo "Collecting Progress-screen perf captures (chompass://go/progress)..."
"${ADB_BIN}" shell am force-stop "${PACKAGE}"
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" reset >/dev/null 2>&1 || true
"${ADB_BIN}" logcat -c
"${ADB_BIN}" shell am start -W -n "${ACTIVITY}" > "${OUT_DIR}/start_progress_nav.txt"
sleep "${HOME_SETTLE_SECONDS}"
"${ADB_BIN}" shell am start -W -n "${ACTIVITY}" -d "${PROGRESS_DEEP_LINK}" \
  > "${OUT_DIR}/start_progress_deeplink.txt"
sleep "${PROGRESS_SETTLE_SECONDS}"
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats_progress.txt"
"${ADB_BIN}" shell dumpsys meminfo "${PACKAGE}" > "${OUT_DIR}/meminfo_progress.txt"
PROGRESS_PID="$("${ADB_BIN}" shell pidof -s "${PACKAGE}" 2>/dev/null | tr -d '\r')"
if [ -n "${PROGRESS_PID}" ]; then
  "${ADB_BIN}" logcat -d -t "${PROGRESS_LOG_TAIL_LINES}" --pid="${PROGRESS_PID}" > "${OUT_DIR}/logcat_progress.txt" || true
fi

echo "Collecting Progress range-chip hops (1W → All)..."
# shellcheck disable=SC2206
RANGE_XS=(${RANGE_CHIP_XS})
RANGE_IDS=(${RANGE_CHIP_IDS})
for i in "${!RANGE_XS[@]}"; do
  id="${RANGE_IDS[$i]}"
  x="${RANGE_XS[$i]}"
  "${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" reset >/dev/null 2>&1 || true
  "${ADB_BIN}" logcat -c
  "${ADB_BIN}" shell input tap "${x}" "${RANGE_CHIP_Y}"
  sleep "${TAB_SETTLE_SECONDS}"
  "${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats_range_${id}.txt"
  if [ -n "${PROGRESS_PID}" ]; then
    "${ADB_BIN}" logcat -d -s FudAIPerf --pid="${PROGRESS_PID}" > "${OUT_DIR}/logcat_range_${id}.txt" || true
  fi
done
"${ADB_BIN}" shell dumpsys meminfo "${PACKAGE}" > "${OUT_DIR}/meminfo_progress_all.txt"

echo "Collecting tab-switch framestats via chompass://go (Home → Progress → Coach → Settings → Home)..."
"${ADB_BIN}" shell am force-stop "${PACKAGE}"
"${ADB_BIN}" shell am start -W -n "${ACTIVITY}" > "${OUT_DIR}/start_tab_loop.txt"
sleep "${HOME_SETTLE_SECONDS}"
for hop in progress coach settings home; do
  "${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" reset >/dev/null 2>&1 || true
  go_nav "${hop}"
  sleep "${TAB_SETTLE_SECONDS}"
  "${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats_tab_${hop}.txt"
done

echo "Collecting Settings sub-screen hops via chompass://go..."
for hop in settings/food settings/goals; do
  slug="${hop##*/}"
  "${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" reset >/dev/null 2>&1 || true
  go_nav "${hop}"
  sleep "${TAB_SETTLE_SECONDS}"
  "${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats_settings_${slug}.txt"
done

echo "Collecting Add Food hub + quick-relog open..."
go_nav home
sleep "${TAB_SETTLE_SECONDS}"
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" reset >/dev/null 2>&1 || true
"${ADB_BIN}" logcat -c
"${ADB_BIN}" shell input tap "${ADD_FOOD_FAB_X}" "${ADD_FOOD_FAB_Y}"
sleep "${TAB_SETTLE_SECONDS}"
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats_add_food.txt"
HUB_PID="$("${ADB_BIN}" shell pidof -s "${PACKAGE}" 2>/dev/null | tr -d '\r')"
if [ -n "${HUB_PID}" ]; then
  "${ADB_BIN}" logcat -d -s FudAIPerf --pid="${HUB_PID}" > "${OUT_DIR}/logcat_add_food.txt" || true
fi
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" reset >/dev/null 2>&1 || true
"${ADB_BIN}" shell input tap "${RELOG_CHIP_X}" "${RELOG_CHIP_Y}"
sleep 1
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats_relog_chip.txt"

echo "Done. Review files under ${OUT_DIR}"
