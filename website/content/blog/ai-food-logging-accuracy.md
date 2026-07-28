---
title: How accurate is AI food logging?
date: 2026-07-28
description: Portioned typed entry is near-solved; plate photos are not. Short meal notes and video clips do not close the gap. Measured on labeled datasets.
draft: true
---

Chompass is BYOK: you bring a cloud AI key (or run Gemma 4 on-device on Android), so food-analysis accuracy depends mostly on the model you pick, not on anything unique to Chompass. We measure accuracy against labeled datasets and publish the numbers instead of claiming a single accuracy percentage.

All figures below come from an offline research harness against labeled datasets with known ground-truth calories and macros. Full methodology, every run, and raw result tables live in the [benchmark status note](https://codeberg.org/fitguy/chompass/src/branch/main/docs/FOOD_ACCURACY_BENCHMARK_STATUS.md) on Codeberg. A shorter research summary is in [ACCURACY.md](https://codeberg.org/fitguy/chompass/src/branch/main/docs/ACCURACY.md).

## Headline numbers

| Entry method | Metric | Result | Dataset |
|---|---|---|---|
| **Typed text with a stated portion** | WMAPE (kcal+protein+carbs+fat) | **5.7%** | 42 USDA FNDDS items |
| **Typed text with a stated portion** | Within ±20% of true calories | **90%** | 42 USDA FNDDS items |
| **Photo only (best paid model tested)** | WMAPE | **32.3%** | 50 real meal photos ([January Food Benchmark](https://github.com/January-ai/food-scan-benchmarks)) |
| **Photo only (best paid model tested)** | Within ±20% of true calories | **50%** | 50 real meal photos |
| **Photo only (free on-device-class model)** | WMAPE | 39.8% | 50 real meal photos |

WMAPE = weighted mean absolute percentage error across calories, protein, carbs, and fat. Lower is better. These use food-analysis prompts equivalent to what ships in the app, run against the same manifests for every model tested.

The “typed text” row is **not** the same as typing a meal name next to a photo. The FNDDS set looks like `Chicken breast, roasted, 150 g` or `1 cup oatmeal (240 g)` — identity **plus** grams or a household unit. That is why it lands near 6% WMAPE. The photo rows are real plated meals with **no** stated mass. Comparing those two columns is comparing portioned lookup to free-form plate estimation.

<figure>
  <img src="/img/blog/accuracy/text-vs-photo.png" alt="Two bar charts comparing portioned typed entry, best paid photo, and free photo: WMAPE 5.7% vs 32.3% vs 39.8%, and within ±20% calories 90% vs 50% vs 32%." width="800" height="450" loading="lazy">
  <figcaption>Portioned typed entry (FNDDS 42) vs plate photos (JFB 50). WMAPE is lower-better; ±20% calorie hit rate is higher-better. Different datasets, different inputs — see below.</figcaption>
</figure>

## Photos are hard for every model

When the string already includes how much (“150 g”, “1 cup”), typed entry is close to solved. Canonical foods with known grams or units usually come back exact or near-exact. A barcode scan or a saved meal with a fixed recipe behaves the same way.

Photo estimation is a different problem. A model has to infer portion size, plate composition, and hidden ingredients (oil, dressing, sauce) from a 2D image with no scale reference. That is hard and unsolved across vision AI in general; it is not specific to Chompass or to any one provider.

In our testing:

- Even the best paid vision model tried still misses about 1 in 2 meals by more than 20% on calories.
- Difficulty is not “big meals.” Hard and easy photo cohorts share almost the same mean ground-truth calories. What fails is portion scale and denseness the camera understates.
- The dominant error is **restaurant-portion overestimate**: models invent diner-scale plates or sides that were not logged. Other recurring modes are **hidden-calorie underestimate** (oil, tahini, whole pie vs slice) and **busy multi-item trays** where identification is roughly right but grams are wrong.
- Clean lab overhead photos are only mildly easier than phone meals. On a small Nutrition5k subset, WMAPE was still around 35% — far from portioned text (~6%). Messy phone photos alone do not explain the gap.

<figure>
  <img src="/img/blog/accuracy/failure-modes.png" alt="Three cards: restaurant overestimate plus 100 to 200 percent kcal, hidden-calorie miss minus 65 to 80 percent kcal, and busy multi-item tray with grams wrong." width="800" height="450" loading="lazy">
  <figcaption>Consensus failure modes across five vision models on JFB 50. Hard plates are not simply high-calorie meals.</figcaption>
</figure>

If you need precise numbers, typed entry **with a stated portion**, barcode scan, or a saved meal is measurably more reliable than a photo alone.

### Photo + a short note is not “typed entry”

We also tried the same 50 JFB meals as **image + user note**: meal title (e.g. `Breakfast Platter`) or an ingredient list without quantities (e.g. `scrambled eggs, bacon, roasted potatoes…`). On a free Gemma pin, photo-only beat both — WMAPE **41.8%** vs **44.9%** (title) vs **45.8%** (ingredient names).

That does **not** contradict the strong FNDDS text result. Those notes have no grams or cups; they are closer to a caption than to `150 g chicken`. We would not expect that kind of text, on its own, to match portioned FNDDS either — we have not published a text-only JFB control, and a paid-model recheck of “photo + title” is still open. Product takeaway: a vague note may help UX or identification, but it does not replace stating how much was on the plate.

## Prompt chasing, depth, and video do not fix plates

After A/B testing multiple prompt shapes on the same 50 meal photos, plate WMAPE stayed in roughly the **33–45%** band. Compact prompts often matched or beat longer “production” wording. Short rules meant to ground portion size or invent fewer sides did not move the hard tail. An explicit scale-anchor prompt (plate/bowl size reference) improved WMAPE by about **1.5 percentage points** — real, but too small to ship on its own.

Model choice moves the needle more than prompt tuning. Paid vision models land closer to ~32% WMAPE; free-tier vision stays around ~40%. Neither approaches portioned typed entry.

<figure>
  <img src="/img/blog/accuracy/plate-model-ladder.png" alt="Horizontal bar chart of plate photo WMAPE by model from Gemini 3.6 Flash at 32.3 percent down to GPT-5 Nano at 43.8 percent, with a dashed typed-entry reference line at 5.7 percent." width="800" height="450" loading="lazy">
  <figcaption>Plate WMAPE by model (JFB 50, compact). The dashed line is portioned typed-entry WMAPE (FNDDS) — a different task, shown for scale.</figcaption>
</figure>

We also checked richer capture cues on Nutrition5k lab clips:

- **Monocular depth / volume** from a single photo did not clear the bar for mass estimation.
- **Native video** (sending the turntable clip as `video_url` instead of one still, same free Gemma pin, 12 paired dishes) **lost** to the still: WMAPE **25.6% → 37.2%**, ±20% kcal **41.7% → 33.3%**, about **4.2×** prompt tokens, and worse free-tier reliability. Parked for now — this was a fixed lab turntable, not a casual phone orbit, but “just give the model more frames” did not help on this evidence.

## What did move the needle

Two results changed how we build the app.

**Leaner production prompts.** The old text prompt never reliably elicited `grams_per_unit` for suggested servings (e.g. "2 slices"). Without that field, the app silently dropped AI serving units on essentially every text response. A rewritten prompt keeps the same macro accuracy, about half the prompt size, and usable units on **40 of 41** eval items. That wording is what ships today.

**Portion clarification (in progress).** In a simulated eval, injecting a ground-truth portion answer into the photo prompt — a stand-in for a one-tap “how much was on the plate?” chip — cut photo WMAPE by **15 percentage points** (35.9% → 22.8%) and raised the ±20%-accurate rate by 12 points on the same 50-photo set. That is why a portion-chip UX is next: ask the user for scale, instead of hoping the model invents it. That matches the typed-entry lesson — **quantity in the input** is what moves macros, whether as typed grams or as a tapped chip.

<figure>
  <img src="/img/blog/accuracy/portion-clarify.png" alt="Grouped bars showing photo-only versus photo plus simulated portion answer: WMAPE 35.9 to 22.8 percent, and within ±20 percent calories 40 to 50 percent." width="800" height="450" loading="lazy">
  <figcaption>Simulated portion clarification on JFB 50 (Gemini 3.5 Flash-Lite). Oracle portion answer as a stand-in for a one-tap chip — why that UX is next.</figcaption>
</figure>

## What to do as a user

- Prefer typed text **with grams or a unit**, barcode, or a saved meal when you care about the number.
- A photo plus a meal title or ingredient list without quantities is still mostly a photo estimate.
- Treat photo-only (or photo + vague note) as a fast draft, not a weighed meal.
- BYOK means your accuracy tracks the model you choose; these figures are per-model harness results, not a single “Chompass accuracy” score.

## Caveats

- These are offline research-harness numbers on small, fixed labeled datasets — not a live production accuracy monitor. Results vary by model, photo quality, and food type.
- The strong text numbers are FNDDS-style portioned strings; the photo / image+note numbers are JFB plated meals. Do not read them as the same meal typed two ways.
- On-device Gemma 4 (Android, opt-in) is smaller than cloud models and generally less accurate.
- Numbers will move as models and prompts change. This post reflects the snapshot dated in the [benchmark status note](https://codeberg.org/fitguy/chompass/src/branch/main/docs/FOOD_ACCURACY_BENCHMARK_STATUS.md) (late July 2026).
