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
PROGRESS_TAB_X="${PROGRESS_TAB_X:-540}"
PROGRESS_TAB_Y="${PROGRESS_TAB_Y:-2210}"
PROGRESS_DEEP_LINK="${PROGRESS_DEEP_LINK:-}"
PROGRESS_LOG_TAIL_LINES="${PROGRESS_LOG_TAIL_LINES:-2000}"

mkdir -p "${OUT_DIR}"

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

echo "Collecting memory snapshots..."
"${ADB_BIN}" shell dumpsys meminfo "${PACKAGE}" > "${OUT_DIR}/meminfo.txt"
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

echo "Collecting Progress-screen perf captures..."
"${ADB_BIN}" shell am force-stop "${PACKAGE}"
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" reset >/dev/null 2>&1 || true
"${ADB_BIN}" logcat -c
"${ADB_BIN}" shell am start -W -n "${ACTIVITY}" > "${OUT_DIR}/start_progress_nav.txt"
sleep "${HOME_SETTLE_SECONDS}"
if [ -n "${PROGRESS_DEEP_LINK}" ]; then
  "${ADB_BIN}" shell am start -W -a android.intent.action.VIEW -d "${PROGRESS_DEEP_LINK}" "${PACKAGE}" \
    > "${OUT_DIR}/start_progress_deeplink.txt"
else
  "${ADB_BIN}" shell input tap "${PROGRESS_TAB_X}" "${PROGRESS_TAB_Y}"
fi
sleep "${PROGRESS_SETTLE_SECONDS}"
"${ADB_BIN}" shell dumpsys gfxinfo "${PACKAGE}" framestats > "${OUT_DIR}/gfx_framestats_progress.txt"
"${ADB_BIN}" shell dumpsys meminfo "${PACKAGE}" > "${OUT_DIR}/meminfo_progress.txt"
PROGRESS_PID="$("${ADB_BIN}" shell pidof -s "${PACKAGE}" 2>/dev/null | tr -d '\r')"
if [ -n "${PROGRESS_PID}" ]; then
  "${ADB_BIN}" logcat -d -t "${PROGRESS_LOG_TAIL_LINES}" --pid="${PROGRESS_PID}" > "${OUT_DIR}/logcat_progress.txt" || true
fi

echo "Done. Review files under ${OUT_DIR}"
