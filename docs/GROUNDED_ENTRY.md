# Grounded food entry

**Status: experimental / disabled in UI.**  
Flip [`GroundedEntryFeature.ENABLED`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundedEntryFeature.kt) to `true` only after the [readiness checklist](#readiness-checklist) below. Code, USDA assets, and the food-accuracy harness remain in-tree for continued work.

Optional entry method that uses the selected AI provider to **search local
databases and pick identities**, then resolves nutrient values from those
sources only (never from invented macros).

## Progress so far (2026-07)

### Built

1. **Offline USDA index** — Foundation + FNDDS SQLite (`~5.8k` foods, `~2.1 MB`), builder at [`scripts/build_usda_food_index.py`](../scripts/build_usda_food_index.py), Android [`UsdaFoodIndex`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/UsdaFoodIndex.kt).
2. **Provenance model** — `FoodGroundingProvenance` / `GroundingCandidate` / validation helpers; `FoodSource.GROUNDED` for diary export/import.
3. **Orchestrator** — [`GroundedFoodEntryService`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundedFoodEntryService.kt): barcode → OFF, history, USDA, then model-estimate fallback. Deterministic **scale from DB rows** (model must not invent macros).
4. **Cloud tool loop** — [`GroundingTools`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/grounding/GroundingTools.kt) + [`GroundedToolLoop`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/ai/GroundedToolLoop.kt) (max 4 rounds): `search_usda` / `search_history` / `lookup_barcode` → `finalize_grounding`. Last OpenAI-compatible round can force finalize.
5. **Retrieval ranking** — Prefer FNDDS/`survey_fndds_food` for cooked/generic meals; soft-penalize flour/powder/dry/pie and beverage mismatches; omit null-calorie Foundation rows from default search.
6. **UI (gated)** — Add-food tile, entry sheet, candidate sheet, provenance badges — hidden while `GroundedEntryFeature.ENABLED == false`.
7. **Harness** — [`benchmarks/food_accuracy/run_grounded_eval.py`](../benchmarks/food_accuracy/run_grounded_eval.py) mirrors the tool loop against shipped SQLite.

### Benchmark snapshot (Flash Lite, FNDDS text-42)

| Path | WMAPE | ±20% kcal | MAE kcal | Notes |
|------|-------|-----------|----------|-------|
| Single-shot baseline (`quick_gemini35_flash_lite_text`) | **~4.8%** | **~93%** | **~7** | Model invents macros |
| Lexical USDA top-1 (pre–tool-loop) | **~90%** | **~48%** | **~150** | Wrong forms (rice→flour, oatmeal→pie); null kcal→0 |
| Tool-loop grounded (`grounded_tool_gemini35_flash_lite_text`) | **~18%** | **~76%** | **~29** | Much better; still behind single-shot; ~17% estimate fallback |

Artifacts: `benchmarks/food_accuracy/results/grounded_tool_gemini35_flash_lite_text/`.

### Known gaps that block shipping

- Still **worse than single-shot** on this text set for nutrient error (users would notice bad kcal vs Photo/Note).
- Multi-item meals and beverage/powder edge cases still under-matched; forced finalize sometimes picks `reject_to_estimate` with empty macros in the harness.
- On-device path is **lexical only** (no tool loop); not productized.
- Foundation null-energy rows exist in the DB (builder keeps protein-only foods); search filters them, but builder Atwater fill is unfinished.
- FNDDS WWEIA categories are **not** in SQLite yet (all survey `food_category` empty).
- Locale strings for grounded UI are English-defaults only.
- No silent auto-accept UX polish for low-confidence picks on device photos (bench is text-only so far).

## Readiness checklist

Enable `GroundedEntryFeature.ENABLED` only when **all** of the following hold:

1. **Accuracy (text)** — Flash Lite (or chosen default provider) grounded tool-loop WMAPE on `eval_text.jsonl` within ~2× of single-shot (target ballpark: WMAPE ≤ ~10%, ±20% kcal ≥ ~85%), with parse/finalize rate ≥ ~95%.
2. **Accuracy (image)** — At least one photo split (e.g. JFB / Nutrition5k subset) where grounded does not regress badly vs single-shot Photo flow; document numbers in this file.
3. **Identity** — Clear drop in form-mismatch failures (flour/powder/pie/dry vs cooked; beer vs solids) on a recorded bad-case list.
4. **Fallback UX** — `reject_to_estimate` / missing match always surfaces a clear estimate badge or candidate sheet; never silent 0 kcal.
5. **On-device policy** — Either disable grounded when provider is on-device, or ship a tested deterministic path with the same provenance rules.
6. **Strings** — Localized grounded UI strings for shipped locales.
7. **Release note** — Short CHANGELOG blurb + privacy line (BYOK recognition + local USDA/OFF/history).

Until then: keep the flag **false**; develop via unit tests + `run_grounded_eval.py`.

### Local re-enable for development

```kotlin
// GroundedEntryFeature.kt — temporary, do not commit true for release
const val ENABLED: Boolean = true
```

## Trust order

1. Exact barcode → live [Open Food Facts](https://world.openfoodfacts.org/) (cached)
2. Explicitly selected confirmed history / favorites (identity only; portion not auto-copied)
3. Compact offline USDA Foundation + FNDDS index (`assets/usda/usda_foods.sqlite`)
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
| SQLite asset | [`android/app/src/main/assets/usda/usda_foods.sqlite`](../android/app/src/main/assets/usda/usda_foods.sqlite) |
| Manifest (sha256, version) | [`android/app/src/main/assets/usda/usda_foods.manifest.json`](../android/app/src/main/assets/usda/usda_foods.manifest.json) |
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

Raw downloads land in `build/usda-fdc/` (gitignored). The APK ships whatever
SQLite is currently under `assets/usda/`.

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
uv run python benchmarks/food_accuracy/run_grounded_eval.py \
  --provider openrouter --model google/gemini-3.5-flash-lite \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --out benchmarks/food_accuracy/results/grounded_tool_gemini35_flash_lite_text \
  --sleep 6

# Compare to single-shot baseline:
uv run python benchmarks/food_accuracy/compare_runs.py \
  benchmarks/food_accuracy/results/quick_gemini35_flash_lite_text/summary.csv \
  benchmarks/food_accuracy/results/grounded_tool_gemini35_flash_lite_text/summary.csv
```

Asset integrity:

```bash
uv run python -c "import json,hashlib,pathlib; m=json.load(open('android/app/src/main/assets/usda/usda_foods.manifest.json')); b=pathlib.Path('android/app/src/main/assets/usda/usda_foods.sqlite').read_bytes(); assert hashlib.sha256(b).hexdigest()==m['sha256']; print(m['food_count'], 'foods ok')"
```

Metrics: top-k identity hit rate, source coverage, gram error, nutrient WMAPE, tool rounds, search count, fallback/correction rate, latency, asset size.
