// @ts-check
/**
 * The Chompass mark, transcribed once for the PWA.
 *
 * Geometry, draw order and motion come from `docs/brand/logo-system.html` (the
 * study) and match the Android Canvas (`ui/components/ChompassMark.kt`) and the
 * generated launcher/SVG assets (`scripts/assets/build_icon_mark.py`). Colours
 * stay in CSS: `--mark-ring`, `--mark-needle-a`, `--mark-needle-b`, `--mark-hub`
 * and `--mark-pin` are the light mark on cream and the glow variant on plum, so
 * the same markup works on both grounds.
 */

/** Ring + needle + wordmark geometry in the study's 200-unit box. */
export const BRAND_MARK_GEOMETRY = Object.freeze({
  box: 200,
  ring: "M171.7 106.3A72 72 0 1 1 118.6 30.5A34 34 0 0 0 159 58.7A34 34 0 0 0 171.7 106.3Z",
  needleA: "M151.6 63.9L104.3 106.1L83.6 111.5Z",
  needleB: "M151.6 63.9L83.6 111.5L95.7 93.9Z",
  hubRadius: 7,
  pinRadius: 2.5,
  /** Lowercase "o" counter: 60-unit box, 8-unit stroke. */
  biteO: "M54.15 23.53A25 25 0 1 1 40.57 7.34A9 9 0 0 0 41.49 20.36A9 9 0 0 0 54.15 23.53Z",
  biteOBox: 60,
  biteOStroke: 8,
});

/**
 * Mark SVG.
 *
 * `intro` plays the study's 1.6 s first-run sequence (ring draw, three needle
 * turns, chomp) before the idle sway; `idle` keeps the needle swaying and the
 * mark floating. Both stop under `prefers-reduced-motion` (see main.css).
 *
 * @param {{ intro?: boolean, idle?: boolean, label?: string, className?: string }} [options]
 * @returns {string}
 */
export function brandMarkSvg(options = {}) {
  const { intro = false, idle = true, label = "", className = "" } = options;
  const g = BRAND_MARK_GEOMETRY;
  const classes = ["brand-mark", intro ? "brand-mark--intro" : "", idle ? "brand-mark--idle" : "", className]
    .filter(Boolean)
    .join(" ");
  const a11y = label ? `role="img" aria-label="${escapeAttr(label)}"` : 'aria-hidden="true" focusable="false"';
  return `<svg class="${classes}" viewBox="0 0 ${g.box} ${g.box}" ${a11y}>
    <g class="brand-mark__dial">
      <path class="brand-mark__ring" pathLength="1" d="${g.ring}" />
    </g>
    <g class="brand-mark__sway">
      <g class="brand-mark__needle">
        <path class="brand-mark__needle-a" d="${g.needleA}" />
        <path class="brand-mark__needle-b" d="${g.needleB}" />
        <circle class="brand-mark__hub" cx="100" cy="100" r="${g.hubRadius}" />
        <circle class="brand-mark__pin" cx="100" cy="100" r="${g.pinRadius}" />
      </g>
    </g>
  </svg>`;
}

/**
 * The bitten-"o" wordmark. The "o" keeps the fixed brand peach in both schemes
 * (never the accent) — the study's guardrail: plum there would over-explain it.
 *
 * @param {string} [className]
 * @returns {string}
 */
export function brandWordmark(className = "") {
  const g = BRAND_MARK_GEOMETRY;
  const classes = ["wordmark", className].filter(Boolean).join(" ");
  return `<span class="${classes}" role="img" aria-label="Chompass"><span aria-hidden="true">ch<svg class="wordmark__o" viewBox="0 0 ${g.biteOBox} ${g.biteOBox}" focusable="false"><path d="${g.biteO}" stroke-width="${g.biteOStroke}" /></svg>mp<span class="wordmark__tail">ass</span></span></span>`;
}

/**
 * Escape a value for a quoted attribute.
 * @param {unknown} value
 * @returns {string}
 */
function escapeAttr(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) =>
    /** @type {Record<string, string>} */ ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    })[c]
  );
}
