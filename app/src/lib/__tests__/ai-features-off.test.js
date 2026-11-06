// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { analyzeFoodEntry } from "../ai/food-analyze.js";
import { calculateGoalsWithAi } from "../ai/calculate-goals.js";
import { dailyTargets } from "../chompass-core/formulas.js";
import { runCoachTurn } from "../ai/coach.js";
import { PROVIDERS } from "../ai/providers.js";

/**
 * Codeberg #20 phase 2 (PWA mirror): with `aiFeaturesEnabled: false`, no data
 * may reach an LLM provider — food analysis and the coach throw before any
 * request, and goal calculation returns the deterministic formula values.
 */

test("analyzeFoodEntry_throwsWhenAiFeaturesOff", async () => {
  const original = PROVIDERS.anthropic.send;
  PROVIDERS.anthropic.send = async () => {
    throw new Error("provider must not be called with AI features off");
  };
  try {
    await assert.rejects(
      analyzeFoodEntry({
        providerId: "anthropic",
        config: { apiKey: "test-key" },
        text: "oatmeal",
        prefsOverride: /** @type {any} */ ({ aiFeaturesEnabled: false }),
      }),
      /AI features are turned off/
    );
  } finally {
    PROVIDERS.anthropic.send = original;
  }
});

test("analyzeFoodEntry_runsNormallyWhenAiFeaturesOn", async () => {
  const original = PROVIDERS.anthropic.send;
  PROVIDERS.anthropic.send = async () => ({
    text: JSON.stringify({ name: "Oatmeal", calories: 300, proteinG: 10, carbsG: 50, fatG: 8 }),
    toolCalls: [],
  });
  try {
    const result = await analyzeFoodEntry({
      providerId: "anthropic",
      config: { apiKey: "test-key" },
      text: "oatmeal",
      prefsOverride: /** @type {any} */ ({ aiFeaturesEnabled: true, aiFallbackEnabled: false }),
    });
    assert.equal(result.name, "Oatmeal");
  } finally {
    PROVIDERS.anthropic.send = original;
  }
});

test("runCoachTurn_throwsWhenAiFeaturesOff", async () => {
  const original = PROVIDERS.anthropic.send;
  PROVIDERS.anthropic.send = async () => {
    throw new Error("provider must not be called with AI features off");
  };
  try {
    await assert.rejects(
      runCoachTurn({
        providerId: "anthropic",
        config: { apiKey: "test-key" },
        history: [],
        userText: "hello",
        prefsOverride: /** @type {any} */ ({ aiFeaturesEnabled: false }),
      }),
      /AI features are turned off/
    );
  } finally {
    PROVIDERS.anthropic.send = original;
  }
});

test("calculateGoalsWithAi_returnsFormulaValuesWhenAiFeaturesOff", async () => {
  const original = PROVIDERS.gemini.send;
  PROVIDERS.gemini.send = async () => {
    throw new Error("provider must not be called with AI features off");
  };
  const profile = {
    sex: /** @type {const} */ ("male"),
    age: 30,
    heightCm: 180,
    weightKg: 80,
    activityLevel: /** @type {const} */ ("moderate"),
    goal: /** @type {const} */ ("lose"),
    weeklyChangeKg: 0.5,
    ketoMode: false,
    customCalories: null,
  };
  try {
    const goals = await calculateGoalsWithAi({
      providerId: "gemini",
      config: { apiKey: "test-key" },
      profile: /** @type {any} */ (profile),
      prefsOverride: /** @type {any} */ ({ aiFeaturesEnabled: false }),
    });
    // Formula-derived, no provider call.
    const expected = dailyTargets(/** @type {any} */ (profile));
    assert.equal(goals.calories, expected.calories);
    assert.equal(goals.protein, expected.proteinG);
    assert.equal(goals.carbs, expected.carbsG);
    assert.equal(goals.fat, expected.fatG);
    assert.ok(goals.reason);
  } finally {
    PROVIDERS.gemini.send = original;
  }
});
