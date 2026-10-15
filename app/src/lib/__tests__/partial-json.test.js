// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { FoodPartialJsonAssembler, extractPartial, partialFromEstimate } from "../ai/partial-json.js";
import { buildCorrectDiff } from "../ai/correct-diff.js";
import { analyzeFoodEntry } from "../ai/food-analyze.js";
import { PROVIDERS } from "../ai/providers.js";

test("partialJson_ignoresIncompleteString", () => {
  const a = new FoodPartialJsonAssembler();
  assert.equal(a.push('{"name":"Piz'), null);
});

test("partialJson_emitsNameWhenStringCloses", () => {
  const a = new FoodPartialJsonAssembler();
  a.push('{"name":"Piz');
  const partial = a.push('za","calories":');
  assert.ok(partial);
  assert.equal(partial.name, "Pizza");
  assert.equal(partial.calories, null);
});

test("partialJson_emitsNumbersOnlyWhenComplete", () => {
  const a = new FoodPartialJsonAssembler();
  a.push('{"name":"Soup","calories":12');
  assert.equal(a.current()?.calories, null);
  const partial = a.push("0,");
  assert.equal(partial?.calories, 120);
});

test("partialJson_advancesMacros", () => {
  const json =
    '{"name":"Oatmeal","calories":300,"proteinG":10.5,"carbsG":45,"fatG":6,"quantityG":250,"fiberG":4}';
  const a = new FoodPartialJsonAssembler();
  /** @type {any} */
  let last = null;
  for (const chunk of json.match(/.{1,11}/g) || []) {
    last = a.push(chunk) || last;
  }
  assert.equal(last.name, "Oatmeal");
  assert.equal(last.calories, 300);
  assert.equal(last.proteinG, 10.5);
  assert.equal(last.quantityG, 250);
  assert.ok(last.micronutrientCount >= 1);
});

test("partialFromEstimate_marksStreamingFalse", () => {
  const p = partialFromEstimate({ name: "Bread", calories: 100, proteinG: 4, fiberG: 2 }, false);
  assert.equal(p.streaming, false);
  assert.equal(p.name, "Bread");
});

test("extractPartial_emptyReturnsNull", () => {
  assert.equal(extractPartial("thinking…"), null);
});

test("buildCorrectDiff_highlightsChanges", () => {
  const before = { name: "Pizza", calories: 800, proteinG: 30, carbsG: 90, fatG: 35, quantityG: 360 };
  const after = { name: "Pepperoni pizza", calories: 920, proteinG: 34, carbsG: 90, fatG: 35, quantityG: 400 };
  const rows = buildCorrectDiff(before, after);
  assert.equal(rows.length, 4);
  assert.ok(rows.some((r) => r.label === "Name"));
  assert.ok(rows.some((r) => r.label === "Serving" && r.after === "400 g"));
});

test("analyzeFoodEntry_emitsPartialsWhenStreamed", async () => {
  const original = PROVIDERS.openai_compatible.send;
  /** @type {any[]} */
  const partials = [];
  PROVIDERS.openai_compatible.send = async (_config, req) => {
    const text = JSON.stringify({
      name: "Yogurt",
      mealType: "breakfast",
      calories: 150,
      proteinG: 12,
      carbsG: 15,
      fatG: 4,
    });
    if (req.onDelta) {
      for (const chunk of text.match(/.{1,8}/g) || []) req.onDelta(chunk);
    }
    return { text, toolCalls: [] };
  };
  try {
    const result = await analyzeFoodEntry({
      providerId: "openai_compatible",
      config: { apiKey: "test" },
      text: "yogurt",
      prefsOverride: /** @type {any} */ ({ aiFallbackEnabled: false }),
      onPartial: (p) => partials.push(p),
    });
    assert.equal(result.name, "Yogurt");
    assert.ok(partials.length >= 1);
    assert.ok(partials.some((p) => p.name === "Yogurt"));
  } finally {
    PROVIDERS.openai_compatible.send = original;
  }
});

test("analyzeFoodEntry_withoutOnPartial_skipsStreamingHookStillWorks", async () => {
  const original = PROVIDERS.anthropic.send;
  PROVIDERS.anthropic.send = async () => ({
    text: JSON.stringify({
      name: "Tea",
      mealType: "snack",
      calories: 2,
      proteinG: 0,
      carbsG: 0,
      fatG: 0,
    }),
    toolCalls: [],
  });
  try {
    const result = await analyzeFoodEntry({
      providerId: "anthropic",
      config: { apiKey: "test" },
      text: "tea",
      prefsOverride: /** @type {any} */ ({ aiFallbackEnabled: false }),
      onPhase: () => {},
    });
    assert.equal(result.name, "Tea");
  } finally {
    PROVIDERS.anthropic.send = original;
  }
});
