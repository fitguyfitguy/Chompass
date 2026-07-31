// @ts-check
// Shared progressive analysis overlay markup (Android EntryAnalysisOverlay parity).
import {
  ANALYSIS_PHASE,
  ANALYSIS_PHASE_LABEL,
  ANALYSIS_PHASE_STEPS,
} from "../ai/food-analyze.js";

/**
 * @param {Object} opts
 * @param {string} opts.phase
 * @param {import('../ai/partial-json.js').PartialFoodEstimate|null} [opts.partial]
 * @param {string[]} [opts.previewUrls]
 * @param {boolean} [opts.showCancel]
 * @returns {string}
 */
export function renderAnalyzeOverlayHtml({
  phase,
  partial = null,
  previewUrls = [],
  showCancel = true,
}) {
  const currentIndex = ANALYSIS_PHASE_STEPS.indexOf(/** @type {any} */ (phase));
  const stepIndex = currentIndex >= 0 ? currentIndex : 0;
  const stepsHtml = ANALYSIS_PHASE_STEPS.map((_step, index) => {
    const done = index < stepIndex;
    const active = index === stepIndex;
    return `
      <div class="analyze-step ${done ? "analyze-step--done" : ""} ${active ? "analyze-step--active" : ""}" aria-hidden="true">
        ${done ? `<span class="analyze-step__check">✓</span>` : active ? `<span class="analyze-step__spin"></span>` : ""}
      </div>
      ${index < ANALYSIS_PHASE_STEPS.length - 1 ? `<div class="analyze-step__rail ${done ? "analyze-step__rail--done" : ""}"></div>` : ""}
    `;
  }).join("");

  const previewHtml =
    previewUrls.length > 1
      ? `<div class="analyze-overlay__thumbs">${previewUrls
          .map((u) => `<img class="analyze-overlay__thumb" src="${escapeAttr(u)}" alt="" />`)
          .join("")}</div>`
      : previewUrls[0]
        ? `<img class="analyze-overlay__preview" src="${escapeAttr(previewUrls[0])}" alt="" />`
        : `<div class="analyze-overlay__icon" aria-hidden="true">⌕</div>`;

  const hasFields = Boolean(partial?.hasAnyField);
  const phaseLabel =
    phase === ANALYSIS_PHASE.CALLING_AI && hasFields
      ? ANALYSIS_PHASE_LABEL.filling_fields
      : ANALYSIS_PHASE_LABEL[phase] || ANALYSIS_PHASE_LABEL[ANALYSIS_PHASE.PREPARING];

  const bodyHtml = hasFields
    ? progressiveCardHtml(partial)
    : `
      <div class="analyze-overlay__spinner" aria-hidden="true"></div>
      ${
        phase === ANALYSIS_PHASE.CALLING_AI
          ? `<p class="analyze-overlay__hint">Waiting for the AI response…</p>`
          : ""
      }
    `;

  return `
    ${previewHtml}
    <div class="analyze-steps">${stepsHtml}</div>
    <p class="analyze-overlay__phase">${escapeHtml(phaseLabel)}</p>
    ${bodyHtml}
    ${
      showCancel
        ? `<button type="button" class="btn btn--ghost analyze-overlay__cancel" data-cancel-analyze>Cancel</button>`
        : ""
    }
  `;
}

/**
 * @param {import('../ai/partial-json.js').PartialFoodEstimate|null|undefined} partial
 */
export function progressiveCardHtml(partial) {
  if (!partial?.hasAnyField) return "";
  const name = partial.name ? escapeHtml(partial.name) : "····";
  const cal = partial.calories != null ? `${partial.calories} kcal` : "··· kcal";
  const macro = (label, value) =>
    value != null
      ? `<div class="analyze-partial__macro">${escapeHtml(label)} ${formatMacro(value)}</div>`
      : `<div class="analyze-partial__macro analyze-partial__macro--pending">${escapeHtml(label)} ···</div>`;
  const serving =
    partial.quantityG != null
      ? `${Math.round(partial.quantityG)} g serving`
      : "Serving size pending…";
  return `
    <div class="analyze-partial card">
      <div class="analyze-partial__name ${partial.name ? "" : "is-pending"}">${name}</div>
      <div class="analyze-partial__cal ${partial.calories != null ? "" : "is-pending"}">${cal}</div>
      ${macro("Protein", partial.proteinG)}
      ${macro("Carbs", partial.carbsG)}
      ${macro("Fat", partial.fatG)}
      <div class="analyze-partial__serving ${partial.quantityG != null ? "" : "is-pending"}">${escapeHtml(serving)}</div>
      ${
        (partial.micronutrientCount ?? 0) > 0
          ? `<div class="analyze-partial__meta">${partial.micronutrientCount} more nutrients found</div>`
          : ""
      }
      ${
        partial.streaming
          ? `<div class="analyze-partial__hint">Values appear as the AI responds. Review them before logging.</div>`
          : ""
      }
    </div>
  `;
}

/** @param {number} v */
function formatMacro(v) {
  const n = Math.round(v * 10) / 10;
  return Number.isInteger(n) ? `${n}g` : `${n.toFixed(1)}g`;
}

function escapeAttr(s) {
  return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}
