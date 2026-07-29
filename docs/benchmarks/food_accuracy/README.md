# Food accuracy benchmark

Python research harness for prompt and model comparison on food text/image entry.

Full documentation: [`docs/FOOD_ACCURACY_BENCHMARK.md`](../../docs/FOOD_ACCURACY_BENCHMARK.md)

**Current results & defaults:** [`docs/FOOD_ACCURACY_BENCHMARK_STATUS.md`](../../docs/FOOD_ACCURACY_BENCHMARK_STATUS.md)

**Free-tier default: `--provider openrouter --model nofud/free`.** Do not use stock `openrouter/free` for accuracy benches (it routes to content-safety models). `nofud/free` rebuilds its pool from the live OpenRouter catalog on each process start (new free models appear; cancelled ones drop); the pool is cached for that run only.

## Layout

```
docs/benchmarks/food_accuracy/
  manifest/           # Checked-in JSONL eval splits
  data/               # Downloaded datasets (gitignored)
  results/            # Eval outputs (gitignored)
  run_eval.py         # Main CLI
  compare_runs.py     # Diff two summary.csv files
  image_text_variants.py  # Shared L1/L2 writers
  download_jfb.py
  download_nutrition5k.py
  download_acetada.py
  download_nutritionverse_real.py
  build_fndds_manifest.py
```

## Prompt A/B (text)

```bash
# Baseline: production text prompt
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt production_text \
  --provider stub \
  --out docs/benchmarks/food_accuracy/results/ab/production_text

# Candidate: few-shot units
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt fewshot_units \
  --provider stub \
  --out docs/benchmarks/food_accuracy/results/ab/fewshot_units

uv run python docs/benchmarks/food_accuracy/compare_runs.py \
  docs/benchmarks/food_accuracy/results/ab/production_text/summary.csv \
  docs/benchmarks/food_accuracy/results/ab/fewshot_units/summary.csv
```

## Grounded metrics (identity / source / portion)

See [`docs/GROUNDED_ENTRY.md`](../../docs/GROUNDED_ENTRY.md) (**WIP — not production**; UI disabled until readiness checklist).

Latest text-42 Flash Lite tool-loop (2026-07-22 post-roadmap): WMAPE **12.8%** / ±20% **78.6%** / parse **100%** — better than the prior ~18% grounded run, still far behind single-shot (~4.8–5.7% WMAPE). Keep treating as research only.

**Smoke (no network):**

```bash
./scripts/check_food_accuracy_smoke.sh
# or: devenv tasks run benchmark:food-accuracy-smoke
```

**Full grounded eval** (tool loop → USDA SQLite lookup → score):

```bash
uv run python docs/benchmarks/food_accuracy/run_grounded_eval.py \
  --provider openrouter --model google/gemini-3.5-flash-lite \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --usda-db android/app/src/debug/assets/usda/usda_foods.sqlite \
  --sleep 6 --retries 2 \
  --out docs/benchmarks/food_accuracy/results/grounded_tool_gemini35_flash_lite_text
```

Bad-case / history-OFF manifests:

- `manifest/grounded_bad_cases.jsonl`
- `manifest/grounded_history_off.jsonl`
- `manifest/retrieval_golden.json` (+ `check_retrieval_golden.py`)
- Thresholds: `baselines/grounded_text_thresholds.json`

**Image grounded** (after `download_jfb.py`): pass a JFB manifest the same way; prefer paired compare against single-shot `run_eval.py` on the same IDs.

Legacy recognize → lexical top-1: add `--legacy-top1`.

Score a pre-made JSONL recognition+lookup trace:

```bash
uv run python docs/benchmarks/food_accuracy/grounded_metrics.py \
  --trace docs/benchmarks/food_accuracy/manifest/grounded_trace_example.jsonl
```

### Resume failed/missing only

```bash
# Preview which IDs would be re-run
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --prompt compact \
  --resume docs/benchmarks/food_accuracy/results/prompt_ab_gemma/compact \
  --dry-run

# Fill 429/parse failures only (keeps successful samples)
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter \
  --model google/gemma-4-26b-a4b-it:free \
  --prompt compact \
  --resume docs/benchmarks/food_accuracy/results/prompt_ab_gemma/compact \
  --sleep 20 \
  --retries 3
```

Replace `--provider stub` with **NoFUD free routing** (preferred):

```bash
uv run python docs/benchmarks/food_accuracy/list_nofud_free_pool.py --vision --show-excluded
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model nofud/free \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl --limit 10
```

## Fixed eval split

| File | Items | Modality |
|------|-------|----------|
| `manifest/eval_text.jsonl` | 42 | Text (FNDDS seed + composites) |
| `data/manifests/jfb.jsonl` | 50 default | Image L0 — no user text (after `download_jfb.py`) |
| `data/manifests/jfb_image_text_l1.jsonl` | 50 | Image + meal title as user note |
| `data/manifests/jfb_image_text_l2.jsonl` | 50 | Image + ingredient names as user note |
| `data/manifests/n5k.jsonl` | 20 default | Image/metadata (after `download_nutrition5k.py`) |

## Image + description A/B

Paired L0/L1/L2 on JFB with pinned Gemma 26B and `compact`:

```bash
uv run python docs/benchmarks/food_accuracy/download_jfb.py --limit 50

for level in l0_image_only:jfb.jsonl l1_meal_name:jfb_image_text_l1.jsonl l2_ingredient_names:jfb_image_text_l2.jsonl; do
  name="${level%%:*}"
  manifest="${level##*:}"
  uv run python docs/benchmarks/food_accuracy/run_eval.py \
    --provider openrouter --model google/gemma-4-26b-a4b-it:free \
    --prompt compact --sleep 15 --retries 3 \
    --manifest "docs/benchmarks/food_accuracy/data/manifests/$manifest" \
    --out "docs/benchmarks/food_accuracy/results/image_text_ab/$name"
done

uv run python docs/benchmarks/food_accuracy/compare_runs.py \
  docs/benchmarks/food_accuracy/results/image_text_ab/l0_image_only/summary.csv \
  docs/benchmarks/food_accuracy/results/image_text_ab/l1_meal_name/summary.csv
```

**Latest results (2026-07-22):** Best plate so far — `google/gemini-3.6-flash` L0 **32.3%** WMAPE / **50%** ±20%. Then gpt-4o-mini **34.5%**, Gemini 3.5 Flash-Lite **35.9%**, free Gemma/nofud ~**41–42%**. No `3.6-flash-lite` on OpenRouter (Lite = 3.5). See [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](../../docs/FOOD_ACCURACY_BENCHMARK_STATUS.md).

## Image baseline

Wide free-router image run on JFB (prefer **`nofud/free`** — no content-safety):

```bash
uv run python docs/benchmarks/food_accuracy/download_jfb.py --limit 50

uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/data/manifests/jfb.jsonl \
  --prompt compact \
  --provider openrouter \
  --model nofud/free \
  --sleep 8 \
  --retries 2 \
  --out docs/benchmarks/food_accuracy/results/baseline_image_nofud_free_compact
```

Inspect the pool:

```bash
uv run python docs/benchmarks/food_accuracy/list_nofud_free_pool.py --vision --show-excluded --smoke
```

Stock `openrouter/free` often routes to `nvidia/nemotron-3.5-content-safety:free` (`User Safety: safe` → parse fail). Prefer `nofud/free` always for free benches.
