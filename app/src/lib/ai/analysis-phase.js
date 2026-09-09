// @ts-check
// Analysis-phase constants + abort check, split out of food-analyze.js so UI
// modules (analyze-view, entry-form, analyze-overlay, photo-ai-flow) can show
// the streaming overlay without pulling the whole AI request stack into the
// bundle. food-analyze.js re-exports these for backwards compatibility.
import { t } from "../i18n/index.js";

export const ANALYSIS_PHASE = Object.freeze({
  PREPARING: "preparing",
  LOOKING_UP_BARCODE: "looking_up_barcode",
  CALLING_AI: "calling_ai",
  PARSING: "parsing",
});

const PHASE_LABEL_KEYS = Object.freeze({
  [ANALYSIS_PHASE.PREPARING]: "analysis.phase.preparing",
  [ANALYSIS_PHASE.LOOKING_UP_BARCODE]: "analysis.phase.barcode",
  [ANALYSIS_PHASE.CALLING_AI]: "analysis.phase.calling_ai",
  [ANALYSIS_PHASE.PARSING]: "analysis.phase.parsing",
  filling_fields: "analysis.phase.filling",
});

/**
 * Localized label for an analysis phase (resolved at call time so a locale
 * activated after module load is honored). Falls back to English via t().
 * @param {string} phase
 * @returns {string}
 */
export function phaseLabel(phase) {
  return t(PHASE_LABEL_KEYS[phase] || PHASE_LABEL_KEYS[ANALYSIS_PHASE.PREPARING]);
}

/** Ordered steps for the analyze overlay (non-grounded). */
export const ANALYSIS_PHASE_STEPS = Object.freeze([
  ANALYSIS_PHASE.PREPARING,
  ANALYSIS_PHASE.LOOKING_UP_BARCODE,
  ANALYSIS_PHASE.CALLING_AI,
  ANALYSIS_PHASE.PARSING,
]);

/** @param {unknown} err */
export function isAbortError(err) {
  if (!err || typeof err !== "object") return false;
  const name = /** @type {{name?: string}} */ (err).name;
  return name === "AbortError";
}
