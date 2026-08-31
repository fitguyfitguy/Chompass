// @ts-check
import { openDB, Store } from "../../vendor/idb.js";
import {
  DEFAULT_OPTIONAL_NUTRIENT_GOALS,
  DEFAULT_HOME_TOP,
  DEFAULT_FOOD_CHIPS,
  DEFAULT_NUTRIENT_CARD_COUNT,
  ANDROID_PREF_DEFAULTS,
  migrateLegacyCaffeineLimit,
} from "./home-nutrients.js";

// Marketing hero (web/app/demo.html) runs the real app shell against a throwaway
// database (window.CHOMPASS_DEMO is set by demo.html before this module evaluates).
// Never seed demo data over real user data.
const DB_NAME =
  typeof window !== "undefined" && /** @type {any} */ (window).CHOMPASS_DEMO
    ? "chompass-pwa-demo"
    : "chompass-pwa";
const DB_VERSION = 7;

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
    if (oldVersion < 4) {
      if (!db.objectStoreNames.contains("dailyNotes")) {
        db.createObjectStore("dailyNotes", { keyPath: "id" }).createIndex("date", "date");
      }
      if (!db.objectStoreNames.contains("nicotine")) db.createObjectStore("nicotine", { keyPath: "id" }).createIndex("date", "date");
    }
    // Nicotine tracker landed after daily notes on the same DB version 4 (both
    // used v4 in their feature branches); devices already on v4 would skip the
    // upgrade block, so the nicotine store gets its own version bump.
    if (oldVersion < 5) {
      if (!db.objectStoreNames.contains("nicotine")) db.createObjectStore("nicotine", { keyPath: "id" }).createIndex("date", "date");
    }
    // Caffeine tracker (device-pass revision): own store, own version bump.
    if (oldVersion < 6) {
      if (!db.objectStoreNames.contains("caffeine")) db.createObjectStore("caffeine", { keyPath: "id" }).createIndex("date", "date");
    }
    // Per-day goal journal (Codeberg #60): entries keyed by ISO date, with the
    // deterministic per-day sync id (goalJournalIdFor) as `id` for sync merges.
    if (oldVersion < 7) {
      if (!db.objectStoreNames.contains("goalJournal")) {
        db.createObjectStore("goalJournal", { keyPath: "date" }).createIndex("date", "date");
      }
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

/**
 * Raw access to a single object store's Store wrapper (bulk demo seeding,
 * diagnostics). Prefer the typed collections above in app code.
 * @param {string} storeName
 */
export async function rawStore(storeName) {
  return store(storeName);
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

export const nicotine = {
  /** @param {import("./chompass-core/models.js").NicotineEntry} entry */
  async put(entry) {
    const result = await (await store("nicotine")).put(entry);
    await touchRevision(entry.id, "nicotine");
    return result;
  },
  async delete(id) {
    const result = await (await store("nicotine")).delete(id);
    await tombstoneRevision(id, "nicotine");
    return result;
  },
  /** @param {string} date */
  async byDate(date) {
    return (await store("nicotine")).getAllFromIndex("date", date);
  },
  async all() {
    return (await store("nicotine")).getAll();
  },
  async clear() {
    return (await store("nicotine")).clear();
  },
};

export const caffeine = {
  /** @param {import("./chompass-core/models.js").CaffeineEntry} entry */
  async put(entry) {
    const result = await (await store("caffeine")).put(entry);
    await touchRevision(entry.id, "caffeine");
    return result;
  },
  async delete(id) {
    const result = await (await store("caffeine")).delete(id);
    await tombstoneRevision(id, "caffeine");
    return result;
  },
  /** @param {string} date */
  async byDate(date) {
    return (await store("caffeine")).getAllFromIndex("date", date);
  },
  async all() {
    return (await store("caffeine")).getAll();
  },
  async clear() {
    return (await store("caffeine")).clear();
  },
};

export const dailyNotes = {
  /** @param {import('./chompass-core/models.js').DailyNote} note */
  async put(note) {
    const result = await (await store("dailyNotes")).put(note);
    await touchRevision(note.id, "daily_note");
    return result;
  },
  async delete(id) {
    const result = await (await store("dailyNotes")).delete(id);
    await tombstoneRevision(id, "daily_note");
    return result;
  },
  /** @param {string} date */
  async byDate(date) {
    return (await store("dailyNotes")).getAllFromIndex("date", date);
  },
  async all() {
    return (await store("dailyNotes")).getAll();
  },
  async clear() {
    return (await store("dailyNotes")).clear();
  },
};

/** Per-day goal journal (Codeberg #60). Entries carry both `date` (key)
 *  and the deterministic per-day sync id (goalJournalIdFor) for merges. */
export const goalJournal = {
  /** @param {import('./chompass-core/macro-plan.js').GoalJournalEntry & { id?: string }} entry */
  async put(entry) {
    const result = await (await store("goalJournal")).put(entry);
    await touchRevision(entry.id ?? entry.date, "goal_journal");
    return result;
  },
  async delete(id) {
    const result = await (await store("goalJournal")).delete(id);
    await tombstoneRevision(id, "goal_journal");
    return result;
  },
  /** @returns {Promise<import('./chompass-core/macro-plan.js').GoalJournalEntry[]>} */
  async all() {
    return (await store("goalJournal")).getAll();
  },
  async clear() {
    return (await store("goalJournal")).clear();
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
 * @property {number|null} [caffeineMg]
 */

/** @typedef {Object} AppPrefs
 * @property {boolean} [onboardingComplete]
 * @property {"system"|"light"|"dark"} [theme]
 * @property {string} [accent]
 * @property {"kg"|"lb"} [weightUnit]
 * @property {"cm"|"in"} [heightUnit]
 * @property {boolean} [showWater]
 * @property {boolean} [showNicotine]
 * @property {number} [nicotineDailyLimit]
 * @property {boolean} [showCaffeine]
 * @property {number} [caffeineDailyLimitMg] Legacy alias of `optionalNutrientGoals.caffeineMg` (pre-WS5); migrated once and never written again
 * @property {boolean} [showNotes]
 * @property {boolean} [coachTabEnabled] Hide the coach tab (Android parity; default true)
 * @property {boolean} [aiFeaturesEnabled] Master AI-features switch; off = no data to any LLM provider (default true)
 * @property {"static"|"add_active"} [calorieGaugeMode]
 * @property {number} [waterGoalMl]
 * @property {boolean} [adaptiveGoals]
 * @property {"RECENTS"|"FREQUENT"|"FAVORITES"|"RECIPES"} [lastSavedMealsSegment]
 * @property {"recent"|"name"|"size"} [lastSavedMealsSort]
 * @property {boolean} [weekStartsOnMonday]
 * @property {"monday"|"sunday"|"saturday"} [weekStartDay]
 * @property {number} [mealBreakfastStart]
 * @property {number} [mealLunchStart]
 * @property {number} [mealDinnerStart]
 * @property {number} [mealSnackStart]
 * @property {{version?: number, meals?: Array<{id: string, label?: string, startMinutes?: number|null, enabled?: boolean}>}|null} [mealCatalog]
 * @property {number} [homeNutrientCardCount]
 * @property {string[]} [homeTopNutrients]
 * @property {string[]} [foodLogMacroChips]
 * @property {OptionalNutrientGoals} [optionalNutrientGoals]
 * @property {string} [userContext]
 * @property {boolean} [aiFallbackEnabled]
 * @property {string} [fallbackAiProvider]
 * @property {string} [fallbackAiModel]
 * @property {string} [primaryAiProvider]
 * @property {string} [openrouterReasoningEffort] "auto"|"low"|"medium"|"high" — OpenRouter thinking budget (auto = omit param)
 * @property {boolean} [mealConstituentsEnabled] Ask AI for optional meal ingredient rows (default true)
 * @property {"gramsOnly"|"heuristic"|"aiCall"} [servingUnitInferenceMode] How to fill non-gram units when AI omits them
 * @property {Array<{id: string, date: string, name: string, calories: number}>} [manualActiveEntries] Local-only active burn logs
 * @property {boolean} [showFasting] Optional local-only intermittent-fasting timer (off by default)
 * @property {number} [fastingGoalHours] Goal fast length in hours; 16 by default (16:8 protocol), 0 = no goal
 * @property {number} [fastingEatHours] Eating-window length in hours; 8 by default (16:8 protocol), 0 = not configured
 * @property {boolean} [fastingAutoWindows] Auto-cycle: fast starts when the eating window closes, ends at the goal
 * @property {number} [fastingStartHour] Daily fast-start clock hour (0-23, default 20)
 * @property {number} [fastingStartMinute] Daily fast-start clock minute (0-59)
 * @property {boolean} [fastingAutoStarted] The running fast was started by the auto-cycle (no manual buttons)
 * @property {number|null} [fastingStartedAt] Epoch millis the running fast started; null = idle
 * @property {number|null} [fastingLastEndedAt] Epoch millis the last completed fast ended
 * @property {number|null} [fastingLastFastStartedAt] Epoch millis the last completed fast started
 * @property {boolean} [fastingGoalNotified] One-shot goal latch (alarm fire)
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
  showNicotine: ANDROID_PREF_DEFAULTS.showNicotine,
  nicotineDailyLimit: ANDROID_PREF_DEFAULTS.nicotineDailyLimit,
  showCaffeine: ANDROID_PREF_DEFAULTS.showCaffeine,
  showNotes: ANDROID_PREF_DEFAULTS.showNotes,
  coachTabEnabled: true,
  aiFeaturesEnabled: true,
  calorieGaugeMode: "static",
  waterGoalMl: ANDROID_PREF_DEFAULTS.waterGoalMl,
  adaptiveGoals: false,
  lastSavedMealsSegment: "RECENTS",
  lastSavedMealsSort: "recent",
  weekStartsOnMonday: true,
  weekStartDay: "monday",
  mealBreakfastStart: 5 * 60,
  mealLunchStart: 11 * 60,
  mealDinnerStart: 15 * 60,
  mealSnackStart: 21 * 60,
  mealCatalog: null,
  homeNutrientCardCount: DEFAULT_NUTRIENT_CARD_COUNT,
  homeTopNutrients: [...DEFAULT_HOME_TOP],
  foodLogMacroChips: [...DEFAULT_FOOD_CHIPS],
  optionalNutrientGoals: { ...DEFAULT_OPTIONAL_NUTRIENT_GOALS },
  userContext: "",
  aiFallbackEnabled: ANDROID_PREF_DEFAULTS.aiFallbackEnabled,
  fallbackAiProvider: ANDROID_PREF_DEFAULTS.fallbackAiProvider,
  fallbackAiModel: ANDROID_PREF_DEFAULTS.fallbackAiModel,
  primaryAiProvider: "gemini",
  openrouterReasoningEffort: ANDROID_PREF_DEFAULTS.openrouterReasoningEffort,
  mealConstituentsEnabled: true,
  servingUnitInferenceMode: "gramsOnly",
  manualActiveEntries: [],
  showFasting: false,
  fastingGoalHours: 16,
  fastingEatHours: 8,
  fastingAutoWindows: true,
  fastingStartHour: 20,
  fastingStartMinute: 0,
  fastingAutoStarted: false,
  fastingStartedAt: null,
  fastingLastEndedAt: null,
  fastingLastFastStartedAt: null,
  fastingGoalNotified: false,
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
    // WS5 one-time migration: the tracker's legacy daily-limit pref
    // (caffeineDailyLimitMg) was an alias of optionalNutrientGoals.caffeineMg
    // and the two could disagree. A customized legacy value wins once over the
    // still-default goal, then the key is dropped and never written again —
    // the Goals & Nutrition caffeine goal is the single daily-max knob.
    const migrated = migrateLegacyCaffeineLimit(merged.optionalNutrientGoals, rest.caffeineDailyLimitMg);
    if (migrated !== merged.optionalNutrientGoals) {
      merged.optionalNutrientGoals = migrated;
      const clean = { ...merged };
      delete clean.caffeineDailyLimitMg;
      await (await store("prefs")).put({ id: PREFS_ID, ...clean });
    }
    delete merged.caffeineDailyLimitMg;
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
    nicotine.clear(),
    caffeine.clear(),
    goalJournal.clear(),
    profile.clear(),
    chat.clear(),
    keys.clear(),
  ]);
  await prefs.save({ onboardingComplete: false, syncRevisions: {}, webdav: undefined });
}
