// @ts-check
// Demo-only seeding for the marketing hero (web/app/demo.html). Runs against
// the throwaway `chompass-pwa-demo` database (see lib/db.js CHOMPASS_DEMO) —
// never touches the real app's data. Guarded: refuses to clear unless the
// demo flag is actually set.
import {
  seedDiaryEntries,
  seedProfile,
  seedWeightHistory,
  seedBodyFatHistory,
  seedFavorites,
} from "../lib/dev-seed.js";
import {
  foodEntries,
  weights,
  bodyFat,
  favorites,
  profile as profileStore,
  prefs,
} from "../lib/db.js";

/** @returns {boolean} */
export function isDemo() {
  return (
    typeof window !== "undefined" &&
    Boolean(/** @type {any} */ (window).CHOMPASS_DEMO)
  );
}

async function clearDemoStores() {
  await Promise.all([
    foodEntries.clear(),
    weights.clear(),
    bodyFat.clear(),
    favorites.clear(),
    profileStore.clear(),
  ]);
  await prefs.save({ onboardingComplete: true, theme: "dark" });
}

/** Demo diary scale: the 90-day history sits near this profile's maintenance
 *  intake so the weight-forecast card agrees with the weight series' flat
 *  maintenance tail (see dev-seed seedDiaryEntries). */
const DEMO_CALORIE_SCALE = 1.38;

/** Full demo dataset: profile + 90 days diary (light today) + 2y realistic weight/body-fat + favorites. */
export async function seedDemo() {
  if (!isDemo())
    throw new Error("seedDemo is demo-only; set window.CHOMPASS_DEMO first");
  await clearDemoStores();
  await seedProfile();
  // Demo-only goal so the Progress screen shows a real "Goal" badge + line.
  // 64 kg sits just below the ~66 kg the 2y weigh-in series ends at, so the
  // forecast card reads as an on-track finish: the series is a front-loaded
  // journey (fast early loss, then a long maintenance plateau), and the
  // remaining ~2 kg to goal at ~0.2 kg/wk keeps "days to goal" alive.
  const prof = await profileStore.load();
  if (prof) {
    await profileStore.save({ ...prof, goalWeightKg: 64 });
  }
  await seedDiaryEntries(90, { lightToday: true, calorieScale: DEMO_CALORIE_SCALE });
  await seedWeightHistory(730, { initialDriftKg: 0.129, lossTauDays: 140, skipProbability: 0.2 });
  await seedBodyFatHistory(730, { initialDriftPct: 0.00025, lossTauDays: 140, skipProbability: 0.35 });
  await seedFavorites();
}

/** Re-seed the diary between demo loops so every loop starts fresh. 90 days
 *  (matching the forecast's 90-day lookback) keeps the weight-forecast card
 *  believable on every loop: with fewer logged days the intake average falls
 *  back to calendar-day averaging and the predicted rate turns absurd. */
export async function reseedDiary() {
  if (!isDemo()) return;
  await foodEntries.clear();
  await seedDiaryEntries(90, { lightToday: true, calorieScale: DEMO_CALORIE_SCALE });
}
