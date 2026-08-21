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

  it("nine consecutive complete days uses logged-day average", () => {
    const foods = Array.from({ length: 9 }, (_, i) => ({
      id: String(i),
      name: "meal",
      mealType: "lunch",
      date: localIso(i + 1),
      time: "12:00",
      calories: 2100,
      proteinG: 100,
      carbsG: 150,
      fatG: 60,
      source: "manual",
    }));
    const f = computeWeightForecast({ weights: [], foods, profile });
    assert.equal(f.usesCalendarDayAverage, false);
    assert.equal(f.daysOfFoodData, 9);
    assert.equal(f.avgDailyCalories, 2100);
    assert.equal(f.loggedDayAvgCalories, 2100);
  });

  it("excludes today from intake average", () => {
    const foods = [
      meal(2000, 1),
      meal(2000, 2),
      meal(500, 0),
    ];
    const f = computeWeightForecast({ weights: [], foods, profile });
    assert.equal(f.daysOfFoodData, 2);
    assert.equal(f.loggedDayAvgCalories, 2000);
  });

  it("skips adaptive when calories are locked", () => {
    const r = suggestAdaptiveCalories({
      profile: { ...profile, customCalories: 1900, caloriesLocked: true },
      weights: [],
      foods: [],
    });
    assert.equal(r.changed, false);
    assert.match(r.message, /locked/i);
  });
});

function localIso(daysAgo) {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function meal(calories, daysAgo) {
  return {
    id: String(daysAgo),
    name: "meal",
    mealType: "lunch",
    date: localIso(daysAgo),
    time: "12:00",
    calories,
    proteinG: 100,
    carbsG: 150,
    fatG: 60,
    source: "manual",
  };
}
