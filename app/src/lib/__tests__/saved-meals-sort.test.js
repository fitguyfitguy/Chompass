// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  filterHistoryTemplates,
  recentTemplatesFrom,
  sortHistoryTemplates,
} from "../saved-meals.js";

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
    recipeLogId: partial.recipeLogId ?? null,
  };
}

const oats = entry({ name: "Oats", time: "08:00", calories: 150 });
const oatmeal = entry({ name: "Oatmeal", time: "10:00", calories: 400 });
const apple = entry({ name: "apple", time: "14:00", calories: 200 });
const avocado = entry({ name: "Avocado", time: "09:00", calories: 200 });
const banana = entry({ name: "Banana", time: "16:00", calories: 90 });
const unique = [oats, oatmeal, apple, avocado, banana];

describe("sortHistoryTemplates (Android SavedMealsSort parity, #76)", () => {
  it("recent keeps newest-first unique keys", () => {
    const collapsed = recentTemplatesFrom(
      [oats, entry({ name: "Oats", time: "07:00", calories: 80 }), oatmeal, apple, avocado, banana],
      Infinity,
    );
    assert.deepEqual(
      sortHistoryTemplates(collapsed, "recent").map((e) => e.name),
      ["Banana", "apple", "Oatmeal", "Avocado", "Oats"],
    );
    assert.equal(collapsed.find((e) => e.name === "Oats")?.calories, 150);
  });

  it("A–Z is case-insensitive", () => {
    assert.deepEqual(
      sortHistoryTemplates(unique, "name").map((e) => e.name),
      ["apple", "Avocado", "Banana", "Oatmeal", "Oats"],
    );
  });

  it("size is kcal desc with name tiebreak", () => {
    assert.deepEqual(
      sortHistoryTemplates(unique, "size").map((e) => e.name),
      ["Oatmeal", "apple", "Avocado", "Oats", "Banana"],
    );
  });

  it("search filter is independent of sort (filter then sort)", () => {
    const filtered = filterHistoryTemplates(unique, "oat");
    assert.deepEqual(new Set(filtered.map((e) => e.name)), new Set(["Oats", "Oatmeal"]));
    assert.deepEqual(sortHistoryTemplates(filtered, "size").map((e) => e.name), ["Oatmeal", "Oats"]);
    assert.deepEqual(sortHistoryTemplates(filtered, "name").map((e) => e.name), ["Oatmeal", "Oats"]);
    assert.deepEqual(sortHistoryTemplates(filtered, "recent").map((e) => e.name), ["Oatmeal", "Oats"]);
  });

  it("blank search returns the input list", () => {
    assert.equal(filterHistoryTemplates(unique, "  "), unique);
  });
});
