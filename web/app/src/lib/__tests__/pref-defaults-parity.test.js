// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  ANDROID_PREF_DEFAULTS,
  DEFAULT_HOME_TOP,
  DEFAULT_FOOD_CHIPS,
  DEFAULT_NUTRIENT_CARD_COUNT,
  DEFAULT_OPTIONAL_NUTRIENT_GOALS,
} from "../home-nutrients.js";
import { DEFAULT_PREFS } from "../db.js";
import { loadParityFixture } from "../parity-fixtures.js";

const fixture = loadParityFixture("pref-defaults.json");

/** @param {string} semantic */
function toPwaKey(semantic) {
  const map = {
    protein: "proteinG",
    carbs: "carbsG",
    fat: "fatG",
    fiber: "fiberG",
    sugar: "sugarG",
  };
  return map[semantic] || semantic;
}

describe("pref defaults (parity fixture)", () => {
  it("ANDROID_PREF_DEFAULTS matches fixture water/AI keys", () => {
    assert.equal(ANDROID_PREF_DEFAULTS.showWater, fixture.showWater);
    assert.equal(ANDROID_PREF_DEFAULTS.waterGoalMl, fixture.waterGoalMl);
    assert.equal(ANDROID_PREF_DEFAULTS.aiFallbackEnabled, fixture.aiFallbackEnabled);
    assert.equal(ANDROID_PREF_DEFAULTS.fallbackAiProvider, fixture.fallbackAiProvider);
    assert.equal(ANDROID_PREF_DEFAULTS.fallbackAiModel, fixture.fallbackAiModel);
  });

  it("home tubes / chips / card count match fixture", () => {
    assert.deepEqual(DEFAULT_HOME_TOP, fixture.homeTopNutrients.map(toPwaKey));
    assert.deepEqual(DEFAULT_FOOD_CHIPS, fixture.foodLogMacroChips.map(toPwaKey));
    assert.equal(DEFAULT_NUTRIENT_CARD_COUNT, fixture.homeNutrientCardCount);
  });

  it("DEFAULT_PREFS schedule / gauge / week start match fixture", () => {
    assert.equal(DEFAULT_PREFS.calorieGaugeMode, fixture.calorieGaugeMode);
    assert.equal(DEFAULT_PREFS.weekStartsOnMonday, fixture.weekStartsOnMonday);
    assert.equal(DEFAULT_PREFS.mealBreakfastStart, fixture.mealBreakfastStart);
    assert.equal(DEFAULT_PREFS.mealLunchStart, fixture.mealLunchStart);
    assert.equal(DEFAULT_PREFS.mealDinnerStart, fixture.mealDinnerStart);
    assert.equal(DEFAULT_PREFS.mealSnackStart, fixture.mealSnackStart);
    assert.equal(DEFAULT_PREFS.progressDefaultRangeId, fixture.progressDefaultRangeId);
  });

  it("optional nutrient goals match fixture", () => {
    const g = fixture.optionalNutrientGoals;
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.sugarG, g.sugar);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.addedSugarG, g.addedSugar);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.fiberG, g.fiber);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.saturatedFatG, g.saturatedFat);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.cholesterolMg, g.cholesterol);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.sodiumMg, g.sodium);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.potassiumMg, g.potassium);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.transFatG, g.transFat);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.calciumMg, g.calcium);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.ironMg, g.iron);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.magnesiumMg, g.magnesium);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.zincMg, g.zinc);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.vitaminAMcg, g.vitaminA);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.vitaminCMg, g.vitaminC);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.vitaminDMcg, g.vitaminD);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.vitaminB12Mcg, g.vitaminB12);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.vitaminEMg, g.vitaminE);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.vitaminKMcg, g.vitaminK);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.folateMcg, g.folate);
    assert.equal(DEFAULT_OPTIONAL_NUTRIENT_GOALS.omega3G, g.omega3);
  });
});
