// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { bmr, tdee, dailyCalories, calorieAdjustment, proteinGoal, fatGoalStandard, dailyTargets, relativeFatMassPercent, safetyFloorKcal, safetyCeilingKcal, clampAutoCalories } from "../formulas.js";
import { averageDailyIntake } from "../forecast.js";
import { loadParityFixture } from "../../parity-fixtures.js";

/**
 * Golden vectors from testdata/parity/formulas-expected.json — keep in sync with
 * Android CalculationGoldenScenariosTest / docs/CALCULATION_METHODS.md.
 */

const { scenarios } = loadParityFixture("formulas-expected.json");

for (const scenario of scenarios) {
  test(scenario.id, () => {
    if (scenario.kind === "rfm") {
      const actual = relativeFatMassPercent(scenario.input);
      if (scenario.expect.percent == null) {
        assert.equal(actual, null);
      } else {
        assert.ok(actual != null);
        assert.ok(Math.abs(actual - scenario.expect.percent) <= (scenario.expect.tol ?? 1e-9));
      }
      return;
    }
    if (scenario.kind === "averageDailyIntake") {
      const r = averageDailyIntake(
        scenario.input.totalCalories,
        scenario.input.loggedDays,
        scenario.input.calendarDaysInWindow,
      );
      assert.equal(r.avgDailyCalories, scenario.expect.avgDailyCalories);
      assert.equal(r.usesCalendarDayAverage, scenario.expect.usesCalendarDayAverage);
      return;
    }

    const profile = scenario.profile;
    const expect = scenario.expect;

    if (expect.bmr != null) {
      assert.ok(Math.abs(bmr(profile) - expect.bmr) <= (expect.bmrTol ?? 1e-6));
    }
    if (expect.tdee != null) {
      assert.ok(Math.abs(tdee(profile) - expect.tdee) <= (expect.tdeeTol ?? 1e-6));
    }
    if (expect.dailyCalories != null) {
      assert.equal(dailyCalories(profile), expect.dailyCalories);
    }
    if (expect.calorieAdjustment != null) {
      assert.equal(calorieAdjustment(profile), expect.calorieAdjustment);
    }
    if (expect.proteinGoal != null) {
      assert.equal(proteinGoal(profile), expect.proteinGoal);
    }
    if (expect.fatGoal != null) {
      assert.equal(fatGoalStandard(profile), expect.fatGoal);
    }
    if (expect.carbsGoal != null || expect.fatGoalMin != null) {
      const targets = dailyTargets(profile);
      if (expect.carbsGoal != null) assert.equal(targets.carbsG, expect.carbsGoal);
      if (expect.fatGoalMin != null) assert.ok(targets.fatG >= expect.fatGoalMin);
    }
    if (expect.estimatedDailyActiveCalories != null) {
      const active = Math.round(tdee(profile) - bmr(profile));
      assert.equal(active, expect.estimatedDailyActiveCalories);
    }
    if (expect.sedentaryCalorieBudget != null) {
      const active = Math.round(tdee(profile) - bmr(profile));
      assert.equal(dailyCalories(profile) - active, expect.sedentaryCalorieBudget);
    }
  });
}

// BF-recency gate: Katch-McArdle only when the reading is undated (legacy) or
// logged within BODY_FAT_RECENCY_DAYS; dated-but-stale falls back to Mifflin-St
// Jeor. Mirrors Android UserProfileCalculationTest staleness cases.
test("katchStale_fallsBackToMifflin", () => {
  const at = new Date("2026-06-01T00:00:00Z");
  const dated = {
    sex: "male", age: 30, heightCm: 180, weightKg: 80,
    bodyFatPercentage: 0.20, bodyFatUpdatedAt: "2026-02-01T00:00:00Z",
    activityLevel: "moderate", goal: "maintain",
  };
  assert.equal(bmr(dated, at), 10 * 80 + 6.25 * 180 - 5 * 30 + 5);
});

test("katchFreshDated_usesKatch", () => {
  const at = new Date("2026-06-01T00:00:00Z");
  const dated = {
    sex: "male", age: 30, heightCm: 180, weightKg: 80,
    bodyFatPercentage: 0.20, bodyFatUpdatedAt: "2026-05-02T00:00:00Z",
    activityLevel: "moderate", goal: "maintain",
  };
  assert.ok(Math.abs(bmr(dated, at) - (370 + 21.6 * 80 * 0.8)) <= 1e-9);
});

test("katchLegacyNullDate_usesKatch", () => {
  const legacy = {
    sex: "male", age: 30, heightCm: 180, weightKg: 80,
    bodyFatPercentage: 0.20, activityLevel: "moderate", goal: "maintain",
  };
  assert.ok(Math.abs(bmr(legacy) - (370 + 21.6 * 80 * 0.8)) <= 1e-9);
});

// CAL-SAFE ceiling is the 1.5×TDEE proportional cap bounded by 6000 (mirrors
// Android CalorieSafety.ceilingKcal): a normal profile caps at 1.5×TDEE; only
// a TDEE above 4000 still reaches the flat parser ceiling.
test("calorieCeiling_proportionalCapBoundedBy6000", () => {
  const base = { sex: "male", age: 30, heightCm: 180, weightKg: 80, activityLevel: "moderate", goal: "maintain" };
  // 1.5 × 2607.7 TDEE = 3912.
  assert.equal(safetyCeilingKcal(base, safetyFloorKcal(base)), 3912);
  assert.equal(clampAutoCalories(7000, base), 3912);
  const heavy = { ...base, weightKg: 150, activityLevel: "very_active" };
  assert.equal(safetyCeilingKcal(heavy, safetyFloorKcal(heavy)), 6000);
});
