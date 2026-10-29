#!/usr/bin/env bash
# Capture release screenshots from the REAL app on a physical Android device.
#
# These are the published images (README, Hugo site, optional Codeberg release
# attachments): native panel resolution, real Compose frames, real seeded data,
# real system bars. The JVM `screenshotTest` previews stay a pixel-regression
# harness only (see scripts/export_release_screenshots.sh).
#
# Determinism:
#   - debug build (`app.chompass.debug`) + seed_full fixture data
#   - per-app locale forced to en-US for the capture, restored afterwards
#   - appearance + brand teal pinned via the `screenshot_appearance` debug extra
#   - system animation scales set to 0 for the capture, restored afterwards
#   - scripted AI response (`demo_ai` + `demo_ai_hold_ms`) for the overlay frame
#
# Device reachability: works with WSL adb OR the Windows adb.exe (auto-detected
# via scripts/_adb_resolve.sh) — the maintainer's USB phone lives on Windows adb.
#
# Usage:
#   ./scripts/capture_device_screenshots.sh                 # build+install+seed, all shots
#   ./scripts/capture_device_screenshots.sh --no-build      # reuse the built APK
#   ./scripts/capture_device_screenshots.sh --no-install    # use the installed debug app
#   ./scripts/capture_device_screenshots.sh --no-seed       # keep existing debug-app data
#   ./scripts/capture_device_screenshots.sh --only home,progress
#   ./scripts/capture_device_screenshots.sh --appearance dark
#   ./scripts/capture_device_screenshots.sh --out /tmp/shots  # review copy, repo untouched
#   ./scripts/capture_device_screenshots.sh --list
#
# Env:
#   ADB_BIN   adb binary (auto-detects Windows adb.exe from WSL)
#   PACKAGE   default: app.chompass.debug
#   SERIAL    device serial (same as --serial)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

PACKAGE="${PACKAGE:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
RELEASE_DIR="${ROOT}/release-screenshots"
README_DIR="${ROOT}/docs/screenshots"
# F-Droid fastlane metadata: listing order, numbered files.
STORE_DIR="${ROOT}/metadata/en-US/images/phoneScreenshots"
STORE_ORDER=(
  home.png add-food.png progress.png meal-components.png ai-analysis.png
  coach.png recipes.png settings.png progress-plots.png
)
OUT_OVERRIDE=""
SERIAL="${SERIAL:-}"
DO_BUILD=1
DO_INSTALL=1
DO_SEED=1
ONLY=""
APPEARANCES="light dark"
LIST_ONLY=0

# key|appearance|numbered release file|README file (blank = not in README)
SCREEN_TABLE=(
  "home|light|01-home-light.png|home-light.png"
  "progress|light|02-progress-light.png|"
  "coach|light|03-coach-light.png|"
  "settings|light|04-settings-light.png|"
  "add-food|light|07-add-food-light.png|"
  "progress-plots|light|14-progress-plots-light.png|"
  "scale-metrics|light|16-scale-metrics-light.png|"
  "home|dark|05-home-dark.png|home.png"
  "progress|dark|06-progress-dark.png|progress.png"
  "coach|dark|08-coach-dark.png|coach.png"
  "settings|dark|09-settings-dark.png|settings.png"
  "add-food|dark|10-add-food-dark.png|add-food.png"
  "meal-components|dark|11-meal-components-dark.png|meal-components.png"
  "recipes|dark|12-recipes-dark.png|recipes.png"
  "ai-analysis|dark|13-ai-analysis-dark.png|ai-analysis.png"
  "progress-plots|dark|15-progress-plots-dark.png|progress-plots.png"
  "scale-metrics|dark|17-scale-metrics-dark.png|scale-metrics.png"
)

usage() { sed -n '2,38p' "$0"; exit "${1:-0}"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="${2:?--serial needs a value}"; shift ;;
    --no-build) DO_BUILD=0 ;;
    --no-install) DO_INSTALL=0 ;;
    --no-seed) DO_SEED=0 ;;
    --only) ONLY="${2:?--only needs a value}"; shift ;;
    --appearance) APPEARANCES="${2:?--appearance needs a value}"; shift ;;
    --out) OUT_OVERRIDE="${2:?--out needs a value}"; shift ;;
    --list) LIST_ONLY=1 ;;
    -h|--help) usage 0 ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
  shift
done

if [ "${LIST_ONLY}" -eq 1 ]; then
  printf '%s\n' "${SCREEN_TABLE[@]}" | cut -d'|' -f1,2 | sort -u
  exit 0
fi

# ── adb plumbing ────────────────────────────────────────────────────────────
ADB=("${ADB_BIN}")
if [ -n "${SERIAL}" ]; then
  ADB+=(-s "${SERIAL}")
fi

# `< /dev/null`: `adb shell` forwards stdin, which would eat a `while read`
# loop's heredoc (only the first screen would ever be captured).
device() { "${ADB[@]}" "$@" < /dev/null; }

device get-state >/dev/null 2>&1 || { echo "adb unusable: ${ADB_BIN}" >&2; exit 1; }

# CRLF: adb.exe writes Windows line endings through the WSL pipe.
DEVICES="$(device devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')"
if [ -z "${DEVICES}" ]; then
  echo "No device attached to ${ADB_BIN}." >&2
  echo "  WSL adb:  ./scripts/adb_wifi.sh --tcpip   (USB bootstrap, see docs/DEVELOPMENT.md)" >&2
  echo "  Windows:  keep the phone on USB, or pass ADB_BIN=/path/to/adb.exe" >&2
  exit 1
fi
if [ -z "${SERIAL}" ] && [ "$(printf '%s\n' "${DEVICES}" | wc -l)" -gt 1 ]; then
  echo "Multiple devices; pass --serial. Candidates:" >&2
  printf '  %s\n' "${DEVICES}" >&2
  exit 1
fi
echo "==> adb: ${ADB_BIN}${SERIAL:+ (serial ${SERIAL})}"

# ── device facts ────────────────────────────────────────────────────────────
WM_SIZE="$(device shell wm size | tr -d '\r')"
SIZE="$(printf '%s\n' "${WM_SIZE}" | sed -n 's/^Physical size: //p')"
[ -n "${SIZE}" ] || { echo "Cannot read wm size:" >&2; echo "${WM_SIZE}" >&2; exit 1; }
WIDTH="${SIZE%x*}"
HEIGHT="${SIZE#*x}"
DENSITY="$(device shell wm density | tr -d '\r' | sed -n 's/^Physical density: //p')"
echo "==> display: ${WIDTH}x${HEIGHT} @ ${DENSITY:-?} dpi"

# Status bar height in px: the published frames are cropped below it, so the
# clock, battery, VPN, and notification icons never reach an exported PNG.
STATUS_BAR_H="$(device shell dumpsys window 2>/dev/null | tr -d '\r' \
  | sed -n 's/.*type=statusBars frame=\[0,0\]\[[0-9]*,\([0-9]*\)\].*/\1/p' | head -1)"
if ! printf '%s' "${STATUS_BAR_H}" | grep -qE '^[0-9]+$' \
  || [ "${STATUS_BAR_H}" -lt 40 ] \
  || [ "${STATUS_BAR_H}" -ge $(( HEIGHT / 4 )) ]; then
  STATUS_BAR_H=$(( 24 * ${DENSITY:-420} / 160 ))
fi
echo "==> status bar: ${STATUS_BAR_H}px (cropped from published images)"

if [ "${WIDTH}" -lt 1000 ]; then
  echo "Panel width ${WIDTH}px is too low for release images (want >= 1000px)." >&2
  exit 1
fi

# ── restore-on-exit state (locale, animation scales) ─────────────────────────
PREV_LOCALE=""
LOCALE_SAVED=0
PREV_ANIMATOR=""
PREV_TRANSITION=""
PREV_WINDOW=""
PREV_STAYON=""
STAYON_SAVED=0
KEEP_AWAKE_PID=""

restore_device() {
  set +e
  if [ -n "${KEEP_AWAKE_PID}" ] && declare -F stop_keep_awake >/dev/null; then
    stop_keep_awake
  fi
  # Empty previous value = follow system; `--locales ""` clears the override.
  # Anything that is not a locale tag (a stale "[locales]" artifact) is cleared
  # instead of being written back.
  if [ "${LOCALE_SAVED}" -eq 1 ]; then
    if printf '%s' "${PREV_LOCALE}" | grep -qE '^([A-Za-z][A-Za-z0-9-]*)(,[A-Za-z][A-Za-z0-9-]*)*$'; then
      device shell cmd locale set-app-locales "${PACKAGE}" --locales "${PREV_LOCALE}" >/dev/null 2>&1
    else
      device shell cmd locale set-app-locales "${PACKAGE}" --locales "" >/dev/null 2>&1
    fi
  fi
  [ -n "${PREV_ANIMATOR}" ] &&
    device shell settings put global animator_duration_scale "${PREV_ANIMATOR}" >/dev/null 2>&1
  [ -n "${PREV_TRANSITION}" ] &&
    device shell settings put global transition_animation_scale "${PREV_TRANSITION}" >/dev/null 2>&1
  [ -n "${PREV_WINDOW}" ] &&
    device shell settings put global window_animation_scale "${PREV_WINDOW}" >/dev/null 2>&1
  if [ "${STAYON_SAVED}" -eq 1 ]; then
    device shell settings put global stay_on_while_plugged_in "${PREV_STAYON}" >/dev/null 2>&1
  fi
  set -e
}
trap restore_device EXIT

if ! device shell pm path "${PACKAGE}" >/dev/null 2>&1; then
  DO_INSTALL=1
fi

if [ "${DO_INSTALL}" -eq 1 ]; then
  INSTALL_FLAGS=(--no-launch)
  [ "${DO_BUILD}" -eq 1 ] || INSTALL_FLAGS+=(--no-build)
  echo "==> Build + install ${PACKAGE}"
  ADB_BIN="${ADB_BIN}" PACKAGE="${PACKAGE}" "${ROOT}/scripts/install_debug.sh" "${INSTALL_FLAGS[@]}"
fi

# Output shape: "Locales for app.<pkg> for user 0 are [en-US]" / "are []".
PREV_LOCALE="$(device shell cmd locale get-app-locales "${PACKAGE}" 2>/dev/null \
  | tr -d '\r' | sed -n 's/.* are \[\(.*\)\]$/\1/p')"
if [ -z "${PREV_LOCALE}" ] && ! device shell cmd locale get-app-locales "${PACKAGE}" 2>/dev/null | tr -d '\r' | grep -q 'are \[\]'; then
  echo "WARNING: could not read the current app locale; leaving en-US in place." >&2
else
  LOCALE_SAVED=1
fi
echo "==> locale: ${PREV_LOCALE:-<system>} -> en-US (restored on exit)"
device shell cmd locale set-app-locales "${PACKAGE}" --locales en-US >/dev/null

PREV_STAYON="$(device shell settings get global stay_on_while_plugged_in | tr -d '\r')"
STAYON_SAVED=1
# Keep the panel awake for the whole run (seeding can idle >30 s and a locked
# screen would make every capture a lock-screen frame).
device shell settings put global stay_on_while_plugged_in 7 >/dev/null

PREV_ANIMATOR="$(device shell settings get global animator_duration_scale | tr -d '\r')"
PREV_TRANSITION="$(device shell settings get global transition_animation_scale | tr -d '\r')"
PREV_WINDOW="$(device shell settings get global window_animation_scale | tr -d '\r')"
device shell settings put global animator_duration_scale 0 >/dev/null
device shell settings put global transition_animation_scale 0 >/dev/null
device shell settings put global window_animation_scale 0 >/dev/null

# ── UI helpers (uiautomator-driven, no coordinate hardcoding) ────────────────
UI_XML="$(mktemp)"
trap 'rm -f "${UI_XML}"; restore_device' EXIT

dump_ui() {
  local target="$1" attempt
  for attempt in 1 2 3 4; do
    device shell "rm -f /sdcard/chompass_ui.xml; uiautomator dump /sdcard/chompass_ui.xml" >/dev/null 2>&1 || true
    device shell cat /sdcard/chompass_ui.xml 2>/dev/null | tr -d '\r' > "${target}" || true
    if [ -s "${target}" ] && ! grep -q 'ERROR' "${target}"; then
      return 0
    fi
    sleep 0.6
  done
  return 1
}

# node_bounds <xml> <attribute> <literal value> [contains]
node_bounds() {
  local xml="$1" attr="$2" value="$3" mode="${4:-exact}" nodes
  nodes="$(tr '>' '\n' < "${xml}" | grep -F "${attr}=\"" || true)"
  if [ "${mode}" = "contains" ]; then
    nodes="$(printf '%s\n' "${nodes}" | grep -F "${value}" || true)"
  else
    nodes="$(printf '%s\n' "${nodes}" | grep -F "${attr}=\"${value}\"" || true)"
  fi
  printf '%s\n' "${nodes}" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1
}

tap_bounds() {
  local x1 y1 x2 y2
  IFS=, read -r x1 y1 x2 y2 < <(
    printf '%s' "$1" | grep -oE '[0-9]+' | paste -sd,
  )
  [ -n "${x2:-}" ] || { echo "tap_bounds: bad bounds '$1'" >&2; return 1; }
  device shell input tap "$(( (x1 + x2) / 2 ))" "$(( (y1 + y2) / 2 ))" >/dev/null
}

tap_text() { # tap_text <text> [contains]
  dump_ui "${UI_XML}" || return 1
  local bounds
  bounds="$(node_bounds "${UI_XML}" text "$1" "${2:-exact}")"
  [ -n "${bounds}" ] || { echo "tap_text: '${1}' not found" >&2; return 1; }
  tap_bounds "${bounds}"
}

tap_desc() {
  dump_ui "${UI_XML}" || return 1
  local bounds
  bounds="$(node_bounds "${UI_XML}" content-desc "$1")"
  [ -n "${bounds}" ] || { echo "tap_desc: '${1}' not found" >&2; return 1; }
  tap_bounds "${bounds}"
}

has_text() { # has_text <xml> <text> [contains]
  local bounds
  bounds="$(node_bounds "$1" text "$2" "${3:-exact}")"
  [ -n "${bounds}" ]
}

has_desc() { # has_desc <xml> <desc>
  local bounds
  bounds="$(node_bounds "$1" content-desc "$2")"
  [ -n "${bounds}" ]
}

wait_text() { # wait_text <text> [timeout_s] [contains]
  local text="$1" timeout="${2:-20}" mode="${3:-exact}" waited=0 bounds
  while [ "${waited}" -lt $(( timeout * 2 )) ]; do
    if dump_ui "${UI_XML}" &&
      bounds="$(node_bounds "${UI_XML}" text "${text}" "${mode}")" &&
      [ -n "${bounds}" ]; then
      return 0
    fi
    sleep 0.5
    waited=$((waited + 1))
  done
  echo "wait_text: '${text}' not visible after ${timeout}s" >&2
  return 1
}

wait_desc() {
  local desc="$1" timeout="${2:-20}" waited=0 bounds
  while [ "${waited}" -lt $(( timeout * 2 )) ]; do
    if dump_ui "${UI_XML}" &&
      bounds="$(node_bounds "${UI_XML}" content-desc "${desc}")" &&
      [ -n "${bounds}" ]; then
      return 0
    fi
    sleep 0.5
    waited=$((waited + 1))
  done
  echo "wait_desc: '${desc}' not visible after ${timeout}s" >&2
  return 1
}

wait_log() {
  local pattern="$1" timeout="${2:-300}" waited=0
  while [ "${waited}" -lt "${timeout}" ]; do
    if device logcat -d 2>/dev/null | grep -q "${pattern}"; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

swipe_up() { device shell input swipe "$(( WIDTH / 2 ))" "$(( HEIGHT * 3 / 4 ))" "$(( WIDTH / 2 ))" "$(( HEIGHT / 4 ))" 400 >/dev/null; }

wake_device() {
  device shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  device shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

# Crop the status bar from the exported PNGs (in place). The raw staging
# directory keeps the full frames for debugging.
sanitize_pngs() {
  [ "$#" -gt 0 ] || return 0
  CROP_TOP="${STATUS_BAR_H}" EXPECTED_H="$(( HEIGHT - STATUS_BAR_H ))" \
    uv run --with pillow python - "$@" <<'PY'
import os
import sys
from pathlib import Path
from PIL import Image

top = int(os.environ["CROP_TOP"])
expected = int(os.environ["EXPECTED_H"])
for root in sys.argv[1:]:
    for path in sorted(Path(root).glob("*.png")):
        im = Image.open(path)
        if im.height <= top:
            raise SystemExit(f"{path}: {im.width}x{im.height} is shorter than the crop")
        out = im.crop((0, top, im.width, im.height))
        if out.height != expected:
            raise SystemExit(f"{path}: cropped to {out.height}px, expected {expected}px")
        out.save(path)
        print(f"    status bar cropped: {path}")
PY
}

# Screen timeout / doze can still surface the (non-secure) lock screen between
# slow steps; recover continuously instead of failing a capture on it.
start_keep_awake() {
  (
    while true; do
      "${ADB[@]}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 < /dev/null || true
      "${ADB[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 < /dev/null || true
      sleep 15
    done
  ) &
  KEEP_AWAKE_PID=$!
}

stop_keep_awake() {
  if [ -n "${KEEP_AWAKE_PID}" ]; then
    kill "${KEEP_AWAKE_PID}" >/dev/null 2>&1 || true
    KEEP_AWAKE_PID=""
  fi
}

start_app() { # start_app <extra args...>
  device shell am start -n "${ACTIVITY}" "$@" >/dev/null 2>&1
}

force_stop() {
  device shell am force-stop "${PACKAGE}" >/dev/null
  sleep 0.7
}

goto_route() { # goto_route <home|progress|coach|settings>
  device shell am start -n "${ACTIVITY}" -d "chompass://go/$1" >/dev/null 2>&1
}

# ── capture ────────────────────────────────────────────────────────────────
STAMP="$(date +%Y%m%d_%H%M%S)"
STAGE="${ROOT}/android/build/device-screenshots/${STAMP}"
mkdir -p "${STAGE}"

CHECK_DIMS=0
capture() { # capture <stage-file>
  local file="$1" info w h
  sleep 0.8
  device exec-out screencap -p > "${file}"
  info="$(file -b "${file}")"
  case "${info}" in
    PNG\ image\ data*)
      w="$(printf '%s' "${info}" | sed -E 's/.*, ([0-9]+) x ([0-9]+).*/\1/')"
      h="$(printf '%s' "${info}" | sed -E 's/.*, ([0-9]+) x ([0-9]+).*/\2/')"
      ;;
    *)
      echo "capture: not a PNG: ${file} (${info})" >&2
      return 1
      ;;
  esac
  if [ "${w}" -ne "${WIDTH}" ] || [ "${h}" -ne "${HEIGHT}" ]; then
    echo "capture: ${file} is ${w}x${h}, expected ${WIDTH}x${HEIGHT}" >&2
    return 1
  fi
  CHECK_DIMS=$((CHECK_DIMS + 1))
  echo "    ${file#"${ROOT}"/} (${w}x${h})"
}

# Home is the anchor for every sheet flow.
goto_home() {
  goto_route home
  wait_desc "Add food" 25
}

# A completed analysis (or a saved draft) can restore a review sheet on launch,
# and its "Recovered analysis" chip takes the FAB's slot. Dismiss both until
# Home's "+" FAB is reachable.
wait_home() {
  local i
  for i in 1 2 3 4 5 6; do
    if dump_ui "${UI_XML}"; then
      if has_desc "${UI_XML}" "Add food"; then
        return 0
      fi
      if has_desc "${UI_XML}" "Discard recovered analysis"; then
        tap_desc "Discard recovered analysis" || true
        sleep 0.8
        continue
      fi
    fi
    device shell input keyevent 4 >/dev/null 2>&1 || true
    sleep 0.8
  done
  wait_desc "Add food" 20
}

open_add_food() {
  goto_home
  tap_desc "Add food"
  wait_text "Add food" 20
  # The search field autofocuses and raises the IME; Back hides the IME first.
  device shell input keyevent 4 >/dev/null 2>&1 || true
  sleep 0.6
  if ! dump_ui "${UI_XML}" || ! has_text "${UI_XML}" "Add food"; then
    tap_desc "Add food"
    wait_text "Add food" 20
    device shell input keyevent 4 >/dev/null 2>&1 || true
    sleep 0.5
  fi
  # The saved-meals tab selection persists across launches; pin Recents so the
  # hub shot never ships the Recipes tab.
  tap_text "Recents" || true
  sleep 0.4
  wait_text "Recents" 10
}

open_note_entry() {
  open_add_food
  tap_text "View More"
  sleep 0.5
  tap_text "Note"
  wait_text "Describe your food" 20
}

scroll_until_text() { # scroll_until_text <text> <max swipes> [contains]
  local text="$1" max="${2:-4}" mode="${3:-contains}" i
  for i in $(seq 1 "${max}"); do
    swipe_up
    sleep 0.4
    if dump_ui "${UI_XML}" && has_text "${UI_XML}" "${text}" "${mode}"; then
      return 0
    fi
  done
  return 1
}

capture_key() { # capture_key <key> <appearance> <file>
  local key="$1" file="$2"
  case "${key}" in
    home)
      goto_home
      ;;
    progress)
      goto_route progress
      wait_text "Net Change" 25 contains
      # Pin the range: the chip selection persists, so a previous run must not
      # change the published chart.
      tap_text "3M" || true
      sleep 1.2
      ;;
    coach)
      goto_route coach
      wait_desc "Reset chat" 25
      ;;
    settings)
      goto_route settings
      wait_text "Personal Info" 25 contains
      ;;
    add-food)
      open_add_food
      ;;
    recipes)
      open_add_food
      tap_text "Recipes"
      wait_text "Overnight oats" 20 contains
      ;;
    meal-components)
      goto_home
      scroll_until_text "Chicken thigh + Brown rice" 5 contains || true
      tap_text "Chicken thigh + Brown rice" contains
      wait_text "Edit Food" 20
      scroll_until_text "Ingredients (" 4 contains || true
      swipe_up
      sleep 0.4
      ;;
    ai-analysis)
      open_note_entry
      dump_ui "${UI_XML}" || return 1
      local edit_bounds
      edit_bounds="$(tr '>' '\n' < "${UI_XML}" | grep 'class="android.widget.EditText"' | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)"
      [ -n "${edit_bounds}" ] || { echo "ai-analysis: no text field" >&2; return 1; }
      tap_bounds "${edit_bounds}"
      sleep 0.3
      # Deterministic prompt: replace any restored draft.
      device shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A >/dev/null 2>&1 || true
      device shell input keyevent 67 >/dev/null
      sleep 0.2
      device shell input text "Greek%syogurt%swith%shoney%sand%salmonds" >/dev/null
      sleep 0.4
      device shell input keyevent 4 >/dev/null   # hide the IME
      sleep 0.4
      tap_text "Analyze"
      wait_text "Filling in nutrition" 25 contains
      sleep 0.5
      capture "${file}"
      return 0
      ;;
    progress-plots)
      goto_route progress
      wait_text "Net Change" 25 contains
      # 1M shows a single reading; 3M draws the weekly tape series.
      tap_text "3M" || true
      sleep 1.2
      local i
      for i in 1 2 3 4 5 6; do
        swipe_up
        if dump_ui "${UI_XML}" && [ -n "$(node_bounds "${UI_XML}" text "Waist" exact)" ]; then
          break
        fi
      done
      wait_text "Waist" 10
      swipe_up
      ;;
    scale-metrics)
      # seed_full writes composition rows every third weigh-in, so the
      # Scale metrics card exists below the body cards on Progress.
      goto_route progress
      wait_text "Net Change" 25 contains
      tap_text "3M" || true
      sleep 1.2
      scroll_until_text "Scale metrics" 8 contains || true
      swipe_up
      sleep 0.6
      ;;
    *)
      echo "Unknown capture key: ${key}" >&2
      return 1
      ;;
  esac
  capture "${file}"
}

selected() { # selected <key>: <appearance>
  [ -z "${ONLY}" ] && return 0
  case ",${ONLY}," in
    *",$1,"*) return 0 ;;
    *) return 1 ;;
  esac
}

# ── seed + launch ──────────────────────────────────────────────────────────
start_keep_awake
wake_device

if [ "${DO_SEED}" -eq 1 ]; then
  echo "==> Seeding seed_full (first launch can take ~2 min on device)"
  force_stop
  device logcat -c >/dev/null 2>&1 || true
  start_app --ez seed_full true --ez demo_ai true --ei demo_ai_hold_ms 8000 \
    --ez screenshot_lean true --es screenshot_appearance dark --ez clear_pending_draft true
  if ! wait_log "debug actions complete" 300; then
    echo "Seed did not report completion; capturing whatever is rendered." >&2
  fi
  # Seeded DataStore makes the in-seed process thrash: always relaunch clean.
  force_stop
fi

for appearance in ${APPEARANCES}; do
  case "${appearance}" in
    light|dark|oled) ;;
    *) echo "Unknown appearance: ${appearance}" >&2; exit 2 ;;
  esac
  echo ""
  echo "==> Appearance: ${appearance}"

  while IFS='|' read -r key entry_appearance release_name readme_name; do
    [ "${entry_appearance}" = "${appearance}" ] || continue
    selected "${key}" || continue
    echo "  - ${key} -> ${release_name}"
    # Clean relaunch per screen: no sheet/overlay/demo-hold can leak into the
    # next frame, and the appearance pref is pinned before the first pixel.
    force_stop
    start_app --es screenshot_appearance "${appearance}" --ez screenshot_lean true \
      --ez demo_ai true --ei demo_ai_hold_ms 8000 --ez clear_pending_draft true
    wait_home
    capture_key "${key}" "${STAGE}/${release_name}"
  done <<< "$(printf '%s\n' "${SCREEN_TABLE[@]}")"
done

# ── export ─────────────────────────────────────────────────────────────────
if [ -n "${OUT_OVERRIDE}" ]; then
  mkdir -p "${OUT_OVERRIDE}"
  echo "==> Copying to ${OUT_OVERRIDE} (repo artifacts untouched)"
  cp "${STAGE}"/*.png "${OUT_OVERRIDE}/"
  sanitize_pngs "${OUT_OVERRIDE}"
  echo ""
  echo "Captured ${CHECK_DIMS} screenshots -> ${OUT_OVERRIDE}"
  exit 0
fi

# Only a full light+dark run may clear the published set; a targeted
# `--only` run updates its own files and leaves the rest untouched.
FULL_SET=0
if [ -z "${ONLY}" ]; then
  case " ${APPEARANCES} " in
    *" light "*)
      case " ${APPEARANCES} " in
        *" dark "*) FULL_SET=1 ;;
      esac
      ;;
  esac
fi

mkdir -p "${RELEASE_DIR}" "${README_DIR}"
if [ "${FULL_SET}" -eq 1 ]; then
  rm -f "${RELEASE_DIR}"/*.png "${README_DIR}"/*.png 2>/dev/null || true
fi
cp "${STAGE}"/*.png "${RELEASE_DIR}/"
echo "==> ${RELEASE_DIR}/ (Codeberg attachments)"
while IFS='|' read -r key entry_appearance release_name readme_name; do
  [ -n "${readme_name}" ] || continue
  [ -f "${STAGE}/${release_name}" ] || continue
  cp "${STAGE}/${release_name}" "${README_DIR}/${readme_name}"
  echo "    docs/screenshots/${readme_name}"
done <<< "$(printf '%s\n' "${SCREEN_TABLE[@]}")"

# Only a complete run rewrites the store listing: partial runs must not leave a
# half-numbered fastlane directory.
if [ "${FULL_SET}" -eq 1 ]; then
  mkdir -p "${STORE_DIR}"
  rm -f "${STORE_DIR}"/*.png
  store_n=1
  for store_name in "${STORE_ORDER[@]}"; do
    [ -f "${README_DIR}/${store_name}" ] || continue
    cp "${README_DIR}/${store_name}" "${STORE_DIR}/${store_n}.png"
    echo "    metadata/en-US/images/phoneScreenshots/${store_n}.png"
    store_n=$((store_n + 1))
  done
fi

echo "==> Cropping the ${STATUS_BAR_H}px status bar from published images"
sanitize_pngs "${RELEASE_DIR}" "${README_DIR}"
if [ "${FULL_SET}" -eq 1 ]; then
  sanitize_pngs "${STORE_DIR}"
fi

cat <<EOF

Device screenshots complete (${CHECK_DIMS} captures, published at ${WIDTH}x$(( HEIGHT - STATUS_BAR_H )); status bar cropped).

Artifacts:
  ${RELEASE_DIR}/                 (gitignored; attach with --with-screenshots)
  ${README_DIR}/           (committed; README + chompass.app gallery)
  ${STORE_DIR}   (committed; F-Droid listing)
  ${STAGE}        (raw capture)

Commit docs/screenshots/ with the release, then:
  ./scripts/publish_release.sh <version> [--with-screenshots]
EOF
