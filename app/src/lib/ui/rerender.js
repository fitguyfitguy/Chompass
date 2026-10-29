// @ts-check
/**
 * Snapshot/restore pair for the house full-re-render idiom
 * (`this.innerHTML = …` + rebind): keeps the focused element, its text
 * selection, and the scroll position (window + element) stable across a
 * rebuild instead of dropping focus to <body> and jumping to the top.
 *
 *   const rr = captureRerender(this);
 *   this.innerHTML = html;
 *   rebind();
 *   rr.restore();
 *
 * The snapshot is synchronous; restore() is idempotent and safe to skip
 * when a render bailed out before touching the DOM. The focused element
 * is re-found by id, falling back to its first data-* attribute (unique
 * per field in this codebase's templates).
 *
 * @param {HTMLElement} el
 */
export function captureRerender(el) {
  const active = document.activeElement;
  const focused =
    active instanceof Node && el.contains(active) && active instanceof HTMLElement
      ? active
      : null;
  /** @type {string | null} */
  let selector = null;
  /** @type {number | null} */
  let caretStart = null;
  /** @type {number | null} */
  let caretEnd = null;
  if (focused) {
    if (focused.id) {
      selector = `#${CSS.escape(focused.id)}`;
    } else {
      const dataAttr = [...focused.attributes].find((a) => a.name.startsWith("data-"));
      if (dataAttr) selector = `[${dataAttr.name}="${CSS.escape(dataAttr.value)}"]`;
    }
    if (focused instanceof HTMLInputElement || focused instanceof HTMLTextAreaElement) {
      try {
        caretStart = focused.selectionStart;
        caretEnd = focused.selectionEnd;
      } catch {
        /* input types without a selection API */
      }
    }
  }
  const scrollY = window.scrollY;
  const elScroll = el.scrollTop;
  let done = false;
  return {
    /** Refocuses the previously focused element (with caret) and restores scroll. */
    restore() {
      if (done) return;
      done = true;
      if (window.scrollY !== scrollY) window.scrollTo(0, scrollY);
      if (el.scrollTop !== elScroll) el.scrollTop = elScroll;
      if (!selector) return;
      const next = /** @type {HTMLElement | null} */ (el.querySelector(selector));
      if (!next) return;
      next.focus({ preventScroll: true });
      if (
        caretStart != null &&
        (next instanceof HTMLInputElement || next instanceof HTMLTextAreaElement)
      ) {
        try {
          next.setSelectionRange(caretStart, caretEnd ?? caretStart);
        } catch {
          /* input types without a selection API */
        }
      }
    },
  };
}
