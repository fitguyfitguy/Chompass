// @ts-check
/**
 * Scale-invariant fingerprint of a meal's constituent mix (permille shares,
 * sorted). Uniform serving scaling does not change it; add/remove/rename/
 * quantity-edit does. Null when there are constituents are empty.
 * Web twin of `FoodConstituent.microsCompositionSignature` (Android); keep the
 * two implementations in step. Local-only by design: never placed on the
 * diary/sync/meal-share wire (same rule as the Android DataStore field).
 */

/**
 * @param {import("./models.js").FoodConstituent[]} constituents
 * @returns {string|null}
 */
export function microsCompositionSignature(constituents) {
  if (!constituents || constituents.length === 0) return null;
  const total = Math.max(
    constituents.reduce((sum, c) => sum + c.servingSizeGrams, 0),
    1e-9,
  );
  return constituents
    .map((c) => `${c.name.trim().toLowerCase()}@${Math.round((c.servingSizeGrams / total) * 1000)}`)
    .sort()
    .join("|");
}

/**
 * True when the stored analysis-time signature no longer matches the mix.
 * A null/legacy stored signature never reads stale (Android rule).
 *
 * @param {string|null|undefined} storedSignature
 * @param {import("./models.js").FoodConstituent[]} constituents
 * @returns {boolean}
 */
export function microsStaleFor(storedSignature, constituents) {
  return storedSignature != null && microsCompositionSignature(constituents) !== storedSignature;
}
