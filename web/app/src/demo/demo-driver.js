// @ts-check
// Beat sequencer for the marketing hero (web/app/demo.html). Drives the REAL
// components through their real DOM — hash navigation plus clicks on the real
// buttons — so the hero stays honest as the app evolves. Performance rules:
// no rAF loop, waits stall while paused, reduced-motion renders a single
// static frame, and the diary is re-seeded between loops so the story (and
// the calorie ring) restarts fresh every cycle.
import { reseedDiary } from "./demo-seed.js";

const VIEW = document.getElementById("view");
if (!(VIEW instanceof HTMLElement)) throw new Error("demo: #view missing");

const ROUTES = {
  home: "<diary-view></diary-view>",
  entry: "<entry-form></entry-form>",
  analyze: "<analyze-view></analyze-view>",
  scan: "<barcode-scanner></barcode-scanner>",
  progress: "<progress-view></progress-view>",
};

const REDUCED_MOTION = matchMedia("(prefers-reduced-motion: reduce)").matches;

/** @type {boolean} */
let paused = false;
/** @type {Array<() => void>} */
let resumeListeners = [];

function setPaused(next) {
  if (next === paused) return;
  paused = next;
  if (!paused) {
    const listeners = resumeListeners;
    resumeListeners = [];
    listeners.forEach((fn) => fn());
  }
}

/**
 * Pause-aware sleep: while paused, the timer fires but the promise resolves
 * only after a resume — total wait becomes ms + paused duration.
 * @param {number} ms
 * @returns {Promise<void>}
 */
function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(() => {
      if (paused) resumeListeners.push(resolve);
      else resolve();
    }, ms);
  });
}

/**
 * Poll for a condition with pause-aware steps.
 * @param {() => unknown} fn
 * @param {{ timeout?: number, step?: number, label?: string }} [opts]
 * @returns {Promise<void>}
 */
async function waitFor(
  fn,
  { timeout = 7000, step = 80, label = "condition" } = {},
) {
  const start = performance.now();
  while (performance.now() - start < timeout) {
    if (fn()) return;
    await sleep(step);
  }
  throw new Error(`demo: timeout waiting for ${label}`);
}

/** @param {string} hash */
function navigate(hash) {
  if (location.hash === hash) renderRoute();
  else location.hash = hash;
}

function routeFromHash() {
  const hash = location.hash.replace(/^#\//, "");
  const seg = hash.split(/[/?]/)[0];
  if (seg === "entry" || hash.startsWith("entry/")) return "entry";
  return seg in ROUTES ? seg : "home";
}

function renderRoute() {
  const route = routeFromHash();
  VIEW.innerHTML = ROUTES[route] || ROUTES.home;
}

window.addEventListener("hashchange", renderRoute);

/**
 * Set a field's value the way a user would (value + input event).
 * @param {HTMLInputElement | HTMLTextAreaElement} el
 * @param {string} text
 */
function setValue(el, text) {
  el.value = text;
  el.dispatchEvent(new Event("input", { bubbles: true }));
}

/** Type progressively so the hero reads like real typing. */
async function typeInto(el, text, perCharMs = 34) {
  el.focus();
  for (const ch of text) {
    setValue(el, el.value + ch);
    await sleep(perCharMs);
  }
}

/** @param {string} selector */
function clickFirst(selector) {
  const el = document.querySelector(selector);
  if (el instanceof HTMLElement) el.click();
}

/** @param {string} text */
async function openAnalyzeNote(text) {
  navigate("#/home");
  await waitFor(() => document.querySelector(".fab"), { label: "home fab" });
  await sleep(900);
  /** @type {HTMLElement | null} */
  const fab = document.querySelector(".fab");
  fab?.click();
  await waitFor(() => document.querySelector('[data-add="note"]'), {
    label: "add-food note tile",
  });
  await sleep(700);
  clickFirst('[data-add="note"]');
  await waitFor(() => document.querySelector("#analyze-form #note"), {
    label: "analyze note field",
  });
  await sleep(400);
  const note = /** @type {HTMLTextAreaElement | null} */ (
    document.querySelector("#analyze-form #note")
  );
  if (note) await typeInto(note, text);
  await sleep(250);
  /** @type {HTMLFormElement | null} */
  const form = document.querySelector("#analyze-form");
  form?.requestSubmit();
}

/** Review-and-log the prefilled entry form. @param {{log: boolean}} opts */
async function finishEntryForm({ log }) {
  await waitFor(() => document.querySelector(".entry-form--review"), {
    label: "entry review form",
  });
  await sleep(900); // review screen settles; nutrition fields animate in
  const submit = /** @type {HTMLButtonElement | null} */ (
    document.querySelector(".entry-form--review .subpage-cta button[type='submit']")
  );
  if (log && submit) {
    submit.click();
    await waitFor(() => document.querySelector(".fab"), {
      label: "home after log",
    });
    await sleep(1500); // calorie ring rises
  } else {
    const cancel = /** @type {HTMLButtonElement | null} */ (
      document.querySelector('.entry-form--review [data-action="cancel"]')
    );
    cancel?.click();
    await waitFor(() => document.querySelector(".fab"), {
      label: "home after cancel",
    });
    await sleep(800);
  }
}

async function beatAi() {
  await openAnalyzeNote("Chicken burrito bowl");
  await waitFor(() => document.querySelector(".analyze-overlay"), {
    label: "analyze overlay",
  });
  await sleep(3400); // phases stream, fields fill, auto-navigates to review
  await finishEntryForm({ log: true });
}

async function beatBarcode() {
  navigate("#/scan");
  await waitFor(() => document.querySelector("#manual-barcode-form"), {
    label: "manual barcode form",
  });
  await sleep(600);
  const input = /** @type {HTMLInputElement | null} */ (
    document.querySelector("#barcode")
  );
  if (input) await typeInto(input, "0049000028911", 60);
  await sleep(300);
  /** @type {HTMLFormElement | null} */
  const form = document.querySelector("#manual-barcode-form");
  form?.requestSubmit();
  await waitFor(() => document.querySelector(".entry-form--review"), {
    label: "product card review",
  });
  await sleep(1000);
  await finishEntryForm({ log: false });
}

async function beatTrend() {
  navigate("#/progress");
  await waitFor(() => document.querySelector('[data-range="All"]'), {
    label: "range chips",
  });
  await sleep(700);
  clickFirst('[data-range="All"]');
  await waitFor(() => document.querySelector(".chart-hit"), {
    label: "all-time charts",
  });
  await sleep(600);
  window.scrollTo({ top: 560, behavior: "smooth" });
  await sleep(1600);
  window.scrollTo({ top: 0, behavior: "smooth" });
  await sleep(700);
}

async function beatRelog() {
  navigate("#/home");
  await waitFor(() => document.querySelector(".fab"), { label: "home fab" });
  await sleep(600);
  /** @type {HTMLElement | null} */
  const fab = document.querySelector(".fab");
  fab?.click();
  await waitFor(() => document.querySelector("[data-relog]"), {
    label: "relog chips",
  });
  await sleep(700);
  clickFirst("[data-relog]");
  await waitFor(() => document.querySelector(".fab"), {
    label: "home after relog",
  });
  await sleep(1700); // ring rises
}

const BEATS = [
  { name: "ai", run: beatAi },
  { name: "barcode", run: beatBarcode },
  { name: "trend", run: beatTrend },
  { name: "relog", run: beatRelog },
];

/** Render one static, fully settled home frame (reduced-motion mode). */
async function renderStaticFrame() {
  navigate("#/home");
  await waitFor(() => document.querySelector(".fab"), { label: "home fab" }).catch(
    () => {},
  );
  await sleep(900);
}

export async function startDemo() {
  document.addEventListener("visibilitychange", () =>
    setPaused(document.hidden),
  );
  window.addEventListener("message", (ev) => {
    const data = /** @type {{source?: string, type?: string}} */ (ev.data);
    if (data?.source !== "chompass-hero") return;
    if (data.type === "pause") setPaused(true);
    else if (data.type === "play") setPaused(false);
  });

  if (REDUCED_MOTION) {
    await renderStaticFrame();
    return;
  }

  renderRoute();
  await sleep(600);
  for (let loop = 0; ; loop++) {
    try {
      await reseedDiary();
      await sleep(200);
      for (const beat of BEATS) {
        try {
          await beat.run();
        } catch (err) {
          console.warn(`demo beat "${beat.name}" failed, skipping`, err);
          navigate("#/home");
          await sleep(500);
        }
      }
      await sleep(1800); // hold the settled home before the next loop
    } catch (err) {
      console.warn("demo loop aborted, restarting", err);
      await sleep(1000);
    }
    if (loop > 0 && loop % 20 === 0) await reseedDiary(); // safety net
  }
}
