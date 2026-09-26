// @ts-check
/**
 * Meal schedule prefs — Android MealSchedule defaults (minutes from midnight).
 */
import { mealIdAt, parseCatalog } from "./chompass-core/meal-catalog.js";

const DEFAULTS = {
  breakfast: 5 * 60,
  lunch: 11 * 60,
  dinner: 15 * 60,
  snack: 21 * 60,
};

/**
 * @param {import('./db.js').AppPrefs | null | undefined} prefs
 * @returns {{breakfast: number, lunch: number, dinner: number, snack: number}}
 */
export function mealStarts(prefs) {
  return {
    breakfast: prefs?.mealBreakfastStart ?? DEFAULTS.breakfast,
    lunch: prefs?.mealLunchStart ?? DEFAULTS.lunch,
    dinner: prefs?.mealDinnerStart ?? DEFAULTS.dinner,
    snack: prefs?.mealSnackStart ?? DEFAULTS.snack,
  };
}

/** @param {number} minutes */
export function minutesToTimeInput(minutes) {
  const m = Math.max(0, Math.min(23 * 60 + 59, Math.round(minutes)));
  const h = Math.floor(m / 60);
  const min = m % 60;
  return `${String(h).padStart(2, "0")}:${String(min).padStart(2, "0")}`;
}

/** @param {string} hhmm */
export function timeInputToMinutes(hhmm) {
  const [h, m] = String(hhmm || "00:00")
    .split(":")
    .map(Number);
  return (h || 0) * 60 + (m || 0);
}

/**
 * @param {import('./db.js').AppPrefs | null | undefined} prefs
 * @param {Date} [now]
 * @returns {string}
 */
export function guessMealTypeFromPrefs(prefs, now = new Date()) {
  const catalog = parseCatalog(prefs?.mealCatalog, mealStarts(prefs));
  return mealIdAt(catalog, now);
}

/**
 * Local calendar YYYY-MM-DD (avoid UTC shift from toISOString).
 * @param {Date} d
 */
function localIso(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/**
 * @param {boolean|string|undefined} weekStart monday/sunday/saturday, or legacy boolean (true=Mon).
 * @returns {0|1|6} JS getDay() of the first weekday
 */
export function resolveWeekStartDow(weekStart) {
  if (weekStart === false || weekStart === "sunday") return 0;
  if (weekStart === "saturday") return 6;
  return 1;
}

/**
 * Week strip starting Monday (default), Sunday, or Saturday.
 * @param {string} selectedIso
 * @param {boolean|string} [weekStart] true/"monday", false/"sunday", or "saturday"
 */
export function weekDates(selectedIso, weekStart = true) {
  const selected = new Date(`${selectedIso}T12:00:00`);
  const dow = selected.getDay(); // 0=Sun
  const startDow = resolveWeekStartDow(weekStart);
  const offset = -((dow - startDow + 7) % 7);
  const start = new Date(selected);
  start.setDate(selected.getDate() + offset);
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    return localIso(d);
  });
}
