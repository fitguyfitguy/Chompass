#!/usr/bin/env bash
set -euo pipefail

# All-in-one entry-perf benchmark: seed the app's settings + tracking data, then
# fire N real analyze+save requests and capture/summarize their FudAIPerf timings.
# Debug build only (the benchmark + Gemini-key seeding are gated on BuildConfig.DEBUG).
#
# Usage:
#   scripts/perf_entry_benchmark.sh [count]        # default count = 3
# Env:
#   ADB_BIN    adb binary (auto-detects a Windows adb.exe from WSL if unset)
#   PACKAGE    app package (default: org.codeberg.fitguy.nofud.debug)
#   SEED       "1" (default) seeds test data + body metrics + keto settings first;
#              "0" skips seeding and only benchmarks
#   TIMEOUT    seconds to wait for the batch before giving up (default: 240)
#
# Prereq: the debug APK is installed and secrets.properties carries a Gemini key
# (so the app can actually issue requests). Build+install per CLAUDE.md.

COUNT="${1:-3}"
PACKAGE="${PACKAGE:-org.codeberg.fitguy.nofud.debug}"
ACTIVITY="${PACKAGE}/org.codeberg.fitguy.nofud.MainActivity"
SEED="${SEED:-1}"
TIMEOUT="${TIMEOUT:-240}"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="android/build/perf-entry/${STAMP}"
LOG="${OUT_DIR}/entry_perf.log"

# shellcheck source=scripts/_adb_resolve.sh
. "$(dirname "$0")/_adb_resolve.sh"

mkdir -p "${OUT_DIR}"
echo "Using package: ${PACKAGE}"
echo "Using adb:     ${ADB_BIN}"
echo "Entries:       ${COUNT}   Seed data: ${SEED}"
echo "Writing to:    ${LOG}"

"${ADB_BIN}" get-state >/dev/null

# One cold start carries every extra: onCreate reads the seeders and the
# benchmark flag. force-stop first so onCreate (not onNewIntent) fires.
echo "Force-stopping and launching with seed + benchmark extras..."
"${ADB_BIN}" shell am force-stop "${PACKAGE}"

START_ARGS=(am start -n "${ACTIVITY}")
if [ "${SEED}" = "1" ]; then
  START_ARGS+=(--ez seed_test_data true --ez seed_body_metrics true --ez seed_keto_settings true)
fi
START_ARGS+=(--ez run_entry_benchmark true --ei benchmark_count "${COUNT}")

# Clear logcat right before the run so the captured buffer is just this batch.
"${ADB_BIN}" logcat -c
"${ADB_BIN}" shell "${START_ARGS[@]}" >/dev/null

echo
echo ">>> Benchmark running on device (${COUNT} live requests). Capturing FudAIPerf..."
echo

# Stream to the log AND to a watcher that exits on the closing marker; that
# SIGPIPEs logcat so we stop as soon as the batch finishes. `timeout` is a
# backstop in case the app never emits phase=done.
timeout "${TIMEOUT}" "${ADB_BIN}" logcat -v epoch -s "FudAIPerf:V" \
  | tee "${LOG}" \
  | awk '/op=benchmark phase=done/ { exit } { }' >/dev/null || true

echo
echo "Saved raw log: ${LOG}"

if ! grep -q "op=benchmark phase=done" "${LOG}" 2>/dev/null; then
  echo "WARNING: no 'phase=done' marker — batch may have timed out (${TIMEOUT}s) or the"
  echo "         build isn't DEBUG / has no benchmark support. Partial log kept above."
fi

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
  echo "(no FudAIPerf lines captured — is this a DEBUG build with a Gemini key set?)"
fi
