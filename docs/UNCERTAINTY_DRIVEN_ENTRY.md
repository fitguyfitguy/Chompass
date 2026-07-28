# Uncertainty-driven entry — strategy

Status: research direction (2026-07-24). Companion to [`GROUNDED_ENTRY.md`](GROUNDED_ENTRY.md) and [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md). No production code changes yet.

## The problem, from first principles

Photo entry is stuck at ~40% WMAPE (~32% best paid model) while text is ~5%. The benchmark record shows the error is **portion and hidden ingredients, not identification**, and that prompt/model A/B is exhausted (`compact_portion` lost; L1/L2 notes didn't help; grounded DB lookup grounds identity, which was never the problem).

The limit is information-theoretic: a single RGB photo does not contain the food's mass or the oil it was cooked in. No model recovers bits that are not in the input. The only levers are:

1. **More sensors** — depth/AR volume estimation (SnapCalorie's approach; best published photo apps still sit around ±20% MAPE). Hardware-gated, big effort. Non-bet for now.
2. **Elicit the missing bits from the user at one-tap cost** — ask exactly one targeted question when it moves the estimate most. Clarification research agrees: detect uncertainty first, then ask one question; never always-ask.
3. **Personal priors** — people eat repetitively; the user's own history and corrections are a better portion prior than any population average.
4. **Honest uncertainty + a noise-tolerant system** — MacroFactor's core insight: per-entry accuracy matters less than consistent logging feeding an adaptive loop; weather-forecast UX research shows users prefer round-number ranges with verbal qualifiers over false-precision points.

## The bets, in order

### Bet 1 — Portion clarification chip (validated 2026-07-24, proceed to design)

The simulated-clarification eval (JFB-50, Gemini 3.5 Flash-Lite) confirmed the oracle ceiling: a correct portion answer cuts WMAPE 38.0%→22.8% (−15.2pp) and lifts ±20%-kcal accuracy 38%→50% (+12pp) — clears both pre-registered thresholds by a wide margin. Full results: `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Simulated clarification eval.

After photo analysis, `FoodResultSheet` should show one tap-row instead of silently accepting a point guess:

- **Portion**: `Small · Regular · Large · Restaurant-size` (attacks the +100–200% restaurant-portion mode)

**Hidden-fat chip: parked, not shipping.** Oracle ceiling only −5.2pp WMAPE and it *hurt* ±20% accuracy slightly — below threshold, consistent with the pre-registered caveat that the fat lexicon/oracle is weak. `compact_clarify_both` gained nothing over portion alone, so there's no reason to bundle a fat question with the portion chip.

**Trigger: must be heuristic, not model self-report.** The two-stage ask-then-answer eval showed the model cannot reliably tell the app when to show a chip: it asked 92% of the time (should discriminate, not near-always-ask) and, when it did ask, preferred the *weaker* fat question over the stronger portion question by 34:12. Any `FoodResultSheet` implementation should trigger the portion chip via a simple rule (e.g. always show it for photo entries, or only when the model didn't return a confident `serving_size_grams`) — not via a `clarify_request` field asked of the model.

**Confirmed on Nutrition5k** (2026-07-24, loose, n=15, true-mass oracle): WMAPE 34.4%→15.6% (−18.7pp), ±20% kcal 13.3%→66.7% (+53.4pp) — stronger than JFB, since N5k's oracle is true mass rather than JFB's stated-ingredient-amount proxy. Two independent datasets now agree: **proceed to Android UX design for the portion chip.**

### Bet 2 — Ranges instead of points when uncertain

Photo entries display a calorie band ("roughly 550–750") with round bounds; text/barcode/label entries keep point estimates. Reuse the existing three-facet `GroundingConfidence` (identity / portion / nutrientSource, `models/FoodGrounding.kt`) rather than inventing a single score — the render path in `FoodResultSheet` already exists, gated on grounding data being present. Depends on bet 1's eval telling us where uncertainty actually is.

### Bet 3 — Personal portion priors / correction memory

Anchor portions to the user's past corrected values for similar meals (favoriteKey / saved meals), extending the `GroundingCorrectionStore` WIP and the P1 "local correction memory" upstream idea. On-device, no new network surface.

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

1. Ship **portion clarification chip** (Bet 1 → Android design)
2. **Calorie bands** for photo uncertainty (Bet 2)
3. **Personal portion priors** from corrections (Bet 3)
4. Optional **ingredient split** for mixed plates / on-device

## Where things live

| Piece | Path |
|-------|------|
| Oracle derivation + answer formatting | `docs/benchmarks/food_accuracy/clarify.py` |
| Enriched manifests + covered-id lists | `docs/benchmarks/food_accuracy/build_clarify_manifests.py` |
| Chip-injection prompts | `prompts.py` (`compact_clarify_portion/fat/both`, `compact_clarify_ask`) |
| Two-stage ask-then-answer runner | `docs/benchmarks/food_accuracy/run_clarify_eval.py` |
| Offline smoke (stub, no network) | `scripts/check_food_accuracy_smoke.sh` / `devenv tasks run benchmark:food-accuracy-smoke` |
| Thresholds + results | `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Simulated clarification eval |
