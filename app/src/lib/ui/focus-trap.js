// @ts-check

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Trap Tab focus inside `root`. Returns a teardown function.
 * @param {HTMLElement} root
 * @param {HTMLElement | null} [restoreTo]
 */
export function trapFocus(root, restoreTo = null) {
  const previouslyFocused =
    restoreTo ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null);

  /** @returns {HTMLElement[]} */
  const focusables = () =>
    /** @type {HTMLElement[]} */ (
      [...root.querySelectorAll(FOCUSABLE)].filter(
        (el) => el instanceof HTMLElement && !el.hasAttribute("disabled") && el.offsetParent !== null
      )
    );

  queueMicrotask(() => {
    const list = focusables();
    const preferred = root.querySelector("[data-autofocus]");
    if (preferred instanceof HTMLElement) preferred.focus();
    else if (list[0]) list[0].focus();
  });

  /** @param {KeyboardEvent} ev */
  function onKeyDown(ev) {
    if (ev.key !== "Tab") return;
    const list = focusables();
    if (list.length === 0) {
      ev.preventDefault();
      return;
    }
    const first = list[0];
    const last = list[list.length - 1];
    if (ev.shiftKey && document.activeElement === first) {
      ev.preventDefault();
      last.focus();
    } else if (!ev.shiftKey && document.activeElement === last) {
      ev.preventDefault();
      first.focus();
    }
  }

  root.addEventListener("keydown", onKeyDown);

  return () => {
    root.removeEventListener("keydown", onKeyDown);
    if (previouslyFocused && document.contains(previouslyFocused)) {
      previouslyFocused.focus();
    }
  };
}
