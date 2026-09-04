#!/usr/bin/env bash
# One-shot device test sweep (debug build, Windows adb) for the recent change
# set: per-constituent micronutrients, ingredient-micro model gating,
# midnight-crossing meal schedules, seeded fixture integrity.
#
# Runs, in order, each benchmark as an intent extra and waits for its
# completion line on the FudAIPerf logcat tag:
#   seed_full (+ seed_macro_cycle) → run_entry_benchmark (analyze+save,
#   exercises constituents + strong/weak model gate) → relog → local entry →
#   water sip → hub chip → day switch → flip.
#
# Usage: ./scripts/device_test_sweep.sh [--skip-install]
# Env: ADB_BIN (auto-detected), PACKAGE (default app.chompass.debug)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/_adb_resolve.sh
. "${ROOT}/scripts/_adb_resolve.sh"

PACKAGE="${PACKAGE:-app.chompass.debug}"
ACTIVITY="${PACKAGE}/app.chompass.MainActivity"
APK_DIR="${ROOT}/android/app/build/outputs/apk/debug"

SKIP_INSTALL=0
[ "${1:-}" = "--skip-install" ] && SKIP_INSTALL=1

log() { printf '[sweep] %s\n' "$*"; }

# --- wait for device -------------------------------------------------------
log "waiting for device ($ADB_BIN devices)..."
for i in $(seq 1 60); do
  if "$ADB_BIN" wait-for-device 2>/dev/null; then break; fi
  sleep 2
  if [ "$i" = 60 ]; then log "ERROR: no device appeared after 2 minutes"; exit 1; fi
done
log "device state: $("$ADB_BIN" get-state)"
case "$("$ADB_BIN" shell getprop sys.usb.state 2>/dev/null | tr -d '\r')" in
  *adb*) log "adb authorized" ;;
  *) log "WARNING: device may be unauthorized; accept the USB debugging prompt" ;;
esac

# --- install + launch helpers ---------------------------------------------
if [ "$SKIP_INSTALL" = 0 ]; then
  log "installing debug APK..."
  "$ADB_BIN" install --user 0 -r "$APK_DIR/app-arm64-v8a-debug.apk"
fi

launch() { # launch <extra>...
  "$ADB_BIN" shell am force-stop "$PACKAGE"
  "$ADB_BIN" logcat -c
  "$ADB_BIN" shell am start -n "$ACTIVITY" "$@" >/dev/null
}

perf_log() { "$ADB_BIN" logcat -d -s FudAIPerf:V 2>/dev/null || true; }

wait_done() { # wait_done <op> <done-phase> <timeout-s> <label>
  local op="$1" phase="$2" tmo="$3" label="$4" n=0 out
  while [ "$n" -lt "$tmo" ]; do
    out="$(perf_log)"
    if printf '%s\n' "$out" | grep -Eq "op=$op (status=fail|phase=fail)"; then
      log "FAIL: $label (fail line)"; return 1
    fi
    if printf '%s\n' "$out" | grep -q "op=$op phase=$phase"; then
      if printf '%s\n' "$out" | grep -Eq "op=$op phase=$phase .*(fail=[1-9]|err=)"; then
        log "FAIL: $label (failures in done line)"; return 1
      fi
      log "PASS: $label"
      return 0
    fi
    sleep 5; n=$((n + 5))
  done
  log "FAIL: $label (no op=$op phase=$phase within ${tmo}s)"
  return 1
}

check_seeded() { # check_seeded <timeout-s> <label>
  local tmo="$1" label="$2" n=0
  while [ "$n" -lt "$tmo" ]; do
    if "$ADB_BIN" logcat -d 2>/dev/null | grep -q "seedMacroCycle failed"; then
      log "FAIL: $label (seedMacroCycle failed — check MealCatalog midnight handling)"
      return 1
    fi
    if "$ADB_BIN" logcat -d 2>/dev/null | grep -q "debug actions complete"; then
      log "PASS: $label"
      return 0
    fi
    sleep 5; n=$((n + 5))
  done
  log "FAIL: $label (no 'debug actions complete' within ${tmo}s)"
  return 1
}

FAILURES=0

# 1. Full seed incl. macro cycle (covers MealCatalog midnight-crossing schedules)
log "1/8 seeding full fixture + macro cycle..."
launch --ez seed_full true --ez seed_macro_cycle true
check_seeded 300 "seed_full + seed_macro_cycle" || FAILURES=$((FAILURES + 1))

# 2. Entry benchmark: real analyze+save pipeline (constituents, model gate)
log "2/8 entry benchmark (analyze+save, 3 items)..."
launch --ez run_entry_benchmark true --ei benchmark_count 3
wait_done benchmark done 900 "run_entry_benchmark" || FAILURES=$((FAILURES + 1))

# 3. Relog benchmark
log "3/8 relog benchmark..."
launch --ez run_relog_benchmark true --ei relog_benchmark_count 3
wait_done relogBench done 120 "run_relog_benchmark" || FAILURES=$((FAILURES + 1))

# 4. Local entry benchmark (persist canned meals through Home)
log "4/8 local entry benchmark..."
wait_done entryLocal done 120 "run_local_entry_benchmark" || FAILURES=$((FAILURES + 1))

# 5. Water sip benchmark
log "5/8 water sip benchmark..."
launch --ez run_water_sip_benchmark true --ei water_sip_benchmark_count 5
wait_done waterSip done 60 "run_water_sip_benchmark" || FAILURES=$((FAILURES + 1))

# 6. Hub chip load benchmark
log "6/8 hub benchmark..."
launch --ez run_hub_benchmark true
wait_done hubOpen benchRows 120 "run_hub_benchmark" || FAILURES=$((FAILURES + 1))

# 7. Day switch benchmark (yesterday then today through Home)
log "7/8 day switch benchmark..."
launch --ez run_day_switch_benchmark true
wait_done daySwitch bench 120 "run_day_switch_benchmark" || FAILURES=$((FAILURES + 1))

# 8. Flip benchmark (hub + relog uiAck + local entry + sip + day switch)
log "8/8 flip benchmark..."
launch --ez run_flip_benchmark true
wait_done flipBench done 180 "run_flip_benchmark" || FAILURES=$((FAILURES + 1))

log "---- perf log ----"
perf_log | grep -E "phase=(start|done|bench|benchRows|fail)|status=fail" || true

if [ "$FAILURES" -gt 0 ]; then
  log "SWEEP FINISHED: $FAILURES failure(s)"
  exit 1
fi
log "SWEEP FINISHED: all green"
