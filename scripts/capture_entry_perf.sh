#!/usr/bin/env bash
set -euo pipefail

# Captures entry-addition timing logs (tag FudAIPerf) from a running debug build.
# The device is only reachable from Windows adb, so from WSL point ADB_BIN at a
# Windows adb.exe:
#   ADB_BIN=/mnt/c/path/to/adb.exe scripts/capture_entry_perf.sh
# or run against whatever `adb` is on PATH:
#   scripts/capture_entry_perf.sh
#
# Usage:
#   scripts/capture_entry_perf.sh [package]
# Env:
#   ADB_BIN    adb binary (default: adb)
#   DURATION   auto-stop after N seconds (default: run until Ctrl-C)
#   LAUNCH     "1" to force-stop + launch MainActivity first (default: 0)
#
# While it records, exercise the add-entry flows on the device (text, photo +
# Save, manual add, barcode). Artifacts land in android/build/perf-entry/<stamp>/.

PACKAGE="${1:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"

# shellcheck source=scripts/_adb_resolve.sh
. "$(dirname "$0")/_adb_resolve.sh"
DURATION="${DURATION:-}"
LAUNCH="${LAUNCH:-0}"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="android/build/perf-entry/${STAMP}"
LOG="${OUT_DIR}/entry_perf.log"

mkdir -p "${OUT_DIR}"

echo "Using package: ${PACKAGE}"
echo "Using adb:     ${ADB_BIN}"
echo "Writing to:    ${LOG}"

"${ADB_BIN}" get-state >/dev/null

echo "Clearing logcat buffer..."
"${ADB_BIN}" logcat -c

if [ "${LAUNCH}" = "1" ]; then
  echo "Launching ${ACTIVITY}..."
  "${ADB_BIN}" shell am force-stop "${PACKAGE}"
  "${ADB_BIN}" shell am start -n "${ACTIVITY}" >/dev/null
fi

echo
echo ">>> Now add entries on the device (text / photo+Save / manual / barcode)."
if [ -n "${DURATION}" ]; then
  echo ">>> Recording for ${DURATION}s..."
else
  echo ">>> Recording... press Ctrl-C when done."
fi
echo

# -v epoch gives a parseable absolute timestamp per line; filter to our tag only.
if [ -n "${DURATION}" ]; then
  timeout "${DURATION}" "${ADB_BIN}" logcat -v epoch -s "FudAIPerf:V" | tee "${LOG}" || true
else
  # Ctrl-C ends the tee; keep going to the summary afterwards.
  trap 'echo' INT
  "${ADB_BIN}" logcat -v epoch -s "FudAIPerf:V" | tee "${LOG}" || true
  trap - INT
fi

echo
echo "Saved raw log: ${LOG}"

SUMMARIZER="$(dirname "$0")/summarize_entry_perf.py"
if [ -s "${LOG}" ] && [ -f "${SUMMARIZER}" ]; then
  echo "=== Summary ==="
  if command -v python3 >/dev/null 2>&1; then
    python3 "${SUMMARIZER}" "${LOG}" || true
  elif command -v uv >/dev/null 2>&1; then
    uv run python "${SUMMARIZER}" "${LOG}" || true
  else
    echo "(no python3/uv found; run: python3 ${SUMMARIZER} ${LOG})"
  fi
else
  echo "(no FudAIPerf lines captured — is this a DEBUG build, and did an entry-add run?)"
fi
