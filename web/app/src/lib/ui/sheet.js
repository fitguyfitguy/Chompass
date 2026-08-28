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

// Back-gesture support: every open sheet pushes a same-URL sentinel history
// entry. The browser/Android back button then pops the sentinel (dismissing
// the sheet) instead of navigating — or exiting the PWA — underneath it.
const SHEET_HISTORY_FLAG = "chompassSheet";
let sheetSeq = 0;
/** @type {Map<number, () => void>} */
const liveSheetBacks = new Map();
let skipNextSheetPop = 0;
let sheetPopBound = false;

/** @param {unknown} state @returns {number | null} */
function sheetIdFromState(state) {
  if (state && typeof state === "object" && !Array.isArray(state)) {
    const id = /** @type {Record<string, unknown>} */ (state)[SHEET_HISTORY_FLAG];
    if (typeof id === "number") return id;
  }
  return null;
}

/** @param {PopStateEvent} ev */
function onSheetPopState(ev) {
  const id = sheetIdFromState(ev.state);
  const landedLive = id !== null && liveSheetBacks.has(id);
  if (skipNextSheetPop > 0) {
    // A traversal we scheduled ourselves (consuming or skipping a sentinel).
    // Never dismiss on arrival, but keep skipping further stale sentinels.
    skipNextSheetPop -= 1;
    if (id !== null && !landedLive) {
      skipNextSheetPop += 1;
      history.back();
    }
    return;
  }
  if (id !== null && !landedLive) {
    // Stale sentinel: its sheet already closed while a navigation landed on
    // top. Skip the dead entry instead of eating this back press.
    skipNextSheetPop += 1;
    history.back();
    return;
  }
  // Landed on a route entry or on a parent sheet's sentinel. Sentinels sit
  // above everything they were pushed over, so the entry just left was the
  // top sheet's sentinel: back dismissed the top sheet.
  dismissTopSheet();
}

/** Dismiss the most recently opened live sheet (Map order = open order). */
function dismissTopSheet() {
  let top;
  for (const back of liveSheetBacks.values()) top = back;
  top?.();
}

/**
 * Push a history sentinel for a sheet. Returns a teardown that consumes the
 * sentinel when the sheet closes any other way (Escape, scrim, action). The
 * consume is deferred and state-guarded so a navigation fired in the same
 * tick (sheet action → location.hash) wins, leaving any orphaned sentinel to
 * the stale-skip in onSheetPopState.
 * @param {() => void} onBack
 * @returns {() => void}
 */
function bindSheetHistory(onBack) {
  const id = ++sheetSeq;
  try {
    history.pushState({ [SHEET_HISTORY_FLAG]: id }, "");
  } catch {
    // Sandboxed contexts without history access: Escape/scrim still dismiss.
    return () => {};
  }
  if (!sheetPopBound) {
    window.addEventListener("popstate", onSheetPopState);
    sheetPopBound = true;
  }
  liveSheetBacks.set(id, onBack);
  let released = false;
  return () => {
    if (released) return;
    released = true;
    liveSheetBacks.delete(id);
    setTimeout(() => {
      if (sheetIdFromState(history.state) === id) {
        skipNextSheetPop += 1;
        history.back();
      }
    }, 0);
  };
}

/**
 * Open a bottom sheet with scrim. Returns a controller.
 * Dismissal: Escape, scrim click, vertical drag-to-dismiss (~80px), and the
 * browser/Android back gesture (back never navigates underneath an open
 * sheet).
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
  let releaseHistory = () => {};
  let unbindDrag = () => {};

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
    releaseHistory();
    unbindDrag();
    document.removeEventListener("keydown", onKey);
    host.classList.add("is-leaving");
    releaseFocus();
    let finished = false;
    const done = () => {
      if (finished) return; // transitionend and the 320ms fallback both land here
      finished = true;
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

  unbindDrag = bindDragDismiss(panel, handle, dismiss);

  scrim.addEventListener("click", dismiss);
  document.addEventListener("keydown", onKey);
  requestAnimationFrame(() => host.classList.add("is-open"));
  releaseFocus = trapFocus(panel);
  releaseHistory = bindSheetHistory(dismiss);

  return {
    el: host,
    panel,
    body: bodyWrap,
    close: dismiss,
  };
}

/**
 * Drag-to-dismiss. The handle always drags; the panel body drags when its
 * content is scrolled to the top — including drags that start on buttons and
 * links (the Add Food sheet is almost entirely buttons), where a committed
 * drag suppresses the trailing click and a gesture under the ~10px slop
 * still taps. Inputs keep their own gestures, and with content scrolled a
 * vertical drag means "scroll", not "dismiss".
 * @param {HTMLElement} panel
 * @param {HTMLElement} handle
 * @param {() => void} dismiss
 * @returns {() => void} teardown removing window-level listeners
 */
function bindDragDismiss(panel, handle, dismiss) {
  const SLOP_PX = 10;
  const DISMISS_PX = 80;
  let pointerId = null;
  let startX = 0;
  let startY = 0;
  let dy = 0;
  let dragging = false;
  let suppressClick = false;

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
    suppressClick = false;
    if (ev.pointerType === "mouse" && ev.button !== 0) return;
    if (pointerId !== null) return; // one gesture at a time
    const fromHandle = handle.contains(/** @type {Node} */ (ev.target));
    if (!fromHandle) {
      // Text fields, sliders and selects own their gestures; never hijack.
      if (/** @type {Element} */ (ev.target).closest("input, textarea, select")) return;
      // With content scrolled up, a vertical drag is a scroll gesture.
      // Swipe-to-dismiss applies at the top of the content (or the handle).
      if (panel.scrollTop > 0) return;
    }
    pointerId = ev.pointerId;
    startX = ev.clientX;
    startY = ev.clientY;
    dy = 0;
  };

  /** @param {PointerEvent} ev */
  const onMove = (ev) => {
    if (pointerId === null || ev.pointerId !== pointerId) return;
    if (!dragging) {
      const dyNow = ev.clientY - startY;
      // Under the slop it is still a tap; horizontal intent wins over a
      // downward drag. Only past that does the pointer commit to the dismiss
      // gesture (at scrollTop 0 a downward drag cannot scroll anyway).
      if (dyNow <= SLOP_PX || dyNow <= Math.abs(ev.clientX - startX)) return;
      dragging = true;
      suppressClick = true;
      try {
        panel.setPointerCapture(pointerId);
      } catch {
        /* ignore */
      }
    }
    dy = ev.clientY - startY;
    ev.preventDefault();
    setOffset(dy);
  };

  /** @param {PointerEvent} ev */
  const onUp = (ev) => {
    if (pointerId === null || ev.pointerId !== pointerId) return;
    pointerId = null;
    if (!dragging) return;
    dragging = false;
    if (dy > DISMISS_PX) {
      dismiss();
      return;
    }
    clearOffset();
    // suppressClick stays armed: the click lands after pointerup.
  };

  const onCancel = () => {
    if (pointerId === null) return;
    pointerId = null;
    dragging = false;
    suppressClick = false;
    clearOffset();
  };

  // Safety net: a gesture released (or canceled) off the panel must still
  // reset the tracking state, or every later pointerdown is dropped.
  /** @param {PointerEvent} ev */
  const onEndAnywhere = (ev) => {
    if (pointerId === null || ev.pointerId !== pointerId) return;
    pointerId = null;
    if (dragging) {
      dragging = false;
      clearOffset();
    }
  };

  // A committed drag must never end in an accidental tap on a button.
  panel.addEventListener(
    "click",
    (ev) => {
      if (!suppressClick) return;
      suppressClick = false;
      ev.preventDefault();
      ev.stopPropagation();
    },
    true
  );

  handle.style.touchAction = "none";
  handle.style.cursor = "grab";
  handle.addEventListener("pointerdown", onDown);
  panel.addEventListener("pointerdown", onDown);
  panel.addEventListener("pointermove", onMove);
  panel.addEventListener("pointerup", onUp);
  panel.addEventListener("pointercancel", onCancel);
  window.addEventListener("pointerup", onEndAnywhere);
  window.addEventListener("pointercancel", onEndAnywhere);

  return () => {
    window.removeEventListener("pointerup", onEndAnywhere);
    window.removeEventListener("pointercancel", onEndAnywhere);
  };
}
