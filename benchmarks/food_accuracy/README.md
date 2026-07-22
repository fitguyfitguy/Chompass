# Food accuracy benchmark

Python research harness for prompt and model comparison on food text/image entry.

Full documentation: [`docs/FOOD_ACCURACY_BENCHMARK.md`](../../docs/FOOD_ACCURACY_BENCHMARK.md)

**Current results & defaults:** [`docs/FOOD_ACCURACY_BENCHMARK_STATUS.md`](../../docs/FOOD_ACCURACY_BENCHMARK_STATUS.md)

**Free-tier default: `--provider openrouter --model nofud/free`.** Do not use stock `openrouter/free` for accuracy benches (it routes to content-safety models). `nofud/free` rebuilds its pool from the live OpenRouter catalog on each process start (new free models appear; cancelled ones drop); the pool is cached for that run only.

## Layout

```
benchmarks/food_accuracy/
  manifest/           # Checked-in JSONL eval splits
  data/               # Downloaded datasets (gitignored)
  results/            # Eval outputs (gitignored)
  run_eval.py         # Main CLI
  compare_runs.py     # Diff two summary.csv files
  download_jfb.py
  download_nutrition5k.py
  build_fndds_manifest.py
```

## Prompt A/B (text)

```bash
# Baseline: production text prompt
uv run python benchmarks/food_accuracy/run_eval.py \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt production_text \
  --provider stub \
  --out benchmarks/food_accuracy/results/ab/production_text

# Candidate: few-shot units
uv run python benchmarks/food_accuracy/run_eval.py \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt fewshot_units \
  --provider stub \
  --out benchmarks/food_accuracy/results/ab/fewshot_units

uv run python benchmarks/food_accuracy/compare_runs.py \
  benchmarks/food_accuracy/results/ab/production_text/summary.csv \
  benchmarks/food_accuracy/results/ab/fewshot_units/summary.csv
```

### Resume failed/missing only

```bash
# Preview which IDs would be re-run
uv run python benchmarks/food_accuracy/run_eval.py \
  --prompt compact \
  --resume benchmarks/food_accuracy/results/prompt_ab_gemma/compact \
  --dry-run

# Fill 429/parse failures only (keeps successful samples)
uv run python benchmarks/food_accuracy/run_eval.py \
  --provider openrouter \
  --model google/gemma-4-26b-a4b-it:free \
  --prompt compact \
  --resume benchmarks/food_accuracy/results/prompt_ab_gemma/compact \
  --sleep 20 \
  --retries 3
```

Replace `--provider stub` with **NoFUD free routing** (preferred):

```bash
uv run python benchmarks/food_accuracy/list_nofud_free_pool.py --vision --show-excluded
uv run python benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model nofud/free \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl --limit 10
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
uv run python benchmarks/food_accuracy/download_jfb.py --limit 50

for level in l0_image_only:jfb.jsonl l1_meal_name:jfb_image_text_l1.jsonl l2_ingredient_names:jfb_image_text_l2.jsonl; do
  name="${level%%:*}"
  manifest="${level##*:}"
  uv run python benchmarks/food_accuracy/run_eval.py \
    --provider openrouter --model google/gemma-4-26b-a4b-it:free \
    --prompt compact --sleep 15 --retries 3 \
    --manifest "benchmarks/food_accuracy/data/manifests/$manifest" \
    --out "benchmarks/food_accuracy/results/image_text_ab/$name"
done

uv run python benchmarks/food_accuracy/compare_runs.py \
  benchmarks/food_accuracy/results/image_text_ab/l0_image_only/summary.csv \
  benchmarks/food_accuracy/results/image_text_ab/l1_meal_name/summary.csv
```

**Latest results (2026-07-22):** Gemma L0/L1/L2 — L0 **41.8%** WMAPE best among free image+text; L1 `production_image` worse (**47.3%**). Paid `gpt-4o-mini` L0: **34.5%** WMAPE / **50%** within ±20%. Cold `nofud/free`: **41.1%**. See [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](../../docs/FOOD_ACCURACY_BENCHMARK_STATUS.md).

## Image baseline

Wide free-router image run on JFB (prefer **`nofud/free`** — no content-safety):

```bash
uv run python benchmarks/food_accuracy/download_jfb.py --limit 50

uv run python benchmarks/food_accuracy/run_eval.py \
  --manifest benchmarks/food_accuracy/data/manifests/jfb.jsonl \
  --prompt compact \
  --provider openrouter \
  --model nofud/free \
  --sleep 8 \
  --retries 2 \
  --out benchmarks/food_accuracy/results/baseline_image_nofud_free_compact
```

Inspect the pool:

```bash
uv run python benchmarks/food_accuracy/list_nofud_free_pool.py --vision --show-excluded --smoke
```

Stock `openrouter/free` often routes to `nvidia/nemotron-3.5-content-safety:free` (`User Safety: safe` → parse fail). Prefer `nofud/free` always for free benches.
