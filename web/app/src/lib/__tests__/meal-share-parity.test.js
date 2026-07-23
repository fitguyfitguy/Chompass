// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { encodeMealShare, decodeMealShare } from "../meal-share.js";
import { loadParityFixture } from "../parity-fixtures.js";

test("parity meal-share sample round-trips through encode/decode", () => {
  const sample = loadParityFixture("meal-share-sample.json");
  assert.equal(sample.v, 1);
  assert.ok(Array.isArray(sample.meals) && sample.meals.length >= 1);

  const entries = sample.meals.map((m) => ({
    name: m.name,
    calories: m.calories,
    proteinG: m.protein ?? 0,
    carbsG: m.carbs ?? 0,
    fatG: m.fat ?? 0,
    fiberG: m.fiber ?? null,
    sodiumMg: m.sodium ?? null,
    mealType: m.mealType ?? "snack",
    quantityG: m.servingSizeGrams ?? null,
    note: m.customNote ?? null,
  }));

  const hash = encodeMealShare(entries);
  assert.match(hash, /^#\/add-meal\?d=/);
  const decoded = decodeMealShare(hash);
  assert.ok(decoded);
  assert.equal(decoded.length, sample.meals.length);
  assert.equal(decoded[0].name, sample.meals[0].name);
  assert.equal(decoded[0].calories, sample.meals[0].calories);
});
