# Food accuracy benchmark

Python research harness for prompt and model comparison on food text/image entry.

Full documentation: [`docs/FOOD_ACCURACY_BENCHMARK.md`](../../docs/FOOD_ACCURACY_BENCHMARK.md)

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

Replace `--provider stub` with OpenRouter free routing:

```bash
uv run python benchmarks/food_accuracy/probe_openrouter_free.py --limit 3 --max-models 2
uv run python benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model openrouter/free \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl --limit 10
```

## Fixed eval split

| File | Items | Modality |
|------|-------|----------|
| `manifest/eval_text.jsonl` | 42 | Text (FNDDS seed + composites) |
| `data/manifests/jfb.jsonl` | 50 default | Image (after `download_jfb.py`) |
| `data/manifests/n5k.jsonl` | 20 default | Image/metadata (after `download_nutrition5k.py`) |
