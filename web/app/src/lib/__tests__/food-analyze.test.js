// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  ANALYSIS_PHASE,
  ANALYSIS_PHASE_LABEL,
  ANALYSIS_PHASE_STEPS,
  analyzeFoodEntry,
  isAbortError,
} from "../ai/food-analyze.js";
import { PROVIDERS } from "../ai/providers.js";

test("analysisPhases_matchAndroidCloudEntry", () => {
  assert.deepEqual([...ANALYSIS_PHASE_STEPS], ["preparing", "calling_ai", "parsing"]);
  assert.equal(ANALYSIS_PHASE_LABEL[ANALYSIS_PHASE.PREPARING], "Preparing request…");
  assert.equal(ANALYSIS_PHASE_LABEL[ANALYSIS_PHASE.CALLING_AI], "Calling AI…");
  assert.equal(ANALYSIS_PHASE_LABEL[ANALYSIS_PHASE.PARSING], "Reading result…");
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
