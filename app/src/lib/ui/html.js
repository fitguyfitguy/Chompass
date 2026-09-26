// @ts-check
/**
 * The one HTML escaping story for the whole app.
 *
 * Per-view copies of these helpers drifted into weaker profiles: several
 * dropped `'`, two dropped `&`/`<`, one escaped only `"`. These variants
 * escape everything, which is always safe for text content and for both
 * double- and single-quoted attributes (call sites use both).
 */

/**
 * Escape a value for interpolation into element text content.
 * @param {unknown} s
 * @returns {string}
 */
export function escapeHtml(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) =>
    /** @type {Record<string, string>} */ ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    })[c]
  );
}

/**
 * Escape a value for interpolation into a quoted attribute value.
 * @param {unknown} s
 * @returns {string}
 */
export function escapeAttr(s) {
  return escapeHtml(s);
}
