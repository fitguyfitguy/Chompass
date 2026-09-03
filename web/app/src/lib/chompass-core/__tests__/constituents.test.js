// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  parseConstituentsFromPrediction,
  reconcileConstituents,
  scaleConstituent,
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

test("parseConstituentsFromPrediction_readsConstituentMicros", () => {
  const rows = parseConstituentsFromPrediction({
    constituents: [
      {
        name: "Egg",
        calories: 70,
        protein: 6,
        carbs: 0.5,
        fat: 5,
        serving_size_grams: 50,
        sugar: 0.5,
        added_sugar: 0,
        fiber: 0,
        saturated_fat: 1.6,
        monounsaturated_fat: 2,
        polyunsaturated_fat: 0.7,
        cholesterol: 186,
        sodium: 70,
        potassium: 63,
        trans_fat: 0,
        calcium: 25,
        iron: 0.9,
        magnesium: 5,
        zinc: 0.6,
        vitamin_a: 80,
        vitamin_c: 0,
        vitamin_d: 1,
        vitamin_b12: 0.4,
        vitamin_e: 0.5,
        vitamin_k: 0.1,
        folate: 24,
        omega_3: 0.03,
        // caffeine is entry-level only — never parsed per constituent.
        caffeine: 5,
      },
      {
        name: "Toast",
        calories: 80,
        protein: 3,
        carbs: 14,
        fat: 1,
        serving_size_grams: 30,
      },
    ],
  });
  assert.equal(rows[0].sugarG, 0.5);
  assert.equal(rows[0].addedSugarG, 0);
  assert.equal(rows[0].saturatedFatG, 1.6);
  assert.equal(rows[0].monounsaturatedFatG, 2);
  assert.equal(rows[0].polyunsaturatedFatG, 0.7);
  assert.equal(rows[0].cholesterolMg, 186);
  assert.equal(rows[0].sodiumMg, 70);
  assert.equal(rows[0].potassiumMg, 63);
  assert.equal(rows[0].calciumMg, 25);
  assert.equal(rows[0].ironMg, 0.9);
  assert.equal(rows[0].vitaminAMcg, 80);
  assert.equal(rows[0].vitaminB12Mcg, 0.4);
  assert.equal(rows[0].vitaminKMcg, 0.1);
  assert.equal(rows[0].folateMcg, 24);
  assert.equal(rows[0].omega3G, 0.03);
  assert.equal(rows[0].caffeineMg, undefined);
  // Absent keys parse as null; non-finite and negative values are ignored.
  assert.deepEqual(
    [rows[1].sugarG, rows[1].cholesterolMg, rows[1].omega3G],
    [null, null, null],
  );
  const bad = parseConstituentsFromPrediction({
    constituents: [
      {
        name: "Weird",
        calories: 10,
        protein: 1,
        carbs: 1,
        fat: 1,
        serving_size_grams: 10,
        sugar: "lots",
        sodium: -5,
      },
    ],
  });
  assert.equal(bad[0].sugarG, null);
  assert.equal(bad[0].sodiumMg, null);
});

test("scaleConstituent_scalesMicrosWithGramsFactor", () => {
  const row = {
    name: "Egg",
    calories: 70,
    proteinG: 6,
    carbsG: 0.5,
    fatG: 5,
    servingSizeGrams: 50,
    servingUnitOptions: [],
    selectedServingUnit: null,
    selectedServingQuantity: null,
    sugarG: 0.34,
    cholesterolMg: 186,
    sodiumMg: 70,
    fiberG: null,
  };
  const doubled = scaleConstituent(row, 2);
  assert.equal(doubled.servingSizeGrams, 100);
  assert.equal(doubled.sugarG, 0.7); // 0.68 -> round1
  assert.equal(doubled.cholesterolMg, 372);
  assert.equal(doubled.sodiumMg, 140);
  assert.equal(doubled.fiberG, null); // null micros stay null
  // Rounding clamps at zero, never negative.
  const shrunk = scaleConstituent({ ...row, sugarG: 0.01, servingSizeGrams: 50 }, 0.01);
  assert.equal(shrunk.sugarG, 0);
});

test("reconcileConstituents_letsMicrosRideTheGramsFactor", () => {
  const meal = reconcileConstituents({
    calories: 200,
    proteinG: 14,
    carbsG: 18,
    fatG: 8,
    quantityG: 100,
    constituents: [
      {
        name: "A",
        calories: 100,
        proteinG: 7,
        carbsG: 9,
        fatG: 4,
        servingSizeGrams: 50,
        sugarG: 5,
        sodiumMg: 100,
        caffeineMg: 8,
      },
      {
        name: "B",
        calories: 100,
        proteinG: 7,
        carbsG: 9,
        fatG: 4,
        servingSizeGrams: 50,
        sodiumMg: 100,
      },
    ],
  });
  assert.equal(meal.constituents.length, 2);
  // Rows are near-exact so the grams factor is ~1; micros ride unscaled sums.
  const [a, b] = meal.constituents;
  assert.equal(a.sugarG, 5);
  assert.equal(a.sodiumMg, 100);
  assert.equal(a.caffeineMg, 8);
  assert.equal(b.sugarG, null); // null micros survive reconcile
  assert.equal(b.sodiumMg, 100);
});

test("reconcileConstituents_scalesMicrosWhenGramsShift", () => {
  // Rows sum to 200g but the meal is 160g — every row scales by 0.8
  // (still inside the ±50% reconcile gate).
  const meal = reconcileConstituents({
    calories: 200,
    proteinG: 14,
    carbsG: 18,
    fatG: 8,
    quantityG: 160,
    constituents: [
      {
        name: "A",
        calories: 100,
        proteinG: 7,
        carbsG: 9,
        fatG: 4,
        servingSizeGrams: 100,
        sodiumMg: 200,
        sugarG: 4,
      },
      {
        name: "B",
        calories: 100,
        proteinG: 7,
        carbsG: 9,
        fatG: 4,
        servingSizeGrams: 100,
        sodiumMg: 200,
      },
    ],
  });
  assert.equal(meal.constituents.length, 2);
  for (const row of meal.constituents) {
    assert.equal(row.servingSizeGrams, 80);
    assert.equal(row.sodiumMg, 160); // scaled by 0.8
  }
  assert.equal(meal.constituents[0].sugarG, 3.2);
  assert.equal(meal.constituents[1].sugarG, null);
});
