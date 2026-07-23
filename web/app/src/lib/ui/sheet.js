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
