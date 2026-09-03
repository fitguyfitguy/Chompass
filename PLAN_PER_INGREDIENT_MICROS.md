# Plan: Per-ingredient micros + % RDI (Codeberg #86, upstream #154 family)

## Goal

Constituent rows (meal ingredient breakdown) carry optional micronutrients, survive
reconcile/export/import/share, and render per-ingredient with percent-of-daily-goal —
the public ask in #86 (@bergieberg), benchmark-validated by
`docs/FOOD_ACCURACY_BENCHMARK_STATUS.md` §23 (Gemini 3.7 Flash: micro presence 100%,
sum reconcile 100%, FNDDS micro WMAPE 35.8%; Flash Lite presence-plausible but weak).

## Decisions

1. **Version bumps per repo convention** (every wire addition bumped: 1.1 item micros,
   1.2 constituents, 1.3 caffeine, 1.4 custom meals): diary **1.5**, sync **1.3**,
   meal-share **v3**. New schema files; export consts bumped; import allowlists extended.
   Known cost (accepted precedent): old apps reject a new-format export entirely.
2. **Four key spaces** (mirror each surface's existing convention):
   - AI prompt/parse: short snake_case, 22 keys, **no caffeine** (`sugar`,
     `added_sugar`, `fiber`, `saturated_fat`, `monounsaturated_fat`,
     `polyunsaturated_fat`, `cholesterol`, `sodium`, `potassium`, `trans_fat`,
     `calcium`, `iron`, `magnesium`, `zinc`, `vitamin_a`, `vitamin_c`, `vitamin_d`,
     `vitamin_b12`, `vitamin_e`, `vitamin_k`, `folate`, `omega_3`) — verbatim from
     benchmark `prompts.py` CONSTITUENTS_MICRO_JSON_SCHEMA.
   - Diary/sync wire: unit-suffixed snake_case, 23 keys (`sugar_g` … `caffeine_mg`),
     identical to the item def.
   - Meal-share wire: camelCase short keys matching MealShare entry micros (`sugar` …
     `caffeine`), 23 keys.
   - Model: camelCase suffixed (`sugarG` … `caffeineMg`), matching FoodEntry.
3. **No micro-sum strip gate.** §23 explicitly says "not a gate"; entry-level micros
   have no cross-check today. Micros ride rows through the existing macro reconcile and
   scale by the row's grams factor (the physical per-100g semantics), round to 1dp,
   clamped ≥ 0. No micro residual fixing (macros keep last-row residual logic).
4. **% RDI = percent of the user's daily goal** (same semantics as #87). Goal
   resolution: `optionalGoals.<field>` (defaults are RDA-style values; zeroed goal →
   no percent). No new reference table.
5. **Display-only v1.** No per-constituent micro editing, no entry-micro rollup from
   constituents (the "stale micros" P2 stays a follow-up), `aggregatesFrom`/`Aggregate`
   unchanged.
6. **Gating**: rides the existing `mealConstituentsRequested` gate (on-device excluded
   already; cloud models all get micros — estimates are labeled as estimates).

## Scope

In: schemas + fixtures + PAIRS; Kotlin model/parse/reconcile/scale/export/import/share
(Diary 1.5, Sync 1.3, MealShare v3); prompt schema + rule (both apps, kept in sync with
benchmark `production_text_constituents_micro`); PWA core (models/constituent
parse/scale/wire/meal-share) + prompts; per-ingredient micros display with % of goal in
ConstituentsSection rows (result/edit/favorite sheets) + PWA entry-form, and an
Ingredients section in NutritionDetailSheet + PWA nutrition detail; unit tests both
apps; CHANGELOG + PARITY.md.

Out of scope: per-constituent micro editing; stale-micros note/re-estimate (internal P2);
PWA top-level prompt only requesting 13 of 23 entry micros (pre-existing gap, noted);
constituent wheel-commit bug (separate backlog row).

## Touchpoints (discovered by scouts)

- Contracts: `contracts/diary-1.5.schema.json` (new), `sync-1.3.schema.json` (new),
  `meal-share-v3.schema.json` (new), `contracts/README.md`,
  `scripts/validate_parity_contracts.py` PAIRS (diary-sample pins diary-1.3 → move to
  1.5; diary-custom-meal stays the 1.4 golden).
- Fixtures: `testdata/parity/diary-sample.json` (1.3→1.5), `sync-sample.json`
  (1.2→1.3), `meal-share-sample.json` (v2→v3) — extend existing constituent rows.
- Android: `models/FoodConstituent.kt` (+23 nullable micros, `scaled()`),
  `services/ai/FoodAnalysis.kt` `parseConstituents` (:692-733 insertion point),
  `services/ai/FoodAnalysisService.kt` (`ENTRY_JSON_SCHEMA_WITH_CONSTITUENTS` :66-67,
  `ENTRY_CONSTITUENTS_RULE` :80-87), `services/ai/ConstituentReconcile.kt`
  (scaleRows micro pass), `export/DiaryExporter.kt` (ConstituentDto :263-274),
  `export/DiaryImporter.kt` (parseConstituents :192-215, allowlist :31-33),
  `export/SyncDocument.kt` (constituentToWire/parseConstituents), `services/MealShare.kt`
  (VERSION :31, constituentsJson :124-145, parseConstituents :195-218),
  `ui/home/ConstituentsSection.kt` (row micros expansion; hosts FoodResultSheet :855,
  EditFoodEntrySheet :651, EditFavoriteSheet :486), `ui/home/NutritionDetailSheet.kt`
  (Ingredients section; `nutritionGoalPercent` :311), strings ×18 locale files if new
  keys are unavoidable.
- PWA: `chompass-core/models.js` (typedef :89-101), `chompass-core/constituents.js`
  (parseConstituentsFromPrediction :297-355, scaleConstituent),
  `chompass-core/diary-format.js` / `sync-format.js` (constituentsToWire/FromWire —
  reuse MICRO_FIELDS pairs), `chompass-core/meal-share.js` (MEAL_SHARE_VERSION :9),
  `lib/ai/food-analyze.js` (SYSTEM_CONSTITUENTS :31-34, runAnalyze :190-199),
  `components/entry-form.js` (renderConstituentRow :530-618), `components/diary-view.js`
  (openNutritionDetail :2067-2125; `nutritionGoalPercent` :80-93 — move to a shared
  module for reuse), i18n catalogs ×18, tests (constituents/diary-format/sync-format/
  meal-share-parity/food-analyze).

## Verification

1. `pwa-test` + `pwa-typecheck`.
2. `:app:testDebugUnitTest` (new/updated round-trip + reconcile + scaled tests).
3. `release:check-parity` (schema validation, parity matrix, locales contract, Android
   strings check, hardcoded-strings check).
4. CHANGELOG entry under Unreleased; PARITY.md row; backlog row #86 → implemented.
