# Food accuracy benchmark — current state

| | |
|---|---|
| **As of** | 2026-07-22 |
| **Harness** | [`benchmarks/food_accuracy/`](../benchmarks/food_accuracy/) |
| **How-to** | [`FOOD_ACCURACY_BENCHMARK.md`](FOOD_ACCURACY_BENCHMARK.md) |
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
uv run python benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model nofud/free \
  --prompt compact --sleep 8 --retries 2
```

`nofud/free` rebuilds its pool from the live OpenRouter `/models` catalog **once per process** (new free models appear; cancelled ones drop). Mid-run the pool is cached. Inspect with:

```bash
uv run python benchmarks/food_accuracy/list_nofud_free_pool.py --vision --show-excluded
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
10. **Paid VL ceiling is better but still hard.** Best so far: `google/gemini-3.6-flash` L0 compact — WMAPE **32.3%**, ±20% **50%**, mae kcal **123**, ~5 s. Then `openai/gpt-4o-mini` **34.5%** / 50% / ~3.5 s, then `google/gemini-3.5-flash-lite` **35.9%** / 40% / **~1.6 s**. All beat free Gemma (~42%) by ~6–10 pp WMAPE; still far from text (~6%). Plate estimation is not only a free-model limit.
11. **Cold `nofud/free` image baseline is solid.** Fresh L0 run: parse **100%**, WMAPE **41.1%**, ±20% **32%** — matches pinned Gemma (~41.8%) within noise; no content-safety fails.
12. **No OpenRouter `gemini-3.6-flash-lite`.** Lite sibling is `google/gemini-3.5-flash-lite`; full Flash is `google/gemini-3.6-flash`.

---

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
| `image_text_ab/l0_gemini35_flash_lite` | `google/gemini-3.5-flash-lite` | compact | 100% | 35.9% | 137 | 40% | **~1.6 s** | Cheapest/fastest paid; close to mini |
| `baseline_image_nofud_free_compact_cold` | `nofud/free` | compact | **100%** | **41.1%** | **152** | **32%** | ~16 s | Cold free-router L0; ≈ Gemma pin |

**Paid L0 ranking:** Gemini 3.6 Flash ≥ gpt-4o-mini ≥ Gemini 3.5 Flash-Lite ≫ nofud/free ≈ Gemma free.

### Image (Nutrition5k overhead RGB, cursory)

| Run | Model | prompt | n | parse_ok | WMAPE | mae kcal | within 20% | Notes |
|-----|-------|--------|---|----------|-------|----------|------------|-------|
| `n5k_cursory_gemma_compact` | Gemma 4 26B :free | compact | 15 | **100%** | **34.7%** | **80** | **40%** | HTTPS overhead subset; ~7.5 s mean; small-kcal dishes inflate MAPE |

Artifacts under `benchmarks/food_accuracy/results/` (gitignored).

---

## Defaults going forward

| Decision | Choice | Why |
|----------|--------|-----|
| Free router | **`nofud/free`** | No content-safety; live catalog; failover |
| Avoid | `openrouter/free` | Food-JSON poison |
| Text prompt for macro research | **`compact`** | Best WMAPE + latency on Gemma |
| Image prompt for macro research | **`compact`** | Best WMAPE + parse + latency on Gemma JFB A/B |
| App-parity prompt | `production_text` / `production_image` | Only when testing schema/units transfer |
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
- [ ] Nutrition5k larger slice (n≥50) if model A/B needs a second image distribution
- [ ] Best paid pin on L1 meal title (does short note help Gemini 3.6 / gpt-4o-mini?)
- [ ] Nutrition-label OCR track (Open Food Facts)
- [ ] On-device LiteRT scoring against the same manifests (phase 2)
- [ ] Port compact text path into [`FoodAnalysisService.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/ai/FoodAnalysisService.kt) only after a deliberate product decision
- [ ] Optional: refresh `nofud/free` pools periodically mid-run (today: once per process)

---

## Suggested next runs

```bash
# 1) Does the plate leader benefit from a short meal title?
uv run python benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model google/gemini-3.6-flash \
  --prompt compact --sleep 3 --retries 2 \
  --manifest benchmarks/food_accuracy/data/manifests/jfb_image_text_l1.jsonl \
  --out benchmarks/food_accuracy/results/image_text_ab/l1_gemini36_flash

# 2) Nutrition5k n≥50 with current best pin
uv run python benchmarks/food_accuracy/download_nutrition5k.py --limit 50
uv run python benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model google/gemini-3.6-flash \
  --prompt compact --sleep 3 --retries 2 \
  --manifest benchmarks/food_accuracy/data/manifests/n5k.jsonl \
  --out benchmarks/food_accuracy/results/n5k_gemini36_flash_compact
```

---

## Related docs

| Doc | Contents |
|-----|----------|
| [`FOOD_ACCURACY_BENCHMARK.md`](FOOD_ACCURACY_BENCHMARK.md) | Datasets, metrics, CLI, `nofud/free` behavior |
| [`benchmarks/food_accuracy/README.md`](../benchmarks/food_accuracy/README.md) | Quick commands |
| [`ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md) | On-device smoke (latency/parse; no GT macros yet) |
