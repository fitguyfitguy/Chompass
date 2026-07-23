// @ts-check
import "./components/diary-view.js";
import "./components/entry-form.js";
import "./components/settings-view.js";
import "./components/coach-view.js";
import "./components/barcode-scanner.js";
import "./components/progress-view.js";

const view = document.getElementById("view");
const navLinks = () => document.querySelectorAll(".bottom-nav a");

const ROUTES = {
  diary: () => "<diary-view></diary-view>",
  entry: () => "<entry-form></entry-form>",
  coach: () => "<coach-view></coach-view>",
  scan: () => "<barcode-scanner></barcode-scanner>",
  progress: () => "<progress-view></progress-view>",
  settings: () => "<settings-view></settings-view>",
};

function currentRoute() {
  const hash = location.hash.replace(/^#\//, "");
  const [segment] = hash.split("/");
  return segment in ROUTES ? segment : "diary";
}

function render() {
  const route = currentRoute();
  view.innerHTML = ROUTES[route]();
  navLinks().forEach((a) => {
    const isCurrent = a.getAttribute("href") === `#/${route}`;
    if (isCurrent) a.setAttribute("aria-current", "page");
    else a.removeAttribute("aria-current");
  });
}

window.addEventListener("hashchange", render);
if (!location.hash) location.hash = "#/diary";
render();

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("./sw.js").catch((err) => {
      console.error("Service worker registration failed", err);
    });
  });
}

maybeShowIosInstallBanner();

function maybeShowIosInstallBanner() {
  const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent);
  const isStandalone = "standalone" in navigator && navigator.standalone === true;
  const dismissed = localStorage.getItem("nofud-install-banner-dismissed") === "1";
  if (!isIos || isStandalone || dismissed) return;

  const banner = document.createElement("div");
  banner.className = "install-banner";
  banner.innerHTML = `
    <span>Add NoFUD to your Home Screen: tap Share, then "Add to Home Screen".</span>
    <button aria-label="Dismiss">✕</button>
  `;
  banner.querySelector("button").addEventListener("click", () => {
    localStorage.setItem("nofud-install-banner-dismissed", "1");
    banner.remove();
  });
  document.body.appendChild(banner);
}
