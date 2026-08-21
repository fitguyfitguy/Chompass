// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  DEFAULT_OPTIONAL_NUTRIENT_GOALS,
  DEFAULT_HOME_TOP,
  DEFAULT_FOOD_CHIPS,
  DEFAULT_NUTRIENT_CARD_COUNT,
  ANDROID_PREF_DEFAULTS,
  normalizeHomeTopNutrients,
  normalizeFoodLogChips,
  sumNutrient,
  sumMealChipValues,
  nutrientGoal,
  mergeOptionalGoals,
  formatFoodChips,
  formatFoodPills,
  ALL_MICRO_KEYS,
} from "../home-nutrients.js";
import { mapProduct } from "../off-client.js";

test("androidAlignedDefaultConstants", () => {
  assert.equal(DEFAULT_NUTRIENT_CARD_COUNT, 4);
  assert.deepEqual(DEFAULT_HOME_TOP, ["proteinG", "carbsG", "fatG", "fiberG"]);
  assert.deepEqual(DEFAULT_FOOD_CHIPS, ["proteinG", "carbsG", "fatG"]);
  assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.fiberG, 30);
  assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.sodiumMg, 2300);
  assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.sugarG, 50);
  assert.equal(ANDROID_PREF_DEFAULTS.showWater, false);
  assert.equal(ANDROID_PREF_DEFAULTS.waterGoalMl, 2000);
  assert.equal(ANDROID_PREF_DEFAULTS.aiFallbackEnabled, true);
  assert.equal(ANDROID_PREF_DEFAULTS.fallbackAiProvider, "gemini");
  assert.equal(ANDROID_PREF_DEFAULTS.fallbackAiModel, "gemini-3.5-flash-lite");
});

test("normalizeHomeTopNutrients_padsAndTruncates", () => {
  assert.deepEqual(normalizeHomeTopNutrients([], 4), DEFAULT_HOME_TOP);
  assert.deepEqual(normalizeHomeTopNutrients(["sodiumMg"], 4), [
    "sodiumMg",
    "proteinG",
    "carbsG",
    "fatG",
  ]);
  assert.deepEqual(normalizeHomeTopNutrients(["proteinG", "carbsG", "fatG", "fiberG", "sugarG"], 3), [
    "proteinG",
    "carbsG",
    "fatG",
  ]);
});

test("normalizeFoodLogChips_defaultsAndFiber", () => {
  assert.deepEqual(normalizeFoodLogChips(null), DEFAULT_FOOD_CHIPS);
  assert.deepEqual(normalizeFoodLogChips(["fiberG"]), ["fiberG", "proteinG", "carbsG", "fatG"]);
});

test("sumNutrient_and_goal", () => {
  const entries = [
    { calories: 100, proteinG: 10, carbsG: 20, fatG: 5, fiberG: 3, sodiumMg: 400 },
    { calories: 50, proteinG: 5, carbsG: 10, fatG: 2, fiberG: null, sodiumMg: null },
  ];
  assert.equal(sumNutrient(entries, "fiberG"), 3);
  assert.equal(sumNutrient(entries, "sodiumMg"), 400);
  const targets = { calories: 2000, proteinG: 150, carbsG: 200, fatG: 70 };
  assert.equal(nutrientGoal("proteinG", targets, null), 150);
  assert.equal(nutrientGoal("fiberG", targets, null), 30);
  assert.equal(nutrientGoal("fiberG", targets, { fiberG: 40 }), 40);
});

test("sumMealChipValues_includesSelectedFiberAndSugar", () => {
  const entries = [
    { calories: 150, proteinG: 5, carbsG: 27, fatG: 3, fiberG: 4.2, sugarG: 0.8 },
    { calories: 50, proteinG: 1, carbsG: 12, fatG: 0.3, fiberG: 2.5, sugarG: 7 },
  ];
  const totals = sumMealChipValues(entries, ["proteinG", "carbsG", "fatG", "fiberG", "sugarG"]);
  assert.equal(totals.calories, 200);
  assert.equal(totals.proteinG, 6);
  assert.equal(totals.carbsG, 39);
  assert.equal(totals.fatG, 3.3);
  assert.equal(totals.fiberG, 6.7);
  assert.equal(totals.sugarG, 7.8);
  // Null micros on an entry count as zero.
  const withNulls = sumMealChipValues(
    [{ calories: 100, proteinG: 10, carbsG: 20, fatG: 5, fiberG: null, sugarG: null }],
    ["fiberG", "sugarG"]
  );
  assert.equal(withNulls.fiberG, 0);
  assert.equal(withNulls.sugarG, 0);
});

test("mergeOptionalGoals_fillsDefaults", () => {
  const merged = mergeOptionalGoals({ fiberG: 25 });
  assert.equal(merged.fiberG, 25);
  assert.equal(merged.sodiumMg, 2300);
});

test("formatFoodChips", () => {
  const entry = { calories: 1, proteinG: 12.4, carbsG: 30, fatG: 8, fiberG: 5.6 };
  const html = formatFoodChips(entry, ["proteinG", "carbsG", "fatG"]);
  assert.match(html, /12<span class="macro-chip macro-chip--protein">P<\/span>/);
  assert.match(html, /30<span class="macro-chip macro-chip--carbs">C<\/span>/);
  assert.match(html, /8<span class="macro-chip macro-chip--fat">F<\/span>/);
  // Fiber shows when selected (Android FoodLogMacroChip parity).
  assert.match(formatFoodChips(entry, ["proteinG", "fiberG"]), /6<span class="macro-chip macro-chip--fiber">Fi<\/span>/);
});

test("formatFoodPills_androidCapsules", () => {
  const entry = { calories: 1, proteinG: 12.4, carbsG: 30, fatG: 8, fiberG: 5.6 };
  const html = formatFoodPills(entry, ["proteinG", "carbsG", "fatG", "fiberG"]);
  // Android FoodLogMacroChipView: "P 24g" — glyph first, value + unit, no space.
  assert.match(html, /<span class="macro-pill macro-pill--protein">P 12g<\/span>/);
  assert.match(html, /<span class="macro-pill macro-pill--carbs">C 30g<\/span>/);
  assert.match(html, /<span class="macro-pill macro-pill--fat">F 8g<\/span>/);
  assert.match(html, /<span class="macro-pill macro-pill--fiber">Fi 6g<\/span>/);
});

test("ALL_MICRO_KEYS_count", () => {
  assert.equal(ALL_MICRO_KEYS.length, 22);
});

test("offMapProduct_servingMicros", () => {
  const mapped = mapProduct(
    {
      product_name: "Test Bar",
      brands: "Acme",
      serving_quantity: 50,
      nutriments: {
        "energy-kcal_100g": 400,
        proteins_100g: 20,
        carbohydrates_100g: 40,
        fat_100g: 10,
        fiber_100g: 8,
        sugars_100g: 12,
        sodium_100g: 0.2,
        "saturated-fat_100g": 3,
      },
    },
    "123"
  );
  assert.ok(mapped);
  assert.equal(mapped.name, "Acme Test Bar");
  assert.equal(mapped.quantityG, 50);
  assert.equal(mapped.calories, 200);
  assert.equal(mapped.proteinG, 10);
  assert.equal(mapped.fiberG, 4);
  assert.equal(mapped.sugarG, 6);
  assert.equal(mapped.sodiumMg, 100);
  assert.equal(mapped.saturatedFatG, 1.5);
});

test("offMapProduct_prefersServingKeys", () => {
  const mapped = mapProduct(
    {
      product_name: "Yogurt",
      serving_quantity: 150,
      nutriments: {
        "energy-kcal_serving": 120,
        proteins_serving: 8,
        carbohydrates_serving: 12,
        fat_serving: 3,
        fiber_serving: 0.5,
        "energy-kcal_100g": 80,
        proteins_100g: 5,
      },
    },
    "999"
  );
  assert.ok(mapped);
  assert.equal(mapped.calories, 120);
  assert.equal(mapped.proteinG, 8);
  assert.equal(mapped.fiberG, 0.5);
});
