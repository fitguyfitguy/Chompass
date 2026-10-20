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

/** An estimate row can donate values only when it carries at least one non-zero macro. */
function analysisRowHasMacros(row) {
  return (
    (row.calories ?? 0) !== 0 ||
    (row.proteinG ?? 0) !== 0 ||
    (row.carbsG ?? 0) !== 0 ||
    (row.fatG ?? 0) !== 0
  );
}

/** Substring hit guarded by non-alphanumerics (or string edges) on both sides. */
function containsAtWordBoundary(haystack, needle) {
  let start = 0;
  while (start + needle.length <= haystack.length) {
    const at = haystack.indexOf(needle, start);
    if (at < 0) return false;
    const end = at + needle.length;
    const beforeOk = at === 0 || !isAlnumChar(haystack[at - 1]);
    const afterOk = end === haystack.length || !isAlnumChar(haystack[end]);
    if (beforeOk && afterOk) return true;
    start = at + 1;
  }
  return false;
}

const isAlnumChar = (ch) => /[\p{L}\p{N}]/u.test(ch);

/**
 * Containment fallback for stored↔estimate names the exact pass left
 * unmatched: one normalized name contains the other at a word boundary
 * (Mealie "200 g flour" ↔ AI "flour"; "rice" inside "price" is blocked by
 * the boundary) and the shorter side has ≥ 3 characters.
 */
function namesMatchByContainment(a, b) {
  if (Math.min(a.length, b.length) < 3) return false;
  return a.length >= b.length ? containsAtWordBoundary(a, b) : containsAtWordBoundary(b, a);
}

/**
 * Scan an entry for values the Fill-missing action may ask the AI for.
 * @param {import("./models.js").FoodEntry} entry
 * @returns {EntryGapReport}
 */
export function gapReport(entry) {
  /** @type {string[]} */
  const missingMacros = [];
  // Fields the fill-missing AI explicitly answered (zero macro, null micro)
  // leave the scan — a legit-zero entry completes after one fill instead of
  // re-appearing forever. Local-only tokens mirror the Android set.
  const answered = entry.gapAnsweredFields ?? [];
  if ((entry.calories ?? 0) === 0 && !answered.includes("calories")) missingMacros.push("calories");
  if ((entry.proteinG ?? 0) === 0 && !answered.includes("proteinG")) missingMacros.push("proteinG");
  if ((entry.carbsG ?? 0) === 0 && !answered.includes("carbsG")) missingMacros.push("carbsG");
  if ((entry.fatG ?? 0) === 0 && !answered.includes("fatG")) missingMacros.push("fatG");
  const missingMicros = MICRO_FIELDS.map(([, modelKey]) => modelKey).filter(
    (modelKey) => entry[modelKey] == null && !answered.includes(modelKey),
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
 * null, and all-zero constituent rows matched by normalized name — exact
 * names first, then a word-boundary containment fallback (Mealie "200 g
 * flour" ↔ AI "flour"). Stored
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

  // Explicit answers stamp the field even when they carry no value: a zero
  // macro or a null micro closes its gap (legit-zero entries complete after
  // one fill). Local-only; mirrors FoodEntry.gapAnsweredFields (Android).
  /** @type {Set<string>} */
  const answered = new Set(entry.gapAnsweredFields ?? []);

  let calories = entry.calories ?? 0;
  let proteinG = entry.proteinG ?? 0;
  let carbsG = entry.carbsG ?? 0;
  let fatG = entry.fatG ?? 0;
  if (calories === 0 && !answered.has("calories")) {
    if ((estimate.calories ?? 0) !== 0) {
      calories = Math.round((estimate.calories ?? 0) * gramsFactor);
      changed = true;
    }
    answered.add("calories");
    changed = true;
  }
  if (proteinG === 0 && !answered.has("proteinG")) {
    if ((estimate.proteinG ?? 0) !== 0) {
      proteinG = scaleG(estimate.proteinG ?? 0);
      changed = true;
    }
    answered.add("proteinG");
    changed = true;
  }
  if (carbsG === 0 && !answered.has("carbsG")) {
    if ((estimate.carbsG ?? 0) !== 0) {
      carbsG = scaleG(estimate.carbsG ?? 0);
      changed = true;
    }
    answered.add("carbsG");
    changed = true;
  }
  if (fatG === 0 && !answered.has("fatG")) {
    if ((estimate.fatG ?? 0) !== 0) {
      fatG = scaleG(estimate.fatG ?? 0);
      changed = true;
    }
    answered.add("fatG");
    changed = true;
  }

  // Field-wise micro merge: write only into null fields — never overwrite a
  // stored value with the estimate (or with a null). A null answer is still
  // an answer: stamp the field so the gap closes.
  /** @type {Record<string, number|null>} */
  const mergedMicros = {};
  for (const [, modelKey] of MICRO_FIELDS) {
    const stored = entry[modelKey];
    if (stored != null) {
      mergedMicros[modelKey] = stored;
      continue;
    }
    if (answered.has(modelKey)) continue;
    const incoming = estimate[modelKey];
    if (incoming == null) {
      answered.add(modelKey);
      changed = true;
      continue;
    }
    mergedMicros[modelKey] =
      gramsFactor === 1 ? incoming : Math.max(0, scaleG(incoming));
    answered.add(modelKey);
    changed = true;
  }

  const fillFrom = (row, candidate) => ({
    ...row,
    calories: Math.round((candidate.calories ?? 0) * gramsFactor),
    proteinG: scaleG(candidate.proteinG ?? 0),
    carbsG: scaleG(candidate.carbsG ?? 0),
    fatG: scaleG(candidate.fatG ?? 0),
  });

  const storedConstituents = entry.constituents ?? [];

  // Constituent rows, pass 1: match by normalized name (favoriteKey
  // convention), filling each stored all-zero row from the first unmatched
  // estimate row with the same name. The exact candidate is consumed even
  // when the stored row is not fillable, so pass 2 never re-uses it.
  const analysisRows = estimate.constituents ?? [];
  const analysisTaken = new Array(analysisRows.length).fill(false);
  /** @type {Map<string, number[]>} */
  const analysisQueuesByName = new Map();
  analysisRows.forEach((row, index) => {
    const key = String(row.name ?? "").trim().toLowerCase();
    let queue = analysisQueuesByName.get(key);
    if (!queue) {
      queue = [];
      analysisQueuesByName.set(key, queue);
    }
    queue.push(index);
  });
  /** @type {Map<number, Record<string, any>>} */
  const filledByStoredIndex = new Map();
  storedConstituents.forEach((row, storedIndex) => {
    const exact = analysisQueuesByName.get(row.name.trim().toLowerCase())?.shift();
    if (exact == null) return;
    analysisTaken[exact] = true;
    const candidate = analysisRows[exact];
    if (analysisRowHasMacros(candidate) && isFillableRow(row)) {
      filledByStoredIndex.set(storedIndex, fillFrom(row, candidate));
      changed = true;
    }
  });

  // Pass 2: containment fallback for pairs the exact pass left unmatched —
  // Mealie-style stored names ("200 g flour") never equal the AI's bare
  // name ("flour"). Still-unmatched stored fillable rows × still-unmatched
  // estimate rows, first match in stored-then-analysis order. Unmatched
  // estimate rows are dropped, never appended.
  storedConstituents.forEach((row, storedIndex) => {
    if (filledByStoredIndex.has(storedIndex) || !isFillableRow(row)) return;
    const storedName = row.name.trim().toLowerCase();
    for (let analysisIndex = 0; analysisIndex < analysisRows.length; analysisIndex++) {
      if (analysisTaken[analysisIndex]) continue;
      const candidate = analysisRows[analysisIndex];
      if (!analysisRowHasMacros(candidate)) continue;
      if (!namesMatchByContainment(storedName, String(candidate.name ?? "").trim().toLowerCase())) continue;
      analysisTaken[analysisIndex] = true;
      filledByStoredIndex.set(storedIndex, fillFrom(row, candidate));
      changed = true;
      break;
    }
  });
  const filledConstituents = storedConstituents.map(
    (row, storedIndex) => filledByStoredIndex.get(storedIndex) ?? row,
  );

  if (!changed) return entry;

  return {
    ...entry,
    calories,
    proteinG,
    carbsG,
    fatG,
    constituents: filledConstituents,
    ...mergedMicros,
    gapAnsweredFields: [...answered],
    microsCompositionSignature: microsCompositionSignature(filledConstituents),
  };
}
