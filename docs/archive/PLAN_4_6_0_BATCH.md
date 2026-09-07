# PLAN: 4.6.0 release batch

Status: **ARCHIVED 2026-09-07**: all six items shipped in 4.6.0 (2026-09-04).
One batch plan per `docs/README.md` § Work artifacts — this doc
absorbs the former per-item plans `PLAN_PER_INGREDIENT_MICROS.md` (root) and
`docs/PLAN_GEMINI_3_8_FLASH.md` (both deleted; git history keeps them).

## Items

### 1. Meal nutrition as a percent of daily goals (#87) — commit 619a3ed6

One guarded helper per app (`nutritionGoalPercent`: null on zero/negative/NaN/Inf
goal — no division hazards), resolving against the user-configurable
`OptionalNutrientGoals` (RDA-style defaults). No new RDI table. Mono/poly fats
get no percent. Android `NutritionDetailSheet` + PWA `diary-view.js`
`openNutritionDetail`.

### 2. Per-ingredient micronutrients (#86) — commits e936243a, bdce5dc4, b7ca27b3

Decisions (condensed from the absorbed plan):

- Wire bumps per repo convention: diary **1.5**, sync **1.3**, meal-share **v3**;
  schemas + fixtures + PAIRS moved together; old formats still import.
- Four key spaces: AI prompt (22 snake_case keys, no caffeine), diary/sync wire
  (23 unit-suffixed keys), meal-share wire (23 camelCase), model (camelCase
  like `FoodEntry`).
- No micro-sum strip gate; micros ride rows through the macro reconcile and
  scale by the row's grams factor (per-100g semantics, 1-dp, clamped >= 0),
  never residual-fixed.
- % of daily goal reuses #87 semantics; display-only v1 (no per-constituent
  micro editing, no entry rollup); gated on `mealConstituentsRequested`
  (on-device excluded).
- Benchmark: §23 validated the schema on Gemini 3.7 Flash (micro presence 100%,
  sum reconcile 100%).

### 3. Gemini 3.8 Flash default — commit f0d1b61b

Prepended to the GEMINI lineup (release default = `models.first()`), debug
default stays 3.5 Flash-Lite, fallback default unchanged, no data migration,
speech default follows. `modelTiers["gemini-3.8-flash"] = "varies"`.

**Pre-release gates:**

- **Free-tier availability — CLOSED 2026-09-03 (maintainer, AI Studio):**
  `gemini-3.8-flash` has a free tier at roughly 10 requests per day, the same
  limited treatment prior Flash models received at launch (Flash-Lite ~100/day).
  Not paid-only, so the release default stands; over-quota requests fall back
  to 3.5 Flash-Lite as designed. The `"varies"` tier tag stays accurate —
  Google still publishes no per-model free-tier table.
- **Device goal-matrix sweep (SMART tier must not clamp, expect 19/19) —
  CLOSED 2026-09-03:** run on the Pixel 9a via the OpenRouter benchmark key
  (`goal_matrix_provider openrouter`, `google/gemini-3.8-flash`, SMART tier)
  after the free-tier Gemini key proved unusable for a full sweep (~10
  requests/day). All 19 scenarios passed with zero BMR clamping: formula
  anchors exact on the sparse/thin cases (sparse_up 1980, sparse_up_active
  2131, maintain_sparse_up 2530, very_active_sparse_up 2429), measured 2500
  anchor honored at 1950 exact, locked returned exactly 2400, keto carbs
  27 g, gain 3090; worst deviation from formula 10 kcal (rich_consistent,
  gain); the only floor hits are the two by-design cases
  (rich_low_empirical, sedentary_sparse_up). The first pass went 14/14 with
  5 transient failures (3 OpenRouter upstream aborts, 2 device DNS blips);
  the scenario-filter re-run of those 5 was clean. The tier regex behaves
  as designed for 3.8: the release default stands.

### 4. Review-fix pass (this work)

Pre-tag review found two blockers plus parity drift; all fixed with tests:

- **Row-quantity edit now scales micros**: `applyConstituentQuantity`
  (`ConstituentsSection.kt`) appends `.microsScaled(factor)` — every other mass
  path already did. `ConstituentsSectionTest` covers scaling + factor-1 identity.
- **Token cap floor for constituent ops**: the 22-micro-fields-per-row schema
  needs ~800-1400 tokens; capped providers (Anthropic / OpenAI-compatible /
  Ollama) got `max(userCap, 4096)` for `analyzeText|analyzeAuto|analyzeFood|analyzeFoodMulti`
  (`floorResponseTokensForOp`), PWA twin `CONSTITUENTS_MIN_RESPONSE_TOKENS`
  threaded into `anthropicSend` (`req.maxTokens ?? 1024`). Gemini stays uncapped
  (pre-existing exemption). Tests: `EntryConstituentTokenFloorTest`, web
  `food-analyze`/`constituent-micros` suites.
- **Web parity aligned to Android (product of record)**: `microOrNull` clamps
  into [0, 100000] (`InputSanitizer.micro` semantics) instead of nulling
  negatives; `scaledMicros` factor-1 is the identity (0.45 survives); present
  zero micros render `0.0` (the em-dash stays meal-level only); string-typed
  wire micros coerce at parse (`diary-format`/`sync-format` use `microOrNull`)
  so the nutrition sheet can no longer crash on them.
- **Android diary/sync constituent import** wraps the 23 micro reads in
  `InputSanitizer.micro` (same policy as the AI parse and meal-share decode;
  entry-level import fields keep the pre-existing raw policy).
- **Disclosure state**: `ConstituentMicrosDisclosure` no longer keys
  `remember` on the row object — editing name/quantity no longer collapses an
  open ingredient micros block.
- **Estimates label** (delivers plan decision 6's "labeled as estimates"):
  one-line note under the Ingredients heading in `NutritionDetailSheet` +
  PWA nutrition detail (`sheet_constituents_estimates_note` /
  `entry.constituents.estimates_note`; en+de shipped, remaining locales ride
  the locale batch per the locales contract).

### 5. Ingredient micros model-class gating — LANDED 2026-09-04 (d51a41ad,
5b70ca16, 5212372a)

Decision (maintainer, 2026-09-03 evening): per-row micros ship only for
strong-class models; weak class gets the pre-#86 macros-only breakdown.
Rationale (stored runs `results/gemini37_constituents_micro/`,
`gemini35lite_constituents_micro/`, n=16 text meals / 41 rows): Gemini 3.7
Flash partitions its meal totals exactly (0/15 meals with any micro row-sum
off by >20%; rollup is moot on fresh logs), FNDDS micro WMAPE 35.8% (98%
match); Flash-Lite drifts >20% on 6/15 meals (worst +80% added sugar),
WMAPE 63.4%. The Settings hint already told users to disable the breakdown
on weaker models; the app now does it automatically. The user toggle stays
the master opt-out; display stays data-driven (rows logged under a strong
model keep their micros under any current model).

Implementation map (edit sites grounded 2026-09-03):

- Android `FoodAnalysisService.kt`: rename `isSmallCloudGoalModel` (~1671)
  to `isSmallCloudModel` and share it (goal tier + constituents). Keep
  `mealConstituentsRequested()` (~862) as the breakdown-level gate; add
  `constituentMicrosRequested()` = breakdown && !isSmallCloudModel(model
  .ifBlank { provider.defaultModel }). Add
  `ENTRY_JSON_SCHEMA_WITH_CONSTITUENT_MACROS` (rows without the 22 micro
  fields; compose the three schemas from shared pieces instead of a third
  700-char literal), make `entryJsonSchema()` three-way, split
  `ENTRY_CONSTITUENTS_RULE` (~80) into base + micros appendix, and thread the
  micros flag into the floor call (~1342) so weak models keep normal caps.
  Debug-default caveat: Gemini debug defaultModel is 3.5-flash-lite, so
  debug first-run gets macros-only; release default 3.8 gets micros.
- No Android display change: `ConstituentMicrosDisclosure` early-returns on
  absent micros (ConstituentsSection.kt:252) and `parseConstituents` maps
  absent to null (FoodAnalysis.kt:727).
- PWA `web/app/src/lib/ai/food-analyze.js`: split SYSTEM_CONSTITUENTS (~31)
  into macros/micros variants, gate in `mealConstituentsEnabled` (~42),
  apply `CONSTITUENTS_MIN_RESPONSE_TOKENS` (~159) only for the micros
  variant; shared weak-model classifier in chompass-core with a test.
- Copy: reword the breakdown hint en+de (PWA `settings.ai.meal_constituents_hint`
  en.js:361 / de.js:302; Android string name still to locate). The other 14
  locales keep the still-valid "turn off for weaker models" hint until the
  translation sweep.
- Tests: Android schema-selection gate test (strong / lite / toggle off);
  PWA twin in `food-analyze.test.js`. `EntryConstituentTokenFloorTest`
  unaffected (flag param unchanged).
Gates after landing (2026-09-04): gradlew test green (1159, incl. the JVM-safe
`PerfLog.warn` fix for the short-raw log), release:check-parity green.

### 6. Recovered review chip (Android) — commit b1523578

Dismissing a completed AI review no longer discards it: the persisted
`PendingFoodAnalysisDraft` is kept with `awaitingReview = true` and Home shows
a glass chip ("Recovered analysis: <name>"); tap restores the review sheet
with the stored result (no new AI call), X discards draft + photo. Saved
Meals / favorites reviews (no AI cost) still dismiss outright. Android-only
for 4.6.0 (PARITY.md matrix row; PWA cancel still drops the analysis —
follow-up twin). Strings en+de+16 locales. Tests:
`PendingFoodAnalysisDraftRecoveryTest`, `HomeUiStateWriteTest` recoveredReview
case. Device check: dismiss → chip → restore verified interactively by the
maintainer on the Pixel 9a (2026-09-04).

## Pre-tag status

All items landed. Remaining for the tag: version bump (72/4.5.0 → 73/4.6.0),
CHANGELOG date, `release:package`, tag + publish.

## Verification

1. `devenv shell bash -lc 'cd android && ./gradlew test'` — green 2026-09-04
   (1159 tests, 0 failures).
2. `devenv tasks run release:check-parity` — green 2026-09-04 (re-run after
   the PARITY.md matrix edit).
3. Device: 4.6.0 regression pass green 8/8 on 2026-09-03
   (`android/build/release-verify/20260903_173941`); item 6 dismiss → chip →
   restore verified interactively 2026-09-04. Formal sweep re-run optional
   before packaging (item 5 gating also smoked by the interactive pass).
