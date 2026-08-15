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
| [NutritionVerse-Real](https://www.kaggle.com/datasets/nutritionverse/nutritionverse-real) | Phone multi-angle | 889 imgs | Scale-weighed → Canada Nutrient File | **CC BY-NC-SA 4.0** | Optional L0/L1/L2; manual Kaggle / `download_nutritionverse_real.py` |
| [ACETADA](https://skynet.ecn.purdue.edu/~coburn6/ACETADA/) | Free-living smartphone | 806 | Dietitian-verified (served macros) | **CC BY-NC 4.0** | Research-only L0/L1/L2 via `download_acetada.py` |
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

Scored on **calories, protein_g, carbs_g, fat_g**, plus 21 micronutrients where
ground truth exists (`schema.MICRO_FIELDS`). Micronutrient GT is currently
populated **only for FNDDS text** (`build_fndds_manifest.py` pulls it from
USDA `food_nutrient.csv`); JFB and Nutrition5k images have no micronutrient
data in their source datasets, so micro metrics on those manifests report
`n_micro=0` per nutrient rather than a score. See
[manifest/schema.md § Micronutrient ground-truth fields](benchmarks/food_accuracy/manifest/schema.md#micronutrient-ground-truth-fields-optional-in-extra)
for field names/units and dataset-specific caveats (`added_sugar_g`/`trans_fat_g`
have no GT coverage in the current FNDDS release; `omega_3_g` GT is a
partial composite, not a true total).

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
| `micro_wmape` | Weighted MAPE across all micronutrients with GT present (separate sum from macro `wmape`: mixing mg/mcg/g-scale nutrients into one blended number would be meaningless) |
| `mae_micro_<field>` / `mape_micro_<field>` / `n_micro_<field>` | Per-nutrient MAE/MAPE and sample count (only samples with non-null GT *and* non-null prediction count) |
| `presence_rate_<field>` | Fraction of parsed samples where the model returned a non-null value for that nutrient, independent of GT availability |

## Prompt variants

| Name | Matches | Notes |
|------|---------|-------|
| `production_text` | Chompass `analyzeText` | Full JSON schema + unit_options |
| `production_image` | Chompass `analyzeFood` | Vision + same schema |
| `compact` | Research ablation | Macros + serving_size_grams only |
| `compact_portion` | Research only | compact + portion/quantity rules; Flash-Lite JFB did not beat compact (see [STATUS § Failure modes](FOOD_ACCURACY_BENCHMARK_STATUS.md#failure-modes--portion-reasoning)) |
| `fewshot_units` | On-device smoke `fewshot_units` | Full schema + pizza/soda/oatmeal unit examples |

Image prompts append optional user context when `text` is set on an image sample (matches app `analyzeFood(description=…)`). `meal_name` is metadata only.

### Image + description eval (L0 / L1 / L2)

Paired manifests share the same IDs; only user `text` differs. Shared helper:
[`image_text_variants.py`](benchmarks/food_accuracy/image_text_variants.py).

| Level | User `text` | JFB | Nutrition5k | NutritionVerse-Real | ACETADA |
|-------|-------------|-----|-------------|---------------------|---------|
| L0 | absent (image only) | `jfb.jsonl` | `n5k.jsonl` | `nvreal.jsonl` | `acetada.jsonl` |
| L1 | meal title / coarse label | meal name | *(none: dish IDs only)* | synthesized from food types | `meal_type` (Breakfast/Lunch/Dinner) |
| L2 | ingredient / item names, no qty | ingredient names | ingredient names | food-type names | dietitian food-item names |

```bash
uv run python docs/benchmarks/food_accuracy/download_jfb.py --limit 50
uv run python docs/benchmarks/food_accuracy/download_nutrition5k.py --limit 15   # also writes n5k_image_text_l2.jsonl
uv run python docs/benchmarks/food_accuracy/download_acetada.py --limit 50      # CC BY-NC; selective zip extract
# NutritionVerse-Real needs a local Kaggle extract (CC BY-NC-SA):
uv run python docs/benchmarks/food_accuracy/download_nutritionverse_real.py \
  --data-dir docs/benchmarks/food_accuracy/data/nutritionverse_real --limit 50

uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model google/gemma-4-26b-a4b-it:free \
  --prompt compact --sleep 15 --retries 3 \
  --manifest docs/benchmarks/food_accuracy/data/manifests/jfb_image_text_l1.jsonl \
  --out docs/benchmarks/food_accuracy/results/image_text_ab/l1_meal_name
```

Compare L0 vs L1 vs L2 with `compare_runs.py`. **ACETADA / NutritionVerse are non-commercial**: research numbers only, not product accuracy claims.

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
| **New process / `run_eval.py` start** | Yes: pools are built from a **fresh** OpenRouter models list. Newly published free models appear; cancelled / non-free / removed models drop out. |
| **Mid-run (same process)** | No refresh: the pool is cached for the lifetime of that provider instance. A multi-hour eval will not pick up catalog changes until you restart (or call `refresh_pools()`). |
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

# Stock OpenRouter free router: not for accuracy benches
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

### Post-hoc analysis (no API calls)

`posthoc_calibration.py` re-scores predictions already stored in
`results/*/samples.jsonl` under transformations that need no new model output:
per-model bias calibration (leave-one-out cross-validated), self-consistency over
repeated runs, cross-model median ensembling, and whether `serving_size_grams` or
cross-model disagreement predict error. Free to run; results in
[STATUS § Post-hoc calibration & ensembling](FOOD_ACCURACY_BENCHMARK_STATUS.md#post-hoc-calibration--ensembling-2026-07-29).

```bash
uv run python docs/benchmarks/food_accuracy/posthoc_calibration.py
uv run python docs/benchmarks/food_accuracy/posthoc_calibration.py --sections A C G
```

See also [`docs/benchmarks/food_accuracy/README.md`](benchmarks/food_accuracy/README.md).

## Download details

### JFB

`download_jfb.py` pulls the public S3 tarball (`january-food-image-dataset-public/food-scan-benchmark-dataset.tar.gz`), extracts to `data/jfb/`, and writes `data/manifests/jfb.jsonl` plus L1/L2 image+text variants.

### Nutrition5k

`download_nutrition5k.py` always fetches dish metadata CSVs from the GitHub repo. With `--limit N` and `gsutil` on PATH, it downloads overhead RGB frames for test-split dishes into `data/nutrition5k/imagery/realsense_overhead/` and writes `data/manifests/n5k.jsonl` plus **`n5k_image_text_l2.jsonl`** (ingredient names only: no natural meal titles, so no L1).

Full Nutrition5k archive is ~181 GB; do not commit images.

Two more per-dish modalities exist on the same GCS bucket and are opt-in via flags
(neither is fetched by default, to keep the cursory subset small): `--with-depth`
fetches the aligned 16-bit RealSense `depth_raw.png` (mm) from the same overhead rig
as the RGB still; `--with-video[=CAMERA]` fetches one fixed turntable camera's raw
`camera_{A..D}.h264` clip from `imagery/side_angles/{dish_id}/` (decode with
`ffmpeg -i camera_A.h264 frame_%03d.jpeg`). Both attach a repo-relative path into the
sample's `extra` dict (`depth_path`, `video_path`: see
`docs/benchmarks/food_accuracy/manifest/schema.md`). No camera calibration/intrinsics
file is published for this dataset: `depth_volume_eval.py` uses a nominal RealSense
D435 640x480 calibration as a documented approximation, not per-dish calibration.

```bash
uv run --with pillow python docs/benchmarks/food_accuracy/download_nutrition5k.py \
  --limit 15 --with-depth --with-video A \
  --out docs/benchmarks/food_accuracy/data/manifests/n5k_depth.jsonl
```

### NutritionVerse-Real

Manual Kaggle download (or `--try-kaggle` with API creds). Unzip under
`data/nutritionverse_real/`, then:

```bash
uv run python docs/benchmarks/food_accuracy/download_nutritionverse_real.py \
  --data-dir docs/benchmarks/food_accuracy/data/nutritionverse_real --limit 50
```

Writes `nvreal.jsonl` + L1/L2. **CC BY-NC-SA 4.0**: research only. Default keeps one
camera angle per dish (`--all-angles` to include every view).

### ACETADA

```bash
uv run python docs/benchmarks/food_accuracy/download_acetada.py --limit 50
```

Pulls the HF CSV via HTTP range from the public ~4.9 GB ZIP (no full archive
required), then selectively extracts before-meal JPEGs for the limited split.
Macros are **served (before-meal)** amounts rescaled from dietitian consumed
labels. L1 = `meal_type`; L2 = food-item names. **CC BY-NC 4.0**: research only.

### FNDDS text (expand seed set)

```bash
uv run python docs/benchmarks/food_accuracy/build_fndds_manifest.py \
  --out docs/benchmarks/food_accuracy/manifest/fndds_full.jsonl \
  --limit 200
```

Requires downloading FNDDS CSV from USDA (script uses the public zip URL).

## License notes

- **Nutrition5k, JFB:** CC BY 4.0: OK for research and commercial adaptation with attribution.
- **ACETADA, NutritionVerse-Real, MM-Food:** Non-commercial: do not use for product accuracy claims.
- **Open Food Facts:** ODbL: share-alike if you redistribute derived DB.
- **USDA FNDDS:** Public domain / US government work.

## Out of scope (v1)

- On-device LiteRT scoring (phase 2: export winning image subset to debug fixtures)
- Nutrition-label OCR eval track
- Micronutrient scoring for **image** datasets (JFB / Nutrition5k): neither
  has micronutrient values in its source data; deriving approximate GT via
  ingredient-name matching to USDA/OFF is a distinct, larger follow-up, not
  attempted here. Text (FNDDS) micronutrient scoring is implemented.
- Bundling dataset images in the APK or git
