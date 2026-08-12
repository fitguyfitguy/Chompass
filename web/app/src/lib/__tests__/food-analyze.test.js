// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  ANALYSIS_PHASE,
  ANALYSIS_PHASE_STEPS,
  analyzeFoodEntry,
  isAbortError,
  phaseLabel,
} from "../ai/food-analyze.js";
import { PROVIDERS } from "../ai/providers.js";

test("analysisPhases_matchAndroidCloudEntry", () => {
  assert.deepEqual(
    [...ANALYSIS_PHASE_STEPS],
    ["preparing", "looking_up_barcode", "calling_ai", "parsing"]
  );
  assert.equal(phaseLabel(ANALYSIS_PHASE.PREPARING), "Preparing request…");
  assert.equal(phaseLabel(ANALYSIS_PHASE.LOOKING_UP_BARCODE), "Checking barcodes…");
  assert.equal(phaseLabel(ANALYSIS_PHASE.CALLING_AI), "Calling AI…");
  assert.equal(phaseLabel(ANALYSIS_PHASE.PARSING), "Reading result…");
});

test("isAbortError_detectsAbortErrorName", () => {
  const err = new Error("aborted");
  err.name = "AbortError";
  assert.equal(isAbortError(err), true);
  assert.equal(isAbortError(new Error("boom")), false);
  assert.equal(isAbortError(null), false);
});

test("analyzeFoodEntry_emitsCallingAiThenParsing", async () => {
  const original = PROVIDERS.anthropic.send;
  /** @type {string[]} */
  const phases = [];
  PROVIDERS.anthropic.send = async () => ({
    text: JSON.stringify({
      name: "Oatmeal",
      mealType: "breakfast",
      calories: 300,
      proteinG: 10,
      carbsG: 50,
      fatG: 8,
    }),
    toolCalls: [],
  });
  try {
    const result = await analyzeFoodEntry({
      providerId: "anthropic",
      config: { apiKey: "test-key" },
      text: "oatmeal",
      prefsOverride: /** @type {any} */ ({ aiFallbackEnabled: false }),
      onPhase: (p) => phases.push(p),
    });
    assert.deepEqual(phases, [ANALYSIS_PHASE.CALLING_AI, ANALYSIS_PHASE.PARSING]);
    assert.equal(result.name, "Oatmeal");
    assert.equal(result.calories, 300);
    assert.equal(result.source, "ai_estimated");
  } finally {
    PROVIDERS.anthropic.send = original;
  }
});

test("analyzeFoodEntry_abortSkipsFallback", async () => {
  const originalAnthropic = PROVIDERS.anthropic.send;
  const originalGemini = PROVIDERS.gemini.send;
  let geminiCalls = 0;
  PROVIDERS.anthropic.send = async (_config, req) => {
    const err = new Error("The operation was aborted");
    err.name = "AbortError";
    // Ensure signal is marked aborted for the outer catch as well.
    if (req.signal && !req.signal.aborted) {
      // Caller already aborted; throw AbortError.
    }
    throw err;
  };
  PROVIDERS.gemini.send = async () => {
    geminiCalls += 1;
    return { text: "{}", toolCalls: [] };
  };
  try {
    const ac = new AbortController();
    ac.abort();
    await assert.rejects(
      () =>
        analyzeFoodEntry({
          providerId: "anthropic",
          config: { apiKey: "test-key" },
          text: "toast",
          signal: ac.signal,
          prefsOverride: /** @type {any} */ ({
            aiFallbackEnabled: true,
            fallbackAiProvider: "gemini",
            fallbackAiModel: "gemini-3.5-flash-lite",
          }),
        }),
      (err) => isAbortError(err)
    );
    assert.equal(geminiCalls, 0);
  } finally {
    PROVIDERS.anthropic.send = originalAnthropic;
    PROVIDERS.gemini.send = originalGemini;
  }
});

test("analyzeFoodEntry_sendsAllImagesOnMessage", async () => {
  const original = PROVIDERS.gemini.send;
  /** @type {any} */
  let capturedReq = null;
  PROVIDERS.gemini.send = async (_config, req) => {
    capturedReq = req;
    return {
      text: JSON.stringify({
        name: "Salad",
        mealType: "lunch",
        calories: 250,
        proteinG: 12,
        carbsG: 20,
        fatG: 10,
      }),
      toolCalls: [],
    };
  };
  const imgA = { mimeType: "image/jpeg", base64: "aaa" };
  const imgB = { mimeType: "image/jpeg", base64: "bbb" };
  const imgC = { mimeType: "image/jpeg", base64: "ccc" };
  try {
    await analyzeFoodEntry({
      providerId: "gemini",
      config: { apiKey: "test-key" },
      images: [imgA, imgB, imgC],
      prefsOverride: /** @type {any} */ ({ aiFallbackEnabled: false }),
    });
    assert.ok(capturedReq);
    const msg = capturedReq.messages[0];
    assert.deepEqual(msg.images, [imgA, imgB, imgC]);
    assert.equal(msg.image, undefined);
    assert.match(msg.text, /3 photos/);
    assert.doesNotMatch(msg.text, /first image included/);
  } finally {
    PROVIDERS.gemini.send = original;
  }
});

test("analyzeFoodEntry_singleImageUsesSingularField", async () => {
  const original = PROVIDERS.anthropic.send;
  /** @type {any} */
  let capturedReq = null;
  PROVIDERS.anthropic.send = async (_config, req) => {
    capturedReq = req;
    return {
      text: JSON.stringify({
        name: "Apple",
        mealType: "snack",
        calories: 95,
        proteinG: 0,
        carbsG: 25,
        fatG: 0,
      }),
      toolCalls: [],
    };
  };
  const img = { mimeType: "image/jpeg", base64: "xyz" };
  try {
    await analyzeFoodEntry({
      providerId: "anthropic",
      config: { apiKey: "test-key" },
      image: img,
      prefsOverride: /** @type {any} */ ({ aiFallbackEnabled: false }),
    });
    assert.ok(capturedReq);
    const msg = capturedReq.messages[0];
    assert.deepEqual(msg.image, img);
    assert.equal(msg.images, undefined);
  } finally {
    PROVIDERS.anthropic.send = original;
  }
});

test("analyzeFoodEntry_abortErrorFromSendSkipsFallback", async () => {
  const originalAnthropic = PROVIDERS.anthropic.send;
  const originalGemini = PROVIDERS.gemini.send;
  let geminiCalls = 0;
  PROVIDERS.anthropic.send = async () => {
    const err = new Error("The operation was aborted");
    err.name = "AbortError";
    throw err;
  };
  PROVIDERS.gemini.send = async () => {
    geminiCalls += 1;
    return { text: "{}", toolCalls: [] };
  };
  try {
    await assert.rejects(
      () =>
        analyzeFoodEntry({
          providerId: "anthropic",
          config: { apiKey: "test-key" },
          text: "toast",
          prefsOverride: /** @type {any} */ ({
            aiFallbackEnabled: true,
            fallbackAiProvider: "gemini",
            fallbackAiModel: "gemini-3.5-flash-lite",
          }),
        }),
      (err) => isAbortError(err)
    );
    assert.equal(geminiCalls, 0);
  } finally {
    PROVIDERS.anthropic.send = originalAnthropic;
    PROVIDERS.gemini.send = originalGemini;
  }
});

test("analyzeFoodEntry_parsesAndReconcilesConstituents", async () => {
  const original = PROVIDERS.anthropic.send;
  PROVIDERS.anthropic.send = async () => ({
    text: JSON.stringify({
      name: "Eggs and toast",
      mealType: "breakfast",
      calories: 200,
      proteinG: 14,
      carbsG: 18,
      fatG: 8,
      quantityG: 120,
      fiberG: 2,
      constituents: [
        {
          name: "Egg",
          calories: 90,
          protein: 8,
          carbs: 1,
          fat: 6,
          serving_size_grams: 55,
          emoji: "🥚",
          unit_options: [{ unit: "piece", quantity: 1, grams_per_unit: 55 }],
        },
        {
          name: "Toast",
          calories: 100,
          protein: 5,
          carbs: 16,
          fat: 2,
          serving_size_grams: 60,
          unit_options: [{ unit: "slice", quantity: 1, grams_per_unit: 60 }],
        },
      ],
    }),
    toolCalls: [],
  });
  try {
    const result = await analyzeFoodEntry({
      providerId: "anthropic",
      config: { apiKey: "test-key" },
      text: "eggs and toast",
      prefsOverride: /** @type {any} */ ({ aiFallbackEnabled: false }),
    });
    assert.equal(result.name, "Eggs and toast");
    assert.equal(result.fiberG, 2);
    assert.ok(Array.isArray(result.constituents));
    assert.equal(result.constituents.length, 2);
    const sumCal = result.constituents.reduce((a, c) => a + c.calories, 0);
    const sumG = result.constituents.reduce((a, c) => a + c.servingSizeGrams, 0);
    assert.equal(sumCal, 200);
    assert.equal(Math.round(sumG * 10) / 10, 120);
    assert.equal(result.constituents[0].selectedServingUnit, "piece");
  } finally {
    PROVIDERS.anthropic.send = original;
  }
});

test("analyzeFoodEntry_respectsMealConstituentsOptOut", async () => {
  const original = PROVIDERS.anthropic.send;
  /** @type {string|undefined} */
  let systemSeen;
  PROVIDERS.anthropic.send = async (_config, req) => {
    systemSeen = req.systemPrompt;
    return {
      text: JSON.stringify({
        name: "Eggs and toast",
        mealType: "breakfast",
        calories: 200,
        proteinG: 14,
        carbsG: 18,
        fatG: 8,
        quantityG: 120,
        constituents: [
          {
            name: "Egg",
            calories: 100,
            protein: 7,
            carbs: 1,
            fat: 7,
            serving_size_grams: 60,
          },
          {
            name: "Toast",
            calories: 100,
            protein: 7,
            carbs: 17,
            fat: 1,
            serving_size_grams: 60,
          },
        ],
      }),
      toolCalls: [],
    };
  };
  try {
    const result = await analyzeFoodEntry({
      providerId: "anthropic",
      config: { apiKey: "test-key" },
      text: "eggs and toast",
      prefsOverride: /** @type {any} */ ({
        aiFallbackEnabled: false,
        mealConstituentsEnabled: false,
      }),
    });
    assert.ok(systemSeen);
    assert.equal(systemSeen.includes("constituents"), false);
    assert.deepEqual(result.constituents, []);
  } finally {
    PROVIDERS.anthropic.send = original;
  }
});

test("analyzeFoodEntry_dropsFarConstituentsKeepsMicros", async () => {
  const original = PROVIDERS.gemini.send;
  PROVIDERS.gemini.send = async () => ({
    text: JSON.stringify({
      name: "Meal",
      mealType: "lunch",
      calories: 600,
      proteinG: 40,
      carbsG: 50,
      fatG: 20,
      quantityG: 400,
      sodiumMg: 500,
      constituents: [
        {
          name: "Crumb",
          calories: 5,
          protein: 0.1,
          carbs: 1,
          fat: 0,
          serving_size_grams: 2,
        },
      ],
    }),
    toolCalls: [],
  });
  try {
    const result = await analyzeFoodEntry({
      providerId: "gemini",
      config: { apiKey: "test-key" },
      text: "meal",
      prefsOverride: /** @type {any} */ ({ aiFallbackEnabled: false }),
    });
    assert.deepEqual(result.constituents, []);
    assert.equal(result.calories, 600);
    assert.equal(result.sodiumMg, 500);
  } finally {
    PROVIDERS.gemini.send = original;
  }
});
