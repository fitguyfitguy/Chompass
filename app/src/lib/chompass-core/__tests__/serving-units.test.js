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
  applyQuantityInput,
  isQuantityExpression,
  displayUnit,
  culinaryUnitKey,
  GRAMS_OPTION,
  HEURISTIC_RULES,
} from "../serving-units.js";

test("displayUnit_localizesAppGeneratedServingUnit", () => {
  const serving = { unit: "serving", gramsPerUnit: 250, quantity: 1 };
  // Localized labels win over the raw English unit.
  assert.equal(displayUnit(serving, 1, "Portion", "Portionen"), "Portion");
  assert.equal(displayUnit(serving, 2, "Portion", "Portionen"), "Portionen");
  assert.equal(displayUnit(serving, 1.5, "Portion", "Portionen"), "Portionen");
  // Null quantity (non-selected dropdown item) reads as singular.
  assert.equal(displayUnit(serving, null, "Portion", "Portionen"), "Portion");
  // "servings" id maps too.
  assert.equal(displayUnit({ unit: "servings", gramsPerUnit: 250 }, 3, "Portion", "Portionen"), "Portionen");
  // Without labels the raw English pluralization is preserved.
  assert.equal(displayUnit(serving, 1), "serving");
  assert.equal(displayUnit(serving, 2), "servings");
  // Non-serving units ignore the labels.
  const slice = { unit: "slice", gramsPerUnit: 30 };
  assert.equal(displayUnit(slice, 2, "Portion", "Portionen"), "slices");
});

test("displayUnit_localizesCulinaryUnits", () => {
  const labels = { cup: ["Tasse", "Tassen"], tbsp: ["EL", "EL"], tsp: ["TL", "TL"] };
  assert.equal(displayUnit({ unit: "cup", gramsPerUnit: 240 }, 1, undefined, undefined, labels), "Tasse");
  assert.equal(displayUnit({ unit: "cup", gramsPerUnit: 240 }, 2.1, undefined, undefined, labels), "Tassen");
  assert.equal(displayUnit({ unit: "tblsp", gramsPerUnit: 15 }, 2, undefined, undefined, labels), "EL");
  assert.equal(displayUnit({ unit: "teaspoon", gramsPerUnit: 5 }, 3, undefined, undefined, labels), "TL");
  assert.equal(displayUnit({ unit: "cup", gramsPerUnit: 240 }, 2), "cups");
  assert.equal(culinaryUnitKey("tblsp"), "tbsp");
  assert.equal(culinaryUnitKey("slice"), null);
});

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

test("applyQuantityInput_plainNumbersParseAsBefore", () => {
  assert.equal(applyQuantityInput("20", 50), 20);
  assert.equal(applyQuantityInput("1.5", 50), 1.5);
  assert.equal(applyQuantityInput("20,5", 50), 20.5);
});

test("applyQuantityInput_deltasApplyToCurrent", () => {
  assert.equal(applyQuantityInput("+20", 50), 70);
  assert.equal(applyQuantityInput("+2.5", 50), 52.5);
  assert.equal(applyQuantityInput("-10", 50), 40);
  assert.equal(applyQuantityInput("-60", 50), -10);
  assert.equal(applyQuantityInput("+20", null), 20);
  assert.equal(applyQuantityInput("-10", null), -10);
});

test("applyQuantityInput_expressionsAreAbsoluteArithmetic", () => {
  assert.equal(applyQuantityInput("50×2", 50), 100);
  assert.equal(applyQuantityInput("50*2", 50), 100);
  assert.equal(applyQuantityInput("200−30", 50), 170);
  assert.equal(applyQuantityInput("200-30", 50), 170);
  assert.equal(applyQuantityInput("100÷4", 50), 25);
  assert.equal(applyQuantityInput("100/4", 50), 25);
  assert.equal(applyQuantityInput("20+20", 50), 40);
  assert.equal(applyQuantityInput("50×2", null), 100);
});

test("applyQuantityInput_respectsOperatorPrecedence", () => {
  assert.equal(applyQuantityInput("2+3×4", 50), 14);
  assert.equal(applyQuantityInput("1+9÷2", 50), 5.5);
  assert.equal(applyQuantityInput("10−5×2", 50), 0);
});

test("applyQuantityInput_acceptsWhitespaceAndCommaDecimals", () => {
  assert.equal(applyQuantityInput(" 50 × 2 ", 50), 100);
  assert.equal(applyQuantityInput("50×2.5", 50), 125);
  assert.equal(applyQuantityInput("50×2,5", 50), 125);
});

test("applyQuantityInput_malformedReturnsNull", () => {
  assert.equal(applyQuantityInput("50×", 50), null);
  assert.equal(applyQuantityInput("×2", 50), null);
  assert.equal(applyQuantityInput("50××2", 50), null);
  assert.equal(applyQuantityInput("50÷0", 50), null);
  assert.equal(applyQuantityInput("1+×2", 50), null);
  assert.equal(applyQuantityInput("+", 50), null);
  assert.equal(applyQuantityInput("-", 50), null);
  assert.equal(applyQuantityInput("", 50), null);
  assert.equal(applyQuantityInput("   ", 50), null);
});

test("isQuantityExpression_distinguishesDeltas", () => {
  assert.equal(isQuantityExpression("50×2"), true);
  assert.equal(isQuantityExpression("200−30"), true);
  assert.equal(isQuantityExpression("+20"), false);
  assert.equal(isQuantityExpression("-20"), false);
  assert.equal(isQuantityExpression("50"), false);
  assert.equal(isQuantityExpression(""), false);
});
