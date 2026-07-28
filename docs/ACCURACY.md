# How accurate is AI food logging?

Site post (draft until published): [chompass.app/blog/ai-food-logging-accuracy/](https://chompass.app/blog/ai-food-logging-accuracy/).

Chompass is BYOK: you bring a cloud AI key (or run Gemma 4 on-device on Android), so
food-analysis accuracy depends mostly on the model you pick, not on anything unique
to Chompass. We measure accuracy against labeled datasets and publish the numbers
instead of claiming a single accuracy percentage.

All figures below come from an offline research harness against labeled datasets
with known ground-truth calories and macros. Full methodology, every run, and raw
result tables: [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md).

## Headline numbers

| Entry method | Metric | Result | Dataset |
|---|---|---|---|
| **Typed text with a stated portion** | WMAPE (kcal+protein+carbs+fat) | **5.7%** | 42 USDA FNDDS items |
| **Typed text with a stated portion** | Within ±20% of true calories | **90%** | 42 USDA FNDDS items |
| **Photo only (best paid model tested)** | WMAPE | **32.3%** | 50 real meal photos ([January Food Benchmark](https://github.com/January-ai/food-scan-benchmarks)) |
| **Photo only (best paid model tested)** | Within ±20% of true calories | **50%** | 50 real meal photos |
| **Photo only (free on-device-class model)** | WMAPE | 39.8% | 50 real meal photos |

WMAPE = weighted mean absolute percentage error across calories, protein, carbs, and
fat. Lower is better. These use food-analysis prompts equivalent to what ships in
the app, run against the same manifests for every model tested.

The typed-text row is FNDDS-style **identity + portion** (e.g. `Chicken breast, roasted, 150 g`).
It is not the same input as a meal title or ingredient list attached to a plate photo.
Those “image + short note” trials on JFB (title / ingredient names, no quantities) did
**not** beat photo-only on free Gemma (~42–46% WMAPE vs ~42% image-only). That is
compatible with the strong FNDDS result: the notes lack grams/cups. See
[`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md) § Image + description.
Native video input on a Nutrition5k turntable subset also lost to a still image
(WMAPE 25.6% → 37.2%); parked for now.

## Photos are hard for every model

Typed entry **with a stated portion** ("150 g chicken", "1 cup oatmeal"), a barcode
scan, or a saved meal is close to solved. Canonical foods with known grams or units
usually come back exact or near-exact.

Photo estimation is a different problem. A model has to infer portion size,
plate composition, and hidden ingredients (oil, dressing, sauce) from a 2D image
with no scale reference. That is hard and unsolved across vision AI in general;
it is not specific to Chompass or to any one provider. In our testing:

- Even the best paid vision model tried (`gemini-3.6-flash`) still misses about 1 in 2
  meals by more than 20% on calories.
- Error correlates weakly with meal complexity. A simple omelette can be exact
  while a sandwich-and-fries plate is off by 100%+ (models tend to add
  restaurant-scale portions or sides that were not actually on the plate).
- The gap is not prompt wording. After A/B testing multiple prompt shapes, plate
  photo WMAPE stayed in the 33-45% band regardless. Model choice moves it more
  than prompt tuning does.
- A short meal title or unquantified ingredient list on top of the photo did not
  rescue macros on the free-Gemma JFB A/B (photo-only was best of L0/L1/L2).

If you need precise numbers, typed entry with a stated portion, barcode scan, or a
saved meal is measurably more reliable than a photo alone (or a photo plus a vague note).

## What we changed because of this data

The production prompts in [`FoodAnalysisService.kt`](../android/app/src/main/java/app/chompass/services/ai/FoodAnalysisService.kt)
were rewritten and re-benchmarked (2026-07-24) after the harness found the old
wording never reliably elicited a `grams_per_unit` value for typed entries.
Without that, AI-suggested serving units (e.g. "2 slices") were silently unusable
on essentially every text-entry response. The rewritten prompt fixes that (40/41
usable in the eval), keeps the same macro accuracy, and does it in about half
the prompt size.

A **portion clarification** feature is in progress based on a simulated eval:
injecting a ground-truth portion answer into the photo prompt (as a stand-in for
a one-tap "how much was on the plate?" chip) cut photo WMAPE by **15 percentage
points** (35.9% → 22.8%) and raised the ±20%-accurate rate by 12 points, on the
same 50-photo set. That result is why the portion-chip UX is next — quantity in
the input is what moves macros, whether typed grams or a tapped chip.

## Caveats

- These are offline research-harness numbers (small, fixed labeled datasets), not
  a live production accuracy monitor. Actual results vary by model, photo
  quality, and food type.
- The strong text numbers are portioned FNDDS strings; photo / image+note numbers
  are JFB plated meals — not the same meal typed two ways.
- BYOK means your accuracy depends on which provider or model you choose. The
  figures above are per-model, not a single "Chompass accuracy" number.
- On-device Gemma 4 (Android, opt-in) is smaller than cloud models and generally
  less accurate. See the on-device note in [`ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md).
- Numbers will move as models and prompts change. This page reflects the
  snapshot dated in [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md).
