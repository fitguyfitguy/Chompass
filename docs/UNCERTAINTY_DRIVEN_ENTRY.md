# Uncertainty-driven entry — strategy

Status: Bet 1 shipping (2026-07-29) — exact-weight correction on every photo entry; qualitative chips remain soft/opt-in after bucket-only A/B (2026-07-30: N5k-50 Flash Lite bucket 28.7% WMAPE vs L0 32.6%; does not beat Lq vague-quantity notes at 27.6%). Companion to [`GROUNDED_ENTRY.md`](GROUNDED_ENTRY.md) and [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md).

## The problem, from first principles

Photo entry is stuck at ~40% WMAPE (~32% best paid model) while text is ~5%. The benchmark record shows the error is **portion and hidden ingredients, not identification**, and that prompt/model A/B is exhausted (`compact_portion` lost; L1/L2 notes didn't help; grounded DB lookup grounds identity, which was never the problem).

The limit is information-theoretic: a single RGB photo does not contain the food's mass or the oil it was cooked in. No model recovers bits that are not in the input. The only levers are:

1. **More sensors** — depth/AR volume estimation (SnapCalorie's approach; best published photo apps still sit around ±20% MAPE). Hardware-gated, big effort. Non-bet for now.
2. **Elicit the missing bits from the user at one-tap cost** — ask exactly one targeted question when it moves the estimate most. Clarification research agrees: detect uncertainty first, then ask one question; never always-ask.
3. **Personal priors** — people eat repetitively; the user's own history and corrections are a better portion prior than any population average.
4. **Honest uncertainty + a noise-tolerant system** — MacroFactor's core insight: per-entry accuracy matters less than consistent logging feeding an adaptive loop; weather-forecast UX research shows users prefer round-number ranges with verbal qualifiers over false-precision points.

## The bets, in order

### Bet 1 — Portion clarification (validated 2026-07-24; signal-split 2026-07-29)

The simulated-clarification eval (JFB-50, Gemini 3.5 Flash-Lite) confirmed the oracle ceiling: a correct portion answer cuts WMAPE 38.0%→22.8% (−15.2pp) and lifts ±20%-kcal accuracy 38%→50% (+12pp) — clears both pre-registered thresholds by a wide margin. Full results: `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Simulated clarification eval.

**Signal caveat:** that JFB run injected **stated ingredient amounts**, and the N5k confirmation injected **true total mass**. Neither isolates the four qualitative size labels (`Small · Regular · Large · Restaurant-size`). The harness now scores those signals separately (`compact_clarify_portion_{grams,bucket,amounts}`).

After photo analysis, `FoodResultSheet` should show a correction row on **every** photo entry:

- **Exact grams** (primary, validated): user enters total edible weight → deterministic nutrient rescale, no second AI call
- **Optional size chips** (secondary, opt-in until bucket A/B): `Small · Regular · Large · Restaurant-size` → re-analyze with the answer injected

**Hidden-fat chip: parked, not shipping.** Oracle ceiling only −5.2pp WMAPE and it *hurt* ±20% accuracy slightly — below threshold, consistent with the pre-registered caveat that the fat lexicon/oracle is weak. `compact_clarify_both` gained nothing over portion alone, so there's no reason to bundle a fat question with the portion row.

**Trigger: must be unconditional on photo entries.** The two-stage ask-then-answer eval showed the model cannot reliably tell the app when to show a chip: it asked 92% of the time (should discriminate, not near-always-ask) and, when it did ask, preferred the *weaker* fat question over the stronger portion question by 34:12. The 2026-07-29 post-hoc analysis closed the two remaining candidate triggers as well: corr(predicted `serving_size_grams`, true kcal) ≈ **+0.14** across twelve runs, and corr(cross-model disagreement, actual error) = **+0.012**. So the earlier suggestion of gating on "the model didn't return a confident `serving_size_grams`" has **no signal behind it** — `FoodResultSheet` should simply show the portion row on every photo entry.

**Confirmed on Nutrition5k** (2026-07-24, loose, n=15, true-mass oracle): WMAPE 34.4%→15.6% (−18.7pp), ±20% kcal 13.3%→66.7% (+53.4pp) — stronger than JFB, since N5k's oracle is true mass rather than JFB's stated-ingredient-amount proxy. Two independent datasets now agree on **exact mass / stated amounts**.

**Bucket-only A/B (2026-07-30, N5k-50, Flash Lite):** `compact_clarify_portion_bucket` reaches WMAPE **28.7%** / ±20% **32%** vs L0 **32.6%** / **24%** (−3.9pp / +8pp). Real but modest; **Lq** vague-quantity user notes land at **27.6%** / **34%** on the same IDs. Keep qualitative chips soft/opt-in; prefer exact grams and quantity language in notes. Full table: `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Photo-adjacent entry matrix.

### Bet 2 — Ranges instead of points when uncertain

Photo entries display a calorie band ("roughly 550–750") with round bounds; text/barcode/label entries keep point estimates. Reuse the existing three-facet `GroundingConfidence` (identity / portion / nutrientSource, `models/FoodGrounding.kt`) rather than inventing a single score — the render path in `FoodResultSheet` already exists, gated on grounding data being present.

**Bands must be fixed-width per entry type, not confidence-scaled** (2026-07-29). No model-side channel predicts per-sample error: not self-report (92% ask rate), not emitted `serving_size_grams` (r ≈ +0.14), not cross-model disagreement (r = +0.012). A band whose width varies with a "confidence" the model cannot actually estimate would be false precision about false precision. Derive one width per entry type from the measured error distributions instead (photo ≈ ±32–40% WMAPE, text ≈ ±6%).

### Bet 3 — Personal portion priors / correction memory

Anchor portions to the user's past corrected values for similar meals (favoriteKey / saved meals), extending the `GroundingCorrectionStore` WIP and the P1 "local correction memory" upstream idea. On-device, no new network surface.

**Now has a measured ceiling** (2026-07-29). Plate overestimation turns out to be a *systematic multiplicative* bias, not just a hard tail: 10 of 12 vision runs fit a kcal scale of 0.76–0.84, while text fits exactly 1.000. A single per-model correction factor is worth up to **−7.5pp WMAPE / +22pp ±20% kcal** (cross-validated). Crucially the magnitude does **not** transfer across datasets (JFB→N5k cost 20pp of ±20% accuracy), which is the argument *for* Bet 3 rather than a shipped constant: the factor should be learned from the individual user's corrections, where it is by construction in-distribution. Two design constraints fall out — the correction must be **modality-gated** (applying the photo factor to typed text wrecks it, 5.7% → 23.5% WMAPE) and **per model**, since `gpt-4o-mini` and Claude do not overestimate. See `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Post-hoc calibration & ensembling.

### Bet 4 — Multi-model ensemble as a "high accuracy" toggle (new, 2026-07-29)

The median of two *differently-biased* models beats every single model measured: `gpt-4o-mini + Qwen3.5-Flash` = **29.4% WMAPE**, and pairing the documented overestimator with the documented underestimator (`gemini-3.6-flash + gpt-4o-mini`) reaches **62% within ±20% kcal** vs 50% for the best single call — a +12pp lift, second only to the portion-clarification oracle. Stacked with calibration: **27.0% WMAPE / 60% ±20%**, the best plate result on record.

Fits BYOK naturally (the user already supplies keys), but costs N× calls and max-latency, so it belongs behind an explicit per-entry or per-setting toggle, never as the default. Two hard constraints from the data: free-tier-only ensembling is **useless** (`gemma_free + nofud_free` = 38.2%, no better than either alone — shared bias, nothing to cancel), and repeating the *same* model is useless (see below). Ranked after bets 1–3 because it spends the user's money rather than adding information.

**Dead end, do not build: self-consistency.** Three independent runs of Flash-Lite `compact` on identical inputs scored 35.9% / 35.9% / 38.0%; the median of all three was **37.6%** — worse than one call. Plate error is bias, not sampling variance, so a retry-and-average path buys nothing. Only genuinely different models cancel.

## Explicit non-bets

- **Depth/AR volume estimation** — revisit only if bets 1–3 plateau above target. Monocular depth was measured 2026-07-28 (see below) and did not clear the bar for a hardware-free version either.
- **Un-gating the grounded DB tool loop** — stays off until it beats single-shot (`GroundedEntryFeature.ENABLED=false`).
- **More vision prompt/model A/B** — measured as exhausted; only re-open on a new model generation.

## SOTA literature triage (2026-07-24)

Peer-reviewed / benchmark SOTA food-plate stacks (RGB-D volume, RAG, segmentation, GPS/wearables, fine-tuning) are a **clinical/research blueprint**. Chompass takes the **diagnosis** (portion ≈ 60–70% of energy error; DB grounding for identity; context helps) and rejects most of the **machinery**. Privacy-first, single-photo casual logging, F-Droid/on-device constraints.

### Explore now (product-shaped)

| Literature priority | Chompass shape | Notes |
|---------------------|----------------|-------|
| P1 Volume | **Bet 1** — portion clarification chip (not LiDAR) | Validated on JFB + Nutrition5k; proceed to UX |
| P5 Uncertainty / structured reasoning | **Bet 2** — calorie bands when uncertain | Skip “expert persona” theater; production prompts are JSON-only (no CoT channel) — see candidate below |
| P4 Personal history | **Bet 3** — local correction / portion priors | Offline; better than GPS for “my large coffee”; see UPSTREAM_IDEAS P1 |
| P3 Mixed dishes (lite) | Per-ingredient edit list on `FoodResultSheet` | Not Mask R-CNN; high value for on-device Gemma portion misses |
| P2 RAG | Keep grounded WIP; ship only if it beats single-shot | Remaining grounded errors are mostly **portion**, not identity |

### Explore later (cheap or if 1–3 plateau)

- **Reference-object / body scale** — prompt or UX that uses a visible spoon, fork, chopsticks, credit card, hand, or known body measures (hand span, fist) for scale normalization. One JFB A/B first; unlikely to beat the portion chip for casual users, but worth measuring when photos already include utensils.
- **Second-view benchmark (later)** — same plate from a second angle (or overhead + 45°) vs single RGB; measure portion/kcal lift. Tests whether multi-view geometry helps without full depth/AR. Keep optional UX (“add another angle”) until the eval shows a clear win.
- **Meal-time priors** (breakfast vs dinner) — soft local constraint; small lift vs portion UX.
- **True depth / ARCore / stereo volume** — literature-correct for labs; hardware-gated; published photo apps still ~±20% MAPE.
- **Monocular image→depth (or depth-aware food models)** — **measured 2026-07-28** on Nutrition5k (15 dishes, true RealSense depth + Depth Anything V2 Small): true depth gives a moderate volume↔mass correlation (r=0.564) but a naive flat-density volume estimate is still far worse than current photo WMAPE; a camera-only monocular depth estimate carries essentially **no** signal (r=0.097) even after per-image calibration to the oracle scale. Turntable multi-angle frames showed ~10% view-to-view variance in a relative depth proxy — some angle sensitivity, not large enough alone to justify capture UX. Full write-up: `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Depth/volume estimation from Nutrition5k. **Verdict: still not worth an Android/on-device follow-up** — combined with the on-device GPU/RAM contention and open F-Droid model-fetch question (`docs/ON_DEVICE_LLM.md`, `build.gradle.kts:220-222`), a plain monocular depth model doesn't clear the bar. Revisit only if a future depth model ships with a learned food-density head, not a flat constant.

### New candidates (2026-07-28 brainstorm)

Ranked by value vs. price, cross-checked against the record above so nothing here
duplicates an exhausted or rejected item.

| Idea | Value | Price | Status |
|------|-------|-------|--------|
| **Reference-object / plate-scale prompting** (`compact_scale_ref`) | Medium | Very low — prompt-only, one harness A/B | **Run 2026-07-28: WMAPE 35.9%→34.4%, ±20% 38%→42%.** Real but small; not worth shipping alone — see `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Reference-object / scale-anchor prompting |
| **Provider reasoning / CoT (allow then hide)** | Low–medium, uncertain | Low — harness A/B + OpenRouter `reasoning` flag; latency/cost up | Not started |
| **Restaurant-item nutrition lookup fallback** | High | Medium — new "is this branded/restaurant" trigger + text-search query path | Not started |
| **Casual orbit video → native multi-frame reasoning** | Medium-high, uncertain | Medium — needs a small self-captured labeled clip set | **Directly measured 2026-07-28, negative.** Sent the same N5k turntable clips as native `video_url` (no depth extraction, model reasons over raw frames itself) vs the single overhead still, same free Gemma pin, same 12 dishes: WMAPE **25.6%→37.2%** (worse), ±20% kcal **41.7%→33.3%** (worse), 4.2× prompt tokens, worse free-tier reliability. Combined with the depth-proxy variance finding above, both mechanisms tried on this fixed-camera dataset now say video hurts, not helps — **park**, don't spend the self-capture-dataset effort here without a stronger prior (e.g. a genuinely user-directed orbit capture, not a lab turntable, or a paid/stronger model). Harness gained first-class video support (`run_eval.py --video`, `providers.py` `video_path`) for any future re-test. See `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Native video input vs still image. |
| **Per-model bias calibration** | High | **Zero** — re-scores stored predictions | **Run 2026-07-29: up to −7.5pp WMAPE / +22pp ±20%.** Direction robust (10/12 models overestimate; text bias is exactly 1.000), magnitude dataset-dependent → fold into Bet 3 rather than hard-coding a constant |
| **Cross-model median ensemble** | High | Low to measure (zero), N× API calls to ship | **Run 2026-07-29: 29.4% WMAPE, 62% ±20%** (best single: 32.3% / 50%). Promoted to **Bet 4** |
| **Self-consistency (same model, N samples)** | — | Zero | **Run 2026-07-29, negative.** Median of 3 identical runs 37.6% vs 35.9% single — error is bias, not variance. Dead |
| **Nutrition-label OCR eval track** | Medium | Very low — closes an already-flagged harness gap | Not started |
| **Nutritionix as a second product DB** (restaurant chains) | Medium | Higher — new vendor, API key/ToS, ongoing cost | Not started; gate on restaurant-lookup finding below |

**Reference-object / plate-scale prompting.** Already listed under "explore later"
above but never actually run. Extends the "if a utensil/hand/coin is visible" line
already shipped in `FoodAnalysisService.analyzeAuto` with an explicit **standard
dinner-plate (~26cm) / bowl (~15cm) fallback** for when no object is visible, so
the model has a size anchor even on a plain plate shot. Cheapest thing on this
list to test — one new prompt variant, one JFB-50 run against the existing pinned
model. See `FOOD_ACCURACY_BENCHMARK_STATUS.md` for the result.

**Provider reasoning / CoT (allow then hide).** Today production forbids visible
scratchpad (“Respond ONLY with JSON”), OpenRouter analyze always sends
`reasoning: {exclude: true}`, Anthropic `thinking` blocks are skipped, and
reasoning-only replies trigger a compact “no reasoning” retry
(`OpenAICompatibleClient` / `AnthropicClient`). That is correct UX (users never
see CoT) but it also means we have **never measured** whether letting a
reasoning-capable model think before the JSON answer improves plate kcal.

Prior: **probably a small lift at best, not a Bet-1-scale win.** The doc’s
information-theoretic diagnosis still holds — mass and cooking oil are not in a
single RGB frame; CoT cannot invent missing bits. Prompt rules that tried to
steer portion priors (`compact_portion`) lost; scale-ref gained ~1.5pp WMAPE.
Where reasoning *might* help is using cues that *are* in the image more
consistently (utensil/plate scale, multi-item trays, not inventing diner sides)
and on models that already allocate reasoning tokens (Gemini thinking, o-series,
Nemotron-omni-reasoning in the free pool). Where it hurts: latency, `$`, and
the known failure mode of burning the budget on reasoning with empty `content`.

Cheap harness experiment (do not ship on vibes):

1. JFB-50 L0, same pinned non-reasoning baseline (`compact` + `exclude: true`).
2. Same model with reasoning **allowed** but **excluded from the user-visible
   payload** (OpenRouter `exclude: false` or drop the exclude flag; still parse
   `content` only via `FoodJsonParser` — never surface scratchpad).
3. Optionally one explicit “think then JSON” prompt variant vs JSON-only, on a
   model that actually emits reasoning when allowed.
4. Kill if &lt;3pp WMAPE or if parse/truncation regressions dominate; do not
   reopen “more prompt A/B” generally — this is specifically the provider
   reasoning channel we currently force off.

On-device Gemma/LiteRT has no separate reasoning API; skip unless a future
on-device build exposes thinking tokens. Do not confuse with Bet 2’s “structured
reasoning” (calorie *bands* for the user) — that is UX honesty, not model CoT.

**Restaurant-item nutrition lookup fallback.** Targets the dominant documented
failure mode directly: the +100-200% restaurant-portion overestimate (status doc
finding #17), which is a *portion* error, not an identity error. This is narrower
than the disabled grounded tool loop — that loop's problem is portion error
surviving *correct* identity across all food types, so un-gating it wholesale
doesn't fix restaurant portions specifically. Before building any new lookup path,
re-slice the existing grounded-eval results (`run_grounded_eval.py` /
`grounded_metrics.py`) by a "looks like a restaurant/branded meal" tag to check
whether DB-sourced portion data actually helps that subset even though it didn't
help the aggregate. If it does, the OFF (or a restaurant-specific DB) lookup only
needs to fire for that subset, with a heuristic trigger analogous to the portion
chip's (never model self-report — see Bet 1's two-stage finding).

**Casual orbit video → native multi-frame reasoning.** Distinct from the
"true depth/ARCore/stereo" non-bet below (no hardware sensor, no explicit depth
map extracted) and from the not-yet-run "second-view" idea above (that's exactly
two static photos; this is N frames sampled from a few seconds of phone motion
around the plate, letting a vision-language model reason about parallax/geometry
implicitly the way it already does with a single RGB frame). The app-side plumbing
is free: `FoodAnalysisService.analyzeFood(imageBytesList)` already accepts and
sends multiple images end-to-end to every provider. The only new work is (a) frame
extraction from a short clip and (b) a small labeled clip set, since no public
food-accuracy dataset ships casual orbit video — Nutrition5k's video is fixed
overhead, not an orbit. Treat as a harness experiment only (extract N frames,
compare WMAPE against single-frame L0) before any capture-UI work; only worth an
"add a few seconds of video" capture affordance if the harness shows a real lift
over the still second-view case.

**Nutrition-label OCR eval track.** Not a new idea — already flagged as an open
item in the status doc's gaps list. `FoodAnalysisService.analyzeNutritionLabel`
and its per-100g schema already ship in production; there is simply no benchmark
run against labeled nutrition-facts photos yet. Cheap to close alongside this
brainstorm once a small labeled-label manifest exists.

**Nutritionix as a second product DB.** Only worth the vendor/API-key/ToS/cost
overhead if the restaurant-lookup-fallback experiment above shows Open Food Facts'
branded/restaurant coverage — not the trigger logic — is the actual bottleneck.
Do not integrate speculatively.

### Skip / misaligned

| Idea | Why |
|------|-----|
| GPS / restaurant locale | Privacy conflict; weak vs personal history |
| Wearable chew / ingestion fusion | Out of scope; sensors we don’t own |
| Full instance segmentation + 3D reconstruction | Research stack, not F-Droid consumer app |
| Food-specific fine-tuning of Gemma | Huge training cost; user correction memory is the practical continuous learning |
| Chasing clinical MAPE &lt;10% from a single RGB photo | Information-theoretic limit; prefer consistent logging + adaptive goals |

### Ordered next steps

1. Ship **exact-weight portion correction** on every photo entry (pre-analysis optional grams + post-analysis deterministic rescale); qualitative chips remain opt-in until bucket A/B
2. Run paired **`compact_clarify_portion_{grams,bucket,amounts}`** A/B and record in the status doc before defaulting qualitative chips on
3. **Calorie bands** for photo uncertainty (Bet 2), fixed-width per entry type
4. **Personal portion priors** from corrections (Bet 3) — now the natural home for the measured per-model bias factor
5. **Multi-model ensemble toggle** (Bet 4) — optional, costs N× calls
6. Optional **ingredient split** for mixed plates / on-device

## Where things live

| Piece | Path |
|-------|------|
| Oracle derivation + answer formatting | `docs/benchmarks/food_accuracy/clarify.py` |
| Enriched manifests + covered-id lists | `docs/benchmarks/food_accuracy/build_clarify_manifests.py` |
| Chip-injection prompts | `prompts.py` (`compact_clarify_portion/fat/both`, `compact_clarify_ask`) |
| Two-stage ask-then-answer runner | `docs/benchmarks/food_accuracy/run_clarify_eval.py` |
| Post-hoc calibration / ensembling (no API calls) | `docs/benchmarks/food_accuracy/posthoc_calibration.py` |
| Offline smoke (stub, no network) | `scripts/check_food_accuracy_smoke.sh` / `devenv tasks run benchmark:food-accuracy-smoke` |
| Thresholds + results | `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Simulated clarification eval |
