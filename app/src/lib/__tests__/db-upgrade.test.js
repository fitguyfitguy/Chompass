// @ts-check
// Codeberg #112: 5.2.0 shipped the `untrackedDays` store consumers (db.js) without
// `createObjectStore` or a version bump, so every install upgrading from DB v7 threw
// `NotFoundError: Failed to execute 'transaction' on 'IDBDatabase'` and Home stayed
// blank. Regression: open the database at v7 first (pre-hotfix install), reopen with
// the real upgrade chain, and require the store to exist and serve reads/writes.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { untrackedDays } from "../db.js";

/**
 * Minimal in-memory IndexedDB covering exactly what vendor/idb.js uses: open with
 * versioned upgrade (upgradeneeded before success, like the browser), objectStoreNames
 * .contains, createObjectStore/createIndex, and transaction()/objectStore() where a
 * missing store throws NotFoundError — the browser semantics that made the bug visible.
 */
function installFakeIndexedDb() {
  /** @type {Map<string, {version: number, stores: Map<string, {keyPath: string, rows: Map<string, any>}>}>} */
  const databases = new Map();

  /** @param {any} result */
  function makeRequest(result) {
    const req = { result, onsuccess: null, onerror: null };
    queueMicrotask(() => req.onsuccess?.({ target: req }));
    return req;
  }

  globalThis.indexedDB = /** @type {any} */ ({
    /** @param {string} name @param {number} version */
    open(name, version) {
      const existing = databases.get(name);
      const oldVersion = existing ? existing.version : 0;
      if (!existing) databases.set(name, { version, stores: new Map() });
      else existing.version = version;
      const record = databases.get(name);

      const storeNames = new Set(record.stores.keys());
      const db = {
        objectStoreNames: { contains: (/** @type {string} */ n) => storeNames.has(n) },
        /** @param {string} n @param {{keyPath: string}} options */
        createObjectStore(n, options) {
          const store = { keyPath: options.keyPath, rows: new Map() };
          record.stores.set(n, store);
          storeNames.add(n);
          return {
            /** @param {string} indexName @param {string} keyPath */
            createIndex(indexName, keyPath) {
              void indexName;
              void keyPath;
              return this;
            },
          };
        },
        /** Browser throws synchronously when a transaction names a missing store. */
        transaction(n) {
          if (!storeNames.has(n)) {
            const err = new Error(
              "Failed to execute 'transaction' on 'IDBDatabase': One of the specified object stores was not found.",
            );
            err.name = "NotFoundError";
            throw err;
          }
          const tx = { oncomplete: null, onerror: null, onabort: null };
          tx.objectStore = () => ({
            /** @param {any} value */
            put(value) {
              record.stores.get(n).rows.set(value[record.stores.get(n).keyPath], value);
            },
            /** @param {string} key */
            delete(key) {
              record.stores.get(n).rows.delete(key);
            },
            getAll() {
              return makeRequest([...record.stores.get(n).rows.values()]);
            },
          });
          queueMicrotask(() => tx.oncomplete?.({ target: tx }));
          return tx;
        },
      };

      const req = {
        result: db,
        onsuccess: null,
        onerror: null,
        onupgradeneeded: null,
      };
      queueMicrotask(() => {
        if (version > oldVersion) {
          req.onupgradeneeded?.({ target: req, oldVersion, newVersion: version });
        }
        req.onsuccess?.({ target: req });
      });
      return req;
    },
  });
  return databases;
}

/** @param {number} version @returns {Promise<void>} */
function openBaseline(version) {
  return new Promise((resolve, reject) => {
    const req = globalThis.indexedDB.open("chompass-pwa", version);
    req.onupgradeneeded = (/** @type {any} */ event) => {
      // Stand-in for the pre-hotfix v1-7 history: what matters is the on-disk
      // version and the missing untrackedDays store.
      event.target.result.createObjectStore("goalJournal", { keyPath: "date" }).createIndex("date", "date");
    };
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

describe("db upgrade chain (#112)", () => {
  it("creates untrackedDays when upgrading a v7 database and serves reads/writes", async () => {
    const databases = installFakeIndexedDb();

    // Simulate a 5.2.0-era install: database at version 7, no untrackedDays store.
    await openBaseline(7);
    assert.equal(databases.get("chompass-pwa").version, 7);
    assert.equal(databases.get("chompass-pwa").stores.has("untrackedDays"), false);

    // Pre-fix, this threw NotFoundError on Home/diary renders.
    assert.deepEqual(await untrackedDays.all(), []);
    assert.equal(databases.get("chompass-pwa").version, 8);
    assert.equal(databases.get("chompass-pwa").stores.has("untrackedDays"), true);

    const row = { date: "2026-09-25", kcal: null };
    await untrackedDays.put(row);
    assert.deepEqual(await untrackedDays.all(), [row]);
    await untrackedDays.delete("2026-09-25");
    assert.deepEqual(await untrackedDays.all(), []);
  });
});
