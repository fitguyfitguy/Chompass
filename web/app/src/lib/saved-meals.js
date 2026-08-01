// @ts-check
/**
 * Saved Meals helpers — Recents / Frequent / Favorites, mirroring Android
 * FoodRepository recentFoodTemplates / frequentFoodGroups / toggleFavorite.
 */
import { foodEntries, favorites as favoritesStore, prefs } from "./db.js";
import { guessMealTypeFromPrefs } from "./meal-schedule.js";

/** @param {import('./chompass-core/models.js').FoodEntry | {name?: string}} entry */
export function favoriteKey(entry) {
  return String(entry.name ?? "")
    .trim()
    .toLowerCase();
}

/**
 * @param {import('./chompass-core/models.js').FoodEntry} entry
 * @param {string} [date]
 * @param {string} [time]
 * @param {"breakfast"|"lunch"|"dinner"|"snack"} [mealType]
 */
export function duplicatedForLogging(entry, date, time, mealType) {
  const now = new Date();
  const hm = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
  return {
    ...entry,
    id: crypto.randomUUID(),
    date: date ?? entry.date,
    time: time ?? hm,
    mealType: mealType ?? entry.mealType,
    recipeLogId: null,
  };
}

/**
 * Newest-first templates, one per favoriteKey, within `days`.
 * @param {number} [days]
 * @param {number} [limit]
 */
export async function recentFoodTemplates(days = 30, limit = 50) {
  const all = await foodEntries.all();
  const cutoff = Date.now() - days * 86400000;
  const filtered = all.filter((e) => {
    const ts = Date.parse(`${e.date}T${e.time || "12:00"}`);
    return !Number.isNaN(ts) && ts >= cutoff;
  });
  const seen = new Set();
  /** @type {import('./chompass-core/models.js').FoodEntry[]} */
  const out = [];
  filtered
    .slice()
    .sort((a, b) => `${b.date}T${b.time}`.localeCompare(`${a.date}T${a.time}`))
    .forEach((e) => {
      const key = favoriteKey(e);
      if (!key || seen.has(key)) return;
      seen.add(key);
      out.push(e);
    });
  return out.slice(0, limit);
}

/**
 * @param {number} [days]
 * @returns {Promise<{template: import('./chompass-core/models.js').FoodEntry, count: number}[]>}
 */
export async function frequentFoodGroups(days = 90) {
  const all = await foodEntries.all();
  const cutoff = Date.now() - days * 86400000;
  /** @type {Map<string, {count: number, template: import('./chompass-core/models.js').FoodEntry}>} */
  const aggregates = new Map();
  for (const e of all) {
    const ts = Date.parse(`${e.date}T${e.time || "12:00"}`);
    if (Number.isNaN(ts) || ts < cutoff) continue;
    const key = favoriteKey(e);
    if (!key) continue;
    const existing = aggregates.get(key);
    if (!existing) {
      aggregates.set(key, { count: 1, template: e });
    } else {
      const newer = `${e.date}T${e.time}`.localeCompare(`${existing.template.date}T${existing.template.time}`) > 0;
      aggregates.set(key, {
        count: existing.count + 1,
        template: newer ? e : existing.template,
      });
    }
  }
  return [...aggregates.values()].sort(
    (a, b) => b.count - a.count || a.template.name.localeCompare(b.template.name, undefined, { sensitivity: "base" })
  );
}

/**
 * @param {import('./chompass-core/models.js').FoodEntry} entry
 */
function entryStamp(entry) {
  return Date.parse(`${entry.date}T${entry.time || "12:00"}`) || 0;
}

/**
 * Mealtime-aware hub chips — mirrors Android quickRelogFoodTemplates.
 * @param {import('./chompass-core/models.js').FoodEntry[]} favorites
 * @param {import('./chompass-core/models.js').FoodEntry[]} recents
 * @param {import('./chompass-core/models.js').FoodEntry[]} frequents
 * @param {"breakfast"|"lunch"|"dinner"|"snack"|"other"} currentMeal
 * @param {Set<string>} [favoriteKeys]
 * @param {number} [limit]
 * @returns {import('./chompass-core/models.js').FoodEntry[]}
 */
export function quickRelogFoodTemplates(
  favorites,
  recents,
  frequents,
  currentMeal,
  favoriteKeys = new Set(favorites.map(favoriteKey).filter(Boolean)),
  limit = 6,
) {
  const seen = new Set();
  /** @type {import('./chompass-core/models.js').FoodEntry[]} */
  const pool = [];
  for (const source of [favorites, recents, frequents]) {
    for (const entry of source) {
      const key = favoriteKey(entry);
      if (!key || seen.has(key)) continue;
      seen.add(key);
      pool.push(entry);
    }
  }

  /**
   * @param {import('./chompass-core/models.js').FoodEntry} a
   * @param {import('./chompass-core/models.js').FoodEntry} b
   */
  const withinBucket = (a, b) => {
    const aFav = favoriteKeys.has(favoriteKey(a)) ? 1 : 0;
    const bFav = favoriteKeys.has(favoriteKey(b)) ? 1 : 0;
    if (bFav !== aFav) return bFav - aFav;
    return entryStamp(b) - entryStamp(a);
  };

  /** @type {import('./chompass-core/models.js').FoodEntry[][]} */
  let buckets;
  if (currentMeal === "snack") {
    buckets = [
      pool.filter((e) => e.mealType === "snack"),
      pool.filter((e) => e.mealType !== "snack"),
    ];
  } else {
    buckets = [
      pool.filter((e) => e.mealType === currentMeal),
      pool.filter((e) => e.mealType === "snack"),
      pool.filter((e) => e.mealType !== currentMeal && e.mealType !== "snack"),
    ];
  }

  return buckets.flatMap((bucket) => bucket.slice().sort(withinBucket)).slice(0, limit);
}

/**
 * Meal-matched (or snacks in the snack window) first; favorites soft-boost
 * within each bucket. Unique by favoriteKey (Android quickRelogTemplates).
 * @param {number} [limit]
 * @returns {Promise<import('./chompass-core/models.js').FoodEntry[]>}
 */
export async function quickRelogTemplates(limit = 6) {
  const [favorites, recents, frequent, appPrefs] = await Promise.all([
    listFavorites(),
    recentFoodTemplates(30, 50),
    frequentFoodGroups(90),
    prefs.load(),
  ]);
  const currentMeal = guessMealTypeFromPrefs(appPrefs);
  return quickRelogFoodTemplates(
    favorites,
    recents,
    frequent.map((g) => g.template),
    currentMeal,
    undefined,
    limit,
  );
}

export async function listFavorites() {
  return favoritesStore.all();
}

/** @param {import('./chompass-core/models.js').FoodEntry} entry */
export async function isFavorite(entry) {
  const key = favoriteKey(entry);
  const list = await favoritesStore.all();
  return list.some((f) => favoriteKey(f) === key);
}

/**
 * Toggle favorite by favoriteKey — stores a full FoodEntry copy.
 * @param {import('./chompass-core/models.js').FoodEntry} entry
 * @returns {Promise<boolean>} true if now favorited
 */
export async function toggleFavorite(entry) {
  const key = favoriteKey(entry);
  if (!key) return false;
  const list = await favoritesStore.all();
  const idx = list.findIndex((f) => favoriteKey(f) === key);
  if (idx >= 0) {
    await favoritesStore.delete(list[idx].id);
    return false;
  }
  const copy = {
    ...entry,
    id: crypto.randomUUID(),
    recipeLogId: null,
  };
  await favoritesStore.put(copy);
  return true;
}

/** Prefill shape used by entry-form / analyze recents. */
/**
 * @param {import('./chompass-core/models.js').FoodEntry} e
 * @returns {import('./chompass-core/models.js').FoodEntry}
 */
export function toPrefill(e) {
  /** @type {import('./chompass-core/models.js').FoodEntry} */
  const out = {
    id: e.id,
    name: e.name,
    calories: e.calories,
    proteinG: e.proteinG,
    carbsG: e.carbsG,
    fatG: e.fatG,
    quantityG: e.quantityG ?? null,
    servingUnitOptions: e.servingUnitOptions ?? [],
    selectedServingUnit: e.selectedServingUnit ?? null,
    selectedServingQuantity: e.selectedServingQuantity ?? null,
    mealType: e.mealType,
    date: e.date,
    time: e.time,
    source: e.source ?? "manual",
    note: e.note ?? null,
    grounding: e.grounding ?? null,
    recipeLogId: null,
  };
  for (const key of [
    "sugarG",
    "addedSugarG",
    "fiberG",
    "saturatedFatG",
    "monounsaturatedFatG",
    "polyunsaturatedFatG",
    "cholesterolMg",
    "sodiumMg",
    "potassiumMg",
    "transFatG",
    "calciumMg",
    "ironMg",
    "magnesiumMg",
    "zincMg",
    "vitaminAMcg",
    "vitaminCMg",
    "vitaminDMcg",
    "vitaminB12Mcg",
    "vitaminEMg",
    "vitaminKMcg",
    "folateMcg",
    "omega3G",
  ]) {
    out[key] = e[key] ?? null;
  }
  return out;
}
