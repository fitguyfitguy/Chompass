// @ts-check
/**
 * Recipe helpers — mirror Android Recipe / RecipeIngredient logging.
 */
import { foodEntries, recipes as recipesStore } from "./db.js";
import { guessMealTypeFromPrefs } from "./meal-schedule.js";

/**
 * @typedef {Object} RecipeIngredient
 * @property {string} id
 * @property {string} name
 * @property {number} baseCalories
 * @property {number} baseProteinG
 * @property {number} baseCarbsG
 * @property {number} baseFatG
 * @property {number} [quantityScale]
 * @property {number|null} [baseFiberG]
 * @property {number|null} [baseSugarG]
 * @property {number|null} [baseSodiumMg]
 * @property {number|null} [baseQuantityG]
 */

/**
 * @typedef {Object} Recipe
 * @property {string} id
 * @property {string} name
 * @property {"breakfast"|"lunch"|"dinner"|"snack"} mealType
 * @property {RecipeIngredient[]} ingredients
 * @property {string} createdAt
 */

/** @param {import('./nofud-core/models.js').FoodEntry} entry */
export function ingredientFromFoodEntry(entry) {
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
  };
}

/** @param {RecipeIngredient} ing */
function scaled(ing) {
  const s = ing.quantityScale ?? 1;
  const scaleOpt = (v) => (v == null ? null : v * s);
  return {
    calories: Math.round(ing.baseCalories * s),
    proteinG: ing.baseProteinG * s,
    carbsG: ing.baseCarbsG * s,
    fatG: ing.baseFatG * s,
    fiberG: scaleOpt(ing.baseFiberG),
    sugarG: scaleOpt(ing.baseSugarG),
    sodiumMg: scaleOpt(ing.baseSodiumMg),
    quantityG: scaleOpt(ing.baseQuantityG),
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
    /** @type {import('./nofud-core/models.js').FoodEntry} */
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
      fiberG: n.fiberG,
      sugarG: n.sugarG,
      sodiumMg: n.sodiumMg,
      quantityG: n.quantityG,
      source: "manual",
      note: null,
      grounding: null,
      recipeLogId,
    };
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
 * @param {import('./nofud-core/models.js').FoodEntry[]} entries
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
