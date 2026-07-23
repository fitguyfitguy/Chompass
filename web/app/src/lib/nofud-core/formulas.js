// @ts-check
/**
 * Verbatim port of the deterministic formula register in
 * docs/CALCULATION_METHODS.md. Do not re-derive these from a generic
 * nutrition reference — several constants are deliberately non-obvious
 * (PAL "moderate" = 1.465, not 1.55; KCAL_PER_KG_BODY_MASS = 7700, not 7000)
 * and are called out as such in the source doc.
 *
 * If Android's formula register changes, this file must be manually
 * mirrored — there is no shared source of truth between the Kotlin and
 * JS implementations. See docs/CALCULATION_METHODS.md's
 * "Calculation change checklist" before touching either side.
 */

/** @typedef {import('./models.js').UserProfile} UserProfile */

export const KCAL_PER_KG_BODY_MASS = 7700;

/** @type {Record<import('./models.js').ActivityLevel, number>} */
export const PAL_MULTIPLIERS = {
  sedentary: 1.2,
  light: 1.375,
  moderate: 1.465,
  active: 1.55,
  very_active: 1.725,
  extra_active: 1.9,
};

/** @type {Record<import('./models.js').ActivityLevel, number>} */
const PROTEIN_G_PER_KG = {
  sedentary: 0.8,
  light: 1.2,
  moderate: 1.6,
  active: 1.8,
  very_active: 2.0,
  extra_active: 2.2,
};

const DEFAULT_WEEKLY_CHANGE_KG = 0.5;

/**
 * BMR-MSJ: Mifflin-St Jeor. Used when bodyFatPercentage is not set.
 * @param {UserProfile} profile
 */
export function bmrMifflinStJeor(profile) {
  const base = 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.age;
  if (profile.sex === "male") return base + 5;
  return base - 161; // female and other share the same term
}

/**
 * BMR-KM: Katch-McArdle. Used when bodyFatPercentage (fraction 0-1) is set.
 * goalBodyFatPercentage is display-only and never used here.
 * @param {UserProfile} profile
 */
export function bmrKatchMcArdle(profile) {
  const bodyFat = profile.bodyFatPercentage ?? 0;
  const lbmKg = profile.weightKg * (1 - bodyFat);
  return 370 + 21.6 * lbmKg;
}

/** @param {UserProfile} profile */
export function bmr(profile) {
  return profile.bodyFatPercentage != null
    ? bmrKatchMcArdle(profile)
    : bmrMifflinStJeor(profile);
}

/** TDEE: BMR × activity multiplier. @param {UserProfile} profile */
export function tdee(profile) {
  return bmr(profile) * PAL_MULTIPLIERS[profile.activityLevel];
}

/**
 * ACT-EST: estimated daily active burn, and the sedentary budget derived
 * from it (used by the home calorie gauge's Add Active / Dual modes).
 * @param {UserProfile} profile
 * @param {number} effectiveCalories
 */
export function estimatedDailyActiveCalories(profile, effectiveCalories) {
  const estimatedDailyActive = Math.round(tdee(profile) - bmr(profile));
  return {
    estimatedDailyActive,
    sedentaryBudget: effectiveCalories - estimatedDailyActive,
  };
}

/**
 * profile.weeklyChangeKg is an unsigned magnitude (defaults to 0.5 kg/week
 * when unset); the sign is derived from `goal` at calculation time, not
 * stored on the profile (UserProfile.kt dailyCalorieAdjustment).
 * @param {UserProfile} profile
 */
function signedWeeklyChangeKg(profile) {
  const magnitude = profile.weeklyChangeKg ?? DEFAULT_WEEKLY_CHANGE_KG;
  if (profile.goal === "lose") return -magnitude;
  if (profile.goal === "gain") return magnitude;
  return 0;
}

/**
 * CAL-ADJ: goal calorie adjustment and resulting daily calorie target.
 * Mirrors Kotlin's Int arithmetic: both tdee and the adjustment are
 * truncated (not rounded) before summing (UserProfile.kt dailyCalories).
 * @param {UserProfile} profile
 */
export function dailyCalories(profile) {
  const adjustment = Math.trunc((signedWeeklyChangeKg(profile) * KCAL_PER_KG_BODY_MASS) / 7);
  return Math.trunc(tdee(profile)) + adjustment;
}

/**
 * Lean-mass fraction used as the protein calculation basis when body fat %
 * is known, clamped to [0.05, 1.0] (UserProfile.kt proteinBasisWeightKg).
 * @param {UserProfile} profile
 */
function leanMassFraction(profile) {
  if (profile.bodyFatPercentage == null) return null;
  return Math.min(1, Math.max(0.05, 1 - profile.bodyFatPercentage));
}

/**
 * MACRO-P: protein target (g/day), ported verbatim from
 * UserProfile.kt/ActivityLevel.kt including the lean-mass-fraction algebra
 * (dividing the per-kg rate by leanFraction, then multiplying by
 * weight×leanFraction) so Int-truncation lines up with Android bit-for-bit.
 * @param {UserProfile} profile
 */
export function proteinGoal(profile) {
  const cuttingBoost = profile.goal === "lose" ? 0.2 : 0;
  const perKg = PROTEIN_G_PER_KG[profile.activityLevel] + cuttingBoost;
  const leanFrac = leanMassFraction(profile);
  const basisWeight = leanFrac != null ? profile.weightKg * leanFrac : profile.weightKg;
  const multiplier = leanFrac != null ? perKg / leanFrac : perKg;
  const standard = Math.trunc(multiplier * basisWeight);

  if (!profile.ketoMode) return standard;
  const ketoFloor = Math.max(Math.trunc(1.6 * basisWeight), 60);
  return Math.max(standard, ketoFloor);
}

/**
 * MACRO-F standard fat target (g/day): 0.6 g/kg, truncated.
 * @param {UserProfile} profile
 */
export function fatGoalStandard(profile) {
  return Math.trunc(0.6 * profile.weightKg);
}

/**
 * MACRO-C carb target (g/day), standard mode.
 * (dailyCalories - protein*4 - fat*9) / 4, integer-divided (Kotlin Int/Int
 * truncates toward zero), floored at 0.
 * @param {number} calories
 * @param {number} proteinG
 * @param {number} fatG
 */
export function carbGoalStandard(calories, proteinG, fatG) {
  return Math.max(0, Math.trunc((calories - proteinG * 4 - fatG * 9) / 4));
}

/**
 * MACRO-F keto fat target: fills calories remaining after carbs (fixed
 * input, not adjusted) and protein, floored at max(standardFat, 45g).
 * Protein yields (is capped down) to preserve the fat floor when calories
 * are tight, matching UserProfile.kt's `fatGoal` KETO branch exactly.
 * @param {UserProfile} profile
 * @param {number} calories
 * @param {number} proteinG   pre-computed proteinGoal(profile)
 * @param {number} carbsG     pre-computed ketoNetCarbGoal(profile)
 */
export function fatGoalKeto(profile, calories, proteinG, carbsG) {
  const minFat = Math.max(fatGoalStandard(profile), 45);
  const caloriesAfterCarbs = Math.max(0, calories - carbsG * 4);
  const maxProteinForFloorRaw = Math.trunc((caloriesAfterCarbs - minFat * 9) / 4);
  const maxProteinForFloor = Math.max(0, maxProteinForFloorRaw);
  const adjustedProtein = Math.min(proteinG, maxProteinForFloor > 0 ? maxProteinForFloor : proteinG);
  const remainingAfterProtein = Math.max(0, caloriesAfterCarbs - adjustedProtein * 4);
  return Math.max(minFat, Math.trunc(remainingAfterProtein / 9));
}

/**
 * KETO-C: net carb heuristic (g/day), clamped 20-50g.
 * @param {UserProfile} profile
 */
export function ketoNetCarbGoal(profile) {
  const baseline = { lose: 25, maintain: 30, gain: 40 }[profile.goal];

  /** @type {Record<import('./models.js').ActivityLevel, number>} */
  const activityOffset = {
    sedentary: -2,
    light: 0,
    moderate: 2,
    active: 4,
    very_active: 6,
    extra_active: 8,
  };

  let value = baseline + activityOffset[profile.activityLevel];

  const magnitude = profile.weeklyChangeKg ?? DEFAULT_WEEKLY_CHANGE_KG;
  if (profile.goal === "lose" && magnitude >= 0.75) value -= 5;

  if ((profile.bodyFatPercentage ?? 0) >= 0.3) value -= 3;

  return Math.trunc(Math.min(50, Math.max(20, value)));
}

/**
 * Full macro/calorie target bundle for a profile, matching the app's
 * standard vs keto branching (UserProfile.kt dailyCalories/proteinGoal/
 * fatGoal/carbsGoal).
 * @param {UserProfile} profile
 * @returns {{calories: number, proteinG: number, fatG: number, carbsG: number}}
 */
export function dailyTargets(profile) {
  const calories = dailyCalories(profile);
  const proteinG = proteinGoal(profile);

  if (profile.ketoMode) {
    const carbsG = ketoNetCarbGoal(profile);
    const fatG = fatGoalKeto(profile, calories, proteinG, carbsG);
    return { calories, proteinG, fatG, carbsG };
  }

  const fatG = fatGoalStandard(profile);
  const carbsG = carbGoalStandard(calories, proteinG, fatG);
  return { calories, proteinG, fatG, carbsG };
}

/**
 * USNAVY: US Navy body fat % (metric coefficients). Returns null when the
 * result falls outside [2, 65]% or the log domain is invalid (matches the
 * Android rejection rule).
 * @param {{sex: import('./models.js').Sex, waistCm: number, neckCm: number, heightCm: number, hipsCm?: number}} m
 */
export function usNavyBodyFatPercent(m) {
  const log10 = Math.log10;
  let denom;
  if (m.sex === "female") {
    const waistPlusHipsMinusNeck = m.waistCm + (m.hipsCm ?? 0) - m.neckCm;
    if (waistPlusHipsMinusNeck <= 0) return null;
    denom = 1.29579 - 0.35004 * log10(waistPlusHipsMinusNeck) + 0.221 * log10(m.heightCm);
  } else {
    const waistMinusNeck = m.waistCm - m.neckCm;
    if (waistMinusNeck <= 0) return null;
    denom = 1.0324 - 0.19077 * log10(waistMinusNeck) + 0.15456 * log10(m.heightCm);
  }
  if (!Number.isFinite(denom) || denom === 0) return null;
  const percent = 495 / denom - 450;
  if (percent < 2 || percent > 65) return null;
  return percent;
}

/** WHR: waist-to-hip ratio. @param {number} waistCm @param {number} hipsCm */
export function waistToHipRatio(waistCm, hipsCm) {
  return waistCm / hipsCm;
}

/** WTH: waist-to-height ratio. @param {number} waistCm @param {number} heightCm */
export function waistToHeightRatio(waistCm, heightCm) {
  return waistCm / heightCm;
}
