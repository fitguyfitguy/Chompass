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

- **Depth/AR volume estimation** — revisit only if bets 1–3 plateau above target.
- **Un-gating the grounded DB tool loop** — stays off until it beats single-shot (`GroundedEntryFeature.ENABLED=false`).
- **More vision prompt/model A/B** — measured as exhausted; only re-open on a new model generation.

## SOTA literature triage (2026-07-24)

Peer-reviewed / benchmark SOTA food-plate stacks (RGB-D volume, RAG, segmentation, GPS/wearables, fine-tuning) are a **clinical/research blueprint**. Chompass takes the **diagnosis** (portion ≈ 60–70% of energy error; DB grounding for identity; context helps) and rejects most of the **machinery**. Privacy-first, single-photo casual logging, F-Droid/on-device constraints.

### Explore now (product-shaped)

| Literature priority | Chompass shape | Notes |
|---------------------|----------------|-------|
| P1 Volume | **Bet 1** — portion clarification chip (not LiDAR) | Validated on JFB + Nutrition5k; proceed to UX |
| P5 Uncertainty / structured reasoning | **Bet 2** — calorie bands when uncertain | Skip “expert persona” theater; keep existing CoT/few-shot |
| P4 Personal history | **Bet 3** — local correction / portion priors | Offline; better than GPS for “my large coffee”; see UPSTREAM_IDEAS P1 |
| P3 Mixed dishes (lite) | Per-ingredient edit list on `FoodResultSheet` | Not Mask R-CNN; high value for on-device Gemma portion misses |
| P2 RAG | Keep grounded WIP; ship only if it beats single-shot | Remaining grounded errors are mostly **portion**, not identity |

### Explore later (cheap or if 1–3 plateau)

- **Reference-object / body scale** — prompt or UX that uses a visible spoon, fork, chopsticks, credit card, hand, or known body measures (hand span, fist) for scale normalization. One JFB A/B first; unlikely to beat the portion chip for casual users, but worth measuring when photos already include utensils.
- **Second-view benchmark (later)** — same plate from a second angle (or overhead + 45°) vs single RGB; measure portion/kcal lift. Tests whether multi-view geometry helps without full depth/AR. Keep optional UX (“add another angle”) until the eval shows a clear win.
- **Meal-time priors** (breakfast vs dinner) — soft local constraint; small lift vs portion UX.
- **True depth / ARCore / stereo volume** — literature-correct for labs; hardware-gated; published photo apps still ~±20% MAPE.
- **Monocular image→depth (or depth-aware food models)** — revisit if on-device (or cheap cloud) depth estimators become small/fast enough to sit in front of recognition. Likely diminishing returns for BYOK frontier VLMs: they already perform implicit geometric / portion reasoning from RGB, so an explicit depth map may add little unless we feed **metric** scale (reference object, known plate diameter, or phone LiDAR) into the calorie path. More interesting as a future on-device assist when Gemma-class models still miss portions.

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
