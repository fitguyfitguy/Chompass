// @ts-check
import { trapFocus } from "./focus-trap.js";

/**
 * @typedef {{
 *   title?: string,
 *   body: string | Node,
 *   onClose?: () => void,
 *   className?: string,
 * }} SheetOptions
 */

/**
 * Open a bottom sheet with scrim. Returns a controller.
 * Supports Escape, scrim click, and vertical drag-to-dismiss (~80px).
 * @param {SheetOptions} opts
 */
export function openSheet(opts) {
  const host = document.createElement("div");
  host.className = `sheet${opts.className ? ` ${opts.className}` : ""}`;
  host.setAttribute("role", "presentation");

  const scrim = document.createElement("div");
  scrim.className = "sheet__scrim";
  scrim.tabIndex = -1;

  const panel = document.createElement("div");
  panel.className = "sheet__panel";
  panel.setAttribute("role", "dialog");
  panel.setAttribute("aria-modal", "true");
  if (opts.title) panel.setAttribute("aria-label", opts.title);

  const handle = document.createElement("div");
  handle.className = "sheet__handle";
  handle.setAttribute("aria-hidden", "true");
  panel.appendChild(handle);

  if (opts.title) {
    const heading = document.createElement("h2");
    heading.className = "sheet__title";
    heading.id = `sheet-title-${Math.random().toString(36).slice(2, 8)}`;
    heading.textContent = opts.title;
    panel.setAttribute("aria-labelledby", heading.id);
    panel.appendChild(heading);
  }

  const bodyWrap = document.createElement("div");
  bodyWrap.className = "sheet__body";
  if (typeof opts.body === "string") bodyWrap.innerHTML = opts.body;
  else bodyWrap.appendChild(opts.body);
  panel.appendChild(bodyWrap);

  host.appendChild(scrim);
  host.appendChild(panel);
  document.body.appendChild(host);
  document.body.classList.add("sheet-open");

  let closed = false;
  let releaseFocus = () => {};

  /** @param {KeyboardEvent} ev */
  const onKey = (ev) => {
    if (ev.key === "Escape") {
      ev.preventDefault();
      dismiss();
    }
  };

  const dismiss = () => {
    if (closed) return;
    closed = true;
    document.removeEventListener("keydown", onKey);
    host.classList.add("is-leaving");
    releaseFocus();
    const done = () => {
      host.remove();
      document.body.classList.remove("sheet-open");
      opts.onClose?.();
    };
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) done();
    else {
      panel.addEventListener("transitionend", done, { once: true });
      setTimeout(done, 320);
    }
  };

  bindDragDismiss(panel, handle, dismiss);

  scrim.addEventListener("click", dismiss);
  document.addEventListener("keydown", onKey);
  requestAnimationFrame(() => host.classList.add("is-open"));
  releaseFocus = trapFocus(panel);

  return {
    el: host,
    panel,
    body: bodyWrap,
    close: dismiss,
  };
}

/**
 * @param {HTMLElement} panel
 * @param {HTMLElement} handle
 * @param {() => void} dismiss
 */
function bindDragDismiss(panel, handle, dismiss) {
  let startY = 0;
  let dy = 0;
  let dragging = false;
  /** @type {number | null} */
  let pointerId = null;

  const desktopSheet = () =>
    typeof window.matchMedia === "function" && window.matchMedia("(min-width: 900px)").matches;

  const setOffset = (y) => {
    panel.style.transition = "none";
    const yPx = Math.max(0, y);
    // Desktop sheets are horizontally centered via translateX(-50%).
    panel.style.transform = desktopSheet()
      ? `translateX(-50%) translateY(${yPx}px)`
      : `translateY(${yPx}px)`;
  };

  const clearOffset = () => {
    panel.style.transition = "";
    panel.style.transform = "";
  };

  /** @param {PointerEvent} ev */
  const onDown = (ev) => {
    if (ev.pointerType === "mouse" && ev.button !== 0) return;
    const fromHandle = handle.contains(/** @type {Node} */ (ev.target));
    if (!fromHandle && panel.scrollTop > 0) return;
    // Ignore interactive controls inside the body unless dragging the handle.
    if (!fromHandle && /** @type {Element} */ (ev.target).closest("button, a, input, textarea, select, label")) {
      return;
    }
    dragging = true;
    pointerId = ev.pointerId;
    startY = ev.clientY;
    dy = 0;
    try {
      panel.setPointerCapture(ev.pointerId);
    } catch {
      /* ignore */
    }
  };

  /** @param {PointerEvent} ev */
  const onMove = (ev) => {
    if (!dragging || ev.pointerId !== pointerId) return;
    dy = ev.clientY - startY;
    if (dy > 8) {
      ev.preventDefault();
      setOffset(dy);
    }
  };

  /** @param {PointerEvent} ev */
  const onUp = (ev) => {
    if (!dragging || ev.pointerId !== pointerId) return;
    dragging = false;
    pointerId = null;
    if (dy > 80) {
      dismiss();
      return;
    }
    clearOffset();
  };

  handle.style.touchAction = "none";
  handle.style.cursor = "grab";
  handle.addEventListener("pointerdown", onDown);
  panel.addEventListener("pointerdown", onDown);
  panel.addEventListener("pointermove", onMove);
  panel.addEventListener("pointerup", onUp);
  panel.addEventListener("pointercancel", onUp);
}
