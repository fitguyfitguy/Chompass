#!/usr/bin/env bash
set -euo pipefail

# Captures on-device LLM daily benchmark logcat for Chompass debug builds.
# Usage:
#   scripts/capture_ondevice_llm_daily.sh [matrix_run]
#   ADB_BIN=/path/to/adb.exe scripts/capture_ondevice_llm_daily.sh 4
#
# matrix_run: 1–4 (see docs/ON_DEVICE_LLM.md). Default: 4 (daily-driver preset).

PACKAGE="${PACKAGE:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
ADB_BIN="${ADB_BIN:-adb}"
RUN="${1:-4}"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="android/build/ondevice-llm/${STAMP}"
LOG_FILE="${OUT_DIR}/ondevice_llm.log"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-900}"

mkdir -p "${OUT_DIR}"

echo "Using package: ${PACKAGE}"
echo "Using adb: ${ADB_BIN}"
echo "Matrix run: ${RUN}"
echo "Writing artifacts to: ${OUT_DIR}"

"${ADB_BIN}" get-state >/dev/null

EXTRA_ARGS=()
case "${RUN}" in
  1)
    EXTRA_ARGS+=(--ez ondevice_llm_clear_cache true --es ondevice_llm_tier daily --es ondevice_llm_prompt fewshot_units)
    ;;
  2)
    EXTRA_ARGS+=(--ez ondevice_llm_clear_cache false --ez ondevice_llm_mtp false --es ondevice_llm_tier daily --es ondevice_llm_prompt fewshot_units)
    ;;
  3)
    EXTRA_ARGS+=(--ez ondevice_llm_clear_cache true --ez ondevice_llm_mtp true --es ondevice_llm_tier daily --es ondevice_llm_prompt fewshot_units)
    ;;
  4)
    EXTRA_ARGS+=(--ez ondevice_llm_clear_cache false --es ondevice_llm_preset daily)
    ;;
  *)
    echo "Unknown matrix run: ${RUN} (expected 1–4)" >&2
    exit 1
    ;;
esac

echo "Clearing logcat buffer..."
"${ADB_BIN}" logcat -c

echo "Force-stopping app and launching on-device LLM test..."
"${ADB_BIN}" shell am force-stop "${PACKAGE}"
"${ADB_BIN}" shell am start -n "${ACTIVITY}" \
  --ez run_ondevice_llm_test true \
  "${EXTRA_ARGS[@]}"

echo "Capturing FudOnDeviceLlm logcat (timeout ${TIMEOUT_SECONDS}s)..."
if timeout "${TIMEOUT_SECONDS}" "${ADB_BIN}" logcat -s FudOnDeviceLlm > "${LOG_FILE}"; then
  :
else
  echo "Logcat capture stopped (timeout or adb disconnect)." >&2
fi

if rg -q "phase=done|phase=daily_summary|phase=fatal" "${LOG_FILE}" 2>/dev/null; then
  echo "Run finished — see ${LOG_FILE}"
else
  echo "Warning: no done/daily_summary/fatal line yet — run may still be in progress." >&2
fi

echo "Done. Artifacts in ${OUT_DIR}"
