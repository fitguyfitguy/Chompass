// @ts-check
/**
 * Canonical formula text fragments for AI goal prompts — mirrors Android
 * GoalFormulaReference.kt. Must stay aligned with formulas.js constants and
 * testdata/parity/goal-formula-prompt-fragments.json.
 */
import { PAL_MULTIPLIERS, PROTEIN_G_PER_KG, KCAL_PER_KG_BODY_MASS, CALORIE_ABSOLUTE_FLOOR_KCAL } from "./formulas.js";

/** @param {number} value */
function formatMultiplier(value) {
  return value % 1 === 0 ? String(value) : String(value);
}

export function activityMultipliersLine() {
  return Object.entries(PAL_MULTIPLIERS)
    .map(([k, v]) => `${k} ${formatMultiplier(v)}`)
    .join(", ");
}

export function proteinPerKgLine() {
  return Object.entries(PROTEIN_G_PER_KG)
    .map(([k, v]) => `${k} ${formatMultiplier(v)}`)
    .join(", ");
}

export function calorieAdjustmentLine() {
  const kcalPerKg = Math.trunc(KCAL_PER_KG_BODY_MASS);
  return `lose: -(weeklyChangeKg*${kcalPerKg}/7); gain: +(weeklyChangeKg*${kcalPerKg}/7)`;
}

export function moderateActivityMultiplierRationale() {
  const moderate = formatMultiplier(PAL_MULTIPLIERS.moderate);
  return (
    `Moderate uses ${moderate} (between FAO/WHO light 1.375 ` +
    `and moderate 1.55) for desk-active users who exercise a few times per week.`
  );
}

export function calorieSafetyLine() {
  return (
    `Never set calories below this user's BMR or ${CALORIE_ABSOLUTE_FLOOR_KCAL}. ` +
    `If the weekly pace would break that floor, shrink the deficit instead of lowering the floor. ` +
    `800 kcal is a medically supervised VLCD, not an app target.`
  );
}
