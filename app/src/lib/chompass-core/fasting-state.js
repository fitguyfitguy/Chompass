// @ts-check
/**
 * PWA mirror of Android's fasting-cycle state derivation
 * (HomeViewModel.refreshFastingTick + FastingSession + FastingViews).
 * Pure function of the persisted fasting prefs — the domain-mirror home for
 * the logic that today lived inside the diary-view HTML builder.
 *
 * Android truth (refreshFastingTick):
 *  - auto cycle only acts once a goal length is set (goal > 0); without one
 *    it falls back to the relative eating-window anchor and shows Start.
 *  - a fresh auto user (no history, lastEndedAt = null) is IDLE — "Next fast
 *    at h:mm", no bar — never a 100 % eating bar with a null anchor.
 *  - eatElapsed is never computed from a null anchor.
 */

export const MILLIS_PER_HOUR = 3_600_000;

export const FastingPhase = Object.freeze({
  IDLE: "IDLE",
  FASTING: "FASTING",
  EATING: "EATING",
});

/**
 * @typedef {{
 *   fastingStartedAt?: number|null,
 *   fastingLastEndedAt?: number|null,
 *   fastingAutoStarted?: boolean,
 *   fastingGoalHours?: number,
 *   fastingEatHours?: number,
 *   fastingAutoWindows?: boolean,
 *   fastingStartHour?: number,
 *   fastingStartMinute?: number,
 * }} FastingPrefs
 */

/**
 * Next occurrence of the daily clock time [hour]:[minute] at or after
 * [nowMillis] (mirror of Android nextFastingStartMillis, including the
 * hour/minute clamping).
 * @param {number} hour
 * @param {number} minute
 * @param {number} nowMillis
 */
export function nextFastStartMillis(hour, minute, nowMillis) {
  const h = clampInt(hour, 0, 23);
  const m = clampInt(minute, 0, 59);
  const today = new Date(nowMillis);
  today.setHours(h, m, 0, 0);
  const todayMs = today.getTime();
  return nowMillis <= todayMs ? todayMs : todayMs + 24 * 60 * 60_000;
}

/** @param {number} v */
function clampInt(v, lo, hi) {
  const n = Number.isFinite(v) ? Math.trunc(v) : 0;
  return Math.max(lo, Math.min(hi, n));
}

/**
 * Derive the fasting cycle display state from the persisted prefs.
 * Mirrors Android exactly:
 *  - phase: FASTING while a fast runs; EATING when the next start is defined
 *    (auto: any stop opens the eating phase until the next clock-time start;
 *    manual: the eating window is still open); else IDLE.
 *  - anchor: the next fast-start instant — clock time in auto mode (with a
 *    goal), else the eating-window end (lastEnded + eatHours). Null while
 *    fasting, in manual idle, or when nothing is scheduled.
 *  - eatElapsed: 0 unless EATING with a real lastEnded anchor.
 *  - clock: labels render "Fast starts at h:mm" / "Next fast at h:mm" only in
 *    an effective auto cycle (goal > 0); manual mode stays relative.
 *  - buttons: Stop only on a manually started fast; Start only in manual
 *    mode (an auto cycle is self-driving).
 * @param {FastingPrefs} p
 * @param {number} nowMillis
 */
export function computeFastingState(p, nowMillis) {
  const goal = p.fastingGoalHours ?? 0;
  const eat = p.fastingEatHours ?? 0;
  const auto = p.fastingAutoWindows === true;
  // The auto cycle only acts with a goal length: without one an auto-started
  // fast could never end (Android FastingAutoPlanner.heal guards goal > 0 too).
  const autoEffective = auto && goal > 0;
  const fasting = p.fastingStartedAt != null;
  const lastEnded = p.fastingLastEndedAt ?? null;
  const windowEnds =
    !fasting && eat > 0 && lastEnded != null && lastEnded + eat * MILLIS_PER_HOUR > nowMillis
      ? lastEnded + eat * MILLIS_PER_HOUR
      : null;
  const anchor = fasting
    ? null
    : autoEffective
      ? nextFastStartMillis(p.fastingStartHour ?? 20, p.fastingStartMinute ?? 0, nowMillis)
      : windowEnds;
  const phase = fasting
    ? FastingPhase.FASTING
    : (autoEffective ? lastEnded != null : windowEnds != null)
      ? FastingPhase.EATING
      : FastingPhase.IDLE;
  const elapsed =
    fasting && p.fastingStartedAt != null ? Math.max(0, nowMillis - p.fastingStartedAt) : 0;
  const eatElapsed =
    phase === FastingPhase.EATING && lastEnded != null ? Math.max(0, nowMillis - lastEnded) : 0;
  const goalMillis = goal * MILLIS_PER_HOUR;
  const reached = fasting && goal > 0 && elapsed >= goalMillis;
  return {
    phase,
    goal,
    eat,
    auto,
    autoEffective,
    /** @type {number|null} */ anchor,
    elapsed,
    eatElapsed,
    goalMillis,
    reached,
    autoStarted: p.fastingAutoStarted === true,
    lastEnded,
    // Android button rule: Stop only for a manually started fast (an auto
    // cycle is self-driving); Start only in manual mode.
    showStop: phase === FastingPhase.FASTING && p.fastingAutoStarted !== true,
    showStart: phase !== FastingPhase.FASTING && !autoEffective,
  };
}
