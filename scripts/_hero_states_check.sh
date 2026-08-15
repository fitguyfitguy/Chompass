#!/usr/bin/env bash
# Ground-truth check: seeds each hero state, restarts the app cleanly (the
# seeded DataStore makes the in-seed process thrash), then dumps the a11y
# content-descriptions + screenshots the settled home.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"
PKG="${PACKAGE:-app.chompass.debug}"
ACT="${PKG}/app.chompass.MainActivity"
OUT="${ROOT}/android/build/hero-states/check-$(date +%Y-%m-%dT%H%M%S)"
mkdir -p "${OUT}"
"${ADB_BIN}" logcat -G 8M >/dev/null 2>&1 || true

# Fresh dump: delete first so a failed uiautomator dump can never serve stale xml.
dump_xml() {
  local tag="$1"
  local xml=""
  for i in 1 2 3 4 5; do
    "${ADB_BIN}" shell "rm -f /sdcard/ui_${tag}.xml; uiautomator dump /sdcard/ui_${tag}.xml" >/dev/null 2>&1
    xml="$("${ADB_BIN}" shell cat "/sdcard/ui_${tag}.xml" 2>/dev/null)" || true
    if [ -n "${xml}" ] && ! printf '%s' "${xml}" | grep -q "ERROR"; then break; fi
    sleep 2
  done
  printf '%s' "${xml}"
}

wait_for_log() {
  local pattern="$1" timeout="${2:-150}" waited=0
  while [ "${waited}" -lt "${timeout}" ]; do
    if "${ADB_BIN}" logcat -d 2>/dev/null | grep -q "${pattern}"; then
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
    local xml
    xml="$(dump_xml "home_${STATE_TAG}")"
    if printf '%s' "${xml}" | grep -q 'text="CALORIES"'; then
      sleep 3
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

state() {
  local name="$1"; shift
  STATE_TAG="${name}"
  echo "===== ${name}"
  # 1) seed (extras run on cold start)
  "${ADB_BIN}" shell am force-stop "${PKG}" >/dev/null
  "${ADB_BIN}" logcat -c 2>/dev/null || true
  "${ADB_BIN}" shell am start -n "${ACT}" "$@" >/dev/null 2>&1
  if ! wait_for_log "debug actions complete"; then
    echo "    WARNING: seed marker not seen; capturing whatever is on screen" >&2
  fi
  # 2) clean relaunch: the in-seed process thrashes on the seeded DataStore
  "${ADB_BIN}" shell am force-stop "${PKG}" >/dev/null
  "${ADB_BIN}" shell am start -n "${ACT}" >/dev/null 2>&1
  if ! wait_for_home; then
    echo "    WARNING: home not detected within 60s; capturing anyway" >&2
  fi
  # 3) capture
  local xml
  xml="$(dump_xml "final_${STATE_TAG}")"
  printf '%s' "${xml}" | grep -oE '(text|content-desc)="[^"]{2,}"' | sort -u | \
    grep -viE "protein|kohlenhydrate|fett|ballast|wasser|water|frühstück|greek|yogurt|progress|fortschritt|coach|einstellungen|start|mehr|essen|keine|kcal ·|auto|&#|ml\"|^text=\"[0-9]+:[0-9]+\"$|^text=\"[0-9]+\"$|^text=\"[0-9]+,[0-9]\"$|^text=\"[0-9]+g" | head -25
  "${ADB_BIN}" exec-out screencap -p > "${OUT}/${name}.png" 2>/dev/null
  echo "    png: ${OUT}/${name}.png"
  echo
}

state "1-static-toggle-off" --ez seed_active_calories true --es set_gauge_mode static --ez set_show_active_calories false
state "2-static-toggle-on"  --ez seed_active_calories true --es set_gauge_mode static --ez set_show_active_calories true
state "3-add-active-estimate" --ez seed_active_calories true --ez clear_debug_activity true --ez set_show_active_calories false
state "4-add-active-morning-0" --ez seed_active_calories true --ei active_today_override 0
state "5-add-active-midday" --ez seed_active_calories true --ei active_today_override 350
state "6-add-active-over-typical" --ez seed_active_calories true --ei active_today_override 1200
state "7-add-active-over-goal" --ez seed_active_calories true --ei active_today_override 350 --ez seed_over_goal true

echo "===== restoring real data"
"${ADB_BIN}" shell am force-stop "${PKG}" >/dev/null
"${ADB_BIN}" logcat -c 2>/dev/null || true
"${ADB_BIN}" shell am start -n "${ACT}" --ez restore_real_data true >/dev/null 2>&1
wait_for_log "debug actions complete" || true
"${ADB_BIN}" shell am force-stop "${PKG}" >/dev/null
"${ADB_BIN}" shell am start -n "${ACT}" >/dev/null 2>&1
echo "done → ${OUT}"
