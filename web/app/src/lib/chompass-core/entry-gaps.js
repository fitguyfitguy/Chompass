// @ts-check
/**
 * Fill-missing scan + only-missing merge over one food entry. Web twin of
 * android .../models/FoodEntryGaps.kt — keep the two implementations in step.
 *
 * Entry-level macro zeros are the Mealie-import / logged-by-name signature
 * (0 already renders as an em dash, so a fill is display-equivalent to
 * missing → filled). Legit zeros (black coffee) count as gaps by design:
 * the scan never auto-fills, the user drives every fill.
 */

import { MICRO_FIELDS } from "./diary-format.js";
import { microsCompositionSignature } from "./micros-composition.js";

/**
 * @typedef {Object} EntryGapReport
 * @property {string[]} missingMacros   zero-valued entry macros, in "calories" | "proteinG" | "carbsG" | "fatG" order
 * @property {string[]} missingMicros   null/undefined micro model keys, in MICRO_FIELDS order
 * @property {import("./models.js").FoodConstituent[]} zeroMacroConstituents  named all-zero-macro rows
 * @property {boolean} hasGaps          any of the three lists non-empty
 * @property {boolean} hasMacroGaps     macro-level gaps only — the gate for
 *                                      the MASS fill surfaces (Home banner,
 *                                      gaps sheet): with 23 optional micro
 *                                      fields, counting any null here would
 *                                      flag virtually every meal.
 */

/**
 * A constituent row the AI may fill: a named row whose four macros are all
 * zero — the shape recipe imports write for every parsed ingredient. Partial
 * zeros are legit keto-style rows and stay untouched; blank-name rows can
 * never be matched back to an analysis row.
 * @param {import("./models.js").FoodConstituent} row
 */
function isFillableRow(row) {
  return (
    row.name.trim() !== "" &&
    row.calories === 0 &&
    row.proteinG === 0 &&
    row.carbsG === 0 &&
    row.fatG === 0
  );
}

/**
 * Scan an entry for values the Fill-missing action may ask the AI for.
 * @param {import("./models.js").FoodEntry} entry
 * @returns {EntryGapReport}
 */
export function gapReport(entry) {
  /** @type {string[]} */
  const missingMacros = [];
  if ((entry.calories ?? 0) === 0) missingMacros.push("calories");
  if ((entry.proteinG ?? 0) === 0) missingMacros.push("proteinG");
  if ((entry.carbsG ?? 0) === 0) missingMacros.push("carbsG");
  if ((entry.fatG ?? 0) === 0) missingMacros.push("fatG");
  const missingMicros = MICRO_FIELDS.map(([, modelKey]) => modelKey).filter(
    (modelKey) => entry[modelKey] == null,
  );
  const zeroMacroConstituents = (entry.constituents ?? []).filter(isFillableRow);
  return {
    missingMacros,
    missingMicros,
    zeroMacroConstituents,
    hasGaps:
      missingMacros.length > 0 ||
      missingMicros.length > 0 ||
      zeroMacroConstituents.length > 0,
    hasMacroGaps: missingMacros.length > 0 || zeroMacroConstituents.length > 0,
  };
}

/**
 * Merge an AI analysis into an entry, writing ONLY missing values: entry
 * macros where the stored value is zero, micros where the stored field is
 * null, and all-zero constituent rows matched by normalized name. Stored
 * non-zero / non-null values are never overwritten; analysis constituents
 * without a stored name match are dropped, never appended.
 *
 * When entry.quantityG and estimate.quantityG are both known and the
 * estimate grams are positive, every written value is scaled by
 * entryGrams / estimateGrams (micros at 1 decimal, like constituents.js
 * scaledMicros). The micros composition signature is re-stamped after a
 * successful fill; when nothing was fillable the SAME entry object is
 * returned unchanged (identity check the UI relies on).
 *
 * @param {import("./models.js").FoodEntry} entry
 * @param {Record<string, any>} estimate analysis-shaped estimate (analyzeFoodEntry / fillFoodEntry return)
 * @returns {import("./models.js").FoodEntry}
 */
export function fillMissingFrom(entry, estimate) {
  const entryGrams = entry.quantityG ?? null;
  const estimateGrams = estimate.quantityG ?? null;
  const gramsFactor =
    entryGrams != null && estimateGrams != null && estimateGrams > 0
      ? entryGrams / estimateGrams
      : 1;
  /** 1-decimal scale, constituents.js round1 convention. */
  const scaleG = (v) => Math.round(v * gramsFactor * 10) / 10;

  let changed = false;

  let calories = entry.calories ?? 0;
  let proteinG = entry.proteinG ?? 0;
  let carbsG = entry.carbsG ?? 0;
  let fatG = entry.fatG ?? 0;
  if (calories === 0 && (estimate.calories ?? 0) !== 0) {
    calories = Math.round((estimate.calories ?? 0) * gramsFactor);
    changed = true;
  }
  if (proteinG === 0 && (estimate.proteinG ?? 0) !== 0) {
    proteinG = scaleG(estimate.proteinG ?? 0);
    changed = true;
  }
  if (carbsG === 0 && (estimate.carbsG ?? 0) !== 0) {
    carbsG = scaleG(estimate.carbsG ?? 0);
    changed = true;
  }
  if (fatG === 0 && (estimate.fatG ?? 0) !== 0) {
    fatG = scaleG(estimate.fatG ?? 0);
    changed = true;
  }

  // Field-wise micro merge: write only into null fields — never overwrite a
  // stored value with the estimate (or with a null).
  /** @type {Record<string, number|null>} */
  const mergedMicros = {};
  for (const [, modelKey] of MICRO_FIELDS) {
    const stored = entry[modelKey];
    if (stored != null) {
      mergedMicros[modelKey] = stored;
      continue;
    }
    const incoming = estimate[modelKey];
    if (incoming == null) continue;
    mergedMicros[modelKey] =
      gramsFactor === 1 ? incoming : Math.max(0, scaleG(incoming));
    changed = true;
  }

  // Constituent rows: match by normalized name (favoriteKey convention),
  // fill each stored all-zero row from the first unmatched estimate row with
  // the same name — the candidate is consumed even when the stored row is
  // not fillable, mirroring FoodEntryGaps.kt. Unmatched estimate rows are
  // dropped.
  /** @type {Map<string, Record<string, any>[]>} */
  const estimateRowsByName = new Map();
  for (const row of estimate.constituents ?? []) {
    const key = String(row.name ?? "").trim().toLowerCase();
    let queue = estimateRowsByName.get(key);
    if (!queue) {
      queue = [];
      estimateRowsByName.set(key, queue);
    }
    queue.push(row);
  }
  const storedConstituents = entry.constituents ?? [];
  const filledConstituents =
    storedConstituents.length === 0
      ? storedConstituents
      : storedConstituents.map((row) => {
          const candidate = estimateRowsByName
            .get(row.name.trim().toLowerCase())
            ?.shift();
          const candidateHasMacros =
            candidate != null &&
            ((candidate.calories ?? 0) !== 0 ||
              (candidate.proteinG ?? 0) !== 0 ||
              (candidate.carbsG ?? 0) !== 0 ||
              (candidate.fatG ?? 0) !== 0);
          if (candidate != null && candidateHasMacros && isFillableRow(row)) {
            changed = true;
            return {
              ...row,
              calories: Math.round((candidate.calories ?? 0) * gramsFactor),
              proteinG: scaleG(candidate.proteinG ?? 0),
              carbsG: scaleG(candidate.carbsG ?? 0),
              fatG: scaleG(candidate.fatG ?? 0),
            };
          }
          return row;
        });

  if (!changed) return entry;

  return {
    ...entry,
    calories,
    proteinG,
    carbsG,
    fatG,
    constituents: filledConstituents,
    ...mergedMicros,
    microsCompositionSignature: microsCompositionSignature(filledConstituents),
  };
}
