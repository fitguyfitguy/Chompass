// @ts-check
import { openDB, Store } from "../../vendor/idb.js";
import {
  DEFAULT_OPTIONAL_NUTRIENT_GOALS,
  DEFAULT_HOME_TOP,
  DEFAULT_FOOD_CHIPS,
  DEFAULT_NUTRIENT_CARD_COUNT,
  ANDROID_PREF_DEFAULTS,
} from "./home-nutrients.js";

// Marketing hero (web/app/demo.html) runs the real app shell against a throwaway
// database (window.CHOMPASS_DEMO is set by demo.html before this module evaluates).
// Never seed demo data over real user data.
const DB_NAME =
  typeof window !== "undefined" && /** @type {any} */ (window).CHOMPASS_DEMO
    ? "chompass-pwa-demo"
    : "chompass-pwa";
const DB_VERSION = 3;

/** @type {Promise<IDBDatabase>|null} */
let dbPromise = null;

function openChompassDb() {
  return openDB(DB_NAME, DB_VERSION, (db, oldVersion) => {
    if (oldVersion < 1) {
      db.createObjectStore("foodEntries", { keyPath: "id" }).createIndex("date", "date");
      db.createObjectStore("weights", { keyPath: "id" }).createIndex("date", "date");
      db.createObjectStore("bodyFat", { keyPath: "id" }).createIndex("date", "date");
      db.createObjectStore("measurements", { keyPath: "id" }).createIndex("date", "date");
      db.createObjectStore("water", { keyPath: "id" }).createIndex("date", "date");
      db.createObjectStore("profile", { keyPath: "id" });
      db.createObjectStore("keys", { keyPath: "id" });
    }
    if (oldVersion < 2) {
      if (!db.objectStoreNames.contains("prefs")) db.createObjectStore("prefs", { keyPath: "id" });
      if (!db.objectStoreNames.contains("chat")) db.createObjectStore("chat", { keyPath: "id" });
    }
    if (oldVersion < 3) {
      if (!db.objectStoreNames.contains("favorites")) db.createObjectStore("favorites", { keyPath: "id" });
      if (!db.objectStoreNames.contains("recipes")) db.createObjectStore("recipes", { keyPath: "id" });
    }
  });
}

async function db() {
  if (!dbPromise) dbPromise = openChompassDb();
  return dbPromise;
}

/** @param {string} storeName */
async function store(storeName) {
  return new Store(await db(), storeName);
}

/** @type {number} */
let revisionHooksSuppressed = 0;

/**
 * Suppress sync revision touch/tombstone while applying a merged sync document.
 * @template T
 * @param {() => Promise<T>} fn
 * @returns {Promise<T>}
 */
export async function withRevisionHooksSuppressed(fn) {
  revisionHooksSuppressed += 1;
  try {
    return await fn();
  } finally {
    revisionHooksSuppressed -= 1;
  }
}

/**
 * @param {string} id
 * @param {string} kind
 */
async function touchRevision(id, kind) {
  if (revisionHooksSuppressed > 0 || !id) return;
  const p = await prefs.load();
  const revisions = { ...(p.syncRevisions ?? {}) };
  revisions[id] = { updatedAt: new Date().toISOString(), deletedAt: null, kind };
  await prefs.save({ syncRevisions: revisions });
}

/**
 * @param {string} id
 * @param {string} kind
 */
async function tombstoneRevision(id, kind) {
  if (revisionHooksSuppressed > 0 || !id) return;
  const p = await prefs.load();
  const revisions = { ...(p.syncRevisions ?? {}) };
  const now = new Date().toISOString();
  revisions[id] = { updatedAt: now, deletedAt: now, kind };
  await prefs.save({ syncRevisions: revisions });
}

export const foodEntries = {
  /** @param {import('./chompass-core/models.js').FoodEntry} entry */
  async put(entry) {
    const result = await (await store("foodEntries")).put(entry);
    await touchRevision(entry.id, "food");
    return result;
  },
  /** @param {string} id */
  async delete(id) {
    const result = await (await store("foodEntries")).delete(id);
    await tombstoneRevision(id, "food");
    return result;
  },
  /** @param {string} date ISO "YYYY-MM-DD" */
  async byDate(date) {
    return (await store("foodEntries")).getAllFromIndex("date", date);
  },
  async all() {
    return (await store("foodEntries")).getAll();
  },
  async clear() {
    return (await store("foodEntries")).clear();
  },
};

export const favorites = {
  /** @param {import('./chompass-core/models.js').FoodEntry} entry */
  async put(entry) {
    const result = await (await store("favorites")).put(entry);
    await touchRevision(entry.id, "favorite");
    return result;
  },
  /** @param {string} id */
  async delete(id) {
    const result = await (await store("favorites")).delete(id);
    await tombstoneRevision(id, "favorite");
    return result;
  },
  async all() {
    return (await store("favorites")).getAll();
  },
  async clear() {
    return (await store("favorites")).clear();
  },
};

export const recipes = {
  /** @param {import('./chompass-core/models.js').Recipe} recipe */
  async put(recipe) {
    const result = await (await store("recipes")).put(recipe);
    await touchRevision(recipe.id, "recipe");
    return result;
  },
  /** @param {string} id */
  async delete(id) {
    const result = await (await store("recipes")).delete(id);
    await tombstoneRevision(id, "recipe");
    return result;
  },
  async all() {
    return (await store("recipes")).getAll();
  },
  /** @param {string} id */
  async get(id) {
    return (await store("recipes")).get(id);
  },
  async clear() {
    return (await store("recipes")).clear();
  },
};

export const weights = {
  async put(entry) {
    const result = await (await store("weights")).put(entry);
    await touchRevision(entry.id, "weight");
    return result;
  },
  async delete(id) {
    const result = await (await store("weights")).delete(id);
    await tombstoneRevision(id, "weight");
    return result;
  },
  async all() {
    return (await store("weights")).getAll();
  },
  async clear() {
    return (await store("weights")).clear();
  },
};

export const bodyFat = {
  async put(entry) {
    const result = await (await store("bodyFat")).put(entry);
    await touchRevision(entry.id, "bodyfat");
    return result;
  },
  async delete(id) {
    const result = await (await store("bodyFat")).delete(id);
    await tombstoneRevision(id, "bodyfat");
    return result;
  },
  async all() {
    return (await store("bodyFat")).getAll();
  },
  async clear() {
    return (await store("bodyFat")).clear();
  },
};

export const measurements = {
  async put(entry) {
    const result = await (await store("measurements")).put(entry);
    await touchRevision(entry.id, "measure");
    return result;
  },
  async delete(id) {
    const result = await (await store("measurements")).delete(id);
    await tombstoneRevision(id, "measure");
    return result;
  },
  async all() {
    return (await store("measurements")).getAll();
  },
  async clear() {
    return (await store("measurements")).clear();
  },
};

export const water = {
  async put(entry) {
    const result = await (await store("water")).put(entry);
    await touchRevision(entry.id, "water");
    return result;
  },
  async delete(id) {
    const result = await (await store("water")).delete(id);
    await tombstoneRevision(id, "water");
    return result;
  },
  /** @param {string} date */
  async byDate(date) {
    return (await store("water")).getAllFromIndex("date", date);
  },
  async all() {
    return (await store("water")).getAll();
  },
  async clear() {
    return (await store("water")).clear();
  },
};

const PROFILE_ID = "singleton";
const PREFS_ID = "singleton";
const CHAT_ID = "singleton";

export const profile = {
  /** @param {import('./chompass-core/models.js').UserProfile} p */
  async save(p) {
    return (await store("profile")).put({ id: PROFILE_ID, ...p });
  },
  /** @returns {Promise<import('./chompass-core/models.js').UserProfile|undefined>} */
  async load() {
    return (await store("profile")).get(PROFILE_ID);
  },
  async clear() {
    return (await store("profile")).delete(PROFILE_ID);
  },
};

/**
 * @typedef {Object} OptionalNutrientGoals
 * @property {number|null} [sugarG]
 * @property {number|null} [addedSugarG]
 * @property {number|null} [fiberG]
 * @property {number|null} [saturatedFatG]
 * @property {number|null} [cholesterolMg]
 * @property {number|null} [sodiumMg]
 * @property {number|null} [potassiumMg]
 * @property {number|null} [transFatG]
 * @property {number|null} [calciumMg]
 * @property {number|null} [ironMg]
 * @property {number|null} [magnesiumMg]
 * @property {number|null} [zincMg]
 * @property {number|null} [vitaminAMcg]
 * @property {number|null} [vitaminCMg]
 * @property {number|null} [vitaminDMcg]
 * @property {number|null} [vitaminB12Mcg]
 * @property {number|null} [vitaminEMg]
 * @property {number|null} [vitaminKMcg]
 * @property {number|null} [folateMcg]
 * @property {number|null} [omega3G]
 */

/** @typedef {Object} AppPrefs
 * @property {boolean} [onboardingComplete]
 * @property {"system"|"light"|"dark"} [theme]
 * @property {string} [accent]
 * @property {"kg"|"lb"} [weightUnit]
 * @property {"cm"|"in"} [heightUnit]
 * @property {boolean} [showWater]
 * @property {"static"|"add_active"} [calorieGaugeMode]
 * @property {number} [waterGoalMl]
 * @property {boolean} [adaptiveGoals]
 * @property {"RECENTS"|"FREQUENT"|"FAVORITES"|"RECIPES"} [lastSavedMealsSegment]
 * @property {boolean} [weekStartsOnMonday]
 * @property {number} [mealBreakfastStart]
 * @property {number} [mealLunchStart]
 * @property {number} [mealDinnerStart]
 * @property {number} [mealSnackStart]
 * @property {number} [homeNutrientCardCount]
 * @property {string[]} [homeTopNutrients]
 * @property {string[]} [foodLogMacroChips]
 * @property {OptionalNutrientGoals} [optionalNutrientGoals]
 * @property {string} [userContext]
 * @property {boolean} [aiFallbackEnabled]
 * @property {string} [fallbackAiProvider]
 * @property {string} [fallbackAiModel]
 * @property {string} [primaryAiProvider]
 * @property {boolean} [mealConstituentsEnabled] Ask AI for optional meal ingredient rows (default true)
 * @property {"gramsOnly"|"heuristic"|"aiCall"} [servingUnitInferenceMode] How to fill non-gram units when AI omits them
 * @property {Array<{id: string, date: string, name: string, calories: number}>} [manualActiveEntries] Local-only active burn logs
 * @property {string} [speechLang] BCP-47 tag for Web Speech (browser STT)
 * @property {string} [uiLang] UI locale id from locales.json (empty = auto-detect browser)
 * @property {string} [progressDefaultRangeId] Settings default Progress range (1W…All)
 * @property {string} [progressRangeId] Last Progress range chip selection (unset until first pick)
 * @property {Record<string, { updatedAt: string, deletedAt?: string|null, kind?: string }>} [syncRevisions]
 * @property {{ url?: string, username?: string, password?: string, etag?: string|null, lastSyncAt?: string|null, autoSync?: boolean, autoSyncDay?: string|null }} [webdav]
 */

export const DEFAULT_PREFS = /** @type {AppPrefs} */ ({
  onboardingComplete: false,
  theme: "system",
  accent: "system",
  weightUnit: "kg",
  heightUnit: "cm",
  showWater: ANDROID_PREF_DEFAULTS.showWater,
  calorieGaugeMode: "static",
  waterGoalMl: ANDROID_PREF_DEFAULTS.waterGoalMl,
  adaptiveGoals: false,
  lastSavedMealsSegment: "RECENTS",
  weekStartsOnMonday: true,
  mealBreakfastStart: 5 * 60,
  mealLunchStart: 11 * 60,
  mealDinnerStart: 15 * 60,
  mealSnackStart: 21 * 60,
  homeNutrientCardCount: DEFAULT_NUTRIENT_CARD_COUNT,
  homeTopNutrients: [...DEFAULT_HOME_TOP],
  foodLogMacroChips: [...DEFAULT_FOOD_CHIPS],
  optionalNutrientGoals: { ...DEFAULT_OPTIONAL_NUTRIENT_GOALS },
  userContext: "",
  aiFallbackEnabled: ANDROID_PREF_DEFAULTS.aiFallbackEnabled,
  fallbackAiProvider: ANDROID_PREF_DEFAULTS.fallbackAiProvider,
  fallbackAiModel: ANDROID_PREF_DEFAULTS.fallbackAiModel,
  primaryAiProvider: "gemini",
  mealConstituentsEnabled: true,
  servingUnitInferenceMode: "gramsOnly",
  manualActiveEntries: [],
  speechLang: "",
  uiLang: "",
  progressDefaultRangeId: "1W",
});

export const prefs = {
  /** @returns {Promise<AppPrefs>} */
  async load() {
    const row = await (await store("prefs")).get(PREFS_ID);
    if (!row) return { ...DEFAULT_PREFS, optionalNutrientGoals: { ...DEFAULT_OPTIONAL_NUTRIENT_GOALS } };
    const { id: _id, ...rest } = row;
    const merged = { ...DEFAULT_PREFS, ...rest };
    merged.optionalNutrientGoals = {
      ...DEFAULT_OPTIONAL_NUTRIENT_GOALS,
      ...(rest.optionalNutrientGoals || {}),
    };
    return merged;
  },
  /** @param {Partial<AppPrefs>} patch */
  async save(patch) {
    const current = await this.load();
    const next = { ...current, ...patch };
    if (patch.optionalNutrientGoals) {
      next.optionalNutrientGoals = {
        ...DEFAULT_OPTIONAL_NUTRIENT_GOALS,
        ...current.optionalNutrientGoals,
        ...patch.optionalNutrientGoals,
      };
    }
    return (await store("prefs")).put({ id: PREFS_ID, ...next });
  },
};

export const chat = {
  async load() {
    const row = await (await store("chat")).get(CHAT_ID);
    return row?.messages ?? [];
  },
  /** @param {any[]} messages */
  async save(messages) {
    return (await store("chat")).put({ id: CHAT_ID, messages });
  },
  async clear() {
    return (await store("chat")).delete(CHAT_ID);
  },
};

/** Encrypted BYOK provider keys — see key-storage.js for the crypto. */
export const keys = {
  async put(record) {
    return (await store("keys")).put(record);
  },
  async get(id) {
    return (await store("keys")).get(id);
  },
  async delete(id) {
    return (await store("keys")).delete(id);
  },
  async all() {
    return (await store("keys")).getAll();
  },
  async clear() {
    return (await store("keys")).clear();
  },
};

export async function clearAllUserData() {
  await Promise.all([
    foodEntries.clear(),
    favorites.clear(),
    recipes.clear(),
    weights.clear(),
    bodyFat.clear(),
    measurements.clear(),
    water.clear(),
    profile.clear(),
    chat.clear(),
    keys.clear(),
  ]);
  await prefs.save({ onboardingComplete: false, syncRevisions: {}, webdav: undefined });
}
