// @ts-check
/**
 * Per-day-type typical active burn — mirror of Android DayTypeActiveStats.
 * Journal-first grouping; derived only (no MacroDayProfile / sync field).
 */

import { isoToEpochDay } from "./macro-plan.js";

export const DAY_TYPE_ACTIVE_WINDOW_DAYS = 28;
export const DAY_TYPE_ACTIVE_MIN_SAMPLES = 3;
export const DAY_TYPE_ACTIVE_HISTORY_KEEP_DAYS = 60;

/**
 * @typedef {{ profileId: string, averageKcal: number, sampleCount: number }} TypeAverage
 * @typedef {{ date: string, profileId: string, profileName: string|null, activeKcal: number }} DailyTagged
 * @typedef {{ byProfileId: Record<string, TypeAverage>, daily: DailyTagged[] }} DayTypeActiveResult
 * @typedef {{ kcal: number, typicalIsDayType: boolean }} TypicalResolution
 */

/**
 * @param {string} iso
 * @param {string} todayIso
 * @param {number} windowDays
 */
function inWindow(iso, todayIso, windowDays) {
  const day = isoToEpochDay(iso);
  const today = isoToEpochDay(todayIso);
  if (Number.isNaN(day) || Number.isNaN(today)) return false;
  const start = today - (windowDays - 1);
  return day >= start && day <= today;
}

/**
 * @param {Array<{date: string, profileId?: string|null, profileName?: string|null}>} journal
 * @param {Record<string, number>} activeByDay
 * @param {string} todayIso
 * @param {number} [windowDays]
 * @returns {DayTypeActiveResult}
 */
export function computeDayTypeActiveStats(journal, activeByDay, todayIso, windowDays = DAY_TYPE_ACTIVE_WINDOW_DAYS) {
  const journalByDate = Object.create(null);
  for (const e of journal || []) {
    if (e && e.date) journalByDate[e.date] = e;
  }
  /** @type {DailyTagged[]} */
  const daily = [];
  for (const [iso, raw] of Object.entries(activeByDay || {})) {
    const active = Math.round(Number(raw) || 0);
    if (active <= 0 || !inWindow(iso, todayIso, windowDays)) continue;
    const entry = journalByDate[iso];
    const profileId = entry?.profileId;
    if (!profileId) continue;
    daily.push({
      date: iso,
      profileId,
      profileName: entry.profileName ?? null,
      activeKcal: active,
    });
  }
  daily.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  /** @type {Record<string, TypeAverage>} */
  const byProfileId = Object.create(null);
  const groups = Object.create(null);
  for (const row of daily) {
    (groups[row.profileId] ||= []).push(row.activeKcal);
  }
  for (const [id, vals] of Object.entries(groups)) {
    const avg = Math.round(vals.reduce((s, n) => s + n, 0) / vals.length);
    byProfileId[id] = { profileId: id, averageKcal: avg, sampleCount: vals.length };
  }
  return { byProfileId, daily };
}

/**
 * @param {DayTypeActiveResult} stats
 * @param {string|null|undefined} profileId
 * @param {number} [minSamples]
 * @returns {number|null}
 */
export function typicalForProfile(stats, profileId, minSamples = DAY_TYPE_ACTIVE_MIN_SAMPLES) {
  if (!profileId || !stats) return null;
  const row = stats.byProfileId[profileId];
  if (!row || row.sampleCount < minSamples) return null;
  return row.averageKcal;
}

/**
 * @param {string|null|undefined} viewedProfileId
 * @param {DayTypeActiveResult} stats
 * @param {number} blendedMeasured
 * @param {number} palEstimate
 * @param {number} [minSamples]
 * @returns {TypicalResolution}
 */
export function resolveActiveTypical(viewedProfileId, stats, blendedMeasured, palEstimate, minSamples = DAY_TYPE_ACTIVE_MIN_SAMPLES) {
  const perType = typicalForProfile(stats, viewedProfileId, minSamples);
  if (perType != null && perType > 0) return { kcal: perType, typicalIsDayType: true };
  if (blendedMeasured > 0) return { kcal: blendedMeasured, typicalIsDayType: false };
  return { kcal: Math.max(0, Math.round(palEstimate || 0)), typicalIsDayType: false };
}

/**
 * @param {Record<string, number>} healthConnectByDay
 * @param {Record<string, number>} manualByDay
 * @returns {Record<string, number>}
 */
export function mergeDayTotals(healthConnectByDay, manualByDay) {
  /** @type {Record<string, number>} */
  const out = Object.create(null);
  for (const src of [healthConnectByDay || {}, manualByDay || {}]) {
    for (const [k, v] of Object.entries(src)) {
      const n = Math.round(Number(v) || 0);
      if (n <= 0) continue;
      out[k] = (out[k] || 0) + n;
    }
  }
  return out;
}

/**
 * @param {Array<{date: string, calories: number}>} entries
 * @returns {Record<string, number>}
 */
export function sumManualByDay(entries) {
  /** @type {Record<string, number>} */
  const out = Object.create(null);
  for (const e of entries || []) {
    const n = Math.round(Number(e.calories) || 0);
    if (!e.date || n <= 0) continue;
    out[e.date] = (out[e.date] || 0) + n;
  }
  return out;
}

/**
 * @param {Record<string, number>} map
 * @param {string} todayIso
 * @param {number} [keepDays]
 * @returns {Record<string, number>}
 */
export function pruneActiveHistory(map, todayIso, keepDays = DAY_TYPE_ACTIVE_HISTORY_KEEP_DAYS) {
  const today = isoToEpochDay(todayIso);
  if (Number.isNaN(today)) return {};
  const cutoff = today - (keepDays - 1);
  /** @type {Record<string, number>} */
  const out = Object.create(null);
  for (const [iso, raw] of Object.entries(map || {})) {
    const n = Math.round(Number(raw) || 0);
    const day = isoToEpochDay(iso);
    if (n > 0 && !Number.isNaN(day) && day >= cutoff) out[iso] = n;
  }
  return out;
}
