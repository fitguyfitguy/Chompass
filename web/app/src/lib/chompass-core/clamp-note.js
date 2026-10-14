// @ts-check
/**
 * CAL-SAFE clamp copy shared by onboarding and Settings → Goals — mirrors the
 * Android budget dialog / recalc sheet clamp rows. English hardcoded: PWA
 * precedent for safety copy (see docs/PARITY.md). Pure helper, node-testable.
 */
import { tdee, calorieAdjustment, safetyFloorKcal } from "./formulas.js";

/** @typedef {import('./models.js').UserProfile} UserProfile */

/**
 * @param {number} kcal
 * @param {"kcal"|"kj"} [unit]
 * @returns {string}
 */
function fmtEnergy(kcal, unit = "kcal") {
  const q = unit === "kj" ? Math.round(kcal * 4.184) : kcal;
  return `${new Intl.NumberFormat("en-US").format(q)} ${unit === "kj" ? "kJ" : "kcal"}`;
}

/**
 * Message body for the active CAL-SAFE clamp, or null when the effective
 * target is the unclamped formula output. `targets` is dailyTargets(profile);
 * the raw formula target is recomputed with customCalories nulled, so a manual
 * pin above the safety floor reads as clamped up and a pin below the floor
 * shows the doctor note.
 * @param {UserProfile} profile
 * @param {{calories: number}} targets
 * @param {"kcal"|"kj"} [unit]
 * @returns {string|null}
 */
export function clampNoteText(profile, targets, unit = "kcal") {
  const formulaDraft = { ...profile, customCalories: null };
  const raw = Math.trunc(tdee(formulaDraft)) + calorieAdjustment(formulaDraft);
  if (raw < targets.calories) {
    return `This pace isn't possible at your size without going below your estimated resting calories. Your target will be ${fmtEnergy(targets.calories, unit)}.`;
  }
  if (targets.calories < safetyFloorKcal(profile)) {
    return `Please consult with a doctor. This is below your estimated resting calories or ${fmtEnergy(1200, unit)} a day.`;
  }
  return null;
}
