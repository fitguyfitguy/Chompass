// @ts-check
import { foodEntries } from "./db.js";

/**
 * Unique recent foods by name (most recent first), shaped for entry prefill.
 * @param {number} [limit]
 */
export async function recentFoods(limit = 12) {
  const all = await foodEntries.all();
  /** @type {Map<string, object>} */
  const byName = new Map();
  const sorted = all.slice().sort((a, b) => `${b.date}T${b.time}`.localeCompare(`${a.date}T${a.time}`));
  for (const e of sorted) {
    const key = e.name.trim().toLowerCase();
    if (!key || byName.has(key)) continue;
    byName.set(key, {
      name: e.name,
      calories: e.calories,
      proteinG: e.proteinG,
      carbsG: e.carbsG,
      fatG: e.fatG,
      fiberG: e.fiberG ?? null,
      quantityG: e.quantityG ?? null,
      mealType: e.mealType,
      source: "manual",
      note: null,
    });
    if (byName.size >= limit) break;
  }
  return [...byName.values()];
}
