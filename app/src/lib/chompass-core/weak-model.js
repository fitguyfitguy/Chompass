// @ts-check
/**
 * Small-cloud-model classifier shared with Android FoodAnalysisService
 * `isSmallCloudModel`. Lite/nano/haiku/mini plus OpenRouter `/free` endpoints
 * are the weak class: SAFE goal-recalc tier and macros-only constituent rows.
 *
 * @param {string} [model]
 * @returns {boolean}
 */
export function isSmallCloudModel(model) {
  const m = (model || "").toLowerCase();
  // "mini" only matches as trailing "-mini" — a bare "mini-" would hit
  // every "gemini-*" id.
  return ["flash-lite", "nano", "haiku", "-mini", "/free"].some((token) => m.includes(token));
}
