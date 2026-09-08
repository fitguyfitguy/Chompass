# Food accuracy benchmark: archived findings (rotated 2026-09-08)

Historical run tables and closed experiment records rotated out of
[`docs/FOOD_ACCURACY_BENCHMARK_STATUS.md`](../FOOD_ACCURACY_BENCHMARK_STATUS.md)
(live defaults, gates, and current findings stay there). Content below is
verbatim from the append-only log.

## Lean production prompt (2026-07-24)

The entry prompts in `FoodAnalysisService` (analyzeText / analyzeAuto / analyzeFood / analyzeFoodMulti) now use the **lean** wording: full 28-field JSON schema, a condensed one-line nutrient-units sentence, a one-line `unit_options` rule that embeds the option object shape (`{"unit":"slice","quantity":2,"grams_per_unit":180}`), and a short emoji/null line. ~995 chars vs ~1937 for the old wording. Harness `production_text` / `production_image` mirror it; `legacy_production_image` preserves the old image wording for baselines. The PWA `food-analyze.js` SYSTEM prompt was already lean-style and is unchanged.

Ablations that picked it (`lean_full` = no unit rule; `lean_units` = rule without object shape; `lean_units2` = shipped):

| Run (JFB-50 L0 / text-42) | Model | WMAPE | ±20% kcal | parse | units usable |
|---|---|------:|----------:|------:|---|
| text old production | Flash-Lite | 6.9% | 85.7% | 100% | 0/46 (no grams_per_unit → app drops all) |
| text `lean_full` | Flash-Lite | 5.4% | 92.9% | 100% | no (83% presence, no gpu) |
| text `lean_units2` **(shipped)** | Flash-Lite | **5.3%** | 87.8% | 97.6% | **40/41 sane with gpu** |
| image old production | Flash-Lite | 31.1% | 48% | 100% | 47/53 sane |
| image `lean_full` | Flash-Lite | 31.2% | 46% | 100% | no: bare strings |
| image `lean_units` | Flash-Lite | 33.0% | 40% | 100% | partial (no gpu) |
| image `lean_units2` **(shipped)** | Flash-Lite | **31.25%** | 46% | 100% | **51/51 sane with gpu** |
| text `lean_full` | Gemma 26B :free | 5.3% | 92.7% | 97.6% | — |
| image `lean_full` | Gemma 26B :free | 41.8% | 25% | 96% | — (old production: 47.8%) |
| image `lean_units2` | Gemini 3.6 Flash | 33.2% | 42% | 100% | — |
| image `legacy_production_image` | Gemini 3.6 Flash | 32.5% | **52%** | 100% | — |

Micros present ≥98%, emoji 100% on the shipped variant (both modalities). **Open wrinkle:** on the app-primary Gemini 3.6 Flash, the legacy image wording beat lean on ±20% kcal (52% vs 42%, n=50 single run; WMAPE within 0.7pp): worth a paired re-run before treating that delta as real. Artifacts: `results/lean_prompt_ab/` (gitignored).

Harness fixes landed alongside: `schema.py`/`env_local.py` ROOT was still `parents[2]` from the pre-`docs/` layout (broke `.env.local` key loading and repo-relative manifest paths; image paths in downloaded manifests resolve via a `docs/` fallback), and the smoke script's `query_normalize` import used the old package path.

## Micronutrient scoring (2026-07-29)

Every shipped prompt except the research-only `compact*`/clarify family
(`FULL_JSON_SCHEMA`: `lean_full`, `lean_units`, `lean_units2` shipped default,
`fewshot_units`, `production_text`, `production_image`,
`legacy_production_image`) already asks the model for 21 micronutrient
fields, and the "Micros present ≥98%" note above (line 283) was a manual,
unverified read of one gitignored artifact. The harness now scores these
fields against real ground truth and computes presence rate exactly. See
[manifest/schema.md § Micronutrient ground-truth fields](benchmarks/food_accuracy/manifest/schema.md#micronutrient-ground-truth-fields-optional-in-extra)
for the full field list, and `run_eval.py`'s `mae_micro_*`/`mape_micro_*`/
`n_micro_*`/`presence_rate_*` summary columns / `AggregateScore.micro_wmape`.

**Ground truth: FNDDS text only.** `build_fndds_manifest.py` now pulls 21
micronutrients from USDA `food_nutrient.csv` (previously discarded down to
just the 4 macros): 19 of 21 have **100% GT coverage** across all 5,431
FNDDS survey foods; `added_sugar_g` and `trans_fat_g` have **zero** rows in
this FNDDS release (GT always `None`, not a bug); `omega_3_g` is a composite
of ALA+EPA+DHA+DPA and undercounts since ALA has zero coverage. **JFB and
Nutrition5k have no micronutrient values anywhere in their source CSVs**: GT-free scoring on those manifests reports `n_micro=0` per nutrient rather
than a score; approximating GT via ingredient-name matching to USDA/Open
Food Facts is a distinct, unstarted follow-up (see Gaps below).

Building the new micro-GT manifest surfaced two pre-existing, unrelated bugs
in `build_fndds_manifest.py`, both fixed here since they corrupted the very
data this eval needed: (1) `n.endswith("food.csv")` also matched
`input_food.csv` (which sorts earlier in the zip), silently building all food
descriptions from the wrong CSV (empty/blank text field); (2) `default_portion`
picked whichever `food_portion.csv` row for a food happened to appear first
in file order rather than the FNDDS-designated primary serving
(`seq_num == 1`), landing some foods on nonsensical guideline-amount portions
(e.g. 2.5g "guideline amount per fl oz of beverage" instead of 244g "1 cup").
Both fixes are in the regenerated `manifest/fndds_generated_micro.jsonl`
(200 items, gitignored, `--out` override of the previous
`manifest/fndds_generated.jsonl` default path).

### Results (pinned `google/gemma-4-26b-a4b-it:free`, `FULL_JSON_SCHEMA` prompts)

| Prompt | n | wmape (macros) | within 20% kcal | parse_ok | micro_wmape | presence rate (all 21 nutrients) |
|---|---:|---:|---:|---:|---:|---:|
| `lean_units2` (shipped) | 40 | 20.4% | 70% | 100% | 36.8% | **100%** |
| `lean_units2`, same 20 ids as below | 20 | 12.1% | 90% | 100% | 33.8% | 100% |
| `production_text` | 20 | 42.5% | 90% | 100% | 48.6% | 100% |
| `fewshot_units` | 20 | 8.8% | 94.7% | 95% | 33.0% | 100% |

Macro `wmape` on this set is higher than the curated `eval_text.jsonl`
baseline (~5.7%, finding 1): this is a **harder, noisier FNDDS text
distribution** (near-duplicate short descriptions like "Milk, NFS" /
"Almond milk, sweetened" / "Coconut milk"), not a regression: e.g.
"Coconut milk, 244 g" GT is USDA's low-fat coconut-milk *beverage* (76 kcal)
while the model reasonably assumed common full-fat canned coconut milk
(440 kcal): the bare description doesn't disambiguate. Small n (20-40) means
none of these deltas should be read as a confident prompt ranking; directionally
consistent with finding 2 (`production_text` no better than `lean`/`compact`)
though.

**Headline: presence is a non-issue, accuracy is not.** All three prompts hit
exactly 100% presence on every one of the 21 nutrient fields (not just
"≥98%"): the model never silently drops a micronutrient. But `micro_wmape`
(33-49%) is 1.5-4× the matching macro `wmape`, i.e. once a value is present it
is *not* proportionally as accurate as calories/protein/carbs/fat: sodium
MAPE ~13-23%, vitamin C MAPE ~100%+ (small-gram vitamin C values make percentage
error extremely noisy), consistent with USDA per-100g micronutrient values
being inherently higher-variance across similar-sounding foods than the four
headline macros.

**Not yet done:** a paired run on the curated `eval_text.jsonl`-style clean
food set (to isolate prompt/model effects from this set's description
ambiguity), a paid-model pin (Gemini/gpt-4o-mini) for a stronger micro
ceiling, and validating whether `micro_wmape` correlates with anything
actionable (e.g. is sodium/potassium error concentrated in the same
ambiguous-description items that drive macro error, or independent).

Artifacts: `results/micro_ab/fndds_{lean_units2,production_text,fewshot_units}_gemma/` (gitignored).

## Results tables

Lower WMAPE is better. `parse_ok` and within-20% are higher-is-better. Free-tier rate limits (429) and upstream 502s inflate “fail” rates on some pin runs: infra-adjusted notes below.

### Text (`eval_text.jsonl`, n=42, prompt `compact` unless noted)

| Run | Model | parse_ok | WMAPE | mae kcal | within 20% | Notes |
|-----|-------|----------|-------|----------|------------|-------|
| `baseline_compact_free` | `openrouter/free` | 81% | **4.9%** | 7.3 | 91% | 7 content-safety fails; surviving backends lucky |
| `prompt_ab_gemma/compact` | Gemma 4 26B :free | **100%** | **5.7%** | 8.2 | 90% | **Best text default** |
| `prompt_ab_gemma/production_text` | Gemma 4 26B :free | 100% | 6.2% | 9.1 | 93% | No win vs compact; ~3× slower |
| `prompt_ab_gemma/fewshot_units` | Gemma 4 26B :free | 100% | 5.9% | 8.7 | **95%** | Slight within-20% edge; not worth latency for text macros |
| `next_free_pins` Gemma | Gemma 4 26B :free | 93% | 5.9% | 8.4 | 92% | 2× 429 mid-run |
| `next_free_pins` Cohere | `cohere/north-mini-code:free` | **100%** | 7.8% | 12.1 | 81% | Reliable but slower/worse macros |
| `next_free_pins` Nemotron-omni | omni reasoning :free | 83% | 8.1% | 11.2 | 83% | Many ResourceExhausted / 502 |

**Text pin ranking (usable):** Gemma ≫ Cohere > Nemotron-omni (flaky free tier).

### Image (JFB 50)

| Run | Model | prompt | parse_ok | WMAPE | mae kcal | within 20% | Notes |
|-----|-------|--------|----------|-------|----------|------------|-------|
| `baseline_image_free_compact` | `openrouter/free` | compact | **58%** | 47.1% | 172 | 31% | **19** content-safety + 1× 504 |
| `baseline_image_nofud_free_compact` | mix / `nofud/free` fill | compact | **98%** | **43.2%** | 166 | 33% | Filled 20 fails; 1 truncated VL JSON left |
| `image_prompt_ab_gemma/compact` | Gemma 4 26B :free | compact | **100%** | **39.8%** | 152 | **32%** | **Best image prompt**; ~9 s mean |
| `image_prompt_ab_gemma/production_image` | Gemma 4 26B :free | production_image | 96% | 47.8% | 186 | 25% | No win; ~17 s; 1× 500 + 1 parse fail |
| `image_prompt_ab_gemma/fewshot_units` | Gemma 4 26B :free | fewshot_units | 96% | 46.6% | 183 | 25% | No win; ~14 s; 2× JSON “Extra data” |

Filled backend mix (nofud baseline): Gemma 26B 27/27, Nemotron nano-VL 14/15, Nemotron omni 8/8.

**Image prompt ranking (pinned Gemma):** compact ≫ fewshot_units ≥ production_image.

### Image + description (JFB 50, Gemma 4 26B :free, prompt `compact`)

Same 50 IDs; only user `text` differs. L1 = meal title; L2 = ingredient names (no qty/macros). Prompt uses `sample.text` only (not `meal_name` metadata).

| Run | User text | parse_ok | WMAPE | mae kcal | within 20% | mean latency | Notes |
|-----|-----------|----------|-------|----------|------------|--------------|-------|
| `image_text_ab/l0_image_only` | none (L0) | **100%** | **41.8%** | **161** | **28%** | ~8.7 s | **Best of L0/L1/L2** |
| `image_text_ab/l1_meal_name` | meal title (L1) | 100% | 44.9% | 173 | 28% | ~9.0 s | +3.1 pp WMAPE vs L0 |
| `image_text_ab/l2_ingredient_names` | ingredient names (L2) | 100% | 45.8% | 178 | 22% | ~8.7 s | +4.0 pp WMAPE vs L0 |

**Image+text ranking (Gemma compact):** L0 ≥ L1 > L2. Product “add a short note” may still help identification/UX; on this pin it did not improve macro WMAPE.

### Image + description on Nutrition5k / ACETADA (2026-07-29)

New adapters (`download_nutrition5k.py` L2, `download_acetada.py` L0/L1/L2). n=15 each,
`compact`, cheap pins. ACETADA is **CC BY-NC** (research only: not for product claims).
NutritionVerse-Real skipped (no local Kaggle extract).

#### Flash Lite (`google/gemini-3.5-flash-lite`)

| Run | Dataset / text | parse_ok | WMAPE | mae kcal | within 20% | cost (n=15) | Notes |
|-----|----------------|----------|------:|---------:|-----------:|------------:|-------|
| `n5k_l0_gemini35_flash_lite` | N5k L0 | 100% | **37.4%** | 88 | 20% | $0.0070 | Lab plates; matches prior ~35% cursory |
| `n5k_l2_gemini35_flash_lite` | N5k L2 ingredient names | 100% | **30.6%** | 70 | 27% | $0.0073 | **−6.8 pp WMAPE vs L0** |
| `acetada_l0_gemini35_flash_lite` | ACETADA L0 | 100% | **22.7%** | 125 | 40% | $0.0073 | Free-living before-meal; easier than JFB/N5k |
| `acetada_l1_gemini35_flash_lite` | ACETADA L1 meal_type | 100% | **18.9%** | 102 | **67%** | $0.0074 | Breakfast/Lunch/Dinner helps |
| `acetada_l2_gemini35_flash_lite` | ACETADA L2 item names | 100% | **15.0%** | 81 | **87%** | $0.0077 | **Best of this slate** |

**Flash Lite ranking:** On N5k and ACETADA, short notes **help** (opposite of JFB Gemma L0≥L1>L2). ACETADA L2 is a large win (+47 pp ±20% vs L0). N5k L2 is a moderate win. Total Flash Lite spend ≈ **$0.037**.

#### Free router (`nofud/free`): skipped (2026-07-30)

Full free slate aborted for rate-limit / wall-time cost. Two N5k runs finished before abort; ACETADA free not run.

| Run | Dataset / text | parse_ok | WMAPE | mae kcal | within 20% | Notes |
|-----|----------------|----------|------:|---------:|-----------:|-------|
| `n5k_l0_nofud_free` | N5k L0 | 100% | **46.7%** | 112 | 40% | Completed before skip |
| `n5k_l2_nofud_free` | N5k L2 | 100% | **33.2%** | 78 | 33% | Completed before skip; **−13.5 pp WMAPE vs L0** (same direction as Flash Lite) |
| ACETADA L0/L1/L2 | — | — | — | — | — | **Skipped** |

### Follow-ups (JFB 50, L0/L1)

| Run | Model | prompt | parse_ok | WMAPE | mae kcal | within 20% | mean latency | Notes |
|-----|-------|--------|----------|-------|----------|------------|--------------|-------|
| `image_text_ab/l1_meal_name_production_image` | Gemma 4 26B :free | production_image | 96% | 47.3% | 184 | 23% | ~15 s | L1 + app prompt; worse than L1 compact |
| `image_text_ab/l0_gemini36_flash` | `google/gemini-3.6-flash` | compact | **100%** | **32.3%** | **123** | **50%** | ~5.1 s | **Best plate so far** |
| `image_text_ab/l0_gpt4o_mini` | `openai/gpt-4o-mini` | compact | 100% | 34.5% | 130 | 50% | ~3.5 s | Strong paid baseline |
| `image_text_ab/l0_gemini35_flash_lite` | `google/gemini-3.5-flash-lite` | compact | 100% | 35.9% | 137 | 40% | **~1.6 s** | Best speed/price among good paid |
| `image_text_ab/l0_qwen35_flash` | `qwen/qwen3.5-flash-02-23` | compact | 100% | 37.1% | 142 | 36% | ~64 s | Cheap; accurate-ish but **very slow** |
| `image_text_ab/l0_claude3_haiku` | `anthropic/claude-3-haiku` | compact | 100% | 37.9% | 141 | 40% | ~2.3 s | Cheap Claude; mid pack |
| `image_text_ab/l0_gemini35_flash_lite_compact_portion` | same | compact_portion | 100% | 37.2% | 141 | 36% | ~1.6 s | Portion rules **no win** vs compact; not shipped |
| `baseline_image_nofud_free_compact_cold` | `nofud/free` | compact | **100%** | **41.1%** | **152** | **32%** | ~16 s | Cold free-router L0; ≈ Gemma pin |
| `image_text_ab/l0_gpt5_nano` | `openai/gpt-5-nano` | compact | 100% | 43.8% | 166 | 28% | ~10 s | Too cheap for plates; ≈ free Gemma |

**Paid L0 ranking:** Gemini 3.6 Flash ≥ gpt-4o-mini ≥ Gemini 3.5 Flash-Lite ≥ Qwen3.5-Flash ≈ Claude 3 Haiku ≫ nofud/free ≈ Gemma ≈ GPT-5 Nano.

### Image (Nutrition5k overhead RGB, cursory)

| Run | Model | prompt | n | parse_ok | WMAPE | mae kcal | within 20% | Notes |
|-----|-------|--------|---|----------|-------|----------|------------|-------|
| `n5k_cursory_gemma_compact` | Gemma 4 26B :free | compact | 15 | **100%** | **34.7%** | **80** | **40%** | HTTPS overhead subset; ~7.5 s mean; small-kcal dishes inflate MAPE |

Artifacts under `docs/benchmarks/food_accuracy/results/` (gitignored).

### Depth/volume estimation from Nutrition5k (2026-07-28)

Reopens the "monocular image→depth" idea from `UNCERTAINTY_DRIVEN_ENTRY.md`
(previously parked as a non-bet) using data that sits one step away from
what the harness already fetches: the same 15-dish cursory Nutrition5k subset
above, plus its aligned RealSense `depth_raw.png` (16-bit, mm-scale sensor
units) and one turntable side-camera clip per dish, neither previously
downloaded. `download_nutrition5k.py --with-depth --with-video` now fetches
both; `depth_volume_eval.py` is a new standalone script (not routed through
`run_eval.py`: no LLM calls, pure geometry/vision).

**No camera intrinsics are published for this dataset** (checked: nothing under
`metadata/` or `scripts/` in the GCS bucket). The observed `depth_raw` values
(~3000-4000 raw units for the table plane) don't match a physically sensible
close-range overhead rig under any nominal RealSense mm-per-unit assumption
tried, so the script does not attempt absolute-unit volume (cm³) or a density
constant. Instead it computes a **volume proxy** (Σ pixel-height-above-table ×
depth², proportional to true volume up to one unknown-but-constant
camera-intrinsic factor for this fixed rig) and fits **one global linear scale**
against true `mass_g` across the 15-dish set: i.e. it measures whether
depth-derived volume correlates with mass at all, not whether it hits absolute
grams.

| Pass | What | Corr(proxy, mass_g) | MAE (g, in-sample) | MAPE (in-sample) |
|------|------|---------------------:|--------------------:|-------------------:|
| Oracle (true RealSense depth) | ceiling | **0.564** | 91.2 | 67.4% |
| Monocular (Depth Anything V2 Small, per-image affine-calibrated to oracle) | realistic phone-camera case | **0.097** | 107.4 | 65.2% |

MAE/MAPE are **in-sample** (the global scale constant was fit on this same
15-dish set) and therefore overstate held-out accuracy: correlation, which
doesn't depend on the fit, is the more honest signal at n=15. Per-dish output:
`results/depth_volume/n5k/per_dish.csv`.

**Verdict:**
- **True depth has a moderate, real signal** (r=0.564) but the volume proxy
  badly compresses dynamic range (predicted mass spans ~68-251g while true
  mass spans 57-552g): the largest dish (552g) is undershot by more than half.
  This is the expected consequence of the flat-density assumption: a dense
  stew and a fluffy salad of the same true depth-volume have very different
  mass, and this prototype has no per-food density model. Even at the oracle
  ceiling, naive volumetric mass is far from competitive with typed-text
  accuracy (~5-6% WMAPE) or even current photo WMAPE (~32-40%).
- **Monocular (camera-only) depth carries no signal here**
  (r=0.097): after per-image affine calibration to the oracle scale, Depth
  Anything V2 Small's relative depth map does not predict Nutrition5k mass
  better than noise. This is the realistic case for an eventual phone-camera
  feature, and it's a negative result.
- **Video/multi-angle** (turntable `side_angles` clips, 12/15 dishes had a
  fetchable clip): per-dish coefficient of variation of a relative "bulge"
  proxy across 2-4 extracted frames averaged **10.1%**: a same-dish, same-lighting
  view-angle sensitivity check only (no metric calibration attempted for side
  cameras; no published geometry). Directionally consistent with "a single
  RGB view is noisy," but not large enough on its own to justify multi-frame
  capture UX given the monocular result above already failed to clear a bar.

### Native video input vs still image (2026-07-28)

Distinct from the depth-extraction result above: instead of extracting a depth
map, send the raw turntable clip **directly** to a vision-language model as
native video (OpenRouter `video_url` content type, base64 `data:video/mp4`),
and let the model reason over motion/parallax itself: the "casual orbit
video → native multi-frame reasoning" candidate from
`UNCERTAINTY_DRIVEN_ENTRY.md` § New candidates. Harness gained first-class
video support for this: `providers.py` now accepts `video_path` and builds a
`video_url` block, `schema.py` adds `Sample.resolved_video_path()` (reads
`extra.video_path`), and `run_eval.py --video` sends the clip instead of the
sample's still image. Raw Nutrition5k `camera_A.h264` elementary streams were
remuxed to `.mp4` (`ffmpeg -c copy`, no re-encode) since OpenRouter only
accepts mp4/mpeg/mov/webm containers. `google/gemma-4-26b-a4b-it:free` was
confirmed to advertise `video` in `input_modalities` (`list_nofud_free_pool`
catalog check): same free pin used elsewhere in this doc, so the run is $0.

Paired same-model, same-prompt (`compact`), same 12 N5k dishes (the subset
with a fetchable `side_angles` clip):

| Input | parse_ok | WMAPE | ±20% kcal | mean prompt tokens | Notes |
|-------|----------|------:|----------:|--------------------:|-------|
| Still image (`image_path`) | 100% | **25.6%** | **41.7%** | 375 | clean run, no retries |
| Whole clip (`video_url`) | 100%* | 37.2% | 33.3% | 1575 (4.2×) | *5/12 first-pass 504 "media decode ~5859 MiB capacity" timeouts: 504 wasn't in the harness's retryable set (only 429/502 were); fixed and resumed to reach 100% parse |

Per-dish the effect is mixed (3/12 improved a lot, 5/12 got much worse, 4/12
unchanged), but the aggregate is a clear net loss, not noise: **+11.6pp
WMAPE, −8.3pp ±20% accuracy, 4.2× prompt tokens, and materially worse
reliability** (free-tier backends struggle with video decode load: this
would cost real money and add latency on a paid tier too, given the token
multiplier). Full per-sample breakdown: `results/video_ab/n5k12_{image_baseline,video}/`.

**Verdict: native video input does not help on this evidence: park.** This
confirms the same direction as the depth-extraction result (temporal/geometric
cues from a single fixed-camera clip do not reliably add signal over one
still frame) via a completely different mechanism (no depth model, raw frames
straight to the VLM). Doesn't rule out a *casual orbit* capture (deliberate
multi-angle from the user, not a fixed lab turntable) or a stronger paid model,
but this was the cheapest test of the "just give the model more frames" idea
and it lost: not worth spending the self-captured-clip-dataset effort this
was gating in `UNCERTAINTY_DRIVEN_ENTRY.md` without a stronger prior. Harness
video support (`--video` flag, `video_path` provider plumbing) is now in place
for any future re-test.

**Product implication:** this does not move Bet 1-3 in `UNCERTAINTY_DRIVEN_ENTRY.md`.
The oracle ceiling (true, sensor-grade depth) is still far from useful for a
direct mass estimate without a food-density model this prototype doesn't have,
and the realistic camera-only case shows ~zero signal. Combined with the
already-documented on-device cost (LiteRT-LM can't host a depth model, a
second inference runtime would contend with Gemma's GPU/RAM budget, and the
same open F-Droid runtime-model-fetch question would apply), **no Android
follow-up is justified from this result.** Revisit only if a future monocular
depth model ships with an explicit, learned food-density head (not a flat
constant): a plain relative depth map alone does not appear to help.

Reproduce:
```bash
uv run --with pillow python docs/benchmarks/food_accuracy/download_nutrition5k.py \
  --limit 15 --with-depth --with-video A \
  --out docs/benchmarks/food_accuracy/data/manifests/n5k_depth.jsonl
nix shell nixpkgs#ffmpeg -c bash -c '
  uv run --with torch --with transformers --with pillow --with numpy python \
    docs/benchmarks/food_accuracy/depth_volume_eval.py \
    --manifest docs/benchmarks/food_accuracy/data/manifests/n5k_depth.jsonl \
    --out docs/benchmarks/food_accuracy/results/depth_volume/n5k
'
```

---

## Gaps / not done yet

- [x] Image **prompt A/B** on pinned Gemma (`compact` vs `production_image` vs `fewshot_units`): compact wins; longer prompts hurt
- [x] Image + **description A/B** on JFB 50 (L0/L1/L2, Gemma compact): L0 image-only wins; L1/L2 do not improve WMAPE
- [x] Image + description A/B on **Nutrition5k L2** + **ACETADA** L0/L1/L2 with Flash Lite (n=15): notes **help** here (opp. JFB); ACETADA L2 best (15.0% WMAPE / 87% ±20%). Free slate skipped after N5k L0/L2 completed
- [ ] NutritionVerse-Real L0/L2 once Kaggle extract is local (`download_nutritionverse_real.py`)
- [ ] ACETADA / N5k free-router replicate (skipped 2026-07-30)
- [x] Full **fresh** 50-image run with `nofud/free` from cold start: parse 100%, WMAPE 41.1% (≈ Gemma pin)
- [x] Nutrition5k overhead RGB **cursory** (n=15, Gemma compact): WMAPE ~35%; lab plates still hard
- [x] Image+text with **`production_image`** on L1: worse than L1 compact (47.3% vs 44.9% WMAPE)
- [x] Paid VL ceiling (`gpt-4o-mini` L0): WMAPE **34.5%** / ±20% **50%**; better than free, still hard
- [x] Gemini paid L0 (`3.5-flash-lite` **35.9%**, `3.6-flash` **32.3%**): 3.6 Flash is current plate leader
- [x] Cheap multi-provider L0: Claude 3 Haiku **37.9%**, Qwen3.5-Flash **37.1%** (slow), GPT-5 Nano **43.8%** (no win). DeepSeek = no vision; Luna skipped (not cheap)
- [x] Nutrition5k larger slice (n≥50) if model A/B needs a second image distribution: done 2026-07-30 as part of entry matrix (Flash Lite L0/L1/Lq/bucket)
- [x] Paid pin on L1 / Lq meal notes (Flash Lite entry matrix 2026-07-30): Lq wins; L1 weak. Optional follow-up: Gemini 3.6 Flash L0/L1/Lq on JFB
- [ ] Nutrition-label OCR track (Open Food Facts)
- [ ] On-device LiteRT scoring against the same manifests (phase 2)
- [x] Port a compact-style prompt into [`FoodAnalysisService.kt`](../android/app/src/main/java/app/chompass/services/ai/FoodAnalysisService.kt): done 2026-07-24 as the **lean** wording (full schema kept; see § Lean production prompt). Follow-up: paired re-run of lean vs `legacy_production_image` on Gemini 3.6 Flash (±20% dip, n=50 single run)
- [x] **Post-hoc bias calibration + cross-model ensembling** (2026-07-29, $0, re-scored from stored artifacts): per-model calibration up to −7.5pp WMAPE; 2-model median ensemble 29.4% WMAPE / 62% ±20%; stacked 27.0% / 60%. Self-consistency, `serving_size_grams` confidence, and disagreement-as-uncertainty all **negative**. See § Post-hoc calibration & ensembling
- [ ] Validate the calibration factor out-of-sample on a **larger N5k slice** (n≥50) before shipping any per-model constant: JFB→N5k transfer cost 20pp of ±20% accuracy; N5k-50 L0 now available for a recalibration pass
- [ ] Live A/B of a 2-model ensemble path in the app (cost/latency vs +12pp ±20%); needs a product decision on N× BYOK spend
- [ ] Optional: refresh `nofud/free` pools periodically mid-run (today: once per process)
- [x] **Simulated clarification eval** (2026-07-24, JFB-50, Flash-Lite): portion clarification **ships** (−15.2pp WMAPE, +12pp ±20% on **stated amounts**); fat clarification **parked** (−5.2pp, hurts ±20%); model self-selecting which question to ask is **not usable** (92% ask rate, prefers the weaker fat question 34/50 vs portion 12/50); the trigger must be heuristic, not model self-report. See § Simulated clarification eval.
- [x] Confirm portion-clarification result on Nutrition5k (true-mass oracle, no lexicon dependency); confirmed, n=15: −18.7pp WMAPE, +53.4pp ±20% (stronger than JFB)
- [x] **Split portion-oracle signals** (2026-07-29): harness prompts `compact_clarify_portion_{grams,bucket,amounts}` + covered-id lists; docs corrected so chip labels are not credited with the mixed-oracle ceiling. Bucket-only default-on still gated on a paid A/B.
- [x] Paired N5k A/B: `compact` vs `compact_clarify_portion_bucket` (2026-07-30, n=50 Flash Lite): bucket **28.7%** WMAPE / **32%** ±20% vs L0 **32.6%** / **24%**; real but does not beat Lq (**27.6%** / **34%**). Grams-split A/B still open. Keep chips soft/opt-in.
- [ ] Paired N5k A/B: `compact` vs `compact_clarify_portion_grams` (exact mass)
- [ ] Paired JFB A/B: `compact` vs `compact_clarify_portion_amounts`
- [x] **Photo-adjacent L0/L1/Lq matrix** (2026-07-30): see § Photo-adjacent entry matrix
- [x] **Text-only hard vague-quantity multi-model bake-off** (2026-07-31, JFB-50): see § Text-only vague-quantity bake-off. N5k text-Lq Flash Lite + DeepSeek replicate done (text-only much harder than image+Lq on N5k).
- [x] **Native video input vs still image** (2026-07-28, N5k turntable clips, free Gemma pin, n=12 paired): video input **lost**: WMAPE 25.6%→37.2%, ±20% 41.7%→33.3%, 4.2× tokens, worse reliability. See § Native video input vs still image.
- [x] **Portion-aware prompt A/B**: `compact` vs `compact_portion` on Gemini 3.5 Flash-Lite JFB L0: portion rules **did not win** (WMAPE 37.2% vs 35.9%, ±20% 36% vs 40%). Reverted from production prompts; `compact_portion` kept as research-only.
- [x] **Micronutrient scoring** (2026-07-29, FNDDS text, pinned Gemma): implemented for text; presence rate **100%** on every nutrient across all `FULL_JSON_SCHEMA` prompts tested; `micro_wmape` (33-49%) trails macro `wmape` by 1.5-4×. See § Micronutrient scoring.
- [ ] **Micronutrient scoring for JFB/Nutrition5k (image)**: no micronutrient values exist in either dataset's source data; needs an ingredient-name → USDA/Open Food Facts lookup to derive approximate GT, a distinct and noisier project from the text case. Not started.
- [ ] **Meal constituents gate (WIP / next app roadmap)**: `production_text_constituents` + `score_constituents.py`. Gemini 3.6 Flash passes; free Gemma + 3.5 Flash Lite fail grams/macros reconcile (Lite 75%). Fix reconcile → re-gate → ship schema + result-sheet UX (#154). See § Meal constituents gate.
- [ ] Paired micronutrient run on a clean, unambiguous text set (current FNDDS-generated manifest has ambiguous near-duplicate descriptions like "Coconut milk" that inflate macro WMAPE vs the curated `eval_text.jsonl`) to isolate prompt/model micro accuracy from description ambiguity.

---
