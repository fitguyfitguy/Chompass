# PLAN: 4.6.0 release batch

Status: WIP (pre-tag). Feature commits are on main; the review-fix pass landed
with this doc. One batch plan per `docs/README.md` § Work artifacts — this doc
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
- **Device goal-matrix sweep (SMART tier must not clamp, expect 19/19)** —
  spot-check passed the first 4/19 scenarios (formula-anchored, no BMR clamp)
  before a USB disconnect; the full re-run was blocked by device availability
  during the review-fix pass. Re-run before or shortly after tagging:
  `adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_goal_matrix_test true --es goal_matrix_tier smart --es goal_matrix_provider gemini --es goal_matrix_model gemini-3.8-flash`
  A clamping result would mean the tier regex picked up Lite-like behavior and
  must be fixed before the model stays default.

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

## Verification

1. `devenv shell bash -lc 'cd android && ./gradlew test'` — full suite incl.
   the two new test files.
2. `devenv tasks run release:check-parity` — PWA tests + tsc + schema/fixture
   validation.
3. Device gate sweep (item 3) when a device is attached; record the result in
   this section.
