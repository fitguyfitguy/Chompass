// @ts-check
/**
 * Build / apply sync-1.0 documents against IndexedDB, and optional WebDAV sync.
 */
import {
  foodEntries,
  favorites,
  recipes,
  weights,
  bodyFat,
  measurements,
  water,
  prefs,
  withRevisionHooksSuppressed,
} from "./db.js";
import {
  exportSyncDocument,
  parseSyncDocument,
  appendTombstones,
  liveFoodEntriesFromSync,
  liveWeightsFromSync,
  liveBodyFatFromSync,
  liveMeasurementsFromSync,
  liveWaterFromSync,
  liveRecipesFromSync,
} from "./chompass-core/sync-format.js";
import { mergeSyncDocuments, partitionLiveAndDeleted } from "./chompass-core/sync-merge.js";

/** @returns {Promise<Record<string, { updatedAt: string, deletedAt?: string|null, kind?: string }>>} */
async function loadRevisions() {
  const p = await prefs.load();
  return p.syncRevisions ?? {};
}

/** @param {Record<string, { updatedAt: string, deletedAt?: string|null, kind?: string }>} revisions */
async function saveRevisions(revisions) {
  await prefs.save({ syncRevisions: revisions });
}

export async function touchRevision(id, kind = "food") {
  const revisions = await loadRevisions();
  const now = new Date().toISOString();
  revisions[id] = { updatedAt: now, deletedAt: null, kind };
  await saveRevisions(revisions);
}

export async function tombstoneRevision(id, kind = "food") {
  const revisions = await loadRevisions();
  const now = new Date().toISOString();
  revisions[id] = { updatedAt: now, deletedAt: now, kind };
  await saveRevisions(revisions);
}

export async function buildLocalSyncDocument() {
  const revisions = await loadRevisions();
  const doc = exportSyncDocument({
    foodEntries: await foodEntries.all(),
    favorites: await favorites.all(),
    weights: await weights.all(),
    bodyFat: await bodyFat.all(),
    measurements: await measurements.all(),
    water: await water.all(),
    recipes: await recipes.all(),
    revisions,
    generatedAt: new Date().toISOString(),
  });
  appendTombstones(doc, revisions);
  return doc;
}

/**
 * @param {any} incoming
 */
export async function importAndMergeSyncDocument(incoming) {
  const remote = parseSyncDocument(incoming);
  const local = await buildLocalSyncDocument();
  const merged = mergeSyncDocuments(local, remote);
  await applySyncDocument(merged);
  return merged;
}

/**
 * @param {any} doc
 */
export async function applySyncDocument(doc) {
  parseSyncDocument(doc);
  await withRevisionHooksSuppressed(async () => {
  /** @type {Record<string, { updatedAt: string, deletedAt?: string|null, kind?: string }>} */
  const revisions = {};

  const foodPart = partitionLiveAndDeleted(doc.food_entries ?? []);
  for (const row of doc.food_entries ?? []) {
    revisions[row.id] = { updatedAt: row.updated_at, deletedAt: row.deleted_at ?? null, kind: "food" };
  }
  for (const id of foodPart.deletedIds) await foodEntries.delete(id);
  for (const entry of liveFoodEntriesFromSync(foodPart.live)) await foodEntries.put(entry);

  const favPart = partitionLiveAndDeleted(doc.favorites ?? []);
  for (const row of doc.favorites ?? []) {
    revisions[row.id] = { updatedAt: row.updated_at, deletedAt: row.deleted_at ?? null, kind: "favorite" };
  }
  for (const id of favPart.deletedIds) await favorites.delete(id);
  for (const entry of liveFoodEntriesFromSync(favPart.live)) await favorites.put(entry);

  const weightPart = partitionLiveAndDeleted(doc.weights ?? []);
  for (const row of doc.weights ?? []) {
    revisions[row.id] = { updatedAt: row.updated_at, deletedAt: row.deleted_at ?? null, kind: "weight" };
  }
  for (const id of weightPart.deletedIds) await weights.delete(id);
  for (const entry of liveWeightsFromSync(weightPart.live)) await weights.put(entry);

  const bfPart = partitionLiveAndDeleted(doc.body_fat ?? []);
  for (const row of doc.body_fat ?? []) {
    revisions[row.id] = { updatedAt: row.updated_at, deletedAt: row.deleted_at ?? null, kind: "bodyfat" };
  }
  for (const id of bfPart.deletedIds) await bodyFat.delete(id);
  for (const entry of liveBodyFatFromSync(bfPart.live)) await bodyFat.put(entry);

  const mPart = partitionLiveAndDeleted(doc.measurements ?? []);
  for (const row of doc.measurements ?? []) {
    revisions[row.id] = { updatedAt: row.updated_at, deletedAt: row.deleted_at ?? null, kind: "measure" };
  }
  for (const id of mPart.deletedIds) await measurements.delete(id);
  for (const entry of liveMeasurementsFromSync(mPart.live)) await measurements.put(entry);

  const wPart = partitionLiveAndDeleted(doc.water ?? []);
  for (const row of doc.water ?? []) {
    revisions[row.id] = { updatedAt: row.updated_at, deletedAt: row.deleted_at ?? null, kind: "water" };
  }
  for (const id of wPart.deletedIds) await water.delete(id);
  for (const entry of liveWaterFromSync(wPart.live)) await water.put(entry);

  const rPart = partitionLiveAndDeleted(doc.recipes ?? []);
  for (const row of doc.recipes ?? []) {
    revisions[row.id] = { updatedAt: row.updated_at, deletedAt: row.deleted_at ?? null, kind: "recipe" };
  }
  for (const id of rPart.deletedIds) await recipes.delete(id);
  for (const entry of liveRecipesFromSync(rPart.live)) await recipes.put(entry);

  await saveRevisions(revisions);
  });
}

/**
 * Normalize a user-entered WebDAV file URL.
 * Missing scheme → https; collapses stacked schemes (https://https://…).
 * @param {string} raw
 * @returns {string}
 */
export function normalizeWebDavUrl(raw) {
  let rest = (raw ?? "").trim();
  if (!rest) return rest;

  let preferHttp = false;
  let sawScheme = false;
  while (true) {
    const lower = rest.toLowerCase();
    if (lower.startsWith("https://")) {
      rest = rest.slice(8);
      preferHttp = false;
      sawScheme = true;
    } else if (lower.startsWith("http://")) {
      rest = rest.slice(7);
      if (!sawScheme) preferHttp = true;
      sawScheme = true;
    } else {
      break;
    }
  }
  rest = rest.replace(/^\/+/, "");
  if (!rest) return (raw ?? "").trim();
  return `${preferHttp ? "http" : "https"}://${rest}`;
}

/**
 * @returns {Promise<{ url: string, username: string, password: string, etag: string|null, lastSyncAt: string|null }>}
 */
export async function loadWebDavSettings() {
  const p = await prefs.load();
  const cfg = p.webdav ?? {};
  return {
    url: cfg.url ?? "",
    username: cfg.username ?? "",
    password: cfg.password ?? "",
    etag: cfg.etag ?? null,
    lastSyncAt: cfg.lastSyncAt ?? null,
  };
}

/** @param {{ url: string, username: string, password: string, etag?: string|null, lastSyncAt?: string|null }} cfg */
export async function saveWebDavSettings(cfg) {
  await prefs.save({
    webdav: {
      url: normalizeWebDavUrl(cfg.url),
      username: cfg.username.trim(),
      password: cfg.password,
      etag: cfg.etag ?? null,
      lastSyncAt: cfg.lastSyncAt ?? null,
    },
  });
}

/**
 * Basic Authorization header using UTF-8 (curl / RFC 7617).
 * Plain `btoa(user:pass)` is Latin-1 and 401s on hosts like Hetzner Storage Box
 * when the password contains characters such as ß or §.
 * @param {string} username
 * @param {string} password
 * @returns {string}
 */
export function webDavBasicAuthHeader(username, password) {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return "Basic " + btoa(binary);
}

/**
 * Pull-merge-push against the configured WebDAV URL.
 * @returns {Promise<{ ok: boolean, message: string }>}
 */
export async function syncWebDavNow() {
  const cfg = await loadWebDavSettings();
  cfg.url = normalizeWebDavUrl(cfg.url);
  if (!cfg.url || !cfg.username || !cfg.password) {
    return { ok: false, message: "Configure WebDAV URL, username, and password first" };
  }
  const auth = webDavBasicAuthHeader(cfg.username, cfg.password);
  let remoteText = null;
  let etag = cfg.etag;
  const getRes = await fetch(cfg.url, { headers: { Authorization: auth } });
  if (getRes.status === 404) {
    remoteText = null;
    etag = null;
  } else if (!getRes.ok) {
    return { ok: false, message: `WebDAV GET failed: HTTP ${getRes.status}` };
  } else {
    remoteText = await getRes.text();
    etag = getRes.headers.get("ETag") ?? etag;
  }

  const local = await buildLocalSyncDocument();
  let merged = local;
  if (remoteText) {
    try {
      merged = mergeSyncDocuments(local, parseSyncDocument(JSON.parse(remoteText)));
    } catch (err) {
      return { ok: false, message: err instanceof Error ? err.message : "Invalid remote sync document" };
    }
  }
  await applySyncDocument(merged);
  const body = JSON.stringify(merged, null, 2);

  /** @type {Record<string, string>} */
  const putHeaders = {
    Authorization: auth,
    "Content-Type": "application/json; charset=utf-8",
  };
  if (etag) putHeaders["If-Match"] = etag;
  else putHeaders["If-None-Match"] = "*";

  let putRes = await fetch(cfg.url, { method: "PUT", headers: putHeaders, body });
  if (putRes.status === 412) {
    const again = await fetch(cfg.url, { headers: { Authorization: auth } });
    if (!again.ok) return { ok: false, message: `Conflict re-fetch failed: HTTP ${again.status}` };
    const againText = await again.text();
    const againEtag = again.headers.get("ETag");
    try {
      merged = mergeSyncDocuments(await buildLocalSyncDocument(), parseSyncDocument(JSON.parse(againText)));
    } catch (err) {
      return { ok: false, message: err instanceof Error ? err.message : "Invalid remote after conflict" };
    }
    await applySyncDocument(merged);
    const retryHeaders = {
      Authorization: auth,
      "Content-Type": "application/json; charset=utf-8",
    };
    if (againEtag) retryHeaders["If-Match"] = againEtag;
    putRes = await fetch(cfg.url, {
      method: "PUT",
      headers: retryHeaders,
      body: JSON.stringify(merged, null, 2),
    });
    if (putRes.status === 412) return { ok: false, message: "WebDAV conflict persisted; try again" };
    etag = putRes.headers.get("ETag") ?? againEtag;
  } else if (!putRes.ok) {
    return { ok: false, message: `WebDAV PUT failed: HTTP ${putRes.status}` };
  } else {
    etag = putRes.headers.get("ETag") ?? etag;
  }

  const lastSyncAt = new Date().toISOString();
  await saveWebDavSettings({ ...cfg, etag, lastSyncAt });
  return { ok: true, message: "Synced with WebDAV" };
}
