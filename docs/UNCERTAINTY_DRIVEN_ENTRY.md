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

### Bet 1 — Clarification chips (in validation)

After photo analysis, when uncertain, `FoodResultSheet` shows one tap-row instead of silently accepting a point guess:

- **Portion**: `Small · Regular · Large · Restaurant-size` (attacks the +100–200% restaurant-portion mode)
- **Hidden fat**: `Cooked with oil / dressing? Yes · No` (attacks the −65 to −80% oil/tahini mode)

**Gate**: the simulated-clarification eval must pass its pre-registered thresholds first (see the status doc § Simulated clarification eval). Two distinct quantities:

- **Oracle ceiling** (`compact_clarify_portion/fat/both` vs `compact`): how much a *correct* answer helps. If even this is small, the UX is dead regardless of design.
- **Ask precision** (`run_clarify_eval.py` two-stage): can the model tell *when* to ask (`clarify_request`), so chips appear only when useful. If not, triggers become heuristic (e.g. photo source + no stated grams).

### Bet 2 — Ranges instead of points when uncertain

Photo entries display a calorie band ("roughly 550–750") with round bounds; text/barcode/label entries keep point estimates. Reuse the existing three-facet `GroundingConfidence` (identity / portion / nutrientSource, `models/FoodGrounding.kt`) rather than inventing a single score — the render path in `FoodResultSheet` already exists, gated on grounding data being present. Depends on bet 1's eval telling us where uncertainty actually is.

### Bet 3 — Personal portion priors / correction memory

Anchor portions to the user's past corrected values for similar meals (favoriteKey / saved meals), extending the `GroundingCorrectionStore` WIP and the P1 "local correction memory" upstream idea. On-device, no new network surface.

## Explicit non-bets

- **Depth/AR volume estimation** — revisit only if bets 1–3 plateau above target.
- **Un-gating the grounded DB tool loop** — stays off until it beats single-shot (`GroundedEntryFeature.ENABLED=false`).
- **More vision prompt/model A/B** — measured as exhausted; only re-open on a new model generation.

## Where things live

| Piece | Path |
|-------|------|
| Oracle derivation + answer formatting | `docs/benchmarks/food_accuracy/clarify.py` |
| Enriched manifests + covered-id lists | `docs/benchmarks/food_accuracy/build_clarify_manifests.py` |
| Chip-injection prompts | `prompts.py` (`compact_clarify_portion/fat/both`, `compact_clarify_ask`) |
| Two-stage ask-then-answer runner | `docs/benchmarks/food_accuracy/run_clarify_eval.py` |
| Offline smoke (stub, no network) | `scripts/check_food_accuracy_smoke.sh` / `devenv tasks run benchmark:food-accuracy-smoke` |
| Thresholds + results | `FOOD_ACCURACY_BENCHMARK_STATUS.md` § Simulated clarification eval |
