// @ts-check
/**
 * FCAST + ADAPT — port of WeightForecastMath / WeightAnalysisService /
 * AdaptiveGoalService. See docs/CALCULATION_METHODS.md.
 */
import { KCAL_PER_KG_BODY_MASS, bmr, dailyCalories, tdee } from "./formulas.js";

const MAX_LOOKBACK_DAYS = 90;
const SPARSE_LOGGING_FRACTION_THRESHOLD = 0.5;
const TREND_DISAGREEMENT_KG_PER_WEEK = 0.3;
const MINIMUM_FOOD_DAYS = 4;
const MINIMUM_WEIGHT_ENTRIES = 3;
const MINIMUM_DAILY_ADJUSTMENT = 25;
const MAXIMUM_DAILY_ADJUSTMENT = 150;

/**
 * @param {number} totalCalories
 * @param {number} loggedDays
 * @param {number} calendarDaysInWindow
 */
export function averageDailyIntake(totalCalories, loggedDays, calendarDaysInWindow) {
  if (loggedDays <= 0) {
    return { avgDailyCalories: 0, loggedDays: 0, calendarDaysInWindow: Math.max(1, calendarDaysInWindow), usesCalendarDayAverage: false };
  }
  const calendarDays = Math.max(1, calendarDaysInWindow);
  const sparse = loggedDays / calendarDays < SPARSE_LOGGING_FRACTION_THRESHOLD;
  const denominator = sparse ? calendarDays : loggedDays;
  return {
    avgDailyCalories: Math.trunc(totalCalories / denominator),
    loggedDays,
    calendarDaysInWindow: calendarDays,
    usesCalendarDayAverage: sparse,
  };
}

/**
 * Theil–Sen slope (median of pairwise slopes) in kg/day.
 * @param {{date: string, weightKg: number}[]} entries
 */
export function theilSenSlopePerDay(entries) {
  if (entries.length < 2) return null;
  const sorted = entries.slice().sort((a, b) => a.date.localeCompare(b.date));
  const origin = dayIndex(sorted[0].date);
  const points = sorted.map((e) => [dayIndex(e.date) - origin, e.weightKg]);
  /** @type {number[]} */
  const slopes = [];
  for (let i = 0; i < points.length; i++) {
    for (let j = i + 1; j < points.length; j++) {
      const dx = points[j][0] - points[i][0];
      if (dx !== 0) slopes.push((points[j][1] - points[i][1]) / dx);
    }
  }
  if (!slopes.length) return null;
  slopes.sort((a, b) => a - b);
  return slopes[Math.floor(slopes.length / 2)];
}

/** @param {string} isoDate */
function dayIndex(isoDate) {
  return Math.floor(new Date(isoDate.includes("T") ? isoDate : `${isoDate}T00:00:00`).getTime() / 86400000);
}

function localIsoDate(d = new Date()) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function addDaysIso(iso, delta) {
  const [y, m, d] = iso.split("-").map(Number);
  const dt = new Date(y, m - 1, d + delta);
  return localIsoDate(dt);
}

function daysBetweenIso(a, b) {
  const [ay, am, ad] = a.split("-").map(Number);
  const [by, bm, bd] = b.split("-").map(Number);
  return Math.round((Date.UTC(by, bm - 1, bd) - Date.UTC(ay, am - 1, ad)) / 86400000);
}

/**
 * @param {Object} args
 * @param {import('./models.js').WeightEntry[]} args.weights
 * @param {import('./models.js').FoodEntry[]} args.foods
 * @param {import('./models.js').UserProfile} args.profile
 */
export function computeWeightForecast({ weights, foods, profile }) {
  const today = localIsoDate();
  const yesterday = addDaysIso(today, -1);
  const lookbackStart = addDaysIso(today, -MAX_LOOKBACK_DAYS);
  const cutoffIso = lookbackStart;

  const foodDay = (f) => String(f.date).slice(0, 10);
  const completeFoods = foods.filter((f) => {
    const d = foodDay(f);
    return d >= lookbackStart && d <= yesterday;
  });
  const loggedDates = [...new Set(completeFoods.map(foodDay))].sort();
  const daysLogged = loggedDates.length;
  const totalRecentCal = completeFoods.reduce((s, f) => s + f.calories, 0);
  const first = loggedDates[0];
  const calendarDays = first ? daysBetweenIso(first, yesterday) + 1 : 1;
  const intake = averageDailyIntake(totalRecentCal, daysLogged, calendarDays);
  const avgDailyCal = intake.avgDailyCalories;
  const loggedDayAvgCalories = daysLogged > 0 ? Math.trunc(totalRecentCal / daysLogged) : 0;
  const maintenance = Math.trunc(tdee(profile));
  const balance = avgDailyCal - maintenance;
  const predictedWeeklyKg = (balance * 7) / KCAL_PER_KG_BODY_MASS;

  const sortedWeights = weights.slice().sort((a, b) => b.date.localeCompare(a.date));
  const currentWeight = sortedWeights[0]?.weightKg ?? profile.weightKg;
  const regressionWindow = sortedWeights.filter((w) => w.date.slice(0, 10) >= cutoffIso);
  const slope = theilSenSlopePerDay(regressionWindow);
  const observedWeeklyKg = slope != null ? slope * 7 : null;

  const hasEnoughData = daysLogged >= 2 && weights.length >= 2;
  const trendsDisagree =
    observedWeeklyKg != null && hasEnoughData && Math.abs(predictedWeeklyKg - observedWeeklyKg) > TREND_DISAGREEMENT_KG_PER_WEEK;

  let daysToGoal = null;
  const goalKg = profile.goalWeightKg ?? null;
  if (goalKg != null && predictedWeeklyKg !== 0 && profile.goal !== "maintain") {
    const kgRemaining = goalKg - currentWeight;
    const movingCorrectWay =
      (profile.goal === "lose" && predictedWeeklyKg < 0 && kgRemaining < 0) ||
      (profile.goal === "gain" && predictedWeeklyKg > 0 && kgRemaining > 0);
    if (movingCorrectWay) {
      const daysPerKg = 7 / Math.abs(predictedWeeklyKg);
      daysToGoal = Math.round(Math.abs(kgRemaining) * daysPerKg);
    }
  }

  return {
    avgDailyCalories: avgDailyCal,
    tdee: maintenance,
    dailyEnergyBalance: balance,
    predictedWeeklyChangeKg: predictedWeeklyKg,
    observedWeeklyChangeKg: observedWeeklyKg,
    currentWeightKg: currentWeight,
    predictedWeight30dKg: currentWeight + (predictedWeeklyKg * 30) / 7,
    predictedWeight60dKg: currentWeight + (predictedWeeklyKg * 60) / 7,
    predictedWeight90dKg: currentWeight + (predictedWeeklyKg * 90) / 7,
    daysToGoal,
    hasEnoughData,
    trendsDisagree,
    daysOfFoodData: daysLogged,
    weightEntriesUsed: regressionWindow.length,
    calendarDaysInWindow: intake.calendarDaysInWindow,
    usesCalendarDayAverage: intake.usesCalendarDayAverage,
    loggedDayAvgCalories,
    firstLoggedDate: first ?? null,
    lastLoggedDate: loggedDates.length ? loggedDates[loggedDates.length - 1] : null,
  };
}

/**
 * ADAPT — suggest a calorie adjustment (does not write). No Health Connect path on web.
 * @param {Object} args
 * @param {import('./models.js').UserProfile} args.profile
 * @param {import('./models.js').WeightEntry[]} args.weights
 * @param {import('./models.js').FoodEntry[]} args.foods
 */
export function suggestAdaptiveCalories({ profile, weights, foods }) {
  if (profile.caloriesLocked) {
    return {
      changed: false,
      updatedCalories: null,
      message: "Calories are locked, so Adaptive Goals did not change them.",
      forecast: computeWeightForecast({ weights, foods, profile }),
    };
  }
  const forecast = computeWeightForecast({ weights, foods, profile });
  const observed = forecast.observedWeeklyChangeKg;
  const magnitude = profile.weeklyChangeKg ?? 0.5;
  const targetWeekly =
    profile.goal === "lose" ? -magnitude : profile.goal === "gain" ? magnitude : 0;
  const currentCalories = profile.customCalories ?? dailyCalories(profile);
  const safetyFloor = Math.max(Math.round(bmr(profile)), 1200);
  const maintenanceTdee = Math.round(tdee(profile));
  const safetyCeiling = Math.max(safetyFloor, Math.round(maintenanceTdee * 1.25));

  const hasWeightTrend =
    forecast.daysOfFoodData >= MINIMUM_FOOD_DAYS &&
    forecast.weightEntriesUsed >= MINIMUM_WEIGHT_ENTRIES &&
    observed != null;

  if (!hasWeightTrend || observed == null) {
    return {
      changed: false,
      updatedCalories: null,
      message: `Adaptive Goals needs at least ${MINIMUM_FOOD_DAYS} logged food days and ${MINIMUM_WEIGHT_ENTRIES} recent weight entries before making a correction.`,
      forecast,
    };
  }

  const raw = ((targetWeekly - observed) * KCAL_PER_KG_BODY_MASS) / 7;
  let limited = Math.round(raw);
  limited = Math.max(-MAXIMUM_DAILY_ADJUSTMENT, Math.min(MAXIMUM_DAILY_ADJUSTMENT, limited));

  if (Math.abs(limited) < MINIMUM_DAILY_ADJUSTMENT) {
    return {
      changed: false,
      updatedCalories: null,
      message: "Your recent trend is close to your goal pace, so Adaptive Goals did not change calories.",
      forecast,
    };
  }

  if (limited < 0 && currentCalories <= safetyFloor) {
    return { changed: false, updatedCalories: null, message: "Already at the safety floor.", forecast };
  }
  if (limited > 0 && currentCalories >= safetyCeiling) {
    return { changed: false, updatedCalories: null, message: "Already at the safety ceiling.", forecast };
  }

  const proposed = currentCalories + limited;
  const adjusted = limited < 0 ? Math.max(proposed, safetyFloor) : Math.min(proposed, safetyCeiling);
  if (adjusted === currentCalories) {
    return { changed: false, updatedCalories: null, message: "Guardrails kept calories unchanged.", forecast };
  }

  const signed = adjusted - currentCalories;
  const sign = signed > 0 ? "+" : "";
  return {
    changed: true,
    updatedCalories: adjusted,
    message: `Adaptive Goals suggests ${sign}${signed} kcal → ${adjusted} kcal based on your recent weight trend.`,
    forecast,
  };
}
