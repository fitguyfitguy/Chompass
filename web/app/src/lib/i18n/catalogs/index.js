// @ts-check
// Catalog registry (Phase 1): only the English catalog is eager. The other 14
// load on demand via loadCatalog(id) — the PWA activates its locale in an
// async boot path anyway, and the demo hero never needs anything but `en`, so
// ~248 KiB of catalogs stopped shipping on every load. t()/tp() keep their
// synchronous English fallback for the brief window before a catalog lands.
import { en } from "./en.js";

/** Loaded catalogs by locale id ("en" is eager; others fill in on demand). */
export const CATALOGS = /** @type {Record<string, Readonly<Record<string, string>>>} */ ({ en });

/** Lazy per-locale loaders; keys match locales.js ids. */
const LAZY_CATALOGS = {
  de: () => import("./de.js"),
  es: () => import("./es.js"),
  fr: () => import("./fr.js"),
  ru: () => import("./ru.js"),
  nl: () => import("./nl.js"),
  it: () => import("./it.js"),
  ar: () => import("./ar.js"),
  ja: () => import("./ja.js"),
  ko: () => import("./ko.js"),
  hi: () => import("./hi.js"),
  ro: () => import("./ro.js"),
  az: () => import("./az.js"),
  uk: () => import("./uk.js"),
  "pt-BR": () => import("./pt-BR.js"),
  "zh-CN": () => import("./zh-CN.js"),
};

/**
 * Ensure a locale's catalog is loaded (idempotent, cached). English resolves
 * immediately. Returns the catalog (or null for unknown ids).
 * @param {string} id
 * @returns {Promise<Readonly<Record<string, string>> | null>}
 */
export async function loadCatalog(id) {
  if (CATALOGS[id]) return CATALOGS[id];
  const loader = LAZY_CATALOGS[id];
  if (!loader) return null;
  const mod = await loader();
  const cat = mod[id === "pt-BR" ? "pt_BR" : id === "zh-CN" ? "zh_CN" : id];
  if (!cat) return null;
  CATALOGS[id] = cat;
  return cat;
}
