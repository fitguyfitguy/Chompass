// @ts-check
import { openDB, Store } from "../../vendor/idb.js";

const DB_NAME = "nofud-pwa";
const DB_VERSION = 2;

/** @type {Promise<import('../../vendor/idb.js').Store[]>|null} */
let dbPromise = null;

function openNoFudDb() {
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
  });
}

async function db() {
  if (!dbPromise) dbPromise = openNoFudDb();
  return dbPromise;
}

/** @param {string} storeName */
async function store(storeName) {
  return new Store(await db(), storeName);
}

export const foodEntries = {
  /** @param {import('./nofud-core/models.js').FoodEntry} entry */
  async put(entry) {
    return (await store("foodEntries")).put(entry);
  },
  /** @param {string} id */
  async delete(id) {
    return (await store("foodEntries")).delete(id);
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

export const weights = {
  async put(entry) {
    return (await store("weights")).put(entry);
  },
  async delete(id) {
    return (await store("weights")).delete(id);
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
    return (await store("bodyFat")).put(entry);
  },
  async delete(id) {
    return (await store("bodyFat")).delete(id);
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
    return (await store("measurements")).put(entry);
  },
  async delete(id) {
    return (await store("measurements")).delete(id);
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
    return (await store("water")).put(entry);
  },
  async delete(id) {
    return (await store("water")).delete(id);
  },
  /** @param {string} date */
  async byDate(date) {
    return (await store("water")).getAllFromIndex("date", date);
  },
  async clear() {
    return (await store("water")).clear();
  },
};

const PROFILE_ID = "singleton";
const PREFS_ID = "singleton";
const CHAT_ID = "singleton";

export const profile = {
  /** @param {import('./nofud-core/models.js').UserProfile} p */
  async save(p) {
    return (await store("profile")).put({ id: PROFILE_ID, ...p });
  },
  /** @returns {Promise<import('./nofud-core/models.js').UserProfile|undefined>} */
  async load() {
    return (await store("profile")).get(PROFILE_ID);
  },
  async clear() {
    return (await store("profile")).delete(PROFILE_ID);
  },
};

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
 */

export const prefs = {
  /** @returns {Promise<AppPrefs>} */
  async load() {
    const row = await (await store("prefs")).get(PREFS_ID);
    if (!row) {
      return {
        onboardingComplete: false,
        theme: "system",
        accent: "teal",
        weightUnit: "kg",
        heightUnit: "cm",
        showWater: true,
        calorieGaugeMode: "static",
        waterGoalMl: 2500,
        adaptiveGoals: false,
      };
    }
    const { id: _id, ...rest } = row;
    return {
      onboardingComplete: false,
      theme: "system",
      accent: "teal",
      weightUnit: "kg",
      heightUnit: "cm",
      showWater: true,
      calorieGaugeMode: "static",
      waterGoalMl: 2500,
      adaptiveGoals: false,
      ...rest,
    };
  },
  /** @param {Partial<AppPrefs>} patch */
  async save(patch) {
    const current = await this.load();
    return (await store("prefs")).put({ id: PREFS_ID, ...current, ...patch });
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
};

export async function clearAllUserData() {
  await Promise.all([
    foodEntries.clear(),
    weights.clear(),
    bodyFat.clear(),
    measurements.clear(),
    water.clear(),
    profile.clear(),
    chat.clear(),
  ]);
  await prefs.save({ onboardingComplete: false });
}
