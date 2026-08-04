// @ts-check
import { trapFocus } from "./focus-trap.js";

/**
 * @typedef {{
 *   title: string,
 *   message: string,
 *   confirmLabel?: string,
 *   cancelLabel?: string,
 *   danger?: boolean,
 * }} ConfirmOptions
 */

/**
 * @typedef {{
 *   title: string,
 *   label: string,
 *   value?: string,
 *   inputMode?: string,
 *   type?: string,
 *   unit?: string,
 *   confirmLabel?: string,
 *   cancelLabel?: string,
 *   placeholder?: string,
 * }} InputOptions
 */

/**
 * Centered glass confirm dialog. Resolves true/false.
 * @param {ConfirmOptions} opts
 * @returns {Promise<boolean>}
 */
export function openConfirm(opts) {
  return new Promise((resolve) => {
    const host = mountDialog({
      title: opts.title,
      bodyHtml: `<p class="dialog__message">${escapeHtml(opts.message)}</p>`,
      actions: [
        {
          label: opts.cancelLabel ?? "Cancel",
          className: "btn btn--ghost",
          value: false,
        },
        {
          label: opts.confirmLabel ?? "Confirm",
          className: opts.danger ? "btn btn--danger" : "btn btn--primary",
          value: true,
          autofocus: true,
        },
      ],
      onResult: resolve,
    });
    void host;
  });
}

/**
 * Numeric/text input dialog. Resolves string or null if cancelled.
 * @param {InputOptions} opts
 * @returns {Promise<string | null>}
 */
export function openInput(opts) {
  return new Promise((resolve) => {
    const unit = opts.unit
      ? `<span class="dialog__unit" aria-hidden="true">${escapeHtml(opts.unit)}</span>`
      : "";
    const bodyHtml = `
      <label class="dialog__field">
        <span class="dialog__label">${escapeHtml(opts.label)}</span>
        <span class="dialog__input-wrap">
          <input
            type="${opts.type ?? "text"}"
            inputmode="${opts.inputMode ?? "text"}"
            value="${escapeAttr(opts.value ?? "")}"
            placeholder="${escapeAttr(opts.placeholder ?? "")}"
            data-autofocus
            class="dialog__input"
          />
          ${unit}
        </span>
      </label>`;

    mountDialog({
      title: opts.title,
      bodyHtml,
      actions: [
        {
          label: opts.cancelLabel ?? "Cancel",
          className: "btn btn--ghost",
          value: null,
        },
        {
          label: opts.confirmLabel ?? "Save",
          className: "btn btn--primary",
          value: "submit",
        },
      ],
      onResult: (v, host) => {
        if (v === "submit") {
          const input = /** @type {HTMLInputElement | null} */ (host.querySelector(".dialog__input"));
          resolve(input?.value ?? "");
        } else {
          resolve(null);
        }
      },
      submitOnEnter: true,
    });
  });
}

/**
 * @param {{
 *   title: string,
 *   bodyHtml: string,
 *   actions: { label: string, className: string, value: unknown, autofocus?: boolean }[],
 *   onResult: (value: unknown, host: HTMLElement) => void,
 *   submitOnEnter?: boolean,
 * }} opts
 */
/**
 * Centered glass informational dialog with a single dismiss action.
 * @param {{ title: string, message?: string, bodyHtml?: string, doneLabel?: string }} opts
 * @returns {Promise<void>}
 */
export function openInfo(opts) {
  return new Promise((resolve) => {
    const bodyHtml =
      opts.bodyHtml ?? `<p class="dialog__message">${escapeHtml(opts.message ?? "")}</p>`;
    mountDialog({
      title: opts.title,
      bodyHtml,
      actions: [
        {
          label: opts.doneLabel ?? "Done",
          className: "btn btn--primary",
          value: true,
          autofocus: true,
        },
      ],
      onResult: () => resolve(),
    });
  });
}

function mountDialog(opts) {
  const host = document.createElement("div");
  host.className = "dialog";
  host.setAttribute("role", "presentation");

  const scrim = document.createElement("div");
  scrim.className = "dialog__scrim";

  const panel = document.createElement("div");
  panel.className = "dialog__panel";
  panel.setAttribute("role", "alertdialog");
  panel.setAttribute("aria-modal", "true");

  const titleId = `dialog-title-${Math.random().toString(36).slice(2, 8)}`;
  panel.setAttribute("aria-labelledby", titleId);

  panel.innerHTML = `
    <h2 class="dialog__title" id="${titleId}">${escapeHtml(opts.title)}</h2>
    <div class="dialog__body">${opts.bodyHtml}</div>
    <div class="dialog__actions"></div>
  `;

  const actionsEl = /** @type {HTMLElement} */ (panel.querySelector(".dialog__actions"));
  for (const action of opts.actions) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = action.className;
    btn.textContent = action.label;
    if (action.autofocus) btn.setAttribute("data-autofocus", "");
    btn.addEventListener("click", () => finish(action.value));
    actionsEl.appendChild(btn);
  }

  host.appendChild(scrim);
  host.appendChild(panel);
  document.body.appendChild(host);
  document.body.classList.add("dialog-open");

  let closed = false;
  let releaseFocus = () => {};

  const finish = (value) => {
    if (closed) return;
    closed = true;
    host.classList.add("is-leaving");
    releaseFocus();
    document.removeEventListener("keydown", onKey);
    const done = () => {
      host.remove();
      document.body.classList.remove("dialog-open");
      opts.onResult(value, host);
    };
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) done();
    else {
      panel.addEventListener("transitionend", done, { once: true });
      setTimeout(done, 280);
    }
  };

  /** @param {KeyboardEvent} ev */
  const onKey = (ev) => {
    if (ev.key === "Escape") {
      ev.preventDefault();
      finish(opts.actions.find((a) => a.value === false || a.value === null)?.value ?? null);
    } else if (ev.key === "Enter" && opts.submitOnEnter) {
      const t = ev.target;
      if (t instanceof HTMLInputElement) {
        ev.preventDefault();
        finish("submit");
      }
    }
  };

  scrim.addEventListener("click", () =>
    finish(opts.actions.find((a) => a.value === false || a.value === null)?.value ?? null)
  );
  document.addEventListener("keydown", onKey);
  requestAnimationFrame(() => host.classList.add("is-open"));
  releaseFocus = trapFocus(panel);

  return host;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]
  );
}

function escapeAttr(s) {
  return String(s).replace(/"/g, "&quot;");
}
