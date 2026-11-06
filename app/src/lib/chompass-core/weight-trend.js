// @ts-check
/**
 * Display-only 7-day trailing moving average for Progress weight charts.
 * Does not feed Adaptive Goals / forecast math.
 *
 * Semantics (shared with Android `WeightTrend.kt`):
 * 1. Bucket weigh-ins by calendar day (ISO `yyyy-MM-dd`); average same-day values.
 * 2. For each day that has a weigh-in, average all daily values in the trailing
 *    [windowDays] calendar-day window that also have data.
 * 3. Emit a trend point only when that window contains at least [minDaysInWindow]
 *    distinct weigh-in days.
 */

export const WEIGHT_TREND_WINDOW_DAYS = 7;
export const WEIGHT_TREND_MIN_DAYS = 2;

/**
 * @typedef {{ date: string, weightKg: number }} WeightTrendInput
 * @typedef {{ date: string, valueKg: number }} WeightTrendPoint
 */

/**
 * Resolve Progress range: last viewed chip → Settings default → factory 1W.
 * @param {string | null | undefined} lastViewedId
 * @param {string | null | undefined} defaultId
 * @param {string[]} [validIds]
 * @returns {string}
 */
export function resolveProgressRangeId(lastViewedId, defaultId, validIds = ["1W", "1M", "3M", "6M", "1Y", "All"]) {
  if (lastViewedId && validIds.includes(lastViewedId)) return lastViewedId;
  if (defaultId && validIds.includes(defaultId)) return defaultId;
  return "1W";
}

/**
 * @param {string} isoDate yyyy-MM-dd
 * @returns {number} epoch day (UTC)
 */
function epochDay(isoDate) {
  const [y, m, d] = isoDate.split("-").map(Number);
  return Math.floor(Date.UTC(y, m - 1, d) / 86_400_000);
}

/**
 * @param {number} day
 * @returns {string}
 */
function isoFromEpochDay(day) {
  return new Date(day * 86_400_000).toISOString().slice(0, 10);
}

/**
 * Average same-calendar-day weigh-ins (ISO date prefix of input.date).
 * @param {WeightTrendInput[]} weighIns
 * @returns {{ date: string, valueKg: number }[]}
 */
export function averageWeightByDay(weighIns) {
  /** @type {Map<string, { sum: number, n: number }>} */
  const byDay = new Map();
  for (const w of weighIns) {
    const day = String(w.date).slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(day) || !(w.weightKg > 0)) continue;
    const acc = byDay.get(day) ?? { sum: 0, n: 0 };
    acc.sum += w.weightKg;
    acc.n += 1;
    byDay.set(day, acc);
  }
  return [...byDay.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, { sum, n }]) => ({ date, valueKg: sum / n }));
}

/**
 * Trailing calendar-day moving average of daily weight averages.
 * @param {WeightTrendInput[]} weighIns chronological or unsorted
 * @param {{ windowDays?: number, minDaysInWindow?: number }} [opts]
 * @returns {WeightTrendPoint[]}
 */
export function computeWeightTrend(weighIns, opts = {}) {
  const windowDays = opts.windowDays ?? WEIGHT_TREND_WINDOW_DAYS;
  const minDaysInWindow = opts.minDaysInWindow ?? WEIGHT_TREND_MIN_DAYS;
  const daily = averageWeightByDay(weighIns);
  if (daily.length === 0) return [];

  /** @type {Map<number, number>} */
  const valueByEpoch = new Map();
  for (const d of daily) valueByEpoch.set(epochDay(d.date), d.valueKg);

  /** @type {WeightTrendPoint[]} */
  const out = [];
  for (const d of daily) {
    const end = epochDay(d.date);
    const start = end - (windowDays - 1);
    let sum = 0;
    let count = 0;
    for (let day = start; day <= end; day++) {
      const v = valueByEpoch.get(day);
      if (v == null) continue;
      sum += v;
      count += 1;
    }
    if (count >= minDaysInWindow) {
      out.push({ date: d.date, valueKg: sum / count });
    }
  }
  return out;
}

/**
 * Split trend points into contiguous segments when calendar gaps exceed [maxGapDays].
 * @param {WeightTrendPoint[]} points
 * @param {number} [maxGapDays]
 * @returns {WeightTrendPoint[][]}
 */
export function splitTrendSegments(points, maxGapDays = WEIGHT_TREND_WINDOW_DAYS) {
  if (points.length === 0) return [];
  /** @type {WeightTrendPoint[][]} */
  const segments = [];
  /** @type {WeightTrendPoint[]} */
  let current = [points[0]];
  for (let i = 1; i < points.length; i++) {
    const gap = epochDay(points[i].date) - epochDay(points[i - 1].date);
    if (gap > maxGapDays) {
      segments.push(current);
      current = [points[i]];
    } else {
      current.push(points[i]);
    }
  }
  segments.push(current);
  return segments;
}

// Keep helper available for tests that want epoch↔iso round-trips.
export const _test = { epochDay, isoFromEpochDay };
