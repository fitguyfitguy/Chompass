# Food accuracy benchmark — current state

| | |
|---|---|
| **As of** | 2026-07-24 |
| **Harness** | [`docs/benchmarks/food_accuracy/`](benchmarks/food_accuracy/) |
| **How-to** | [`FOOD_ACCURACY_BENCHMARK.md`](FOOD_ACCURACY_BENCHMARK.md) |
| **Grounded WIP** | [`GROUNDED_ENTRY.md`](GROUNDED_ENTRY.md) — **not production**; UI flag off |
| **API** | OpenRouter via `OPENROUTER_TOKEN` in `.env.local` |

This note records what we have measured so far, which defaults to use, and what is still open. It is a snapshot, not a permanent API.

---

## Harness overview

| Piece | Role |
|-------|------|
| `run_eval.py` | Main CLI: manifest → prompt → provider → MAE/MAPE/WMAPE |
| `nofud/free` | Client-side free router (preferred); excludes content-safety |
| `openrouter/free` | Stock OpenRouter router — **not for accuracy benches** |
| `manifest/eval_text.jsonl` | 42 FNDDS-derived text items (checked in) |
| `data/manifests/jfb.jsonl` | 50 JFB meal images L0 — image only (downloaded, gitignored) |
| `data/manifests/jfb_image_text_l1.jsonl` | 50 JFB — image + meal title as user note |
| `data/manifests/jfb_image_text_l2.jsonl` | 50 JFB — image + ingredient names as user note |
| Metrics | `parse_ok`, WMAPE on {kcal, protein, carbs, fat}, ±20% kcal rate |

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
7. **Lab overhead (Nutrition5k) is only mildly easier than phone meals.** Cursory n=15 overhead RGB + Gemma compact: WMAPE **34.7%**, ±20% **40%**, mae kcal **80**, parse **100%**. Better than JFB (~40% WMAPE) but still far from text (~6%). Error is not “messy phone photo” alone — portion/macro estimation stays hard even with clean top-down plates.
8. **Simple JFB descriptions do not help Gemma compact macros.** Paired L0/L1/L2 on JFB 50 (pinned Gemma 26B, `compact`): L0 image-only WMAPE **41.8%** / ±20% **28%** beats L1 meal title (**44.9%**) and L2 ingredient names (**45.8%** / ±20% **22%**). All 100% parse.
9. **`production_image` + L1 meal title still loses.** Same Gemma pin: WMAPE **47.3%**, parse **96%**, ±20% **23%**, ~15 s — worse than L1 compact (44.9%) and L0 compact (41.8%). App-parity prompt does not rescue short-note context on free Gemma.
10. **Paid VL ceiling is better but still hard.** Best so far: `google/gemini-3.6-flash` L0 compact — WMAPE **32.3%**, ±20% **50%**, mae kcal **123**, ~5 s. Then gpt-4o-mini **34.5%**, Gemini 3.5 Flash-Lite **35.9%**. Cheap multi-provider adds: Qwen3.5-Flash **37.1%**, Claude 3 Haiku **37.9%**, GPT-5 Nano **43.8%** (≈ free). Still far from text (~6%).
11. **Cold `nofud/free` image baseline is solid.** Fresh L0 run: parse **100%**, WMAPE **41.1%**, ±20% **32%** — matches pinned Gemma (~41.8%) within noise; no content-safety fails.
12. **No OpenRouter `gemini-3.6-flash-lite`.** Lite sibling is `google/gemini-3.5-flash-lite`; full Flash is `google/gemini-3.6-flash`.
13. **Plate error is portion priors, not meal size.** Consensus hard vs easy JFB meals have nearly identical mean GT kcal (~395 vs ~391). Short “portion grounding” prompt rules did **not** beat compact (see [Failure modes](#failure-modes--portion-reasoning)).
14. **DeepSeek Flash / GPT-5.6 Luna not useful for this plate slate.** DeepSeek models on OpenRouter are **text-only** (no vision). Luna is ~$1/$6 input/output — not in the cheap tier; skipped for cost.
15. **The old production-prompt gap was rule verbosity, not schema size — lean production shipped (2026-07-24).** A "lean" prompt (full 28-field app schema, compact wording + one-line unit_options rule carrying the object shape, `lean_units2`) matches compact-level text macros while keeping micros/emoji/units: Flash-Lite text WMAPE **5.3%** (old production 6.9%, compact 4.8%) and image **31.25%** (old production 31.1%, compact 35.9%). The Gemma image "production is 8pp worse" finding was a Gemma artifact — on Flash-Lite the verbose image prompt was actually *better* than compact; lean keeps that win at half the prompt tokens. Bonus: the old text prompt never elicited `grams_per_unit`, so **every AI text serving unit was silently dropped by the app parser**; lean fixes this (40/41 usable vs 0). Shipped to `FoodAnalysisService` (all four entry prompts) and mirrored in `prompts.py` `production_*`. See § Lean production prompt (2026-07-24).

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
| image `lean_full` | Flash-Lite | 31.2% | 46% | 100% | no — bare strings |
| image `lean_units` | Flash-Lite | 33.0% | 40% | 100% | partial (no gpu) |
| image `lean_units2` **(shipped)** | Flash-Lite | **31.25%** | 46% | 100% | **51/51 sane with gpu** |
| text `lean_full` | Gemma 26B :free | 5.3% | 92.7% | 97.6% | — |
| image `lean_full` | Gemma 26B :free | 41.8% | 25% | 96% | — (old production: 47.8%) |
| image `lean_units2` | Gemini 3.6 Flash | 33.2% | 42% | 100% | — |
| image `legacy_production_image` | Gemini 3.6 Flash | 32.5% | **52%** | 100% | — |

Micros present ≥98%, emoji 100% on the shipped variant (both modalities). **Open wrinkle:** on the app-primary Gemini 3.6 Flash, the legacy image wording beat lean on ±20% kcal (52% vs 42%, n=50 single run; WMAPE within 0.7pp) — worth a paired re-run before treating that delta as real. Artifacts: `results/lean_prompt_ab/` (gitignored).

Harness fixes landed alongside: `schema.py`/`env_local.py` ROOT was still `parents[2]` from the pre-`docs/` layout (broke `.env.local` key loading and repo-relative manifest paths; image paths in downloaded manifests resolve via a `docs/` fallback), and the smoke script's `query_normalize` import used the old package path.

## Results tables

Lower WMAPE is better. `parse_ok` and within-20% are higher-is-better. Free-tier rate limits (429) and upstream 502s inflate “fail” rates on some pin runs — infra-adjusted notes below.

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
| Image+text eval | L0/L1/L2 JFB manifests via `download_jfb.py` | Paired A/B; `text` field = user note |

---

## Gaps / not done yet

- [x] Image **prompt A/B** on pinned Gemma (`compact` vs `production_image` vs `fewshot_units`) — compact wins; longer prompts hurt
- [x] Image + **description A/B** on JFB 50 (L0/L1/L2, Gemma compact) — L0 image-only wins; L1/L2 do not improve WMAPE
- [x] Full **fresh** 50-image run with `nofud/free` from cold start — parse 100%, WMAPE 41.1% (≈ Gemma pin)
- [x] Nutrition5k overhead RGB **cursory** (n=15, Gemma compact) — WMAPE ~35%; lab plates still hard
- [x] Image+text with **`production_image`** on L1 — worse than L1 compact (47.3% vs 44.9% WMAPE)
- [x] Paid VL ceiling (`gpt-4o-mini` L0) — WMAPE **34.5%** / ±20% **50%**; better than free, still hard
- [x] Gemini paid L0 (`3.5-flash-lite` **35.9%**, `3.6-flash` **32.3%**) — 3.6 Flash is current plate leader
- [x] Cheap multi-provider L0 — Claude 3 Haiku **37.9%**, Qwen3.5-Flash **37.1%** (slow), GPT-5 Nano **43.8%** (no win). DeepSeek = no vision; Luna skipped (not cheap)
- [ ] Nutrition5k larger slice (n≥50) if model A/B needs a second image distribution
- [ ] Best paid pin on L1 meal title (does short note help Gemini 3.6 / gpt-4o-mini?)
- [ ] Nutrition-label OCR track (Open Food Facts)
- [ ] On-device LiteRT scoring against the same manifests (phase 2)
- [x] Port a compact-style prompt into [`FoodAnalysisService.kt`](../android/app/src/main/java/app/chompass/services/ai/FoodAnalysisService.kt) — done 2026-07-24 as the **lean** wording (full schema kept; see § Lean production prompt). Follow-up: paired re-run of lean vs `legacy_production_image` on Gemini 3.6 Flash (±20% dip, n=50 single run)
- [ ] Optional: refresh `nofud/free` pools periodically mid-run (today: once per process)
- [x] **Portion-aware prompt A/B** — `compact` vs `compact_portion` on Gemini 3.5 Flash-Lite JFB L0: portion rules **did not win** (WMAPE 37.2% vs 35.9%, ±20% 36% vs 40%). Reverted from production prompts; `compact_portion` kept as research-only.

---

## Suggested next runs

```bash
# 1) Does the plate leader benefit from a short meal title?
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model google/gemini-3.6-flash \
  --prompt compact --sleep 3 --retries 2 \
  --manifest docs/benchmarks/food_accuracy/data/manifests/jfb_image_text_l1.jsonl \
  --out docs/benchmarks/food_accuracy/results/image_text_ab/l1_gemini36_flash

# 2) Nutrition5k n≥50 with current best pin
uv run python docs/benchmarks/food_accuracy/download_nutrition5k.py --limit 50
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model google/gemini-3.6-flash \
  --prompt compact --sleep 3 --retries 2 \
  --manifest docs/benchmarks/food_accuracy/data/manifests/n5k.jsonl \
  --out docs/benchmarks/food_accuracy/results/n5k_gemini36_flash_compact
```

---

## Related docs

| Doc | Contents |
|-----|----------|
| [`FOOD_ACCURACY_BENCHMARK.md`](FOOD_ACCURACY_BENCHMARK.md) | Datasets, metrics, CLI, `nofud/free` behavior |
| [`docs/benchmarks/food_accuracy/README.md`](benchmarks/food_accuracy/README.md) | Quick commands |
| [`ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md) | On-device smoke (latency/parse; no GT macros yet) |

Failure modes / portion reasoning for hard vs easy samples: [§ Failure modes & portion reasoning](#failure-modes--portion-reasoning) above.

## Grounded entry (WIP — not production)

Canonical status and checklist: [`GROUNDED_ENTRY.md`](GROUNDED_ENTRY.md). **`GroundedEntryFeature.ENABLED` remains false.**

Text-42 tool-loop progress (Flash Lite, same split as single-shot benches):

| Run | WMAPE | ±20% kcal | parse | vs ungrounded Flash Lite |
|-----|------:|----------:|------:|--------------------------|
| Prior grounded tool-loop | 17.7% | 76.3% | 90.5% | much worse |
| Post-roadmap grounded (2026-07-22) | **12.8%** | **78.6%** | **100%** | still ~2.7× WMAPE of 4.8% single-shot |
| Ungrounded Flash Lite `compact` | **4.8%** | **92.9%** | 100% | reference |
| Ungrounded Gemma `compact` | **5.7%** | 90.5% | 100% | best free text |

Ship targets (≤10% WMAPE, ≥85% ±20%) are **not** met. Remaining grounded errors are mostly portion, then identity. Local artifact: `results/grounded_tool_gemini35_flash_lite_text_post_roadmap/` (gitignored).

