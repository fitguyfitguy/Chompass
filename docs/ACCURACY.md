# How accurate is AI food logging?

Chompass is BYOK: you bring a cloud AI key (or run Gemma 4 on-device on Android), so
food-analysis accuracy is mostly a function of the model you pick, not a Chompass
secret sauce. What Chompass does differently is **measure it and publish the
numbers** instead of asserting an accuracy percentage.

All figures below come from an offline research harness against labeled datasets
(known ground-truth calories/macros), not from user reports or vibes. Full
methodology, every run, and raw result tables: [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md).

## Headline numbers

| Entry method | Metric | Result | Dataset |
|---|---|---|---|
| **Typed / text** | WMAPE (kcal+protein+carbs+fat) | **5.7%** | 42 USDA FNDDS items |
| **Typed / text** | Within ±20% of true calories | **90%** | 42 USDA FNDDS items |
| **Photo (best paid model tested)** | WMAPE | **32.3%** | 50 real meal photos ([January Food Benchmark](https://github.com/January-ai/food-scan-benchmarks)) |
| **Photo (best paid model tested)** | Within ±20% of true calories | **50%** | 50 real meal photos |
| **Photo (free on-device-class model)** | WMAPE | 39.8% | 50 real meal photos |

WMAPE = weighted mean absolute percentage error across calories, protein, carbs, and
fat — lower is better. These are measured on food-analysis prompts equivalent to
what ships in the app, run against the same manifests across every model tested,
so the comparisons are apples-to-apples.

## The honest part: photos are hard, for every model

Typed/text entry ("2 eggs and toast", a barcode scan, a saved meal) is close to
solved — canonical foods with known grams or units come back exact or near-exact.

Photo estimation is a different problem: a model has to infer portion size,
plate composition, and hidden ingredients (oil, dressing, sauce) from a 2D image,
with no scale reference. That's a hard, unsolved problem across the vision-AI
industry — it is not specific to Chompass or to any one provider. In our testing:

- Even the best paid vision model tried (`gemini-3.6-flash`) still misses ~1 in 2
  meals by more than 20% on calories.
- Error correlates weakly with meal complexity — a simple omelette can be exact
  while a sandwich-and-fries plate is off by 100%+ (models tend to add
  restaurant-scale portions/sides that weren't actually on the plate).
- The gap is not prompt wording — after A/B testing multiple prompt shapes, plate
  photo WMAPE stayed in the 33–45% band regardless. Model choice moves it more
  than prompt tuning does.

If you need precise numbers, typed entry, barcode scan, or a stated portion
("1 cup") is measurably more reliable than a photo alone — the app doesn't
oversell photo-only logging as lab-grade.

## What we changed because of this data

The production prompts in [`FoodAnalysisService.kt`](../android/app/src/main/java/app/chompass/services/ai/FoodAnalysisService.kt)
were rewritten and re-benchmarked (2026-07-24) after the harness found the old
wording never reliably elicited a `grams_per_unit` value for typed entries —
so AI-suggested serving units (e.g. "2 slices") were silently unusable on
essentially every text-entry response. The rewritten prompt fixes that (40/41
usable in the eval), keeps the same macro accuracy, and does it in about half
the prompt size.

A **portion clarification** feature is in progress based on a simulated eval:
injecting a ground-truth portion answer into the photo prompt (as a stand-in for
a one-tap "how much was on the plate?" chip) cut photo WMAPE by **15 percentage
points** (35.9% → 22.8%) and raised the ±20%-accurate rate by 12 points, on the
same 50-photo set. That result is what's driving the upcoming portion-chip UX —
it shipped as a measured bet, not a guess.

## Caveats

- These are offline research-harness numbers (small, fixed labeled datasets), not
  a live/production accuracy monitor — actual results vary by model, photo
  quality, and food type.
- BYOK means your accuracy depends on which provider/model you choose; the
  figures above are per-model, not "Chompass accuracy" as a single number.
- On-device Gemma 4 (Android, opt-in) is smaller than cloud models and generally
  less accurate — see the on-device note in [`ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md).
- Numbers will move as models and prompts change; this page reflects the
  snapshot dated in [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md).
