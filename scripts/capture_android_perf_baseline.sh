#!/usr/bin/env bash
set -euo pipefail

# Full-seed perf suite: cold start, Progress (go/ + range chips), tabs,
# Settings Food/Goals, Add Food hub, Home fling/day/swipe/sip, relog bench,
# analyze+save pipeline.
# Seed first with ./scripts/install_debug.sh; do not pass seed extras here.
# Usage:
#   scripts/capture_android_perf_baseline.sh [package]
#   ADB_BIN=/path/to/adb scripts/capture_android_perf_baseline.sh [package]
#   RUN_ENTRY_BENCH=0 RUN_RELOG_BENCH=0 RUN_WATER_SIP_BENCH=0
# Default package is the debug flavor. See docs/PERFORMANCE.md.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

PACKAGE="${1:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
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
RUN_ENTRY_BENCH="${RUN_ENTRY_BENCH:-1}"
ENTRY_COUNT="${ENTRY_COUNT:-3}"
RUN_RELOG_BENCH="${RUN_RELOG_BENCH:-1}"
RELOG_COUNT="${RELOG_COUNT:-3}"
RUN_WATER_SIP_BENCH="${RUN_WATER_SIP_BENCH:-1}"
WATER_SIP_COUNT="${WATER_SIP_COUNT:-1}"
RUN_FLIP_BENCH="${RUN_FLIP_BENCH:-1}"
LOCAL_ENTRY_COUNT="${LOCAL_ENTRY_COUNT:-3}"
ENTRY_TIMEOUT="${ENTRY_TIMEOUT:-240}"
# Pixel 9a 1080×2424-ish: Home fling / hero day-swipe / first food-row swipe.
HOME_FLING_FROM_Y="${HOME_FLING_FROM_Y:-1900}"
HOME_FLING_TO_Y="${HOME_FLING_TO_Y:-700}"
DAY_SWIPE_Y="${DAY_SWIPE_Y:-520}"
DAY_CHIP_Y="${DAY_CHIP_Y:-180}"
FOOD_ROW_Y="${FOOD_ROW_Y:-1400}"
WATER_SIP_X="${WATER_SIP_X:-880}"
WATER_SIP_Y="${WATER_SIP_Y:-2080}"
PROGRESS_FLING_FROM_Y="${PROGRESS_FLING_FROM_Y:-1900}"
PROGRESS_FLING_TO_Y="${PROGRESS_FLING_TO_Y:-700}"

mkdir -p "${OUT_DIR}"

go_nav() {
  local dest="$1"
  "${ADB_BIN}" shell am start -W -n "${ACTIVITY}" -d "chompass://go/${dest}" \
    > "${OUT_DIR}/start_go_${dest//\//_}.txt"
}

wait_fud_mark() {
  local needle="$1"
  local dest="$2"
  local timeout="${3:-60}"
  local deadline=$((SECONDS + timeout))
  while [ "${SECONDS}" -lt "${deadline}" ]; do
    if grep -q "${needle}" "${dest}" 2>/dev/null; then return 0; fi
    sleep 1
  done
  echo "WARNING: timed out waiting for ${needle} in ${dest}" >&2
  return 1
}

capture_input() {
  local name="$1"
  shift
  "${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" reset >/dev/null 2>&1 || true
  "${ADB_BIN}" logcat -c
  "${ADB_BIN}" shell input "$@"
  sleep "${TAB_SETTLE_SECONDS}"
  "${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats_${name}.txt"
  local pid
  pid="$("${ADB_BIN}" shell pidof -s "${PACKAGE}" 2>/dev/null | tr -d '\r')"
  if [ -n "${pid}" ]; then
    "${ADB_BIN}" logcat -d -s FudAIPerf --pid="${pid}" > "${OUT_DIR}/logcat_${name}.txt" || true
  fi
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

echo "Collecting Progress fling after All-range..."
capture_input progress_fling swipe 540 "${PROGRESS_FLING_FROM_Y}" 540 "${PROGRESS_FLING_TO_Y}" 250

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

echo "Collecting Home fling / day switch / food-row swipe..."
go_nav home
sleep "${TAB_SETTLE_SECONDS}"
capture_input home_fling swipe 540 "${HOME_FLING_FROM_Y}" 540 "${HOME_FLING_TO_Y}" 250
# Hero swipe-right → yesterday (120dp threshold; more reliable than a day chip).
capture_input day_switch swipe 200 "${DAY_SWIPE_Y}" 850 "${DAY_SWIPE_Y}" 120
# Also tap yesterday's week-strip chip when today is not Monday.
DOW="$("${ADB_BIN}" shell date +%u 2>/dev/null | tr -d '\r')"
if [ -n "${DOW}" ] && [ "${DOW}" -ge 2 ] && [ "${DOW}" -le 7 ]; then
  # 16dp side pad ≈ 42px on 2.625 density; 7 equal tiles on 1080-wide.
  today_idx=$((DOW - 1))
  yday_idx=$((today_idx - 1))
  yday_x=$((42 + yday_idx * 142 + 71))
  capture_input day_chip tap "${yday_x}" "${DAY_CHIP_Y}"
fi
# Short left-swipe on the first food row: reveal then cancel (snaps back).
capture_input row_swipe swipe 900 "${FOOD_ROW_Y}" 550 "${FOOD_ROW_Y}" 180
# Back to today so later hub/relog land on the seeded day.
go_nav home
sleep "${TAB_SETTLE_SECONDS}"

echo "Collecting Add Food hub first frame (no chip/photo taps — those hit Photo)..."
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" reset >/dev/null 2>&1 || true
"${ADB_BIN}" logcat -c
"${ADB_BIN}" shell input tap "${ADD_FOOD_FAB_X}" "${ADD_FOOD_FAB_Y}"
sleep "${TAB_SETTLE_SECONDS}"
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats_add_food.txt"
HUB_PID="$("${ADB_BIN}" shell pidof -s "${PACKAGE}" 2>/dev/null | tr -d '\r')"
if [ -n "${HUB_PID}" ]; then
  "${ADB_BIN}" logcat -d -s FudAIPerf --pid="${HUB_PID}" > "${OUT_DIR}/logcat_add_food.txt" || true
fi
# Dismiss the sheet. Coordinate taps on this sheet land on Photo and leave
# the in-app camera up while a save freezes the UI.
"${ADB_BIN}" shell input keyevent KEYCODE_BACK
sleep 0.4
"${ADB_BIN}" shell input keyevent KEYCODE_BACK
go_nav home
sleep "${TAB_SETTLE_SECONDS}"

if [ "${RUN_FLIP_BENCH}" = 1 ]; then
  echo "Running flip bench via Home (hub chips, relog uiAck, local entry, sip, day switch)..."
  "${ADB_BIN}" shell input keyevent KEYCODE_BACK
  go_nav home
  sleep "${TAB_SETTLE_SECONDS}"
  "${ADB_BIN}" logcat -c
  "${ADB_BIN}" logcat -v epoch -s "FudAIPerf:V" > "${OUT_DIR}/logcat_flip_bench.txt" &
  FLIP_PID=$!
  "${ADB_BIN}" shell am start -n "${ACTIVITY}" \
    --ez run_flip_benchmark true \
    --ei relog_benchmark_count "${RELOG_COUNT}" \
    --ei local_entry_benchmark_count "${LOCAL_ENTRY_COUNT}" \
    --ei water_sip_benchmark_count "${WATER_SIP_COUNT}" >/dev/null
  wait_fud_mark "op=flipBench phase=done" "${OUT_DIR}/logcat_flip_bench.txt" 120 || true
  sleep 1
  kill "${FLIP_PID}" 2>/dev/null || true
elif [ "${RUN_RELOG_BENCH}" = 1 ] || [ "${RUN_WATER_SIP_BENCH}" = 1 ]; then
  echo "Running leftover repo benches (set RUN_FLIP_BENCH=1 for the Home path)..."
  if [ "${RUN_WATER_SIP_BENCH}" = 1 ]; then
    "${ADB_BIN}" logcat -c
    "${ADB_BIN}" logcat -v epoch -s "FudAIPerf:V" > "${OUT_DIR}/logcat_water_sip_bench.txt" &
    WATER_PID=$!
    "${ADB_BIN}" shell am start -n "${ACTIVITY}" --ez run_water_sip_benchmark true --ei water_sip_benchmark_count "${WATER_SIP_COUNT}" >/dev/null
    wait_fud_mark "op=waterSip phase=done" "${OUT_DIR}/logcat_water_sip_bench.txt" 30 || true
    sleep 1
    kill "${WATER_PID}" 2>/dev/null || true
  fi
  if [ "${RUN_RELOG_BENCH}" = 1 ]; then
    "${ADB_BIN}" logcat -c
    "${ADB_BIN}" logcat -v epoch -s "FudAIPerf:V" > "${OUT_DIR}/logcat_relog_bench.txt" &
    RELLOG_PID=$!
    "${ADB_BIN}" shell am start -n "${ACTIVITY}" --ez run_relog_benchmark true --ei relog_benchmark_count "${RELOG_COUNT}" >/dev/null
    wait_fud_mark "op=relogBench phase=done" "${OUT_DIR}/logcat_relog_bench.txt" 60 || true
    sleep 1
    kill "${RELLOG_PID}" 2>/dev/null || true
  fi
fi

if [ "${RUN_ENTRY_BENCH}" = 1 ]; then
  echo "Running entry pipeline benchmark (${ENTRY_COUNT} analyze+save, no reseed)..."
  ROOT="$(cd "$(dirname "$0")/.." && pwd)"
  SEED=0 QUIET=1 OUT_DIR="${OUT_DIR}" PACKAGE="${PACKAGE}" ADB_BIN="${ADB_BIN}" TIMEOUT="${ENTRY_TIMEOUT}" \
    "${ROOT}/scripts/perf_entry_benchmark.sh" "${ENTRY_COUNT}" || true
fi

echo
echo "=== FudAIPerf summary (${OUT_DIR}) ==="
grep -hE 'op=(coldStart|hubOpen|relog|relogBench|entryLocal|waterSip|daySwitch|progress|save|flipBench)' \
  "${OUT_DIR}"/logcat*.txt 2>/dev/null | sed 's/.*FudAIPerf: //' | sort -u || true
echo
echo "Done. Review files under ${OUT_DIR}"
