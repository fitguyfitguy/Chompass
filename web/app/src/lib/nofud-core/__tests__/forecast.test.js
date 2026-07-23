// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { averageDailyIntake, theilSenSlopePerDay, computeWeightForecast, suggestAdaptiveCalories } from "../forecast.js";

describe("averageDailyIntake (FCAST sparse logging)", () => {
  it("uses logged days when coverage >= 50%", () => {
    const r = averageDailyIntake(10000, 10, 14);
    assert.equal(r.usesCalendarDayAverage, false);
    assert.equal(r.avgDailyCalories, 1000);
  });

  it("uses calendar days when sparse", () => {
    const r = averageDailyIntake(5000, 2, 14);
    assert.equal(r.usesCalendarDayAverage, true);
    assert.equal(r.avgDailyCalories, Math.trunc(5000 / 14));
  });
});

describe("theilSenSlopePerDay", () => {
  it("returns null for single point", () => {
    assert.equal(theilSenSlopePerDay([{ date: "2024-01-01T00:00:00.000Z", weightKg: 80 }]), null);
  });

  it("detects steady loss", () => {
    const slope = theilSenSlopePerDay([
      { date: "2024-01-01T00:00:00.000Z", weightKg: 80 },
      { date: "2024-01-08T00:00:00.000Z", weightKg: 79.3 },
      { date: "2024-01-15T00:00:00.000Z", weightKg: 78.6 },
    ]);
    assert.ok(slope != null && slope < 0);
  });
});

describe("computeWeightForecast / suggestAdaptiveCalories", () => {
  const profile = /** @type {import('../models.js').UserProfile} */ ({
    sex: "male",
    age: 30,
    heightCm: 180,
    weightKg: 80,
    bodyFatPercentage: null,
    activityLevel: "moderate",
    goal: "lose",
    weeklyChangeKg: 0.5,
    ketoMode: false,
    goalWeightKg: 75,
    customCalories: null,
  });

  it("returns a forecast object", () => {
    const foods = Array.from({ length: 10 }, (_, i) => ({
      id: String(i),
      name: "meal",
      mealType: "lunch",
      date: new Date(Date.now() - i * 86400000).toISOString().slice(0, 10),
      time: "12:00",
      calories: 1800,
      proteinG: 100,
      carbsG: 150,
      fatG: 60,
      source: "manual",
    }));
    const weights = [
      { id: "1", date: new Date(Date.now() - 14 * 86400000).toISOString(), weightKg: 81 },
      { id: "2", date: new Date().toISOString(), weightKg: 80 },
    ];
    const f = computeWeightForecast({ weights, foods, profile });
    assert.equal(typeof f.predictedWeeklyChangeKg, "number");
    assert.equal(typeof f.avgDailyCalories, "number");
  });

  it("adaptive needs enough data", () => {
    const r = suggestAdaptiveCalories({ profile, weights: [], foods: [] });
    assert.equal(r.changed, false);
    assert.match(r.message, /needs at least/);
  });
});
