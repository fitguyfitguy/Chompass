// @ts-check
import { openDB, Store } from "../../vendor/idb.js";

const DB_NAME = "nofud-pwa";
const DB_VERSION = 1;

/** @type {Promise<import('../../vendor/idb.js').Store[]>|null} */
let dbPromise = null;

function openNoFudDb() {
  return openDB(DB_NAME, DB_VERSION, (db) => {
    db.createObjectStore("foodEntries", { keyPath: "id" }).createIndex("date", "date");
    db.createObjectStore("weights", { keyPath: "id" }).createIndex("date", "date");
    db.createObjectStore("bodyFat", { keyPath: "id" }).createIndex("date", "date");
    db.createObjectStore("measurements", { keyPath: "id" }).createIndex("date", "date");
    db.createObjectStore("water", { keyPath: "id" }).createIndex("date", "date");
    db.createObjectStore("profile", { keyPath: "id" });
    db.createObjectStore("keys", { keyPath: "id" });
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
};

const PROFILE_ID = "singleton";

export const profile = {
  /** @param {import('./nofud-core/models.js').UserProfile} p */
  async save(p) {
    return (await store("profile")).put({ id: PROFILE_ID, ...p });
  },
  /** @returns {Promise<import('./nofud-core/models.js').UserProfile|undefined>} */
  async load() {
    return (await store("profile")).get(PROFILE_ID);
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
