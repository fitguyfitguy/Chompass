// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import { formatOffPromptContext } from "../ai/off-prompt-context.js";
import { analyzeFoodEntry } from "../ai/food-analyze.js";
import { PROVIDERS } from "../ai/providers.js";

test("formatOffPromptContext_empty", () => {
  assert.equal(formatOffPromptContext([]), "");
});

test("formatOffPromptContext_singleHit", () => {
  const text = formatOffPromptContext([
    {
      barcode: "3017620422003",
      name: "Ferrero Nutella",
      servingGrams: 15,
      calories: 80,
      proteinG: 0.6,
      carbsG: 10.5,
      fatG: 4.3,
      sugarG: 10,
      fiberG: 0.5,
      sodiumMg: 5,
    },
  ]);
  assert.match(text, /Open Food Facts match detected/);
  assert.match(text, /barcode: 3017620422003/);
  assert.match(text, /name: Ferrero Nutella/);
  assert.match(text, /15g/);
  assert.match(text, /80 kcal/);
  assert.match(text, /P 0\.6 g/);
  assert.match(text, /also: sugar 10\.0 g/);
  assert.match(text, /per 100 g \(derived from labeled serving\)/);
  assert.match(text, /authoritative package label data/);
  assert.doesNotMatch(text, /matches detected/);
});

test("formatOffPromptContext_plural", () => {
  const text = formatOffPromptContext([
    { barcode: "111", name: "A", servingGrams: 100, calories: 100, proteinG: 1, carbsG: 2, fatG: 3 },
    { barcode: "222", name: "B", servingGrams: 50, calories: 50, proteinG: 0, carbsG: 0, fatG: 0 },
  ]);
  assert.match(text, /Open Food Facts matches detected/);
  assert.match(text, /barcode: 111/);
  assert.match(text, /barcode: 222/);
});

test("analyzeFoodEntry_appendsProductContextToUserText", async () => {
  const original = PROVIDERS.anthropic.send;
  /** @type {any} */
  let capturedReq = null;
  PROVIDERS.anthropic.send = async (_config, req) => {
    capturedReq = req;
    return {
      text: JSON.stringify({
        name: "Nutella toast",
        mealType: "breakfast",
        calories: 350,
        proteinG: 8,
        carbsG: 40,
        fatG: 16,
      }),
      toolCalls: [],
    };
  };
  try {
    await analyzeFoodEntry({
      providerId: "anthropic",
      config: { apiKey: "test-key" },
      text: "toast with spread",
      productContext:
        "Open Food Facts match detected in the attached photo(s):\n- barcode: 3017620422003\n  name: Nutella",
      prefsOverride: /** @type {any} */ ({ aiFallbackEnabled: false }),
    });
    assert.ok(capturedReq);
    assert.match(capturedReq.messages[0].text, /toast with spread/);
    assert.match(capturedReq.messages[0].text, /Open Food Facts match detected/);
    assert.match(capturedReq.messages[0].text, /3017620422003/);
  } finally {
    PROVIDERS.anthropic.send = original;
  }
});
