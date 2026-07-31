// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { encodeMealShare, decodeMealShare, MEAL_SHARE_VERSION } from "../meal-share.js";
import { loadParityFixture } from "../parity-fixtures.js";

test("parity meal-share sample round-trips through encode/decode", () => {
  const sample = loadParityFixture("meal-share-sample.json");
  assert.equal(sample.v, MEAL_SHARE_VERSION);
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
    servingUnitOptions: (m.servingUnitOptions ?? []).map((u) => ({
      unit: u.unit,
      gramsPerUnit: u.gramsPerUnit,
      quantity: u.quantity ?? null,
    })),
    selectedServingUnit: m.selectedServingUnit ?? null,
    selectedServingQuantity: m.selectedServingQuantity ?? null,
    constituents: (m.constituents ?? []).map((c) => ({
      name: c.name,
      calories: c.calories,
      proteinG: c.protein ?? 0,
      carbsG: c.carbs ?? 0,
      fatG: c.fat ?? 0,
      servingSizeGrams: c.servingSizeGrams ?? 0,
      emoji: c.emoji ?? null,
      servingUnitOptions: (c.servingUnitOptions ?? []).map((u) => ({
        unit: u.unit,
        gramsPerUnit: u.gramsPerUnit,
        quantity: u.quantity ?? null,
      })),
      selectedServingUnit: c.selectedServingUnit ?? null,
      selectedServingQuantity: c.selectedServingQuantity ?? null,
    })),
  }));

  const hash = encodeMealShare(entries);
  assert.match(hash, /^#\/add-meal\?d=/);
  const decoded = decodeMealShare(hash);
  assert.ok(decoded);
  assert.equal(decoded.length, sample.meals.length);
  assert.equal(decoded[0].name, sample.meals[0].name);
  assert.equal(decoded[0].calories, sample.meals[0].calories);
  assert.equal(decoded[0].selectedServingUnit, "bowl");
  assert.equal(decoded[0].servingUnitOptions?.[0].gramsPerUnit, 200);
  assert.deepEqual(decoded[0].constituents, []);
  assert.equal(decoded[1].constituents?.length, 2);
  assert.equal(decoded[1].constituents?.[0].selectedServingUnit, "piece");
  assert.equal(decoded[1].constituents?.[0].servingUnitOptions?.[0].gramsPerUnit, 75);
});

test("decode accepts legacy meal-share v1", () => {
  const payload = JSON.stringify({
    v: 1,
    meals: [{ name: "Toast", calories: 120, protein: 4, carbs: 20, fat: 2, mealType: "breakfast", servingSizeGrams: 40 }],
  });
  const b64 = btoa(unescape(encodeURIComponent(payload)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  const decoded = decodeMealShare(`#/add-meal?d=${b64}`);
  assert.ok(decoded);
  assert.equal(decoded[0].name, "Toast");
  assert.deepEqual(decoded[0].constituents, []);
});
