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
| **Typed vague-quantity text (best paid)** | WMAPE | **22.7%** | 50 JFB meal diary notes (no photo) |
| **Typed vague-quantity text (best paid)** | Within ±20% of true calories | **68%** | 50 JFB meal diary notes (no photo) |
| **Photo only (best paid model tested)** | WMAPE | **32.3%** | 50 real meal photos ([January Food Benchmark](https://github.com/January-ai/food-scan-benchmarks)) |
| **Photo only (best paid model tested)** | Within ±20% of true calories | **50%** | 50 real meal photos |
| **Photo only (free on-device-class model)** | WMAPE | 39.8% | 50 real meal photos |

WMAPE = weighted mean absolute percentage error across calories, protein, carbs, and
fat. Lower is better. These use food-analysis prompts equivalent to what ships in
the app, run against the same manifests for every model tested.

The typed-text row is FNDDS-style **identity + portion** (e.g. `Chicken breast, roasted, 150 g`).
It is not the same input as a meal title or ingredient list attached to a plate photo.
Those “image + short note” trials are **mixed**: on JFB with free Gemma, title /
ingredient names (no quantities) did **not** beat photo-only (~42–46% WMAPE vs ~42%
image-only). On Nutrition5k and ACETADA with Flash Lite, **specific item names**
helped (N5k 37.4% → 30.6%; ACETADA research-only L2 reached 15.0% WMAPE / 87% ±20%).
A dedicated **vague quantity note** condition (**Lq**: e.g. “large plate of …”,
“a couple eggs…”, no exact grams) on Flash Lite clearly beats image-only on both
JFB-50 (**35.9% → 25.3%** WMAPE, ±20% **40% → 52%**) and N5k-50 (**32.6% → 27.6%**).
Meal-title-only L1 stays weak. Qualitative size chips (bucket) on N5k help modestly
but do not beat Lq. The same Lq strings scored as **typed text only** (no photo)
match image+Lq within noise on Flash Lite (**24.9%** / **52%**); a multi-model
bake-off puts Gemini 3.6 Flash at **22.7%** / **68%** and DeepSeek (text-only
model) at **23.5%** / **62%**. Quantity language is the lever; the photo adds
almost nothing once it is present: still far from typed grams (~6% WMAPE). See
[`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md)
§ Photo-adjacent entry matrix and § Text-only vague-quantity bake-off.
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
- A short meal title or unquantified ingredient list is not a substitute for a
  stated portion. Vague **quantity** language (Lq) does move macros (~36% → ~23–25%
  WMAPE depending on model) whether typed alone or attached to a photo, but still
  leaves a large gap vs typed entry with grams (~6% WMAPE).

If you need precise numbers, typed entry with a stated portion, barcode scan, or a
saved meal is measurably more reliable than a photo alone. Quantity language
(even vague) beats a title-only note; neither replaces grams.

## What we changed because of this data

The production prompts in [`FoodAnalysisService.kt`](../android/app/src/main/java/app/chompass/services/ai/FoodAnalysisService.kt)
were rewritten and re-benchmarked (2026-07-24) after the harness found the old
wording never reliably elicited a `grams_per_unit` value for typed entries.
Without that, AI-suggested serving units (e.g. "2 slices") were silently unusable
on nearly every text-entry response. The rewritten prompt fixes that (40/41
usable in the eval), keeps the same macro accuracy, and does it in about half
the prompt size.

A **portion clarification** feature is in progress based on a simulated eval:
injecting a ground-truth portion answer into the photo prompt (as a stand-in for
a one-tap "how much was on the plate?" chip) cut photo WMAPE by **15 percentage
points** (35.9% → 22.8%) and raised the ±20%-accurate rate by 12 points, on the
same 50-photo set. That result is why the portion-chip UX is next: quantity in
the input is what moves macros, whether typed grams or a tapped chip.

## Caveats

- These are offline research-harness numbers (small, fixed labeled datasets), not
  a live production accuracy monitor. Actual results vary by model, photo
  quality, and food type.
- The strong text numbers are portioned FNDDS strings; photo numbers are mostly
  JFB plated meals; image+note also covers Nutrition5k and research-only ACETADA;
  not the same meal typed two ways.
- BYOK means your accuracy depends on which provider or model you choose. The
  figures above are per-model, not a single "Chompass accuracy" number.
- On-device Gemma 4 (Android, opt-in) is smaller than cloud models and generally
  less accurate. See the on-device note in [`ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md).
- Numbers will move as models and prompts change. This page reflects the
  snapshot dated in [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md).
