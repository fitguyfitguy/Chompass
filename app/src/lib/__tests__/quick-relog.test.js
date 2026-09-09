// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { quickRelogRowsFrom } from "../saved-meals.js";
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

describe("quickRelogRowsFrom", () => {
  it("recents are newest-first and unique by key", () => {
    const olderApple = entry({ name: "Apple", time: "08:00", calories: 80 });
    const banana = entry({ name: "Banana", time: "10:00" });
    const newerApple = entry({ name: "Apple", time: "14:00", calories: 120 });
    const cherry = entry({ name: "Cherry", time: "16:00" });
    const out = quickRelogRowsFrom([olderApple, banana, newerApple, cherry], []);
    assert.deepEqual(out.recents.map((e) => e.name), ["Cherry", "Apple", "Banana"]);
    assert.equal(out.recents.find((e) => e.name === "Apple")?.calories, 120);
    assert.equal(out.frequents.length, 0);
  });

  it("frequents are count-desc and exclude recent keys", () => {
    const apple = entry({ name: "Apple", date: "2024-06-01" });
    const banana = entry({ name: "Banana", date: "2024-05-01" });
    const cherry = entry({ name: "Cherry", date: "2024-05-02" });
    const frequentWindow = [apple, apple, apple, banana, banana, cherry];
    const out = quickRelogRowsFrom([apple], frequentWindow);
    assert.deepEqual(out.recents.map((e) => e.name), ["Apple"]);
    assert.deepEqual(out.frequents.map((e) => e.name), ["Banana", "Cherry"]);
  });

  it("honors perRow on each side", () => {
    const recents = Array.from({ length: 12 }, (_, i) =>
      entry({ name: `Recent ${i + 1}`, time: `${String(i + 1).padStart(2, "0")}:00` }),
    );
    const frequents = Array.from({ length: 12 }, (_, i) =>
      Array.from({ length: 13 - (i + 1) }, () =>
        entry({ name: `Freq ${i + 1}`, date: "2024-04-01" }),
      ),
    ).flat();
    const out = quickRelogRowsFrom(recents, frequents, 3);
    assert.equal(out.recents.length, 3);
    assert.equal(out.frequents.length, 3);
    assert.deepEqual(out.recents.map((e) => e.name), ["Recent 12", "Recent 11", "Recent 10"]);
    assert.deepEqual(out.frequents.map((e) => e.name), ["Freq 1", "Freq 2", "Freq 3"]);
  });

  it("empty recents plus some frequents yields only the frequent list", () => {
    const yogurt = entry({ name: "Yogurt", date: "2024-04-01" });
    const oats = entry({ name: "Oats", date: "2024-04-02" });
    const out = quickRelogRowsFrom([], [yogurt, yogurt, oats]);
    assert.equal(out.recents.length, 0);
    assert.deepEqual(out.frequents.map((e) => e.name), ["Yogurt", "Oats"]);
  });

  it("empty both yields empty rows", () => {
    const out = quickRelogRowsFrom([], []);
    assert.equal(out.recents.length, 0);
    assert.equal(out.frequents.length, 0);
  });
});
