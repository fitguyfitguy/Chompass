// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  formatQuantity,
  parseQuantity,
  pickerOptions,
  optionMatching,
  initialUnitId,
  initialQuantityText,
  matchingHeuristicRule,
  heuristicOptions,
  scaleNutrition,
  ensureServingUnits,
  GRAMS_OPTION,
  HEURISTIC_RULES,
} from "../serving-units.js";

test("formatQuantity_integersAndDecimals", () => {
  assert.equal(formatQuantity(2), "2");
  assert.equal(formatQuantity(1.5), "1.5");
  assert.equal(formatQuantity(0.25), "0.25");
  assert.equal(formatQuantity(12.34), "12.3");
});

test("parseQuantity_acceptsCommaDecimal", () => {
  assert.equal(parseQuantity("1.5"), 1.5);
  assert.equal(parseQuantity("1,5"), 1.5);
  assert.equal(parseQuantity(""), null);
  assert.equal(parseQuantity("  "), null);
});

test("pickerOptions_alwaysIncludesGramsFirst", () => {
  const picker = pickerOptions([{ unit: "slice", gramsPerUnit: 30 }]);
  assert.equal(picker[0].unit, "g");
  assert.equal(picker[1].unit, "slice");
  assert.equal(optionMatching("slice", picker).gramsPerUnit, 30);
  assert.equal(optionMatching("missing", picker).unit, GRAMS_OPTION.unit);
});

test("initialUnitId_prefersHeuristicThenGrams", () => {
  const opts = [{ unit: "piece", gramsPerUnit: 50 }];
  assert.equal(initialUnitId(null, opts), "piece");
  assert.equal(initialUnitId("g", opts), "g");
  assert.equal(initialUnitId(null, []), "g");
});

test("initialQuantityText_usesSelectedQuantityForNonGrams", () => {
  const opts = [{ unit: "slice", gramsPerUnit: 120 }];
  assert.equal(initialQuantityText(240, "slice", 2, opts), "2");
  assert.equal(initialQuantityText(120, "g", null, opts), "120");
});

test("matchingHeuristicRule_matchesWholeWords", () => {
  assert.equal(matchingHeuristicRule("Pepperoni pizza")?.id, "pizza");
  assert.equal(matchingHeuristicRule("Boiled egg")?.id, "egg");
  assert.equal(matchingHeuristicRule("Cheeseburger")?.id, "burger");
});

test("matchingHeuristicRule_avoidsSubstringFalsePositives", () => {
  assert.equal(matchingHeuristicRule("Eggplant parmesan"), null);
  assert.equal(matchingHeuristicRule("Cheesecake"), null);
});

test("matchingHeuristicRule_pluralsAndMultiWord", () => {
  assert.equal(matchingHeuristicRule("Turkey sandwiches")?.id, "sandwich");
  assert.equal(matchingHeuristicRule("Vanilla ice cream")?.id, "icecream");
  assert.equal(matchingHeuristicRule("Hot dog with mustard")?.id, "hotdog");
  assert.equal(matchingHeuristicRule("Chocolate protein bar")?.id, "bar");
});

test("matchingHeuristicRule_firstTableHit", () => {
  assert.equal(matchingHeuristicRule("Toast")?.id, "bread");
  assert.equal(matchingHeuristicRule("Toast")?.unit, "slice");
  assert.equal(matchingHeuristicRule("Toast")?.defaultGramsPerUnit, 30);
});

test("matchingHeuristicRule_unknown", () => {
  assert.equal(matchingHeuristicRule(""), null);
  assert.equal(matchingHeuristicRule("Grilled salmon fillet"), null);
});

test("heuristicRules_haveStableUniqueIds", () => {
  const ids = HEURISTIC_RULES.map((r) => r.id);
  assert.equal(ids.length, new Set(ids).size);
  assert.ok(HEURISTIC_RULES.every((r) => r.keywords.length > 0 && r.defaultGramsPerUnit > 0));
});

test("heuristicOptions_eggPiece", () => {
  const opts = heuristicOptions("Boiled egg", 50);
  assert.equal(opts.length, 1);
  assert.equal(opts[0].unit, "piece");
  assert.equal(opts[0].gramsPerUnit, 50);
});

test("scaleNutrition_halvesMacrosAndMicros", () => {
  const scaled = scaleNutrition(
    {
      calories: 200,
      proteinG: 10,
      carbsG: 20,
      fatG: 8,
      fiberG: 4,
      sodiumMg: 100,
      sugarG: null,
    },
    0.5
  );
  assert.equal(scaled.calories, 100);
  assert.equal(scaled.proteinG, 5);
  assert.equal(scaled.carbsG, 10);
  assert.equal(scaled.fatG, 4);
  assert.equal(scaled.fiberG, 2);
  assert.equal(scaled.sodiumMg, 50);
  assert.equal(scaled.sugarG, null);
});

test("ensureServingUnits_fillsHeuristicWhenMissing", () => {
  const ensured = ensureServingUnits({ name: "Pepperoni pizza", quantityG: 240 });
  assert.equal(ensured.servingUnitOptions[0]?.unit, "slice");
  assert.equal(ensured.selectedServingUnit, "slice");
  assert.equal(ensured.selectedServingQuantity, 2);
  assert.equal(ensured.quantityG, 240);
});
