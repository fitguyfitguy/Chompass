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

const view = document.getElementById("view");
const nav = document.getElementById("bottom-nav");
const navLinks = () => document.querySelectorAll(".bottom-nav a");

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

async function applyTheme() {
  const p = await prefs.load();
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
  await applyTheme();
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
window.addEventListener("nofud-prefs-changed", () => applyTheme());

if (!location.hash || location.hash === "#/") location.hash = "#/home";
maybeSeedFromUrl().then(render);

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("./sw.js").catch((err) => {
      console.error("Service worker registration failed", err);
    });
  });
}

initInstallPrompt();
