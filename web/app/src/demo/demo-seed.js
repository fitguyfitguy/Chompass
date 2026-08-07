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

/** Full demo dataset: profile + 90 days diary (light today) + 2y realistic weight/body-fat + favorites. */
export async function seedDemo() {
  if (!isDemo())
    throw new Error("seedDemo is demo-only; set window.CHOMPASS_DEMO first");
  await clearDemoStores();
  await seedProfile();
  // Demo-only goal so the Progress screen shows a real "Goal" badge + line.
  // 64 kg sits just below the ~66 kg the 2y weigh-in series ends at, so the
  // forecast card reads as an on-track safe loss (~2–3 weeks to goal).
  const prof = await profileStore.load();
  if (prof) {
    await profileStore.save({ ...prof, goalWeightKg: 64 });
  }
  await seedDiaryEntries(90, { lightToday: true });
  await seedWeightHistory(730, { dailyDriftKg: 0.12 });
  await seedBodyFatHistory(730, { dailyDriftPct: 0.0002 });
  await seedFavorites();
}

/** Re-seed the diary between demo loops so every loop starts fresh. 90 days
 *  (matching the forecast's 90-day lookback) keeps the weight-forecast card
 *  believable on every loop: with fewer logged days the intake average falls
 *  back to calendar-day averaging and the predicted rate turns absurd. */
export async function reseedDiary() {
  if (!isDemo()) return;
  await foodEntries.clear();
  await seedDiaryEntries(90, { lightToday: true });
}
