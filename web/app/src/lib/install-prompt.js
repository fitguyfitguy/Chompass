// @ts-check
import { openSheet } from "./ui/sheet.js";

const BANNER_DISMISSED_KEY = "chompass-install-banner-dismissed";
const SHEET_SHOWN_KEY = "chompass-install-sheet-shown";

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

/** @returns {boolean} */
export function isAndroid() {
  return /android/i.test(navigator.userAgent);
}

/** @returns {boolean} */
export function isFirefox() {
  return /firefox|fxios/i.test(navigator.userAgent);
}

/** @returns {boolean} */
export function isDuckDuckGo() {
  return /duckduckgo/i.test(navigator.userAgent);
}

/**
 * Chromium install prompt API. Firefox and DuckDuckGo never fire this.
 * @returns {boolean}
 */
export function hasDeferredInstallPrompt() {
  return !!deferredPrompt;
}

/**
 * iOS browsers other than Safari (Brave, Chrome, Firefox, Edge, Opera). Only
 * Safari can install a true home-screen web app on iOS. Brave mimics Safari's
 * user agent, so it is detected via `navigator.brave` instead.
 * @returns {boolean}
 */
export function isIosNonSafari() {
  if (!isIos()) return false;
  if ("brave" in navigator) return true;
  return /crios|fxios|edgios|opt\//i.test(navigator.userAgent);
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

/**
 * @returns {{ tip: string, stepsHtml: string, note: string }}
 */
export function installHelpContent() {
  if (isIosNonSafari()) {
    return {
      tip: "On iOS, only Safari can install home-screen web apps. Open Chompass in Safari:",
      stepsHtml: `
        <ol class="install-steps">
          <li>Copy this page’s address and open it in <strong>Safari</strong>.</li>
          <li>Tap <strong>Share</strong> (square with an upward arrow).</li>
          <li>Choose <strong>Add to Home Screen</strong>, then Add.</li>
          <li>Open from the home-screen icon (not a browser tab).</li>
        </ol>`,
      note: "Chrome, Firefox, and Brave on iOS cannot install a true home-screen web app.",
    };
  }
  if (isIos()) {
    return {
      tip: "Add Chompass to your Home Screen from Safari:",
      stepsHtml: `
        <ol class="install-steps">
          <li>Tap <strong>Share</strong> (square with an upward arrow).</li>
          <li>Choose <strong>Add to Home Screen</strong>, then Add.</li>
          <li>Open from the home-screen icon (not a Safari tab).</li>
        </ol>`,
      note: "",
    };
  }
  if (isDuckDuckGo() && isAndroid()) {
    return {
      tip: "DuckDuckGo on Android does not support full PWA install. Use the browser menu for a shortcut, or open Chompass in Chrome / Firefox for a better install:",
      stepsHtml: `
        <ol class="install-steps">
          <li>Tap the browser menu (⋮).</li>
          <li>Tap <strong>Add to Home</strong> / <strong>Add to Home Screen</strong>.</li>
          <li>If nothing happens, your launcher may block shortcuts — try Chrome or Firefox, or set a Home app under Android Settings → Apps → Default apps.</li>
        </ol>`,
      note: "For a full-screen installed app, open https://chompass.app/app/ in Chrome, Edge, or Brave, then use Install app / Add to Home screen.",
    };
  }
  if (isFirefox() && isAndroid()) {
    return {
      tip: "Firefox on Android installs from the browser menu (there is no in-page install popup):",
      stepsHtml: `
        <ol class="install-steps">
          <li>Tap the Firefox menu (⋮).</li>
          <li>Tap <strong>Add to Home screen</strong> or <strong>Add app to Home screen</strong>.</li>
          <li>Confirm. Open Chompass from the new icon afterward.</li>
        </ol>`,
      note: "If the menu item does nothing, check Android Settings → Apps → Default apps → Home app (it must not be “None”). Chromium browsers (Chrome, Edge, Brave) usually install more reliably.",
    };
  }
  if (isAndroid()) {
    return {
      tip: "Install from your browser menu:",
      stepsHtml: `
        <ol class="install-steps">
          <li>Open the browser menu (⋮).</li>
          <li>Tap <strong>Install app</strong> or <strong>Add to Home screen</strong>.</li>
          <li>Confirm. Open Chompass from the new icon afterward.</li>
        </ol>`,
      note: "Many Chromium browsers do not show an automatic install popup — use the menu.",
    };
  }
  return {
    tip: "Install from the address bar or browser menu:",
    stepsHtml: `
      <ol class="install-steps">
        <li>Look for the install icon in the address bar, or open the browser menu.</li>
        <li>Choose <strong>Install Chompass</strong> (or Install app).</li>
        <li>Launch from your dock, taskbar, or app launcher.</li>
      </ol>`,
    note: "Firefox desktop has limited PWA install; bookmarking still works.",
  };
}

function platformTip() {
  if (deferredPrompt) {
    return "Install Chompass for quicker access and a full-screen app.";
  }
  if (isIosNonSafari()) {
    return 'This browser cannot install apps on iOS. Open this page in Safari, then tap Share → "Add to Home Screen".';
  }
  if (isIos()) {
    return 'Add Chompass to your Home Screen: tap Share → "Add to Home Screen", then open from the icon (not a Safari tab).';
  }
  if (isDuckDuckGo() && isAndroid()) {
    return "DuckDuckGo cannot fully install PWAs. Use the menu for a shortcut, or open in Chrome / Firefox to install.";
  }
  if (isFirefox() && isAndroid()) {
    return "Firefox has no in-page install button — use the menu (⋮) → Add to Home screen.";
  }
  return "Install Chompass to your home screen or dock for easier access.";
}

/**
 * Open a sheet with browser-specific install steps. Always actionable —
 * Firefox / DuckDuckGo never get beforeinstallprompt.
 * @returns {ReturnType<typeof openSheet>}
 */
export function showInstallHelpSheet() {
  const { tip, stepsHtml, note } = installHelpContent();
  const body = document.createElement("div");
  body.className = "install-sheet-body";
  body.innerHTML = `
    <p style="margin:0;">${tip}</p>
    ${stepsHtml}
    ${note ? `<p class="install-note">${note}</p>` : ""}
    <div class="btn-row" style="margin-top:0.75rem;">
      <a class="btn btn--ghost" href="#/settings?section=install" data-howto>Full install guide</a>
      <button type="button" class="btn btn--ghost" data-close>Got it</button>
    </div>
  `;
  const sheet = openSheet({ title: "Add to Home Screen", body });
  body.querySelector("[data-howto]")?.addEventListener("click", () => sheet.close());
  body.querySelector("[data-close]")?.addEventListener("click", () => sheet.close());
  return sheet;
}

/**
 * @param {HTMLElement} banner
 */
function fillBanner(banner) {
  const hasPrompt = !!deferredPrompt;
  const actionLabel = hasPrompt ? "Install" : "How to add";
  banner.innerHTML = `
    <div class="install-banner__text">
      <span>${platformTip()}</span>
      <a class="install-banner__how" href="#/settings?section=install">Full guide</a>
    </div>
    <div class="install-banner__actions">
      <button type="button" class="btn btn--primary install-banner__install">${actionLabel}</button>
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

/**
 * Try Chromium's deferred install prompt; otherwise open browser-specific help
 * so the button never silently does nothing (Firefox / DuckDuckGo Android).
 * @returns {Promise<boolean>} true if the OS install UI accepted
 */
export async function promptInstall() {
  if (!deferredPrompt) {
    showInstallHelpSheet();
    return false;
  }
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
    // Prompt can fail or be unavailable after capture; fall back to help.
    maybeShowInstallBanner();
    showInstallHelpSheet();
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

    const { tip, stepsHtml, note } = installHelpContent();
    const body = document.createElement("div");
    body.className = "install-sheet-body";

    let lead = tip;
    if (deferredPrompt) {
      lead = "Install Chompass for a full-screen icon on your home screen or dock.";
    }

    body.innerHTML = `
      <p style="margin:0;">${lead}</p>
      ${deferredPrompt ? "" : stepsHtml}
      ${!deferredPrompt && note ? `<p class="install-note">${note}</p>` : ""}
      <div class="btn-row" style="margin-top:0.4rem;">
        ${
          deferredPrompt
            ? `<button type="button" class="btn btn--primary" data-install>Install</button>`
            : ""
        }
        <a class="btn btn--ghost" href="#/settings?section=install" data-howto>How to install</a>
        <button type="button" class="btn btn--ghost" data-skip>${deferredPrompt ? "Not now" : "Got it"}</button>
      </div>
    `;

    const sheet = openSheet({ title: "Install Chompass", body });

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
