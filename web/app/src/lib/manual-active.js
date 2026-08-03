// @ts-check
/**
 * Manual active burn entries (Android ManualActiveEntry) — local prefs only, not synced.
 */

import { prefs } from "./db.js";

/**
 * @typedef {Object} ManualActiveEntry
 * @property {string} id
 * @property {string} date  YYYY-MM-DD
 * @property {string} name
 * @property {number} calories
 */

/**
 * @param {string} date
 * @param {string} name
 * @param {number} calories
 * @returns {ManualActiveEntry}
 */
export function makeManualActiveEntry(date, name, calories) {
  return {
    id: crypto.randomUUID(),
    date,
    name: String(name || "").trim() || "Activity",
    calories: Math.max(0, Math.round(Number(calories) || 0)),
  };
}

/** @returns {Promise<ManualActiveEntry[]>} */
export async function loadManualActiveEntries() {
  const p = await prefs.load();
  const list = p.manualActiveEntries;
  return Array.isArray(list) ? list : [];
}

/**
 * @param {string} date
 * @returns {Promise<number>}
 */
export async function manualActiveKcalForDate(date) {
  const entries = await loadManualActiveEntries();
  return entries.filter((e) => e.date === date).reduce((s, e) => s + (Number(e.calories) || 0), 0);
}

/**
 * @param {ManualActiveEntry} entry
 */
export async function addManualActiveEntry(entry) {
  if (!entry || entry.calories <= 0) return;
  const current = await loadManualActiveEntries();
  await prefs.save({
    manualActiveEntries: [...current, { ...entry, name: entry.name.trim() || "Activity" }],
  });
}

/**
 * Resolve ADD_ACTIVE burn without Health Connect (estimate + manual).
 * Mirrors Android HomeCalorieDisplay.resolveActiveBurn for the web path.
 * @param {number} estimatedDailyActive
 * @param {number} manualKcal
 */
export function resolveWebActiveBurn(estimatedDailyActive, manualKcal) {
  const estimated = Math.max(0, Math.round(estimatedDailyActive) || 0);
  const manual = Math.max(0, Math.round(manualKcal) || 0);
  const total = estimated + manual;
  if (total <= 0) return null;
  return {
    calories: total,
    source: estimated > 0 ? "estimated" : "manual",
  };
}

/**
 * Effective calorie gauge target for ADD_ACTIVE mode.
 * @param {number} fullTarget  dailyTargets.calories
 * @param {number} sedentaryBudget
 * @param {{ calories: number }|null} burn
 */
export function addActiveGaugeTarget(fullTarget, sedentaryBudget, burn) {
  if (!burn || burn.calories <= 0) return Math.max(0, fullTarget);
  return Math.max(0, sedentaryBudget) + burn.calories;
}
