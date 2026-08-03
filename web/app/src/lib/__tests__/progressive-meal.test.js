// @ts-check
import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import {
  addToProgressiveMeal,
  discardProgressiveMeal,
  draftTotals,
  getProgressiveMeal,
  progressiveMealToFoodEntries,
  removeProgressiveMealItem,
  updateProgressiveMealMeta,
} from "../progressive-meal.js";

function analysis(name, calories, proteinG, carbsG, fatG, quantityG) {
  return {
    id: crypto.randomUUID(),
    name,
    mealType: "lunch",
    date: "2026-08-03",
    time: "12:00",
    calories,
    proteinG,
    carbsG,
    fatG,
    quantityG,
    source: "ai_estimated",
    servingUnitOptions: [],
    selectedServingUnit: null,
    selectedServingQuantity: null,
  };
}

describe("progressive meal draft", () => {
  beforeEach(() => {
    discardProgressiveMeal();
  });

  it("accumulates items and totals", () => {
    addToProgressiveMeal({ analysis: analysis("Buckwheat", 200, 7, 40, 2, 180) });
    addToProgressiveMeal({ analysis: analysis("Chicken", 250, 40, 0, 8, 120) });
    const d = getProgressiveMeal();
    assert.ok(d);
    assert.equal(d.items.length, 2);
    const totals = draftTotals(d);
    assert.equal(totals.calories, 450);
    assert.equal(totals.proteinG, 47);
    assert.equal(totals.carbsG, 40);
    assert.equal(totals.fatG, 10);
  });

  it("toFoodEntries shares recipeLogId and meal type", () => {
    addToProgressiveMeal({ analysis: analysis("A", 100, 10, 5, 2, 100), mealType: "dinner" });
    addToProgressiveMeal({ analysis: analysis("B", 50, 1, 8, 1, 50) });
    updateProgressiveMealMeta("Plate", "lunch");
    const d = getProgressiveMeal();
    assert.ok(d);
    const recipeLogId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    const entries = progressiveMealToFoodEntries(d, {
      date: "2026-08-03",
      time: "12:00",
      recipeLogId,
    });
    assert.equal(entries.length, 2);
    assert.ok(entries.every((e) => e.recipeLogId === recipeLogId));
    assert.ok(entries.every((e) => e.mealType === "lunch"));
    assert.equal(entries[0].name, "A");
    assert.equal(entries[1].name, "B");
    assert.equal(entries[0].id === entries[1].id, false);
  });

  it("remove last item clears draft", () => {
    addToProgressiveMeal({ analysis: analysis("Only", 10, 1, 1, 1, 10) });
    const id = getProgressiveMeal()?.items[0].id;
    assert.ok(id);
    removeProgressiveMealItem(id);
    assert.equal(getProgressiveMeal(), null);
  });
});
