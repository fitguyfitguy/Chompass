// @ts-check
import { openSheet } from "./ui/sheet.js";
import { t } from "./i18n/index.js";

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
      tip: t("install.help_ios_nonsafari_tip"),
      stepsHtml: stepsHtml([
        t("install.help_ios_nonsafari_step1"),
        t("install.help_ios_step_share"),
        t("install.help_ios_step_add"),
        t("install.help_ios_nonsafari_step2"),
      ]),
      note: "",
    };
  }
  if (isIos()) {
    return {
      tip: t("install.help_ios_tip"),
      stepsHtml: stepsHtml([
        t("install.help_ios_step_share"),
        t("install.help_ios_step_add"),
        t("install.help_ios_step_open"),
      ]),
      note: "",
    };
  }
  if (isDuckDuckGo() && isAndroid()) {
    return {
      tip: t("install.help_ddg_tip"),
      stepsHtml: stepsHtml([
        t("install.help_step_browser_menu"),
        t("install.help_ddg_step_add"),
        t("install.help_ddg_step_blocked"),
      ]),
      note: t("install.help_ddg_note"),
    };
  }
  if (isFirefox() && isAndroid()) {
    return {
      tip: t("install.help_firefox_tip"),
      stepsHtml: stepsHtml([
        t("install.help_step_firefox_menu"),
        t("install.help_firefox_step_add"),
        t("install.help_step_confirm_open"),
      ]),
      note: t("install.help_firefox_note"),
    };
  }
  if (isAndroid()) {
    return {
      tip: t("install.help_android_tip"),
      stepsHtml: stepsHtml([
        t("install.help_step_open_menu"),
        t("install.help_android_step_add"),
        t("install.help_step_confirm_open"),
      ]),
      note: t("install.help_android_note"),
    };
  }
  return {
    tip: t("install.help_desktop_tip"),
    stepsHtml: stepsHtml([
      t("install.help_desktop_step1"),
      t("install.help_desktop_step2"),
      t("install.help_desktop_step3"),
    ]),
    note: t("install.help_desktop_note"),
  };
}

/** @param {string[]} steps */
function stepsHtml(steps) {
  return `<ol class="install-steps">${steps.map((s) => `<li>${s}</li>`).join("")}</ol>`;
}

function platformTip() {
  if (deferredPrompt) {
    return t("install.prompt_tip");
  }
  if (isIosNonSafari()) {
    return t("install.banner_ios_nonsafari");
  }
  if (isIos()) {
    return t("install.banner_ios");
  }
  if (isDuckDuckGo() && isAndroid()) {
    return t("install.banner_ddg");
  }
  if (isFirefox() && isAndroid()) {
    return t("install.banner_firefox");
  }
  return t("install.banner_default");
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
      <a class="btn btn--ghost" href="#/settings?section=install" data-howto>${t("install.full_guide")}</a>
      <button type="button" class="btn btn--ghost" data-close>${t("install.got_it")}</button>
    </div>
  `;
  const sheet = openSheet({ title: t("install.sheet_title"), body });
  body.querySelector("[data-howto]")?.addEventListener("click", () => sheet.close());
  body.querySelector("[data-close]")?.addEventListener("click", () => sheet.close());
  return sheet;
}

/**
 * @param {HTMLElement} banner
 */
function fillBanner(banner) {
  const hasPrompt = !!deferredPrompt;
  const actionLabel = hasPrompt ? t("install.install") : t("install.how_to_add");
  banner.innerHTML = `
    <div class="install-banner__text">
      <span>${platformTip()}</span>
      <a class="install-banner__how" href="#/settings?section=install">${t("install.full_guide_short")}</a>
    </div>
    <div class="install-banner__actions">
      <button type="button" class="btn btn--primary install-banner__install">${actionLabel}</button>
      <button type="button" class="install-banner__dismiss" aria-label="${t("install.dismiss")}">✕</button>
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
      lead = t("install.post_lead");
    }

    body.innerHTML = `
      <p style="margin:0;">${lead}</p>
      ${deferredPrompt ? "" : stepsHtml}
      ${!deferredPrompt && note ? `<p class="install-note">${note}</p>` : ""}
      <div class="btn-row" style="margin-top:0.4rem;">
        ${
          deferredPrompt
            ? `<button type="button" class="btn btn--primary" data-install>${t("install.install")}</button>`
            : ""
        }
        <a class="btn btn--ghost" href="#/settings?section=install" data-howto>${t("install.howto_link")}</a>
        <button type="button" class="btn btn--ghost" data-skip>${deferredPrompt ? t("install.not_now") : t("install.got_it")}</button>
      </div>
    `;

    const sheet = openSheet({ title: t("install.sheet_post_title"), body });

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
