// @ts-check
/**
 * Bounded constituent reconciliation matching
 * docs/benchmarks/food_accuracy/reconcile_constituents.py and
 * android ConstituentReconcile.kt.
 */
import { ALL_MICRO_KEYS } from "../home-nutrients.js";

/** @typedef {import('./models.js').FoodConstituent} FoodConstituent */
/** @typedef {import('./models.js').FoodEntry} FoodEntry */
/** @typedef {import('./serving-units.js').ServingUnitOption} ServingUnitOption */

export const RECONCILE_TOL = 0.05;
export const MAX_REL_ERROR = 0.5;
export const MAX_CONSTITUENTS = 12;

/**
 * @param {number} value
 * @returns {number}
 */
function round1(value) {
  return Math.round(value * 10) / 10;
}

/**
 * Constituent micro fields: AI short snake_case key ↔ model field. 22 keys —
 * caffeine is entry-level only (benchmark CONSTITUENTS_MICRO_JSON_SCHEMA).
 * @type {Array<[string, string]>}
 */
const AI_MICRO_FIELDS = [
  ["sugar", "sugarG"],
  ["added_sugar", "addedSugarG"],
  ["fiber", "fiberG"],
  ["saturated_fat", "saturatedFatG"],
  ["monounsaturated_fat", "monounsaturatedFatG"],
  ["polyunsaturated_fat", "polyunsaturatedFatG"],
  ["cholesterol", "cholesterolMg"],
  ["sodium", "sodiumMg"],
  ["potassium", "potassiumMg"],
  ["trans_fat", "transFatG"],
  ["calcium", "calciumMg"],
  ["iron", "ironMg"],
  ["magnesium", "magnesiumMg"],
  ["zinc", "zincMg"],
  ["vitamin_a", "vitaminAMcg"],
  ["vitamin_c", "vitaminCMg"],
  ["vitamin_d", "vitaminDMcg"],
  ["vitamin_b12", "vitaminB12Mcg"],
  ["vitamin_e", "vitaminEMg"],
  ["vitamin_k", "vitaminKMcg"],
  ["folate", "folateMcg"],
  ["omega_3", "omega3G"],
];

/** Clamp ceiling for micronutrients — mirrors Android InputSanitizer.MAX_MICRO_UNITS. */
export const MAX_MICRO_UNITS = 100_000;

/**
 * Normalized micro value: null when absent or non-finite; otherwise clamped
 * into [0, MAX_MICRO_UNITS]. Android InputSanitizer.micro parity: a negative
 * estimate clamps to 0 instead of dropping the nutrient, absurd magnitudes cap.
 * @param {unknown} v
 * @returns {number|null}
 */
export function microOrNull(v) {
  if (v == null) return null;
  const n = Number(v);
  if (!Number.isFinite(n)) return null;
  return Math.min(Math.max(n, 0), MAX_MICRO_UNITS);
}

/**
 * Copy a row's micros with absent/invalid values normalized to null.
 * @param {Partial<FoodConstituent>} c
 * @returns {Partial<FoodConstituent>}
 */
function microFrom(c) {
  /** @type {Partial<FoodConstituent>} */
  const out = {};
  for (const key of ALL_MICRO_KEYS) {
    out[key] = microOrNull(c[key]);
  }
  return out;
}

/**
 * Scale a row's present micros by a grams factor (per-100g semantics):
 * round to 1dp, clamp >= 0; absent micros stay absent. Factor 1 is the
 * identity — no rounding drift (Android FoodConstituent.microsScaled parity).
 * @param {FoodConstituent} row
 * @param {number} factor
 * @returns {Partial<FoodConstituent>}
 */
function scaledMicros(row, factor) {
  /** @type {Partial<FoodConstituent>} */
  const out = {};
  for (const key of ALL_MICRO_KEYS) {
    const v = row[key];
    if (v == null) continue;
    out[key] = factor === 1 ? v : Math.max(0, round1(v * factor));
  }
  return out;
}

/**
 * @param {number} sum
 * @param {number} total
 * @returns {number|null}
 */
function relError(sum, total) {
  if (total <= 0) return sum === 0 ? null : Number.POSITIVE_INFINITY;
  return Math.abs(sum - total) / total;
}

/**
 * @param {number} sumCal
 * @param {number} sumP
 * @param {number} sumC
 * @param {number} sumF
 * @param {number} mealCal
 * @param {number} mealP
 * @param {number} mealC
 * @param {number} mealF
 * @returns {number|null}
 */
function macroRelError(sumCal, sumP, sumC, sumF, mealCal, mealP, mealC, mealF) {
  const denom = Math.abs(mealCal) + Math.abs(mealP) + Math.abs(mealC) + Math.abs(mealF);
  if (denom <= 0) {
    return sumCal + sumP + sumC + sumF === 0 ? null : Number.POSITIVE_INFINITY;
  }
  const err =
    Math.abs(sumCal - mealCal) +
    Math.abs(sumP - mealP) +
    Math.abs(sumC - mealC) +
    Math.abs(sumF - mealF);
  return err / denom;
}

/**
 * @param {number[]} values
 * @param {number} target
 * @returns {number[]}
 */
function scaleDoubles(values, target) {
  const total = values.reduce((a, b) => a + b, 0);
  if (total <= 0) {
    if (target <= 0) return values.slice();
    const each = target / values.length;
    const out = values.map(() => each);
    out[out.length - 1] = target - each * (values.length - 1);
    return out;
  }
  const factor = target / total;
  const out = values.map((v) => v * factor);
  out[out.length - 1] = target - out.slice(0, -1).reduce((a, b) => a + b, 0);
  return out;
}

/**
 * @param {number[]} values
 * @param {number} target
 * @returns {number[]}
 */
function scaleInts(values, target) {
  const total = values.reduce((a, b) => a + b, 0);
  if (total <= 0) {
    if (target <= 0) return values.slice();
    const each = Math.round(target / values.length);
    const out = values.map(() => each);
    out[out.length - 1] = target - each * (values.length - 1);
    return out;
  }
  const factor = target / total;
  const out = values.map((v) => Math.round(v * factor));
  out[out.length - 1] = target - out.slice(0, -1).reduce((a, b) => a + b, 0);
  return out;
}

/**
 * @param {FoodConstituent} row
 * @param {number} factor
 * @returns {FoodConstituent}
 */
export function scaleConstituent(row, factor) {
  if (factor === 1) return row;
  const grams = row.servingSizeGrams * factor;
  return {
    ...row,
    calories: Math.max(0, Math.round(row.calories * factor)),
    proteinG: row.proteinG * factor,
    carbsG: row.carbsG * factor,
    fatG: row.fatG * factor,
    servingSizeGrams: grams,
    selectedServingQuantity:
      row.selectedServingQuantity != null ? row.selectedServingQuantity * factor : null,
    ...scaledMicros(row, factor),
  };
}

/**
 * @param {FoodConstituent[]} constituents
 * @param {number} factor
 * @returns {FoodConstituent[]}
 */
export function scaleAllConstituents(constituents, factor) {
  if (factor === 1 || !constituents?.length) return constituents || [];
  return constituents.map((c) => scaleConstituent(c, factor));
}

/**
 * @param {FoodConstituent[]} constituents
 * @returns {{calories:number, proteinG:number, carbsG:number, fatG:number, servingSizeGrams:number}|null}
 */
export function aggregatesFromConstituents(constituents) {
  if (!constituents?.length) return null;
  return {
    calories: constituents.reduce((a, c) => a + c.calories, 0),
    proteinG: round1(constituents.reduce((a, c) => a + c.proteinG, 0)),
    carbsG: round1(constituents.reduce((a, c) => a + c.carbsG, 0)),
    fatG: round1(constituents.reduce((a, c) => a + c.fatG, 0)),
    servingSizeGrams: round1(constituents.reduce((a, c) => a + c.servingSizeGrams, 0)),
  };
}

/**
 * @param {{
 *   calories: number,
 *   proteinG: number,
 *   carbsG: number,
 *   fatG: number,
 *   quantityG: number,
 *   constituents?: FoodConstituent[],
 * }} meal
 * @param {number} [maxRelError]
 * @returns {{
 *   calories: number,
 *   proteinG: number,
 *   carbsG: number,
 *   fatG: number,
 *   quantityG: number,
 *   constituents: FoodConstituent[],
 * }}
 */
export function reconcileConstituents(meal, maxRelError = MAX_REL_ERROR) {
  const raw = Array.isArray(meal.constituents) ? meal.constituents : [];
  /** @type {FoodConstituent[]} */
  const rows = [];
  for (const c of raw) {
    if (!c || typeof c.name !== "string" || !c.name.trim()) continue;
    const grams = Number(c.servingSizeGrams);
    const calories = Number(c.calories);
    const proteinG = Number(c.proteinG);
    const carbsG = Number(c.carbsG);
    const fatG = Number(c.fatG);
    if (![grams, calories, proteinG, carbsG, fatG].every((n) => Number.isFinite(n))) continue;
    if (grams <= 0 || calories < 0 || proteinG < 0 || carbsG < 0 || fatG < 0) continue;
    rows.push({
      ...microFrom(c),
      name: c.name.trim(),
      calories,
      proteinG,
      carbsG,
      fatG,
      servingSizeGrams: grams,
      emoji: c.emoji ?? null,
      servingUnitOptions: Array.isArray(c.servingUnitOptions) ? c.servingUnitOptions : [],
      selectedServingUnit: c.selectedServingUnit ?? null,
      selectedServingQuantity: c.selectedServingQuantity ?? null,
    });
    if (rows.length >= MAX_CONSTITUENTS) break;
  }

  if (!rows.length || !(meal.quantityG > 0)) {
    return { ...meal, constituents: [] };
  }

  const sumG = rows.reduce((a, r) => a + r.servingSizeGrams, 0);
  const sumCal = rows.reduce((a, r) => a + r.calories, 0);
  const sumP = rows.reduce((a, r) => a + r.proteinG, 0);
  const sumC = rows.reduce((a, r) => a + r.carbsG, 0);
  const sumF = rows.reduce((a, r) => a + r.fatG, 0);
  const gErr = relError(sumG, meal.quantityG);
  const mErr = macroRelError(
    sumCal,
    sumP,
    sumC,
    sumF,
    meal.calories,
    meal.proteinG,
    meal.carbsG,
    meal.fatG,
  );
  if (gErr == null || mErr == null || gErr > maxRelError || mErr > maxRelError) {
    return { ...meal, constituents: [] };
  }

  const grams = scaleDoubles(
    rows.map((r) => r.servingSizeGrams),
    meal.quantityG,
  );
  const cals = scaleInts(
    rows.map((r) => r.calories),
    Math.round(meal.calories),
  );
  const protein = scaleDoubles(
    rows.map((r) => r.proteinG),
    meal.proteinG,
  );
  const carbs = scaleDoubles(
    rows.map((r) => r.carbsG),
    meal.carbsG,
  );
  const fat = scaleDoubles(
    rows.map((r) => r.fatG),
    meal.fatG,
  );

  /** @type {FoodConstituent[]} */
  let scaled = rows.map((r, i) => ({
    ...r,
    servingSizeGrams: round1(grams[i]),
    calories: cals[i],
    proteinG: round1(protein[i]),
    carbsG: round1(carbs[i]),
    fatG: round1(fat[i]),
    // Micros ride the row's grams factor (per-100g semantics); no residual
    // fixing — macros keep the last-row residual logic below.
    ...scaledMicros(r, grams[i] / r.servingSizeGrams),
  }));

  const head = scaled.slice(0, -1);
  const last = scaled[scaled.length - 1];
  scaled = [
    ...head,
    {
      ...last,
      servingSizeGrams: round1(meal.quantityG - head.reduce((a, r) => a + r.servingSizeGrams, 0)),
      proteinG: round1(meal.proteinG - head.reduce((a, r) => a + r.proteinG, 0)),
      carbsG: round1(meal.carbsG - head.reduce((a, r) => a + r.carbsG, 0)),
      fatG: round1(meal.fatG - head.reduce((a, r) => a + r.fatG, 0)),
      calories: Math.round(meal.calories) - head.reduce((a, r) => a + r.calories, 0),
    },
  ];

  if (
    scaled.some(
      (r) =>
        r.servingSizeGrams <= 0 ||
        r.calories < 0 ||
        r.proteinG < 0 ||
        r.carbsG < 0 ||
        r.fatG < 0,
    )
  ) {
    return { ...meal, constituents: [] };
  }

  return { ...meal, constituents: scaled };
}

/**
 * Apply display-space constituent edits (Android applyConstituentDisplayEdit).
 * Returns cleaned rows, optional aggregate, and total grams.
 * @param {FoodConstituent[]} displayRows
 * @returns {{rows: FoodConstituent[], aggregate: ReturnType<typeof aggregatesFromConstituents>, servingGrams: number}}
 */
export function applyConstituentDisplayEdit(displayRows) {
  const cleaned = (displayRows || []).filter(
    (r) => (typeof r.name === "string" && r.name.trim()) || r.servingSizeGrams > 0,
  );
  const named = cleaned.filter((r) => r.name?.trim() && r.servingSizeGrams > 0);
  const aggregate = aggregatesFromConstituents(named);
  const servingGrams =
    aggregate?.servingSizeGrams ?? cleaned.reduce((a, r) => a + (Number(r.servingSizeGrams) || 0), 0);
  return { rows: cleaned, aggregate, servingGrams };
}

/**
 * Unscale display-space constituent edits back to base rows.
 * @param {FoodConstituent[]} displayRows
 * @param {number} scale
 * @returns {{bases: FoodConstituent[], baseAggregate: ReturnType<typeof aggregatesFromConstituents>, displaySum: number, baseSum: number}}
 */
export function commitConstituentDisplayEdit(displayRows, scale) {
  const { rows: cleaned, servingGrams: displaySum } = applyConstituentDisplayEdit(displayRows);
  const bases =
    scale === 0 || Math.abs(scale - 1) < 1e-9
      ? cleaned
      : scaleAllConstituents(cleaned, 1 / scale);
  const named = bases.filter((r) => r.name?.trim() && r.servingSizeGrams > 0);
  const baseAggregate = aggregatesFromConstituents(named);
  const baseSum =
    baseAggregate?.servingSizeGrams ??
    bases.reduce((a, r) => a + (Number(r.servingSizeGrams) || 0), 0);
  return { bases, baseAggregate, displaySum, baseSum };
}


/**
 * Parse provider JSON constituents (snake_case) into PWA FoodConstituent rows.
 * @param {any} prediction
 * @returns {FoodConstituent[]}
 */
export function parseConstituentsFromPrediction(prediction) {
  if (!prediction || typeof prediction !== "object") return [];
  const raw =
    prediction.constituents ||
    prediction.ingredients ||
    prediction.components ||
    prediction.items;
  if (!Array.isArray(raw)) return [];
  /** @type {FoodConstituent[]} */
  const out = [];
  for (const c of raw) {
    if (!c || typeof c !== "object") continue;
    const name = String(c.name || "").trim();
    if (!name) continue;
    const grams = Number(c.serving_size_grams ?? c.servingSizeGrams ?? c.quantityG);
    const calories = Number(c.calories);
    const proteinG = Number(c.protein ?? c.proteinG);
    const carbsG = Number(c.carbs ?? c.carbsG);
    const fatG = Number(c.fat ?? c.fatG);
    if (![grams, calories, proteinG, carbsG, fatG].every((n) => Number.isFinite(n))) continue;
    if (grams <= 0 || calories < 0 || proteinG < 0 || carbsG < 0 || fatG < 0) continue;
    /** @type {import('./serving-units.js').ServingUnitOption[]} */
    const unitOptions = [];
    const units = c.unit_options || c.servingUnitOptions || [];
    if (Array.isArray(units)) {
      for (const u of units) {
        if (!u || typeof u !== "object") continue;
        const unit = String(u.unit || "").trim();
        const gramsPerUnit = Number(u.grams_per_unit ?? u.gramsPerUnit);
        if (!unit || !(gramsPerUnit > 0)) continue;
        const norm = unit.toLowerCase();
        if (norm === "g" || norm === "gram" || norm === "grams") continue;
        unitOptions.push({
          unit,
          gramsPerUnit,
          quantity: Number(u.quantity) > 0 ? Number(u.quantity) : grams / gramsPerUnit,
        });
        if (unitOptions.length >= 4) break;
      }
    }
    const selected = unitOptions[0] || null;
    /** @type {Partial<FoodConstituent>} */
    const micros = {};
    for (const [aiKey, modelKey] of AI_MICRO_FIELDS) {
      micros[modelKey] = microOrNull(c[aiKey]);
    }
    out.push({
      name,
      calories: Math.round(calories),
      proteinG,
      carbsG,
      fatG,
      ...micros,
      servingSizeGrams: grams,
      emoji: typeof c.emoji === "string" && c.emoji.trim() ? c.emoji.trim() : null,
      servingUnitOptions: unitOptions,
      selectedServingUnit: selected?.unit ?? null,
      selectedServingQuantity: selected
        ? selected.quantity ?? grams / selected.gramsPerUnit
        : null,
    });
    if (out.length >= MAX_CONSTITUENTS) break;
  }
  return out;
}
