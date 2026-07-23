// Minimal Promise wrapper around IndexedDB — original implementation
// (not a copy of any third-party package), covering only what db.js needs:
// open with versioned upgrade, and promise-ified transactions/stores.

/**
 * @param {string} name
 * @param {number} version
 * @param {(db: IDBDatabase, oldVersion: number, newVersion: number) => void} upgrade
 * @returns {Promise<IDBDatabase>}
 */
export function openDB(name, version, upgrade) {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(name, version);
    req.onupgradeneeded = (event) => {
      upgrade(req.result, event.oldVersion, event.newVersion ?? version);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
    req.onblocked = () => reject(new Error(`openDB("${name}") blocked by another open connection`));
  });
}

/** @param {IDBRequest} req */
function wrapRequest(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

/** @param {IDBTransaction} tx */
function wrapTransaction(tx) {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve(undefined);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error ?? new Error("transaction aborted"));
  });
}

export class Store {
  /** @param {IDBDatabase} db @param {string} name */
  constructor(db, name) {
    this.db = db;
    this.name = name;
  }

  /** @param {any} value */
  put(value) {
    const tx = this.db.transaction(this.name, "readwrite");
    tx.objectStore(this.name).put(value);
    return wrapTransaction(tx);
  }

  /** @param {IDBValidKey} key */
  delete(key) {
    const tx = this.db.transaction(this.name, "readwrite");
    tx.objectStore(this.name).delete(key);
    return wrapTransaction(tx);
  }

  /** @param {IDBValidKey} key */
  async get(key) {
    const tx = this.db.transaction(this.name, "readonly");
    return wrapRequest(tx.objectStore(this.name).get(key));
  }

  async getAll() {
    const tx = this.db.transaction(this.name, "readonly");
    return wrapRequest(tx.objectStore(this.name).getAll());
  }

  /** @param {string} indexName @param {IDBValidKey|IDBKeyRange} query */
  async getAllFromIndex(indexName, query) {
    const tx = this.db.transaction(this.name, "readonly");
    return wrapRequest(tx.objectStore(this.name).index(indexName).getAll(query));
  }

  async clear() {
    const tx = this.db.transaction(this.name, "readwrite");
    tx.objectStore(this.name).clear();
    return wrapTransaction(tx);
  }
}
