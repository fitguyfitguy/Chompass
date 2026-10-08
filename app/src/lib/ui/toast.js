// @ts-check
import { escapeHtml } from "./html.js";
import { t } from "../i18n/index.js";

/**
 * Shared glass toasts. Only one is visible at a time: a new toast replaces
 * the previous one, so an undo window is single-level by design (the second
 * deletion's toast supersedes the first).
 */

/**
 * Show a transient message. Auto-dismisses after 3.5s.
 * @param {string} message
 */
export function showToast(message) {
  document.querySelector(".toast")?.remove();
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.textContent = message;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 3500);
}

/**
 * Show a transient message with an Undo action (5s window).
 * @param {string} message
 * @param {() => (void | Promise<void>)} onUndo
 */
export function showUndoToast(message, onUndo) {
  document.querySelector(".toast")?.remove();
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.innerHTML = `${escapeHtml(message)} <button type="button">${t("toast.undo")}</button>`;
  toast.querySelector("button")?.addEventListener("click", async () => {
    await onUndo();
    toast.remove();
  });
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 5000);
}
