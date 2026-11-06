// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { clampNoteText } from "../clamp-note.js";
import { dailyTargets } from "../formulas.js";

const base = {
  sex: "male", age: 30, heightCm: 180, weightKg: 80,
  activityLevel: "moderate", goal: "maintain",
};

test("clampNote unclamped maintain returns null", () => {
  const p = { ...base };
  assert.equal(clampNoteText(p, dailyTargets(p)), null);
});

test("clampNote aggressive deficit clamps up to resting calories", () => {
  // TDEE ~2608, 1.5 kg/wk lose = -1650 → raw ~958 < BMR 1780.
  const p = { ...base, goal: "lose", weeklyChangeKg: 1.5 };
  const targets = dailyTargets(p);
  assert.ok(targets.calories > Math.trunc(2608 - 1650));
  const note = clampNoteText(p, targets);
  assert.ok(note != null);
  assert.ok(note.startsWith("This pace isn't possible"));
  assert.ok(note.includes("1,780 kcal"));
});

test("clampNote pinned below floor shows doctor note", () => {
  const p = { ...base, customCalories: 900 };
  const targets = dailyTargets(p);
  const note = clampNoteText(p, targets);
  assert.ok(note != null);
  assert.ok(note.startsWith("Please consult with a doctor."));
});

test("clampNote formats kJ when requested", () => {
  const p = { ...base, goal: "lose", weeklyChangeKg: 1.5 };
  const note = clampNoteText(p, dailyTargets(p), "kj");
  assert.ok(note != null && note.includes("kJ"));
});
