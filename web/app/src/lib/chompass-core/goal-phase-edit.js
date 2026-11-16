// @ts-check

/**
 * Pure goal-phase write path (Progress v2) — JS mirror of Android
 * `app.chompass.models.GoalPhaseEdit` plus the per-phase adherence rollup
 * (`ui/progress/ProgressAdherence` byPhase, shared with the Settings editor).
 *
 * Semantics (see docs/PARITY.md "Goal phases"):
 *  - a phase = intent (CUT/MAINTAIN/BULK) + explicit targets + an inclusive
 *    ISO date range, clipped to the ±366-day planning window;
 *  - applying maps the phase onto one `MacroDayProfile` and stamps
 *    `MacroPlan.dayAssignments` across its (clipped) range — overlap
 *    ownership follows `createdAtMillis` (later write wins);
 *  - removal is profile-scoped: manual day switches and other phases survive;
 *  - `restampAssignments` rolls the stamps with the keep window so far-future
 *    phases self-activate as their range arrives (idempotent).
 *
 * Storage rides the shared `UserProfile.goalPhases` payload — the sync
 * envelope stays opaque, no wire version bump. All functions are pure;
 * persistence + UI live in components/lib.
 */

import { clampAutoCalories, dailyTargets } from "./formulas.js";
import {
  ASSIGNMENT_KEEP_DAYS,
  MAX_PROFILES,
  normalizedPlan,
  profileById,
  rebalanceProfileTo,
  deleteProfile,
  isProfileReferenced,
} from "./macro-plan-edit.js";
import { isoToEpochDay, epochDayToIso, resolveDayJournaled } from "./macro-plan.js";

/** @typedef {import('./macro-plan.js').MacroPlan} MacroPlan */
/** @typedef {import('./macro-plan.js').MacroDayProfile} MacroDayProfile */
/** @typedef {import('./macro-plan.js').DayTargets} DayTargets */
/** @typedef {import('./macro-plan.js').GoalJournalEntry} GoalJournalEntry */
/** @typedef {import('./models.js').UserProfile} UserProfile */

/** Matches ASSIGNMENT_KEEP_DAYS; longer spans clip (helper text, not an error). */
export const MAX_SPAN_DAYS = ASSIGNMENT_KEEP_DAYS;

/** Name of the auto-created day-type profile that mirrors the base targets. */
export const BASE_PROFILE_NAME = "Base";

/**
 * A dated goal phase. `intent` drives tone in the phases UI; `profileId` is
 * the MacroDayProfile id the phase applies through (linked on first apply);
 * `createdAtMillis` is set once on first save and keeps apply-order stable
 * across edits.
 * @typedef {Object} GoalPhase
 * @property {string} id
 * @property {string} name
 * @property {"CUT"|"MAINTAIN"|"BULK"} intent
 * @property {string} startDate ISO inclusive "YYYY-MM-DD"
 * @property {string} endDate   ISO inclusive
 * @property {number} calories
 * @property {number} proteinG
 * @property {number} carbsG
 * @property {number} fatG
 * @property {string|null} [profileId]
 * @property {number} [createdAtMillis]  0/unset = not saved yet
 */

/**
 * Typed validation failures for phase writes, consumed by the editor sheet.
 * (Mirror of the Kotlin sealed class; plain string codes.)
 * @typedef {"BLANK_NAME"|"BAD_RANGE"|"TOO_LONG"|"NO_PROFILE_SLOT"} PhaseEditError
 */

/** @param {string} iso @returns {number|null} epoch day; null when malformed */
function parseIsoOrNull(iso) {
  const d = isoToEpochDay(iso);
  return Number.isFinite(d) ? d : null;
}

/** Inclusive day count; 0 when either date fails to parse. @param {GoalPhase} phase */
export function phaseSpanDays(phase) {
  const start = parseIsoOrNull(phase.startDate);
  const end = parseIsoOrNull(phase.endDate);
  if (start == null || end == null) return 0;
  return end - start + 1;
}

/**
 * True when `iso` falls inside the phase window. ISO dates compare
 * lexicographically (mirrors the Kotlin operator contains).
 * @param {GoalPhase} phase @param {string} iso
 */
export function phaseContains(phase, iso) {
  return iso >= phase.startDate && iso <= phase.endDate;
}

/**
 * Validates `phase` against the plan capacity in `profile`.
 * @param {UserProfile} profile @param {GoalPhase} phase
 * @returns {PhaseEditError|null} null = ok
 */
export function validatePhase(profile, phase) {
  if (!phase.name || !phase.name.trim()) return "BLANK_NAME";
  const start = parseIsoOrNull(phase.startDate);
  const end = parseIsoOrNull(phase.endDate);
  if (start == null || end == null) return "BAD_RANGE";
  if (end < start) return "BAD_RANGE";
  if (end - start + 1 > MAX_SPAN_DAYS) return "TOO_LONG";
  const plan = profile.macroPlan;
  if (plan) {
    // Reviving a disabled plan appends a fresh Base profile on top of the
    // phase profile; enabled plans upsert the phase in place.
    const adds =
      (plan.enabled ? 0 : 1) + (phase.profileId != null && profileById(plan, phase.profileId) ? 0 : 1);
    if (adds > 0 && (plan.profiles?.length ?? 0) + adds > MAX_PROFILES) return "NO_PROFILE_SLOT";
  }
  return null;
}

/**
 * Applies a phase to the profile:
 *  - phase calories clamp to the safety floor/ceiling first — the stored
 *    GoalPhase.calories always equals the applied profile's calories, and the
 *    macros are re-balanced to that total (same shape as the base write path);
 *  - plan missing → fresh resolvable MANUAL plan: "Base" (current base
 *    targets, fresh stable id) + the phase profile, enabled, Base default;
 *  - plan present but disabled → revived in place (profiles, mode, weekday
 *    map and cycle pattern survive; a fresh Base becomes the default);
 *  - enabled plan → upsert the phase profile in place (reuses profileId);
 *  - then stamps dayAssignments for every date in [startDate, endDate]
 *    clipped to today ± ASSIGNMENT_KEEP_DAYS. Overlap ownership follows
 *    createdAtMillis: a newer phase keeps days an older phase re-saves into;
 *    the phase's own stale stamps outside its (clipped) range are dropped.
 *
 * @param {UserProfile} profile
 * @param {GoalPhase} phase
 * @param {string} isoToday
 * @param {number} [nowMillis] epoch millis stamping a first save
 * @returns {UserProfile}
 */
export function applyPhase(profile, phase, isoToday, nowMillis = Date.now()) {
  const start = parseIsoOrNull(phase.startDate);
  const end = parseIsoOrNull(phase.endDate);
  if (start == null || end == null) return profile;
  const clampedCalories = clampAutoCalories(phase.calories, profile);
  const clamped = balancedMacros(phase, clampedCalories);
  const today = parseIsoOrNull(isoToday);
  if (today == null) return profile;
  const clipFrom = today - ASSIGNMENT_KEEP_DAYS;
  const clipTo = today + ASSIGNMENT_KEEP_DAYS;
  const effectiveStart = Math.max(start, clipFrom);
  const effectiveEnd = Math.min(end, clipTo);

  const plan = profile.macroPlan;
  if (!plan) {
    // Fresh two-profile plan; replaces any inert (disabled) data.
    const base = baseProfile(profile);
    const nextPhase = { ...clamped, profileId: clamped.profileId ?? crypto.randomUUID() };
    const freshPlan = /** @type {MacroPlan} */ ({
      enabled: true,
      profiles: [base, phaseProfile(nextPhase, /** @type {string} */ (nextPhase.profileId))],
      mode: "MANUAL",
      defaultProfileId: base.id,
      weekdayProfileIds: {},
      cyclePattern: [],
      cycleAnchorDay: null,
      dayAssignments: assignmentsFor(nextPhase, effectiveStart, effectiveEnd),
    });
    return {
      ...profile,
      macroPlan: normalizedPlan(freshPlan, isoToday),
      goalPhases: upsertPhase(profile.goalPhases ?? [], nextPhase, nowMillis),
    };
  }

  const phaseProfileId = clamped.profileId ?? crypto.randomUUID();
  const linked = { ...clamped, profileId: phaseProfileId };
  // Stamp first: the overlap guard compares real createdAtMillis, so the row
  // must carry its stamp before stampedFor runs.
  const goalPhases = upsertPhase(profile.goalPhases ?? [], linked, nowMillis);
  const stored = goalPhases.find((p) => p.id === linked.id) ?? linked;
  if (!plan.enabled) {
    // Revive in place: keep every byte of the paused plan and re-point the
    // default at a fresh Base mirroring today's targets. Paused day-type data
    // survives; non-phase days resolve to Base.
    const base = baseProfile(profile);
    const revived = {
      ...plan,
      profiles: [
        ...(plan.profiles ?? []).filter((p) => p.id !== phaseProfileId),
        base,
        phaseProfile(linked, phaseProfileId),
      ],
      enabled: true,
      defaultProfileId: base.id,
      dayAssignments: {
        ...prunedWithin(plan.dayAssignments ?? {}, phaseProfileId, effectiveStart, effectiveEnd),
        ...stampedFor(stored, effectiveStart, effectiveEnd, plan.dayAssignments ?? {}, goalPhases),
      },
    };
    return { ...profile, macroPlan: normalizedPlan(revived, isoToday), goalPhases };
  }

  // Enabled plan: upsert the phase profile, reusing its id.
  const updatedPlan = {
    ...plan,
    profiles: [...(plan.profiles ?? []).filter((p) => p.id !== phaseProfileId), phaseProfile(linked, phaseProfileId)],
    dayAssignments: {
      ...prunedWithin(plan.dayAssignments ?? {}, phaseProfileId, effectiveStart, effectiveEnd),
      ...stampedFor(stored, effectiveStart, effectiveEnd, plan.dayAssignments ?? {}, goalPhases),
    },
  };
  return { ...profile, macroPlan: normalizedPlan(updatedPlan, isoToday), goalPhases };
}

/**
 * Removes a phase: drops it from the list, clears the dayAssignments
 * **inside its range that point at its profile** (manual switches to other
 * profiles survive), and deletes the phase profile only when nothing else
 * references it. With the two-profile auto plan this disables the plan —
 * Base equals the base targets, so days outside phases resolve unchanged.
 *
 * @param {UserProfile} profile @param {string} phaseId @param {string} isoToday
 * @returns {UserProfile}
 */
export function removePhase(profile, phaseId, isoToday) {
  const phase = (profile.goalPhases ?? []).find((p) => p.id === phaseId);
  if (!phase) return profile;
  const remainingPhases = (profile.goalPhases ?? []).filter((p) => p.id !== phaseId);
  const plan = profile.macroPlan;
  const profileId = phase.profileId;
  if (!plan || profileId == null || !profileById(plan, profileId)) {
    return { ...profile, goalPhases: remainingPhases };
  }
  const cleared = {
    ...plan,
    // Remove only the phase-window assignments pointing at the phase
    // profile; manual switches elsewhere (or to other profiles) survive.
    dayAssignments: Object.fromEntries(
      Object.entries(plan.dayAssignments ?? {}).filter(([date, ref]) => !(phaseContains(phase, date) && ref === profileId)),
    ),
  };
  const nextPlan = isProfileReferenced(cleared, profileId)
    ? cleared
    : deleteProfile(cleared, profileId, null, isoToday);
  return { ...profile, macroPlan: nextPlan, goalPhases: remainingPhases };
}

/**
 * Rolls phase stamps with the keep window (pure half of the journal refresh):
 * per phase, drops its own assignments that slid outside the clipped range
 * and gap-fills only missing days, so far-future phases self-activate as
 * their range enters today ± ASSIGNMENT_KEEP_DAYS and tails extend as the
 * window rolls. Fills newest-first (createdAtMillis) so fresh fills match
 * applyPhase overlap ownership; existing entries pointing at other profiles
 * (manual day-type switches inside a phase window) are never overwritten.
 *
 * @param {UserProfile} profile @param {string} isoToday
 * @returns {UserProfile|null} null when nothing moved (refresh loop converges)
 */
export function restampAssignments(profile, isoToday) {
  const plan = profile.macroPlan;
  if (!plan || !plan.enabled) return null;
  const phased = (profile.goalPhases ?? []).filter((p) => p.profileId != null);
  if (phased.length === 0) return null;
  const today = parseIsoOrNull(isoToday);
  if (today == null) return null;
  const clipFrom = today - ASSIGNMENT_KEEP_DAYS;
  const clipTo = today + ASSIGNMENT_KEEP_DAYS;
  /** @type {Record<string,string>} */
  let assignments = { ...(plan.dayAssignments ?? {}) };
  for (const phase of phased.slice().sort((a, b) => (b.createdAtMillis ?? 0) - (a.createdAtMillis ?? 0))) {
    const id = /** @type {string} */ (phase.profileId);
    const start = parseIsoOrNull(phase.startDate);
    const end = parseIsoOrNull(phase.endDate);
    if (start == null || end == null) continue;
    const from = Math.max(start, clipFrom);
    const to = Math.min(end, clipTo);
    if (from > to) continue;
    assignments = prunedWithin(assignments, id, from, to);
    for (const [date, ref] of Object.entries(assignmentsFor(phase, from, to))) {
      if (!(date in assignments)) assignments[date] = ref;
    }
  }
  if (shallowEqualAssignments(assignments, plan.dayAssignments ?? {})) return null;
  return { ...profile, macroPlan: normalizedPlan({ ...plan, dayAssignments: assignments }, isoToday) };
}

/**
 * Per-phase adherence rollup over logged days: intake vs journal-resolved
 * per-day goals (MACRO-CYCLE-D read rule). Excludes untracked days, days
 * with nothing logged, and today — the calorieAverage today-exclusion rule.
 * Mirrors Android ProgressAdherence byPhase / SettingsViewModel.phaseAdherence.
 *
 * @param {GoalPhase} phase
 * @param {Record<string,number>|Map<string,number>} intakeByDay daily calorie totals over logged days
 * @param {GoalJournalEntry[]} journal
 * @param {MacroPlan|null} plan
 * @param {DayTargets} base
 * @param {Set<string>} untrackedDays
 * @param {string} isoToday
 * @param {string} [isoFrom] analysis window start (default = phase start)
 * @param {string} [isoTo]   analysis window end   (default = phase end)
 * @returns {{overlapDays: number, avgDelta: number|null, netKcal: number|null}}
 */
export function phaseAdherence(phase, intakeByDay, journal, plan, base, untrackedDays, isoToday, isoFrom, isoTo) {
  const from = isoFrom ?? phase.startDate;
  const to = isoTo ?? phase.endDate;
  const startDay = parseIsoOrNull(from);
  const endDay = parseIsoOrNull(to);
  /** @type {number[]} */
  const deltas = [];
  if (startDay != null && endDay != null) {
    const get = /** @param {string} d */ (d) =>
      intakeByDay instanceof Map ? intakeByDay.get(d) : intakeByDay[d];
    for (let d = startDay; d <= endDay; d++) {
      const iso = epochDayToIso(d);
      if (!(iso < isoToday)) continue; // today + future excluded (isBefore(today))
      if (untrackedDays.has(iso)) continue;
      const intake = get(iso);
      if (intake == null || !Number.isFinite(intake)) continue; // nothing logged
      if (!phaseContains(phase, iso)) continue;
      deltas.push(Math.round(intake) - Math.round(resolveDayJournaled(journal, plan, base, iso, isoToday).targets.calories));
    }
  }
  if (deltas.length === 0) return { overlapDays: 0, avgDelta: null, netKcal: null };
  const sum = deltas.reduce((s, v) => s + v, 0);
  return {
    overlapDays: deltas.length,
    avgDelta: Math.trunc(sum / deltas.length), // Kotlin average().toInt() truncation
    netKcal: sum,
  };
}

// -- helpers ---------------------------------------------------------------

/** Fresh Base day-type profile mirroring today's effective targets. */
function baseProfile(profile) {
  const base = dailyTargets(profile);
  return {
    id: crypto.randomUUID(),
    name: BASE_PROFILE_NAME,
    calories: base.calories,
    proteinG: base.proteinG,
    carbsG: base.carbsG,
    fatG: base.fatG,
  };
}

/** @param {GoalPhase} phase @param {string} profileId */
function phaseProfile(phase, profileId) {
  return {
    id: profileId,
    name: phase.name,
    calories: phase.calories,
    proteinG: phase.proteinG,
    carbsG: phase.carbsG,
    fatG: phase.fatG,
  };
}

/**
 * Clamp the phase's macros to `calories` via the MacroDayProfile rebalance,
 * so the stored GoalPhase and its MacroDayProfile always carry the same
 * balanced grams. @param {GoalPhase} phase @param {number} calories
 */
function balancedMacros(phase, calories) {
  const balanced = rebalanceProfileTo(
    { id: "", name: "", calories: 0, proteinG: phase.proteinG, carbsG: phase.carbsG, fatG: phase.fatG },
    calories,
  );
  return {
    ...phase,
    calories,
    proteinG: balanced.proteinG,
    carbsG: balanced.carbsG,
    fatG: balanced.fatG,
  };
}

/** @param {GoalPhase} phase @param {number} startDay @param {number} endDay */
function assignmentsFor(phase, startDay, endDay) {
  /** @type {Record<string,string>} */
  const out = {};
  const id = phase.profileId;
  if (id == null) return out;
  for (let d = startDay; d <= endDay; d++) {
    out[epochDayToIso(d)] = id;
  }
  return out;
}

/** Drops `id`'s assignments that fall outside `[fromDay, toDay]`; others survive. */
function prunedWithin(assignments, id, fromDay, toDay) {
  /** @type {Record<string,string>} */
  const out = {};
  for (const [date, ref] of Object.entries(assignments)) {
    const d = parseIsoOrNull(date);
    if (ref === id && d != null && (d < fromDay || d > toDay)) continue;
    out[date] = ref;
  }
  return out;
}

/**
 * assignmentsFor guarded by overlap ownership: a date whose existing
 * assignment belongs to a **newer** phase (created after `linked` and
 * covering the day) is skipped, so re-saving an older phase never reclaims
 * days a newer phase owns. Everything else stamps normally.
 */
function stampedFor(linked, fromDay, toDay, existing, phases) {
  /** @type {Record<string,string>} */
  const out = {};
  for (const [date, ref] of Object.entries(assignmentsFor(linked, fromDay, toDay))) {
    // Ownership is read from the assignment on the ground (existing), not
    // from the phase's own stamp — mirror of the Kotlin filterKeys guard.
    const current = existing[date];
    if (current != null) {
      const owner = phases.find((p) => p.profileId === current && p.id !== linked.id);
      if (owner != null && (owner.createdAtMillis ?? 0) > (linked.createdAtMillis ?? 0) && phaseContains(owner, date)) {
        continue;
      }
    }
    out[date] = ref;
  }
  return out;
}

/** Replace by id; keep a non-zero createdAtMillis stable across edits. */
function upsertPhase(phases, next, nowMillis) {
  const existing = phases.find((p) => p.id === next.id);
  const stamp =
    next.createdAtMillis || existing?.createdAtMillis || nowMillis;
  return [...phases.filter((p) => p.id !== next.id), { ...next, createdAtMillis: stamp }];
}

/** @param {Record<string,string>} a @param {Record<string,string>} b */
function shallowEqualAssignments(a, b) {
  const ak = Object.keys(a);
  const bk = Object.keys(b);
  if (ak.length !== bk.length) return false;
  return ak.every((k) => a[k] === b[k]);
}
