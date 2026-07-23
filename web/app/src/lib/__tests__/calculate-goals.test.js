// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import {
  parseGoalCalculation,
  buildCalculateGoalsPrompt,
  formulaDailyCalories,
  recalculatedFromFormulas,
} from "../ai/calculate-goals.js";

test("parseGoalCalculation_clampsRanges", () => {
  const goals = parseGoalCalculation(
    `{"calories":99999,"protein":-5,"carbs":50,"fat":40,"reason":"test"}`
  );
  assert.equal(goals.calories, 6000);
  assert.equal(goals.protein, 0);
  assert.equal(goals.carbs, 50);
  assert.equal(goals.fat, 40);
  assert.equal(goals.reason, "test");
});

test("parseGoalCalculation_extractsFencedJson", () => {
  const goals = parseGoalCalculation(`Here you go:\n\`\`\`json\n{"calories":2100,"protein":140,"carbs":180,"fat":70,"reason":"ok"}\n\`\`\``);
  assert.equal(goals.calories, 2100);
  assert.equal(goals.protein, 140);
  assert.equal(goals.reason, "ok");
});

test("formulaDailyCalories_ignoresCustomPin", () => {
  const profile = {
    sex: /** @type {const} */ ("male"),
    age: 30,
    heightCm: 180,
    weightKg: 80,
    activityLevel: /** @type {const} */ ("moderate"),
    goal: /** @type {const} */ ("lose"),
    weeklyChangeKg: 0.5,
    ketoMode: false,
    customCalories: 9999,
  };
  assert.notEqual(formulaDailyCalories(profile), 9999);
  assert.equal(recalculatedFromFormulas(profile).customCalories, null);
});

test("buildCalculateGoalsPrompt_includesFormulaAnchor", () => {
  const profile = {
    sex: /** @type {const} */ ("female"),
    age: 28,
    heightCm: 165,
    weightKg: 62,
    activityLevel: /** @type {const} */ ("light"),
    goal: /** @type {const} */ ("maintain"),
    weeklyChangeKg: null,
    ketoMode: false,
    customCalories: null,
  };
  const prompt = buildCalculateGoalsPrompt(profile, null, true, true);
  assert.match(prompt, /APP FORMULA REFERENCE/);
  assert.match(prompt, /Weight goal: maintain/);
  assert.match(prompt, /Diet mode: standard/);
});
