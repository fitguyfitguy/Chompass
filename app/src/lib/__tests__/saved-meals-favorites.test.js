// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  favoriteKey,
  normalizedFavoriteUpdate,
  favoriteNameTakenFrom,
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
    date: partial.date ?? "2026-08-01",
    time: partial.time ?? "12:00",
    source: "manual",
    note: null,
    grounding: null,
    recipeLogId: partial.recipeLogId ?? null,
  };
}

describe("normalizedFavoriteUpdate (Android updateFavorite parity, #66)", () => {
  it("keeps the id so sync LWW survives renames", () => {
    const fav = entry({ name: "Chicken", id: "11111111-1111-4111-8111-111111111111" });
    const updated = normalizedFavoriteUpdate({ ...fav, name: "Pollo" });
    assert.equal(updated.id, fav.id);
    assert.equal(updated.name, "Pollo");
  });

  it("normalizes recipeLogId to null", () => {
    const recipeId = "22222222-2222-4222-8222-222222222222";
    const fav = entry({ name: "Curry", recipeLogId: recipeId });
    assert.equal(normalizedFavoriteUpdate({ ...fav, recipeLogId: recipeId }).recipeLogId, null);
  });

  it("copies all other fields through", () => {
    const fav = entry({ name: "Oats", mealType: "breakfast", calories: 150 });
    const out = normalizedFavoriteUpdate({ ...fav });
    assert.equal(out.mealType, "breakfast");
    assert.equal(out.calories, 150);
    assert.deepEqual(Object.keys(out).sort(), Object.keys(fav).sort());
  });
});

describe("favoriteNameTakenFrom (rename collision, #66)", () => {
  const chickenFav = entry({ name: "Chicken", id: "33333333-3333-4333-8333-333333333333" });
  const oatsFav = entry({ name: "Oats" });
  const saladDiary = entry({ name: "Salad" });
  const diary = [saladDiary];
  const favorites = [chickenFav, oatsFav];

  it("blocks names used by diary rows", () => {
    assert.equal(favoriteNameTakenFrom("Salad", diary, favorites), true);
    assert.equal(favoriteNameTakenFrom(" salad ", diary, favorites), true);
  });

  it("blocks names used by other favorites", () => {
    assert.equal(favoriteNameTakenFrom("Oats", diary, favorites, chickenFav.id), true);
  });

  it("allows the favorite's own name (rename to itself)", () => {
    assert.equal(favoriteNameTakenFrom("Chicken", diary, favorites, chickenFav.id), false);
    assert.equal(favoriteNameTakenFrom("  chicken ", diary, favorites, chickenFav.id), false);
  });

  it("collides with itself when no selfId is passed", () => {
    assert.equal(favoriteNameTakenFrom("Chicken", diary, favorites), true);
  });

  it("allows fresh names and rejects blank keys", () => {
    assert.equal(favoriteNameTakenFrom("Pollo", diary, favorites, chickenFav.id), false);
    assert.equal(favoriteNameTakenFrom("   ", diary, favorites), false);
  });

  it("matches case-insensitively like favoriteKey", () => {
    assert.equal(favoriteKey({ name: "  CHICKEN " }), "chicken");
    assert.equal(favoriteNameTakenFrom("CHICKEN", diary, favorites, oatsFav.id), true);
  });
});
