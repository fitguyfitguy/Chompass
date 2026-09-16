// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import {
  MIN_PROFILES,
  MAX_PROFILES,
  normalizedPlan,
  setPlanEnabled,
  upsertProfile,
  deleteProfile,
  setPlanMode,
  setCyclePattern,
  setDayAssignment,
  pausedForKeto,
  isProfileReferenced,
  shiftProfileBy,
  rebalanceProfileTo,
  applyingAiGoalsToPlan,
  updatedJournalEntries,
} from "../macro-plan-edit.js";
import { dailyTargets } from "../formulas.js";
import { upsertJournalEntry } from "../macro-plan.js";

const PROFILE = {
  sex: "male",
  age: 30,
  heightCm: 180,
  weightKg: 80,
  bodyFatPercentage: null,
  activityLevel: "moderate",
  goal: "maintain",
  ketoMode: false,
};

const TRAINING = { id: "t", name: "Training day", calories: 2600, proteinG: 170, carbsG: 300, fatG: 75 };
const REST = { id: "r", name: "Rest day", calories: 2050, proteinG: 150, carbsG: 150, fatG: 75 };

function plan(overrides = {}) {
  return {
    enabled: true,
    profiles: [TRAINING, REST],
    mode: "MANUAL",
    defaultProfileId: "t",
    weekdayProfileIds: {},
    cyclePattern: [],
    cycleAnchorDay: null,
    dayAssignments: {},
    ...overrides,
  };
}

const TODAY = "2026-08-25";

test("setEnabled requires 2..7 profiles and seeds a default", () => {
  const lone = setPlanEnabled(plan({ profiles: [TRAINING], defaultProfileId: null, enabled: false }), true, TODAY);
  assert.equal(lone.enabled, false);
  const ok = setPlanEnabled(plan({ enabled: false, defaultProfileId: null }), true, TODAY);
  assert.equal(ok.enabled, true);
  assert.equal(ok.defaultProfileId, "t");
});

test("upsertProfile clamps calories to the safety floor (MACRO-CYCLE-C)", () => {
  const next = upsertProfile(plan(), { ...TRAINING, id: "x", name: "Very low", calories: 400 }, PROFILE, TODAY);
  const x = next.profiles.find((p) => p.id === "x");
  assert.ok(x && x.calories >= 1200);
});

test("deleteProfile scrubs references and disables below MIN_PROFILES", () => {
  const referenced = plan({ mode: "CYCLE", cyclePattern: ["t", "r"], cycleAnchorDay: TODAY, dayAssignments: { "2026-08-20": "t" } });
  const scrubbed = deleteProfile(referenced, "t", null, TODAY);
  assert.equal(scrubbed.defaultProfileId, null);
  assert.deepEqual(scrubbed.cyclePattern.filter(Boolean), ["r"]);
  assert.equal(scrubbed.dayAssignments["2026-08-20"], undefined);
  // one profile left -> disabled, data kept
  assert.equal(scrubbed.enabled, false);
  assert.equal(scrubbed.profiles.length, 1);
});

test("deleteProfile can re-point references to a replacement", () => {
  const p = plan({ dayAssignments: { "2026-08-20": "t" }, weekdayProfileIds: { MONDAY: "t" } });
  const next = deleteProfile(p, "t", "r", TODAY);
  assert.equal(next.dayAssignments["2026-08-20"], "r");
  assert.equal(next.weekdayProfileIds.MONDAY, "r");
});

test("setPlanMode CYCLE seeds pattern + anchor; broken CYCLE falls back to MANUAL", () => {
  const cycled = setPlanMode(plan(), "CYCLE", TODAY);
  assert.equal(cycled.mode, "CYCLE");
  assert.equal(cycled.cyclePattern.length, MIN_PROFILES);
  assert.equal(cycled.cycleAnchorDay, TODAY);
  const broken = normalizedPlan(plan({ mode: "CYCLE", cyclePattern: ["t"], cycleAnchorDay: null }), TODAY);
  assert.equal(broken.mode, "MANUAL");
});

test("setCyclePattern drops unknown ids; assignments pruned to ±366 days", () => {
  const withJunk = plan({ cyclePattern: ["t", "ghost"] });
  assert.deepEqual(setCyclePattern(withJunk, ["t", "ghost"]).cyclePattern, ["t"]);
  const stale = plan({ dayAssignments: { "2020-01-01": "t", "2026-08-24": "r" } });
  const pruned = normalizedPlan(stale, TODAY);
  assert.equal(pruned.dayAssignments["2020-01-01"], undefined);
  assert.equal(pruned.dayAssignments["2026-08-24"], "r");
});

test("pausedForKeto keeps data, only disables", () => {
  const paused = pausedForKeto(plan());
  assert.equal(paused.enabled, false);
  assert.equal(paused.profiles.length, 2);
});

test("isProfileReferenced finds schedule references", () => {
  const p = plan({ weekdayProfileIds: { FRIDAY: "r" } });
  assert.equal(isProfileReferenced(p, "r"), true);
  assert.equal(isProfileReferenced(p, "ghost"), false);
});

test("rebalanceProfileTo keeps kcal shares; shiftProfileBy clamps", () => {
  const rb = rebalanceProfileTo(TRAINING, 2600);
  assert.equal(rb.calories, 2600);
  // 4*p + 4*c + 9*f ≈ target (fat absorbs the remainder)
  assert.ok(Math.abs(4 * rb.proteinG + 4 * rb.carbsG + 9 * rb.fatG - 2600) <= 9);
  const shifted = shiftProfileBy(REST, -100, PROFILE);
  assert.equal(shifted.calories, REST.calories - 100);
  const floored = shiftProfileBy(REST, -5000, PROFILE);
  assert.ok(floored.calories >= 1200);
});

const applyBase = (p, result) => ({
  ...p,
  customCalories: result.calories,
  customProtein: result.protein,
  customCarbs: result.carbs,
  customFat: result.fat,
});

test("applyingAiGoalsToPlan: explicit rows win, clamped", () => {
  const out = applyingAiGoalsToPlan(
    { ...PROFILE, macroPlan: plan() },
    {
      calories: 2500,
      protein: 160,
      carbs: 250,
      fat: 70,
      profiles: [{ id: "t", calories: 2700, proteinG: 175, carbsG: 320, fatG: 80 }],
    },
    applyBase,
  );
  const t = out.macroPlan.profiles.find((p) => p.id === "t");
  assert.equal(t.calories, 2700);
  assert.equal(t.proteinG, 175);
  // untouched profile shifts by the base delta (2500 - effectiveBefore)
  const r = out.macroPlan.profiles.find((p) => p.id === "r");
  const baseBefore = dailyTargets(PROFILE);
  assert.equal(r.calories, 2050 + (2500 - baseBefore.calories));
});

test("applyingAiGoalsToPlan: no rows -> every profile shifts by the base delta", () => {
  const target = dailyTargets(PROFILE);
  const out = applyingAiGoalsToPlan(
    { ...PROFILE, macroPlan: plan() },
    { calories: target.calories + 200, protein: 160, carbs: 250, fat: 70 },
    applyBase,
  );
  const t = out.macroPlan.profiles.find((p) => p.id === "t");
  assert.equal(t.calories, 2600 + 200);
});

test("applyingAiGoalsToPlan: locked base calories skip the plan pass; disabled plan untouched", () => {
  const out = applyingAiGoalsToPlan(
    { ...PROFILE, customCalories: 2200, caloriesLocked: true, macroPlan: plan() },
    { calories: 2500, protein: 160, carbs: 250, fat: 70 },
    (p, r) => ({ ...p, customCalories: 2200, customProtein: r.protein }),
  );
  assert.equal(out.macroPlan.profiles[0].calories, 2600); // unchanged
  const disabled = applyingAiGoalsToPlan(
    { ...PROFILE, macroPlan: plan({ enabled: false }) },
    { calories: 2500, protein: 160, carbs: 250, fat: 70 },
    applyBase,
  );
  assert.equal(disabled.macroPlan.profiles[0].calories, 2600);
});

const BASE = { calories: 2400, proteinG: 150, carbsG: 250, fatG: 70 };

test("updatedJournalEntries: records today, no-op when unchanged", () => {
  const first = updatedJournalEntries({ ...PROFILE, macroPlan: plan() }, [], BASE, TODAY, 1000, "PLAN");
  assert.ok(first);
  assert.equal(first.find((e) => e.date === TODAY).profileId, "t");
  assert.equal(first.find((e) => e.date === TODAY).calories, 2600);
  const again = updatedJournalEntries({ ...PROFILE, macroPlan: plan() }, first, BASE, TODAY, 2000, "PLAN");
  assert.equal(again, null);
});

test("updatedJournalEntries: null while plan off/disabled; frozen past survives", () => {
  assert.equal(updatedJournalEntries({ ...PROFILE, macroPlan: null }, [], BASE, TODAY, 1, "PLAN"), null);
  assert.equal(
    updatedJournalEntries({ ...PROFILE, macroPlan: plan({ enabled: false }) }, [], BASE, TODAY, 1, "PLAN"),
    null,
  );
  const past = [{ date: "2026-08-24", calories: 2600, proteinG: 170, carbsG: 300, fatG: 75, profileId: "t", profileName: "Training day", updatedAtMillis: 1, source: "PLAN" }];
  const out = updatedJournalEntries({ ...PROFILE, macroPlan: plan() }, past, BASE, TODAY, 1000, "PLAN");
  assert.ok(out.some((e) => e.date === "2026-08-24" && e.updatedAtMillis === 1)); // untouched
});

test("updatedJournalEntries gap-fills missing days with GAP_FILL source", () => {
  const seed = upsertJournalEntry([], {
    date: "2026-08-20",
    calories: 2600,
    proteinG: 170,
    carbsG: 300,
    fatG: 75,
    profileId: "t",
    profileName: "Training day",
    updatedAtMillis: 1,
    source: "PLAN",
  }, TODAY);
  const out = updatedJournalEntries({ ...PROFILE, macroPlan: plan() }, seed, BASE, TODAY, 1000, "PLAN");
  const gap = out.find((e) => e.date === "2026-08-21");
  assert.equal(gap.source, "GAP_FILL");
  assert.equal(gap.calories, 2600); // MANUAL default = Training
});
