// @ts-check
/**
 * Meal schedule prefs — Android MealSchedule defaults (minutes from midnight).
 */

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
 * @returns {"breakfast"|"lunch"|"dinner"|"snack"}
 */
export function guessMealTypeFromPrefs(prefs, now = new Date()) {
  const mins = now.getHours() * 60 + now.getMinutes();
  const s = mealStarts(prefs);
  // Mirror Android MealSchedule.mealTypeAt: overnight / pre-breakfast is snack.
  if (mins >= s.snack || mins < s.breakfast) return "snack";
  if (mins >= s.dinner) return "dinner";
  if (mins >= s.lunch) return "lunch";
  return "breakfast";
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
 * Week strip starting Monday (default) or Sunday.
 * @param {string} selectedIso
 * @param {boolean} [weekStartsOnMonday]
 */
export function weekDates(selectedIso, weekStartsOnMonday = true) {
  const selected = new Date(`${selectedIso}T12:00:00`);
  const dow = selected.getDay(); // 0=Sun
  let offset;
  if (weekStartsOnMonday) {
    offset = dow === 0 ? -6 : 1 - dow;
  } else {
    offset = -dow;
  }
  const start = new Date(selected);
  start.setDate(selected.getDate() + offset);
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    return localIso(d);
  });
}
