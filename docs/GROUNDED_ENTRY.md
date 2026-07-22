# Grounded food entry

**Status: WIP — not production, not ready to ship.**  
[`GroundedEntryFeature.ENABLED`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundedEntryFeature.kt) stays **`false`**. Do not package USDA into release/F-Droid APKs, do not advertise the entry method, and do not flip the flag until the [readiness checklist](#readiness-checklist) is fully green. Code + harness remain in-tree for continued research.

Optional entry method that uses the selected AI provider to **search local
databases and pick identities**, then resolves nutrient values from those
sources only (never from invented macros).

## Progress so far (2026-07-22)

### Built (research / debug only)

1. **Offline USDA index** — Foundation + FNDDS SQLite (`~5.8k` foods, `~3.8 MB` with FTS), builder at [`scripts/build_usda_food_index.py`](../scripts/build_usda_food_index.py) with **FTS5**, multi-portion table, Atwater energy fill, WWEIA categories when present, omega-3 merge. Android [`UsdaFoodIndex`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/UsdaFoodIndex.kt). Packaged in **debug** builds only (`src/debug/assets/usda/`), not release.
2. **Provenance model** — `FoodGroundingProvenance` / `GroundingCandidate` / validation helpers; `FoodSource.GROUNDED` for diary export/import (**provenance round-trips in JSON export**).
3. **Orchestrator** — [`GroundedFoodEntryService`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundedFoodEntryService.kt): barcode → OFF, history, USDA, then model-estimate fallback. Deterministic **scale from DB rows** (model must not invent macros). Preserves first-pass recognition on candidate review.
4. **Cloud tool loop** — [`GroundingTools`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundingTools.kt) + [`GroundedToolLoop`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/ai/GroundedToolLoop.kt) (max 4 rounds): `search_usda` / `search_history` / `lookup_barcode` → `finalize_grounding`. Finalize only accepts `source_id`s seen in this run.
5. **Retrieval ranking** — Shared [`QueryNormalizer`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/QueryNormalizer.kt) (Kotlin + Python); prefer FNDDS; soft-penalize flour/powder/dry/pie and beverage mismatches; calibrated source-aware scores + ambiguity margin `1.5`; local [`GroundingCorrectionStore`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundingCorrectionStore.kt) priors.
6. **Portion resolver** — [`PortionResolver`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/PortionResolver.kt): override → estimated grams → quantity×unit → candidate serving → heuristic; **never silent 100 g**.
7. **UI (gated)** — Add-food tile, entry sheet, candidate sheet, provenance + confidence badges — hidden while `GroundedEntryFeature.ENABLED == false`. On-device policy: `ALLOW_ON_DEVICE = false`.
8. **Harness** — [`docs/benchmarks/food_accuracy/run_grounded_eval.py`](benchmarks/food_accuracy/run_grounded_eval.py), failure-class metrics, bad-case + history/OFF manifests, retrieval golden vectors, `devenv tasks run benchmark:food-accuracy-smoke`.

### Benchmark snapshot (Flash Lite, FNDDS text-42)

Same-model apples-to-apples unless noted. **Grounded is still WIP** — improved, not shippable.

| Path | WMAPE | ±20% kcal | MAE kcal | parse | Notes |
|------|-------|-----------|----------|-------|-------|
| Single-shot Flash Lite (`quick_gemini35_flash_lite_text`) | **4.8%** | **92.9%** | **7.1** | 100% | Best same-model ungrounded |
| Single-shot Gemma compact (best free text) | **5.7%** | 90.5% | 8.2 | 100% | Best overall ungrounded text |
| Lexical USDA top-1 (pre–tool-loop) | ~90% | ~48% | ~150 | — | Form mismatches; null kcal→0 |
| Tool-loop grounded **prior** (`grounded_tool_gemini35_flash_lite_text`) | 17.7% | 76.3% | 28.9 | 90.5% | ~17% estimate fallback |
| Tool-loop grounded **post-roadmap** (`…_post_roadmap`, 2026-07-22) | **12.8%** | **78.6%** | **19.9** | **100%** | 0% fallback; identity top-1 88%; remaining errors mostly **portion** (7) then identity (5) |

**Vs ungrounded:** still ~2–2.5× worse on WMAPE and ~14 pp behind on ±20% kcal — users would still notice vs Photo/Note on this text set.

**Vs readiness targets** ([`baselines/grounded_text_thresholds.json`](benchmarks/food_accuracy/baselines/grounded_text_thresholds.json)): WMAPE ≤10% ✗ · ±20% ≥85% ✗ · parse ≥95% ✓ · silent-zero 0% ✓ · identity top-3 ≥80% ✓.

Artifacts: `docs/benchmarks/food_accuracy/results/grounded_tool_gemini35_flash_lite_text_post_roadmap/` (gitignored local results).

### Known gaps that keep this WIP

- Nutrient accuracy still **behind single-shot** on `eval_text.jsonl` (ship blocker #1).
- Remaining failure mix is mostly **portion**, not retrieval/fallback — next accuracy work should target gram/unit resolution and multi-item portions.
- Photo grounded eval (JFB / Nutrition5k) not run to readiness.
- On-device path is lexical only; gated off via `ALLOW_ON_DEVICE`.
- Release packaging must not run until checklist is green (`./scripts/package_usda_for_release.sh --confirm` is intentionally confirm-gated).

## Readiness checklist

Enable `GroundedEntryFeature.ENABLED` only when **all** of the following hold:

1. **Accuracy (text)** — Flash Lite (or chosen default provider) grounded tool-loop WMAPE on `eval_text.jsonl` within ~2× of single-shot (target ballpark: WMAPE ≤ ~10%, ±20% kcal ≥ ~85%), with parse/finalize rate ≥ ~95%.
2. **Accuracy (image)** — At least one photo split (e.g. JFB / Nutrition5k subset) where grounded does not regress badly vs single-shot Photo flow; document numbers in this file.
3. **Identity** — Clear drop in form-mismatch failures (flour/powder/pie/dry vs cooked; beer vs solids) on a recorded bad-case list.
4. **Fallback UX** — `reject_to_estimate` / missing match always surfaces a clear estimate badge or candidate sheet; never silent 0 kcal.
5. **On-device policy** — `GroundedEntryFeature.ALLOW_ON_DEVICE == false` (grounded disabled for LiteRT) **or** ship a tested deterministic path with the same provenance rules.
6. **Strings** — Localized grounded UI strings for shipped locales (EN + DE/ES/FR done; remaining locales fall back to EN until translated).
7. **Release note** — Short CHANGELOG blurb + privacy line (BYOK recognition + local USDA/OFF/history).
8. **USDA packaging** — Run [`scripts/package_usda_for_release.sh --confirm`](../scripts/package_usda_for_release.sh) to copy `src/debug/assets/usda/` → `src/main/assets/usda/` (or ship a downloadable index) so release/F-Droid APKs include the offline DB before flipping the flag.

Until then: keep the flag **false**; treat grounded as **WIP research only**; USDA stays out of release APKs; develop via unit tests + `run_grounded_eval.py` against the debug asset.

### Local re-enable for development

```kotlin
// GroundedEntryFeature.kt — temporary, do not commit true for release
const val ENABLED: Boolean = true
```

## Trust order

1. Exact barcode → live [Open Food Facts](https://world.openfoodfacts.org/) (cached)
2. Explicitly selected confirmed history / favorites (identity only; portion not auto-copied)
3. Compact offline USDA Foundation + FNDDS index (`src/debug/assets/usda/usda_foods.sqlite` until productized)
4. Clearly marked model estimate when no database match exists

## UX (when enabled)

- **Add food → Grounded**: text, photo, or photo+text
- Progress phases: Recognizing → History → USDA → Resolving (mapped from tool rounds)
- Ambiguous matches open a candidate/portion sheet before `FoodResultSheet`
- Review sheet shows a provenance badge (USDA / OFF / history / estimate)

Existing Photo / Note / Barcode / Manual flows are unchanged.

## Cloud tool loop vs on-device

| Provider | Behavior |
|----------|----------|
| Cloud BYOK (OpenAI-compatible / Gemini / Anthropic) | Bounded tool loop (max 4 rounds): `search_usda`, `search_history`, `lookup_barcode`, then required `finalize_grounding`. The model chooses `source_id` / grams; the app scales nutrients from DB rows. |
| On-device LiteRT | Deterministic recognize → lexical retrieve/rank (no tool chat). Tool calling stays experimental for Coach only. |

If a cloud provider fails to tool-call or finalize, the orchestrator falls back to the deterministic path.

## Offline USDA index

| Item | Path |
|------|------|
| SQLite asset (debug APK only) | [`android/app/src/debug/assets/usda/usda_foods.sqlite`](../android/app/src/debug/assets/usda/usda_foods.sqlite) |
| Manifest (sha256, version) | [`android/app/src/debug/assets/usda/usda_foods.manifest.json`](../android/app/src/debug/assets/usda/usda_foods.manifest.json) |
| Build script | [`scripts/build_usda_food_index.py`](../scripts/build_usda_food_index.py) |

Ranking prefers **FNDDS / `survey_fndds_food`** for cooked or generic meals and soft-penalizes form mismatches (flour / powder / dry / pie vs cooked solids; wrong beverage hits). Search omits rows with null calories by default so Foundation energy gaps cannot scale as 0 kcal.

Regenerate the small committed fixture (no network):

```bash
uv run python scripts/build_usda_food_index.py --fixture
```

Build from a pinned FoodData Central CSV zip (downloads ~hundreds of MB once):

```bash
uv run python scripts/build_usda_food_index.py
# or: uv run python scripts/build_usda_food_index.py --zip-path /path/to/FoodData_Central_csv_*.zip
```

Raw downloads land in `build/usda-fdc/` (gitignored). Debug APKs ship whatever
SQLite is currently under `src/debug/assets/usda/`. Release APKs omit it until
grounded entry is productized (then move back under `src/main/assets`).

### Licensing

| Source | License | Notes |
|--------|---------|--------|
| USDA FoodData Central | CC0 / public domain | Cite USDA; safe to ship offline |
| Open Food Facts | ODbL database + DbCL contents | Live lookup only; do not merge into USDA SQLite; keep provenance separable |
| User history | Private on-device | Only confirmed diary/favorites; portion never silently reused |

## Privacy

- Recognition images/text go to the **user-selected** AI provider (same BYOK path as other entry methods), or on-device when configured
- USDA lookups are fully local
- Open Food Facts requests send only the barcode (existing barcode flow)
- History search never leaves the device

## Architecture

```mermaid
flowchart TD
  Sheet[GroundedEntrySheet] --> HVM[HomeViewModel.analyzeGrounded]
  HVM --> GFE[GroundedFoodEntryService]
  GFE --> Loop[GroundedToolLoop]
  Loop -->|"search_usda / search_history / lookup_barcode"| Tools[GroundingTools]
  Tools --> Hist[ConfirmedHistorySearch]
  Tools --> Usda[UsdaFoodIndex]
  Tools --> Off[OpenFoodFactsService]
  Loop -->|"finalize_grounding"| Scale[Deterministic scale + provenance]
  Scale --> Cand{Ambiguous?}
  Cand -->|yes| Review[GroundedCandidateSheet]
  Review --> HVM
  Cand -->|no| Draft[savePendingDraft]
  Draft --> Result[FoodResultSheet]
  GFE -.->|on-device fallback| Det[Recognize then lexical rank]
```

Key types: [`FoodGrounding.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/models/FoodGrounding.kt),
[`GroundedFoodEntryService.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundedFoodEntryService.kt),
[`GroundingTools.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundingTools.kt),
[`GroundedToolLoop.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/ai/GroundedToolLoop.kt),
[`UsdaFoodIndex.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/UsdaFoodIndex.kt),
[`GroundedEntryFeature.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundedEntryFeature.kt).

## Benchmarks

```bash
uv run python docs/benchmarks/food_accuracy/run_grounded_eval.py \
  --provider openrouter --model google/gemini-3.5-flash-lite \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --out docs/benchmarks/food_accuracy/results/grounded_tool_gemini35_flash_lite_text \
  --sleep 6

# Compare to single-shot baseline:
uv run python docs/benchmarks/food_accuracy/compare_runs.py \
  docs/benchmarks/food_accuracy/results/quick_gemini35_flash_lite_text/summary.csv \
  docs/benchmarks/food_accuracy/results/grounded_tool_gemini35_flash_lite_text/summary.csv
```

Asset integrity:

```bash
uv run python -c "import json,hashlib,pathlib; m=json.load(open('android/app/src/debug/assets/usda/usda_foods.manifest.json')); b=pathlib.Path('android/app/src/debug/assets/usda/usda_foods.sqlite').read_bytes(); assert hashlib.sha256(b).hexdigest()==m['sha256']; print(m['food_count'], 'foods ok')"
```

Metrics: top-k identity hit rate, source coverage, gram error, nutrient WMAPE, tool rounds, search count, fallback/correction rate, latency, asset size.
