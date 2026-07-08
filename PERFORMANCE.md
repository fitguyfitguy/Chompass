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
scripts/capture_android_perf_baseline.sh org.codeberg.fitguy.nofud
```

Artifacts are written under:

- `android/build/perf-baseline/<timestamp>/`

## Current Baseline Status

- Capture attempted in agent environment.
- Blocker: `adb` is not installed/available in this runtime.
- Next step: run the script on a machine with Android platform tools and a connected device/emulator.

## Validation Cadence

After each performance phase:

1. Run the baseline script again.
2. Compare `start_run_*.txt` totals against previous run.
3. Compare `gfx_framestats.txt` jank/frame distribution.
4. Compare `meminfo.txt` resident footprint and Java heap usage.
5. Keep only changes with measurable startup or smoothness improvement.
