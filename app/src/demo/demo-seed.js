// @ts-check
// Demo-only seeding for the marketing hero (web/app/demo.html). Runs against
// the throwaway `chompass-pwa-demo` database (see lib/db.js CHOMPASS_DEMO) —
// never touches the real app's data. Guarded: refuses to clear unless the
// demo flag is actually set.
//
// Performance rules (Phase 2): records are written through Store.putAll (one
// transaction per object store) inside withRevisionHooksSuppressed, so the
// dataset lands in a handful of transactions instead of ~5,100 (one per
// record + a prefs read/write per record for sync revisions). The 2y series
// is thinned (skipProbability 0.5/0.55) — visually identical at every hero
// zoom level, roughly half the records. seedDemo is now a minimal boot seed
// (profile + prefs + favorites); the 90-day diary and 2y series are owned by
// reseedDiary / reseedWeights where the beats actually consume them.
import {
  buildDiaryEntries,
  buildWeightHistory,
  buildBodyFatHistory,
  buildFavorites,
  seedProfile,
} from "../lib/dev-seed.js";
import {
  foodEntries,
  weights,
  bodyFat,
  favorites,
  profile as profileStore,
  prefs,
  rawStore,
  withRevisionHooksSuppressed,
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
 *  maintenance tail (see dev-seed buildDiaryEntries). */
const DEMO_CALORIE_SCALE = 1.38;

/** 2y success-story weight journey: S-curve loss (slow start, faster middle,
 *  gentle landing), ending ~64.5 kg just above the 64 kg goal so the forecast
 *  card reads as an on-track finish (~1 week to goal). Thinned: half the
 *  daily readings are skipped (runs capped at 4, last 3 days always kept),
 *  which the trend lines at every hero zoom level cannot tell apart. */
const WEIGHT_OPTS = { totalLossKg: 19.5, midpointDays: 365, steepnessDays: 75, skipProbability: 0.5 };
const BODY_FAT_OPTS = { totalLossPct: 0.045, midpointDays: 365, steepnessDays: 75, skipProbability: 0.55 };

/** Bulk-put an array into one object store (single transaction). */
async function bulkPut(storeName, values) {
  if (!values.length) return;
  const s = await rawStore(storeName);
  await s.putAll(values);
}

/** Minimal boot seed: profile + prefs + favorites only. The full 90-day diary
 *  and the 2y weight/body-fat series are owned by reseedDiary / reseedWeights
 *  at the loop start / trend beat — seeding them here too used to write
 *  ~1,700 redundant records on every page load. */
export async function seedDemo() {
  if (!isDemo())
    throw new Error("seedDemo is demo-only; set window.CHOMPASS_DEMO first");
  await withRevisionHooksSuppressed(async () => {
    await clearDemoStores();
    await seedProfile();
    // Demo-only goal so the Progress screen shows a real "Goal" badge + line.
    // 64 kg sits just below the ~64.5 kg the 2y S-curve ends at, so the story
    // is a success: fast middle, slow finish, ~1 week of loss left to goal.
    const prof = await profileStore.load();
    if (prof) {
      await profileStore.save({ ...prof, goalWeightKg: 64 });
    }
    await bulkPut("favorites", buildFavorites());
  });
}

/** Re-seed the weight + body-fat stores to the canonical 2y S-curve journey,
 *  ending 3 days ago, so the trend beat can log the missing readings live
 *  (see demo-driver beatTrend) and the scene stays identical on every loop. */
export async function reseedWeights() {
  if (!isDemo()) return;
  await withRevisionHooksSuppressed(async () => {
    await weights.clear();
    await bodyFat.clear();
    // Drop the last 3 days: beatTrend logs them one by one (2 programmatic
    // readings, then the real log-weight dialog).
    const cutoff = new Date(Date.now() - 2 * 864e5).toISOString().slice(0, 10);
    const keep = (/** @type {{ date: string }} */ w) => w.date.slice(0, 10) < cutoff;
    await bulkPut("weights", buildWeightHistory(730, WEIGHT_OPTS).filter(keep));
    await bulkPut("bodyFat", buildBodyFatHistory(730, BODY_FAT_OPTS).filter(keep));
  });
}

/** Re-seed the diary between demo loops so every loop starts fresh. 90 days
 *  (matching the forecast's 90-day lookback) keeps the weight-forecast card
 *  believable on every loop: with fewer logged days the intake average falls
 *  back to calendar-day averaging and the predicted rate turns absurd. */
export async function reseedDiary() {
  if (!isDemo()) return;
  await withRevisionHooksSuppressed(async () => {
    await foodEntries.clear();
    await bulkPut(
      "foodEntries",
      buildDiaryEntries(90, { lightToday: true, calorieScale: DEMO_CALORIE_SCALE }),
    );
  });
}
