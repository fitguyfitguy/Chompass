// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { bmr, tdee, dailyCalories, proteinGoal, fatGoalStandard, dailyTargets } from "../formulas.js";

/**
 * Golden vectors ported from
 * android/app/src/test/java/org/codeberg/fitguy/nofud/models/CalculationGoldenScenariosTest.kt
 * Keep in sync per docs/CALCULATION_METHODS.md's change checklist.
 */

test("golden_maleModerateMaintain_mifflinPath", () => {
  const profile = {
    sex: "male", age: 30, heightCm: 180, weightKg: 80,
    bodyFatPercentage: null, activityLevel: "moderate", goal: "maintain",
    weeklyChangeKg: null, ketoMode: false,
  };
  assert.ok(Math.abs(bmr(profile) - 1780.0) < 1e-6);
  assert.ok(Math.abs(tdee(profile) - 2607.7) < 1e-6);
  assert.equal(dailyCalories(profile), 2607);
  assert.equal(proteinGoal(profile), 128);
  assert.equal(fatGoalStandard(profile), 48);
});

test("golden_femaleCut_katchPath", () => {
  const profile = {
    sex: "female", age: 35, heightCm: 165, weightKg: 70,
    bodyFatPercentage: 0.28, activityLevel: "light", goal: "lose",
    weeklyChangeKg: 0.5, ketoMode: false,
  };
  assert.ok(Math.abs(bmr(profile) - 1458.64) < 1e-6);
  assert.equal(dailyCalories(profile) - Math.trunc(tdee(profile)), -550);
  assert.equal(proteinGoal(profile), 98);
});

test("golden_ketoLose_sedentaryCarbsClamped", () => {
  const profile = {
    sex: "male", age: 40, heightCm: 178, weightKg: 95,
    bodyFatPercentage: 0.32, activityLevel: "sedentary", goal: "lose",
    weeklyChangeKg: 0.8, ketoMode: true,
  };
  const targets = dailyTargets(profile);
  assert.equal(targets.carbsG, 20);
  assert.ok(targets.fatG >= 45);
});

test("golden_gainExtraActive_proteinAndCalories", () => {
  const profile = {
    sex: "male", age: 25, heightCm: 185, weightKg: 75,
    bodyFatPercentage: null, activityLevel: "extra_active", goal: "gain",
    weeklyChangeKg: 0.25, ketoMode: false,
  };
  assert.equal(dailyCalories(profile) - Math.trunc(tdee(profile)), 275);
  assert.equal(proteinGoal(profile), 165);
});
