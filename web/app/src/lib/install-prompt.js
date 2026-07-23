// @ts-check
import { openSheet } from "./ui/sheet.js";

const BANNER_DISMISSED_KEY = "nofud-install-banner-dismissed";
const SHEET_SHOWN_KEY = "nofud-install-sheet-shown";

/**
 * @typedef {{
 *   prompt: () => Promise<void>,
 *   userChoice: Promise<{ outcome: "accepted" | "dismissed" }>,
 * }} BeforeInstallPromptLike
 */

/** @type {BeforeInstallPromptLike | null} */
let deferredPrompt = null;

/** @returns {boolean} */
export function isStandalone() {
  if (window.matchMedia("(display-mode: standalone)").matches) return true;
  // iOS Safari
  return "standalone" in navigator && /** @type {{ standalone?: boolean }} */ (navigator).standalone === true;
}

/** @returns {boolean} */
export function isIos() {
  return /iphone|ipad|ipod/i.test(navigator.userAgent);
}

/** Capture Chromium install events and show a soft banner when useful. */
export function initInstallPrompt() {
  if (isStandalone()) return;

  window.addEventListener("beforeinstallprompt", (ev) => {
    ev.preventDefault();
    deferredPrompt = /** @type {BeforeInstallPromptLike} */ (/** @type {unknown} */ (ev));
    maybeShowInstallBanner();
  });

  window.addEventListener("appinstalled", () => {
    deferredPrompt = null;
    localStorage.setItem(BANNER_DISMISSED_KEY, "1");
    document.querySelector(".install-banner")?.remove();
  });

  maybeShowInstallBanner();
}

function platformTip() {
  if (deferredPrompt) {
    return "Install NoFUD for quicker access and a full-screen app.";
  }
  if (isIos()) {
    return 'Add NoFUD to your Home Screen: tap Share, then "Add to Home Screen".';
  }
  return "Install NoFUD to your home screen or dock for easier access.";
}

/**
 * @param {HTMLElement} banner
 */
function fillBanner(banner) {
  const hasPrompt = !!deferredPrompt;
  banner.innerHTML = `
    <div class="install-banner__text">
      <span>${platformTip()}</span>
      <a class="install-banner__how" href="#/settings?section=install">How?</a>
    </div>
    <div class="install-banner__actions">
      ${hasPrompt ? `<button type="button" class="btn btn--primary install-banner__install">Install</button>` : ""}
      <button type="button" class="install-banner__dismiss" aria-label="Dismiss">✕</button>
    </div>
  `;

  banner.querySelector(".install-banner__dismiss")?.addEventListener("click", () => {
    localStorage.setItem(BANNER_DISMISSED_KEY, "1");
    banner.remove();
  });

  banner.querySelector(".install-banner__install")?.addEventListener("click", () => {
    void promptInstall();
  });
}

export function maybeShowInstallBanner() {
  if (isStandalone()) return;
  if (localStorage.getItem(BANNER_DISMISSED_KEY) === "1") return;
  // Avoid stacking with the first-run onboarding flow.
  if (/#\/?onboarding\b/.test(location.hash || "")) return;

  const existing = /** @type {HTMLElement | null} */ (document.querySelector(".install-banner"));
  if (existing) {
    fillBanner(existing);
    return;
  }

  const banner = document.createElement("div");
  banner.className = "install-banner";
  banner.setAttribute("role", "status");
  fillBanner(banner);
  document.body.appendChild(banner);
}

/** @returns {Promise<boolean>} */
export async function promptInstall() {
  if (!deferredPrompt) return false;
  const ev = deferredPrompt;
  deferredPrompt = null;
  try {
    await ev.prompt();
    const { outcome } = await ev.userChoice;
    if (outcome === "accepted") {
      localStorage.setItem(BANNER_DISMISSED_KEY, "1");
      document.querySelector(".install-banner")?.remove();
      return true;
    }
    maybeShowInstallBanner();
    return false;
  } catch {
    maybeShowInstallBanner();
    return false;
  }
}

/**
 * One-shot sheet after onboarding. Safe to call when already standalone or previously shown.
 */
export function maybeShowPostOnboardingInstallSheet() {
  if (isStandalone()) return;
  if (localStorage.getItem(SHEET_SHOWN_KEY) === "1") return;
  localStorage.setItem(SHEET_SHOWN_KEY, "1");

  window.setTimeout(() => {
    if (isStandalone()) return;

    const body = document.createElement("div");
    body.className = "install-sheet-body";

    let tip = "Add NoFUD to your home screen or dock so it opens like an app.";
    if (isIos()) {
      tip = 'On iPhone or iPad: tap Share, then "Add to Home Screen".';
    } else if (deferredPrompt) {
      tip = "Install NoFUD for a full-screen icon on your home screen or dock.";
    } else {
      tip = "Use your browser menu → Install app / Add to Home screen. Steps differ by browser.";
    }

    body.innerHTML = `
      <p style="margin:0;">${tip}</p>
      <div class="btn-row" style="margin-top:0.4rem;">
        ${
          deferredPrompt
            ? `<button type="button" class="btn btn--primary" data-install>Install</button>`
            : ""
        }
        <a class="btn btn--ghost" href="#/settings?section=install" data-howto>How to install</a>
        <button type="button" class="btn btn--ghost" data-skip>Not now</button>
      </div>
    `;

    const sheet = openSheet({ title: "Install NoFUD", body });

    body.querySelector("[data-install]")?.addEventListener("click", async () => {
      const ok = await promptInstall();
      if (ok) sheet.close();
    });

    body.querySelector("[data-howto]")?.addEventListener("click", () => {
      sheet.close();
    });

    body.querySelector("[data-skip]")?.addEventListener("click", () => {
      sheet.close();
    });
  }, 450);
}
