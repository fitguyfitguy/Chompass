// @ts-check
/**
 * Lightweight PWA i18n: catalog lookup, locale preference, Intl formatters.
 * English is the complete fallback for core surfaces.
 */
import { CATALOGS, loadCatalog } from "./catalogs/index.js";
import { FALLBACK_LOCALE, LOCALES, detectLocaleId, getLocale, resolveLocaleId } from "./locales.js";

export { LOCALES, FALLBACK_LOCALE, detectLocaleId, getLocale, resolveLocaleId };

/** @type {string} */
let activeLocaleId = FALLBACK_LOCALE;

/** @returns {string} */
export function getActiveLocale() {
  return activeLocaleId;
}

/**
 * @param {string} localeId
 * @returns {string} resolved id
 */
export function setActiveLocale(localeId) {
  activeLocaleId = resolveLocaleId(localeId);
  applyDocumentLocale(activeLocaleId);
  return activeLocaleId;
}

/**
 * Apply lang/dir to <html> and optional meta description.
 * @param {string} [localeId]
 */
export function applyDocumentLocale(localeId = activeLocaleId) {
  if (typeof document === "undefined") return;
  const info = getLocale(resolveLocaleId(localeId)) || getLocale(FALLBACK_LOCALE);
  if (!info) return;
  const root = document.documentElement;
  root.lang = info.bcp47;
  root.dir = info.rtl ? "rtl" : "ltr";
  const title = t("app.title");
  if (title) document.title = title;
  const meta = document.querySelector('meta[name="description"]');
  if (meta) meta.setAttribute("content", t("app.description"));
}

/**
 * Translate a key with optional `{name}` interpolation.
 * Missing keys fall back to English, then the key itself.
 * @param {string} key
 * @param {Record<string, string | number>} [vars]
 * @param {string} [localeId]
 */
export function t(key, vars, localeId = activeLocaleId) {
  const id = resolveLocaleId(localeId);
  const catalog = CATALOGS[id] || CATALOGS[FALLBACK_LOCALE];
  let text = catalog?.[key] ?? CATALOGS[FALLBACK_LOCALE]?.[key] ?? key;
  if (vars) {
    for (const [name, value] of Object.entries(vars)) {
      text = text.replaceAll(`{${name}}`, String(value));
    }
  }
  return text;
}

/**
 * Simple plural helper: uses `key` for one, `key_plural` otherwise when present.
 * @param {string} key
 * @param {number} count
 * @param {Record<string, string | number>} [vars]
 */
export function tp(key, count, vars) {
  const pluralKey = `${key}_plural`;
  const usePlural = count !== 1 && (CATALOGS[activeLocaleId]?.[pluralKey] || CATALOGS[FALLBACK_LOCALE]?.[pluralKey]);
  return t(usePlural ? pluralKey : key, { count, ...(vars || {}) });
}

/** @returns {string} BCP-47 tag for Intl */
export function intlLocale() {
  return getLocale(activeLocaleId)?.bcp47 || FALLBACK_LOCALE;
}

/**
 * @param {Date | number} date
 * @param {Intl.DateTimeFormatOptions} [options]
 */
export function formatDate(date, options = { dateStyle: "medium" }) {
  try {
    return new Intl.DateTimeFormat(intlLocale(), options).format(date);
  } catch {
    return new Intl.DateTimeFormat(FALLBACK_LOCALE, options).format(date);
  }
}

/**
 * @param {number} value
 * @param {Intl.NumberFormatOptions} [options]
 */
export function formatNumber(value, options = { maximumFractionDigits: 1 }) {
  try {
    return new Intl.NumberFormat(intlLocale(), options).format(value);
  } catch {
    return new Intl.NumberFormat(FALLBACK_LOCALE, options).format(value);
  }
}

/**
 * Resolve locale from prefs + browser, load its catalog, and activate it.
 * Awaiting the catalog load is what lets the rest of the app render in the
 * chosen language on the first frame (no English flash).
 * @param {{ uiLang?: string }} prefs
 * @param {string} [browserTag]
 */
export async function activateFromPrefs(prefs, browserTag) {
  const id = detectLocaleId(prefs?.uiLang || "", browserTag);
  await loadCatalog(id);
  return setActiveLocale(id);
}

/** English key set (for completeness tests). */
export function englishKeys() {
  return Object.keys(CATALOGS.en || {});
}

/**
 * Diff a locale catalog against English (used by completeness tests). Loads
 * the catalog on demand.
 * @param {string} localeId
 * @returns {Promise<{ missing: string[], extra: string[] }>}
 */
export async function catalogDiff(localeId) {
  const id = resolveLocaleId(localeId);
  await loadCatalog(id);
  const base = new Set(englishKeys());
  const cat = CATALOGS[id] || {};
  const keys = new Set(Object.keys(cat));
  const missing = [...base].filter((k) => !keys.has(k));
  const extra = [...keys].filter((k) => !base.has(k));
  return { missing, extra };
}
