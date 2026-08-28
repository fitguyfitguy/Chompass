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

  unbindDrag = bindDragDismiss(panel, handle, bodyWrap, dismiss);

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
 * Drag-to-dismiss. The panel shell (handle, title, padding — everything
 * outside the scrollable body) is touch-action: none, so drags there always
 * work, fast or slow. Drags starting inside the scrollable body dismiss when
 * its content is at the top — including drags that start on buttons and
 * links (the Add Food sheet is almost entirely buttons), where a committed
 * drag suppresses the trailing click and a gesture under the claim threshold
 * still taps. Inputs keep their own gestures, and with content scrolled a
 * vertical drag means "scroll", not "dismiss".
 *
 * Touch drags inside the scroller are tracked with a non-passive touchmove +
 * preventDefault: a pointer-move tracker loses the race against the
 * browser's scroll detector, which starts a scroll session once the ~8px
 * touch slop is crossed and cancels the pointers. The claim threshold sits
 * below that slop, so the drag wins it. Mouse drags keep pointer events.
 * @param {HTMLElement} panel
 * @param {HTMLElement} handle
 * @param {HTMLElement} scroller the scrollable body inside the panel
 * @param {() => void} dismiss
 * @returns {() => void} teardown removing window-level listeners
 */
function bindDragDismiss(panel, handle, scroller, dismiss) {
  const CLAIM_PX = 6; // must stay below the browser's ~8px scroll slop
  const DISMISS_PX = 80;
  let mouseId = null; // pointerId while a mouse drag is tracked
  let touchId = null; // touch identifier while a touch drag is tracked
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

  /**
   * @param {EventTarget} target
   * @returns {boolean} true when a gesture starting here may become a drag
   */
  const dragEligible = (target) => {
    if (handle.contains(/** @type {Node} */ (target))) return true;
    // The panel shell (handle, title, padding) is touch-action: none — the
    // browser can never claim gestures there, so they always drag.
    if (!scroller.contains(/** @type {Node} */ (target))) return true;
    // Text fields, sliders and selects own their gestures; never hijack.
    if (/** @type {Element} */ (target).closest("input, textarea, select")) return false;
    // With content scrolled up, a vertical drag is a scroll gesture.
    // Swipe-to-dismiss applies at the top of the content.
    return scroller.scrollTop === 0;
  };

  /** @param {TouchList} touches @returns {Touch | null} */
  const trackedTouch = (touches) => {
    if (touchId === null) return null;
    for (let i = 0; i < touches.length; i += 1) {
      if (touches[i].identifier === touchId) return touches[i];
    }
    return null;
  };

  /** @param {TouchEvent} ev */
  const onTouchStart = (ev) => {
    suppressClick = false;
    if (touchId !== null || mouseId !== null) return; // one gesture at a time
    const touch = ev.changedTouches[0];
    if (!touch || !dragEligible(ev.target)) return;
    touchId = touch.identifier;
    startX = touch.clientX;
    startY = touch.clientY;
    dy = 0;
  };

  /** @param {TouchEvent} ev */
  const onTouchMove = (ev) => {
    const touch = trackedTouch(ev.changedTouches);
    if (!touch) return;
    const dyNow = touch.clientY - startY;
    const dxNow = touch.clientX - startX;
    if (!dragging) {
      // Under the claim threshold it is still a tap; horizontal intent wins
      // over a downward drag.
      if (dyNow <= CLAIM_PX || dyNow <= Math.abs(dxNow)) return;
      dragging = true;
      suppressClick = true;
    }
    // Claim the gesture before the scroll detector can: at scrollTop 0 a
    // downward drag cannot scroll anyway, and once the scroll session starts
    // the browser cancels the touch and the drag is dead.
    ev.preventDefault();
    dy = dyNow;
    setOffset(dy);
  };

  /** @param {TouchEvent} ev */
  const onTouchEnd = (ev) => {
    if (touchId === null || !trackedTouch(ev.changedTouches)) return;
    touchId = null;
    if (!dragging) return;
    dragging = false;
    if (dy > DISMISS_PX) {
      dismiss();
      return;
    }
    clearOffset();
    // suppressClick stays armed: the click lands after the touch ends.
  };

  const onTouchCancel = () => {
    if (touchId === null) return;
    touchId = null;
    dragging = false;
    suppressClick = false;
    clearOffset();
  };

  /** @param {PointerEvent} ev */
  const onMouseDown = (ev) => {
    suppressClick = false;
    if (ev.pointerType !== "mouse" || ev.button !== 0) return;
    if (mouseId !== null || touchId !== null) return; // one gesture at a time
    if (!dragEligible(ev.target)) return;
    mouseId = ev.pointerId;
    startX = ev.clientX;
    startY = ev.clientY;
    dy = 0;
  };

  /** @param {PointerEvent} ev */
  const onMouseMove = (ev) => {
    if (mouseId === null || ev.pointerId !== mouseId) return;
    const dyNow = ev.clientY - startY;
    if (!dragging) {
      if (dyNow <= CLAIM_PX || dyNow <= Math.abs(ev.clientX - startX)) return;
      dragging = true;
      suppressClick = true;
      try {
        panel.setPointerCapture(mouseId);
      } catch {
        /* ignore */
      }
    }
    dy = dyNow;
    ev.preventDefault();
    setOffset(dy);
  };

  /** @param {PointerEvent} ev */
  const onMouseUp = (ev) => {
    if (mouseId === null || ev.pointerId !== mouseId) return;
    mouseId = null;
    if (!dragging) return;
    dragging = false;
    if (dy > DISMISS_PX) {
      dismiss();
      return;
    }
    clearOffset();
    // suppressClick stays armed: the click lands after pointerup.
  };

  /** @param {PointerEvent} ev */
  const onMouseCancel = (ev) => {
    if (mouseId === null || ev.pointerId !== mouseId) return;
    mouseId = null;
    dragging = false;
    suppressClick = false;
    clearOffset();
  };

  // Safety net: a mouse gesture released (or canceled) off the panel must
  // still reset the tracking state, or later pointerdowns are dropped.
  // (Touch events implicitly capture to their start element, so they always
  // end on the panel.)
  /** @param {PointerEvent} ev */
  const onEndAnywhere = (ev) => {
    if (mouseId === null || ev.pointerId !== mouseId) return;
    mouseId = null;
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
  panel.addEventListener("touchstart", onTouchStart);
  panel.addEventListener("touchmove", onTouchMove, { passive: false });
  panel.addEventListener("touchend", onTouchEnd);
  panel.addEventListener("touchcancel", onTouchCancel);
  panel.addEventListener("pointerdown", onMouseDown);
  panel.addEventListener("pointermove", onMouseMove);
  panel.addEventListener("pointerup", onMouseUp);
  panel.addEventListener("pointercancel", onMouseCancel);
  window.addEventListener("pointerup", onEndAnywhere);
  window.addEventListener("pointercancel", onEndAnywhere);

  return () => {
    window.removeEventListener("pointerup", onEndAnywhere);
    window.removeEventListener("pointercancel", onEndAnywhere);
  };
}
