// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import {
  parseGoalCalculation,
  buildCalculateGoalsPrompt,
  formulaDailyCalories,
  recalculatedFromFormulas,
  applyingAiGoals,
  lockConstraintsSection,
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

test("parseGoalCalculation_raisesBelowAbsoluteFloor", () => {
  const goals = parseGoalCalculation(`{"calories":200,"protein":80,"carbs":50,"fat":40}`);
  assert.equal(goals.calories, 1200);
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

test("applyingAiGoals_honorsLockedCaloriesAndWritesMacros", () => {
  const profile = {
    sex: /** @type {const} */ ("male"),
    age: 30,
    heightCm: 180,
    weightKg: 80,
    activityLevel: /** @type {const} */ ("moderate"),
    goal: /** @type {const} */ ("maintain"),
    weeklyChangeKg: null,
    ketoMode: false,
    customCalories: 1900,
    caloriesLocked: true,
    customProtein: 180,
  };
  const next = applyingAiGoals(profile, { calories: 1500, protein: 120, carbs: 100, fat: 40, reason: null });
  assert.equal(next.customCalories, 1900);
  assert.equal(next.customProtein, 120);
});

test("applyingAiGoals_clampsBelowFloor", () => {
  const profile = {
    sex: /** @type {const} */ ("female"),
    age: 60,
    heightCm: 155,
    weightKg: 52,
    activityLevel: /** @type {const} */ ("sedentary"),
    goal: /** @type {const} */ ("lose"),
    weeklyChangeKg: 0.5,
    ketoMode: false,
    customCalories: null,
  };
  const next = applyingAiGoals(profile, { calories: 800, protein: 80, carbs: 80, fat: 40, reason: null });
  assert.ok(next.customCalories >= 1200);
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
  assert.match(prompt, /Never set calories below this user's BMR or 1200/);
  assert.doesNotMatch(prompt, /800-6000/);
});

test("buildCalculateGoalsPrompt_usesLoggedDayAverageForEmpiricalTdee", () => {
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
  const forecast = {
    hasEnoughData: true,
    usesCalendarDayAverage: true,
    avgDailyCalories: 207,
    loggedDayAvgCalories: 2096,
    daysOfFoodData: 9,
    calendarDaysInWindow: 91,
    observedWeeklyChangeKg: -0.4,
    weightEntriesUsed: 5,
    tdee: 2500,
    trendsDisagree: true,
  };
  const prompt = buildCalculateGoalsPrompt(profile, forecast, true, true);
  assert.match(prompt, /avg 2096 kcal\/day across 9 logged days/);
  assert.match(prompt, /do not use that as recorded intake/);
  assert.doesNotMatch(prompt, /Logged intake: avg 207 kcal\/day across/);
});

test("buildCalculateGoalsPrompt_includesLockedCaloriesConstraint", () => {
  const profile = {
    sex: /** @type {const} */ ("male"),
    age: 30,
    heightCm: 180,
    weightKg: 80,
    activityLevel: /** @type {const} */ ("moderate"),
    goal: /** @type {const} */ ("lose"),
    weeklyChangeKg: 0.5,
    ketoMode: false,
    customCalories: 1900,
    caloriesLocked: true,
    lockedMacros: /** @type {const} */ (["protein"]),
    customProtein: 180,
  };
  const prompt = buildCalculateGoalsPrompt(profile, null, true, true);
  assert.match(prompt, /Calories locked at 1900/);
  assert.match(prompt, /Protein locked at 180/);
  assert.equal(lockConstraintsSection({ ...profile, caloriesLocked: false, lockedMacros: [] }), "");
});
