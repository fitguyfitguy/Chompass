// @ts-check
// Demo-only seeding for the marketing hero (web/app/demo.html). Runs against
// the throwaway `chompass-pwa-demo` database (see lib/db.js CHOMPASS_DEMO) —
// never touches the real app's data. Guarded: refuses to clear unless the
// demo flag is actually set.
import {
  seedDiaryEntries,
  seedProfile,
  seedWeightHistory,
  seedFavorites,
} from "../lib/dev-seed.js";
import {
  foodEntries,
  weights,
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
    favorites.clear(),
    profileStore.clear(),
  ]);
  await prefs.save({ onboardingComplete: true });
}

/** Full demo dataset: profile + 14 days diary (today empty) + 2y weights + favorites. */
export async function seedDemo() {
  if (!isDemo())
    throw new Error("seedDemo is demo-only; set window.CHOMPASS_DEMO first");
  await clearDemoStores();
  await seedProfile();
  await seedDiaryEntries(14, { skipToday: true });
  await seedWeightHistory(730);
  await seedFavorites();
}

/** Re-seed the diary between demo loops so every loop starts fresh. */
export async function reseedDiary() {
  if (!isDemo()) return;
  await foodEntries.clear();
  await seedDiaryEntries(14, { skipToday: true });
}
