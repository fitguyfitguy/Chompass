// @ts-check
// Local-only test-data seeder, mirroring the Android debug app's
// seed_test_data / seed_body_metrics intent extras (see MainActivity). Never
// linked from the UI — triggered by ?seed=1 on any route while developing
// locally (see app.js's maybeSeedFromUrl call). Writes only to IndexedDB;
// touches no network, no BYOK key storage.
import { foodEntries, favorites, weights, bodyFat, profile as profileStore } from "./db.js";

const MEALS = [
  { mealType: "breakfast", time: "07:30", name: "Oatmeal with banana", calories: 380, proteinG: 12, carbsG: 65, fatG: 8 },
  { mealType: "lunch", time: "12:30", name: "Chicken burrito bowl", calories: 640, proteinG: 42, carbsG: 70, fatG: 18 },
  { mealType: "dinner", time: "19:00", name: "Salmon, rice, broccoli", calories: 590, proteinG: 38, carbsG: 55, fatG: 22 },
  { mealType: "snack", time: "16:00", name: "Greek yogurt", calories: 150, proteinG: 15, carbsG: 12, fatG: 4 },
];

function isoDaysAgo(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

export async function seedProfile() {
  await profileStore.save({
    sex: "male",
    age: 32,
    heightCm: 178,
    weightKg: 82,
    bodyFatPercentage: 0.22,
    activityLevel: "moderate",
    goal: "lose",
    weeklyChangeKg: 0.5,
    ketoMode: false,
  });
}

/**
 * @param {number} [days]
 * @param {{lightToday?: boolean, calorieScale?: number}} [opts] calorieScale
 *   scales every logged day's calories + macros (default 1). The demo hero
 *   passes ~1.38 so the 90-day history sits near this profile's maintenance
 *   intake — the forecast card then agrees with the weight series' flat tail
 *   instead of flagging "trends disagree".
 */
export async function seedDiaryEntries(days = 14, { lightToday = false, calorieScale = 1 } = {}) {
  for (let i = 0; i < days; i++) {
    const date = isoDaysAgo(i);
    const jitter = () => 0.85 + Math.random() * 0.3;
    // Demo hero: today starts with breakfast + dinner so the calorie ring
    // visibly rises as the demo logs more meals during the loop (and the home
    // reads as a real, mostly-full day at full zoom-out).
    const meals = i === 0 && lightToday ? [MEALS[0], MEALS[2]] : MEALS;
    for (const meal of meals) {
      if (meal.mealType === "snack" && Math.random() < 0.4) continue; // some days skip the snack, more realistic
      await foodEntries.put({
        id: crypto.randomUUID(),
        name: meal.name,
        mealType: meal.mealType,
        date,
        time: meal.time,
        quantityG: null,
        calories: Math.round(meal.calories * jitter() * calorieScale),
        proteinG: Math.round(meal.proteinG * jitter() * calorieScale),
        carbsG: Math.round(meal.carbsG * jitter() * calorieScale),
        fatG: Math.round(meal.fatG * jitter() * calorieScale),
        source: "manual",
        note: null,
        grounding: null,
      });
    }
  }
}

/**
 * @param {number} [days]
 * @param {{totalLossKg?: number, midpointDays?: number, steepnessDays?: number, skipProbability?: number}} [opts]
 *   Success-story shape: cumulative loss follows a logistic S-curve, so the
 *   journey reads slow start → accelerating middle → gentle landing at the
 *   goal weight. [totalLossKg] is the total lost over the span (start 84 kg),
 *   [midpointDays] the day of fastest loss, [steepnessDays] how drawn-out
 *   the transition is. Defaults center the S on the seeded span (mild visible
 *   loss for short dev horizons); the 2y demo hero passes ~19.5 kg with the
 *   midpoint a year back so the recent stretch is a near-goal plateau.
 *   [skipProbability] skips some weigh-in days the way real logs do (the 3
 *   most recent readings are always kept, and runs cap at 4 so the trend
 *   line never breaks).
 */
export async function seedWeightHistory(days = 42, { totalLossKg = 6, midpointDays = Math.max(1, Math.round(days / 2)), steepnessDays = 75, skipProbability = 0 } = {}) {
  // Realistic daily weigh-ins: S-curve trend (slow start, faster middle,
  // maintenance finish) + autocorrelated day-to-day water-retention noise
  // (AR(1), φ≈0.6 → σ≈0.5 kg), rounded to the 0.1 kg a real scale reports.
  // Noise must be added BEFORE rounding — the old seeder's drift (~0.005
  // kg/day) sat ~20× below the 0.1 kg resolution, so a month of readings
  // collapsed into a straight line.
  const noisePhi = 0.6;
  const noiseAmpKg = 0.7; // uniform ε ∈ ±0.7 kg → σ_total ≈ 0.5 kg
  let prevNoise = 0;
  let skipped = 0;
  for (let i = days; i >= 0; i--) {
    const t = days - i; // days elapsed since the oldest reading
    if (i > 2 && skipProbability > 0 && skipped < 4 && Math.random() < skipProbability) {
      skipped++;
      continue;
    }
    skipped = 0;
    const progress = 1 / (1 + Math.exp(-(t - midpointDays) / steepnessDays));
    const baseline = 84 - totalLossKg * progress;
    const noise = noisePhi * prevNoise + (Math.random() * 2 - 1) * noiseAmpKg;
    prevNoise = noise;
    const date = new Date();
    date.setDate(date.getDate() - i);
    await weights.put({ id: crypto.randomUUID(), date: date.toISOString(), weightKg: Math.round((baseline + noise) * 10) / 10 });
  }
}

/**
 * @param {number} [days]
 * @param {{totalLossPct?: number, midpointDays?: number, steepnessDays?: number, skipProbability?: number}} [opts]
 *   Same S-curve shape as seedWeightHistory: [totalLossPct] is the body-fat
 *   fraction dropped over the span (start 24%).
 */
export async function seedBodyFatHistory(days = 42, { totalLossPct = 0.0045, midpointDays = Math.max(1, Math.round(days / 2)), steepnessDays = 75, skipProbability = 0 } = {}) {
  // Same S-curve + AR(1) treatment as weight: slow start, faster middle,
  // plateau finish (σ≈0.33%), rounded to the 0.1% a real scale reports.
  const noisePhi = 0.6;
  const noiseAmpPct = 0.0045; // uniform ε ∈ ±0.45% → σ_total ≈ 0.33%
  let prevNoise = 0;
  let skipped = 0;
  for (let i = days; i >= 0; i--) {
    const t = days - i;
    if (i > 2 && skipProbability > 0 && skipped < 4 && Math.random() < skipProbability) {
      skipped++;
      continue;
    }
    skipped = 0;
    const progress = 1 / (1 + Math.exp(-(t - midpointDays) / steepnessDays));
    const baseline = 0.24 - totalLossPct * progress;
    const noise = noisePhi * prevNoise + (Math.random() * 2 - 1) * noiseAmpPct;
    prevNoise = noise;
    const date = new Date();
    date.setDate(date.getDate() - i);
    await bodyFat.put({ id: crypto.randomUUID(), date: date.toISOString(), bodyFatPercent: Math.round((baseline + noise) * 1000) / 1000 });
  }
}

/** Favorite templates so the home Add-food sheet shows one-tap relog chips. */
export async function seedFavorites() {
  const now = new Date();
  const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
  for (const [name, calories, proteinG, carbsG, fatG] of [
    ["Oatmeal with banana", 380, 12, 65, 8],
    ["Chicken burrito bowl", 640, 42, 70, 18],
    ["Greek yogurt", 150, 15, 12, 4],
  ]) {
    await favorites.put({
      id: crypto.randomUUID(),
      name,
      mealType: "breakfast",
      date: new Date().toISOString().slice(0, 10),
      time,
      quantityG: null,
      calories,
      proteinG,
      carbsG,
      fatG,
      source: "manual",
      note: null,
      grounding: null,
    });
  }
}

export async function seedAll() {
  await seedProfile();
  await seedDiaryEntries(14);
  await seedWeightHistory(42);
  await seedBodyFatHistory(42);
  console.info("[chompass-dev-seed] seeded profile, 14 days of diary entries, 42 days of weight + body-fat history");
}

/**
 * Reads `seed=1` off the current hash's query string, seeds once, then
 * strips the param so a later reload/hashchange doesn't reseed on top.
 */
export async function maybeSeedFromUrl() {
  const [path, query] = location.hash.replace(/^#/, "").split("?");
  const params = new URLSearchParams(query ?? "");
  if (params.get("seed") !== "1") return;
  await seedAll();
  params.delete("seed");
  const rest = params.toString();
  history.replaceState(null, "", `${location.pathname}${location.search}#${path}${rest ? `?${rest}` : ""}`);
}
