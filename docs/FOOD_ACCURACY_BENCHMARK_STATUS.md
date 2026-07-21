# Food accuracy benchmark — current state

| | |
|---|---|
| **As of** | 2026-07-21 |
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
| `data/manifests/jfb.jsonl` | 50 JFB meal images (downloaded, gitignored) |
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

### Image (JFB 50, prompt `compact`)

| Run | Model | parse_ok | WMAPE | mae kcal | within 20% | Notes |
|-----|-------|----------|-------|----------|------------|-------|
| `baseline_image_free_compact` | `openrouter/free` | **58%** | 47.1% | 172 | 31% | **19** content-safety + 1× 504 |
| `baseline_image_nofud_free_compact` | mix / `nofud/free` fill | **98%** | **43.2%** | 166 | 33% | Filled 20 fails; 1 truncated VL JSON left |

Filled backend mix: Gemma 26B 27/27, Nemotron nano-VL 14/15, Nemotron omni 8/8.

Artifacts under `benchmarks/food_accuracy/results/` (gitignored).

---

## Defaults going forward

| Decision | Choice | Why |
|----------|--------|-----|
| Free router | **`nofud/free`** | No content-safety; live catalog; failover |
| Avoid | `openrouter/free` | Food-JSON poison |
| Text prompt for macro research | **`compact`** | Best WMAPE + latency on Gemma |
| App-parity prompt | `production_text` / `production_image` | Only when testing schema/units transfer |
| Text pin (stable A/B) | `google/gemma-4-26b-a4b-it:free` | Fast, accurate, vision-capable |
| Image pacing | `--sleep 8`+ and `--retries 2`+ | Free-tier per-minute limits |

---

## Gaps / not done yet

- [ ] Image **prompt A/B** on pinned Gemma (`compact` vs `production_image` vs `fewshot_units`)
- [ ] Full **fresh** 50-image run with `nofud/free` from cold start (current image set is openrouter/free survivors + nofud fill — fine for reliability proof, slightly mixed for pure router A/B)
- [ ] Nutrition5k overhead RGB subset (metadata path exists; frames optional via GCS)
- [ ] Nutrition-label OCR track (Open Food Facts)
- [ ] On-device LiteRT scoring against the same manifests (phase 2)
- [ ] Port compact text path into [`FoodAnalysisService.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/ai/FoodAnalysisService.kt) only after a deliberate product decision
- [ ] Optional: refresh `nofud/free` pools periodically mid-run (today: once per process)

---

## Suggested next runs

```bash
# 1) Image prompt A/B on pinned Gemma (pace for free tier)
for prompt in compact production_image fewshot_units; do
  uv run python benchmarks/food_accuracy/run_eval.py \
    --provider openrouter --model google/gemma-4-26b-a4b-it:free \
    --prompt "$prompt" \
    --manifest benchmarks/food_accuracy/data/manifests/jfb.jsonl \
    --sleep 15 --retries 3 \
    --out "benchmarks/food_accuracy/results/image_prompt_ab_gemma/${prompt}"
  sleep 60
done

# 2) Optional: cold nofud/free image baseline (clean router mix)
uv run python benchmarks/food_accuracy/run_eval.py \
  --provider openrouter --model nofud/free \
  --prompt compact \
  --manifest benchmarks/food_accuracy/data/manifests/jfb.jsonl \
  --sleep 8 --retries 2 \
  --out benchmarks/food_accuracy/results/baseline_image_nofud_free_compact_cold
```

---

## Related docs

| Doc | Contents |
|-----|----------|
| [`FOOD_ACCURACY_BENCHMARK.md`](FOOD_ACCURACY_BENCHMARK.md) | Datasets, metrics, CLI, `nofud/free` behavior |
| [`benchmarks/food_accuracy/README.md`](../benchmarks/food_accuracy/README.md) | Quick commands |
| [`ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md) | On-device smoke (latency/parse; no GT macros yet) |
