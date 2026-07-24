# Food accuracy benchmark

Offline research harness for comparing **prompts** and **models** on food text and image entry. Measures macronutrient error (calories, protein, carbs, fat) against labeled datasets. Usable for Chompass prompt tuning and general food-AI research.

**Status snapshot (results + defaults):** [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md)

**Related:** production prompts in [`FoodAnalysisService.kt`](../android/app/src/main/java/app/chompass/services/ai/FoodAnalysisService.kt); on-device smoke tests in [`docs/ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md) (latency/parse only, no GT scoring).

## Quick start

```bash
# Text eval (no download; uses checked-in FNDDS seed manifest)
uv run --with httpx python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt production_text \
  --provider stub \
  --limit 5

# Real API (OpenAI-compatible: Ollama, OpenRouter, Gemini proxy, etc.)
export OPENAI_API_KEY=...
export OPENAI_BASE_URL=http://127.0.0.1:11434/v1   # Ollama example
uv run --with httpx python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt production_text \
  --model llama3.2-vision \
  --limit 10

# Download image datasets (cached under docs/benchmarks/food_accuracy/data/, gitignored)
uv run --with httpx --with pandas python docs/benchmarks/food_accuracy/download_jfb.py --limit 50
uv run python docs/benchmarks/food_accuracy/download_nutrition5k.py --metadata-only   # metadata only, no gsutil
uv run python docs/benchmarks/food_accuracy/download_nutrition5k.py --limit 20          # needs gsutil + GCS access

# Image eval after JFB download
uv run --with httpx python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/data/manifests/jfb.jsonl \
  --prompt production_image \
  --model gpt-4o-mini \
  --limit 20
```

Results land in `docs/benchmarks/food_accuracy/results/<run_id>/` (summary CSV + per-sample JSONL).

## Dataset catalog

| Dataset | Modality | Size | Ground truth | License | Use in v1 |
|---------|----------|------|--------------|---------|-----------|
| [Nutrition5k](https://github.com/google-research-datasets/Nutrition5k) | Plate RGB (+ depth/video) | ~5k dishes | Weighed ingredients → USDA macros | **CC BY 4.0** | Lab-grade image GT; use metadata + overhead RGB subset (~181 GB full) |
| [January Food Benchmark (JFB)](https://github.com/January-ai/food-scan-benchmarks) | Mobile meal photos | 1,000 | Human-validated meal + macros | **CC BY 4.0** (MIT code) | Primary real-world image eval |
| [NutritionVerse-Real](https://www.kaggle.com/datasets/nutritionverse/nutritionverse-real) | Phone multi-angle | 889 imgs | Scale-weighed → Canada Nutrient File | Kaggle / metadata CC0 | Optional; manual Kaggle download |
| [ACETADA](https://skynet.ecn.purdue.edu/~coburn6/ACETADA/) | Free-living smartphone | 806 | Dietitian-verified | **CC BY-NC 4.0** | Research only; no commercial claims |
| [USDA FNDDS](https://fdc.nal.usda.gov/download-datasets/) | Text | ~5.4k foods | Per-100g + portions | US gov public | Text GT via `build_fndds_manifest.py` or seed manifest |
| [Open Food Facts](https://world.openfoodfacts.org/) | Label/packaging photos | Millions | Crowd-sourced (variable) | **ODbL** | Label OCR track (future) |
| MM-Food-100K / Recipe1M+ / Food-101 | Images | Large | Class-only or weak nutrition | Mixed / NC | Recognition research; weak calorie GT |

**v1 default split:** `manifest/eval_text.jsonl` (40 FNDDS-derived text items) + JFB subset via `download_jfb.py --limit 50`.

## Manifest format

Each line is JSON (JSONL). Required fields:

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique sample id |
| `modality` | `"text"` \| `"image"` | Input type |
| `source` | string | e.g. `fndds_seed`, `jfb`, `nutrition5k` |
| `calories` | number | Ground-truth kcal |
| `protein_g` | number | Ground-truth protein (g) |
| `carbs_g` | number | Ground-truth carbs (g) |
| `fat_g` | number | Ground-truth fat (g) |
| `text` | string? | Food description (text modality, or user note on image+text entry) |
| `image_path` | string? | Path to image relative to repo root or absolute |
| `mass_g` | number? | Portion mass when known |
| `meal_name` | string? | Reference name (image sets) |

See [`docs/benchmarks/food_accuracy/manifest/schema.md`](benchmarks/food_accuracy/manifest/schema.md).

## Metrics

Scored on **calories, protein_g, carbs_g, fat_g** only (micronutrients omitted in v1 — sparse GT).

| Metric | Description |
|--------|-------------|
| `parse_ok` | Model output parsed to valid JSON with required macro keys |
| `mae_*` | Mean absolute error per nutrient |
| `mape_*` | Mean absolute percentage error per nutrient |
| `wmape` | Weighted MAPE across all four macros: `sum(|pred-gt|) / sum(|gt|)` (JFB-style) |
| `within_20pct_calories` | Fraction of samples with calorie error ≤ 20% |
| `latency_ms` | Wall time per sample (when provider reports it) |
| `prompt_tokens` / `completion_tokens` / `total_tokens` | From provider `usage` (OpenRouter always returns these) |
| `cached_tokens` / `cache_write_tokens` | From `usage.prompt_tokens_details` when the upstream reports cache hits/writes |
| `reasoning_tokens` | From `usage.completion_tokens_details` when present |
| `cost` | OpenRouter credit cost for the request (when present) |
| `sum_*` / `mean_*` / `cache_hit_rate` | Aggregated into `summary.csv` / `summary.json` (`cache_hit_rate` = sum cached / sum prompt) |

## Prompt variants

| Name | Matches | Notes |
|------|---------|-------|
| `production_text` | Chompass `analyzeText` | Full JSON schema + unit_options |
| `production_image` | Chompass `analyzeFood` | Vision + same schema |
| `compact` | Research ablation | Macros + serving_size_grams only |
| `compact_portion` | Research only | compact + portion/quantity rules; Flash-Lite JFB did not beat compact (see [STATUS § Failure modes](FOOD_ACCURACY_BENCHMARK_STATUS.md#failure-modes--portion-reasoning)) |
| `fewshot_units` | On-device smoke `fewshot_units` | Full schema + pizza/soda/oatmeal unit examples |

Image prompts append optional user context when `text` is set on an image sample (matches app `analyzeFood(description=…)`). `meal_name` is metadata only.

### Image + description eval (JFB)

`download_jfb.py` writes three paired manifests (same 50 IDs):

| Level | File | User `text` |
|-------|------|-------------|
| L0 | `data/manifests/jfb.jsonl` | absent (image only) |
| L1 | `data/manifests/jfb_image_text_l1.jsonl` | meal title |
| L2 | `data/manifests/jfb_image_text_l2.jsonl` | ingredient names only |

```bash
uv run python docs/benchmarks/food_accuracy/download_jfb.py --limit 50

uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model google/gemma-4-26b-a4b-it:free \
  --prompt compact --sleep 15 --retries 3 \
  --manifest docs/benchmarks/food_accuracy/data/manifests/jfb_image_text_l1.jsonl \
  --out docs/benchmarks/food_accuracy/results/image_text_ab/l1_meal_name
```

Compare L0 vs L1 vs L2 with `compare_runs.py`.

Loads `OPENROUTER_TOKEN` from repo-root [`.env.local`](../.env.local) automatically.

### Prefer `nofud/free` (default)

**Always use `--model nofud/free` for free-tier benches** (this is also the default when `--provider openrouter` and no `--model` is set). Do **not** use stock `openrouter/free` for accuracy work: it frequently routes to `nvidia/nemotron-3.5-content-safety:free`, which returns `User Safety: safe` instead of food JSON (image baseline: 19/50 samples lost that way; fill with `nofud/free` recovered parse_ok to 98%).

`nofud/free` is a **client-side** router in this harness:

- Fetches the live OpenRouter `/api/v1/models` catalog
- Keeps price-zero / `:free` chat models that accept text (and image, when the sample has a photo)
- **Excludes** content-safety / moderation / embeddings / TTS / image-gen style IDs via [`openrouter_models.py`](benchmarks/food_accuracy/openrouter_models.py)
- Randomly picks from the pool; on 429/502/ResourceExhausted fails over to another pool member (up to 3 attempts)

### Does the pool stay up to date?

| When | Behavior |
|------|----------|
| **New process / `run_eval.py` start** | Yes — pools are built from a **fresh** OpenRouter models list. Newly published free models appear; cancelled / non-free / removed models drop out. |
| **Mid-run (same process)** | No refresh — the pool is cached for the lifetime of that provider instance. A multi-hour eval will not pick up catalog changes until you restart (or call `refresh_pools()`). |
| **Inspect anytime** | `list_nofud_free_pool.py` always hits the live API. |

So: **yes, it finds newly added free models and drops cancelled ones across runs**; it does **not** continuously re-poll during a single long eval.

```bash
# Inspect the pool (and what we exclude)
uv run python docs/benchmarks/food_accuracy/list_nofud_free_pool.py
uv run python docs/benchmarks/food_accuracy/list_nofud_free_pool.py --vision --show-excluded --smoke

# Eval with Chompass free router (preferred / default)
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter \
  --model nofud/free \
  --prompt compact \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --limit 10

# Stock OpenRouter free router — not for accuracy benches
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model openrouter/free ...
```

The app still lists `openrouter/free` in [`AIProvider.kt`](../android/app/src/main/java/app/chompass/models/AIProvider.kt); this harness router is benchmark-side only for now.

Vision models: image samples are sent as base64 JPEG in the chat completion request.

## Prompt / model comparison workflow

1. Pick a fixed manifest (`eval_text.jsonl` or downloaded `jfb.jsonl`).
2. Run the same manifest with different `--prompt` and `--model` values.
3. Compare `summary.csv` columns (`wmape`, `parse_ok_rate`, `within_20pct_calories`).
4. When a prompt wins offline, port wording to [`FoodAnalysisService.kt`](../android/app/src/main/java/app/chompass/services/ai/FoodAnalysisService.kt) and re-check on-device smoke tests.

### Prompt A/B example

```bash
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt production_text --provider stub \
  --out docs/benchmarks/food_accuracy/results/ab/production_text

uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt fewshot_units --provider stub \
  --out docs/benchmarks/food_accuracy/results/ab/fewshot_units

uv run python docs/benchmarks/food_accuracy/compare_runs.py \
  docs/benchmarks/food_accuracy/results/ab/production_text/summary.csv \
  docs/benchmarks/food_accuracy/results/ab/fewshot_units/summary.csv
```

See also [`docs/benchmarks/food_accuracy/README.md`](benchmarks/food_accuracy/README.md).

## Download details

### JFB

`download_jfb.py` pulls the public S3 tarball (`january-food-image-dataset-public/food-scan-benchmark-dataset.tar.gz`), extracts to `data/jfb/`, and writes `data/manifests/jfb.jsonl`.

### Nutrition5k

`download_nutrition5k.py` always fetches dish metadata CSVs from the GitHub repo. With `--limit N` and `gsutil` on PATH, it downloads overhead RGB frames for test-split dishes into `data/nutrition5k/imagery/realsense_overhead/` and writes `data/manifests/n5k.jsonl`.

Full Nutrition5k archive is ~181 GB; do not commit images.

### FNDDS text (expand seed set)

```bash
uv run python docs/benchmarks/food_accuracy/build_fndds_manifest.py \
  --out docs/benchmarks/food_accuracy/manifest/fndds_full.jsonl \
  --limit 200
```

Requires downloading FNDDS CSV from USDA (script uses the public zip URL).

## License notes

- **Nutrition5k, JFB:** CC BY 4.0 — OK for research and commercial adaptation with attribution.
- **ACETADA, MM-Food:** Non-commercial — do not use for product accuracy claims.
- **Open Food Facts:** ODbL — share-alike if you redistribute derived DB.
- **USDA FNDDS:** Public domain / US government work.

## Out of scope (v1)

- On-device LiteRT scoring (phase 2: export winning image subset to debug fixtures)
- Nutrition-label OCR eval track
- Micronutrient scoring
- Bundling dataset images in the APK or git
