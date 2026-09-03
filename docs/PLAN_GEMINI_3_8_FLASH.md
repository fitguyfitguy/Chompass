# PLAN: Gemini 3.8 Flash as high-performance default, 3.5 Flash-Lite budget fallback

Status: executed 2026-09-03 — Android unit tests + `release:check-parity` green; device SMART spot-check on 3.8 passed the first 4/19 scenarios (formula-anchored, no BMR clamp) before the USB device disconnected. Re-run the full sweep: `adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_goal_matrix_test true --es goal_matrix_tier smart --es goal_matrix_provider gemini --es goal_matrix_model gemini-3.8-flash`. Cross-app (Android + PWA) + parity fixture + device pass → plan doc per `docs/README.md` § Work artifacts.

## Context

- `gemini-3.8-flash` GA (stable alias) 2026-09-02: text/image/video/audio/PDF input, text out, 1M in / 65,536 out; search grounding, function calling, structured output all supported. Thinking supports low/medium/high; **`minimal` was removed** — irrelevant here: neither `GeminiClient` nor the PWA `geminiSend` ever sends `thinkingConfig`/`generationConfig.thinking` (only OpenRouter's OpenAI-compatible format carries a `reasoning` body), and neither sends `candidate_count`. **No wire-format change needed.**
- Current state (locked by `testdata/parity/ai-provider-defaults.json` + `AiProviderDefaultsParityTest` + PWA `ai-provider-defaults-parity.test.js`):
  - Release default `gemini-3.7-flash` (= `models.first()`), debug default `gemini-3.5-flash-lite` (3.7 free-tier 503s, 2026-09-01).
  - Fallback default `gemini-3.5-flash-lite` — the budget fallback; **stays unchanged**.

## Decisions

1. **Prepend `gemini-3.8-flash` to the GEMINI lineup.** Keep 3.7 and everything below: the "only models currently in service" rule still holds for them, and stored user selections must keep resolving (`supportedModelOrDefault` / PWA `resolveProviderModel` return the stored id while it's listed).
2. **Release default follows automatically** (`models.first()` → 3.8). **Debug default stays `gemini-3.5-flash-lite`**: 3.8's free-tier behavior is unverified and Lite is the budget model anyway; device testing shouldn't burn the new flagship quota.
3. **Fallback default unchanged**: `gemini-3.5-flash-lite` on Android, PWA, fixture, and `ANDROID_PREF_DEFAULTS` (`web/app/src/lib/home-nutrients.js`). This is the explicit product ask.
4. **`modelTiers["gemini-3.8-flash"] = "varies"`**: Google's rate-limits page no longer publishes a per-model free-tier table (moved into AI Studio), so the new Flash gets the same treatment 3.7 got at introduction. Verify in AI Studio before release (see Risks).
5. **No data migration.** Existing installs keep `gemini-3.7-flash` (still in lineup); only blank/unknown stored models resolve to the new default.
6. **Goal tier: nothing to gate.** `isSmallCloudGoalModel` (`FoodAnalysisService.kt:1653`) matches `flash-lite|nano|haiku|-mini|/free` — `gemini-3.8-flash` runs SMART by default, same as 3.6/3.7. Verify on device (3.6 passed 19/19; Lite clamps 6/19).
7. **Speech default follows the analysis default** (`SpeechProvider.kt:41` carries the "matches AI food-analysis default" invariant) → `gemini-3.8-flash`.

## Edits

One commit — parity rule 3 (`docs/PARITY.md`): change both clients + fixture together, plus tests and CHANGELOG.

| File | Change |
|---|---|
| `android/app/src/main/java/app/chompass/models/AIProvider.kt` | `models`: prepend `"gemini-3.8-flash"` to GEMINI list (line 68-77). `modelTiers`: add `"gemini-3.8-flash" to "varies"` (line 153-157). Doc comments: lineup verified 2026-09-03 (3.8 added 2026-09-02; still no new lite — 3.5 Flash-Lite remains newest); update defaultModel KDoc (3.7 503 note stays, release default is now 3.8) and modelTiers KDoc. |
| `android/app/src/main/java/app/chompass/models/SpeechProvider.kt` | Line 41: `"gemini-3.7-flash"` → `"gemini-3.8-flash"`. |
| `android/app/src/test/java/app/chompass/models/AIProviderFallbackTest.kt` | Line 13: release expectation `"gemini-3.7-flash"` → `"gemini-3.8-flash"`. |
| `testdata/parity/ai-provider-defaults.json` | gemini block: `defaultModel` → `"gemini-3.8-flash"`; prepend to `models`; add `"gemini-3.8-flash": "varies"` to `modelTiers`. |
| `web/app/src/lib/ai/providers.js` | gemini block (lines 451-471): `defaultModel`, `models`, `modelTiers` — mirror fixture exactly (Android parity test asserts list + tier map equality). |
| `web/app/src/lib/__tests__/ai-provider-defaults-parity.test.js` | Lines 29, 35: `"gemini-3.7-flash"` → `"gemini-3.8-flash"`. |
| `docs/CHANGELOG.md` | Unreleased line: Gemini 3.8 Flash as default primary model; fallback stays 3.5 Flash-Lite. |

**Deliberately untouched:**
- `AiErrorTest` 404 sample (`models/gemini-3.7-flash` string) — marker-based, 3.7 still a valid id.
- `FoodAnalysisWatchdogTest` / `GoalTierSelectionTest` — pin 3.7 explicitly; still in lineup, tests stay meaningful (they must not run on the budget fallback).
- `AiModelRoutingTest` — 3.6/3.5 ids, unaffected.
- `docs/PARITY.md` — names the fixture file, no model ids.
- `docs/ON_DEVICE_LLM.md` goal-matrix examples — OpenRouter slugs (`google/gemini-3.6-flash`); OpenRouter 3.8 slug not announced.
- `web/README.md` defaults note — fallback id unchanged; primary listed as provider only.
- OPENROUTER lineup — separate curated list; no 3.8 slug yet.

## Verification

1. `devenv shell bash -lc 'cd android && ./gradlew test'` — `AiProviderDefaultsParityTest` (fixture ↔ code lock) + `AIProviderFallbackTest` cover the swap exactly.
2. `devenv tasks run release:check-parity` — PWA `node --test` (defaults parity test) + `tsc` + fixture validation.
3. Device pass (Windows adb, `.debug` package):
   - Fresh install / `reset_onboarding` → onboarding AI step shows `gemini-3.8-flash` selected with the "(free tier varies)" subtitle (new-install default path).
   - Existing install upgrade → stored 3.7 selection survives Settings re-open (no forced migration).
   - Analyze a meal on 3.8 (seeded debug key) → normal result path.
   - `demo_ai_fail` style forced failure with primary=3.8 → fallback leg runs `gemini-3.5-flash-lite`.
   - `--ez run_goal_matrix_test true --es goal_matrix_provider gemini` → SMART tier, expect 19/19 (3.8 must not clamp like Lite).
4. Post-merge benchmark run (not a merge blocker): food-accuracy slate + goal matrix on `gemini-3.8-flash` (direct Gemini key), then add rows to `docs/FOOD_ACCURACY_BENCHMARK_STATUS.md` / `docs/benchmarks/food_accuracy/README.md`. Relevant to the #154 constituents gate: current strong-model row is 3.7 (100% reconcile); 3.8 needs the same excursory run before any constituents schema decision.

## Risks / open items

- **3.8 free-tier availability unknown** ("varies" placeholder). If it ships paid-only like the Pro models did on 2026-04-01: flip tier tag to `"paid"` on both sides + fixture and revisit the release default before tagging (a paid-only default is hostile to BYOK free-tier users; fallback Lite would carry everything). AI Studio check is a pre-release gate, not optional.
- **Latency/cost drift**: 3.8 has default thinking; per-request latency may exceed 3.7. Benchmarks capture actuals; `maxResponseTokens`/timeout plumbing is model-agnostic. If streaming UX regresses, that's a follow-up tuning item, not a lineup blocker.
- **Upstream iOS mirror** (`AIProvider.swift` per the lineup doc comment): out of this repo's control; note only.
