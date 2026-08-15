#!/usr/bin/env bash
# Hero state walkthrough: seeds each reachable calorie-hero state and captures
# a screenshot per state, for signing off on the expected-day target design.
#
# States (screenshots land in android/build/hero-states/<timestamp>/):
#   1  static-toggle-off      STATIC goal, no caption (unchanged baseline)
#   2  static-toggle-on       STATIC goal + "N active" caption
#   3  add-active-estimate    ADD_ACTIVE, no tracker: legacy tail, goal = base + estimate
#   4  add-active-morning-0   measured-0 morning: projected day "of base+typical",
#                             caption "0 of Y active"
#   5  add-active-midday      live burn under typical: "X of Y active", notch + shades
#   6  add-active-over-typical live burn past typical: success-colored stretch,
#                             "+Z over typical" (no dot)
#   7  add-active-over-goal   eaten > expected target: full ring, "0 left"
#   8  restore                real data back (only in full runs)
#
# Usage:
#   ./scripts/hero_states.sh                  # full walkthrough + restore
#   ./scripts/hero_states.sh --state 4        # single state (no restore)
#   ./scripts/hero_states.sh --rebuild        # build + install debug APK first
#   ./scripts/hero_states.sh --wait 8         # settle seconds per state (default 6)
#   ./scripts/hero_states.sh --no-restore     # skip the final restore_real_data
#   ./scripts/hero_states.sh --tap-x 520 --tap-y 400
#                                            # also tap the ⓘ budget-sheet icon and
#                                            # capture "<state>-sheet.png" (coords are
#                                            # device-dependent; find via uiautomator
#                                            # dump or eyeballing the screenshot)
#
# Env:
#   ADB_BIN          adb binary (auto-detects Windows adb.exe from WSL if unset)
#   PACKAGE          default: app.chompass.debug
#   OVERRIDE_MID     live burn for state 5/7 (default 350; must be < your typical)
#   OVERRIDE_OVER    live burn for state 6 (default 1200; must be > your typical,
#                    which is your profile's activity estimate — the caption shows it)
#
# Notes:
#   - The USB device is reachable from the Windows adb server, not WSL adb; the
#     script auto-detects adb.exe like install_debug.sh.
#   - Each state force-stops the app first so the seed extras fire on onCreate.
#   - After states 4-6, also eyeball the widget: medium shows "/ <target>" and
#     "X of Y active", small shows numbers only. Screenshot the launcher
#     manually:  adb exec-out screencap -p > widget.png

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

PACKAGE="${PACKAGE:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
OVERRIDE_MID="${OVERRIDE_MID:-350}"
OVERRIDE_OVER="${OVERRIDE_OVER:-1200}"
WAIT_SECS=14
DO_REBUILD=0
DO_RESTORE=1
STATE_ONLY=0
TAP_X=""
TAP_Y=""

while [ $# -gt 0 ]; do
  case "$1" in
    --rebuild) DO_REBUILD=1 ;;
    --no-restore) DO_RESTORE=0 ;;
    --state) STATE_ONLY="$2"; shift ;;
    --wait) WAIT_SECS="$2"; shift ;;
    --tap-x) TAP_X="$2"; shift ;;
    --tap-y) TAP_Y="$2"; shift ;;
    -h|--help)
      sed -n '2,38p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1 (try --help)" >&2
      exit 2
      ;;
  esac
  shift
done

if [ -n "${TAP_X}" ] && [ -z "${TAP_Y}" ] || [ -z "${TAP_X}" ] && [ -n "${TAP_Y}" ]; then
  echo "--tap-x and --tap-y must be given together" >&2
  exit 2
fi

if [ "${STATE_ONLY}" -ne 0 ] && { [ "${STATE_ONLY}" -lt 1 ] || [ "${STATE_ONLY}" -gt 7 ]; }; then
  echo "--state expects 1..7 (see --help)" >&2
  exit 2
fi

if [ "${DO_REBUILD}" -eq 1 ]; then
  "${ROOT}/scripts/install_debug.sh" --no-launch
fi

echo "Using adb: ${ADB_BIN}"
"${ADB_BIN}" devices

if ! "${ADB_BIN}" shell pm path "${PACKAGE}" >/dev/null 2>&1; then
  echo "${PACKAGE} is not installed. Build + install first (./scripts/install_debug.sh or --rebuild)." >&2
  exit 1
fi

OUT_DIR="${ROOT}/android/build/hero-states/$(date +%Y-%m-%dT%H%M%S)"
mkdir -p "${OUT_DIR}"
echo "Screenshots → ${OUT_DIR}"
echo

# Best-effort: wake the screen so captures are not black.
"${ADB_BIN}" shell wm dismiss-keyguard >/dev/null 2>&1 || true

# Seeding a year of data is slow on device (big DataStore writes + GC churn),
# and the in-seed process thrashes afterwards; always restart cleanly before
# capturing. Polls:
#   - logcat for MainActivity's "debug actions complete" marker (seed done)
#   - the UI dump for the hero ("CALORIES") after the clean relaunch
POLL_TIMEOUT="${POLL_TIMEOUT:-150}"

wait_for_log() {
  local pattern="$1" waited=0
  while [ "${waited}" -lt "${POLL_TIMEOUT}" ]; do
    if "${ADB_BIN}" logcat -d 2>/dev/null | grep -q "${pattern}"; then
      sleep 3
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

wait_for_home() {
  local waited=0
  while [ "${waited}" -lt 60 ]; do
    "${ADB_BIN}" shell "rm -f /sdcard/ui_home.xml; uiautomator dump /sdcard/ui_home.xml" >/dev/null 2>&1
    if "${ADB_BIN}" shell cat /sdcard/ui_home.xml 2>/dev/null | grep -q 'text="CALORIES"'; then
      sleep 3
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

STATE_NUM=0
capture_state() {
  local name="$1"; shift
  local expected="$1"; shift
  STATE_NUM=$((STATE_NUM + 1))
  local tag
  tag="$(printf '%02d-%s' "${STATE_NUM}" "${name}")"
  local png="${OUT_DIR}/${tag}.png"

  echo "── State ${STATE_NUM}: ${name}"
  echo "    extras: $*"
  echo "    check:  ${expected}"
  "${ADB_BIN}" shell am force-stop "${PACKAGE}" >/dev/null
  "${ADB_BIN}" logcat -G 8M 2>/dev/null || true
  "${ADB_BIN}" logcat -c 2>/dev/null || true
  "${ADB_BIN}" shell am start -n "${ACTIVITY}" "$@" >/dev/null
  if ! wait_for_log "debug actions complete"; then
    echo "    WARNING: seed marker not seen within ${POLL_TIMEOUT}s; capturing whatever is on screen" >&2
  fi
  # Clean relaunch: the in-seed process thrashes on the seeded DataStore.
  "${ADB_BIN}" shell am force-stop "${PACKAGE}" >/dev/null
  "${ADB_BIN}" shell am start -n "${ACTIVITY}" >/dev/null
  if ! wait_for_home; then
    echo "    WARNING: home not detected within 60s; capturing anyway" >&2
  fi
  "${ADB_BIN}" exec-out screencap -p > "${png}"
  echo "    saved:  ${png}"

  if [ -n "${TAP_X}" ] && [ -n "${TAP_Y}" ]; then
    # Open the ⓘ budget sheet for the extra-info capture.
    "${ADB_BIN}" shell input tap "${TAP_X}" "${TAP_Y}"
    sleep 1
    "${ADB_BIN}" exec-out screencap -p > "${OUT_DIR}/${tag}-sheet.png"
    echo "    saved:  ${OUT_DIR}/${tag}-sheet.png"
    "${ADB_BIN}" shell input keyevent KEYCODE_BACK
    sleep 1
  fi
  echo
}

if [ "${STATE_ONLY}" -eq 0 ] || [ "${STATE_ONLY}" -eq 1 ]; then
  capture_state static-toggle-off \
    "STATIC goal, no caption, no ⓘ; 'X left' vs base goal. Unchanged legacy look." \
    --ez seed_active_calories true --es set_gauge_mode static --ez set_show_active_calories false
fi
if [ "${STATE_ONLY}" -eq 0 ] || [ "${STATE_ONLY}" -eq 2 ]; then
  capture_state static-toggle-on \
    "STATIC goal + 'N active' caption under the ring (N = today's seeded burn)." \
    --ez seed_active_calories true --es set_gauge_mode static --ez set_show_active_calories true
fi
if [ "${STATE_ONLY}" -eq 0 ] || [ "${STATE_ONLY}" -eq 3 ]; then
  capture_state add-active-estimate \
    "No tracker: legacy dim tail [base → base+estimate] + notch; goal 'of base+estimate'; no caption; ⓘ sheet shows 'Goal base + estimate active' + 'estimated from your activity level'." \
    --ez seed_active_calories true --ez clear_debug_activity true --ez set_show_active_calories false
fi
if [ "${STATE_ONLY}" -eq 0 ] || [ "${STATE_ONLY}" -eq 4 ]; then
  capture_state add-active-morning-0 \
    "Projected day from 7am: goal 'of base+typical' (typical = Y in the caption); dim typical zone; caption '0 of Y active'; ring empty; ⓘ sheet explains 'Goal base + typical' + 'Burned today: 0'." \
    --ez seed_active_calories true --ei active_today_override 0
fi
if [ "${STATE_ONLY}" -eq 0 ] || [ "${STATE_ONLY}" -eq 5 ]; then
  capture_state add-active-midday \
    "Opaque live shade grows from the base notch toward the typical zone; caption '${OVERRIDE_MID} of Y active'; eaten fill shares the same scale." \
    --ez seed_active_calories true --ei active_today_override "${OVERRIDE_MID}"
fi
if [ "${STATE_ONLY}" -eq 0 ] || [ "${STATE_ONLY}" -eq 6 ]; then
  capture_state add-active-over-typical \
    "Live shade stretches past the typical zone in success color (no dot); caption '+Z over typical'; arc end grown to base + ${OVERRIDE_OVER}. If no success color, your typical (caption Y) exceeds ${OVERRIDE_OVER} — rerun with OVERRIDE_OVER bigger." \
    --ez seed_active_calories true --ei active_today_override "${OVERRIDE_OVER}"
fi
if [ "${STATE_ONLY}" -eq 0 ] || [ "${STATE_ONLY}" -eq 7 ]; then
  capture_state add-active-over-goal \
    "Eaten > expected target: full primary ring, '0 left'; shades + caption still visible behind the fill." \
    --ez seed_active_calories true --ei active_today_override "${OVERRIDE_MID}" --ez seed_over_goal true
fi

if [ "${STATE_ONLY}" -eq 0 ] && [ "${DO_RESTORE}" -eq 1 ]; then
  echo "── Restoring real data"
  "${ADB_BIN}" shell am force-stop "${PACKAGE}" >/dev/null
  "${ADB_BIN}" logcat -c 2>/dev/null || true
  "${ADB_BIN}" shell am start -n "${ACTIVITY}" --ez restore_real_data true >/dev/null
  wait_for_log "debug actions complete" || true
  "${ADB_BIN}" shell am force-stop "${PACKAGE}" >/dev/null
  "${ADB_BIN}" shell am start -n "${ACTIVITY}" >/dev/null
  wait_for_home || true
  echo "    Real data restored; the app relaunched clean."
else
  echo "── Done (${STATE_NUM} state(s) captured; real data NOT restored — relaunch with --ez restore_real_data true when finished)"
fi

echo
echo "Widget check (manual): after states 4-6, open the launcher and screenshot the widget:"
echo "  ${ADB_BIN} exec-out screencap -p > widget.png"
echo "  Medium: '/ <target>' center + 'X of Y active' under 'left'. Small: numbers only."
echo
echo "Captures: ${OUT_DIR}"
