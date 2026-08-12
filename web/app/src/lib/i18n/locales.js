// @ts-check
/**
 * Shared UI locale metadata — keep in sync with testdata/parity/locales.json.
 * Speech recognition languages are separate (see speech.js / settings SPEECH_LANGS).
 */

/** @typedef {{ id: string, bcp47: string, name: string, nativeName: string, rtl: boolean }} LocaleInfo */

/** @type {readonly LocaleInfo[]} */
export const LOCALES = Object.freeze([
  { id: "en", bcp47: "en", name: "English", nativeName: "English", rtl: false },
  { id: "ar", bcp47: "ar", name: "Arabic", nativeName: "العربية", rtl: true },
  { id: "az", bcp47: "az", name: "Azerbaijani", nativeName: "Azərbaycan", rtl: false },
  { id: "de", bcp47: "de", name: "German", nativeName: "Deutsch", rtl: false },
  { id: "es", bcp47: "es", name: "Spanish", nativeName: "Español", rtl: false },
  { id: "fr", bcp47: "fr", name: "French", nativeName: "Français", rtl: false },
  { id: "hi", bcp47: "hi", name: "Hindi", nativeName: "हिन्दी", rtl: false },
  { id: "it", bcp47: "it", name: "Italian", nativeName: "Italiano", rtl: false },
  { id: "ja", bcp47: "ja", name: "Japanese", nativeName: "日本語", rtl: false },
  { id: "ko", bcp47: "ko", name: "Korean", nativeName: "한국어", rtl: false },
  { id: "nl", bcp47: "nl", name: "Dutch", nativeName: "Nederlands", rtl: false },
  { id: "pt-BR", bcp47: "pt-BR", name: "Portuguese (Brazil)", nativeName: "Português (Brasil)", rtl: false },
  { id: "ro", bcp47: "ro", name: "Romanian", nativeName: "Română", rtl: false },
  { id: "ru", bcp47: "ru", name: "Russian", nativeName: "Русский", rtl: false },
  { id: "uk", bcp47: "uk", name: "Ukrainian", nativeName: "Українська", rtl: false },
  { id: "zh-CN", bcp47: "zh-CN", name: "Chinese (Simplified)", nativeName: "简体中文", rtl: false },
]);

export const FALLBACK_LOCALE = "en";

/** @type {ReadonlyMap<string, LocaleInfo>} */
const BY_ID = new Map(LOCALES.map((l) => [l.id, l]));

/** @param {string} id */
export function getLocale(id) {
  return BY_ID.get(id) ?? null;
}

/**
 * Map a BCP-47 / browser language tag onto a supported UI locale id.
 * @param {string} [tag]
 * @returns {string}
 */
export function resolveLocaleId(tag) {
  if (!tag || typeof tag !== "string") return FALLBACK_LOCALE;
  const raw = tag.trim().replace(/_/g, "-");
  if (!raw) return FALLBACK_LOCALE;
  if (BY_ID.has(raw)) return raw;
  const lower = raw.toLowerCase();
  for (const loc of LOCALES) {
    if (loc.bcp47.toLowerCase() === lower || loc.id.toLowerCase() === lower) return loc.id;
  }
  // zh-Hans / zh-CN / zh → zh-CN
  if (lower === "zh" || lower.startsWith("zh-hans") || lower.startsWith("zh-cn")) return "zh-CN";
  if (lower.startsWith("pt")) return "pt-BR";
  const primary = lower.split("-")[0];
  for (const loc of LOCALES) {
    if (loc.id.toLowerCase() === primary || loc.bcp47.toLowerCase().split("-")[0] === primary) {
      return loc.id;
    }
  }
  return FALLBACK_LOCALE;
}

/**
 * @param {string} [pref] stored prefs.uiLang; empty = auto
 * @param {string} [browserTag] navigator.language
 */
export function detectLocaleId(pref = "", browserTag = "") {
  if (pref && String(pref).trim()) return resolveLocaleId(pref);
  return resolveLocaleId(browserTag || (typeof navigator !== "undefined" ? navigator.language : "") || FALLBACK_LOCALE);
}
