#!/usr/bin/env bash
# Build (optional), install the debug APK via Windows-aware adb, and launch with
# the common seed extras.
#
# Default: assembleDebug → install arm64 (or universal) → force-stop → start with
# seed_test_data + seed_body_metrics + seed_keto_settings.
#
# Usage:
#   ./scripts/install_debug.sh              # build + install + seeded launch
#   ./scripts/install_debug.sh --no-build   # skip Gradle; install existing APK
#   ./scripts/install_debug.sh --no-seed    # build + install + plain launch
#   ./scripts/install_debug.sh --no-launch  # build + install only
#   ./scripts/install_debug.sh --reseed     # skip build/install; force-stop + seed
#   ./scripts/install_debug.sh --2y         # also pass seed_body_metrics_2y
#   ./scripts/install_debug.sh --universal  # prefer universal APK over arm64
#   ./scripts/install_debug.sh --reinstall  # uninstall first (signature-mismatch installs)
#
# Env:
#   ADB_BIN   adb binary (auto-detects Windows adb.exe from WSL if unset)
#   PACKAGE   default: app.chompass.debug
#   APK       override APK path (otherwise picks arm64 / universal under outputs/)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

PACKAGE="${PACKAGE:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
DEBUG_APK_DIR="${ROOT}/android/app/build/outputs/apk/debug"
# Install only for the primary Android user (id 0), never the full multi-user
# space (work profile, guest, etc.). Other profiles stay untouched.
ADB_INSTALL_USER="${ADB_INSTALL_USER:-0}"

DO_BUILD=1
DO_INSTALL=1
DO_LAUNCH=1
DO_SEED=1
SEED_2Y=0
PREFER_UNIVERSAL=0
DO_REINSTALL=0
APK_OVERRIDE="${APK:-}"

while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) DO_BUILD=0 ;;
    --no-seed) DO_SEED=0 ;;
    --no-launch) DO_LAUNCH=0 ;;
    --reseed) DO_BUILD=0; DO_INSTALL=0; DO_LAUNCH=1; DO_SEED=1 ;;
    --reinstall) DO_REINSTALL=1 ;;
    --2y) SEED_2Y=1 ;;
    --universal) PREFER_UNIVERSAL=1 ;;
    -h|--help)
      sed -n '2,21p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1 (try --help)" >&2
      exit 2
      ;;
  esac
  shift
done

resolve_apk() {
  if [ -n "${APK_OVERRIDE}" ]; then
    printf '%s\n' "${APK_OVERRIDE}"
    return
  fi
  # ABI splits: there is no app-debug.apk — only per-ABI + universal.
  local candidates=()
  if [ "${PREFER_UNIVERSAL}" -eq 1 ]; then
    candidates+=(
      "${DEBUG_APK_DIR}/app-universal-debug.apk"
      "${DEBUG_APK_DIR}/app-arm64-v8a-debug.apk"
    )
  else
    candidates+=(
      "${DEBUG_APK_DIR}/app-arm64-v8a-debug.apk"
      "${DEBUG_APK_DIR}/app-universal-debug.apk"
    )
  fi
  local cand
  for cand in "${candidates[@]}"; do
    if [ -f "${cand}" ]; then
      printf '%s\n' "${cand}"
      return
    fi
  done
  return 1
}

echo "Using adb: ${ADB_BIN}"
"${ADB_BIN}" devices

if [ "${DO_BUILD}" -eq 1 ]; then
  echo "Building debug APK..."
  (
    cd "${ROOT}/android"
    ./gradlew :app:assembleDebug
  )
fi

if [ "${DO_INSTALL}" -eq 1 ]; then
  if ! APK="$(resolve_apk)"; then
    echo "APK not found under ${DEBUG_APK_DIR}/" >&2
    echo "Expected app-arm64-v8a-debug.apk or app-universal-debug.apk (ABI splits)." >&2
    echo "Build first: build-debug   (or omit --no-build)" >&2
    exit 1
  fi
  echo "Installing: ${APK}"
  if [ "${DO_REINSTALL}" -eq 1 ]; then
    # Signature mismatch (different debug keystore) blocks -r; drop the old
    # package first. Launch seeds below recreate the sample data.
    echo "Uninstalling ${PACKAGE} (old signature)..."
    "${ADB_BIN}" uninstall "${PACKAGE}" || true
  fi
  "${ADB_BIN}" install -r --user "${ADB_INSTALL_USER}" "${APK}"
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
