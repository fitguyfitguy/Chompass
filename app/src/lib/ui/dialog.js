// @ts-check
import { trapFocus } from "./focus-trap.js";
import { escapeHtml, escapeAttr } from "./html.js";

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
          autofocus: opts.danger === true,
        },
        {
          label: opts.confirmLabel ?? "Confirm",
          className: opts.danger ? "btn btn--danger" : "btn btn--primary",
          value: true,
          autofocus: !opts.danger,
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
 * @typedef {{
 *   label: string,
 *   value?: number,
 *   min: number,
 *   max: number,
 *   step?: number,
 *   unit?: string,
 * }} WheelField
 */

/**
 * Centered glass dialog with one scroll-snap number wheel per field — the PWA
 * port of Android's NumericWheelPicker used by the tracker quick-log sheets.
 * Resolves one value per field (in order), or null if cancelled.
 * @param {{ title: string, fields: WheelField[], confirmLabel?: string, cancelLabel?: string }} opts
 * @returns {Promise<number[] | null>}
 */
export function openWheelDialog(opts) {
  return new Promise((resolve) => {
    /** @type {number[]} */
    const startIdx = [];
    const bodyHtml = opts.fields
      .map((f, i) => {
        const step = f.step ?? 1;
        const count = Math.floor((f.max - f.min) / step) + 1;
        const idx = Math.min(count - 1, Math.max(0, Math.round(((f.value ?? f.min) - f.min) / step)));
        startIdx[i] = idx;
        const v = f.min + idx * step;
        const items = Array.from({ length: count }, (_, k) => {
          const val = f.min + k * step;
          const unit = f.unit ? ` <span class="wheel__unit">${escapeHtml(f.unit)}</span>` : "";
          return `<div class="wheel__item" data-value="${val}">${val}${unit}</div>`;
        }).join("");
        return `
        <div class="wheel">
          <span class="dialog__label">${escapeHtml(f.label)}</span>
          <div class="wheel__viewport">
            <div class="wheel__band" aria-hidden="true"></div>
            <div
              class="wheel__list"
              data-wheel
              tabindex="0"
              role="spinbutton"
              aria-label="${escapeAttr(f.label)}"
              aria-valuemin="${f.min}"
              aria-valuemax="${f.max}"
              aria-valuenow="${v}"
            >
              <div class="wheel__pad" aria-hidden="true"></div>${items}<div class="wheel__pad" aria-hidden="true"></div>
            </div>
          </div>
        </div>`;
      })
      .join("");

    const host = mountDialog({
      title: opts.title,
      bodyHtml,
      actions: [
        { label: opts.cancelLabel ?? "Cancel", className: "btn btn--ghost", value: null },
        { label: opts.confirmLabel ?? "Save", className: "btn btn--primary", value: "submit", autofocus: true },
      ],
      onResult: (v, dialogHost) => {
        if (v !== "submit") {
          resolve(null);
          return;
        }
        const lists = dialogHost.querySelectorAll("[data-wheel]");
        resolve(Array.from(lists).map((list) => readWheelValue(/** @type {HTMLElement} */ (list))));
      },
      submitOnEnter: true,
    });

    host.querySelectorAll("[data-wheel]").forEach((list, i) =>
      initWheel(/** @type {HTMLElement} */ (list), startIdx[i]),
    );
  });
}

/** @param {HTMLElement} list */
function wheelRowHeight(list) {
  const item = list.querySelector(".wheel__item");
  return item ? item.getBoundingClientRect().height : 0;
}

/** @param {HTMLElement} list @param {number} start */
function initWheel(list, start) {
  const items = list.querySelectorAll(".wheel__item");
  const rowH = wheelRowHeight(list);
  if (rowH > 0) list.scrollTop = start * rowH;
  let active = start;
  items[active]?.classList.add("is-active");
  list.addEventListener(
    "scroll",
    () => {
      const h = wheelRowHeight(list);
      if (!h) return;
      const idx = Math.min(items.length - 1, Math.max(0, Math.round(list.scrollTop / h)));
      if (idx === active) return;
      items[active]?.classList.remove("is-active");
      items[idx]?.classList.add("is-active");
      active = idx;
      list.setAttribute("aria-valuenow", String(Number(/** @type {HTMLElement} */ (items[idx]).dataset.value)));
    },
    { passive: true },
  );
}

/** @param {HTMLElement} list */
function readWheelValue(list) {
  // Read aria-valuenow, not scrollTop: onResult runs after the dialog host is
  // detached, and a detached scroller reports scrollTop 0 (silently logging
  // the minimum instead of the picked value).
  const now = list.getAttribute("aria-valuenow");
  return now == null ? 0 : Number(now);
}

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

/**
 * @param {{
 *   title: string,
 *   bodyHtml: string,
 *   actions: { label: string, className: string, value: unknown, autofocus?: boolean }[],
 *   onResult: (value: unknown, host: HTMLElement) => void,
 *   submitOnEnter?: boolean,
 * }} opts
 */
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
