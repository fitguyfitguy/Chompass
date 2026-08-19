# Android performance capture

Repeatable measurements under a **fully utilized** debug diary (year of food,
2y metrics, water, recipes, favorites, chat). Build in WSL; talk to the USB
device with Windows `adb` (see [DEVELOPMENT.md](DEVELOPMENT.md)).

## One-shot suite

Seed once, then capture **without** reseeding (otherwise splash time is the
seeder, not the app):

```bash
./scripts/install_debug.sh            # build + install + seed_full
# wait until Home is interactive (first seed ~15s write)

ADB_BIN=... scripts/capture_android_perf_baseline.sh
```

`install_debug.sh --reseed` only when you need a fresh fixture. The capture
script force-stops and cold-starts with **no** seed extras.

Standalone entry pipeline (same fixture, or seed itself):

```bash
SEED=0 scripts/perf_entry_benchmark.sh 3          # diary already on disk
scripts/perf_entry_benchmark.sh                   # seed_full, then 3 analyses
SEED_MODE=slim scripts/perf_entry_benchmark.sh    # old trio instead of seed_full
```

Needs a Gemini key in `android/secrets.properties` (debug builds only).

## What the baseline script records

| Step | How | Artifacts under `android/build/perf-baseline/<timestamp>/` |
|------|-----|--------------------------------------------------------------|
| Cold start ×5 | `am start -W` | `start_run_1..5.txt` (WaitTime) |
| Home settle mem | `dumpsys meminfo` | `meminfo_home.txt` |
| Progress first open | `chompass://go/progress` | `gfx_framestats_progress.txt`, `meminfo_progress.txt` |
| Range chips 1W→All | taps + `FudAIPerf rangeChange` | `gfx_framestats_range_*.txt`, `logcat_range_*.txt` |
| Tabs Home/Progress/Coach/Settings | `chompass://go/<tab>` | `gfx_framestats_tab_*.txt` |
| Settings Food / Goals | `chompass://go/settings/food` etc. | `gfx_framestats_settings_*.txt` |
| Add Food hub | FAB tap | `gfx_framestats_add_food.txt`, `logcat_add_food.txt` (`hubOpen`) |
| Relog first hub row | `run_relog_benchmark` (no coordinates) | `logcat_relog_bench.txt` (`relogBench`, `save`) |
| Analyze+save ×N | `perf_entry_benchmark.sh` `SEED=0` | `entry_perf.log` + summarizer |

Do **not** use raw tab-bar taps for Progress: on a 1080-wide Pixel,
`PROGRESS_TAB_X=540` lands between Progress and Coach. Deep links are the
source of truth.

Skip the live AI / relog tails with `RUN_ENTRY_BENCH=0` / `RUN_RELOG_BENCH=0`.

## Fixture (`install_debug.sh`)

Default is `seed_full`. Flags: `--slim` (old trio), `--keto`, `--busy-home`,
`--reseed`, `--no-seed`. Intent extras: `seed_full`, `seed_busy_home`,
`run_entry_benchmark` / `benchmark_count`, `run_relog_benchmark` /
`relog_benchmark_count`.

## FudAIPerf marks (debug only)

Tag `FudAIPerf`. Release builds emit nothing.

```
op=progress phase=rangeChange ms=323 range=1Y foods=1265 weights=588
op=hubOpen phase=quickRelog ms=1015 perRow=10
op=relogBench phase=addEntry ms=4773 i=0 name=...
op=save phase=dataStore ms=590 month=2026-08
op=analyzeText phase=promptBuild ms=8
op=analyzeText phase=parse ms=3 chars=1830
op=net phase=call host=... ttfbMs=980 totalMs=1420 status=200
op=benchmark phase=entry i=0 ms=57354 status=ok
op=benchmark phase=done count=3 ok=1 fail=2
```

`dataStore` writes one month bucket (`month=yyyy-MM`), not the whole diary.
A photo analysis can still fire a second `net` call (`op=inferServing`) when
the main prompt returns empty `unit_options`.

Re-summarize any entry log:

```bash
uv run python scripts/summarize_entry_perf.py android/build/perf-entry/<timestamp>/entry_perf.log
```

Manual interactive capture (you tap the UI yourself) is still
`scripts/capture_entry_perf.sh`.

## Compare runs

After each perf change, on the same device, same seed already on disk:

1. WaitTime in `start_run_*.txt`
2. Progress `rangeChange` ms for 1Y / All
3. `hubOpen` / `relogBench` / `save dataStore` in the FudAIPerf logs
4. Java heap in `meminfo_home.txt` vs `meminfo_progress.txt`
5. Keep only changes that move a number or a felt hitch
