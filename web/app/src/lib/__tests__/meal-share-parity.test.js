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
    fiberG: m.fiber ?? null,
    sodiumMg: m.sodium ?? null,
    caffeineMg: m.caffeine ?? null,
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
      servingUnitOptions: (c.servingUnitOptions ?? []).map((u) => ({
        unit: u.unit,
        gramsPerUnit: u.gramsPerUnit,
        quantity: u.quantity ?? null,
      })),
      sugarG: c.sugar ?? null,
      addedSugarG: c.addedSugar ?? null,
      fiberG: c.fiber ?? null,
      saturatedFatG: c.saturatedFat ?? null,
      monounsaturatedFatG: c.monounsaturatedFat ?? null,
      polyunsaturatedFatG: c.polyunsaturatedFat ?? null,
      cholesterolMg: c.cholesterol ?? null,
      sodiumMg: c.sodium ?? null,
      potassiumMg: c.potassium ?? null,
      transFatG: c.transFat ?? null,
      calciumMg: c.calcium ?? null,
      ironMg: c.iron ?? null,
      magnesiumMg: c.magnesium ?? null,
      zincMg: c.zinc ?? null,
      vitaminAMcg: c.vitaminA ?? null,
      vitaminCMg: c.vitaminC ?? null,
      vitaminDMcg: c.vitaminD ?? null,
      vitaminB12Mcg: c.vitaminB12 ?? null,
      vitaminEMg: c.vitaminE ?? null,
      vitaminKMcg: c.vitaminK ?? null,
      folateMcg: c.folate ?? null,
      omega3G: c.omega3 ?? null,
      caffeineMg: c.caffeine ?? null,
      emoji: c.emoji ?? null,
      selectedServingQuantity: c.selectedServingQuantity ?? null,
      selectedServingUnit: c.selectedServingUnit ?? null,
    })),
  }));

  const hash = encodeMealShare(entries);
  assert.match(hash, /^#\/add-meal\?d=/);
  const decoded = decodeMealShare(hash);
  assert.ok(decoded);
  assert.equal(decoded.length, sample.meals.length);
  assert.equal(decoded[0].name, sample.meals[0].name);
  assert.equal(decoded[0].calories, sample.meals[0].calories);
  assert.equal(decoded[0].fiberG, 6);
  assert.equal(decoded[0].caffeineMg, 95);
  assert.equal(decoded[0].servingUnitOptions?.[0].gramsPerUnit, 200);
  assert.deepEqual(decoded[0].constituents, []);
  assert.equal(decoded[1].constituents?.length, 2);
  assert.equal(decoded[1].constituents?.[0].selectedServingUnit, "piece");
  assert.equal(decoded[1].constituents?.[0].servingUnitOptions?.[0].gramsPerUnit, 75);
  // v3: per-constituent micros ride the share wire (Codeberg #86).
  const chicken = decoded[1].constituents?.[0];
  assert.equal(chicken.sugarG, 0); // explicit zero survives, not omitted
  assert.equal(chicken.saturatedFatG, 4.5);
  assert.equal(chicken.cholesterolMg, 145);
  assert.equal(chicken.sodiumMg, 320);
  assert.equal(chicken.potassiumMg, 620);
  assert.equal(chicken.ironMg, 1);
  assert.equal(chicken.zincMg, 3.4);
  assert.equal(chicken.vitaminB12Mcg, 0.5);
  assert.equal(chicken.magnesiumMg, null); // absent keys decode to null
  const rice = decoded[1].constituents?.[1];
  assert.equal(rice.sugarG, 0.5);
  assert.equal(rice.fiberG, 1.2);
  assert.equal(rice.sodiumMg, 160);
  assert.equal(rice.magnesiumMg, 20);
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

test("decode rejects oversized payloads (Android MealShare cap)", () => {
  const big = { v: 2, meals: [{ name: "x".repeat(200_000), calories: 1, protein: 1, carbs: 1, fat: 1 }] };
  const b64 = btoa(unescape(encodeURIComponent(JSON.stringify(big))))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  assert.equal(decodeMealShare(`#/add-meal?d=${b64}`), null);
});

test("decode caps meal count and clamps hostile numbers (Android parity)", () => {
  const meals = Array.from({ length: 120 }, (_, i) => ({
    name: `meal-${i}`,
    calories: i === 0 ? 999999999 : 200,
    protein: i === 0 ? -5 : 10,
    sodium: i === 1 ? 9999999 : null,
  }));
  const payload = JSON.stringify({ v: 2, meals });
  const b64 = btoa(unescape(encodeURIComponent(payload)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  const decoded = decodeMealShare(`#/add-meal?d=${b64}`);
  assert.equal(decoded.length, 50); // Android MealShare.MAX_MEALS
  assert.equal(decoded[0].calories, 10000); // clamped, not absurd
  assert.equal(decoded[0].proteinG, 0); // negative clamped to 0
  assert.equal(decoded[1].sodiumMg, 100000); // micro clamp
});

test("decode strips control/bidi chars and caps name length (Android parity)", () => {
  const payload = JSON.stringify({
    v: 2,
    meals: [{ name: "evil\u0000name\u202e", calories: 100, protein: 1, carbs: 1, fat: 1 }],
  });
  const b64 = btoa(unescape(encodeURIComponent(payload)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  const decoded = decodeMealShare(`#/add-meal?d=${b64}`);
  assert.equal(decoded[0].name, "evilname");
});
