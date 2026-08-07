// @ts-check
// Beat sequencer for the marketing hero (web/app/demo.html). Drives the REAL
// components through their real DOM — hash navigation plus clicks on the real
// buttons — so the hero stays honest as the app evolves. Performance rules:
// no rAF loop, waits stall while paused, reduced-motion renders a single
// static frame, and the diary is re-seeded between loops so the story (and
// the calorie ring) restarts fresh every cycle.
//
// The sequencer also announces "scenes" to the parent page (chompass-hero
// postMessage). The site's camera controller turns each scene into a smooth
// zoom/crop over the app UI, so the presentation is driven by the same
// timeline that runs the demo.
import { reseedDiary, seedDemo } from "./demo-seed.js";

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
  // Bottom nav (decorative) — mirror the real app's aria-current handling.
  document.querySelectorAll(".bottom-nav a").forEach((a) => {
    const isCurrent = a.getAttribute("href") === `#/${route}`;
    if (isCurrent) a.setAttribute("aria-current", "page");
    else a.removeAttribute("aria-current");
  });
}

window.addEventListener("hashchange", renderRoute);

// Register pause/play listeners at module scope: the site may send "pause"
// before seeding finishes (e.g. the hero is scrolled away during first load).
document.addEventListener("visibilitychange", () => setPaused(document.hidden));
window.addEventListener("message", (ev) => {
  const data = /** @type {{source?: string, type?: string}} */ (ev.data);
  if (data?.source !== "chompass-hero") return;
  if (data.type === "pause") setPaused(true);
  else if (data.type === "play") setPaused(false);
});

/**
 * Announce a camera scene to the site hero (see website/assets/js/hero.js).
 * Selectors resolve inside this iframe (same origin), so the site camera can
 * compute the crop geometry itself. `key: null` means "pull back to rest".
 * @param {string | null} key
 * @param {string} [selector]
 * @param {number} [index]
 */
function scene(key, selector, index = 0) {
  try {
    parent.postMessage(
      { source: "chompass-hero", type: "scene", key, selector, index },
      "*",
    );
  } catch {
    /* ignore */
  }
}

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
  window.scrollTo({ top: 0 });
  await waitFor(() => document.querySelector(".fab"), { label: "home fab" });
  await sleep(1000);
  /** @type {HTMLElement | null} */
  const fab = document.querySelector(".fab");
  fab?.click();
  await waitFor(() => document.querySelector('[data-add="note"]'), {
    label: "add-food note tile",
  });
  await sleep(900);
  clickFirst('[data-add="note"]');
  await waitFor(() => document.querySelector("#analyze-form #note"), {
    label: "analyze note field",
  });
  await sleep(500);
  // Zoom into the note field itself: the AI prompt is where the story starts.
  scene("ai-typing", "#analyze-form #note");
  const note = /** @type {HTMLTextAreaElement | null} */ (
    document.querySelector("#analyze-form #note")
  );
  if (note) await typeInto(note, text, 45);
  await sleep(450);
  /** @type {HTMLFormElement | null} */
  const form = document.querySelector("#analyze-form");
  form?.requestSubmit();
}

/** Review the prefilled entry form; log or cancel. @param {{log: boolean, sceneKey: string}} opts */
async function finishEntryForm({ log, sceneKey }) {
  await waitFor(() => document.querySelector(".entry-form--review"), {
    label: "entry review form",
  });
  scene(sceneKey, ".entry-form--review");
  await sleep(1200); // review screen settles; nutrition fields animate in
  const submit = /** @type {HTMLButtonElement | null} */ (
    document.querySelector(
      ".entry-form--review .subpage-cta button[type='submit']",
    )
  );
  if (log && submit) {
    submit.click();
    await waitFor(() => document.querySelector(".fab"), {
      label: "home after log",
    });
  } else {
    const cancel = /** @type {HTMLButtonElement | null} */ (
      document.querySelector('.entry-form--review [data-action="cancel"]')
    );
    cancel?.click();
    await waitFor(() => document.querySelector(".fab"), {
      label: "home after cancel",
    });
    await sleep(900);
  }
}

async function beatAi() {
  await openAnalyzeNote("Chicken burrito bowl");
  await waitFor(() => document.querySelector(".analyze-overlay"), {
    label: "analyze overlay",
  });
  // Stage 1: the overlay's spinner + phase label are visible content — crop
  // there immediately instead of holding the now-covered form (no blank).
  scene("ai-stream", ".analyze-overlay__phase");
  await sleep(700);
  // Stage 2: once three macros resolved, the partial card is near its final
  // size; re-announce so the camera crops the card as the fields fill in.
  await waitFor(
    () =>
      document.querySelectorAll(".analyze-partial__macro:not(.is-pending)")
        .length >= 3,
    { timeout: 5000, label: "analyze partial macros" },
  ).catch(() => {});
  scene("ai-stream", ".analyze-partial");
  await sleep(3200); // phases stream, fields fill, auto-navigates to review
  await finishEntryForm({ log: true, sceneKey: "ai-review" });
  scene("ai-ring", ".calorie-ring--semi");
  await sleep(2400); // calorie ring rises
}

async function beatBarcode() {
  navigate("#/scan");
  await waitFor(() => document.querySelector("#manual-barcode-form"), {
    label: "manual barcode form",
  });
  await sleep(800);
  scene("barcode-form", "#manual-barcode-form");
  const input = /** @type {HTMLInputElement | null} */ (
    document.querySelector("#barcode")
  );
  if (input) await typeInto(input, "0049000028911", 60);
  await sleep(400);
  /** @type {HTMLFormElement | null} */
  const form = document.querySelector("#manual-barcode-form");
  form?.requestSubmit();
  await finishEntryForm({ log: false, sceneKey: "barcode-card" });
}

// The weight line chart inside the first (weight) card. Shared by the warp
// beat and the camera: both resolve querySelectorAll(...)[0] in the same doc.
const CHART_SEL = ".card.card--glass .chart-svg";

/** First `.card.card--glass` matching `pred`, or null. */
function cardAt(pred) {
  return (
    Array.from(document.querySelectorAll(".card.card--glass")).find(pred) ??
    null
  );
}

/** Index of the first `.card.card--glass` matching `pred` (for the camera). */
function cardIndex(pred) {
  return Array.from(document.querySelectorAll(".card.card--glass")).findIndex(
    pred,
  );
}

/** Center an element in the phone viewport so the camera crop lands on it. */
function centerOn(el) {
  el?.scrollIntoView({ block: "center", behavior: "auto" });
}

/**
 * Click a range chip and wait for the chart SVG to be replaced by the
 * re-render, so the camera always crops the freshly widened chart.
 * @param {string} id
 */
async function clickRange(id) {
  const before = document.querySelector(CHART_SEL);
  clickFirst(`[data-range="${id}"]`);
  await waitFor(() => document.querySelector(CHART_SEL) !== before, {
    timeout: 6000,
    label: `range ${id} chart`,
  });
  await sleep(280); // let the new chart paint
}

async function beatTrend() {
  navigate("#/progress");
  window.scrollTo({ top: 0 });
  await waitFor(() => document.querySelector('[data-range="All"]'), {
    label: "range chips",
  });
  await sleep(700);

  // 1) Close-up on the weight chart at 1M: noisy daily readings, mild drift.
  await clickRange("1M");
  centerOn(document.querySelector(CHART_SEL));
  await sleep(400);
  scene("trend-warp-close", CHART_SEL);
  await sleep(3400);

  // 2) Warp: ranges widen with accelerating tempo — 2y of weigh-ins compress
  // into a few seconds. 1Y steps out to the whole card so the camera zooms out
  // while the data expands; All settles back on the full downward trend.
  const warp = [
    { range: "3M", selector: CHART_SEL, dwell: 1300 },
    { range: "6M", selector: CHART_SEL, dwell: 950 },
    { range: "1Y", selector: ".card.card--glass", card: 0, dwell: 900 },
    { range: "All", selector: CHART_SEL, dwell: 2600 },
  ];
  for (const step of warp) {
    await clickRange(step.range);
    centerOn(
      step.card != null
        ? cardAt((_, i) => i === step.card)
        : document.querySelector(step.selector),
    );
    await sleep(300);
    scene("trend-warp", step.selector, step.card ?? 0);
    await sleep(step.dwell);
  }

  // 3) The important numbers: current / goal / net / average badges.
  const badges = document.querySelector(".card.card--glass .stat-badges");
  centerOn(badges);
  await sleep(400);
  scene("trend-stats", ".card.card--glass .stat-badges");
  await sleep(2600);

  // 4) Body-fat card.
  const bfIndex = cardIndex((c) =>
    /body fat/i.test(c.querySelector("h2.chart-title")?.textContent ?? ""),
  );
  if (bfIndex >= 0) {
    centerOn(cardAt((_, i) => i === bfIndex));
    await sleep(400);
    scene("trend-bodyfat", ".card.card--glass", bfIndex);
    await sleep(2400);
  }

  // 5) Forecast card: predicted change, days to goal, adaptive message.
  const fIndex = cardIndex((c) =>
    /weight forecast/i.test(
      c.querySelector("h2.chart-title")?.textContent ?? "",
    ),
  );
  if (fIndex >= 0) {
    centerOn(cardAt((_, i) => i === fIndex));
    await sleep(400);
    scene("trend-forecast", ".card.card--glass", fIndex);
    await sleep(2600);
  }

  window.scrollTo({ top: 0, behavior: "smooth" });
  await sleep(700);
}

async function beatRelog() {
  navigate("#/home");
  window.scrollTo({ top: 0 });
  await waitFor(() => document.querySelector(".fab"), { label: "home fab" });
  await sleep(700);
  /** @type {HTMLElement | null} */
  const fab = document.querySelector(".fab");
  fab?.click();
  await waitFor(() => document.querySelector("[data-relog]"), {
    label: "relog chips",
  });
  // Wait for the sheet to actually slide in before the camera crops it.
  await waitFor(() => document.querySelector(".sheet.is-open"), {
    label: "add-food sheet open",
  });
  await sleep(400);
  scene("relog-chips", ".add-food-relog");
  await sleep(2000);
  clickFirst("[data-relog]");
  await waitFor(() => document.querySelector(".fab"), {
    label: "home after relog",
  });
  scene("relog-ring", ".calorie-ring--semi");
  await sleep(2400); // ring rises
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
  window.scrollTo({ top: 0 });
  await waitFor(() => document.querySelector(".fab"), {
    label: "home fab",
  }).catch(() => {});
  scene("intro");
  await sleep(900);
}

export async function startDemo() {
  if (REDUCED_MOTION) {
    await renderStaticFrame();
    return;
  }

  // First paint NOW: render the home route immediately, then seed the demo
  // database in the background and re-render once it lands. The marketing
  // page reveals the stage as soon as the home has painted, so the hero never
  // shows an empty canvas while ~1,500 seed records are written.
  renderRoute();
  await sleep(200);
  await seedDemo();
  renderRoute();
  await sleep(600);
  for (let loop = 0; ; loop++) {
    try {
      await reseedDiary();
      await sleep(200);
      if (loop === 0) {
        // First impression only: the whole phone, once per page load.
        scene("intro");
        await sleep(1300);
      } else {
        scene("rest");
        await sleep(900);
      }
      for (const beat of BEATS) {
        try {
          await beat.run();
        } catch (err) {
          console.warn(`demo beat "${beat.name}" failed, skipping`, err);
          scene("rest");
          navigate("#/home");
          await sleep(500);
        }
      }
      scene("rest");
      await sleep(2200); // camera pulls back before the next loop
    } catch (err) {
      console.warn("demo loop aborted, restarting", err);
      await sleep(1000);
    }
    if (loop > 0 && loop % 20 === 0) await reseedDiary(); // safety net
  }
}
