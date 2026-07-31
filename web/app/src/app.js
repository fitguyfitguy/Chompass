// @ts-check
import "./components/diary-view.js";
import "./components/entry-form.js";
import "./components/settings-view.js";
import "./components/coach-view.js";
import "./components/barcode-scanner.js";
import "./components/progress-view.js";
import "./components/analyze-view.js";
import "./components/onboarding-view.js";
import "./components/measurements-view.js";
import "./components/add-meal-view.js";
import { maybeSeedFromUrl } from "./lib/dev-seed.js";
import { prefs, profile } from "./lib/db.js";
import { initInstallPrompt, maybeShowInstallBanner } from "./lib/install-prompt.js";
import { initUpdatePrompt } from "./lib/update-prompt.js";
import { activateFromPrefs, t } from "./lib/i18n/index.js";

const view = document.getElementById("view");
const nav = document.getElementById("bottom-nav");
const navLinks = () => document.querySelectorAll(".bottom-nav a");

function applyNavLabels() {
  const labels = {
    home: t("nav.home"),
    progress: t("nav.progress"),
    coach: t("nav.coach"),
    settings: t("nav.settings"),
  };
  navLinks().forEach((a) => {
    const href = a.getAttribute("href") || "";
    const tab = href.replace("#/", "");
    const label = labels[tab];
    if (!label) return;
    const icon = a.querySelector("svg");
    a.textContent = "";
    if (icon) a.appendChild(icon);
    a.appendChild(document.createTextNode(` ${label}`));
  });
  if (nav) nav.setAttribute("aria-label", t("a11y.primary_nav"));
}

const ROUTES = {
  home: () => "<diary-view></diary-view>",
  diary: () => "<diary-view></diary-view>",
  entry: () => "<entry-form></entry-form>",
  analyze: () => "<analyze-view></analyze-view>",
  coach: () => "<coach-view></coach-view>",
  scan: () => "<barcode-scanner></barcode-scanner>",
  progress: () => "<progress-view></progress-view>",
  settings: () => "<settings-view></settings-view>",
  onboarding: () => "<onboarding-view></onboarding-view>",
  measurements: () => "<measurements-view></measurements-view>",
  "add-meal": () => "<add-meal-view></add-meal-view>",
};

const HIDE_NAV = new Set(["entry", "analyze", "scan", "onboarding", "measurements", "add-meal"]);

function currentRoute() {
  const hash = location.hash.replace(/^#\//, "");
  const [segment] = hash.split(/[/?]/);
  if (segment === "entry" || hash.startsWith("entry/")) return "entry";
  if (segment === "add-meal") return "add-meal";
  return segment in ROUTES ? segment : "home";
}

async function applyThemeAndLocale() {
  const p = await prefs.load();
  activateFromPrefs(p);
  applyNavLabels();
  const root = document.documentElement;
  if (p.theme === "system") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", p.theme);
  if (p.accent && p.accent !== "teal") root.setAttribute("data-accent", p.accent);
  else root.removeAttribute("data-accent");
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", getComputedStyle(root).getPropertyValue("--teal-deep").trim() || "#006b5e");
}

async function ensureOnboarding() {
  const p = await prefs.load();
  const route = currentRoute();
  if (!p.onboardingComplete) {
    const existing = await profile.load();
    if (existing) {
      await prefs.save({ onboardingComplete: true });
      return true;
    }
    if (route !== "onboarding") {
      location.hash = "#/onboarding";
      return false;
    }
  }
  return true;
}

async function render() {
  await applyThemeAndLocale();
  const ok = await ensureOnboarding();
  if (!ok && currentRoute() !== "onboarding") return;

  const route = currentRoute();
  document.body.classList.toggle("hide-nav", HIDE_NAV.has(route));
  view.innerHTML = ROUTES[route]();
  navLinks().forEach((a) => {
    const href = a.getAttribute("href") || "";
    const tab = href.replace("#/", "");
    const isCurrent =
      (tab === "home" && (route === "home" || route === "diary")) ||
      href === `#/${route}`;
    if (isCurrent) a.setAttribute("aria-current", "page");
    else a.removeAttribute("aria-current");
  });
  if (nav) nav.hidden = HIDE_NAV.has(route);
  if (route === "onboarding") {
    document.querySelector(".install-banner")?.remove();
  } else {
    maybeShowInstallBanner();
  }
}

window.addEventListener("hashchange", () => {
  render();
});
window.addEventListener("chompass-prefs-changed", () => {
  applyThemeAndLocale().then(() => {
    // Re-render so localized templates pick up the new locale.
    render();
  });
});

if (!location.hash || location.hash === "#/") location.hash = "#/home";
maybeSeedFromUrl().then(render);

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker
      .register("./sw.js")
      .then((registration) => initUpdatePrompt(registration))
      .catch((err) => {
        console.error("Service worker registration failed", err);
      });
  });
}

initInstallPrompt();
