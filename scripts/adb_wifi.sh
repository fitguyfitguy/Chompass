#!/usr/bin/env bash
# adb over Wi-Fi for the maintainer phone — device work runs natively in WSL.
#
# Kills the "WSL builds / Windows adb installs" split: once connected, the WSL
# adb server (port 5038) owns the phone and Gradle/Maestro/scripts talk to it
# directly. USB stays as the fallback for pairing bootstrap and perf captures
# that must match release conditions.
#
# Two bootstrap paths:
#   1. --tcpip (preferred, zero interaction): while the USB cable is plugged in,
#      switches the phone's adbd to TCP on port 5555 via the Windows adb
#      (`adb tcpip 5555`), reads the phone's Wi-Fi IP, connects from WSL and
#      saves the target. Survives unplugging until the phone reboots.
#   2. pair (Android 11+ wireless debugging): one-time `pair` with the code
#      shown under Developer options → Wireless debugging, then `connect` with
#      the ip:port shown on the same screen (port changes on every toggle).
#
# Usage:
#   ./scripts/adb_wifi.sh status            # devices, saved target, mdns hints
#   ./scripts/adb_wifi.sh --tcpip [serial]  # USB→TCP bootstrap (cable plugged in)
#   ./scripts/adb_wifi.sh pair IP:PAIR_PORT CODE   # wireless-debugging pairing
#   ./scripts/adb_wifi.sh connect [IP[:PORT]]      # default: saved target
#   ./scripts/adb_wifi.sh forget            # drop saved target
#
# Env:
#   ADB_BIN          WSL adb to use after connect (default: adb on PATH; devenv
#                    pins the 5038 server). WIN_ADB overrides the Windows adb.exe
#                    used for the --tcpip bootstrap (auto-detected otherwise).
#   ADB_WIFI_TARGET  override saved target as IP[:PORT] (port defaults 5555).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Windows adb for the --tcpip bootstrap (reuse the proven auto-detection).
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

CONF_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/chompass"
CONF_FILE="${CONF_DIR}/adb-wifi"

WSL_ADB="${ADB_IN:-}"          # filled below; WSL-side adb
TARGET="${ADB_WIFI_TARGET:-}"

usage() { sed -n '2,32p' "$0"; exit "${1:-0}"; }

# WSL adb: plain `adb` (devenv provides it). Windows adb.exe is WIN_ADB.
WIN_ADB="${WIN_ADB:-}"
case "${ADB_BIN}" in
  *.exe) WIN_ADB="${WIN_ADB:-${ADB_BIN}}" ;;
esac

save_target() {
  mkdir -p "${CONF_DIR}"
  printf 'TARGET=%s\nMODE=%s\n' "$1" "$2" > "${CONF_FILE}"
  chmod 600 "${CONF_FILE}"
}

load_target() {
  if [ -z "${TARGET}" ] && [ -f "${CONF_FILE}" ]; then
    # shellcheck disable=SC1090
    TARGET="$(. "${CONF_FILE}" && printf '%s' "${TARGET}")"
  fi
  printf '%s\n' "${TARGET}"
}

wsl_adb() {
  command -v adb >/dev/null 2>&1 || { echo "No adb on PATH (enter devenv shell)." >&2; exit 1; }
  printf 'adb\n'
}

connected_to_wsl() {
  adb devices 2>/dev/null | awk '$1 == t && $2 == "device" { found = 1 } END { exit !found }' t="$1"
}

cmd_status() {
  echo "== WSL adb (${ANDROID_ADB_SERVER_PORT:-5037}) =="
  adb devices -l
  local saved
  saved="$(load_target)"
  if [ -n "${saved}" ]; then
    echo "Saved target: ${saved} ($(connected_to_wsl "${saved}" && echo connected || echo 'not connected'))"
  else
    echo "Saved target: none (run: $0 --tcpip, or pair + connect)"
  fi
  if [ -n "${WIN_ADB}" ]; then
    echo "== Windows adb (USB) =="
    "${WIN_ADB}" devices -l || true
  fi
  echo "== mDNS discovery (best effort; multicast often dies in WSL2 NAT) =="
  adb mdns services 2>/dev/null || true
}

# --tcpip: enable adbd TCP via the USB-connected Windows adb, then connect natively.
cmd_tcpip() {
  [ -n "${WIN_ADB}" ] || { echo "Windows adb.exe not found; set WIN_ADB=/path/to/adb.exe" >&2; exit 1; }
  local serial="${1:-}"
  local sel=()
  [ -n "${serial}" ] && sel=( -s "${serial}" )
  if ! "${WIN_ADB}" "${sel[@]}" get-state >/dev/null 2>&1; then
    echo "No USB device on the Windows adb server. Plug the cable in, check: '${WIN_ADB}' devices" >&2
    exit 1
  fi
  local ip
  # wlan0 is the phone's Wi-Fi interface; fall back to any global-scope inet.
  ip="$("${WIN_ADB}" "${sel[@]}" shell ip -f inet addr show wlan0 2>/dev/null \
      | sed -n 's/.*inet \([0-9.]*\).*/\1/p' | head -1)"
  if [ -z "${ip}" ]; then
    echo "Could not read wlan0 IP (Wi-Fi off or unusual interface naming)." >&2
    "${WIN_ADB}" "${sel[@]}" shell ip -f inet addr show 2>/dev/null || true
    exit 1
  fi
  echo "Switching adbd to TCP (port 5555) on ${ip} ..."
  "${WIN_ADB}" "${sel[@]}" tcpip 5555
  sleep 2
  TARGET="${ip}:5555"
  do_connect tcpip
}

do_connect() {
  local mode="${1:-wd}"
  [ -n "${TARGET}" ] || { echo "No target. Run '$0 --tcpip', 'pair'+'connect IP:PORT', or set ADB_WIFI_TARGET." >&2; exit 1; }
  case "${TARGET}" in
    *:*) : ;;
    *) TARGET="${TARGET}:5555" ;;
  esac
  echo "Connecting WSL adb → ${TARGET} ..."
  if adb connect "${TARGET}" | tee /dev/stderr | grep -qE 'connected to|already connected'; then
    :
  else
    echo "adb connect failed. Check: phone and PC on the same LAN, wireless debugging" >&2
    echo "still toggled on (or run --tcpip again after a reboot), firewall." >&2
    exit 1
  fi
  save_target "${TARGET}" "${mode}"
  # Surface as a plain device line.
  sleep 1
  adb devices -l
  echo "OK: device is now owned by the WSL adb server — Gradle, install_debug.sh and"
  echo "Maestro run against it without Windows adb."
}

cmd_pair() {
  [ $# -ge 2 ] || { echo "Usage: $0 pair IP:PAIR_PORT CODE  (both shown under Developer options → Wireless debugging → Pair device)" >&2; exit 2; }
  adb pair "$1" "$2"
  echo "Paired. Now find the (different!) port on the Wireless debugging screen and run:"
  echo "  $0 connect <phone-ip>:<PORT>"
}

cmd_forget() {
  local saved
  saved="$(load_target)"
  [ -n "${saved}" ] && adb disconnect "${saved}" >/dev/null 2>&1 || true
  rm -f "${CONF_FILE}"
  echo "Forgot saved target."
}

[ $# -ge 1 ] || usage 1
CMD="$1"; shift
case "${CMD}" in
  status)  cmd_status ;;
  --tcpip) cmd_tcpip "$@" ;;
  pair)    cmd_pair "$@" ;;
  connect)
    # Priority: explicit arg > ADB_WIFI_TARGET > saved target.
    if [ $# -ge 1 ]; then TARGET="$1"; else load_target >/dev/null; fi
    do_connect "${ADB_WIFI_MODE:-wd}"
    ;;
  forget)  cmd_forget ;;
  -h|--help) usage ;;
  *) echo "Unknown command: ${CMD}" >&2; usage 2 ;;
esac
