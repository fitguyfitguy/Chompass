// @ts-check
/**
 * Home nutrient tubes / food-row chips — mirrors Android HomeTopNutrient,
 * FoodLogMacroChip, and OptionalNutrientGoals defaults.
 */

/** @typedef {import('./chompass-core/models.js').FoodEntry} FoodEntry */
/** @typedef {{calories: number, proteinG: number, fatG: number, carbsG: number}} DailyTargets */
/** @typedef {import('./db.js').OptionalNutrientGoals} OptionalNutrientGoals */

/**
 * @typedef {Object} NutrientDef
 * @property {string} key           FoodEntry / prefs field (e.g. proteinG)
 * @property {string} label
 * @property {string} unit          g | mg | mcg
 * @property {string} tubeCss       CSS modifier for macro-tube--*
 * @property {string} [chipGlyph]   Short chip label (food rows)
 * @property {boolean} [isMacro]    True for P/C/F (goals from dailyTargets)
 * @property {boolean} [displayOnly] No optional goal (mono/poly)
 */

/** Home-tube nutrients (Android HomeTopNutrient). */
export const HOME_TOP_NUTRIENTS = /** @type {NutrientDef[]} */ ([
  { key: "proteinG", label: "Protein", unit: "g", tubeCss: "protein", chipGlyph: "P", isMacro: true },
  { key: "carbsG", label: "Carbs", unit: "g", tubeCss: "carbs", chipGlyph: "C", isMacro: true },
  { key: "fatG", label: "Fat", unit: "g", tubeCss: "fat", chipGlyph: "F", isMacro: true },
  { key: "fiberG", label: "Fiber", unit: "g", tubeCss: "fiber", chipGlyph: "Fi" },
  { key: "sugarG", label: "Sugar", unit: "g", tubeCss: "sugar", chipGlyph: "S" },
  { key: "addedSugarG", label: "Added sugar", unit: "g", tubeCss: "added-sugar" },
  { key: "saturatedFatG", label: "Sat fat", unit: "g", tubeCss: "sat-fat" },
  { key: "cholesterolMg", label: "Cholesterol", unit: "mg", tubeCss: "cholesterol" },
  { key: "sodiumMg", label: "Sodium", unit: "mg", tubeCss: "sodium" },
  { key: "potassiumMg", label: "Potassium", unit: "mg", tubeCss: "potassium" },
  { key: "transFatG", label: "Trans fat", unit: "g", tubeCss: "trans-fat" },
  { key: "calciumMg", label: "Calcium", unit: "mg", tubeCss: "calcium" },
  { key: "ironMg", label: "Iron", unit: "mg", tubeCss: "iron" },
  { key: "magnesiumMg", label: "Magnesium", unit: "mg", tubeCss: "magnesium" },
  { key: "zincMg", label: "Zinc", unit: "mg", tubeCss: "zinc" },
  { key: "vitaminAMcg", label: "Vit A", unit: "mcg", tubeCss: "vit-a" },
  { key: "vitaminCMg", label: "Vit C", unit: "mg", tubeCss: "vit-c" },
  { key: "vitaminDMcg", label: "Vit D", unit: "mcg", tubeCss: "vit-d" },
  { key: "vitaminB12Mcg", label: "B12", unit: "mcg", tubeCss: "b12" },
  { key: "vitaminEMg", label: "Vit E", unit: "mg", tubeCss: "vit-e" },
  { key: "vitaminKMcg", label: "Vit K", unit: "mcg", tubeCss: "vit-k" },
  { key: "folateMcg", label: "Folate", unit: "mcg", tubeCss: "folate" },
  { key: "omega3G", label: "Omega-3", unit: "g", tubeCss: "omega" },
]);

/** Food-row chip options (Android FoodLogMacroChip). */
export const FOOD_LOG_CHIP_KEYS = ["proteinG", "carbsG", "fatG", "fiberG", "sugarG"];

/** Android OptionalNutrientGoals.Default (int goals). */
export const DEFAULT_OPTIONAL_NUTRIENT_GOALS = /** @type {Required<OptionalNutrientGoals>} */ ({
  sugarG: 50,
  addedSugarG: 25,
  fiberG: 30,
  saturatedFatG: 20,
  cholesterolMg: 300,
  sodiumMg: 2300,
  potassiumMg: 3500,
  transFatG: 0,
  calciumMg: 1000,
  ironMg: 18,
  magnesiumMg: 400,
  zincMg: 11,
  vitaminAMcg: 900,
  vitaminCMg: 90,
  vitaminDMcg: 20,
  vitaminB12Mcg: 3,
  vitaminEMg: 15,
  vitaminKMcg: 120,
  folateMcg: 400,
  omega3G: 2,
});

/**
 * Upper clamp for custom goal values — mirrors Android
 * OptionalNutrient.maxCustomGoal (Vit D keeps the 10,000 IU / 250 mcg
 * target reachable).
 */
export const MAX_CUSTOM_GOAL_BY_KEY = /** @type {Record<string, number>} */ ({
  sugarG: 500,
  addedSugarG: 300,
  fiberG: 300,
  saturatedFatG: 200,
  cholesterolMg: 2000,
  sodiumMg: 10000,
  potassiumMg: 15000,
  transFatG: 50,
  calciumMg: 5000,
  ironMg: 200,
  magnesiumMg: 2000,
  zincMg: 100,
  vitaminAMcg: 5000,
  vitaminCMg: 2000,
  vitaminDMcg: 500,
  vitaminB12Mcg: 100,
  vitaminEMg: 1000,
  vitaminKMcg: 1000,
  folateMcg: 2000,
  omega3G: 50,
});

export const DEFAULT_HOME_TOP = ["proteinG", "carbsG", "fatG", "fiberG"];
export const DEFAULT_FOOD_CHIPS = ["proteinG", "carbsG", "fatG"];
export const MIN_NUTRIENT_CARD_COUNT = 1;
export const MAX_NUTRIENT_CARD_COUNT = 4;
export const DEFAULT_NUTRIENT_CARD_COUNT = 4;

/** Android-aligned non-nutrient prefs used by db.js DEFAULT_PREFS. */
export const ANDROID_PREF_DEFAULTS = {
  showWater: false,
  waterGoalMl: 2000,
  aiFallbackEnabled: true,
  fallbackAiProvider: "gemini",
  fallbackAiModel: "gemini-3.5-flash-lite",
  openrouterReasoningEffort: "auto",
};

/** @type {Map<string, NutrientDef>} */
const BY_KEY = new Map(HOME_TOP_NUTRIENTS.map((n) => [n.key, n]));

/** Nutrition-detail micros (incl. mono/poly display-only). */
export const NUTRITION_DETAIL_MICROS = /** @type {NutrientDef[]} */ ([
  { key: "sugarG", label: "Sugar", unit: "g", tubeCss: "sugar" },
  { key: "addedSugarG", label: "Added sugar", unit: "g", tubeCss: "added-sugar" },
  { key: "fiberG", label: "Fiber", unit: "g", tubeCss: "fiber" },
  { key: "saturatedFatG", label: "Saturated fat", unit: "g", tubeCss: "sat-fat" },
  { key: "monounsaturatedFatG", label: "Mono unsat. fat", unit: "g", tubeCss: "mono", displayOnly: true },
  { key: "polyunsaturatedFatG", label: "Poly unsat. fat", unit: "g", tubeCss: "poly", displayOnly: true },
  { key: "cholesterolMg", label: "Cholesterol", unit: "mg", tubeCss: "cholesterol" },
  { key: "sodiumMg", label: "Sodium", unit: "mg", tubeCss: "sodium" },
  { key: "potassiumMg", label: "Potassium", unit: "mg", tubeCss: "potassium" },
  { key: "transFatG", label: "Trans fat", unit: "g", tubeCss: "trans-fat" },
  { key: "calciumMg", label: "Calcium", unit: "mg", tubeCss: "calcium" },
  { key: "ironMg", label: "Iron", unit: "mg", tubeCss: "iron" },
  { key: "magnesiumMg", label: "Magnesium", unit: "mg", tubeCss: "magnesium" },
  { key: "zincMg", label: "Zinc", unit: "mg", tubeCss: "zinc" },
  { key: "vitaminAMcg", label: "Vitamin A", unit: "mcg", tubeCss: "vit-a" },
  { key: "vitaminCMg", label: "Vitamin C", unit: "mg", tubeCss: "vit-c" },
  { key: "vitaminDMcg", label: "Vitamin D", unit: "mcg", tubeCss: "vit-d" },
  { key: "vitaminB12Mcg", label: "Vitamin B12", unit: "mcg", tubeCss: "b12" },
  { key: "vitaminEMg", label: "Vitamin E", unit: "mg", tubeCss: "vit-e" },
  { key: "vitaminKMcg", label: "Vitamin K", unit: "mcg", tubeCss: "vit-k" },
  { key: "folateMcg", label: "Folate", unit: "mcg", tubeCss: "folate" },
  { key: "omega3G", label: "Omega-3", unit: "g", tubeCss: "omega" },
]);

/** @param {string} key */
export function nutrientDef(key) {
  return BY_KEY.get(key) ?? null;
}

/**
 * @param {OptionalNutrientGoals|null|undefined} stored
 * @returns {Required<OptionalNutrientGoals>}
 */
export function mergeOptionalGoals(stored) {
  return { ...DEFAULT_OPTIONAL_NUTRIENT_GOALS, ...(stored || {}) };
}

/**
 * @param {string[]|null|undefined} selection
 * @param {number} [cardCount]
 * @returns {string[]}
 */
export function normalizeHomeTopNutrients(selection, cardCount = DEFAULT_NUTRIENT_CARD_COUNT) {
  const count = Math.min(
    MAX_NUTRIENT_CARD_COUNT,
    Math.max(MIN_NUTRIENT_CARD_COUNT, Number(cardCount) || DEFAULT_NUTRIENT_CARD_COUNT)
  );
  const allowed = new Set(HOME_TOP_NUTRIENTS.map((n) => n.key));
  const picked = (selection || [])
    .map((k) => String(k).trim())
    .filter((k) => allowed.has(k));
  const merged = [...new Set([...picked, ...DEFAULT_HOME_TOP])];
  return merged.slice(0, count);
}

/**
 * @param {string[]|null|undefined} selection
 * @returns {string[]}
 */
export function normalizeFoodLogChips(selection) {
  const allowed = new Set(FOOD_LOG_CHIP_KEYS);
  const picked = (selection || [])
    .map((k) => String(k).trim())
    .filter((k) => allowed.has(k));
  const merged = [...new Set([...(picked.length ? picked : DEFAULT_FOOD_CHIPS), ...DEFAULT_FOOD_CHIPS])];
  return merged.slice(0, 5);
}

/**
 * @param {FoodEntry} entry
 * @param {string} key
 */
export function entryNutrientValue(entry, key) {
  const v = /** @type {Record<string, unknown>} */ (entry)[key];
  if (v == null || v === "") return 0;
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

/**
 * @param {FoodEntry[]} entries
 * @param {string} key
 */
export function sumNutrient(entries, key) {
  return entries.reduce((s, e) => s + entryNutrientValue(e, key), 0);
}

/**
 * Combined totals for the selected food-log chips across a meal group
 * (Android FoodLogMealGroup totals — P/C/F + optional fiber/sugar).
 * @param {FoodEntry[]} entries
 * @param {string[]} chipKeys
 * @returns {{calories: number, [key: string]: number}}
 */
export function sumMealChipValues(entries, chipKeys) {
  const keys = chipsForFoodLogDisplay(chipKeys);
  const totals = /** @type {{calories: number, [key: string]: number}} */ ({
    calories: 0,
    ...Object.fromEntries(keys.map((k) => [k, 0])),
  });
  for (const e of entries) {
    totals.calories += e.calories;
    for (const k of keys) totals[k] += entryNutrientValue(e, k);
  }
  return totals;
}

/**
 * @param {string} key
 * @param {DailyTargets|null|undefined} targets
 * @param {OptionalNutrientGoals|null|undefined} optionalGoals
 */
export function nutrientGoal(key, targets, optionalGoals) {
  const def = nutrientDef(key);
  if (!def) return 0;
  if (def.isMacro) {
    if (!targets) return 0;
    if (key === "proteinG") return targets.proteinG;
    if (key === "carbsG") return targets.carbsG;
    if (key === "fatG") return targets.fatG;
    return 0;
  }
  const goals = mergeOptionalGoals(optionalGoals);
  const g = goals[/** @type {keyof OptionalNutrientGoals} */ (key)];
  return g == null ? 0 : Number(g);
}

/**
 * Status text for a tube (unit-aware; Android macro_status_left/over formats:
 * "64g left" / "24g over" — no space between value and unit).
 * @param {number} value
 * @param {number} target
 * @param {string} unit
 */
export function tubeStatus(value, target, unit) {
  if (target <= 0) return "—";
  if (Math.round(value) === Math.round(target)) return "goal";
  const left = Math.round(target - value);
  if (value < target) return `${left}${unit} left`;
  return `${Math.round(value - target)}${unit} over`;
}

/**
 * Chip glyph for a food-row nutrient key.
 * @param {string} key
 */
export function chipGlyph(key) {
  return nutrientDef(key)?.chipGlyph ?? key.slice(0, 1).toUpperCase();
}

/** CSS modifier for colored macro chip glyphs. */
const CHIP_CSS = {
  proteinG: "protein",
  carbsG: "carbs",
  fatG: "fat",
  fiberG: "fiber",
  sugarG: "sugar",
};

/**
 * Food-row / meal-header chips follow the user's selection (Android FoodLogMacroChip).
 * @param {string[]} chipKeys
 * @returns {string[]}
 */
export function chipsForFoodLogDisplay(chipKeys) {
  return normalizeFoodLogChips(chipKeys);
}

/**
 * Colored macro chip HTML: value + colored glyph (Android-style inline).
 * @param {string} key
 * @param {number} value
 */
export function formatMacroChip(key, value) {
  const g = chipGlyph(key);
  const css = CHIP_CSS[key] || "protein";
  return `${Math.round(value)}<span class="macro-chip macro-chip--${css}">${g}</span>`;
}

/**
 * Join colored macro chips (Android meal-header style: chips separated by a
 * single space, no separator glyphs — e.g. "320 kcal · 24P 28C 9F").
 * @param {{[key: string]: number}|FoodEntry} values
 * @param {string[]} chipKeys
 * @param {string} [sep] separator between chips
 */
export function formatMacroChipLine(values, chipKeys, sep = " ") {
  const keys = chipsForFoodLogDisplay(chipKeys);
  return keys
    .map((k) => formatMacroChip(k, entryNutrientValue(/** @type {FoodEntry} */ (values), k)))
    .join(sep);
}

/**
 * Format food-row chip line from prefs (HTML).
 * @param {FoodEntry} entry
 * @param {string[]} chipKeys
 */
export function formatFoodChips(entry, chipKeys) {
  return formatMacroChipLine(entry, chipKeys);
}

/**
 * Android FoodLogMacroChipView capsules — tinted pills "P 24g" (glyph first,
 * value with unit) on a 12% tint of the macro color.
 * @param {FoodEntry} entry
 * @param {string[]} chipKeys
 */
export function formatFoodPills(entry, chipKeys) {
  const keys = chipsForFoodLogDisplay(chipKeys);
  return keys
    .map((k) => {
      const def = nutrientDef(k);
      const css = CHIP_CSS[k] || "protein";
      const unit = def?.unit ?? "g";
      const value = Math.round(entryNutrientValue(entry, k));
      return `<span class="macro-pill macro-pill--${css}">${chipGlyph(k)} ${value}${unit}</span>`;
    })
    .join("");
}

/** All FoodEntry micro field keys (for OFF / AI / share carry-through). */
export const ALL_MICRO_KEYS = [
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
];
