// @ts-check
// Local-only test-data seeder, mirroring the Android debug app's
// seed_test_data / seed_body_metrics intent extras (see MainActivity). Never
// linked from the UI — triggered by ?seed=1 on any route while developing
// locally (see app.js's maybeSeedFromUrl call). Writes only to IndexedDB;
// touches no network, no BYOK key storage.
import { foodEntries, favorites, weights, profile as profileStore } from "./db.js";

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

/** @param {number} [days] @param {{skipToday?: boolean}} [opts] */
export async function seedDiaryEntries(days = 14, { skipToday = false } = {}) {
  for (let i = skipToday ? 1 : 0; i < days; i++) {
    const date = isoDaysAgo(i);
    const jitter = () => 0.85 + Math.random() * 0.3;
    for (const meal of MEALS) {
      if (meal.mealType === "snack" && Math.random() < 0.4) continue; // some days skip the snack, more realistic
      await foodEntries.put({
        id: crypto.randomUUID(),
        name: meal.name,
        mealType: meal.mealType,
        date,
        time: meal.time,
        quantityG: null,
        calories: Math.round(meal.calories * jitter()),
        proteinG: Math.round(meal.proteinG * jitter()),
        carbsG: Math.round(meal.carbsG * jitter()),
        fatG: Math.round(meal.fatG * jitter()),
        source: "manual",
        note: null,
        grounding: null,
      });
    }
  }
}

/** @param {number} days */
export async function seedWeightHistory(days = 42) {
  let weightKg = 84;
  for (let i = days; i >= 0; i--) {
    weightKg -= 0.03 + Math.random() * 0.02; // gentle downward trend, matches goal:"lose" above
    const date = new Date();
    date.setDate(date.getDate() - i);
    await weights.put({ id: crypto.randomUUID(), date: date.toISOString(), weightKg: Math.round(weightKg * 10) / 10 });
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
  console.info("[chompass-dev-seed] seeded profile, 14 days of diary entries, 42 days of weight history");
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
