// @ts-check
import { recentFoodTemplates, toPrefill } from "./saved-meals.js";

/**
 * Unique recent foods by name (most recent first), shaped for entry prefill.
 * @param {number} [limit]
 */
export async function recentFoods(limit = 12) {
  const templates = await recentFoodTemplates(30, limit);
  return templates.map(toPrefill);
}
