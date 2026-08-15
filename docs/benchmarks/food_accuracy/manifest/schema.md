# Manifest schema (JSONL)

One JSON object per line. All paths in `image_path` may be relative to the NoFUD repo root.

## Required

```json
{
  "id": "fndds-001",
  "modality": "text",
  "source": "fndds_seed",
  "calories": 248,
  "protein_g": 46.5,
  "carbs_g": 0.0,
  "fat_g": 5.4
}
```

## Text sample

```json
{
  "id": "fndds-001",
  "modality": "text",
  "source": "fndds_seed",
  "text": "Chicken breast, roasted, 150 g",
  "calories": 248,
  "protein_g": 46.5,
  "carbs_g": 0.0,
  "fat_g": 5.4,
  "mass_g": 150,
  "notes": "USDA FNDDS 2021-2023, portion scaled from per-100g"
}
```

## Image sample

```json
{
  "id": "jfb-00042",
  "modality": "image",
  "source": "jfb",
  "image_path": "docs/benchmarks/food_accuracy/data/jfb/food-scan-benchmark-dataset/fsb_images/042.jpg",
  "meal_name": "Grilled chicken salad",
  "calories": 420,
  "protein_g": 35.0,
  "carbs_g": 18.0,
  "fat_g": 22.0
}
```

## Image + user description (same modality, `text` set)

Prompt builders use `text` as the optional user note (matches app `analyzeFood(description=…)`). `meal_name` is metadata only and is not injected unless copied into `text`.

| Level | Manifest | `text` field |
|-------|----------|--------------|
| L0 | `jfb.jsonl` / `n5k.jsonl` / `nvreal.jsonl` / `acetada.jsonl` | absent: image only |
| L1 | `*_image_text_l1.jsonl` | meal title / coarse label (`meal_type` on ACETADA; synthesized food types on NutritionVerse; N5k: short identity from top ingredients; dish IDs are not human titles) |
| L2 | `*_image_text_l2.jsonl` | comma-joined ingredient / food-item names (no qty/macros) |
| Lq | `*_image_text_lq.jsonl` | **vague quantity** diary note (bucket language and/or coarsened amounts); **no** `\d+ g` in `text`; built by `build_image_text_lq.py` |

### Text-only Lq / L1 (typed diary entry)

Same `text` strings as the image+note manifests, but `modality: "text"` and no
`image_path`: scores the common typed vague-quantity path (`analyzeText`).
Built by [`build_text_lq.py`](../build_text_lq.py) from `*_image_text_{lq,l1}.jsonl`.

| Level | Manifest | Role |
|-------|----------|------|
| Text Lq | `*_text_lq.jsonl` | Hard vague-quantity typed entry (primary bake-off) |
| Text L1 | `*_text_l1.jsonl` | Title/identity-only control on the same meal IDs |

```json
{
  "id": "jfb-fsb_00000",
  "modality": "text",
  "source": "jfb",
  "text": "Breakfast Platter — a little scrambled eggs, a slice of bacon, some roasted potatoes, some fruit salad",
  "meal_name": "Breakfast Platter",
  "calories": 408.5,
  "protein_g": 30.1,
  "carbs_g": 33.5,
  "fat_g": 17.3,
  "note_level": "lq",
  "notes": "Lq vague quantity note; no grams in text; text-only Lq vague quantity; no grams in text; derived from image Lq"
}
```

### Photo-adjacent entry matrix (optimization lane)

Four conditions on **shared meal IDs** (paired WMAPE / ±20% kcal):

| Entry method | Harness condition | How the signal is supplied |
|--------------|-------------------|----------------------------|
| Image only | **L0** | `text` absent |
| Vague meal quantity labels | **Bucket chips** | `compact_clarify_portion_bucket` on `*_clarify.jsonl` (`small` / `regular` / `large` / `restaurant-size`); requires `mass_g` → **N5k covered; JFB has no total mass** |
| Image + meal text | **L1** | meal title / coarse identity as user `text` |
| Image + vague meal quantity text | **Lq** | user `description` note with vague qty (not chip injection) |

Hard-tail IDs for JFB (never ±20% across five L0 models + documented hard plates): [`jfb_hard_ids.txt`](jfb_hard_ids.txt).

```json
{
  "id": "jfb-fsb_00000",
  "modality": "image",
  "source": "jfb",
  "text": "Breakfast Platter",
  "image_path": "docs/benchmarks/food_accuracy/data/jfb/food-scan-benchmark-dataset/fsb_images/fsb_00000.jpg",
  "meal_name": "Breakfast Platter",
  "calories": 408.5,
  "protein_g": 30.1,
  "carbs_g": 33.5,
  "fat_g": 17.3
}
```

### Lq sample

```json
{
  "id": "jfb-fsb_00000",
  "modality": "image",
  "source": "jfb",
  "text": "Breakfast Platter — some scrambled eggs, a couple bacon slices, some roasted potatoes",
  "image_path": "docs/benchmarks/food_accuracy/data/jfb/food-scan-benchmark-dataset/fsb_images/fsb_00000.jpg",
  "meal_name": "Breakfast Platter",
  "calories": 408.5,
  "protein_g": 30.1,
  "carbs_g": 33.5,
  "fat_g": 17.3,
  "note_level": "lq",
  "notes": "Lq vague quantity note; no grams in text"
}
```

## Eval split files

- `eval_text.jsonl`: gram-rich FNDDS text (identity / form **regression** only; not the grounded ship gate).
- `eval_grounded_realistic_text.jsonl`: **primary grounded text readiness gate**: vague titles, household units, multi-item meals, branded OFF names. No `\d+ g` in prompts; `mass_g` / macros kept for scoring. Built by `build_realistic_text_manifest.py`. Each row has `"slice": "vague"|"household"|"multi"|"branded"`.
- `off_fixtures.json`: offline Open Food Facts search hits keyed by query for branded `search_off` in the grounded eval harness (`--off-fixtures`).
- `grounded_bad_cases.jsonl`: failure-mode suite (form mismatch, portion units, orchestration).
- `data/manifests/jfb.jsonl`: L0 image-only JFB split (generated by `download_jfb.py`).
- `data/manifests/jfb_image_text_l1.jsonl`: L1 meal-name descriptions (generated by `download_jfb.py`).
- `data/manifests/jfb_image_text_l2.jsonl`: L2 ingredient-name descriptions (generated by `download_jfb.py`).
- `data/manifests/jfb_image_text_lq.jsonl`: Lq vague quantity notes (`build_image_text_lq.py`).
- `data/manifests/jfb_text_lq.jsonl` / `jfb_text_l1.jsonl`: text-only Lq/L1 (`build_text_lq.py`).
- `data/manifests/n5k.jsonl`: generated by `download_nutrition5k.py` (includes name-only `ingredients` + `ingredients_weighed`).
- `data/manifests/n5k_image_text_l1.jsonl`: N5k coarse identity from top ingredients (`build_image_text_lq.py` / download with L1).
- `data/manifests/n5k_image_text_l2.jsonl`: L2 ingredient names (same script).
- `data/manifests/n5k_image_text_lq.jsonl`: Lq vague quantity notes (`build_image_text_lq.py`).
- `data/manifests/n5k_text_lq.jsonl` / `n5k_text_l1.jsonl`: text-only Lq/L1 (`build_text_lq.py`).
- `manifest/jfb_hard_ids.txt`: hard-plate ID list for per-condition hard-tail reporting.
- `data/manifests/n5k_depth.jsonl`: same dish subset as `n5k.jsonl`, plus `extra.depth_path` /
  `extra.video_path` (generated by `download_nutrition5k.py --with-depth --with-video`; see
  "Nutrition5k depth/video fields" below).
- `data/manifests/nvreal.jsonl` (+ `_image_text_l1/l2`): NutritionVerse-Real via `download_nutritionverse_real.py` (CC BY-NC-SA; local Kaggle extract).
- `data/manifests/acetada.jsonl` (+ `_image_text_l1/l2`): ACETADA via `download_acetada.py` (CC BY-NC; served macros).

### Realistic text sample

```json
{
  "id": "real-vague-001",
  "modality": "text",
  "source": "grounded_realistic",
  "text": "Chicken breast, roasted",
  "calories": 248,
  "protein_g": 46.5,
  "carbs_g": 0.0,
  "fat_g": 5.4,
  "mass_g": 150,
  "slice": "vague",
  "derived_from": "fndds-001",
  "notes": "vague title from fndds-001; mass held out of prompt"
}
```

## Micronutrient ground-truth fields (optional, in `extra`)

Like the Nutrition5k depth/video fields below, micronutrient GT lives in the
free-form `extra` dict, not the core `Sample` dataclass; see
`schema.MICRO_FIELDS` for the authoritative field list (GT key -> model
prediction JSON key) and `Sample.micro_ground_truth()` for the accessor.
Field names carry an explicit unit suffix matching `protein_g`/`carbs_g`
(grams: `sugar_g`, `added_sugar_g`, `fiber_g`, `saturated_fat_g`,
`monounsaturated_fat_g`, `polyunsaturated_fat_g`, `trans_fat_g`, `omega_3_g`;
milligrams: `cholesterol_mg`, `sodium_mg`, `potassium_mg`, `calcium_mg`,
`iron_mg`, `magnesium_mg`, `zinc_mg`, `vitamin_c_mg`, `vitamin_e_mg`;
micrograms: `vitamin_a_mcg`, `vitamin_d_mcg`, `vitamin_b12_mcg`,
`vitamin_k_mcg`, `folate_mcg`).

**Populated only by `build_fndds_manifest.py` (USDA FNDDS text).** JFB and
Nutrition5k have no micronutrient values anywhere in their source CSVs; image
samples will simply have no keys present (`micro_ground_truth()` returns all
`None`), so micro scoring on those datasets reports `n_micro=0` per nutrient,
not zero error. A key is present in `extra` only when the source USDA
`food_nutrient.csv` row for that food exists; absence means "not measured
for this food," not zero; the builder never zero-fills a missing micro.

Caveats specific to the FNDDS builder:
- `added_sugar_g` and `trans_fat_g` are defined in `MICRO_FIELDS` but the
  2024-10-31 FNDDS survey-food nutrient set carries **zero** rows for either
  nutrient_nbr (539, 605); every sample's GT for these two is `None`. Kept in
  the mapping in case a future FNDDS release populates them.
- `omega_3_g` has no single USDA nutrient_nbr; it's summed from ALA + EPA +
  DHA + DPA (nutrient_nbr 851/629/621/631) when present. In the current
  dataset ALA (851) has zero coverage, so `omega_3_g` GT is a **partial,
  under-counting composite**, not a true omega-3 total; treat it as a lower
  bound only.
- `folate_mcg` GT is USDA "Folate, total" (nutrient_nbr 417), not "Folate,
  DFE" (435): chosen to match the plain "folate" field name the model is
  prompted for, not the dietary-folate-equivalent weighting FDC also
  publishes.

Only prompts built on `FULL_JSON_SCHEMA` (`lean_full`, `lean_units`,
`lean_units2`: shipped default, `fewshot_units`, `production_text`,
`production_image`, `legacy_production_image`) ask the model for
micronutrients at all; `compact*`/clarify prompts use `COMPACT_JSON_SCHEMA`
and will always show 0% presence rate.

## Nutrition5k depth/video fields (optional, in `extra`)

`download_nutrition5k.py --with-depth --with-video[=CAMERA]` attaches two additional
fields per sample, alongside the standard `image_path` overhead RGB still. Both are
best-effort (omitted when GCS returns 404 for that dish) and live in the JSONL's free-form
`extra` dict, not the core `Sample` schema:

- `depth_path`: repo-relative path to the aligned 16-bit RealSense `depth_raw.png`
  (640x480, values in millimeters, `0` = no return) from the same overhead rig as
  `image_path`. Used by `depth_volume_eval.py` for the oracle geometric-volume pass.
- `video_path`: repo-relative path to one fixed turntable camera's raw `.h264` clip
  (`imagery/side_angles/{dish_id}/camera_{A..D}.h264`, decode with
  `ffmpeg -i camera_A.h264 frame_%03d.jpeg`, no container). `video_camera` records which
  of the four cameras (A-D) was fetched (default `A`).

```json
{
  "id": "n5k-dish_1558549605",
  "modality": "image",
  "source": "nutrition5k",
  "image_path": "docs/benchmarks/food_accuracy/data/nutrition5k/realsense_overhead/dish_1558549605/rgb.png",
  "meal_name": "dish_1558549605",
  "calories": 233.0,
  "mass_g": 75.0,
  "depth_path": "docs/benchmarks/food_accuracy/data/nutrition5k/realsense_overhead/dish_1558549605/depth_raw.png",
  "video_path": "docs/benchmarks/food_accuracy/data/nutrition5k/side_angles/dish_1558549605/camera_A.h264",
  "video_camera": "A"
}
```
