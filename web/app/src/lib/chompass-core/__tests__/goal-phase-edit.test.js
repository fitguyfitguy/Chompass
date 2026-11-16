// @ts-check
// Mirror of Android GoalPhaseEditTest + the ProgressAdherence byPhase vectors.
import test from "node:test";
import assert from "node:assert/strict";
import {
  MAX_SPAN_DAYS,
  BASE_PROFILE_NAME,
  validatePhase,
  applyPhase,
  removePhase,
  restampAssignments,
  phaseAdherence,
  phaseContains,
  phaseSpanDays,
} from "../goal-phase-edit.js";
import { safetyFloorKcal, clampAutoCalories, dailyTargets } from "../formulas.js";

const PROFILE = {
  sex: "male",
  age: 30,
  heightCm: 180,
  weightKg: 80,
  bodyFatPercentage: null,
  activityLevel: "moderate",
  goal: "maintain",
  ketoMode: false,
  customCalories: 2200,
  customProtein: 150,
  customCarbs: 220,
  customFat: 70,
};

const TODAY = "2026-08-10";
const DAY_MS = 86_400_000;
const EPOCH_TODAY = Math.round(Date.UTC(2026, 7, 10) / DAY_MS);
const dayIso = (offset) => new Date((EPOCH_TODAY + offset) * DAY_MS).toISOString().slice(0, 10);

/** @param {Record<string, any>} [overrides] */
function profile(overrides = {}) {
  return { ...PROFILE, goalPhases: [], ...overrides };
}

function phase(overrides = {}) {
  return {
    id: "phase-1",
    name: "Shred",
    intent: "CUT",
    startDate: dayIso(1),
    endDate: dayIso(14),
    calories: 1800,
    proteinG: 160,
    carbsG: 150,
    fatG: 55,
    profileId: null,
    createdAtMillis: 0,
    ...overrides,
  };
}

function plan(overrides = {}) {
  return {
    enabled: true,
    profiles: [],
    mode: "MANUAL",
    defaultProfileId: null,
    weekdayProfileIds: {},
    cyclePattern: [],
    cycleAnchorDay: null,
    dayAssignments: {},
    ...overrides,
  };
}

/** @param {Record<string, string>} dayAssignments @param {string} id */
function assignedDays(dayAssignments, id) {
  return Object.keys(dayAssignments)
    .filter((d) => dayAssignments[d] === id)
    .sort();
}

test("validate rejects blank name, inverted range and oversize", () => {
  const p = profile();
  assert.equal(validatePhase(p, phase({ name: "  " })), "BLANK_NAME");
  assert.equal(validatePhase(p, phase({ startDate: dayIso(10), endDate: dayIso(5) })), "BAD_RANGE");
  // Unparseable start date → BAD_RANGE.
  assert.equal(validatePhase(p, phase({ startDate: "not-a-date", endDate: dayIso(5) })), "BAD_RANGE");
  assert.equal(validatePhase(p, phase({ startDate: TODAY, endDate: dayIso(MAX_SPAN_DAYS) })), "TOO_LONG");
  // Exactly the keep window is fine.
  assert.equal(validatePhase(p, phase({ startDate: TODAY, endDate: dayIso(MAX_SPAN_DAYS - 1) })), null);
});

test("validate rejects a new phase when the enabled plan is full", () => {
  const profiles = Array.from({ length: 7 }, (_, i) => ({
    id: `p${i + 1}`,
    name: `T${i + 1}`,
    calories: 2000,
    proteinG: 1,
    carbsG: 1,
    fatG: 1,
  }));
  const full = profile({ macroPlan: plan({ profiles }) });
  assert.equal(validatePhase(full, phase()), "NO_PROFILE_SLOT");
  // An existing phase profile (update path) still validates.
  assert.equal(validatePhase(full, phase({ profileId: "p1" })), null);
  // Disabled plans revive: a fresh Base appends on top of the phase profile.
  assert.equal(validatePhase(profile({ macroPlan: plan({ enabled: false, profiles }) }), phase()), "NO_PROFILE_SLOT");
  const roomy = profiles.slice(0, 5);
  assert.equal(validatePhase(profile({ macroPlan: plan({ enabled: false, profiles: roomy }) }), phase()), null);
  assert.equal(validatePhase(profile(), phase()), null);
});

test("apply on no plan creates Base + phase and assigns the clipped range", () => {
  const p = profile();
  // start = today - 5 (inside the keep window), end = today + 400 (clips to today + 366).
  const start = dayIso(-5);
  const end = dayIso(400);
  const next = applyPhase(p, phase({ startDate: start, endDate: end }), TODAY);
  const planOut = next.macroPlan;
  assert.ok(planOut);
  assert.equal(planOut.enabled, true);
  assert.equal(planOut.mode, "MANUAL");
  assert.equal(planOut.profiles.length, 2);
  const base = planOut.profiles.find((x) => x.name === BASE_PROFILE_NAME);
  assert.ok(base);
  assert.equal(planOut.defaultProfileId, base.id);
  // Base mirrors the profile's effective targets.
  const effective = dailyTargets(p);
  assert.equal(base.calories, effective.calories);
  assert.equal(base.proteinG, effective.proteinG);
  const stored = next.goalPhases[0];
  assert.ok(stored.profileId);
  const assigned = assignedDays(planOut.dayAssignments, stored.profileId);
  assert.equal(assigned[0], start);
  assert.equal(assigned[assigned.length - 1], dayIso(MAX_SPAN_DAYS));
  // First save stamps createdAtMillis.
  assert.ok(stored.createdAtMillis > 0);
});

test("applyPhase rebalances macros to the stored calories", () => {
  const next = applyPhase(profile(), phase(), TODAY);
  const stored = next.goalPhases[0];
  const clamped = clampAutoCalories(1800, profile());
  assert.equal(stored.calories, clamped);
  const planProfile = next.macroPlan.profiles.find((x) => x.id === stored.profileId);
  // Stored phase and plan profile carry the same balanced grams.
  assert.equal(stored.proteinG, planProfile.proteinG);
  assert.equal(stored.carbsG, planProfile.carbsG);
  assert.equal(stored.fatG, planProfile.fatG);
  // STANDARD keep-protein/fat: macros add up to the stored total (≤3 kcal slack).
  const macroKcal = 4 * stored.proteinG + 4 * stored.carbsG + 9 * stored.fatG;
  assert.ok(macroKcal >= clamped - 3 && macroKcal <= clamped, `macro kcal ${macroKcal} vs ${clamped}`);
});

test("apply on an existing plan updates in place and keeps manual days", () => {
  const extra = { id: "extra", name: "Extra", calories: 1900, proteinG: 1, carbsG: 1, fatG: 1 };
  const manualDay = dayIso(35); // outside the phase range
  const start = dayIso(10);
  const end = dayIso(30);
  const first = applyPhase(
    profile({ macroPlan: plan({ profiles: [extra], defaultProfileId: extra.id, dayAssignments: { [manualDay]: extra.id } }) }),
    phase({ startDate: start, endDate: end }),
    TODAY,
  );
  const stored = first.goalPhases[0];
  const plan1 = first.macroPlan;
  assert.equal(plan1.enabled, true);
  assert.equal(plan1.profiles.length, 2);
  /** @type {string[]} */
  const expectedDays = [];
  for (let d = 10; d <= 30; d++) expectedDays.push(dayIso(d));
  assert.deepEqual(assignedDays(plan1.dayAssignments, stored.profileId), expectedDays);
  // Manual day outside the phase survives; default profile untouched.
  assert.equal(plan1.dayAssignments[manualDay], extra.id);
  assert.equal(plan1.defaultProfileId, extra.id);

  // Re-apply with different targets: updates the same profile + phase id,
  // createdAtMillis stable. Calories clamp to the safety floor.
  const newKcal = safetyFloorKcal(profile()) + 100;
  const edited = { ...stored, calories: newKcal, createdAtMillis: 12345 };
  const second = applyPhase(first, edited, TODAY);
  const plan2 = second.macroPlan;
  assert.equal(plan2.profiles.length, 2);
  assert.equal(plan2.profiles.find((x) => x.id === stored.profileId).calories, newKcal);
  assert.equal(second.goalPhases[0].createdAtMillis, 12345);
  assert.equal(second.goalPhases[0].id, stored.id);
});

test("overlap overwrite: later write wins", () => {
  const cut = applyPhase(profile(), phase({ name: "Cut", startDate: dayIso(1), endDate: dayIso(20) }), TODAY);
  const both = applyPhase(cut, phase({ id: "phase-2", name: "Bulk", intent: "BULK", startDate: dayIso(10), endDate: dayIso(30) }), TODAY);
  // The bulk (applied later) owns the overlap day.
  const bulkRow = both.goalPhases.find((x) => x.name === "Bulk");
  assert.equal(both.macroPlan.dayAssignments[dayIso(15)], bulkRow.profileId);
  // Cut-only days still point at the cut profile.
  const cutRow = both.goalPhases.find((x) => x.name === "Cut");
  assert.equal(both.macroPlan.dayAssignments[dayIso(5)], cutRow.profileId);
  assert.equal(both.goalPhases.length, 2);
});

test("removePhase cleans only its own assignments and profile", () => {
  const extra = { id: "extra", name: "Extra", calories: 1900, proteinG: 1, carbsG: 1, fatG: 1 };
  const applied = applyPhase(
    profile({ macroPlan: plan({ profiles: [extra], defaultProfileId: extra.id }) }),
    phase({ startDate: dayIso(1), endDate: dayIso(10) }),
    TODAY,
  );
  const phaseRow = applied.goalPhases[0];
  const planAfterApply = applied.macroPlan;
  assert.equal(planAfterApply.dayAssignments[dayIso(5)], phaseRow.profileId);
  // Manual switch inside the range to another profile, written AFTER the
  // phase — removal must not clobber it.
  const withManual = {
    ...applied,
    macroPlan: { ...planAfterApply, dayAssignments: { ...planAfterApply.dayAssignments, [dayIso(3)]: extra.id } },
  };
  const removed = removePhase(withManual, phaseRow.id, TODAY);
  assert.equal(removed.macroPlan.dayAssignments[dayIso(5)], undefined);
  assert.equal(removed.macroPlan.dayAssignments[dayIso(3)], extra.id);
  assert.equal(removed.goalPhases.length, 0);
  // The phase profile lost its last reference → deleted; the plan drops
  // below the 2-profile minimum and disables (data otherwise kept).
  assert.equal(removed.macroPlan.enabled, false);
  assert.deepEqual(removed.macroPlan.profiles.map((x) => x.id), ["extra"]);
});

test("removePhase on the auto plan disables and keeps Base data", () => {
  const applied = applyPhase(profile(), phase(), TODAY);
  const phaseRow = applied.goalPhases[0];
  const removed = removePhase(applied, phaseRow.id, TODAY);
  const planOut = removed.macroPlan;
  assert.equal(planOut.enabled, false);
  assert.equal(planOut.profiles.length, 1);
  assert.deepEqual(planOut.dayAssignments, {});
  assert.equal(removed.goalPhases.length, 0);
});

test("remove unknown phase is a no-op", () => {
  const p = profile();
  assert.deepEqual(removePhase(p, "nope", TODAY), p);
});

test("phaseContains uses the inclusive ISO range; spanDays counts inclusively", () => {
  const p = phase({ startDate: "2026-08-01", endDate: "2026-08-10" });
  assert.equal(phaseContains(p, "2026-08-01"), true);
  assert.equal(phaseContains(p, "2026-08-10"), true);
  assert.equal(phaseContains(p, "2026-08-11"), false);
  assert.equal(phaseSpanDays(p), 10);
  assert.equal(phaseSpanDays(phase({ startDate: "bad", endDate: "2026-08-10" })), 0);
});

test("shrinking the range clears stale assignments", () => {
  const start = dayIso(1);
  const applied = applyPhase(profile(), phase({ startDate: start, endDate: dayIso(30) }), TODAY);
  const stored = applied.goalPhases[0];
  const shrunk = applyPhase(applied, { ...stored, endDate: dayIso(10) }, TODAY);
  const targets = assignedDays(shrunk.macroPlan.dayAssignments, stored.profileId);
  assert.equal(targets[targets.length - 1], dayIso(10));
  assert.ok(!targets.includes(dayIso(11)));
  assert.equal(targets[0], start);
});

test("removePhase after shrink leaves no trace", () => {
  const applied = applyPhase(profile(), phase({ startDate: dayIso(1), endDate: dayIso(30) }), TODAY);
  const stored = applied.goalPhases[0];
  const shrunk = applyPhase(applied, { ...stored, endDate: dayIso(10) }, TODAY);
  const removed = removePhase(shrunk, stored.id, TODAY);
  assert.equal(removed.goalPhases.length, 0);
  assert.deepEqual(assignedDays(removed.macroPlan.dayAssignments, stored.profileId), []);
  assert.ok(!removed.macroPlan.profiles.some((x) => x.id === stored.profileId));
});

test("re-saving an older phase does not reclaim newer-owned days", () => {
  const cut = phase({ name: "Cut", startDate: dayIso(1), endDate: dayIso(20), createdAtMillis: 1000 });
  const first = applyPhase(profile(), cut, TODAY);
  const bulk = phase({
    id: "phase-2",
    name: "Bulk",
    intent: "BULK",
    startDate: dayIso(10),
    endDate: dayIso(30),
    createdAtMillis: 2000,
  });
  const both = applyPhase(first, bulk, TODAY);
  // Re-save the older cut (edited targets): newer bulk keeps the overlap.
  const cutRow = both.goalPhases.find((x) => x.name === "Cut");
  const newKcal = safetyFloorKcal(profile()) + 50;
  const reSaved = applyPhase(both, { ...cutRow, calories: newKcal }, TODAY);
  const map = reSaved.macroPlan.dayAssignments;
  const bulkRow = reSaved.goalPhases.find((x) => x.name === "Bulk");
  assert.equal(map[dayIso(15)], bulkRow.profileId);
  const cutRowAfter = reSaved.goalPhases.find((x) => x.name === "Cut");
  assert.equal(map[dayIso(5)], cutRowAfter.profileId);
  // Targets still updated on the shared profile.
  assert.equal(reSaved.macroPlan.profiles.find((x) => x.id === cutRowAfter.profileId).calories, newKcal);
});

test("apply on a disabled plan revives it keeping the schedule", () => {
  const paused = plan({
    enabled: false,
    profiles: [
      { id: "training", name: "Training", calories: 2800, proteinG: 1, carbsG: 1, fatG: 1 },
      { id: "rest", name: "Rest", calories: 2200, proteinG: 1, carbsG: 1, fatG: 1 },
    ],
    mode: "WEEKDAYS",
    defaultProfileId: "rest",
    weekdayProfileIds: { MONDAY: "training" },
  });
  const next = applyPhase(profile({ macroPlan: paused }), phase(), TODAY);
  const planOut = next.macroPlan;
  assert.equal(planOut.enabled, true);
  assert.equal(planOut.mode, "WEEKDAYS");
  assert.equal(planOut.weekdayProfileIds.MONDAY, "training");
  const ids = new Set(planOut.profiles.map((x) => x.id));
  assert.ok(ids.has("training") && ids.has("rest"));
  const base = planOut.profiles.find((x) => x.name === BASE_PROFILE_NAME);
  assert.equal(planOut.defaultProfileId, base.id);
  // Base mirrors the profile's effective targets, not the phase's.
  assert.equal(base.calories, dailyTargets(profile({ macroPlan: paused })).calories);
  // The phase row links to its profile and stamps its range.
  const stored = next.goalPhases[0];
  assert.ok(stored.profileId);
  assert.ok(assignedDays(planOut.dayAssignments, stored.profileId).length > 0);
});

test("apply clamps phase calories to the safety floor", () => {
  const p = profile();
  const floor = safetyFloorKcal(p);
  const next = applyPhase(p, phase({ calories: 800 }), TODAY);
  const stored = next.goalPhases[0];
  assert.equal(stored.calories, floor);
  assert.equal(stored.calories, next.macroPlan.profiles.find((x) => x.id === stored.profileId).calories);
});

const baseAndPhaseProfiles = (phaseId, phaseCalories = 1800) => [
  { id: "base", name: BASE_PROFILE_NAME, calories: 2200, proteinG: 1, carbsG: 1, fatG: 1 },
  { id: phaseId, name: "Phased", calories: phaseCalories, proteinG: 1, carbsG: 1, fatG: 1 },
];

/** @param {[number, number]} range @param {string} id */
function stamped([from, to], id) {
  /** @type {Record<string, string>} */
  const out = {};
  for (let d = from; d <= to; d++) out[dayIso(d)] = id;
  return out;
}

test("restampAssignments fills the tail only as the window rolls", () => {
  const phaseId = "farPhase";
  const start = 300;
  const end = 665; // 366-day span, tail beyond today + keep.
  const far = phase({
    name: "Far",
    startDate: dayIso(start),
    endDate: dayIso(end),
    profileId: phaseId,
    createdAtMillis: 1000,
  });
  const p = profile({
    macroPlan: plan({
      profiles: baseAndPhaseProfiles(phaseId),
      defaultProfileId: "base",
      dayAssignments: stamped([start, MAX_SPAN_DAYS], phaseId),
    }),
    goalPhases: [far],
  });
  // Today: the tail sits outside the keep window → unchanged (null).
  assert.equal(restampAssignments(p, TODAY), null);
  assert.equal(p.macroPlan.dayAssignments[dayIso(MAX_SPAN_DAYS + 1)], undefined);
  // Tomorrow: today + 367 enters the window → filled.
  const rolled = restampAssignments(p, dayIso(1));
  assert.ok(rolled);
  assert.equal(rolled.macroPlan.dayAssignments[dayIso(MAX_SPAN_DAYS + 1)], phaseId);
  assert.equal(rolled.macroPlan.dayAssignments[dayIso(start)], phaseId);
});

test("restampAssignments fills newest-first on overlap", () => {
  const cutId = "cutP";
  const bulkId = "bulkP";
  const cut = phase({ name: "Cut", startDate: TODAY, endDate: dayIso(20), profileId: cutId, createdAtMillis: 1000 });
  const bulk = phase({
    id: "phase-2",
    name: "Bulk",
    intent: "BULK",
    startDate: dayIso(10),
    endDate: dayIso(30),
    profileId: bulkId,
    createdAtMillis: 2000,
  });
  const p = profile({
    macroPlan: plan({
      profiles: [
        ...baseAndPhaseProfiles(cutId),
        { id: bulkId, name: "Bulk", calories: 2900, proteinG: 1, carbsG: 1, fatG: 1 },
      ],
      defaultProfileId: "base",
      dayAssignments: { [dayIso(15)]: bulkId, [dayIso(16)]: bulkId },
    }),
    goalPhases: [cut, bulk],
  });
  const rolled = restampAssignments(p, TODAY);
  assert.ok(rolled);
  const map = rolled.macroPlan.dayAssignments;
  // Overlap keeps the newer bulk; cut fills only the leftovers.
  assert.equal(map[dayIso(15)], bulkId);
  assert.equal(map[dayIso(25)], bulkId);
  assert.equal(map[TODAY], cutId);
  assert.equal(map[dayIso(9)], cutId);
});

test("restampAssignments preserves a manual assignment inside a phase", () => {
  const phaseId = "cutP";
  const manualDay = dayIso(5);
  const p = profile({
    macroPlan: plan({
      profiles: [...baseAndPhaseProfiles(phaseId), { id: "extra", name: "Extra", calories: 1900, proteinG: 1, carbsG: 1, fatG: 1 }],
      defaultProfileId: "base",
      dayAssignments: { [manualDay]: "extra" },
    }),
    goalPhases: [phase({ name: "Phased", startDate: TODAY, endDate: dayIso(10), profileId: phaseId, createdAtMillis: 1000 })],
  });
  const rolled = restampAssignments(p, TODAY);
  assert.ok(rolled);
  const map = rolled.macroPlan.dayAssignments;
  assert.equal(map[manualDay], "extra");
  assert.equal(map[dayIso(1)], phaseId);
  assert.equal(map[dayIso(10)], phaseId);
});

test("restampAssignments is idempotent", () => {
  const applied = applyPhase(profile(), phase({ startDate: TODAY, endDate: dayIso(30) }), TODAY);
  // Window already covers the range → nothing moves.
  assert.equal(restampAssignments(applied, TODAY), null);
  // A hole in the middle gets filled once, then stable.
  const holed = {
    ...applied,
    macroPlan: {
      ...applied.macroPlan,
      dayAssignments: Object.fromEntries(
        Object.entries(applied.macroPlan.dayAssignments).filter(([d]) => d !== dayIso(5)),
      ),
    },
  };
  const once = restampAssignments(holed, TODAY);
  assert.ok(once);
  assert.equal(once.macroPlan.dayAssignments[dayIso(5)], holed.goalPhases[0].profileId);
  assert.deepEqual(restampAssignments(once, TODAY), null);
});

test("restampAssignments ignores a disabled plan and missing phases", () => {
  assert.equal(restampAssignments(profile(), TODAY), null);
  assert.equal(restampAssignments(profile({ macroPlan: plan({ enabled: false }) }), TODAY), null);
});

test("phaseAdherence rolls up intake vs journaled goals", () => {
  const p = phase({ startDate: "2026-08-01", endDate: "2026-08-10" });
  const base = dailyTargets(profile()); // 2200 kcal
  const journal = [{ date: "2026-08-01", calories: 2200, proteinG: 150, carbsG: 220, fatG: 70 }];
  const intake = { "2026-08-01": 2400, "2026-08-02": 2000, "2026-08-03": 5000, "2026-08-04": 2200 };
  const untracked = new Set(["2026-08-03"]);
  const r = phaseAdherence(p, intake, journal, null, base, untracked, "2026-08-04");
  // 08-01: +200 (journaled 2200); 08-02: −200 (no journal → resolver 2200);
  // 08-03 untracked; 08-04 is today → excluded. 08-05+ has no intake.
  assert.equal(r.overlapDays, 2);
  assert.equal(r.avgDelta, 0);
  assert.equal(r.netKcal, 0);
});

test("phaseAdherence restricts to the phase window and truncates the average", () => {
  const p = phase({ startDate: "2026-08-02", endDate: "2026-08-05" });
  const base = dailyTargets(profile());
  const journal = [];
  const intake = { "2026-08-01": 3000, "2026-08-02": 2210, "2026-08-03": 2201 };
  const r = phaseAdherence(p, intake, journal, null, base, new Set(), "2026-08-06");
  // 08-01 sits outside the window; +10 and +1 → avg trunc(11/2) = 5, net 11.
  assert.equal(r.overlapDays, 2);
  assert.equal(r.avgDelta, 5);
  assert.equal(r.netKcal, 11);
});

test("phaseAdherence reports zeros when nothing logged", () => {
  const p = phase({ startDate: "2026-08-01", endDate: "2026-08-10" });
  const r = phaseAdherence(p, {}, [], null, dailyTargets(profile()), new Set(), "2026-08-04");
  assert.deepEqual(r, { overlapDays: 0, avgDelta: null, netKcal: null });
});

test("phaseAdherence honors an explicit analysis window (range ∩ phase)", () => {
  const p = phase({ startDate: "2026-08-01", endDate: "2026-08-30" });
  const base = dailyTargets(profile());
  const intake = { "2026-08-01": 2400, "2026-08-20": 2400 };
  const r = phaseAdherence(p, intake, [], null, base, new Set(), "2026-08-25", "2026-08-15", "2026-08-25");
  // Only 08-20 is inside both the phase and the window.
  assert.equal(r.overlapDays, 1);
  assert.equal(r.avgDelta, 200);
  assert.equal(r.netKcal, 200);
});
