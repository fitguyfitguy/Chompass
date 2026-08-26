// @ts-check
/**
 * Meal catalog — mirrors Android MealCatalog.kt.
 */

export const MAX_MEALS = 8;
export const MIN_GAP_MINUTES = 15;
export const CUSTOM_ID_RE = /^c_[0-9a-f]{4,16}$/;

const DEFAULT_STARTS = {
  breakfast: 5 * 60,
  lunch: 11 * 60,
  dinner: 15 * 60,
  snack: 21 * 60,
};

/** @typedef {{id: string, label?: string, startMinutes?: number|null, enabled?: boolean}} MealDef */

/**
 * @param {{breakfast?: number, lunch?: number, dinner?: number, snack?: number}} [starts]
 * @returns {{meals: MealDef[], version: number}}
 */
export function defaultCatalog(starts = DEFAULT_STARTS) {
  const s = { ...DEFAULT_STARTS, ...starts };
  return {
    version: 1,
    meals: [
      { id: "breakfast", label: "", startMinutes: s.breakfast, enabled: true },
      { id: "lunch", label: "", startMinutes: s.lunch, enabled: true },
      { id: "dinner", label: "", startMinutes: s.dinner, enabled: true },
      { id: "snack", label: "", startMinutes: s.snack, enabled: true },
      { id: "other", label: "", startMinutes: null, enabled: false },
    ],
  };
}

/**
 * @param {any} raw
 * @param {{breakfast?: number, lunch?: number, dinner?: number, snack?: number}} [legacyStarts]
 */
export function parseCatalog(raw, legacyStarts) {
  if (raw && Array.isArray(raw.meals) && raw.meals.length) {
    const catalog = { version: 1, meals: raw.meals.map(normalizeDef) };
    return isValid(catalog) ? catalog : defaultCatalog(legacyStarts);
  }
  return defaultCatalog(legacyStarts);
}

/** @param {any} def */
function normalizeDef(def) {
  return {
    id: String(def?.id ?? ""),
    label: String(def?.label ?? ""),
    startMinutes: def?.startMinutes == null ? null : Number(def.startMinutes),
    enabled: def?.enabled !== false,
  };
}

/** @param {{meals: MealDef[]}} catalog */
export function isValid(catalog) {
  const meals = catalog?.meals ?? [];
  if (!meals.length || meals.length > MAX_MEALS) return false;
  const ids = new Set();
  for (const m of meals) {
    if (!m.id || ids.has(m.id)) return false;
    ids.add(m.id);
    const builtin = ["breakfast", "lunch", "dinner", "snack", "other"].includes(m.id);
    if (!builtin && !CUSTOM_ID_RE.test(m.id)) return false;
    if (m.startMinutes != null && (m.startMinutes < 0 || m.startMinutes >= 1440)) return false;
  }
  const timed = meals.filter((m) => m.enabled && m.startMinutes != null);
  if (!timed.length) return false;
  for (let i = 1; i < timed.length; i++) {
    if ((timed[i].startMinutes ?? 0) < (timed[i - 1].startMinutes ?? 0) + MIN_GAP_MINUTES) return false;
  }
  return true;
}

/**
 * @param {{meals: MealDef[]}} catalog
 * @param {Date} [now]
 */
export function mealIdAt(catalog, now = new Date()) {
  const windows = (catalog?.meals ?? []).filter((m) => m.enabled && m.startMinutes != null);
  if (!windows.length) return mealIdAt(defaultCatalog(), now);
  const minutes = now.getHours() * 60 + now.getMinutes();
  const first = windows[0].startMinutes ?? 0;
  if (minutes < first) return windows[windows.length - 1].id;
  let current = windows[0].id;
  for (const w of windows) {
    if (minutes >= (w.startMinutes ?? 0)) current = w.id;
    else break;
  }
  return current;
}

/** @param {{meals: MealDef[]}} catalog */
export function enabledIds(catalog) {
  return (catalog?.meals ?? []).filter((m) => m.enabled).map((m) => m.id);
}

/** @param {{meals: MealDef[]}} catalog */
export function exportMealCatalog(catalog) {
  return (catalog?.meals ?? []).map((m) => ({ id: m.id, label: m.label ?? "" }));
}
