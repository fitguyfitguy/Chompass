// @ts-check
/**
 * Recipe helpers — mirror Android Recipe / RecipeIngredient logging.
 */
import { foodEntries, recipes as recipesStore } from "./db.js";
import { guessMealTypeFromPrefs } from "./meal-schedule.js";
import { ALL_MICRO_KEYS } from "./home-nutrients.js";

/**
 * @typedef {Object} RecipeIngredient
 * @property {string} id
 * @property {string} name
 * @property {number} baseCalories
 * @property {number} baseProteinG
 * @property {number} baseCarbsG
 * @property {number} baseFatG
 * @property {number} [quantityScale]
 * @property {number|null} [baseQuantityG]
 * @property {Record<string, number|null|undefined>} [baseMicros]
 */

/**
 * @typedef {Object} Recipe
 * @property {string} id
 * @property {string} name
 * @property {"breakfast"|"lunch"|"dinner"|"snack"} mealType
 * @property {RecipeIngredient[]} ingredients
 * @property {string} createdAt
 */

/** @param {import('./chompass-core/models.js').FoodEntry} entry */
export function ingredientFromFoodEntry(entry) {
  /** @type {Record<string, number|null>} */
  const baseMicros = {};
  for (const key of ALL_MICRO_KEYS) {
    const v = /** @type {Record<string, unknown>} */ (entry)[key];
    baseMicros[key] = v == null ? null : Number(v);
  }
  // Legacy single-field names kept for older recipe rows
  return {
    id: crypto.randomUUID(),
    name: entry.name,
    baseCalories: entry.calories,
    baseProteinG: entry.proteinG,
    baseCarbsG: entry.carbsG,
    baseFatG: entry.fatG,
    quantityScale: 1,
    baseFiberG: entry.fiberG ?? null,
    baseSugarG: entry.sugarG ?? null,
    baseSodiumMg: entry.sodiumMg ?? null,
    baseQuantityG: entry.quantityG ?? null,
    baseMicros,
  };
}

/** @param {RecipeIngredient & {baseFiberG?: number|null, baseSugarG?: number|null, baseSodiumMg?: number|null}} ing */
function scaled(ing) {
  const s = ing.quantityScale ?? 1;
  const scaleOpt = (v) => (v == null ? null : v * s);
  /** @type {Record<string, number|null>} */
  const micros = {};
  const source = ing.baseMicros || {
    fiberG: ing.baseFiberG ?? null,
    sugarG: ing.baseSugarG ?? null,
    sodiumMg: ing.baseSodiumMg ?? null,
  };
  for (const key of ALL_MICRO_KEYS) {
    micros[key] = scaleOpt(source[key] ?? null);
  }
  return {
    calories: Math.round(ing.baseCalories * s),
    proteinG: ing.baseProteinG * s,
    carbsG: ing.baseCarbsG * s,
    fatG: ing.baseFatG * s,
    quantityG: scaleOpt(ing.baseQuantityG),
    micros,
  };
}

/**
 * Expand a recipe into diary rows sharing recipeLogId.
 * @param {Recipe} recipe
 * @param {string} date
 * @param {import('./db.js').AppPrefs} [appPrefs]
 */
export async function logRecipe(recipe, date, appPrefs) {
  const recipeLogId = crypto.randomUUID();
  const now = new Date();
  const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
  const mealType = recipe.mealType || guessMealTypeFromPrefs(appPrefs);
  for (const ing of recipe.ingredients) {
    const n = scaled(ing);
    /** @type {import('./chompass-core/models.js').FoodEntry} */
    const entry = {
      id: crypto.randomUUID(),
      name: ing.name,
      mealType,
      date,
      time,
      calories: n.calories,
      proteinG: n.proteinG,
      carbsG: n.carbsG,
      fatG: n.fatG,
      quantityG: n.quantityG,
      source: "manual",
      note: null,
      grounding: null,
      recipeLogId,
    };
    for (const key of ALL_MICRO_KEYS) {
      entry[key] = n.micros[key];
    }
    await foodEntries.put(entry);
  }
  return recipeLogId;
}

export async function listRecipes() {
  const all = await recipesStore.all();
  return all.slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

/** @param {Recipe} recipe */
export async function saveRecipe(recipe) {
  return recipesStore.put(recipe);
}

/** @param {string} id */
export async function deleteRecipe(id) {
  return recipesStore.delete(id);
}

/**
 * @param {string} name
 * @param {import('./chompass-core/models.js').FoodEntry[]} entries
 * @param {"breakfast"|"lunch"|"dinner"|"snack"} [mealType]
 */
export function recipeFromEntries(name, entries, mealType) {
  return {
    id: crypto.randomUUID(),
    name,
    mealType: mealType ?? entries[0]?.mealType ?? "snack",
    ingredients: entries.map(ingredientFromFoodEntry),
    createdAt: new Date().toISOString(),
  };
}
