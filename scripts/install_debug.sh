#!/usr/bin/env bash
# Install the debug APK (Windows-aware adb) and launch with the common seed extras.
#
# Default matches the usual WSL device loop: install → force-stop → start with
# seed_test_data + seed_body_metrics + seed_keto_settings.
#
# Usage:
#   ./scripts/install_debug.sh              # install + seeded launch
#   ./scripts/install_debug.sh --no-seed    # install + plain launch
#   ./scripts/install_debug.sh --no-launch  # install only
#   ./scripts/install_debug.sh --reseed     # skip install; force-stop + seeded launch
#   ./scripts/install_debug.sh --2y         # also pass seed_body_metrics_2y
#
# Env:
#   ADB_BIN   adb binary (auto-detects Windows adb.exe from WSL if unset)
#   PACKAGE   default: app.chompass.debug
#   APK       default: android/app/build/outputs/apk/debug/app-debug.apk

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

PACKAGE="${PACKAGE:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
APK="${APK:-${ROOT}/android/app/build/outputs/apk/debug/app-debug.apk}"

DO_INSTALL=1
DO_LAUNCH=1
DO_SEED=1
SEED_2Y=0

while [ $# -gt 0 ]; do
  case "$1" in
    --no-seed) DO_SEED=0 ;;
    --no-launch) DO_LAUNCH=0 ;;
    --reseed) DO_INSTALL=0; DO_LAUNCH=1; DO_SEED=1 ;;
    --2y) SEED_2Y=1 ;;
    -h|--help)
      sed -n '2,18p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1 (try --help)" >&2
      exit 2
      ;;
  esac
  shift
done

echo "Using adb: ${ADB_BIN}"
"${ADB_BIN}" devices

if [ "${DO_INSTALL}" -eq 1 ]; then
  if [ ! -f "${APK}" ]; then
    echo "APK not found: ${APK}" >&2
    echo "Build first: build-debug   (or: devenv shell bash -lc 'cd android && ./gradlew :app:assembleDebug')" >&2
    exit 1
  fi
  echo "Installing: ${APK}"
  "${ADB_BIN}" install -r "${APK}"
fi

if [ "${DO_LAUNCH}" -eq 0 ]; then
  exit 0
fi

echo "Force-stopping ${PACKAGE}"
"${ADB_BIN}" shell am force-stop "${PACKAGE}"

START_ARGS=(am start -n "${ACTIVITY}")
if [ "${DO_SEED}" -eq 1 ]; then
  START_ARGS+=(--ez seed_test_data true --ez seed_body_metrics true --ez seed_keto_settings true)
  if [ "${SEED_2Y}" -eq 1 ]; then
    START_ARGS+=(--ez seed_body_metrics_2y true)
  fi
  echo "Launching with seed extras"
else
  echo "Launching (no seed)"
fi

"${ADB_BIN}" shell "${START_ARGS[@]}"
