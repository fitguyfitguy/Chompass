#!/usr/bin/env bash
# Run Maestro device-pass flows against the phone, natively from WSL.
#
# The device must be visible to the WSL adb server (connect once with
# scripts/adb_wifi.sh; USB devices on the Windows adb are invisible here).
#
# Usage:
#   ./scripts/device_pass_maestro.sh                # build+install+seed, all flows
#   ./scripts/device_pass_maestro.sh --no-build     # reuse the built debug APK
#   ./scripts/device_pass_maestro.sh --fresh        # pm clear first (item-7 state)
#   ./scripts/device_pass_maestro.sh untracked      # only flows matching "untracked"
#
# Artifacts: android/build/maestro/<ts>/ (junit report.xml, maestro logs).
# Exit code mirrors Maestro (non-zero = any flow failed).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FLOWS_DIR="${ROOT}/android/maestro/flows"
OUT_BASE="${ROOT}/android/build/maestro"
PACKAGE="${PACKAGE:-app.chompass.debug}"

FRESH=0
BUILD=1
FILTERS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --fresh) FRESH=1 ;;
    --no-build) BUILD=0 ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) FILTERS+=("$1") ;;
  esac
  shift
done

# Device must be on the WSL adb server — never fall back to Windows adb.exe
# (Maestro talks to this same server).
export ADB_BIN="${ADB_BIN:-adb}"
if [ "${ADB_BIN}" = adb ] && ! command -v adb >/dev/null 2>&1; then
  echo "No adb on PATH (enter devenv shell)." >&2
  exit 1
fi

DEVICE="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
if [ -z "${DEVICE}" ]; then
  echo "No device on the WSL adb server. Connect once:" >&2
  echo "  ./scripts/adb_wifi.sh --tcpip      # cable plugged in (until reboot)" >&2
  echo "  ./scripts/adb_wifi.sh pair .../connect ...   # wireless debugging" >&2
  exit 1
fi
echo "Device: ${DEVICE}"

MAESTRO_BIN="$("${ROOT}/scripts/ensure_maestro.sh")"
echo "Maestro: ${MAESTRO_BIN}"

# Staging: fresh state (optional) + install + seed_full launch.
if [ "${FRESH}" -eq 1 ]; then
  echo "pm clear ${PACKAGE} (fresh state)"
  adb shell pm clear "${PACKAGE}" >/dev/null
fi
"${ROOT}/scripts/install_debug.sh" $([ "${BUILD}" -eq 1 ] || echo --no-build)

FLOWS=()
if [ ${#FILTERS[@]} -gt 0 ]; then
  for f in "${FILTERS[@]}"; do
    match="$(find "${FLOWS_DIR}" -maxdepth 1 -name "*${f}*.yaml" | head -1)"
    [ -n "${match}" ] || { echo "No flow matches '${f}' in ${FLOWS_DIR}" >&2; exit 2; }
    FLOWS+=("${match}")
  done
else
  # shellcheck disable=SC2012
  while IFS= read -r f; do FLOWS+=("${f}"); done < <(ls "${FLOWS_DIR}"/*.yaml | sort)
fi
[ ${#FLOWS[@]} -gt 0 ] || { echo "No flows found in ${FLOWS_DIR}" >&2; exit 2; }
echo "Flows: ${FLOWS[*]#"$ROOT"/}"

OUT="${OUT_BASE}/$(date +%Y%m%d_%H%M%S)"
mkdir -p "${OUT}"

set +e
( cd "${OUT}" && "${MAESTRO_BIN}" test \
    --format junit --output "${OUT}/report.xml" \
    "${FLOWS[@]}" )
STATUS=$?
set -e

echo ""
if [ "${STATUS}" -eq 0 ]; then
  echo "PASS — report: ${OUT}/report.xml"
else
  echo "FAIL (exit ${STATUS}) — report: ${OUT}/report.xml (failure screenshots in ${OUT})"
fi
exit "${STATUS}"
