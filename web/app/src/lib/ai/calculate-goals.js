// @ts-check
/**
 * Settings → Recalculate Goals — port of Android FoodAnalysisService.calculateGoals.
 * AI path pins customCalories from the model; without a configured provider the caller
 * should fall back to clearing customCalories (live formula targets).
 */
import { PROVIDERS, resolveProviderModel } from "./providers.js";
import { prefs } from "../db.js";
import { loadProviderKey, listConfiguredProviders } from "./key-storage.js";
import { t } from "../i18n/index.js";
import {
  KCAL_PER_KG_BODY_MASS,
  CALORIE_ABSOLUTE_FLOOR_KCAL,
  CALORIE_PARSER_CEILING_KCAL,
  bmr,
  tdee,
  dailyCalories,
  dailyTargets,
  proteinGoal,
  clampAutoCalories,
  safetyFloorKcal,
} from "../chompass-core/formulas.js";
import {
  activityMultipliersLine,
  proteinPerKgLine,
  calorieAdjustmentLine,
  calorieSafetyLine,
} from "../chompass-core/goal-formula-reference.js";

/** @typedef {import('../chompass-core/models.js').UserProfile} UserProfile */
/** @typedef {ReturnType<import('../chompass-core/forecast.js').computeWeightForecast>} WeightForecast */

/**
 * @typedef {Object} GoalCalculation
 * @property {number} calories
 * @property {number} protein
 * @property {number} carbs
 * @property {number} fat
 * @property {string|null} [reason]
 */

/**
 * Formula calorie target ignoring any pinned customCalories (UserProfile.dailyCalories).
 * @param {UserProfile} profile
 */
export function formulaDailyCalories(profile) {
  return dailyCalories({ ...profile, customCalories: null });
}

/**
 * Live formula targets with customCalories cleared (PWA has no pinned macros).
 * @param {UserProfile} profile
 */
export function recalculatedFromFormulas(profile) {
  return { ...profile, customCalories: null };
}

/**
 * @param {string} text
 * @returns {GoalCalculation}
 */
export function parseGoalCalculation(text) {
  const json = parseJsonObject(text);
  if (!json) throw new Error(t("errors.parse_goal"));
  const calories = intOf(json.calories);
  if (calories == null) throw new Error(t("errors.missing_calories"));
  /** @param {unknown} v @param {number} cap */
  const macro = (v, cap) => Math.min(cap, Math.max(0, intOf(v) ?? 0));
  const reasonRaw = json.reason != null ? String(json.reason).trim() : "";
  return {
    calories: Math.min(CALORIE_PARSER_CEILING_KCAL, Math.max(CALORIE_ABSOLUTE_FLOOR_KCAL, calories)),
    protein: macro(json.protein, 500),
    carbs: macro(json.carbs, 1200),
    fat: macro(json.fat, 400),
    reason: reasonRaw || null,
  };
}

/**
 * @param {Object} args
 * @param {keyof typeof PROVIDERS} args.providerId
 * @param {{apiKey: string, model?: string, baseUrl?: string, reasoningEffort?: string, visionModel?: string}} args.config
 * @param {UserProfile} args.profile
 * @param {WeightForecast|null} [args.forecast]
 * @param {boolean} [args.heightMetric]
 * @param {boolean} [args.weightMetric]
 * @param {AbortSignal} [args.signal]
 * @param {import('../db.js').AppPrefs} [args.prefsOverride] test hook
 * @returns {Promise<GoalCalculation>}
 */
export async function calculateGoalsWithAi({
  providerId,
  config,
  profile,
  forecast = null,
  heightMetric = true,
  weightMetric = true,
  signal,
  prefsOverride,
}) {
  const provider = PROVIDERS[providerId];
  if (!provider) throw new Error(`Unknown AI provider "${providerId}"`);
  const appPrefs = prefsOverride ?? (await prefs.load());
  // Codeberg #20 phase 2: with the master AI switch off, return the
  // deterministic formula targets (same anchor the prompt uses) — no request.
  if (appPrefs.aiFeaturesEnabled === false) {
    const t = dailyTargets(profile);
    return {
      calories: t.calories,
      protein: t.proteinG,
      carbs: t.carbsG,
      fat: t.fatG,
      reason: "Calculated from the built-in formulas.",
    };
  }
  config = { ...config, model: resolveProviderModel(providerId, config.model, "primary") };

  const prompt = buildCalculateGoalsPrompt(profile, forecast, heightMetric, weightMetric);
  let systemPrompt =
    "You are the goal calculator for a calorie & macro tracking app. Reply with ONLY a single JSON object (no markdown).";
  if (appPrefs.userContext?.trim()) {
    systemPrompt += `\n\nUser preferences:\n${appPrefs.userContext.trim()}`;
  }

  const response = await provider.send(config, {
    systemPrompt,
    messages: [{ role: "user", text: prompt }],
    tools: [],
    signal,
  });
  const parsed = parseGoalCalculation(response.text);
  return { ...parsed, calories: clampAutoCalories(parsed.calories, profile) };
}

/**
 * Resolve primary (or first configured) provider + key for goal recalculation.
 * @returns {Promise<{providerId: keyof typeof PROVIDERS, config: {apiKey: string, model?: string, baseUrl?: string}}|null>}
 */
export async function resolveGoalsAiClient() {
  const appPrefs = await prefs.load();
  const configured = await listConfiguredProviders();
  if (!configured.length) return null;
  const preferred =
    appPrefs.primaryAiProvider && configured.includes(/** @type {any} */ (appPrefs.primaryAiProvider))
      ? appPrefs.primaryAiProvider
      : configured[0];
  const providerId = /** @type {keyof typeof PROVIDERS} */ (preferred);
  const config = await loadProviderKey(/** @type {import('./key-storage.js').ProviderId} */ (providerId));
  if (!config?.apiKey) return null;
  return { providerId, config };
}

/**
 * @param {UserProfile} profile
 */
export function lockConstraintsSection(profile) {
  const caloriesLocked = !!profile.caloriesLocked;
  const locked = new Set(profile.lockedMacros || []);
  if (!caloriesLocked && locked.size === 0) return "";
  const lines = ["", "LOCKED TARGETS (the user pinned these; return these exact numbers):"];
  const calories =
    profile.customCalories != null ? Math.trunc(profile.customCalories) : null;
  if (caloriesLocked && calories != null) {
    lines.push(
      `- Calories locked at ${calories} kcal. Return that exact calories value. Choose unlocked macros so 4*protein + 4*carbs + 9*fat is about ${calories}. Do not lower calories to force a bigger deficit.`,
    );
  }
  if (locked.has("protein") && profile.customProtein != null) {
    lines.push(`- Protein locked at ${Math.round(profile.customProtein)} g. Return that exact protein value.`);
  }
  if (locked.has("carbs") && profile.customCarbs != null) {
    lines.push(`- Carbs locked at ${Math.round(profile.customCarbs)} g. Return that exact carbs value.`);
  }
  if (locked.has("fat") && profile.customFat != null) {
    lines.push(`- Fat locked at ${Math.round(profile.customFat)} g. Return that exact fat value.`);
  }
  lines.push("Unlocked macros may change. Locked fields must match the numbers above.");
  return lines.join("\n");
}

/**
 * @param {UserProfile} profile
 * @param {WeightForecast|null} forecast
 * @param {boolean} heightMetric
 * @param {boolean} weightMetric
 */
export function buildCalculateGoalsPrompt(profile, forecast, heightMetric, weightMetric) {
  const weight = weightMetric
    ? `${profile.weightKg.toFixed(1)} kg`
    : `${(profile.weightKg * 2.20462).toFixed(1)} lb`;
  const height = heightMetric
    ? `${Math.round(profile.heightCm)} cm`
    : `${(profile.heightCm / 2.54).toFixed(1)} in`;
  const bodyFat =
    profile.bodyFatPercentage != null ? `${Math.round(profile.bodyFatPercentage * 100)}%` : "not set";
  const goalWeight =
    profile.goalWeightKg != null
      ? weightMetric
        ? `${profile.goalWeightKg.toFixed(1)} kg`
        : `${(profile.goalWeightKg * 2.20462).toFixed(1)} lb`
      : "not set";
  const weekly =
    profile.weeklyChangeKg != null ? `${profile.weeklyChangeKg.toFixed(2)} kg/week` : "not set (maintain)";
  const bmrMethod =
    profile.bodyFatPercentage != null
      ? "Katch-McArdle (body fat known and enabled)"
      : "Mifflin-St Jeor";

  const formulaProfile = { ...profile, customCalories: null };
  const targets = dailyTargets(formulaProfile);
  const activityLine = activityMultipliersLine();
  const proteinLine = proteinPerKgLine();
  const calorieAdjLine = calorieAdjustmentLine();

  const observedSection = buildObservedSection(forecast, weightMetric);
  const dietLine = profile.ketoMode
    ? `- Diet mode: keto (net carbs target ${targets.carbsG} g/day)`
    : "- Diet mode: standard";
  const ketoSection = profile.ketoMode
    ? `\nDIET MODE OVERRIDE: the user follows a KETO diet. Ignore the standard fat/carb formulas above and use these rules instead (they match the app's own keto math):` +
      `\n- Carbs: fixed at the keto net-carb target of ${targets.carbsG} g/day. Do not raise it to fill remaining calories.` +
      `\n- Protein: at least the formula protein below (it already includes the keto floor of 1.6 g/kg lean mass, minimum 60 g).` +
      `\n- Fat: fills the calories remaining after carbs and protein, never below 45 g/day. Fat is the primary energy source.` +
      `\n- Keep 4*protein + 4*carbs + 9*fat approximately equal to calories.`
    : "";

  return `You are the goal calculator for a calorie & macro tracking app. Using the FORMULAS, the USER PROFILE, and any OBSERVED DATA below, compute the user's daily targets.
Return ONLY valid JSON with these exact keys (integers, plus a short reason):
{"calories":2000,"protein":150,"carbs":200,"fat":60,"reason":"Short reason under 100 characters"}

Use the app's formulas as the basis. When OBSERVED DATA is present and reliable, prefer the empirical maintenance estimate it implies over the formula TDEE.
FORMULAS
- BMR (Mifflin-St Jeor): base = 10*weightKg + 6.25*heightCm - 5*age - 161; if male add 166; female/other use base.
- BMR (Katch-McArdle, used when body fat is known and enabled): 370 + 21.6 * (1 - bodyFatFraction) * weightKg.
- TDEE = BMR * activity multiplier. Multipliers: ${activityLine}.
- Calorie target = TDEE + adjustment. adjustment = 0 for maintain; ${calorieAdjLine}.
- Protein: aim NEAR the formula protein value shown below. That value is the activity multiplier (${proteinLine} g/kg; +0.2 if losing) applied to the user's full bodyweight. You may choose a value within about ±15% of it based on the weight goal and the observed history (lean toward the higher end during a calorie deficit to preserve muscle). Do NOT scale protein down just to fit a lower calorie target, except at the safety floor where protein may yield so 4*protein + 4*carbs + 9*fat stays near calories.
- Fat: 0.6 g/kg of full bodyweight.
- Carbs: the calories remaining after protein (4 kcal/g) and fat (9 kcal/g), divided by 4. Keep 4*protein + 4*carbs + 9*fat approximately equal to calories.
BMR method in effect for this user: ${bmrMethod}.
${calorieSafetyLine()}
This user's BMR is ${Math.trunc(bmr(formulaProfile))} kcal; floor is ${safetyFloorKcal(formulaProfile)} kcal. Use integers only. Output no keys other than calories, protein, carbs, fat, reason.

USER PROFILE
- Gender: ${profile.sex}
- Age: ${profile.age}
- Height: ${height}
- Weight: ${weight}
- Body fat: ${bodyFat}
- Activity level: ${profile.activityLevel}
- Weight goal: ${profile.goal}
- Weekly change preference: ${weekly}
- Goal weight: ${goalWeight}
${dietLine}
${ketoSection}
${lockConstraintsSection(profile)}
APP FORMULA REFERENCE (already computed deterministically; use as the anchor)
- BMR: ${Math.trunc(bmr(formulaProfile))} kcal/day
- TDEE: ${Math.trunc(tdee(formulaProfile))} kcal/day
- Formula calorie target: ${targets.calories} kcal/day
- Formula macros: ${Math.round(proteinGoal(formulaProfile))} g protein, ${targets.carbsG} g carbs, ${targets.fatG} g fat
${observedSection}`;
}

/**
 * @param {WeightForecast|null} forecast
 * @param {boolean} weightMetric
 */
function buildObservedSection(forecast, weightMetric) {
  if (!forecast?.hasEnoughData) return "";
  const lines = [
    "",
    "OBSERVED DATA: from the user's OWN logs (prefer this over the formula when reliable):",
  ];
  const loggedAvg =
    forecast.loggedDayAvgCalories > 0 ? forecast.loggedDayAvgCalories : forecast.avgDailyCalories;
  const intakeBasis = forecast.usesCalendarDayAverage
    ? `avg ${loggedAvg} kcal/day across ${forecast.daysOfFoodData} logged days. Sparse coverage: calendar-day average is ${forecast.avgDailyCalories} kcal over ${forecast.calendarDaysInWindow} days — do not use that as recorded intake.`
    : `avg ${loggedAvg} kcal/day across ${forecast.daysOfFoodData} logged days`;
  lines.push(`- Logged intake: ${intakeBasis}`);
  const obs = forecast.observedWeeklyChangeKg;
  if (obs != null) {
    const obsStr = weightMetric
      ? `${obs >= 0 ? "+" : ""}${obs.toFixed(2)} kg/week`
      : `${obs * 2.20462 >= 0 ? "+" : ""}${(obs * 2.20462).toFixed(2)} lb/week`;
    const empiricalTdee = loggedAvg - Math.trunc((obs * KCAL_PER_KG_BODY_MASS) / 7);
    lines.push(`- Observed weight trend: ${obsStr} from ${forecast.weightEntriesUsed} weigh-ins`);
    lines.push(
      `- Implied actual maintenance (logged intake minus the weekly change): ~${empiricalTdee} kcal/day`
    );
  } else {
    lines.push("- Observed weight trend: not enough weigh-ins yet to measure");
  }
  lines.push(`- Formula TDEE for comparison: ${forecast.tdee} kcal/day`);
  if (forecast.trendsDisagree) {
    lines.push(
      "- WARNING: logged intake and the real weight trend DISAGREE. The user is likely under-logging. Trust the weight trend over raw logged calories."
    );
  }
  lines.push(
    `HIT-AND-TRIAL: when this observed data is reliable, estimate true maintenance from intake and the real weight trend, then apply the goal + weekly-change target to THAT maintenance instead of the formula TDEE. If implied actual maintenance is below BMR, discard it and use the formula or measured TDEE. If data is thin or trends disagree, lean on the formula/weight trend accordingly. ${calorieSafetyLine()}`
  );
  return lines.join("\n");
}

/** @param {unknown} v */
function intOf(v) {
  if (typeof v === "number" && Number.isFinite(v)) return Math.round(v);
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v);
    return Number.isFinite(n) ? Math.round(n) : null;
  }
  return null;
}

/** @param {string} text */
function parseJsonObject(text) {
  if (!text) return null;
  const trimmed = text.trim();
  try {
    return JSON.parse(trimmed);
  } catch {
    const match = trimmed.match(/\{[\s\S]*\}/);
    if (!match) return null;
    try {
      return JSON.parse(match[0]);
    } catch {
      return null;
    }
  }
}
