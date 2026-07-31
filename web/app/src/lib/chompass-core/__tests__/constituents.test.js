// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  parseConstituentsFromPrediction,
  reconcileConstituents,
  scaleAllConstituents,
  aggregatesFromConstituents,
  applyConstituentDisplayEdit,
  MAX_CONSTITUENTS,
} from "../constituents.js";

test("parseConstituentsFromPrediction_readsSnakeCaseUnitOptions", () => {
  const rows = parseConstituentsFromPrediction({
    constituents: [
      {
        name: "Egg",
        calories: 70,
        protein: 6,
        carbs: 0.5,
        fat: 5,
        serving_size_grams: 50,
        emoji: "🥚",
        unit_options: [{ unit: "piece", quantity: 1, grams_per_unit: 50 }],
      },
      {
        name: "Toast",
        calories: 80,
        protein: 3,
        carbs: 14,
        fat: 1,
        serving_size_grams: 30,
        unit_options: [{ unit: "slice", quantity: 1, grams_per_unit: 30 }],
      },
    ],
  });
  assert.equal(rows.length, 2);
  assert.equal(rows[0].name, "Egg");
  assert.equal(rows[0].proteinG, 6);
  assert.equal(rows[0].servingSizeGrams, 50);
  assert.equal(rows[0].emoji, "🥚");
  assert.equal(rows[0].selectedServingUnit, "piece");
  assert.equal(rows[0].servingUnitOptions[0].gramsPerUnit, 50);
  assert.equal(rows[1].selectedServingUnit, "slice");
});

test("parseConstituentsFromPrediction_skipsInvalidAndCaps", () => {
  const many = Array.from({ length: MAX_CONSTITUENTS + 3 }, (_, i) => ({
    name: `Item ${i}`,
    calories: 10,
    protein: 1,
    carbs: 1,
    fat: 1,
    serving_size_grams: 10,
  }));
  many.unshift({ name: "", calories: 10, protein: 1, carbs: 1, fat: 1, serving_size_grams: 10 });
  many.push({ name: "Bad", calories: 10, protein: 1, carbs: 1, fat: 1, serving_size_grams: 0 });
  const rows = parseConstituentsFromPrediction({ constituents: many });
  assert.equal(rows.length, MAX_CONSTITUENTS);
  assert.equal(rows[0].name, "Item 0");
});

test("reconcileConstituents_scalesNearMissToMealTotals", () => {
  const meal = reconcileConstituents({
    calories: 200,
    proteinG: 10,
    carbsG: 20,
    fatG: 8,
    quantityG: 100,
    constituents: [
      {
        name: "A",
        calories: 95,
        proteinG: 5,
        carbsG: 10,
        fatG: 4,
        servingSizeGrams: 48,
        servingUnitOptions: [],
        selectedServingUnit: null,
        selectedServingQuantity: null,
      },
      {
        name: "B",
        calories: 95,
        proteinG: 5,
        carbsG: 10,
        fatG: 4,
        servingSizeGrams: 48,
        servingUnitOptions: [],
        selectedServingUnit: null,
        selectedServingQuantity: null,
      },
    ],
  });
  assert.equal(meal.constituents.length, 2);
  const agg = aggregatesFromConstituents(meal.constituents);
  assert.ok(agg);
  assert.equal(agg.calories, 200);
  assert.equal(agg.proteinG, 10);
  assert.equal(agg.carbsG, 20);
  assert.equal(agg.fatG, 8);
  assert.equal(agg.servingSizeGrams, 100);
});

test("reconcileConstituents_dropsWhenFarFromTotals", () => {
  const meal = reconcileConstituents({
    calories: 500,
    proteinG: 40,
    carbsG: 50,
    fatG: 20,
    quantityG: 300,
    constituents: [
      {
        name: "Tiny",
        calories: 10,
        proteinG: 1,
        carbsG: 1,
        fatG: 1,
        servingSizeGrams: 10,
        servingUnitOptions: [],
        selectedServingUnit: null,
        selectedServingQuantity: null,
      },
    ],
  });
  assert.deepEqual(meal.constituents, []);
  assert.equal(meal.calories, 500);
});

test("scaleAllConstituents_andAggregatesFromEdit", () => {
  const base = [
    {
      name: "Rice",
      calories: 100,
      proteinG: 2,
      carbsG: 22,
      fatG: 0.5,
      servingSizeGrams: 80,
      servingUnitOptions: [],
      selectedServingUnit: "g",
      selectedServingQuantity: 80,
    },
    {
      name: "Chicken",
      calories: 120,
      proteinG: 22,
      carbsG: 0,
      fatG: 3,
      servingSizeGrams: 70,
      servingUnitOptions: [{ unit: "piece", gramsPerUnit: 70, quantity: 1 }],
      selectedServingUnit: "piece",
      selectedServingQuantity: 1,
    },
  ];
  const doubled = scaleAllConstituents(base, 2);
  assert.equal(doubled[0].calories, 200);
  assert.equal(doubled[0].servingSizeGrams, 160);
  assert.equal(doubled[1].selectedServingQuantity, 2);

  const edited = doubled.map((row, i) =>
    i === 0
      ? {
          ...row,
          servingSizeGrams: 200,
          calories: 250,
          proteinG: 2.5,
          carbsG: 27.5,
          fatG: 0.6,
          selectedServingQuantity: 200,
        }
      : row,
  );
  const { rows, aggregate, servingGrams } = applyConstituentDisplayEdit(edited);
  assert.equal(rows.length, 2);
  assert.ok(aggregate);
  assert.equal(aggregate.calories, 250 + 240);
  assert.equal(servingGrams, 200 + 140);
  assert.equal(aggregatesFromConstituents(rows)?.servingSizeGrams, servingGrams);
});

test("applyConstituentDisplayEdit_ignoresBlankNamelessZeroRows", () => {
  const { rows, aggregate } = applyConstituentDisplayEdit([
    {
      name: "  ",
      calories: 0,
      proteinG: 0,
      carbsG: 0,
      fatG: 0,
      servingSizeGrams: 0,
      servingUnitOptions: [],
      selectedServingUnit: null,
      selectedServingQuantity: null,
    },
    {
      name: "Apple",
      calories: 95,
      proteinG: 0.5,
      carbsG: 25,
      fatG: 0.3,
      servingSizeGrams: 180,
      servingUnitOptions: [],
      selectedServingUnit: null,
      selectedServingQuantity: null,
    },
  ]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].name, "Apple");
  assert.equal(aggregate?.calories, 95);
});
