// @ts-check
// Analysis-phase constants + abort check, split out of food-analyze.js so UI
// modules (analyze-view, entry-form, analyze-overlay, photo-ai-flow) can show
// the streaming overlay without pulling the whole AI request stack into the
// bundle. food-analyze.js re-exports these for backwards compatibility.
export const ANALYSIS_PHASE = Object.freeze({
  PREPARING: "preparing",
  LOOKING_UP_BARCODE: "looking_up_barcode",
  CALLING_AI: "calling_ai",
  PARSING: "parsing",
});

/** @type {Record<string, string>} */
export const ANALYSIS_PHASE_LABEL = Object.freeze({
  [ANALYSIS_PHASE.PREPARING]: "Preparing request…",
  [ANALYSIS_PHASE.LOOKING_UP_BARCODE]: "Checking barcodes…",
  [ANALYSIS_PHASE.CALLING_AI]: "Calling AI…",
  [ANALYSIS_PHASE.PARSING]: "Reading result…",
  filling_fields: "Filling in nutrition…",
});

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
