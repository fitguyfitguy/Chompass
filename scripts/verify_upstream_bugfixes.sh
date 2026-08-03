#!/usr/bin/env bash
# Manual + semi-automated verification for upstream bug-fix pass (2026-07-20).
#
# Most fixes are UI/UX and need eyes on the device. This script:
#   1. Preflight: device, package, optional APK install
#   2. Smoke: launch MainActivity, watch logcat briefly for crashes
#   3. Prints a checkbox checklist with exact adb commands (PowerShell + WSL)
#
# USB devices: run from Windows PowerShell OR WSL with Windows adb:
#   ADB_BIN=/mnt/c/Users/<you>/AppData/Local/Android/Sdk/platform-tools/adb.exe \
#     ./scripts/verify_upstream_bugfixes.sh
#
# Usage:
#   ./scripts/verify_upstream_bugfixes.sh [--install] [--skip-smoke]
# Env:
#   ADB_BIN   adb binary (see scripts/_adb_resolve.sh)
#   APK       debug APK path (default: android/app/build/outputs/apk/debug/app-debug.apk)
#   PACKAGE   default app.chompass.debug

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

PACKAGE="${PACKAGE:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
APK="${APK:-${ROOT}/android/app/build/outputs/apk/debug/app-debug.apk}"
DO_INSTALL=0
DO_SMOKE=1

while [ $# -gt 0 ]; do
  case "$1" in
    --install) DO_INSTALL=1 ;;
    --skip-smoke) DO_SMOKE=0 ;;
    -h|--help)
      sed -n '2,20p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $1 (try --help)" >&2
      exit 2
      ;;
  esac
  shift
done

section() { printf '\n=== %s ===\n' "$1"; }
check() { printf '  [ ] %s\n' "$1"; }

section "Preflight"
echo "ADB:     ${ADB_BIN}"
echo "Package: ${PACKAGE}"
echo "APK:     ${APK}"

if ! "${ADB_BIN}" get-state >/dev/null 2>&1; then
  echo "ERROR: No device. Use Windows PowerShell adb if USB-connected:" >&2
  echo '  adb devices' >&2
  exit 1
fi
echo "Device:  $("${ADB_BIN}" get-state)"

if [ "${DO_INSTALL}" -eq 1 ]; then
  if [ ! -f "${APK}" ]; then
    echo "ERROR: APK missing. Build first:" >&2
    echo "  devenv shell bash -lc 'cd android && ./gradlew :app:assembleDebug'" >&2
    exit 1
  fi
  echo "Installing ${APK} ..."
  "${ADB_BIN}" install -r "${APK}"
fi

if ! "${ADB_BIN}" shell pm path "${PACKAGE}" >/dev/null 2>&1; then
  echo "ERROR: ${PACKAGE} not installed. Re-run with --install or install from PowerShell:" >&2
  echo '  adb install -r \\wsl$\<distro>\home\<user>\chompass\android\app\build\outputs\apk\debug\app-debug.apk' >&2
  exit 1
fi

VERSION="$("${ADB_BIN}" shell dumpsys package "${PACKAGE}" | grep -m1 versionName | tr -d ' ' || true)"
echo "Installed: ${VERSION:-unknown}"

section "Smoke launch"
LAUNCH_CMD="${ADB_BIN} shell am start -n ${ACTIVITY}"
echo "Launch: ${LAUNCH_CMD}"
"${ADB_BIN}" shell am force-stop "${PACKAGE}" >/dev/null 2>&1 || true
"${ADB_BIN}" shell am start -n "${ACTIVITY}" >/dev/null

if [ "${DO_SMOKE}" -eq 1 ]; then
  echo "Watching logcat 8s for FATAL/Exception in ${PACKAGE} ..."
  if "${ADB_BIN}" logcat -d -t 200 | grep -E "FATAL|AndroidRuntime.*${PACKAGE}" | tail -5 | grep -q .; then
    echo "WARN: Recent crash lines in logcat — clear and retry after manual tests:"
    echo "  ${ADB_BIN} logcat -c"
  else
    echo "OK: No recent fatal for ${PACKAGE} in last ~200 lines."
  fi
  sleep 2
fi

section "Manual verification checklist"
cat <<'EOF'
Run through on the device. Mark pass/fail. Fixes from upstream bug-fix pass 2026-07-20.

#143 Water imperial (lbs)
  [ ] Settings → Personal → Weight unit → lbs
  [ ] Enable water tracking; Home shows water as "X / Y fl oz" (not ml)
  [ ] Add food → water quick row shows fl oz; Custom water sheet suffix is fl oz
  [ ] Add Water home-screen widget → center/goal/remaining use fl oz

#133 Settings cut pace (lbs)
  [ ] Settings → Goals → Weekly change (lose/gain goal) → options show ~0.6 / 1.1 / 2.2 lbs
      (not 0.25 / 0.5 / 1.0 labeled as lbs)

#105 API key trim
  [ ] Settings → AI → API key → paste key with trailing newline (copy from Notes/email)
  [ ] Save; log food via text — no "unexpected char 0x0a" / auth header error

#147 AI read timeout
  [ ] Settings → AI → "AI read timeout" visible (default 60 s); set to 120 s for slow Ollama/custom vision
  [ ] Photo food log with self-hosted vision — completes instead of timing out at ~60 s

#145 Max response tokens clamp
  [ ] Settings → AI → Max response tokens → try 99999 → saves clamped (256–8192); app stays stable

#134 Camera preview FOV
  [ ] Add food → Camera → preview framing matches captured photo (no zoom mismatch)

#61 Swipe delete undo
  [ ] Home → swipe food row far left → delete → snackbar "Food removed" + Undo
  [ ] Tap Undo → entry reappears

#50 Swipe thresholds
  [ ] Vertical scroll food list — fewer accidental day changes / deletes
  [ ] Day change still works: deliberate horizontal swipe on calorie hero (120dp+)

#12 Save durability
  [ ] Log food → on review sheet tap Log → sheet stays until save finishes
  [ ] Optional kill-test: during "Logging..." force-stop app; reopen — draft/restored or entry saved

#148/#94 Widgets loading
  [ ] Add all four widgets (Calorie, Protein, Water, All Metrics) — render within ~3 s
  [ ] Remove/re-add widget after force-stop app — not stuck on Glance loading spinner
EOF

section "Useful adb commands (WSL / bash)"
cat <<EOF
Force-stop + cold start:
  ${ADB_BIN} shell am force-stop ${PACKAGE}
  ${ADB_BIN} shell am start -n ${ACTIVITY}

Clear logcat before a test:
  ${ADB_BIN} logcat -c
  ${ADB_BIN} logcat -s AndroidRuntime Chompass FudAI

Seed progress tab data (optional):
  ${ADB_BIN} shell am start -n ${ACTIVITY} --ez seed_test_data true

List app widgets (after you add them on launcher):
  ${ADB_BIN} shell dumpsys appwidget | grep -A2 ${PACKAGE} || true

Kill app during save (timing manual — run Log then quickly):
  ${ADB_BIN} shell am force-stop ${PACKAGE}
EOF

section "PowerShell equivalents (Windows host adb)"
cat <<'EOF'
Install debug APK from WSL path:
  adb install -r \\wsl$\<distro>\home\<user>\chompass\android\app\build\outputs\apk\debug\app-debug.apk

Launch:
  adb shell am start -n app.chompass.debug/app.chompass.MainActivity

Logcat:
  adb logcat -c
  adb logcat -s AndroidRuntime
EOF

section "Done"
echo "Script finished. Complete the checklist above on the device."
echo "Artifact tip: screenshot failures and capture logcat if anything regresses."
