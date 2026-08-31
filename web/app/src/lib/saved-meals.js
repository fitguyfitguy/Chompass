// @ts-check
/**
 * Saved Meals helpers — Recents / Frequent / Favorites, mirroring Android
 * FoodRepository recentFoodTemplates / frequentFoodGroups / toggleFavorite.
 */
import { foodEntries, favorites as favoritesStore } from "./db.js";

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
 * @param {string} [mealType]
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
 * @param {import('./chompass-core/models.js').FoodEntry[]} entries
 * @param {number} days
 */
function entriesInWindow(entries, days) {
  const cutoff = Date.now() - days * 86400000;
  return entries.filter((e) => {
    const ts = Date.parse(`${e.date}T${e.time || "12:00"}`);
    return !Number.isNaN(ts) && ts >= cutoff;
  });
}

/**
 * Newest-first templates, one per favoriteKey.
 * @param {import('./chompass-core/models.js').FoodEntry[]} entries
 * @param {number} [limit]
 */
export function recentTemplatesFrom(entries, limit = 50) {
  const seen = new Set();
  /** @type {import('./chompass-core/models.js').FoodEntry[]} */
  const out = [];
  entries
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
 * Newest-first templates, one per favoriteKey, within `days`.
 * @param {number} [days]
 * @param {number} [limit]
 */
export async function recentFoodTemplates(days = 30, limit = 50) {
  return recentTemplatesFrom(entriesInWindow(await foodEntries.all(), days), limit);
}

/**
 * All-time diary collapse (newest row per favoriteKey) for Saved Meals Recents.
 * @returns {Promise<import('./chompass-core/models.js').FoodEntry[]>}
 */
export async function historyTemplates() {
  return recentTemplatesFrom(await foodEntries.all(), Number.POSITIVE_INFINITY);
}

/**
 * Substring, case-insensitive name match. Blank query returns `list` as-is.
 * @param {import('./chompass-core/models.js').FoodEntry[]} list
 * @param {string} query
 */
export function filterHistoryTemplates(list, query) {
  const q = String(query ?? "").trim().toLowerCase();
  if (!q) return list;
  return list.filter((e) => e.name.toLowerCase().includes(q));
}

/**
 * Recents display order. `list` is already unique-by-favoriteKey.
 * Filter first, then sort.
 * @param {import('./chompass-core/models.js').FoodEntry[]} list
 * @param {"recent"|"name"|"size"} [sort]
 */
export function sortHistoryTemplates(list, sort = "recent") {
  const copy = list.slice();
  if (sort === "name") {
    return copy.sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" }));
  }
  if (sort === "size") {
    return copy.sort(
      (a, b) =>
        (b.calories ?? 0) - (a.calories ?? 0) ||
        a.name.localeCompare(b.name, undefined, { sensitivity: "base" }),
    );
  }
  return copy.sort((a, b) => `${b.date}T${b.time}`.localeCompare(`${a.date}T${a.time}`));
}

/**
 * @param {import('./chompass-core/models.js').FoodEntry[]} entries
 * @returns {{template: import('./chompass-core/models.js').FoodEntry, count: number}[]}
 */
export function frequentGroupsFrom(entries) {
  /** @type {Map<string, {count: number, template: import('./chompass-core/models.js').FoodEntry}>} */
  const aggregates = new Map();
  for (const e of entries) {
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
 * @param {number} [days]
 * @returns {Promise<{template: import('./chompass-core/models.js').FoodEntry, count: number}[]>}
 */
export async function frequentFoodGroups(days = 90) {
  return frequentGroupsFrom(entriesInWindow(await foodEntries.all(), days));
}

/**
 * Two hub rows from already-windowed diary snapshots. Recents are newest
 * first and unique by favoriteKey; frequents are count-desc (then name)
 * and skip keys already shown in recents.
 * @param {import('./chompass-core/models.js').FoodEntry[]} recentWindow
 * @param {import('./chompass-core/models.js').FoodEntry[]} frequentWindow
 * @param {number} [perRow]
 * @returns {{recents: import('./chompass-core/models.js').FoodEntry[], frequents: import('./chompass-core/models.js').FoodEntry[]}}
 */
export function quickRelogRowsFrom(recentWindow, frequentWindow, perRow = 10) {
  const recents = recentTemplatesFrom(recentWindow, perRow);
  const recentKeys = new Set(recents.map(favoriteKey).filter(Boolean));
  const frequents = frequentGroupsFrom(frequentWindow)
    .filter((g) => {
      const key = favoriteKey(g.template);
      return key && !recentKeys.has(key);
    })
    .slice(0, perRow)
    .map((g) => g.template);
  return { recents, frequents };
}

/**
 * Hub chips: recents (30 days) then frequents (90 days), one diary decode.
 * @param {number} [perRow]
 * @returns {Promise<{recents: import('./chompass-core/models.js').FoodEntry[], frequents: import('./chompass-core/models.js').FoodEntry[]}>}
 */
export async function quickRelogRows(perRow = 10) {
  const all = await foodEntries.all();
  return quickRelogRowsFrom(entriesInWindow(all, 30), entriesInWindow(all, 90), perRow);
}

export async function listFavorites() {
  return favoritesStore.all();
}

/**
 * Pure update normalization for a stored favorite (Codeberg #66, Android
 * FoodRepository.updateFavorite parity): the id stays as passed so the sync
 * LWW chain survives renames (which change favoriteKey), and recipeLogId is
 * normalized to null — a favorite is a standalone saved food.
 * @param {import('./chompass-core/models.js').FoodEntry} entry
 * @returns {import('./chompass-core/models.js').FoodEntry}
 */
export function normalizedFavoriteUpdate(entry) {
  return { ...entry, recipeLogId: null };
}

/**
 * Update a stored favorite in place (Codeberg #66): favorites are a
 * permanently editable saved-foods library. Same id → IndexedDB put replaces
 * the row; the caller has already checked the name is not taken.
 * @param {import('./chompass-core/models.js').FoodEntry} entry
 * @returns {Promise<import('./chompass-core/models.js').FoodEntry>}
 */
export async function updateFavorite(entry) {
  const copy = normalizedFavoriteUpdate(entry);
  await favoritesStore.put(copy);
  return copy;
}

/**
 * Pure rename-collision check (Android FoodRepository.favoriteRenameBlocklist
 * parity): true when [name] identifies any diary row or any favorite other
 * than the one being renamed — the name is the identity key, so a taken name
 * blocks the save instead of silently forking identities.
 * @param {string} name
 * @param {import('./chompass-core/models.js').FoodEntry[]} diary
 * @param {import('./chompass-core/models.js').FoodEntry[]} favorites
 * @param {string|null} [selfId] id of the favorite being renamed (own key allowed)
 */
export function favoriteNameTakenFrom(name, diary, favorites, selfId = null) {
  const key = favoriteKey({ name });
  if (!key) return false;
  return (
    diary.some((e) => favoriteKey(e) === key) ||
    favorites.some((f) => f.id !== selfId && favoriteKey(f) === key)
  );
}

/**
 * @param {string} name
 * @param {string|null} [selfId]
 * @returns {Promise<boolean>}
 */
export async function favoriteNameTaken(name, selfId = null) {
  return favoriteNameTakenFrom(name, await foodEntries.all(), await favoritesStore.all(), selfId);
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
    "caffeineMg",
  ]) {
    out[key] = e[key] ?? null;
  }
  return out;
}
