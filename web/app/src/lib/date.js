// @ts-check
/**
 * Local-time date helpers shared across views.
 *
 * Diary day keys are local ISO dates (YYYY-MM-DD), never UTC: a UTC
 * `toISOString().slice(0, 10)` rolls to the next/previous day near midnight
 * and lands entries on the wrong day. The per-view copies of these helpers
 * drifted into UTC variants for exactly that reason; use these instead.
 */

/**
 * Format a Date as a local YYYY-MM-DD string.
 * @param {Date} d
 * @returns {string}
 */
export function localIsoDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/** @returns {string} today's local ISO date */
export function todayIso() {
  return localIsoDate(new Date());
}

/**
 * Shift an ISO date by N days (local calendar arithmetic, DST-safe).
 * @param {string} iso
 * @param {number} days
 * @returns {string}
 */
export function shiftDate(iso, days) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return localIsoDate(d);
}

/** Home / week-strip forward window. Mirrors Android DIARY_FUTURE_WEEKS. */
export const DIARY_FUTURE_WEEKS = 8;

/**
 * Keep past dates; cap future at today + 8 weeks. Invalid ISO falls back to today.
 * @param {string} iso
 * @param {string} [today]
 * @returns {string}
 */
export function clampDiaryDate(iso, today = todayIso()) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(iso)) return today;
  const max = shiftDate(today, DIARY_FUTURE_WEEKS * 7);
  return iso > max ? max : iso;
}
