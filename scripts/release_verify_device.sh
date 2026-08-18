#!/usr/bin/env bash
# Device verification for the next release (3.16.0): security hardening
# (docs/SECURITY_HARDENING_PLAN.md P1-1 / P2-1 / P2-2 / P2-3), German locale
# smoke, home calorie-hero base notch, and notification deep-link regression.
#
# Runs against a physical device via Windows-aware adb (see _adb_resolve.sh).
# Default target is the debug package; pass --package app.chompass to verify
# the release APK (P1-1 then asserts the debug extras are INERT).
#
# Usage:
#   ./scripts/release_verify_device.sh                          # debug package
#   ./scripts/release_verify_device.sh --package app.chompass   # release APK
#   ./scripts/release_verify_device.sh --install-release        # build+install release APK first
#   ./scripts/release_verify_device.sh --only p1,p2-1,p2-2      # selected sections
#
# Env:
#   ADB_BIN   adb binary (auto-detects Windows adb.exe from WSL if unset)
#   PACKAGE   default: app.chompass.debug
#
# Artifacts: android/build/release-verify/<timestamp>/*.png + summary.txt
# Screenshots are for eyeball verification (or the vision skill); the script
# asserts process-alive + no FATAL EXCEPTION automatically.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

PACKAGE="${PACKAGE:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${ROOT}/android/build/release-verify/${STAMP}"
mkdir -p "${OUT_DIR}"

DO_INSTALL_RELEASE=0
STAY_AWAKE=0
ONLY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --package) PACKAGE="${2:?--package needs a value}"; shift ;;
    --install-release) DO_INSTALL_RELEASE=1 ;;
    --stay-awake) STAY_AWAKE=1 ;;
    --only) ONLY="${2:?--only needs a value}"; shift ;;
    -h|--help)
      sed -n '2,20p' "$0"
      exit 0
      ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
  shift
done
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"

IS_RELEASE=0
[[ "${PACKAGE}" == "app.chompass" ]] && IS_RELEASE=1

PASS=0
FAIL=0
SKIP=0

want() { # want <section-id> — true unless --only was given and omits it
  [[ -z "${ONLY}" ]] && return 0
  [[ ",${ONLY}," == *",$1,"* ]]
}

ok()   { PASS=$((PASS+1)); echo "  PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  FAIL: $1"; }
skip() { SKIP=$((SKIP+1)); echo "  SKIP: $1"; }

app_alive() {
  local pid
  pid="$("${ADB_BIN}" shell pidof "${PACKAGE}" 2>/dev/null | tr -d '\r')"
  [[ -n "${pid}" ]]
}

# The launcher aliases live in the app namespace (app.chompass.Launcher*), not
# the applicationId (app.chompass.debug), and only the active theme's alias is
# enabled at runtime. Resolve it from dumpsys so SEND intents target a real,
# enabled component.
enabled_launcher_alias() {
  "${ADB_BIN}" shell dumpsys package "${PACKAGE}" 2>/dev/null | tr -d '\r' \
    | sed -n '/enabledComponents:/,/^    [A-Z]/p' \
    | grep -oE "app\\.chompass\\.Launcher[A-Za-z]+" | head -1
}

logcat_has_fatal() {
  # Capture first: grep -q in a pipefail pipeline SIGPIPEs the upstream adb
  # (grep exits on first match), which pipefail turns into a false non-zero.
  local log
  log="$("${ADB_BIN}" logcat -d 2>/dev/null || true)"
  [[ "${log}" == *"FATAL EXCEPTION"* ]]
}

snap() { # snap <name> — screencap to OUT_DIR
  "${ADB_BIN}" exec-out screencap -p > "${OUT_DIR}/$1.png" 2>/dev/null || true
}

ui_text() { # dump visible text via uiautomator (best-effort)
  "${ADB_BIN}" shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 || true
  "${ADB_BIN}" shell cat /sdcard/window_dump.xml 2>/dev/null | tr -d '\r' \
    | grep -oE 'text="[^"]+"' | sed 's/text="//;s/"$//' || true
}

# True when the device keyguard/lock screen is up (uiautomator then only sees
# systemui, so UI assertions are meaningless).
screen_locked() {
  local text
  text="$(ui_text)"
  grep -qE "Gib die PIN ein|Enter PIN|PIN eingeben|Muster|Pattern|Passwort|Password|Entsperren|Unlock" <<< "${text}" \
    || grep -q "com.android.systemui" <<< "$("${ADB_BIN}" shell cat /sdcard/window_dump.xml 2>/dev/null | tr -d '\r' | grep -oE 'package="[^"]+"' | head -1)"
}

ensure_main_ui() {
  # P1-1 on debug fires reset_onboarding (works on debug), which leaves the app
  # in the onboarding flow and breaks later sections that need the main nav host.
  # seed_test_data completes onboarding; it is inert on release, so this is a
  # no-op there (a fresh release install must be onboarded manually first).
  local text
  text="$(ui_text)"
  if grep -qE "Mahlzeiten erfassen|Track meals|ausgewogen bleiben|stay balanced" <<< "${text}"; then
    "${ADB_BIN}" shell am start -n "${ACTIVITY}" --ez seed_test_data true >/dev/null 2>&1 || true
    sleep 3
    launch
  fi
}

launch() {
  "${ADB_BIN}" shell am force-stop "${PACKAGE}" >/dev/null 2>&1 || true
  "${ADB_BIN}" shell am start -n "${ACTIVITY}" >/dev/null 2>&1 || true
  sleep 3
}

echo "Using adb: ${ADB_BIN}"
echo "Package:  ${PACKAGE} ($([ "${IS_RELEASE}" -eq 1 ] && echo release || echo debug))"
echo "Artifacts: ${OUT_DIR}"
"${ADB_BIN}" get-state >/dev/null || { echo "No device reachable via ${ADB_BIN}" >&2; exit 1; }

if [ "${STAY_AWAKE}" -eq 1 ]; then
  # Long runs can exceed the screen timeout; a locked keyguard breaks uiautomator
  # assertions and screenshots. Keep the screen on while USB-connected.
  "${ADB_BIN}" shell svc power stayon true >/dev/null 2>&1 || true
  "${ADB_BIN}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "${ADB_BIN}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
fi

if [ "${DO_INSTALL_RELEASE}" -eq 1 ]; then
  echo "Building + installing release APK (needs android/keystore.properties)..."
  (
    cd "${ROOT}/android"
    ./gradlew :app:assembleRelease
  )
  RELEASE_APK="${ROOT}/android/app/build/outputs/apk/release/app-universal-release.apk"
  if [ ! -f "${RELEASE_APK}" ]; then
    echo "Release APK not found at ${RELEASE_APK} (unsigned build? run with keystore)" >&2
    exit 1
  fi
  "${ADB_BIN}" install -r --user 0 "${RELEASE_APK}"
fi

INSTALLED_PKGS="$("${ADB_BIN}" shell pm list packages 2>/dev/null | tr -d '\r' || true)"
if ! grep -q "^package:${PACKAGE}$" <<< "${INSTALLED_PKGS}"; then
  echo "Package ${PACKAGE} is not installed. Install it first (debug: ./scripts/install_debug.sh)." >&2
  exit 1
fi

SUMMARY="${OUT_DIR}/summary.txt"
: > "${SUMMARY}"

###############################################################################
# P1-1  Debug intent extras: inert on release, active on debug
###############################################################################
if want p1; then
  echo ""
  echo "== P1-1 debug extras (seed_test_data / restore_real_data / reset_onboarding) =="
  "${ADB_BIN}" logcat -c 2>/dev/null || true
  launch
  snap "p1-before"
  for extra in seed_test_data restore_real_data reset_onboarding; do
    "${ADB_BIN}" shell am start -n "${ACTIVITY}" --ez "${extra}" true >/dev/null 2>&1 || true
    sleep 2
  done
  # Debug positive control: re-seed LAST so the final screenshot shows sample
  # data (restore_real_data above restores over the seed). Inert on release.
  "${ADB_BIN}" shell am start -n "${ACTIVITY}" --ez seed_test_data true >/dev/null 2>&1 || true
  sleep 2
  snap "p1-after-extras"
  if app_alive && ! logcat_has_fatal; then
    if [ "${IS_RELEASE}" -eq 1 ]; then
      ok "release: extras fired without crash; diary must be UNCHANGED (eyeball p1-after-extras.png vs p1-before.png)"
    else
      ok "debug: extras fired without crash; diary should now show sample data (eyeball p1-after-extras.png)"
    fi
  else
    bad "process died or FATAL EXCEPTION after firing extras"
  fi
fi

###############################################################################
# P2-1  chompass://go/<dest> whitelist: unknown dest ignored, known dest works
###############################################################################
if want p2-1; then
  echo ""
  echo "== P2-1 go-destination whitelist =="
  ALIAS="$(enabled_launcher_alias)"
  if [[ -z "${ALIAS}" ]]; then
    skip "no enabled launcher alias found"
  else
    "${ADB_BIN}" logcat -c 2>/dev/null || true
    launch
    ensure_main_ui
    # Delivery matches ChompassLaunchIntents.openApp: explicit component to the
    # enabled launcher alias with the go URI as data. There is NO manifest filter
    # for chompass://go, so an implicit VIEW intent never reaches the app; the
    # real attack surface is an explicit-component intent from any app.
    "${ADB_BIN}" shell am start -n "${PACKAGE}/${ALIAS}" \
      -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
      -d "chompass://go/__garbage__" >/dev/null 2>&1 || true
    sleep 3
    snap "p2-1-garbage"
    if app_alive && ! logcat_has_fatal; then
      ok "unknown dest 'chompass://go/__garbage__' ignored, no crash (app should be on Home)"
    else
      bad "unknown go-destination crashed the app"
    fi
    "${ADB_BIN}" shell am start -n "${PACKAGE}/${ALIAS}" \
      -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
      -d "chompass://go/progress" >/dev/null 2>&1 || true
    sleep 3
    snap "p2-1-progress"
    if screen_locked; then
      skip "device locked; Progress navigation verified in the unlocked release run (uiautomator showed weight charts)"
    else
      PROGRESS_TEXT="$(ui_text)"
      if app_alive && ! logcat_has_fatal && \
         grep -qE "1 Woche|1W|1 Monat|1M|3 Monate|3M|6 Monate|6M|1 Jahr|1Y|Gewicht eintragen|Log weight|1Н|1М|3М|6М|1Г|Записать вес" <<< "${PROGRESS_TEXT}"; then
        ok "known dest 'chompass://go/progress' navigated to Progress tab"
      else
        bad "known go-destination did not navigate to Progress (UI: ${PROGRESS_TEXT:0:120})"
      fi
    fi
  fi
fi

###############################################################################
# P2-2  Meal-share payload caps: oversized refused, valid payload stages
###############################################################################
if want p2-2; then
  echo ""
  echo "== P2-2 meal-share payload caps =="
  # ~49 KB of 'x' -> base64url ~65.7 KB > MAX_ENCODED_PAYLOAD (64 KB).
  # Windows adb.exe caps the command line at 32 KB, so the URI is fired from
  # a script pushed to the device (device shell arg limit ~128 KB).
  BIG_NAME="$(printf 'x%.0s' $(seq 1 49200))"
  BIG_PAYLOAD="{\"v\":2,\"meals\":[{\"name\":\"${BIG_NAME}\",\"calories\":300,\"protein\":10,\"carbs\":5,\"fat\":5}]}"
  BIG_B64="$(printf '%s' "${BIG_PAYLOAD}" | base64 -w0 | tr '+/' '-_' | tr -d '=')"
  "${ADB_BIN}" logcat -c 2>/dev/null || true
  launch
  ensure_main_ui
  # Explicit component: with both debug and release installed, an implicit VIEW
  # for chompass://add-meal opens the system chooser instead of the app.
  FIRE_SH="/data/local/tmp/fire_big.sh"
  printf 'am start -n %s/app.chompass.MainActivity -a android.intent.action.VIEW -d "chompass://add-meal?d=%s"\n' "${PACKAGE}" "${BIG_B64}" > "${OUT_DIR}/fire_big.sh"
  if "${ADB_BIN}" push "${OUT_DIR}/fire_big.sh" "${FIRE_SH}" >/dev/null 2>&1 && \
     "${ADB_BIN}" shell sh "${FIRE_SH}" >/dev/null 2>&1; then
    "${ADB_BIN}" shell rm -f "${FIRE_SH}" >/dev/null 2>&1 || true
    sleep 3
    snap "p2-2-oversized"
    if app_alive && ! logcat_has_fatal; then
      ok "oversized payload (>64 KB) refused without ANR/crash (eyeball p2-2-oversized.png: no review sheet)"
    else
      bad "oversized payload crashed or ANR'd the app"
    fi
  else
    skip "could not fire oversized URI on device; covered by MealShareSecurityTest.oversizedPayload_isRejected"
  fi
  # Positive control: small valid payload should stage the review sheet.
  SMALL_PAYLOAD='{"v":2,"meals":[{"name":"Test Meal","calories":300,"protein":10,"carbs":5,"fat":5}]}'
  SMALL_B64="$(printf '%s' "${SMALL_PAYLOAD}" | base64 -w0 | tr '+/' '-_' | tr -d '=')"
  "${ADB_BIN}" shell am start -n "${PACKAGE}/app.chompass.MainActivity" \
    -a android.intent.action.VIEW -d "chompass://add-meal?d=${SMALL_B64}" >/dev/null 2>&1 || true
  sleep 3
  snap "p2-2-valid"
  if app_alive && ! logcat_has_fatal; then
    if screen_locked; then
      skip "device locked; valid-payload staging needs an unlocked screen (unit-tested in MealShareSecurityTest)"
    else
      ok "valid payload staged (eyeball p2-2-valid.png: review sheet with 'Test Meal')"
    fi
  else
    bad "valid meal-share payload crashed the app"
  fi
fi

###############################################################################
# P2-3  Oversized shared image skipped at ingest (no OOM)
###############################################################################
if want p2-3; then
  echo ""
  echo "== P2-3 oversized shared image (40 MB > 25 MB/image cap) =="
  BIG_IMG="${OUT_DIR}/big-40mb.jpg"
  dd if=/dev/zero of="${BIG_IMG}" bs=1M count=40 2>/dev/null
  ALIAS="$(enabled_launcher_alias)"
  if [[ -z "${ALIAS}" ]]; then
    skip "no enabled launcher alias found; covered by unit tests (AiImageBytes/BarcodeImageDecoder bounds)"
  else
    "${ADB_BIN}" logcat -c 2>/dev/null || true
    launch
    ensure_main_ui
    if "${ADB_BIN}" push "${BIG_IMG}" /sdcard/Download/big-40mb.jpg >/dev/null 2>&1; then
      "${ADB_BIN}" shell am start --grant-read-uri-permission \
        -a android.intent.action.SEND -t image/jpeg \
        --eu android.intent.extra.STREAM file:///sdcard/Download/big-40mb.jpg \
        -n "${PACKAGE}/${ALIAS}" >/dev/null 2>&1 || true
      sleep 4
      snap "p2-3-oversized-image"
      if app_alive && ! logcat_has_fatal; then
        ok "40 MB share-in skipped at ingest, process alive (eyeball p2-3-oversized-image.png: no photo sheet)"
      else
        bad "oversized share-in crashed the app"
      fi
      "${ADB_BIN}" shell rm -f /sdcard/Download/big-40mb.jpg >/dev/null 2>&1 || true
    else
      skip "adb push to /sdcard/Download failed"
    fi
  fi
fi

###############################################################################
# German locale smoke (full-pack translation)
###############################################################################
if want de; then
  echo ""
  echo "== German locale smoke =="
  ORIG_LOCALES="$("${ADB_BIN}" shell settings get system system_locales 2>/dev/null | tr -d '\r')"
  if "${ADB_BIN}" shell settings put system system_locales de-DE >/dev/null 2>&1; then
    launch
    sleep 3
    snap "de-home"
    if app_alive && ! logcat_has_fatal; then
      ok "app runs under de-DE (eyeball de-home.png: German UI, no English fallback on main surfaces)"
    else
      bad "app crashed under de-DE locale"
    fi
    "${ADB_BIN}" shell settings put system system_locales "${ORIG_LOCALES:-en-US}" >/dev/null 2>&1 || true
  else
    skip "settings put system_locales not permitted on this device; set language manually in Settings"
  fi
fi

###############################################################################
# Home calorie-hero base notch (visual regression, dac3206)
###############################################################################
if want notch; then
  echo ""
  echo "== Home calorie-hero base notch =="
  "${ADB_BIN}" logcat -c 2>/dev/null || true
  launch
  ensure_main_ui
  if [ "${IS_RELEASE}" -eq 1 ]; then
    # Release has no seed extras; the notch is visible once eaten > base budget.
    echo "  (release: seed via real logging or skip; notch needs eaten > sedentary base)"
    snap "notch-home"
  else
    "${ADB_BIN}" shell am start -n "${ACTIVITY}" --ez seed_test_data true --ez seed_over_goal true >/dev/null 2>&1 || true
    sleep 3
    snap "notch-over-goal"
    "${ADB_BIN}" shell am start -n "${ACTIVITY}" --ez seed_test_data true >/dev/null 2>&1 || true
    sleep 3
    snap "notch-under-goal"
  fi
  if app_alive && ! logcat_has_fatal; then
    ok "hero rendered (eyeball notch-*.png: base-boundary notch visible ON TOP of the eaten fill in both states)"
  else
    bad "hero crashed"
  fi
fi

###############################################################################
# Summary
###############################################################################
{
  echo "release-verify ${STAMP}  package=${PACKAGE}"
  echo "PASS=${PASS} FAIL=${FAIL} SKIP=${SKIP}"
} >> "${SUMMARY}"

echo ""
echo "=============================================="
echo "Results: ${PASS} passed, ${FAIL} failed, ${SKIP} skipped"
echo "Screenshots: ${OUT_DIR}"
echo "Summary: ${SUMMARY}"
if [ "${FAIL}" -gt 0 ]; then
  echo ">>> ${FAIL} check(s) FAILED — investigate before release."
  exit 1
fi
echo ">>> All automated checks passed. Eyeball the PNGs (or run the vision skill),"
echo "    then finish the manual checklist documented in this script's section comments."