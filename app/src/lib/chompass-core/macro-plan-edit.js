// @ts-check

/**
 * Pure macro day-plan write path — JS mirror of Android `MacroPlanEdit`
 * (Codeberg #60, docs/local/MACRO_PROFILES_DESIGN.md § Write-path invariants)
 * plus the AI-recalc plan application (`UserProfile.applyingAiGoalsToPlan`,
 * incl. `MacroDayProfile.shiftedBy` / `rebalancedTo`) and the journal-refresh
 * pure core (`GoalJournalService.updatedJournal`).
 *
 * All functions are pure; persistence + UI live in components/lib.
 */

import { clampAutoCalories, dailyCalories } from "./formulas.js";
import {
  isoToEpochDay,
  epochDayToIso,
  resolveDay,
  gapFillJournal,
  upsertJournalEntry,
} from "./macro-plan.js";

/** @typedef {import('./macro-plan.js').MacroPlan} MacroPlan */
/** @typedef {import('./macro-plan.js').MacroDayProfile} MacroDayProfile */
/** @typedef {import('./macro-plan.js').GoalJournalEntry} GoalJournalEntry */
/** @typedef {import('./models.js').UserProfile} UserProfile */

export const MIN_PROFILES = 2;
export const MAX_PROFILES = 7;
export const ASSIGNMENT_KEEP_DAYS = 366;

/** @param {MacroPlan|null} plan @param {string|null} id */
export function profileById(plan, id) {
  if (!plan || !id) return undefined;
  return plan.profiles?.find((p) => p.id === id);
}

/**
 * Shift a profile's kcal share to a new calorie total (applyCaloriesEdit-style
 * split; fat absorbs the rounding remainder). Mirror of
 * MacroDayProfile.rebalancedTo.
 * @param {MacroDayProfile} profile
 * @param {number} targetCalories
 */
export function rebalanceProfileTo(profile, targetCalories) {
  const weights = [profile.proteinG * 4, profile.carbsG * 4, profile.fatG * 9];
  const total = weights[0] + weights[1] + weights[2];
  const target = Math.max(0, Math.trunc(targetCalories));
  if (total <= 0) return { ...profile, calories: target };
  const protein = Math.round((target * weights[0]) / total / 4);
  const carbs = Math.round((target * weights[1]) / total / 4);
  const fat = Math.trunc(Math.max(0, target - protein * 4 - carbs * 4) / 9);
  return { ...profile, calories: target, proteinG: protein, carbsG: carbs, fatG: fat };
}

/**
 * Shift calories by deltaKcal with the MACRO-CYCLE-C clamp, then re-balance
 * macros. Mirror of MacroDayProfile.shiftedBy.
 * @param {MacroDayProfile} profile
 * @param {number} deltaKcal
 * @param {UserProfile} userProfile
 */
export function shiftProfileBy(profile, deltaKcal, userProfile) {
  const target = clampAutoCalories(profile.calories + deltaKcal, userProfile);
  if (target === profile.calories) return profile;
  return rebalanceProfileTo(profile, target);
}

/** @param {MacroPlan} plan @param {string} isoToday */
function prunedAssignments(plan, isoToday) {
  const today = isoToEpochDay(isoToday);
  if (!Number.isFinite(today)) return { ...plan.dayAssignments };
  const from = epochDayToIso(today - ASSIGNMENT_KEEP_DAYS);
  const to = epochDayToIso(today + ASSIGNMENT_KEEP_DAYS);
  /** @type {Record<string,string>} */
  const out = {};
  for (const [date, id] of Object.entries(plan.dayAssignments ?? {})) {
    if (date >= from && date <= to) out[date] = id;
  }
  return out;
}

/** @param {MacroPlan} plan @param {string} isoToday */
export function normalizedPlan(plan, isoToday) {
  const cycleBroken =
    plan.mode === "CYCLE" && ((plan.cyclePattern?.length ?? 0) < MIN_PROFILES || !plan.cycleAnchorDay);
  const count = plan.profiles?.length ?? 0;
  /** @type {Record<string,string>} */
  const weekdayProfileIds = {};
  for (const [day, id] of Object.entries(plan.weekdayProfileIds ?? {})) {
    if (profileById(plan, id)) weekdayProfileIds[day] = id;
  }
  /** @type {Record<string,string>} */
  const dayAssignments = {};
  for (const [date, id] of Object.entries(prunedAssignments(plan, isoToday))) {
    if (profileById(plan, id)) dayAssignments[date] = id;
  }
  return {
    ...plan,
    enabled: plan.enabled === true && count >= MIN_PROFILES && count <= MAX_PROFILES,
    mode: cycleBroken ? "MANUAL" : plan.mode ?? "MANUAL",
    defaultProfileId: plan.defaultProfileId != null && profileById(plan, plan.defaultProfileId)
      ? plan.defaultProfileId
      : null,
    weekdayProfileIds,
    cyclePattern: (plan.cyclePattern ?? []).filter((id) => profileById(plan, id)),
    dayAssignments,
  };
}

/** Fill in a resolvable config (default profile + usable CYCLE pattern/anchor). */
function seededPlan(plan, isoToday) {
  let next = plan;
  if (!profileById(next, next.defaultProfileId ?? null)) {
    next = { ...next, defaultProfileId: next.profiles?.[0]?.id ?? null };
  }
  if (next.mode === "CYCLE") {
    const pattern = (next.cyclePattern ?? []).filter((id) => profileById(next, id));
    if (pattern.length < MIN_PROFILES) {
      next = {
        ...next,
        cyclePattern: (next.profiles ?? []).slice(0, MAX_PROFILES).map((p) => p.id),
        cycleAnchorDay: next.cycleAnchorDay ?? isoToday,
      };
    }
  }
  return next;
}

/**
 * Master toggle. Enabling seeds a resolvable mode config when pieces are missing.
 * @param {MacroPlan|null} plan
 * @param {boolean} enabled
 * @param {string} isoToday
 * @returns {MacroPlan|null}
 */
export function setPlanEnabled(plan, enabled, isoToday) {
  if (!plan) return null;
  if (!enabled) return normalizedPlan({ ...plan, enabled: false }, isoToday);
  const count = plan.profiles?.length ?? 0;
  if (count < MIN_PROFILES || count > MAX_PROFILES) {
    return normalizedPlan({ ...plan, enabled: false }, isoToday);
  }
  return normalizedPlan({ ...seededPlan(plan, isoToday), enabled: true }, isoToday);
}

/**
 * Add or update one profile. Calories clamp to the safety floor/ceiling
 * (MACRO-CYCLE-C); the caller enforces distinct non-empty names. Never flips
 * `enabled`.
 * @param {MacroPlan|null} plan
 * @param {MacroDayProfile} profile
 * @param {UserProfile} userProfile
 * @param {string} isoToday
 */
export function upsertProfile(plan, profile, userProfile, isoToday) {
  const current = plan ?? { enabled: false, profiles: [], mode: "MANUAL" };
  const clamped = { ...profile, calories: clampAutoCalories(profile.calories, userProfile) };
  const others = (current.profiles ?? []).filter((p) => p.id !== clamped.id);
  const next =
    others.length >= MAX_PROFILES && (current.profiles ?? []).every((p) => p.id !== clamped.id)
      ? current
      : { ...current, profiles: [...others, clamped] };
  return normalizedPlan(next, isoToday);
}

/**
 * Delete a profile and scrub or re-point every reference (replacementId from
 * the surviving profiles re-points; null scrubs). Below MIN_PROFILES the plan
 * disables (data kept); a CYCLE pattern below 2 entries falls back to MANUAL.
 * @param {MacroPlan} plan
 * @param {string} id
 * @param {string|null} replacementId
 * @param {string} isoToday
 */
export function deleteProfile(plan, id, replacementId, isoToday) {
  const remaining = (plan.profiles ?? []).filter((p) => p.id !== id);
  if (remaining.length === (plan.profiles ?? []).length) return plan;
  const replace =
    replacementId && replacementId !== id && remaining.some((p) => p.id === replacementId)
      ? replacementId
      : null;
  /** @param {string|null} ref */
  const repoint = (ref) => (ref !== id ? ref : replace);
  /** @type {Record<string,string>} */
  const weekdayProfileIds = {};
  for (const [day, ref] of Object.entries(plan.weekdayProfileIds ?? {})) {
    const r = repoint(ref);
    if (r) weekdayProfileIds[day] = r;
  }
  /** @type {Record<string,string>} */
  const dayAssignments = {};
  for (const [date, ref] of Object.entries(plan.dayAssignments ?? {})) {
    const r = repoint(ref);
    if (r) dayAssignments[date] = r;
  }
  return normalizedPlan(
    {
      ...plan,
      profiles: remaining,
      defaultProfileId: repoint(plan.defaultProfileId ?? null) ?? null,
      weekdayProfileIds,
      cyclePattern: (plan.cyclePattern ?? []).map(repoint).filter((r) => r != null),
      dayAssignments,
    },
    isoToday,
  );
}

/** Reorder the profile list (display order + pattern-builder chip order). */
export function reorderProfiles(plan, orderedIds) {
  const profiles = orderedIds
    .map((id) => (plan.profiles ?? []).find((p) => p.id === id))
    .filter(Boolean);
  if (!profiles.length) return plan;
  return { ...plan, profiles };
}

/**
 * Switch mode, seeding the new mode's config so it resolves immediately.
 * @param {MacroPlan} plan @param {"MANUAL"|"WEEKDAYS"|"CYCLE"} mode @param {string} isoToday
 */
export function setPlanMode(plan, mode, isoToday) {
  const base =
    mode === "CYCLE" ? { ...plan, cycleAnchorDay: plan.cycleAnchorDay ?? isoToday } : plan;
  return normalizedPlan(seededPlan({ ...base, mode }, isoToday), isoToday);
}

/** @param {MacroPlan} plan @param {string|null} profileId */
export function setDefaultProfile(plan, profileId) {
  return { ...plan, defaultProfileId: profileId };
}

/** Weekday map write; null clears the entry (falls back to the default). */
export function setWeekdayProfile(plan, dayName, profileId) {
  const weekdayProfileIds = { ...(plan.weekdayProfileIds ?? {}) };
  if (profileId == null) delete weekdayProfileIds[dayName];
  else weekdayProfileIds[dayName] = profileId;
  return { ...plan, weekdayProfileIds };
}

/** @param {MacroPlan} plan @param {string[]} pattern */
export function setCyclePattern(plan, pattern) {
  return {
    ...plan,
    cyclePattern: pattern.filter((id) => profileById(plan, id) != null),
  };
}

/** Re-anchor the cycle so the pattern starts over at today. */
export function restartCycle(plan, isoToday) {
  return { ...plan, cycleAnchorDay: isoToday };
}

/** Per-day override write for ANY mode; null removes the override. */
export function setDayAssignment(plan, isoDate, profileId) {
  const dayAssignments = { ...(plan.dayAssignments ?? {}) };
  if (profileId == null) delete dayAssignments[isoDate];
  else dayAssignments[isoDate] = profileId;
  return { ...plan, dayAssignments };
}

/** Keto transition (settled Q4): pause the plan, keep every byte of data. */
export function pausedForKeto(plan) {
  return plan ? { ...plan, enabled: false } : plan;
}

/** Whether any schedule piece points at id. */
export function isProfileReferenced(plan, id) {
  if (!plan || !id) return false;
  return (
    plan.defaultProfileId === id ||
    Object.values(plan.weekdayProfileIds ?? {}).includes(id) ||
    (plan.cyclePattern ?? []).includes(id) ||
    Object.values(plan.dayAssignments ?? {}).includes(id)
  );
}

/**
 * AI/Adaptive plan application (#60 phase 4 mirror): the base target set is
 * written by `applyBase` (applyingAiGoals); when a macro plan is enabled (and
 * base calories are not locked — locks stay base-scoped), every day-type
 * profile moves too. Explicit model rows (result.profiles, matched by id,
 * per-profile MACRO-CYCLE-C clamps) win; a missing array falls back to the
 * same kcal delta the base change implies, so the training/rest spread
 * survives and older-model responses still work.
 *
 * @param {UserProfile} profile
 * @param {{ calories: number, protein: number, carbs: number, fat: number, profiles?: Array<{id: string, calories: number, proteinG?: number, protein?: number, carbsG?: number, carbs?: number, fatG?: number, fat?: number}> }} result
 * @param {(profile: UserProfile, result: { calories: number, protein: number, carbs: number, fat: number }) => UserProfile} applyBase
 * @returns {UserProfile}
 */
export function applyingAiGoalsToPlan(profile, result, applyBase) {
  const base = applyBase(profile, {
    calories: result.calories,
    protein: result.protein,
    carbs: result.carbs,
    fat: result.fat,
  });
  const plan = profile.macroPlan;
  if (!plan || plan.enabled !== true) return base;
  if (profile.caloriesLocked) return base;
  const deltaKcal = dailyCalories(base) - dailyCalories(profile);
  const aiById = new Map((result.profiles ?? []).map((row) => [row.id, row]));
  const updated = (plan.profiles ?? []).map((dayProfile) => {
    const ai = aiById.get(dayProfile.id);
    if (ai) {
      return {
        ...dayProfile,
        calories: clampAutoCalories(ai.calories, profile),
        proteinG: Math.min(500, Math.max(0, Math.round(ai.proteinG ?? ai.protein ?? 0))),
        carbsG: Math.min(1200, Math.max(0, Math.round(ai.carbsG ?? ai.carbs ?? 0))),
        fatG: Math.min(400, Math.max(0, Math.round(ai.fatG ?? ai.fat ?? 0))),
      };
    }
    return shiftProfileBy(dayProfile, deltaKcal, profile);
  });
  return { ...base, macroPlan: { ...plan, profiles: updated } };
}

/**
 * Journal-refresh pure core — mirror of GoalJournalService.updatedJournal:
 * gap-fill + record today, or null when nothing changes (plan off, or today's
 * entry already holds the resolved values) so unrelated profile writes never
 * bump updatedAtMillis and provenance survives.
 *
 * @param {UserProfile|null} profile
 * @param {GoalJournalEntry[]} current
 * @param {import('./macro-plan.js').DayTargets} baseTargets live base (formula) targets
 * @param {string} isoToday
 * @param {number} nowMillis
 * @param {"PLAN"|"MANUAL_SWITCH"|"OVERRIDE"|"GAP_FILL"} source
 * @returns {GoalJournalEntry[]|null}
 */
export function updatedJournalEntries(profile, current, baseTargets, isoToday, nowMillis, source) {
  const plan = profile?.macroPlan;
  if (!profile || !plan || plan.enabled !== true) return null;
  const gapped = gapFillJournal(current, isoToday, nowMillis, (isoDate) => resolveDay(plan, baseTargets, isoDate));
  const resolved = resolveDay(plan, baseTargets, isoToday);
  /** @type {GoalJournalEntry} */
  const todayEntry = {
    date: isoToday,
    calories: resolved.targets.calories,
    proteinG: resolved.targets.proteinG,
    carbsG: resolved.targets.carbsG,
    fatG: resolved.targets.fatG,
    profileId: resolved.profileId,
    profileName: resolved.profileName,
    updatedAtMillis: nowMillis,
    source,
  };
  const existing = gapped.find((e) => e.date === todayEntry.date);
  if (existing && hasSameValues(existing, todayEntry)) {
    return arraysEqual(gapped, current) ? null : gapped;
  }
  const upserted = upsertJournalEntry(gapped, todayEntry, isoToday);
  return arraysEqual(upserted, current) ? null : upserted;
}

/** @param {GoalJournalEntry} a @param {GoalJournalEntry} b */
function hasSameValues(a, b) {
  return (
    a.date === b.date && a.calories === b.calories && a.proteinG === b.proteinG &&
    a.carbsG === b.carbsG && a.fatG === b.fatG &&
    (a.profileId ?? null) === (b.profileId ?? null) && (a.profileName ?? null) === (b.profileName ?? null)
  );
}

/** @param {GoalJournalEntry[]} a @param {GoalJournalEntry[]} b */
function arraysEqual(a, b) {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    const x = a[i];
    const y = b[i];
    if (
      x.date !== y.date || x.calories !== y.calories || x.proteinG !== y.proteinG ||
      x.carbsG !== y.carbsG || x.fatG !== y.fatG ||
      (x.profileId ?? null) !== (y.profileId ?? null) ||
      (x.profileName ?? null) !== (y.profileName ?? null) ||
      (x.source ?? "") !== (y.source ?? "")
    ) {
      return false;
    }
  }
  return true;
}
