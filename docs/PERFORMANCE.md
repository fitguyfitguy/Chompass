# Android Performance Baseline

This project now includes a repeatable baseline capture script:

- `scripts/capture_android_perf_baseline.sh`

It gathers:

- cold-start timing via `am start -W`
- frame stats via `dumpsys gfxinfo ... framestats`
- memory snapshots via `dumpsys meminfo`

## Run

```bash
scripts/capture_android_perf_baseline.sh
```

Or for release package:

```bash
scripts/capture_android_perf_baseline.sh app.chompass
```

Artifacts are written under:

- `android/build/perf-baseline/<timestamp>/`

## Current Baseline Status

- Capture requires the device; run via the WSL2 build + Windows adb split (see
  [DEVELOPMENT.md](DEVELOPMENT.md)) or `ADB_BIN` pointing at a Windows `adb.exe`.
- Artifacts land in `android/build/perf-baseline/<timestamp>/` (gitignored).

## Validation Cadence

After each performance phase:

1. Run the baseline script again.
2. Compare `start_run_*.txt` totals against previous run.
3. Compare `gfx_framestats.txt` jank/frame distribution.
4. Compare `meminfo.txt` resident footprint and Java heap usage.
5. Keep only changes with measurable startup or smoothness improvement.

## Entry-addition timing (FudAIPerf)

Debug builds emit per-phase latency for the add-a-food-entry pipeline under the
logcat tag `FudAIPerf` (gated by `BuildConfig.DEBUG`; release builds emit
nothing). Each line is one measurement in `key=value` form:

```
op=analyzeText phase=promptBuild ms=8
op=analyzeText phase=parse ms=3 chars=1830
op=net phase=call host=generativelanguage.googleapis.com dnsMs=12 connectMs=45 tlsMs=60 ttfbMs=980 totalMs=1420 reqBytes=2100 respBytes=1830 status=200
op=save phase=imageWrite ms=27 bytes=142000
op=save phase=dataStore ms=41 entries=214
op=save phase=healthWrite ms=88
```

Phases: `promptBuild` (input assembly + prefs read), `parse` (JSON deserialize),
`net` (OkHttp round-trip: DNS/connect/TLS/TTFB/total + byte counts, covers every
AI/STT/OpenFoodFacts call), and the Phase-2 persistence `imageWrite` / `dataStore`
/ `healthWrite`. Notes: `dataStore` re-serializes the whole log per add, so it
scales with `entries=`; a single photo analysis fires **two** `net` calls (main
analysis + serving-unit inference, `op=inferServing`) when the main analysis
prompt's own `unit_options` comes back empty; the primary prompts were
strengthened to make that less common, but the fallback still exists
structurally since `inferServing` depends on the main call's parsed result;
`net` metrics are `-1` when a phase is skipped (e.g. pooled connection) or on
failure (`status=-1`).

### Capture

The USB device is reachable from Windows adb, not WSL adb. Build + install the
debug APK (see [DEVELOPMENT.md](DEVELOPMENT.md)), then:

```bash
# From WSL: the script auto-detects a Windows adb.exe (Downloads/platform-tools,
# the Android SDK, C:\platform-tools) and drops the WSL-only adb server port.
scripts/capture_entry_perf.sh
# Override the adb binary if it lives elsewhere:
ADB_BIN=/mnt/c/path/to/adb.exe scripts/capture_entry_perf.sh
# Optional: LAUNCH=1 to cold-launch first; DURATION=60 to auto-stop after 60s.
```

```powershell
# Natively on Windows (device attached there):
scripts\capture_entry_perf.ps1          # -Launch to cold-start first
```

Add entries on the device (text, photo + Save, manual, barcode) while it records;
press Ctrl-C to stop. Raw log + summary land in
`android/build/perf-entry/<timestamp>/entry_perf.log`. Re-summarize any log with:

```bash
uv run python scripts/summarize_entry_perf.py android/build/perf-entry/<timestamp>/entry_perf.log
```

The summarizer prints per-`(op, phase)` count/min/p50/p90/max/mean(ms) and a
network-phase breakdown.

### All-in-one benchmark

`scripts/perf_entry_benchmark.sh` does the whole loop in one shot: seed the app's
settings + tracking data, fire N real analyze+save requests through the live AI
provider, then capture and summarize their timings. It stops automatically when
the batch finishes (a `op=benchmark phase=done` marker). Debug build only, and it
needs a Gemini key in `secrets.properties` so requests actually go out.

```bash
scripts/perf_entry_benchmark.sh          # 3 entries, seed_full first
scripts/perf_entry_benchmark.sh 5        # 5 entries
SEED=0 scripts/perf_entry_benchmark.sh   # benchmark only, no data seeding
SEED_MODE=slim scripts/perf_entry_benchmark.sh  # old trio instead of seed_full
```

`scripts/capture_android_perf_baseline.sh` also runs this at the end (`SEED=0`)
plus a coordinate-free relog bench (`run_relog_benchmark`) after Add Food.
Set `RUN_ENTRY_BENCH=0` / `RUN_RELOG_BENCH=0` to skip.

It force-stops, waits 2s, and cold-launches MainActivity with extras in one intent:
`seed_full` (or `SEED_MODE=slim` for the older trio) populate the app,
and `run_entry_benchmark` + `benchmark_count` drive
`EntryPerfBenchmark.run(...)`, which calls `FoodAnalysisService.analyzeText` and
persists via `FoodRepository.addEntry` for each sample, so every phase
(promptBuild / net / parse / dataStore / healthWrite) is timed. The batch also
emits `op=benchmark phase=entry ms=<wall>` per entry (full analyze+save time).
Artifacts land under `android/build/perf-entry/<timestamp>/` like the manual
capture.
