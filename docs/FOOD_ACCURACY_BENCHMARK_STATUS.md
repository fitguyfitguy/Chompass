# Food accuracy benchmark: current state

| | |
|---|---|
| **As of** | 2026-08-14 |
| **Harness** | [`docs/benchmarks/food_accuracy/`](benchmarks/food_accuracy/) |
| **How-to** | [`FOOD_ACCURACY_BENCHMARK.md`](FOOD_ACCURACY_BENCHMARK.md) |
| **Grounded WIP** | [`GROUNDED_ENTRY.md`](GROUNDED_ENTRY.md): **not production**; UI flag off |
| **API** | OpenRouter via `OPENROUTER_TOKEN` in `.env.local` |

**Summary (2026-08-14):** Meal-constituents gate **PASS**: `constituents[]` shipped
in 3.5.0 with bounded client reconcile (Settings toggle *Meal ingredient
breakdown*). Per-constituent micro extension is an **excursory** result, **not
shipped**: Gemini 3.7 Flash is ~2× more accurate than Flash Lite there; wire
change pending (design analysis in maintainer-local docs). Photo entry:
Flash Lite + vague-quantity notes remains the value default; 3.7 Flash adds
+14pp ±20% on JFB at ~4× cost. Grounded entry stays **WIP / off**. Defaults in
§ Defaults; full findings log below.

This note records what we have measured so far, which defaults to use, and what is still open. It is a snapshot, not a permanent API.

---

## Harness overview

| Piece | Role |
|-------|------|
| `run_eval.py` | Main CLI: manifest → prompt → provider → MAE/MAPE/WMAPE |
| `nofud/free` | Client-side free router (preferred); excludes content-safety |
| `openrouter/free` | Stock OpenRouter router: **not for accuracy benches** |
| `manifest/eval_text.jsonl` | 42 FNDDS-derived text items (checked in) |
| `data/manifests/jfb.jsonl` | 50 JFB meal images L0: image only (downloaded, gitignored) |
| `data/manifests/jfb_image_text_l1.jsonl` | 50 JFB: image + meal title as user note |
| `data/manifests/jfb_image_text_l2.jsonl` | 50 JFB: image + ingredient names as user note |
| `data/manifests/jfb_image_text_lq.jsonl` | 50 JFB: image + vague quantity diary note (`build_image_text_lq.py`) |
| `data/manifests/jfb_text_lq.jsonl` / `jfb_text_l1.jsonl` | 50 JFB: **text-only** Lq / L1 (`build_text_lq.py`) |
| `manifest/jfb_hard_ids.txt` | Hard-tail IDs (never ±20% cohort + documented hard plates) |
| `data/manifests/n5k.jsonl` | Nutrition5k overhead RGB L0 (n=50; name-only + weighed ingredients) |
| `data/manifests/n5k_image_text_l1.jsonl` | Nutrition5k: coarse identity from top ingredients |
| `data/manifests/n5k_image_text_l2.jsonl` | Nutrition5k: image + ingredient names |
| `data/manifests/n5k_image_text_lq.jsonl` | Nutrition5k: image + vague quantity note (bucket + ingredients) |
| `data/manifests/n5k_text_lq.jsonl` / `n5k_text_l1.jsonl` | 50 N5k: text-only Lq / L1 (`build_text_lq.py`) |
| `data/manifests/acetada.jsonl` (+ `_l1` / `_l2`) | ACETADA before-meal L0/L1/L2: **CC BY-NC**, research only |
| `data/manifests/nvreal.jsonl` (+ `_l1` / `_l2`) | NutritionVerse-Real: needs local Kaggle extract; **CC BY-NC-SA** |
| Metrics | `parse_ok`, WMAPE on {kcal, protein, carbs, fat}, ±20% kcal rate |
| `manifest/eval_constituents_text.jsonl` | 16 composite meals for P1 constituents gate |
| `production_text_constituents` | Research candidate prompt (optional `constituents[]`) |
| `production_text_constituents_micro` | Excursory variant: per-constituent full macro + 21-micro breakdown |
| `score_constituents.py` | Constituent presence / min-components / reconcile gate scorer |
| `score_constituents_micro.py` | Excursory: per-constituent micro presence / sum reconcile + FNDDS name-match micro GT |

**Default free-tier command shape:**

```bash
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model nofud/free \
  --prompt compact --sleep 8 --retries 2
```

`nofud/free` rebuilds its pool from the live OpenRouter `/models` catalog **once per process** (new free models appear; cancelled ones drop). Mid-run the pool is cached. Inspect with:

```bash
uv run python docs/benchmarks/food_accuracy/list_nofud_free_pool.py --vision --show-excluded
```

Vision pool as of this date (4): `google/gemma-4-26b-a4b-it:free`, `google/gemma-4-31b-it:free`, `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`, `nvidia/nemotron-nano-12b-v2-vl:free`.

---

## Headline findings

1. **Text macros are strong on free Gemma.** With `compact` + `google/gemma-4-26b-a4b-it:free`, parse_ok ≈ 100%, WMAPE ≈ **5.7%**, within ±20% kcal ≈ **90%**, mean latency ≈ **5 s**.
2. **Full production text prompt does not beat compact** on this text set. `production_text` WMAPE ≈ 6.2%, `fewshot_units` ≈ 5.9%, both ~3× slower. Prefer compact for cloud text calorie accuracy research.
3. **Stock `openrouter/free` is unsafe for food JSON.** It often routes to `nvidia/nemotron-3.5-content-safety:free` → `User Safety: safe` → parse fail (text: 7/42; image: **19/50**).
4. **`nofud/free` fixes reliability.** Image fill-in: parse_ok **58% → 98%**, zero content-safety routes.
5. **Plate image estimation is still hard.** Even with 98% parse_ok, image WMAPE ≈ **43%**, within ±20% kcal ≈ **33%**. Reliability ≠ calorie accuracy for vision.
6. **Image prompt shape does not fix plate WMAPE.** On pinned Gemma 26B :free (JFB 50), `compact` wins: WMAPE **39.8%** / ±20% **32%** / parse **100%** / ~9 s. `production_image` and `fewshot_units` are worse (~47% / ~46% WMAPE), slower (~17 s / ~14 s), and 96% parse. Stop prompt-chasing for cloud vision accuracy; next bets are models/datasets/priors.
7. **Lab overhead (Nutrition5k) is only mildly easier than phone meals.** Cursory n=15 overhead RGB + Gemma compact: WMAPE **34.7%**, ±20% **40%**, mae kcal **80**, parse **100%**. Better than JFB (~40% WMAPE) but still far from text (~6%). Error is not “messy phone photo” alone: portion/macro estimation stays hard even with clean top-down plates.
8. **Simple JFB descriptions do not help Gemma compact macros.** Paired L0/L1/L2 on JFB 50 (pinned Gemma 26B, `compact`): L0 image-only WMAPE **41.8%** / ±20% **28%** beats L1 meal title (**44.9%**) and L2 ingredient names (**45.8%** / ±20% **22%**). All 100% parse.
9. **`production_image` + L1 meal title still loses.** Same Gemma pin: WMAPE **47.3%**, parse **96%**, ±20% **23%**, ~15 s: worse than L1 compact (44.9%) and L0 compact (41.8%). App-parity prompt does not rescue short-note context on free Gemma.
10. **Paid VL ceiling is better but still hard.** Best so far: `google/gemini-3.6-flash` L0 compact: WMAPE **32.3%**, ±20% **50%**, mae kcal **123**, ~5 s. Then gpt-4o-mini **34.5%**, Gemini 3.5 Flash-Lite **35.9%**. Cheap multi-provider adds: Qwen3.5-Flash **37.1%**, Claude 3 Haiku **37.9%**, GPT-5 Nano **43.8%** (≈ free). Still far from text (~6%).
11. **Cold `nofud/free` image baseline is solid.** Fresh L0 run: parse **100%**, WMAPE **41.1%**, ±20% **32%**: matches pinned Gemma (~41.8%) within noise; no content-safety fails.
12. **No OpenRouter `gemini-3.6-flash-lite`.** Lite sibling is `google/gemini-3.5-flash-lite`; full Flash is `google/gemini-3.6-flash`.
13. **Plate error is portion priors, not meal size.** Consensus hard vs easy JFB meals have nearly identical mean GT kcal (~395 vs ~391). Short “portion grounding” prompt rules did **not** beat compact (see [Failure modes](#failure-modes--portion-reasoning)).
14. **DeepSeek Flash / GPT-5.6 Luna not useful for the plate (vision) slate.** DeepSeek models on OpenRouter are **text-only** (no vision). Luna is ~$1/$6 input/output: not in the cheap tier; skipped for cost. DeepSeek **is** competitive on text-only Lq (see finding 20).
15b. **Native video input loses to a still image (2026-07-28).** Sending the raw Nutrition5k turntable clip directly as `video_url` (no depth extraction, model reasons over motion itself) instead of the single overhead RGB still, same free Gemma pin / `compact` prompt / 12 paired dishes: WMAPE **25.6% → 37.2%** (+11.6pp), ±20% kcal **41.7% → 33.3%** (−8.3pp), 4.2× prompt tokens, and materially worse free-tier reliability (video decode capacity 504s). See § Native video input vs still image.
16. **Plate overestimation is a systematic, correctable per-model bias (2026-07-29).** Free re-scoring of stored predictions: 10/12 vision runs have a fitted kcal scale of 0.76–0.84 (text fits are exactly 1.000). Per-model LOO calibration buys up to **−7.5pp WMAPE / +22pp ±20%**; median-ensembling two models beats every single model (**29.4% WMAPE**, and Gemini 3.6 + gpt-4o-mini reaches **62% ±20%**); the two stack to **27.0% WMAPE / 60% ±20%**: the best plate numbers in this doc. Magnitude does not transfer across datasets, so ship a conservative per-model factor or learn it per user, not a JFB constant. See § Post-hoc calibration & ensembling.
17. **Nothing the model emits predicts its own error (2026-07-29).** Self-consistency (median of 3 identical runs) does **not** beat one call (37.6% vs 35.9%): the error is bias, not variance. corr(predicted `serving_size_grams`, true kcal) ≈ +0.14; corr(cross-model disagreement, actual error) = **+0.012**. Together with the 92% ask-rate finding, all model-side confidence channels are dead: chip triggers must be unconditional and calorie bands fixed-width per entry type.
15. **The old production-prompt gap was rule verbosity, not schema size: lean production shipped (2026-07-24).** A "lean" prompt (full 28-field app schema, compact wording + one-line unit_options rule carrying the object shape, `lean_units2`) matches compact-level text macros while keeping micros/emoji/units: Flash-Lite text WMAPE **5.3%** (old production 6.9%, compact 4.8%) and image **31.25%** (old production 31.1%, compact 35.9%). The Gemma image "production is 8pp worse" finding was a Gemma artifact; on Flash-Lite the verbose image prompt was actually *better* than compact; lean keeps that win at half the prompt tokens. Bonus: the old text prompt never elicited `grams_per_unit`, so **every AI text serving unit was silently dropped by the app parser**; lean fixes this (40/41 usable vs 0). Shipped to `FoodAnalysisService` (all four entry prompts) and mirrored in `prompts.py` `production_*`. See § Lean production prompt (2026-07-24).
18. **Micronutrient scoring is now implemented: text/FNDDS only (2026-07-29).** The harness previously discarded all 21 micronutrient fields the shipped prompts already ask for; it now scores them against real USDA FNDDS ground truth (`schema.MICRO_FIELDS`, populated by `build_fndds_manifest.py`). Headline: the model **reliably emits every micronutrient**: presence rate is **100%** across all three `FULL_JSON_SCHEMA` prompts tested (`lean_units2`, `production_text`, `fewshot_units`, pinned Gemma 26B :free, n=20-40), confirming the previously-manual, unverified "Micros present ≥98%" note. Micro accuracy trails macro accuracy by roughly the same ratio macros/plates already show: `micro_wmape` **33-49%** vs macro `wmape` **9-42%** on the same runs (see § Micronutrient scoring below); this is a **new, harder FNDDS text subset** with ambiguous short descriptions (e.g. "Coconut milk, 244 g": USDA's low-fat beverage definition at 76 kcal vs the model's reasonable full-fat-can assumption at 440 kcal), not directly comparable to the curated `eval_text.jsonl` (~5.7% WMAPE). **JFB and Nutrition5k have no micronutrient ground truth in their source data at all**: micro scoring on those manifests reports `n_micro=0`, not a score; deriving approximate GT via ingredient-name matching to USDA/OFF is a distinct, unstarted follow-up. See § Micronutrient scoring.
19. **Vague quantity notes (Lq) beat image-only and meal-title notes (2026-07-30).** Paired Flash Lite matrix on shared IDs: JFB-50 Lq WMAPE **25.3%** / ±20% **52%** vs L0 **35.9%** / **40%** and L1 **33.0%** / **36%**. N5k-50: Lq **27.6%** / **34%** vs L0 **32.6%** / **24%**, L1 **29.6%** / **28%**, bucket chips **28.7%** / **32%**. Identity-only L1 is weak; **quantity language in the user note** (without exact grams) is the lever. Bucket chips help on N5k but do not beat Lq. Hard-tail JFB (10 IDs) still 0% ±20% under all three note conditions: Lq cuts hard-tail mean MAPE proxy 92%→54% but does not “solve” those plates. See § Photo-adjacent entry matrix.
20. **Text-only hard vague-quantity (Lq) bake-off (2026-07-31).** Same JFB-50 Lq diary strings scored as typed entry (`modality=text`, no photo), 7 models + L1 control. Flash Lite text-Lq (**24.9%** / **52%**) ≈ image+Lq Flash Lite (**25.3%** / **52%**): the photo adds almost nothing once quantity language is present. L1 title-only text collapses to **37.0%** / **32%**. Leader: Gemini 3.6 Flash **22.7%** / **68%**; Qwen3.5-Flash **23.2%** / **66%** (slow); DeepSeek v4 Flash **23.5%** / **62%** (cheap, text-only model finally useful). Still far from gram-rich text (~6%). Hard-tail ±20% opens under stronger models (Qwen **60%**, Gemini/DeepSeek/Claude **40%**) vs 0% under image L0/L1/Lq Flash Lite. **N5k-50 text Lq** (Flash Lite + DeepSeek): text-only is **much worse** than image+Lq: Flash Lite **52.0%** / **12%** vs image+Lq **27.6%** / **34%**; DeepSeek **37.3%** / **34%**. Coarse bucket+ingredient notes without the photo invent scale badly; JFB’s “photo ≈ redundant” finding does not transfer. See § Text-only vague-quantity bake-off.
21. **Meal constituents gate PASS with client normalize (2026-07-31).** Optional `constituents[]` ships with bounded post-process reconcile. Strong Gemini 3.6 Flash clears raw; cheap Gemini 3.5 Flash Lite clears after normalize. See § Meal constituents gate.
22. **Gemini 3.7 Flash: +14pp ±20% on real-meal image+Lq, no WMAPE gain (2026-08-14).** Paired `compact` runs on the Lq manifests: JFB-50 image+vague-note **25.2%** / ±20% **66%** vs Flash Lite 25.3% / 52%: the best image+note ±20% in this doc, nearly matching the 3.6 Flash text-only ceiling (22.7% / 68%). N5k-50: **29.1%** / **34%** vs 27.6% / 34%: no win (likely noise). Hard-tail JFB opens 0% → **20%** (2/10). Costs: ~4× Flash Lite ($0.09 vs $0.03 per 50), 2.6× slower (5.5 s vs 2.1 s), ~14× completion tokens (751 vs 55: reasoning model, ~2.7k reasoning tokens/5 samples). See § Photo-adjacent entry matrix.
23. **Excursory: full macro+micro per-constituent breakdown works on both gate models (2026-08-14).** New `production_text_constituents_micro` prompt (per-constituent rows carry all 21 micros; micro sums allowed ±20% vs the ±5% grams/macro gate), scored by `score_constituents_micro.py` with approximate FNDDS name-match micro GT. Gemini 3.7 Flash: micro presence **100%** (41/41 constituents), micro-sum reconcile **100%**, FNDDS-matched micro WMAPE **35.8%** (98% match). Flash Lite: presence 93%, reconcile 92%, micro WMAPE **63.4%** (93% match). Macro gate unaffected: both still 100% parse / 100% grams+macros reconcile, WMAPE 18.1% / 17.5%. 3.7 Flash is **~2× better** at constituent micro accuracy; neither regresses the shipped macro-only gate. See § Meal constituents gate → excursory micro extension.

---

## Meal constituents gate (2026-07-31): PASS with client normalize

**Status: PASS (WIP → ship).** Upstream [#154](https://github.com/apoorvdarshan/fud-ai/issues/154): optional `constituents[]` on composite text meals. Strong Gemini 3.6 Flash already cleared every check raw; cheap Gemini 3.5 Flash Lite clears after bounded client post-process (`reconcile_constituents.py`, `MAX_REL_ERROR=0.50`) that preserves top-level nutrition and scales or drops the optional breakdown. Prompt rule tightened to ±5% to match the scorer. Free Gemma still regresses WMAPE slightly and is **not** the production cheap gate model.

| Artifact | Path |
|----------|------|
| Manifest | `manifest/eval_constituents_text.jsonl` (n=16; 12 original + burrito/side, porridge, steak/fries/beer, single apple) |
| Candidate prompt | `production_text_constituents` in `prompts.py` (±5% reconcile rule) |
| Reconciler | `reconcile_constituents.py` |
| Scorer / gate | `score_constituents.py` (applies reconciler before reconcile metrics) |
| Offline pass summary | `results/constituent_gate_normalize_2026-07-31/gate_summary.json` |
| Raw prior runs | `results/constituent_gate_2026-07-31/`, `results/constituent_gate_flashlite_2026-07-31/` |

**Criteria:** parse ≥95%; candidate WMAPE ≤ baseline +2pp; min-components coverage ≥90% strong / ≥75% cheap; grams+macro reconcile within 5% on ≥95% of samples that emit constituents (after normalize).

| Run | Model | Prompt | parse | WMAPE | min-comp | grams recon | macros recon |
|-----|-------|--------|------:|------:|---------:|------------:|-------------:|
| baseline strong | `google/gemini-3.6-flash` | `production_text` | 100% | 13.6% | — | — | — |
| candidate strong | same | `production_text_constituents` | 100% | **12.2%** | **100%** | **100%** | **100%** |
| baseline cheap | `google/gemini-3.5-flash-lite` | `production_text` | 100% | 11.4% | — | — | — |
| candidate cheap (+normalize) | same | `production_text_constituents` | 100% | **10.6%** | **100%** | **100%** | **100%** |

**Verdict: PASS.** Ship `constituents` into Android/PWA with the same bounded reconciler. Keep free Gemma out of the production cheap gate (raw WMAPE 17.7% vs 15.5%+2pp).

### Excursory extension: full macro+micro per constituent (2026-08-14)

Prompt variant `production_text_constituents_micro` asks each `constituents[]` row to carry the **full 21-micro breakdown** (not just macros), with micro sums allowed ±20% vs meal (looser than the ±5% grams/macro gate: micros are noisier per item). Scored by `score_constituents_micro.py`, which adds per-constituent micro presence, per-micro sum-vs-meal reconcile, and **approximate micro accuracy** against USDA FNDDS per-100g values via token-overlap name matching (coverage reported; match is approximate, no product claim). Runs: `results/gemini37_constituents_micro/`, `results/gemini35lite_constituents_micro/` (same n=16 manifest).

| Run | Model | micro presence | micro-sum reconcile | FNDDS match | FNDDS micro WMAPE | macro WMAPE | grams/macros recon |
|-----|-------|---------------:|--------------------:|------------:|------------------:|------------:|-------------------:|
| strong | `google/gemini-3.7-flash` | **100%** (41/41) | **100%** | 98% | **35.8%** | 18.1% | 100% / 100% |
| cheap | `google/gemini-3.5-flash-lite` | 93% (40/43) | 92% | 93% | 63.4% | 17.5% | 100% / 100% |

**Excursory verdict:** the full micro breakdown is emission-reliable on both models (≥93% presence, ≥92% sums reconcile) and **does not regress the shipped macro-only gate**. Gemini 3.7 Flash is ~2× better at actual per-constituent micro accuracy (35.8% vs 63.4% blended WMAPE on FNDDS-matched items): if a future per-ingredient micro UI ever ships, the strong model carries it; Flash Lite micros are presence-plausible but quantitatively weak. Not a gate; no schema ship without a product decision on per-ingredient micros (per-ingredient micro GT from USDA matching remains an unstarted follow-up).

**Next work:** grouped-row result-sheet UX + g/unit + versioned diary/sync/meal-share parity.

Reproduce:

```bash
OUT=docs/benchmarks/food_accuracy/results/constituent_gate_YYYY-MM-DD
MANIFEST=docs/benchmarks/food_accuracy/manifest/eval_constituents_text.jsonl
# baseline + candidate × strong/cheap, then:
uv run --with httpx python docs/benchmarks/food_accuracy/score_constituents.py \
  --manifest "$MANIFEST" \
  --strong "$OUT/candidate_strong/samples.jsonl" \
  --weak "$OUT/candidate_weak/samples.jsonl" \
  --baseline-strong "$OUT/baseline_strong/samples.jsonl" \
  --baseline-weak "$OUT/baseline_weak/samples.jsonl" \
  --out "$OUT/gate_summary.json"
```

---

## Failure modes & portion reasoning

Per-sample analysis on JFB 50 L0, mean WMAPE across five compact runs: Gemini 3.6 Flash, GPT-4o mini, Gemini 3.5 Flash-Lite, Gemma 26B :free, `nofud/free` cold. Text reference: Gemma compact on `eval_text.jsonl`.

### Headline

| Signal | Value |
|--------|------:|
| Hardest meal mean WMAPE | **179%** (Breakfast Scramble, 226 kcal GT) |
| Easiest meal mean WMAPE | **6.5%** (bacon & cheese omelette) |
| Never within ±20% kcal on any of 5 models | **8 / 50** |
| Always within ±20% on all 5 | **1 / 50** |
| corr(GT kcal, mean WMAPE) | ≈ **0** |
| corr(ingredient count, mean WMAPE) | ≈ **0.21** (weak) |

Difficulty is **not** “big meals.” Hard and easy cohorts share ~same mean calories; hard examples look restaurant-like with small logged GT portions, or denseness the camera understates.

### Image failure modes

| Mode | Typical examples | What models do |
|------|------------------|----------------|
| **Restaurant-portion overestimate** (dominant) | Breakfast Scramble, sandwich+fries, breakfast platter | Invent diner-scale plates / sides (home fries, bread) not in GT; +100–200% kcal |
| **Hidden-calorie underestimate** | Baba Ganoush, Apple Pie | Oil/tahini denseness or whole pie vs slice; all models ≈ −65% to −80% kcal |
| **Busy multi-item trays** | Asian fried-chicken tray, spring-roll plate, veg toast | ID roughly right; portion grounding fails. L1 meal title / L2 ingredient names do not rescue |

**Model bias (signed kcal, same 50):** free Gemma / nofud / Gemini tend to **overestimate** (+17–31% mean); GPT-4o mini is the mild **underestimator** (−6%) and sometimes cracks hard trays others miss.

**Easiest plates:** clear single-subject breakfasts (omelette, simple avocado toast + eggs) whose visible amount matches restaurant priors.

### Text (for contrast)

Even “hard” text is mild vs plates (max ~41% WMAPE): ambiguous commercial servings (hummus 2 tbsp, whey shake, sushi roll) and low-kcal items where absolute misses look large in %. Canonical USDA singles with grams/cups are often **exact** (0% WMAPE).

### Portion-aware prompt experiment (rejected for shipping)

Hypothesis: short rules (“estimate only visible food”, “don’t invent sides”, “include thin oil/dip calories”, “honor stated tbsp/scoop”) would cut the hard tail without the latency of full production prompts.

| Run | Model | prompt | WMAPE | ±20% kcal |
|-----|-------|--------|------:|----------:|
| baseline | `google/gemini-3.5-flash-lite` | `compact` | **35.9%** | **40%** |
| candidate | same | `compact_portion` | 37.2% | 36% |

Hard-tail effect was mixed (some overestimates shrank; others grew; Baba Ganoush still badly undershot). **Not shipped** to `FoodAnalysisService`. Harness keeps `compact_portion` research-only.

### Product implication

Prompt/model A/B alone will not move the 8 “never ±20%” meals much. Next bets for **real** entry (photos, typed text, product names) are outside more prompt text:

- Portion grounding via UX (stated grams/units, reference objects) or retrieval/priors
- Stronger VL where paid ceiling still sits ~32% WMAPE
- Treat text/product-name path as largely solved for canonical foods; invest vision effort in **scale**, not identification

Artifacts: `results/image_text_ab/l0_*`, `l0_gemini35_flash_lite_compact_portion/`.

### Reference-object / scale-anchor prompting (2026-07-28)

Hypothesis: unlike `compact_portion` (visibility/invention rules), give the model
an explicit **size anchor**: a visible reference object, or a standard dinner
plate (~26cm) / bowl (~15cm) fallback when nothing else is visible, since the
dominant failure mode is portion scale, not what to count. New prompt
`compact_scale_ref` in `docs/benchmarks/food_accuracy/prompts.py`. Both runs
re-executed together (JFB-50, `google/gemini-3.5-flash-lite`) rather than reusing
an older baseline row, to avoid cross-run noise.

| Run | prompt | WMAPE | ±20% kcal | parse_ok | cost (50 items) |
|-----|--------|------:|----------:|---------:|-----------------:|
| baseline (rerun) | `compact` | 35.9% | 38% | 100% | $0.0245 |
| candidate | `compact_scale_ref` | **34.4%** | **42%** | 100% | $0.0261 |

WMAPE −1.5pp, ±20% +4pp, no parse-rate cost, negligible token/cost increase (~6%
more prompt tokens for the extra rule text). Positive but small: well below the
±8pp WMAPE bar used to graduate the clarification chip, and on the same order as
noise between `compact` reruns elsewhere in this doc (e.g. `compact` baseline is
recorded as both 38% and 40% ±20% across runs). **Verdict: mild, real improvement
in the right direction, but not large enough alone to justify a production prompt
change.** Reasonable next step if pursued further: combine with the shipping
portion-clarification chip (Bet 1) rather than as a standalone lever, since the
two attack the same failure mode from different angles (chip = ask; this = infer)
and might be complementary: not tested here. See
`docs/UNCERTAINTY_DRIVEN_ENTRY.md` § New candidates (2026-07-28 brainstorm).

Artifacts: `results/scale_ref_ab/jfb_compact_rerun/`, `results/scale_ref_ab/jfb_compact_scale_ref/`.

---

## Post-hoc calibration & ensembling (2026-07-29)

Everything in this section is re-scored from **already-stored** `results/*/samples.jsonl`
predictions: no new API calls, $0. Reproduce with
`uv run python docs/benchmarks/food_accuracy/posthoc_calibration.py` (sections A–G).
All rows are JFB-50 L0 `compact`, 50/50 common ids, unless noted.

### The finding: every vision model overestimates, and the bias is correctable

Fitting one multiplicative scale per macro per model (prediction-weighted median
ratio, **leave-one-out cross-validated** so no sample sees its own fit):

| Model | fitted kcal scale | WMAPE | Δ | ±20% kcal | Δ |
|---|---:|---:|---:|---:|---:|
| `qwen/qwen3.5-flash` | 0.800 | **29.62%** | −7.50pp | **58%** | +22pp |
| `openai/gpt-5-mini` | 0.808 | 32.14% | −7.41pp | 50% | +18pp |
| Gemma 26B :free | 0.759 | 33.28% | −6.54pp | 40% | +8pp |
| `nofud/free` | 0.775 | 35.51% | −5.60pp | 40% | +8pp |
| `openai/gpt-5-nano` | 0.765 | 37.86% | −5.90pp | 30% | +2pp |
| Gemini 3.5 Flash-Lite | 0.814 | 31.44% | −4.45pp | 50% | +10pp |
| **Gemini 3.6 Flash** | 0.837 | **31.02%** | −1.32pp | 52% | +2pp |
| `gpt-4o-mini` | 1.053 | 34.31% | −0.17pp | 46% | −4pp |
| Claude 3 Haiku | 0.993 | 38.71% | +0.79pp | 40% | 0pp |
| Claude Haiku 4.5 | 0.924 | 41.85% | +1.40pp | 30% | −8pp |

Ten of twelve runs have a fitted kcal scale **below 1.0** (0.76–0.84 for most): the +100–200% restaurant-portion mode documented under [Failure modes](#failure-modes--portion-reasoning)
is not just a tail, it is a **systematic multiplicative bias**. `gpt-4o-mini`
(1.053) is the known mild underestimator; calibrating it or Claude does nothing
or hurts, so any correction must be **per model, never global**.

**Modality gate is mandatory.** Text fits are exactly **1.000** on every macro
(`TEXT42 gemma_free`, `TEXT42 flashlite`): the shrink is a genuine *vision
portion* bias, not a ground-truth normalisation artifact. Applying the JFB photo
scale to the text split destroys it (WMAPE 5.71% → 23.46%, ±20% 90% → 14%).

### But the magnitude does not transfer across datasets

| Fit on → apply to | WMAPE | ±20% kcal |
|---|---|---|
| JFB-50 → N5k-15 (same free Gemma) | 34.72% → 36.52% (+1.80pp) | 40% → **20%** (−20pp) |
| N5k-15 → JFB-50 | 39.83% → **34.05%** (−5.77pp) | 32% → 40% (+8pp) |
| JFB even-half → odd-half (Flash-Lite) | 37.17% → 34.91% (−2.27pp) | 44% → 44% |
| JFB odd-half → even-half (Flash-Lite) | 34.32% → **27.33%** (−7.00pp) | 36% → 56% |

Within one dataset the correction always helps; across datasets only the
*direction* survives, not the size (N5k's own kcal scale is 0.855 vs JFB's
0.759). A uniform-factor sweep across 9 runs bottoms out around **0.85–0.90**
(mean WMAPE 36.99% → 33.36%), but 0.85 actively hurts the two non-overestimating
models. **Verdict: real and large, but do not hard-code a constant from JFB.**
The shippable form is a *conservative* per-model factor (≈0.90 for the
overestimators, 1.00 for `gpt-4o-mini`/Claude), or better, learn it per user from
`GroundingCorrectionStore` edits, which is exactly [`UNCERTAINTY_DRIVEN_ENTRY.md`](UNCERTAINTY_DRIVEN_ENTRY.md)
Bet 3, now with a measured ceiling instead of a hunch.

### Cross-model ensembling: best plate numbers in the record

Median of N independent models per sample:

| Ensemble | WMAPE | ±20% kcal |
|---|---:|---:|
| best single (Gemini 3.6 Flash) | 32.33% | 50% |
| gpt-4o-mini + Qwen3.5-Flash | **29.44%** | 56% |
| Gemini 3.6 Flash + gpt-4o-mini | 29.66% | **62%** |
| Gemini 3.6 + gpt-4o-mini + Claude 3 Haiku + Qwen3.5 | **29.05%** | **64%** |
| median-then-calibrate (Gemini 3.6 + gpt-4o-mini + Qwen3.5) | **27.01%** | 60% |

Two models beat every single model tested, and pairing a documented
overestimator with the documented underestimator (Gemini + gpt-4o-mini) lifts
±20% accuracy by **12pp**: the largest photo gain in this doc outside the
portion-clarification oracle (−15.2pp WMAPE). Calibration and ensembling stack:
**27.01% WMAPE / 60% ±20%** is the best plate result recorded here. Cost is N×
API calls and max-latency, so this is a "high accuracy" toggle, not a default;
free-tier-only ensembling does **not** work (`gemma_free + nofud_free` = 38.16%,
no better than either alone: they share a bias, so the median has nothing to
cancel).

### Three negative results that close open questions

1. **Self-consistency does not work.** Three independent runs of Flash-Lite
   `compact` on identical inputs: singles 35.89% / 35.93% / 37.98%, median of the
   three **37.61%**, mean 36.34%: no better than one call. The error is *bias*,
   not sampling variance, so re-sampling the same model buys nothing. Only
   *different* models cancel. Don't build a retry-and-average path.
2. **`serving_size_grams` is not a confidence signal.** corr(predicted grams,
   true kcal) is +0.005 to +0.31 across all twelve runs (median ≈ +0.14). Models
   emit a plausible-looking mass that barely tracks reality. This matters for
   Bet 1: the proposed heuristic trigger *"show the portion chip when the model
   didn't return a confident `serving_size_grams`"* has no signal behind it: **trigger the chip on every photo entry instead.**
3. **Model disagreement is not an uncertainty signal either.** Splitting JFB-50
   by cross-model spread, the high-disagreement half is *not* less accurate
   (mean |kcal err| 29.4% vs 33.5%; ±20% hit 48% vs 52%), corr(spread, error)
   = **+0.012**. Combined with the 92%-ask-rate result, nothing the models
   produce (self-report, emitted grams, or mutual agreement) tells the app when
   it is wrong. Bet 2's calorie bands must be **fixed-width by entry type**, not
   confidence-scaled.

### Also recorded here: two undocumented model runs

`results/image_text_ab/` contains two JFB-50 L0 `compact` runs (2026-07-22) never
added to the tables below: `openai/gpt-5-mini` WMAPE **39.54%** / ±20% **32%**,
and `anthropic/claude-haiku-4.5` WMAPE **40.45%** / ±20% **38%**. Both land in
free-Gemma territory and neither changes the paid ranking. (`l0_qwen36_flash` is
an abandoned partial run, n=34, no summary: ignore it.)

---

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

## Simulated clarification eval (pre-registered 2026-07-24)

Strategy doc: [`UNCERTAINTY_DRIVEN_ENTRY.md`](UNCERTAINTY_DRIVEN_ENTRY.md). Since prompt A/B is exhausted and the dominant image failure modes are portion (+100–200%) and hidden fat (−65–80%), this eval measures the **ceiling** of a one-tap clarification UX before building it: inject oracle answers (from GT) into the `compact` prompt as if the user tapped a chip.

Harness: `clarify.py` (oracle derivation), `build_clarify_manifests.py` (enriched `*_clarify.jsonl` + covered-id lists), prompts `compact_clarify_{portion,portion_grams,portion_bucket,portion_amounts,fat,both,ask}`, `run_clarify_eval.py` (two-stage ask-then-answer: ask_rate / answered_rate / stage-1 vs final). Stage-0 smoke covers all paths with the stub provider. Oracle coverage on current local data: JFB 50/50 portion (stated ingredient amounts: no total mass in JFB), fat 50/50 (present 24/50); N5k 15/15 portion (true grams + bucket), fat 15/15 (present 6/15, lexicon likely misses cooking oil: treat N5k fat condition as weak).

### Signal split (2026-07-29): do not conflate chip labels with mass oracles

The historical `compact_clarify_portion` prompt injects the **richest** available oracle per sample. That mixed two different product shapes:

| Prompt / signal | What the model sees | Dataset coverage | Product analogue |
|-----------------|---------------------|------------------|------------------|
| `compact_clarify_portion` (legacy) | grams+bucket **or** stated ingredient amounts | N5k / JFB | Mixed: not chip-only |
| `compact_clarify_portion_grams` | exact total edible grams only | N5k (`mass_g`) | Exact-weight field / deterministic rescale |
| `compact_clarify_portion_bucket` | `small` / `regular` / `large` / `restaurant-size` only | N5k (bucket from mass) | Qualitative size chips |
| `compact_clarify_portion_amounts` | per-ingredient qty + unit | JFB ingredient lists | Typed amounts / ingredient rows |

**Important:** the published JFB −15.2 pp WMAPE / +12 pp ±20% result used **stated ingredient amounts**, not the four size-chip labels. The N5k −18.7 pp confirmation used **true total mass**. Neither run isolates bucket-only chips. Until a paired `compact_clarify_portion_bucket` A/B clears the pre-registered thresholds, ship **exact-weight correction** as the validated default path and keep qualitative chips opt-in / soft UX.

```bash
# Split-signal A/B (N5k for grams/bucket; JFB for amounts)
uv run python docs/benchmarks/food_accuracy/build_clarify_manifests.py --manifests n5k jfb
for P in compact compact_clarify_portion_grams compact_clarify_portion_bucket; do
  uv run python docs/benchmarks/food_accuracy/run_eval.py \
    --provider openrouter --model google/gemini-3.5-flash-lite \
    --prompt "$P" --sleep 3 --retries 2 \
    --manifest docs/benchmarks/food_accuracy/data/manifests/n5k_clarify.jsonl \
    --ids "$(paste -sd, docs/benchmarks/food_accuracy/data/manifests/n5k_clarify_ids_portion_grams.txt)" \
    --out docs/benchmarks/food_accuracy/results/clarify_ab/n5k_${P}
done
```

**Pre-registered decision thresholds** (set before any paid run; JFB-50 L0 covered ids, Gemini 3.5 Flash-Lite as the stable pin, baseline `compact` = 35.9% WMAPE / 40% ±20%):

- **Ship-investigate**: `compact_clarify_both` improves WMAPE by **≥8 pp absolute** AND ±20% kcal by **≥10 pp** → clarification chips graduate to an Android UX design.
- **Park**: 3–8 pp WMAPE gain → re-test on N5k grams-oracle (cleaner portion signal) before deciding.
- **Kill**: <3 pp → the chips bet dies like `compact_portion`; remaining bets are ranges + correction memory.
- Two-stage sanity: ask_rate in 20–80% and error reduction concentrated on asked items; otherwise the model can't self-detect uncertainty and any UI would need heuristic triggers instead.
- **Bucket-only gate (2026-07-29):** enable qualitative chips by default only if `compact_clarify_portion_bucket` clears the same ≥8 pp WMAPE / ≥10 pp ±20% bar vs `compact` on N5k covered ids. Exact grams does not need that gate: mass is information-theoretically sufficient and N5k already confirmed the grams oracle.

Caveats registered up front: JFB portion oracle (stated amounts) is a stronger hint than a grams chip; JFB and N5k results are reported per-dataset, never pooled; fat answers stay qualitative to avoid leaking the fat macro.

### Results (2026-07-24, JFB-50, `google/gemini-3.5-flash-lite`): legacy mixed portion oracle (stated amounts on JFB)

Baseline `compact`: WMAPE 38.0%, ±20% kcal 38%.

| Condition | WMAPE | Δ vs baseline | ±20% kcal | Δ vs baseline |
|-----------|------:|---------------:|----------:|---------------:|
| `compact_clarify_portion` | **22.8%** | **−15.2 pp** | **50%** | **+12 pp** |
| `compact_clarify_fat` | 32.8% | −5.2 pp | 36% | −2 pp |
| `compact_clarify_both` | 24.0% | −14.0 pp | 48% | +10 pp |

Two-stage ask-then-answer (`run_clarify_eval.py`, same manifest/pin): ask_rate **92%** (near-universal, poor discrimination), asked-type split **portion 12 / added_fat 34 / none 4**: the model preferred asking the *weaker* question 3× more often than the stronger one. Final WMAPE 31.3% (worse than the portion-oracle ceiling, because most asks were fat). answered_rate 100%, parse_ok 98%.

**Decision:**

- **Exact-weight / stated-amount clarification: SHIP.** Clears both pre-registered thresholds; Android ships optional pre-analysis total grams + post-analysis exact-grams correction (deterministic rescale) on every photo entry.
- **Qualitative size chips (bucket-only): OPT-IN until split A/B.** The published ceiling did **not** isolate chip labels; keep chips behind the portion-clarify setting / soft UX until `compact_clarify_portion_bucket` clears the gate above.
- **Hidden-fat clarification: park.** −5.2pp WMAPE misses the ship-investigate bar and it slightly *hurt* ±20% accuracy: consistent with the pre-registered caveat that JFB's fat lexicon is thin and the answer stays qualitative. Not worth a dedicated chip on this evidence; revisit only with a stronger fat-oracle dataset.
- **Model self-selects which question to ask: not usable as a trigger.** Ask rate and question choice are both poor (over-asks, and prefers the weaker lever). The portion row's trigger is **every photo entry**, not model self-reported uncertainty.

**N5k confirmation** (2026-07-24, loose: n=15, true-mass oracle, no lexicon dependency): baseline `compact` WMAPE 34.4% / ±20% 13.3% → `compact_clarify_portion` WMAPE **15.6%** (**−18.7pp**) / ±20% **66.7%** (**+53.4pp**). Direction and magnitude confirm JFB, if anything stronger since the oracle is true mass rather than JFB's stated-ingredient-amount proxy. Small n: treat as confirmatory, not a replacement for a larger N5k run. This is the evidence base for shipping exact grams, not for claiming bucket chips alone.

Artifacts: `results/clarify_ab/jfb_{compact,compact_clarify_portion,compact_clarify_fat,compact_clarify_both,two_stage}/` (gitignored).

---

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

## Defaults going forward

| Decision | Choice | Why |
|----------|--------|-----|
| Free router | **`nofud/free`** | No content-safety; live catalog; failover |
| Avoid | `openrouter/free` | Food-JSON poison |
| Text prompt for macro research | **`compact`** | Best WMAPE + latency on Gemma |
| Image prompt for macro research | **`compact`** | Best WMAPE + parse + latency on Gemma JFB A/B |
| App-parity prompt | `production_text` / `production_image` (= lean, 2026-07-24) | Only when testing schema/units transfer; `legacy_production_image` for the old wording |
| Text / image pin (stable A/B) | `google/gemma-4-26b-a4b-it:free` | Fast, accurate, vision-capable |
| Image pacing | `--sleep 15`+ and `--retries 3`+ | Free-tier per-minute limits (image) |
| Image+text eval | L0/L1/L2 via `download_jfb.py` / `download_nutrition5k.py` (L2) / `download_acetada.py` / `download_nutritionverse_real.py` | Paired A/B; `text` = user note; ACETADA/NV = NC research only |

---

## Photo-adjacent entry matrix (2026-07-30)

Four entry methods on shared meal IDs: image only (**L0**), meal title (**L1**),
vague quantity diary note (**Lq**), and qualitative size chips (**bucket**).
Builder: [`build_image_text_lq.py`](benchmarks/food_accuracy/build_image_text_lq.py).
Summarizer: [`summarize_entry_matrix.py`](benchmarks/food_accuracy/summarize_entry_matrix.py).
Hard-tail list: [`manifest/jfb_hard_ids.txt`](benchmarks/food_accuracy/manifest/jfb_hard_ids.txt).
Artifacts: `results/entry_matrix/` (gitignored).

Pin: `google/gemini-3.5-flash-lite`, `compact` (bucket uses `compact_clarify_portion_bucket`).
JFB L0 reuses the prior Flash Lite L0 artifact (`image_text_ab/l0_gemini35_flash_lite`).
JFB has **no** `mass_g`, so bucket chips are N5k-only (0/50 JFB bucket coverage).

### JFB-50 (phone meals)

| Condition | WMAPE | ±20% kcal | parse | Hard-tail WMAPE† | Hard ±20% |
|-----------|------:|----------:|------:|-----------------:|----------:|
| L0 image only | **35.9%** | **40%** | 100% | 91.9% | 0% |
| L1 meal title | 33.0% | 36% | 100% | 72.1% | 0% |
| Lq vague qty note | **25.3%** | **52%** | 100% | **54.2%** | 0% |

† Hard-tail = 10 IDs in `jfb_hard_ids.txt` (8 never-±20% across five prior L0 models + Breakfast Platter + Asian fried-chicken tray). Mean of per-sample macro MAPEs (not harness WMAPE).

**Takeaway:** L1 identity help is small / mixed (±20% actually dips). **Lq** (−10.6pp WMAPE, +12pp ±20% vs L0) is the first user-note condition that clearly beats image-only on JFB under Flash Lite. Hard plates remain unsolved on ±20%.

### Nutrition5k-50 (lab overhead)

| Condition | WMAPE | ±20% kcal | parse |
|-----------|------:|----------:|------:|
| L0 image only | 32.6% | 24% | 100% |
| L1 coarse identity | 29.6% | 28% | 100% |
| Lq vague qty note | **27.6%** | **34%** | 100% |
| Bucket chips | 28.7% | 32% | 100% |

**Takeaway:** On N5k n=50, notes help (unlike free-Gemma JFB L1). Lq ≥ bucket ≥ L1 > L0. Bucket-only (−3.9pp WMAPE / +8pp ±20% vs L0) is real but **below** the portion-clarify grams/amounts ceiling and does **not** beat Lq: keep qualitative chips soft/opt-in; prefer prompting users toward quantity language (or exact grams) in the note / chip answer. Exact-grams clarify remains the stronger Bet 1 signal.

### Product implication

- Encourage **quantity language** in photo notes (even vague: “large plate”, “a couple eggs”): not just meal titles.
- Bucket chips remain optional until a stronger signal (or unpaid replicate) justifies default-on; Lq-style free text already captures similar gains.
- Hard-tail still needs exact portion UX (grams / amounts chips), not notes alone.

---

## Text-only vague-quantity bake-off (2026-07-31)

Typed diary entry with **vague quantity language** (no exact grams, no photo): the common “I typed what I ate with fuzzy amounts” path. Manifests:
[`jfb_text_lq.jsonl`](benchmarks/food_accuracy/data/manifests/jfb_text_lq.jsonl) /
[`jfb_text_l1.jsonl`](benchmarks/food_accuracy/data/manifests/jfb_text_l1.jsonl)
from [`build_text_lq.py`](benchmarks/food_accuracy/build_text_lq.py) (clones of
image Lq/L1 strings with `modality=text`). Prompt `compact`. Artifacts:
`results/text_lq_bakeoff/` (gitignored).

### JFB-50 multi-model Lq (+ L1 control)

| Condition | Model | WMAPE | ±20% kcal | parse | Hard WMAPE† | Hard ±20% | mean lat | cost |
|-----------|-------|------:|----------:|------:|------------:|----------:|---------:|-----:|
| Text Lq | `google/gemini-3.6-flash` | **22.7%** | **68%** | 100% | 41.2% | 40% | ~4.4 s | $0.32 |
| Text Lq | `qwen/qwen3.5-flash-02-23` | 23.2% | 66% | 100% | 40.8% | **60%** | ~27 s | $0.08 |
| Text Lq | `deepseek/deepseek-v4-flash-0731` | 23.5% | 62% | 100% | **37.9%** | 40% | ~9.1 s | $0.014 |
| Text Lq | `google/gemini-3.5-flash-lite` | 24.9% | 52% | 100% | 50.6% | 20% | **~0.9 s** | **$0.008** |
| Text Lq | `google/gemma-4-26b-a4b-it:free` | 27.4% | 44% | 100% | 48.9% | 50% | ~7.8 s | $0 |
| Text Lq | `openai/gpt-4o-mini` | 27.5% | 48% | 100% | 50.2% | 20% | ~1.3 s | $0.002 |
| Text Lq | `anthropic/claude-3-haiku` | 28.4% | 46% | 100% | 46.8% | 40% | ~1.1 s | $0.006 |
| Text L1 (control) | `google/gemini-3.5-flash-lite` | 37.0% | 32% | 100% | 70.6% | 10% | ~0.9 s | $0.008 |
| *Image+Lq (prior)* | *Flash Lite* | *25.3%* | *52%* | *100%* | *54.2%* | *0%* | — | — |

† Hard-tail = 10 IDs in `jfb_hard_ids.txt`; mean of per-sample macro MAPEs.

**Ranking (WMAPE):** Gemini 3.6 ≥ Qwen ≈ DeepSeek ≥ Flash Lite ≥ Gemma free ≈ gpt-4o-mini ≥ Claude Haiku ≫ L1 title-only.

### Takeaways

- **Photo is nearly redundant given Lq text.** Flash Lite text-only Lq matches image+Lq within noise (−0.4pp WMAPE, same ±20%). Prefer prompting quantity language in typed entry; attaching a photo on top is optional for macro accuracy when the note already carries scale.
- **Quantity language ≫ meal title** even without a photo (−12.1pp WMAPE / +20pp ±20% Flash Lite Lq vs L1).
- **DeepSeek belongs in the text BYOK slate**: text-only model that was useless for plates lands mid-pack here, cheap.
- **Qwen is accurate but latency-hostile** (~27 s mean); Flash Lite remains the latency/cost sweet spot.
- Still **~4× worse than gram-rich typed text** (~6% WMAPE). Hard plates remain hard; stronger models open hard-tail ±20% but do not clear it.

### Nutrition5k-50 text Lq (Flash Lite + DeepSeek)

Same builder strings (`{bucket} plate of {ingredients}`), no photo.

| Condition | Model | WMAPE | ±20% kcal | parse | mae kcal |
|-----------|-------|------:|----------:|------:|---------:|
| Text Lq | `deepseek/deepseek-v4-flash-0731` | **37.3%** | **34%** | 100% | 88.8 |
| Text Lq | `google/gemini-3.5-flash-lite` | 52.0% | 12% | 100% | 128.2 |
| *Image+Lq (prior)* | *Flash Lite* | *27.6%* | *34%* | *100%* | *65.7* |

**Takeaway:** Unlike JFB, N5k text-only Lq is **much worse** than image+Lq for Flash Lite (+24.4pp WMAPE). N5k notes are coarse bucket+ingredient labels (`small plate of cantaloupe`) with less diary detail than JFB’s coarsened stated amounts: without the overhead photo the model invents scale badly. DeepSeek still beats Flash Lite text (−14.7pp) and matches image+Lq ±20% (34%), but trails image+Lq WMAPE by ~10pp. JFB’s “photo ≈ redundant given Lq” finding does **not** transfer to this N5k note style.

Artifacts: `results/text_lq_bakeoff/n5k_lq_{flashlite,deepseek}/`.

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

## Suggested next runs

```bash
# Exact-grams clarify vs L0 on N5k-50 (bucket already measured)
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model google/gemini-3.5-flash-lite \
  --prompt compact_clarify_portion_grams --sleep 2 --retries 2 \
  --manifest docs/benchmarks/food_accuracy/data/manifests/n5k_clarify.jsonl \
  --out docs/benchmarks/food_accuracy/results/entry_matrix/n5k_grams_flashlite
```

---

## Related docs

| Doc | Contents |
|-----|----------|
| [`FOOD_ACCURACY_BENCHMARK.md`](FOOD_ACCURACY_BENCHMARK.md) | Datasets, metrics, CLI, `nofud/free` behavior |
| [`docs/benchmarks/food_accuracy/README.md`](benchmarks/food_accuracy/README.md) | Quick commands |
| [`ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md) | On-device smoke (latency/parse; no GT macros yet) |

Failure modes / portion reasoning for hard vs easy samples: [§ Failure modes & portion reasoning](#failure-modes--portion-reasoning) above.

## Grounded entry (WIP: not production)

Canonical status and checklist: [`GROUNDED_ENTRY.md`](GROUNDED_ENTRY.md). **`GroundedEntryFeature.ENABLED` remains false.**

### Primary text gate: realistic prompts (no grams in text)

Manifest: [`eval_grounded_realistic_text.jsonl`](benchmarks/food_accuracy/manifest/eval_grounded_realistic_text.jsonl) (38 samples: vague / household / multi / branded). Thresholds: [`grounded_realistic_text_thresholds.json`](benchmarks/food_accuracy/baselines/grounded_realistic_text_thresholds.json).

| Run (Flash Lite, 2026-07-29) | WMAPE | ±20% kcal | parse |
|------------------------------|------:|----------:|------:|
| Ungrounded `compact` | 27.3% | 71.1% | 100% |
| Grounded tool-loop + OFF fixtures | **18.5%** | **76.3%** | **100%** |

Grounded **beats** same-manifest single-shot overall. Branded slice is the clearest win (7.1% vs 120% WMAPE; 75% OFF source rate). Vague titles remain the weak slice for grounded. Artifacts: `results/*_realistic_text/` (gitignored).

### Gram-rich identity regression (`eval_text.jsonl`)

Not a ship gate: every prompt embeds mass, so single-shot parsing dominates.

| Run | WMAPE | ±20% kcal | parse |
|-----|------:|----------:|------:|
| Prior grounded tool-loop | 17.7% | 76.3% | 90.5% |
| Post-roadmap grounded (2026-07-22) | 12.8% | 78.6% | 100% |
| Portion-fidelity grounded (2026-07-29) | 10.1% | 81.0% | 100% |
| Ungrounded Flash Lite `compact` | **4.8%** | **92.9%** | 100% |

