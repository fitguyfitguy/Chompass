# BFP measurement accuracy (method-aware trust)

Stacked on `fix/goal-recalc-baseline` (edits its spike guard + staleness).
User decision 2026-10-20: method + custom accuracy override; method-driven
recency; stacked branch.

## Model

- `UserProfile.bodyFatMethod: BodyFatMethod? = null` (null = legacy behavior).
- `UserProfile.bodyFatAccuracyPoints: Double? = null` (overrides the method default).
- `BodyFatMethod(accuracyPoints, recencyDays)`:
  DEXA(1.5, 365) · BIA_EIGHT(3.2, 90) · BIA_FOUR(5.0, 90) · TAPE(3.5, 90).
- `effectiveBodyFatAccuracyPoints`: custom → method → **5.0** (legacy = today's guard).
- `bodyFatRecencyDays`: method → **90** (legacy). `isBodyFatStale` uses it.
- Fields join `goalInputSignature` (recalc nudge on method change).

## Consumers

- Spike guard (`representativeBodyFat`): threshold = accuracy/100 fraction
  (replaces the fixed 0.05; legacy 5.0 pp keeps today's behavior exactly).
- Prompt (Android + PWA): body-fat line gains "(<method>, ±<acc> pp)" when set.
- Settings (Android `BodyFatSheet`, PWA personal form): method picker + accuracy input.
- Display: nothing else reads accuracy; BMR math stays a point estimate.

## Commits

1. Android: enum + fields + guard + staleness + sheet UI + prompt line + tests.
2. PWA mirror: typedef, `formulas.js` helpers, personal form, tests.

## Gates

Targeted suites → `gradlew test` → check_parity → device smoke (set method in
Settings, confirm profile JSON + guard behavior).
