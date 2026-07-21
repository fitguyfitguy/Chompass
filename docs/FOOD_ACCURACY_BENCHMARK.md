# Food accuracy benchmark

Offline research harness for comparing **prompts** and **models** on food text and image entry. Measures macronutrient error (calories, protein, carbs, fat) against labeled datasets. Usable for NoFUD prompt tuning and general food-AI research.

**Related:** production prompts in [`FoodAnalysisService.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/ai/FoodAnalysisService.kt); on-device smoke tests in [`docs/ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md) (latency/parse only, no GT scoring).

## Quick start

```bash
# Text eval (no download; uses checked-in FNDDS seed manifest)
uv run --with httpx python benchmarks/food_accuracy/run_eval.py \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt production_text \
  --provider stub \
  --limit 5

# Real API (OpenAI-compatible: Ollama, OpenRouter, Gemini proxy, etc.)
export OPENAI_API_KEY=...
export OPENAI_BASE_URL=http://127.0.0.1:11434/v1   # Ollama example
uv run --with httpx python benchmarks/food_accuracy/run_eval.py \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt production_text \
  --model llama3.2-vision \
  --limit 10

# Download image datasets (cached under benchmarks/food_accuracy/data/, gitignored)
uv run --with httpx --with pandas python benchmarks/food_accuracy/download_jfb.py --limit 50
uv run python benchmarks/food_accuracy/download_nutrition5k.py --metadata-only   # metadata only, no gsutil
uv run python benchmarks/food_accuracy/download_nutrition5k.py --limit 20          # needs gsutil + GCS access

# Image eval after JFB download
uv run --with httpx python benchmarks/food_accuracy/run_eval.py \
  --manifest benchmarks/food_accuracy/data/manifests/jfb.jsonl \
  --prompt production_image \
  --model gpt-4o-mini \
  --limit 20
```

Results land in `benchmarks/food_accuracy/results/<run_id>/` (summary CSV + per-sample JSONL).

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
| `text` | string? | Food description (text modality) |
| `image_path` | string? | Path to image relative to repo root or absolute |
| `mass_g` | number? | Portion mass when known |
| `meal_name` | string? | Reference name (image sets) |

See [`benchmarks/food_accuracy/manifest/schema.md`](../benchmarks/food_accuracy/manifest/schema.md).

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

## Prompt variants

| Name | Matches | Notes |
|------|---------|-------|
| `production_text` | NoFUD `analyzeText` | Full JSON schema + unit_options |
| `production_image` | NoFUD `analyzeFood` | Vision + same schema |
| `compact` | Research ablation | Macros + serving_size_grams only |
| `fewshot_units` | On-device smoke `fewshot_units` | Full schema + pizza/soda/oatmeal unit examples |

## OpenRouter / NoFUD free router

Loads `OPENROUTER_TOKEN` from repo-root [`.env.local`](../.env.local) automatically.

**Prefer `nofud/free`** for benchmarks: picks randomly among live OpenRouter `:free` chat models and **excludes content-safety / moderation / non-chat** entries (the stock `openrouter/free` router often routes to `nvidia/nemotron-3.5-content-safety:free`, which returns `User Safety: safe` instead of JSON).

```bash
# Inspect the pool (and what we exclude)
uv run python benchmarks/food_accuracy/list_nofud_free_pool.py
uv run python benchmarks/food_accuracy/list_nofud_free_pool.py --vision --show-excluded --smoke

# Eval with NoFUD free router (default openrouter model)
uv run python benchmarks/food_accuracy/run_eval.py \
  --provider openrouter \
  --model nofud/free \
  --prompt compact \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --limit 10

# Stock OpenRouter free router (includes content-safety — not recommended)
uv run python benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model openrouter/free ...
```

On 429/502 the NoFUD router fails over to another free model in the pool (up to 3 attempts).

The app still lists `openrouter/free` in [`AIProvider.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/models/AIProvider.kt); this harness router is benchmark-side only for now.

Vision models: image samples are sent as base64 JPEG in the chat completion request.

## Prompt / model comparison workflow

1. Pick a fixed manifest (`eval_text.jsonl` or downloaded `jfb.jsonl`).
2. Run the same manifest with different `--prompt` and `--model` values.
3. Compare `summary.csv` columns (`wmape`, `parse_ok_rate`, `within_20pct_calories`).
4. When a prompt wins offline, port wording to [`FoodAnalysisService.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/ai/FoodAnalysisService.kt) and re-check on-device smoke tests.

### Prompt A/B example

```bash
uv run python benchmarks/food_accuracy/run_eval.py \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt production_text --provider stub \
  --out benchmarks/food_accuracy/results/ab/production_text

uv run python benchmarks/food_accuracy/run_eval.py \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt fewshot_units --provider stub \
  --out benchmarks/food_accuracy/results/ab/fewshot_units

uv run python benchmarks/food_accuracy/compare_runs.py \
  benchmarks/food_accuracy/results/ab/production_text/summary.csv \
  benchmarks/food_accuracy/results/ab/fewshot_units/summary.csv
```

See also [`benchmarks/food_accuracy/README.md`](../benchmarks/food_accuracy/README.md).

## Download details

### JFB

`download_jfb.py` pulls the public S3 tarball (`january-food-image-dataset-public/food-scan-benchmark-dataset.tar.gz`), extracts to `data/jfb/`, and writes `data/manifests/jfb.jsonl`.

### Nutrition5k

`download_nutrition5k.py` always fetches dish metadata CSVs from the GitHub repo. With `--limit N` and `gsutil` on PATH, it downloads overhead RGB frames for test-split dishes into `data/nutrition5k/imagery/realsense_overhead/` and writes `data/manifests/n5k.jsonl`.

Full Nutrition5k archive is ~181 GB; do not commit images.

### FNDDS text (expand seed set)

```bash
uv run python benchmarks/food_accuracy/build_fndds_manifest.py \
  --out benchmarks/food_accuracy/manifest/fndds_full.jsonl \
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
