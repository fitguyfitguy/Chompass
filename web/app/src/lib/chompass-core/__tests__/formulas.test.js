// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { bmr, tdee, dailyCalories, calorieAdjustment, proteinGoal, fatGoalStandard, dailyTargets } from "../formulas.js";
import { averageDailyIntake } from "../forecast.js";
import { loadParityFixture } from "../../parity-fixtures.js";

/**
 * Golden vectors from testdata/parity/formulas-expected.json — keep in sync with
 * Android CalculationGoldenScenariosTest / docs/CALCULATION_METHODS.md.
 */

const { scenarios } = loadParityFixture("formulas-expected.json");

for (const scenario of scenarios) {
  test(scenario.id, () => {
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
