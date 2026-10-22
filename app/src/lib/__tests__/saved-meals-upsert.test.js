// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { favoriteUpsertPlan, favoriteKey } from "../saved-meals.js";

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
    date: partial.date ?? "2026-08-01",
    time: partial.time ?? "12:00",
    source: partial.source ?? "barcode",
    note: null,
    grounding: null,
    recipeLogId: partial.recipeLogId ?? null,
    productMetadata: partial.productMetadata ?? null,
  };
}

describe("favoriteUpsertPlan (Android upsertFavorite parity)", () => {
  it("appends a new key with the entry's own id", () => {
    const fresh = entry({ name: "Cola" });
    const { next, stored, inserted } = favoriteUpsertPlan([], fresh);
    assert.equal(inserted, true);
    assert.equal(stored.id, fresh.id);
    assert.deepEqual(next, [stored]);
  });

  it("replaces in place keeping the stored id", () => {
    const existing = entry({ name: "Cola", id: "44444444-4444-4444-8444-444444444444", calories: 140 });
    const scanned = entry({ name: "Cola", calories: 139, source: "barcode" });
    const { next, stored, inserted } = favoriteUpsertPlan([existing], scanned);
    assert.equal(inserted, false);
    assert.equal(stored.id, existing.id);
    assert.equal(stored.calories, 139);
    assert.equal(next.length, 1);
    assert.equal(next[0].id, existing.id);
    // Case/whitespace differences still match the favoriteKey identity.
    assert.equal(favoriteKey(scanned), favoriteKey(existing));
  });

  it("normalizes recipeLogId to null on both paths", () => {
    const recipeId = "55555555-5555-4555-8555-555555555555";
    const existing = entry({ name: "Cola", recipeLogId: recipeId });
    const replaced = favoriteUpsertPlan([existing], entry({ name: "Cola", recipeLogId: recipeId }));
    assert.equal(replaced.stored.recipeLogId, null);
    const fresh = favoriteUpsertPlan([], entry({ name: "Fanta", recipeLogId: recipeId }));
    assert.equal(fresh.stored.recipeLogId, null);
  });

  it("a second save of the same name does not delete — list stays length 1", () => {
    const first = favoriteUpsertPlan([], entry({ name: "Cola" }));
    const second = favoriteUpsertPlan(first.next, entry({ name: "Cola", calories: 139 }));
    assert.equal(second.inserted, false);
    assert.equal(second.next.length, 1);
    // And saving another product appends instead of removing.
    const third = favoriteUpsertPlan(second.next, entry({ name: "Fanta" }));
    assert.equal(third.inserted, true);
    assert.equal(third.next.length, 2);
  });
});
