// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { quickRelogFoodTemplates } from "../saved-meals.js";
import { guessMealTypeFromPrefs } from "../meal-schedule.js";

/** @param {Partial<import('../chompass-core/models.js').FoodEntry> & {name: string}} partial */
function entry(partial) {
  return {
    id: partial.id ?? crypto.randomUUID(),
    name: partial.name,
    calories: partial.calories ?? 100,
    proteinG: partial.proteinG ?? 10,
    carbsG: partial.carbsG ?? 10,
    fatG: partial.fatG ?? 5,
    quantityG: null,
    servingUnitOptions: [],
    selectedServingUnit: null,
    selectedServingQuantity: null,
    mealType: partial.mealType ?? "lunch",
    date: partial.date ?? "2024-06-01",
    time: partial.time ?? "12:00",
    source: "manual",
    note: null,
    grounding: null,
    recipeLogId: null,
  };
}

const defaultSchedule = {
  mealBreakfastStart: 5 * 60,
  mealLunchStart: 11 * 60,
  mealDinnerStart: 15 * 60,
  mealSnackStart: 21 * 60,
};

describe("guessMealTypeFromPrefs (Android MealSchedule parity)", () => {
  it("maps overnight / pre-breakfast to snack", () => {
    assert.equal(
      guessMealTypeFromPrefs(defaultSchedule, new Date("2026-07-21T04:59:00")),
      "snack",
    );
  });

  it("maps default windows like MealSchedule.mealTypeAt", () => {
    assert.equal(
      guessMealTypeFromPrefs(defaultSchedule, new Date("2026-07-21T05:00:00")),
      "breakfast",
    );
    assert.equal(
      guessMealTypeFromPrefs(defaultSchedule, new Date("2026-07-21T11:00:00")),
      "lunch",
    );
    assert.equal(
      guessMealTypeFromPrefs(defaultSchedule, new Date("2026-07-21T15:00:00")),
      "dinner",
    );
    assert.equal(
      guessMealTypeFromPrefs(defaultSchedule, new Date("2026-07-21T21:00:00")),
      "snack",
    );
  });
});

describe("quickRelogFoodTemplates", () => {
  it("at lunch ranks meal-matched before dinner favorite, then snacks", () => {
    const favorites = [entry({ name: "Dinner Fav", mealType: "dinner", time: "19:00" })];
    const recents = [
      entry({ name: "Lunch Bowl", mealType: "lunch", time: "12:00" }),
      entry({ name: "Yogurt", mealType: "snack", time: "15:00" }),
    ];
    const out = quickRelogFoodTemplates(favorites, recents, [], "lunch");
    assert.deepEqual(
      out.map((e) => e.name),
      ["Lunch Bowl", "Yogurt", "Dinner Fav"],
    );
  });

  it("soft-boosts favorites within the meal-matched bucket", () => {
    const favorites = [entry({ name: "Fav Salad", mealType: "lunch", time: "11:00" })];
    const recents = [
      entry({ name: "Newer Sandwich", mealType: "lunch", time: "13:00" }),
      entry({ name: "Fav Salad", mealType: "lunch", time: "12:30" }),
    ];
    const out = quickRelogFoodTemplates(favorites, recents, [], "lunch");
    assert.deepEqual(
      out.map((e) => e.name),
      ["Fav Salad", "Newer Sandwich"],
    );
  });

  it("at snack ranks snacks then recent others", () => {
    const favorites = [entry({ name: "Steak", mealType: "dinner", time: "19:00" })];
    const recents = [
      entry({ name: "Apple", mealType: "snack", time: "16:00" }),
      entry({ name: "Coffee", mealType: "snack", time: "10:00" }),
      entry({ name: "Oatmeal", mealType: "breakfast", time: "08:00" }),
    ];
    const out = quickRelogFoodTemplates(favorites, recents, [], "snack");
    assert.deepEqual(
      out.map((e) => e.name),
      ["Apple", "Coffee", "Steak", "Oatmeal"],
    );
  });

  it("dedupes by favoriteKey and respects limit", () => {
    const favorites = [entry({ name: "Shared", mealType: "lunch", calories: 200 })];
    const recents = [entry({ name: "Shared", mealType: "lunch", calories: 150 })];
    const frequents = [
      entry({ name: "Shared", mealType: "lunch", calories: 100 }),
      entry({ name: "Extra", mealType: "lunch" }),
      entry({ name: "More", mealType: "lunch" }),
    ];
    const out = quickRelogFoodTemplates(favorites, recents, frequents, "lunch", undefined, 2);
    assert.equal(out.length, 2);
    assert.equal(out[0].name, "Shared");
    assert.equal(out[0].calories, 200);
  });
});
