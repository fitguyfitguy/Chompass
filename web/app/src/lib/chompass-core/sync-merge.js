// @ts-check
/**
 * Last-write-wins merge for Chompass sync-1.0 documents.
 * Pure functions — no I/O. Mirrors android/.../export/SyncMerge.kt.
 */

/**
 * @typedef {{ id: string, updated_at: string, deleted_at?: string|null }} SyncMeta
 */

/**
 * Compare ISO-8601 (or any lexicographically ordered) timestamps.
 * Returns negative if a < b, 0 if equal, positive if a > b.
 * @param {string} a
 * @param {string} b
 */
export function compareUpdatedAt(a, b) {
  if (a === b) return 0;
  return a < b ? -1 : 1;
}

/**
 * Pick the winning record for one id. Tombstones are records with deleted_at set.
 * Tie-break: prefer the side that has a deleted_at when updated_at equal (delete wins),
 * otherwise prefer `remote` so a pull-then-merge prefers the server copy on ties.
 * @template {SyncMeta} T
 * @param {T|undefined} local
 * @param {T|undefined} remote
 * @returns {T|undefined}
 */
export function pickNewer(local, remote) {
  if (!local) return remote;
  if (!remote) return local;
  const cmp = compareUpdatedAt(local.updated_at, remote.updated_at);
  if (cmp < 0) return remote;
  if (cmp > 0) return local;
  const localDeleted = !!local.deleted_at;
  const remoteDeleted = !!remote.deleted_at;
  if (localDeleted !== remoteDeleted) return remoteDeleted ? remote : local;
  return remote;
}

/**
 * Merge two id-keyed record arrays by LWW.
 * @template {SyncMeta} T
 * @param {T[]} local
 * @param {T[]} remote
 * @returns {T[]}
 */
export function mergeRecordLists(local, remote) {
  /** @type {Map<string, T>} */
  const byId = new Map();
  for (const row of local) {
    if (!row?.id) continue;
    byId.set(row.id, row);
  }
  for (const row of remote) {
    if (!row?.id) continue;
    const winner = pickNewer(byId.get(row.id), row);
    if (winner) byId.set(row.id, winner);
  }
  return [...byId.values()].sort((a, b) => a.id.localeCompare(b.id));
}

/**
 * Merge singleton envelope objects (profile / prefs).
 * @param {{ updated_at: string, deleted_at?: string|null, payload?: object }|null|undefined} local
 * @param {{ updated_at: string, deleted_at?: string|null, payload?: object }|null|undefined} remote
 */
export function mergeSingleton(local, remote) {
  if (!local) return remote ?? null;
  if (!remote) return local;
  return pickNewer(
    /** @type {SyncMeta & {payload?: object}} */ ({ id: "_", ...local }),
    /** @type {SyncMeta & {payload?: object}} */ ({ id: "_", ...remote }),
  );
}

const LIST_KEYS = [
  "food_entries",
  "favorites",
  "weights",
  "body_fat",
  "measurements",
  "water",
  "recipes",
];

/**
 * Merge two sync documents into one. Export metadata comes from `remote`
 * when present, else `local`, with generated_at refreshed by the caller if desired.
 * @param {any} local
 * @param {any} remote
 */
export function mergeSyncDocuments(local, remote) {
  if (!local) return remote;
  if (!remote) return local;
  /** @type {Record<string, any>} */
  const out = {
    export: {
      ...(remote.export ?? local.export ?? {}),
      app: "Chompass",
      kind: "sync",
      format_version: "1.1",
    },
  };
  for (const key of LIST_KEYS) {
    out[key] = mergeRecordLists(local[key] ?? [], remote[key] ?? []);
  }
  const profile = mergeSingleton(local.profile, remote.profile);
  const prefs = mergeSingleton(local.prefs, remote.prefs);
  out.profile = profile
    ? { updated_at: profile.updated_at, deleted_at: profile.deleted_at ?? null, payload: profile.payload ?? {} }
    : null;
  out.prefs = prefs
    ? { updated_at: prefs.updated_at, deleted_at: prefs.deleted_at ?? null, payload: prefs.payload ?? {} }
    : null;
  return out;
}

/**
 * Drop tombstoned rows for applying to a live local store.
 * @template {SyncMeta} T
 * @param {T[]} rows
 * @returns {{ live: T[], deletedIds: string[] }}
 */
export function partitionLiveAndDeleted(rows) {
  /** @type {T[]} */
  const live = [];
  /** @type {string[]} */
  const deletedIds = [];
  for (const row of rows) {
    if (row.deleted_at) deletedIds.push(row.id);
    else live.push(row);
  }
  return { live, deletedIds };
}
