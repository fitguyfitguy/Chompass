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
import { reseedDiary, reseedWeights, seedDemo } from "./demo-seed.js";
import { weights } from "../lib/db.js";

const VIEW = document.getElementById("view");
if (!(VIEW instanceof HTMLElement)) throw new Error("demo: #view missing");

/** ?debug=1 turns on console tracing + parent-side status overlays. */
const DEBUG = new URLSearchParams(location.search).has("debug");

/** Demo telemetry: seed/beat timings + module budget (window.__demoStats). */
const stats = {
  loopIndex: 0,
  loops: [],
  seedsMs: {},
  firstBeatMs: null,
  moduleBytes: 0,
  moduleCount: 0,
  beatFailures: 0,
  lastBeatFailure: null,
};

/** Count JS modules + transfer bytes fetched so far (performance timeline). */
function sampleModuleStats() {
  let bytes = 0;
  let count = 0;
  for (const r of performance.getEntriesByType("resource")) {
    if (!r.name.endsWith(".js")) continue;
    count += 1;
    // transferSize is a ResourceTiming field (0 when served from cache).
    bytes += (/** @type {{transferSize?: number}} */ (r)).transferSize || 0;
  }
  stats.moduleCount = count;
  stats.moduleBytes = bytes;
}

const ROUTES = {
  home: "<diary-view></diary-view>",
  entry: "<entry-form></entry-form>",
  analyze: "<analyze-view></analyze-view>",
  scan: "<barcode-scanner></barcode-scanner>",
  progress: "<progress-view></progress-view>",
};

// Realtime tracing: every scene the driver announces is logged to the console
// with a plain-language description of what the app should be showing, so the
// hero's camera view can be matched against the demo timeline while watching.
const SCENE_LABELS = {
  intro: "full phone intro",
  rest: "pull back to hero crop (rest)",
  "ai-typing": "AI note being typed",
  "ai-stream": "AI analysis streaming — macros filling in",
  "ai-review": "entry review sheet",
  "ai-ring": "calorie ring after logging",
  "barcode-scan": "mock barcode viewfinder — cereal box + lock brackets",
  "barcode-card": "Open Food Facts product card",
  "plate-scan": "mock plate camera — framing the plate",
  "trend-warp-close": "1M weight chart close-up (final stretch near goal)",
  "trend-warp": "weight chart warping 3M→6M→1Y→All — the success arc",
  "trend-log": "log-weight button on the weight card",
  "trend-log-dialog": "weigh-in input dialog — 64.3 kg",
  "trend-logged": "weight chart extended with new readings",
  "trend-stats": "current/goal/net stats badges",
  "trend-bodyfat": "body-fat chart",
  "trend-forecast": "weight forecast card",
  "relog-chips": "relog favorites sheet",
  "relog-ring": "calorie ring after relog",
};

/** @type {boolean} */
let paused = false;
/** @type {boolean} */
let stopped = false; // static mode: parent detected a restart loop; stop beating
/** @type {Array<() => void>} */
let resumeListeners = [];

/** Thrown by sleep() once the parent switches the demo to static mode. */
const STOPPED = new Error("demo stopped (static mode)");

function setPaused(next) {
  if (next === paused) return;
  paused = next;
  console.log(next ? "[demo] ⏸ paused — sequence stalls" : "[demo] ▶ resumed");
  if (!paused) {
    const listeners = resumeListeners;
    resumeListeners = [];
    listeners.forEach((fn) => fn());
  }
}

/**
 * Pause-aware sleep: while paused, the timer fires but the promise resolves
 * only after a resume — total wait becomes ms + paused duration. In static
 * mode (parent detected a restart loop) the promise rejects so the beat loop
 * unwinds immediately.
 * @param {number} ms
 * @returns {Promise<void>}
 */
function sleep(ms) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (stopped) reject(STOPPED);
      else if (paused) resumeListeners.push(resolve);
      else resolve();
    }, ms);
  });
}

/**
 * Poll for a condition with pause-aware steps. Time spent paused does NOT
 * count against the timeout — a pause mid-beat must not burn the budget and
 * fail the beat on resume (spurious "beat failed: timeout" warnings).
 * @param {() => unknown} fn
 * @param {{ timeout?: number, step?: number, label?: string }} [opts]
 * @returns {Promise<void>}
 */
async function waitFor(
  fn,
  { timeout = 9000, step = 80, label = "condition" } = {},
) {
  let elapsed = 0;
  let last = performance.now();
  while (elapsed < timeout) {
    if (fn()) return;
    await sleep(step);
    const now = performance.now();
    if (!paused) elapsed += now - last;
    last = now;
  }
  throw new Error(`demo: timeout waiting for ${label}`);
}

/** @param {string} hash */
function navigate(hash) {
  // Same-hash navigations are a no-op: remounting the whole view at every
  // beat start (5×/loop) churned the diary-view for no benefit. Beats that
  // need a fresh mount (relog after reseed) arrive via a real hash change.
  if (location.hash === hash) return;
  console.log(`[demo] route → ${hash}`);
  location.hash = hash;
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
// The driver intentionally does NOT pause on its own visibilitychange —
// embedded webviews can report the iframe as hidden while it is plainly on
// screen (Cursor/VS Code previews do this), which would stall the sequence
// forever. The parent page owns pause/resume via postMessage.
window.addEventListener("message", (ev) => {
  const data = /** @type {{source?: string, type?: string, paused?: boolean, static?: boolean}} */ (ev.data);
  if (data?.source !== "chompass-hero") return;
  if (data.type === "pause") setPaused(true);
  else if (data.type === "play") setPaused(false);
  else if (data.type === "state") {
    setPaused(Boolean(data.paused));
    if (data.static) runStaticMode(); // survives the boot race via the handshake
  } else if (data.type === "static") runStaticMode();
});

/**
 * Static mode: the parent detected a restart loop (the iframe document keeps
 * reloading — embedded previews discard/restore it, and some Firefox builds
 * do too). Instead of looping forever from loop 0, render a single stable
 * home frame and stop beating, so the hero degrades gracefully.
 * @returns {Promise<void>}
 */
async function runStaticMode() {
  if (stopped) return;
  console.warn("[demo] ⚠ static mode: parent detected a restart loop — stopping the demo");
  try {
    await seedDemo();
  } catch {
    /* ignore */
  }
  renderRoute();
  stopped = true;
  scene("intro");
}

// Handshake: tell the parent we're alive. The parent may have sent
// pause/play before this listener existed (e.g. it resumed a reloaded
// iframe), so ask for the current state instead of racing it.
try {
  parent.postMessage(
    { source: "chompass-hero", type: "hello", loop: stats.loopIndex, ts: Math.round(performance.now()) },
    "*",
  );
} catch {
  /* ignore */
}

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
    console.log(
      `[demo] ▶ scene ${key ?? "rest"} — ${SCENE_LABELS[key] ?? "pull back to hero crop"}`,
    );
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
  // preventScroll: focusing would otherwise scroll the PARENT marketing page
  // (and the iframe) to reveal the input — the hero camera must be the only
  // thing that moves.
  el.focus({ preventScroll: true });
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
  await sleep(1200);
  /** @type {HTMLElement | null} */
  const fab = document.querySelector(".fab");
  fab?.click();
  await waitFor(() => document.querySelector('[data-add="note"]'), {
    label: "add-food note tile",
  });
  await sleep(1100);
  clickFirst('[data-add="note"]');
  await waitFor(() => document.querySelector("#analyze-form #note"), {
    label: "analyze note field",
  });
  await sleep(600);
  // Zoom into the note field itself: the AI prompt is where the story starts.
  scene("ai-typing", "#analyze-form #note");
  const note = /** @type {HTMLTextAreaElement | null} */ (
    document.querySelector("#analyze-form #note")
  );
  if (note) await typeInto(note, text, 45);
  await sleep(600);
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
  await sleep(1500); // review screen settles; nutrition fields animate in
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
  await streamAnalyzeOverlay();
  await finishEntryForm({ log: true, sceneKey: "ai-review" });
  scene("ai-ring", ".calorie-ring--semi");
  await sleep(3000); // calorie ring rises
}

/** Shared overlay sequence for the note and plate beats: quick-cut on the
    phase pill, then crop the partial card as the macros fill in. */
async function streamAnalyzeOverlay() {
  await waitFor(() => document.querySelector(".analyze-overlay"), {
    label: "analyze overlay",
  });
  scene("ai-stream", ".analyze-overlay__phase");
  await sleep(900);
  await waitFor(
    () =>
      document.querySelectorAll(".analyze-partial__macro:not(.is-pending)")
        .length >= 3,
    { timeout: 5000, label: "analyze partial macros" },
  ).catch(() => {});
  // Photo mode renders a preview above the partial card, which can push the
  // card below the phone viewport — announcing a crop the camera can never
  // resolve made it fall back to rest mid-stream. Crop the whole overlay
  // there; the note beat keeps the tighter partial-card crop.
  const partial = document.querySelector(".analyze-partial");
  const partialFits =
    partial && partial.getBoundingClientRect().top < window.innerHeight - 8;
  scene("ai-stream", partialFits ? ".analyze-partial" : ".analyze-overlay");
  await sleep(3800); // phases stream, fields fill, auto-navigates to review
}

async function beatBarcode() {
  navigate("#/scan");
  await waitFor(() => document.querySelector("[data-mock-barcode]"), {
    label: "mock barcode viewfinder",
  });
  await sleep(800);
  scene("barcode-scan", ".scanner-frame--mock");
  await sleep(2600); // framing + a lock flash
  const status = document.querySelector("#scanner-status");
  if (status) status.textContent = "Scanning…";
  await sleep(900);
  // The mock feed has no detector — the driver plays it, the same scripted
  // hand that types into the manual form in the pre-demo flow.
  const scanner = /** @type {any} */ (
    document.querySelector("barcode-scanner")
  );
  await scanner?.lookupAndPrefill("0049000028911");
  await finishEntryForm({ log: false, sceneKey: "barcode-card" });
}

async function beatPlate() {
  navigate(
    `#/analyze?date=${new Date().toISOString().slice(0, 10)}&mode=photo`,
  );
  await waitFor(() => document.querySelector("[data-mock-plate]"), {
    label: "mock plate camera",
  });
  await sleep(800);
  scene("plate-scan", ".scanner-frame--plate");
  await sleep(2700); // framing + sweep settle
  const status = document.querySelector("#plate-status");
  if (status) status.textContent = "Analyzing…";
  await sleep(900);
  const analyze = /** @type {any} */ (document.querySelector("analyze-view"));
  await analyze?.captureMockPhoto();
  await streamAnalyzeOverlay();
  await finishEntryForm({ log: true, sceneKey: "ai-review" });
  scene("ai-ring", ".calorie-ring--semi");
  await sleep(3000); // ring rises
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
  if (!el) return;
  // Manual scroll of ONLY the iframe document: scrollIntoView would also
  // scroll every ancestor scroll container — including the marketing page —
  // jumping the hero out of the user's view during the warp beat.
  const rect = el.getBoundingClientRect();
  const doc = el.ownerDocument;
  doc.documentElement.scrollTop +=
    rect.top + rect.height / 2 - doc.documentElement.clientHeight / 2;
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
    timeout: 9000,
    label: `range ${id} chart`,
  });
  await sleep(280); // let the new chart paint
}

/** Put a weigh-in entry directly (same store the real Log weight dialog
 *  writes to). daysAgo 0 = today. */
async function logWeighIn(kg, daysAgo) {
  const date = new Date(Date.now() - daysAgo * 864e5);
  await weights.put({ id: crypto.randomUUID(), date: date.toISOString(), weightKg: kg });
}

async function beatTrend() {
  // Reset to the canonical 2y S-curve journey ending 3 days ago; the missing
  // readings get logged live below, so every loop tells the same story.
  await reseedWeights();
  navigate("#/progress");
  window.scrollTo({ top: 0 });
  await waitFor(() => document.querySelector('[data-range="All"]'), {
    label: "range chips",
  });
  await sleep(900);

  // 1) Close-up on the 1M window: the final stretch — readings bounce around
  // the goal weight while the trend creeps down.
  await clickRange("1M");
  centerOn(document.querySelector(CHART_SEL));
  await sleep(500);
  scene("trend-warp-close", CHART_SEL);
  await sleep(3200);

  // 2) Live weigh-ins: two readings land one at a time (the chart extends at
  // the right edge each time), then the real Log weight dialog types today's
  // reading — the honest "logging a weigh-in" beat.
  const last = (await weights.all()).sort((a, b) => b.date.localeCompare(a.date))[0].weightKg;
  for (const [daysAgo, dwell] of [[2, 1100], [1, 1100]]) {
    await logWeighIn(Math.round((last + (Math.random() * 0.6 - 0.3)) * 10) / 10, daysAgo);
    await clickRange("1M"); // re-render shows the new reading on the chart
    centerOn(document.querySelector(CHART_SEL));
    await sleep(350);
    scene("trend-logged", CHART_SEL);
    await sleep(dwell);
  }

  scene("trend-log", "[data-log-weight]");
  await sleep(900);
  const before = document.querySelector(CHART_SEL);
  clickFirst("[data-log-weight]");
  await waitFor(() => document.querySelector(".dialog__input"), {
    label: "log-weight dialog",
  });
  await sleep(500);
  scene("trend-log-dialog", ".dialog__panel");
  await sleep(700);
  const input = /** @type {HTMLInputElement | null} */ (document.querySelector(".dialog__input"));
  if (input) setValue(input, "64.3");
  await sleep(300);
  clickFirst(".dialog .btn--primary");
  await waitFor(
    () =>
      !document.querySelector(".dialog") &&
      document.querySelector(CHART_SEL) !== before,
    { timeout: 9000, label: "chart after weigh-in" },
  );
  scene("trend-logged", CHART_SEL);
  await sleep(2400);

  // 3) Warp: ranges widen with accelerating tempo — the loss picks up pace,
  // then eases into the goal. 1Y steps out to the whole card so the camera
  // zooms out while the data expands; All settles on the full S-curve.
  const warp = [
    { range: "3M", selector: CHART_SEL, dwell: 1800 },
    { range: "6M", selector: CHART_SEL, dwell: 1400 },
    { range: "1Y", selector: ".card.card--glass", card: 0, dwell: 1200 },
    { range: "All", selector: CHART_SEL, dwell: 3200 },
  ];
  for (const step of warp) {
    await clickRange(step.range);
    centerOn(
      step.card != null
        ? cardAt((_, i) => i === step.card)
        : document.querySelector(step.selector),
    );
    await sleep(500);
    scene("trend-warp", step.selector, step.card ?? 0);
    await sleep(step.dwell);
  }

  // 4) The important numbers: current / goal / net / average badges.
  const badges = document.querySelector(".card.card--glass .stat-badges");
  centerOn(badges);
  await sleep(500);
  scene("trend-stats", ".card.card--glass .stat-badges");
  await sleep(3200);

  // 5) Body-fat card (behind the metric toggle — the section has no glass
  // wrapper, so the camera crops its chart; the forecast card lives in the
  // weight section, so toggle back before the payoff).
  const toggleBf = document.querySelector('[data-metric="body_fat"]');
  if (toggleBf) {
    clickFirst('[data-metric="body_fat"]');
    await waitFor(() => document.querySelector("[data-log-bf]"), {
      label: "body-fat section",
    });
    centerOn(document.querySelector(".chart-svg"));
    await sleep(500);
    scene("trend-bodyfat", ".chart-svg", 0);
    await sleep(2900);
    clickFirst('[data-metric="weight"]');
    await waitFor(() => document.querySelector('[data-range="All"]'), {
      label: "weight section back",
    });
    await sleep(400);
  }

  // 6) Forecast card: predicted change, days to goal, adaptive message.
  const fIndex = cardIndex((c) =>
    /weight forecast/i.test(
      c.querySelector("h2.chart-title")?.textContent ?? "",
    ),
  );
  if (fIndex >= 0) {
    centerOn(cardAt((_, i) => i === fIndex));
    await sleep(500);
    scene("trend-forecast", ".card.card--glass", fIndex);
    await sleep(3200);
  }

  window.scrollTo({ top: 0, behavior: "smooth" });
  await sleep(900);
}

async function beatRelog() {
  navigate("#/home");
  window.scrollTo({ top: 0 });
  await waitFor(() => document.querySelector(".fab"), { label: "home fab" });
  await sleep(900);
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
  await sleep(600);
  scene("relog-chips", ".add-food-relog");
  await sleep(2600);
  clickFirst("[data-relog]");
  await waitFor(() => document.querySelector(".fab"), {
    label: "home after relog",
  });
  scene("relog-ring", ".calorie-ring--semi");
  await sleep(3000); // ring rises
}

const BEATS = [
  { name: "ai", run: beatAi },
  { name: "barcode", run: beatBarcode },
  { name: "plate", run: beatPlate },
  { name: "trend", run: beatTrend },
  { name: "relog", run: beatRelog },
];

export async function startDemo() {
  // Reduced motion does NOT stop the demo: the parent camera (hero.js) snaps
  // between scenes instead of animating when the visitor prefers reduced
  // motion (e.g. Firefox on Windows with system animations off), so the hero
  // still tells the full story as a static slideshow. Freezing on a single
  // frame made the hero look broken there — it never got past the intro.
  // First paint NOW: render the home route immediately, then seed the demo
  // database in the background and re-render once it lands. The marketing
  // page reveals the stage as soon as the home has painted, so the hero never
  // shows an empty canvas while the seed records are written. If the parent
  // switches us to static mode during boot (restart loop), the STOPPED error
  // unwinds here.
  renderRoute();
  /** @type {any} */ (window).__demoStats = stats;
  try {
    await sleep(200);
    const seedT0 = performance.now();
    await seedDemo();
    stats.seedsMs.initial = Math.round(performance.now() - seedT0);
    renderRoute();
    await sleep(600);
  } catch (err) {
    if (err !== STOPPED) throw err;
    return; // static mode took over during boot
  }
  const loop0Start = performance.now();
  let lastLoopStart = loop0Start;
  // Watchdog (Phase 0): a fresh `loop 0` can only happen if the document
  // reloaded — the open Firefox bug. If no loop advancement happens within
  // ~2× the nominal loop budget (~60 s), log a distinct STALLED marker and
  // tell the parent, instead of silently looping. This distinguishes a
  // reload-loop from a hang (the missing evidence per docs/DEMO_HERO_FIREFOX.md).
  const WATCHDOG_MS = 150_000;
  setInterval(() => {
    if (performance.now() - lastLoopStart > WATCHDOG_MS) {
      console.warn(
        `[demo] ⚠ STALLED: no loop advancement in ${Math.round((performance.now() - lastLoopStart) / 1000)}s`,
      );
      try {
        parent.postMessage(
          { source: "chompass-hero", type: "stalled", ms: Math.round(performance.now() - lastLoopStart) },
          "*",
        );
      } catch {
        /* ignore */
      }
      lastLoopStart = performance.now();
    }
  }, 30_000);
  for (let loop = 0; ; loop++) {
    if (stopped) break;
    stats.loopIndex = loop;
    lastLoopStart = performance.now();
    const loopT0 = performance.now();
    try {
      console.log(`[demo] ═══ loop ${loop} start ═══`);
      const reseedT0 = performance.now();
      await reseedDiary();
      stats.seedsMs[`reseed:${loop}`] = Math.round(performance.now() - reseedT0);
      await sleep(200);
      if (loop === 0) {
        // The intro shows the whole phone: paint the reseeded home (seedDemo
        // only boots profile/prefs/favorites now — the diary lands here).
        renderRoute();
      }
      if (loop === 0) {
        // First impression only: the whole phone, once per page load.
        scene("intro");
        stats.firstBeatMs = Math.round(performance.now() - loop0Start);
        await sleep(1500);
      } else {
        scene("rest");
        await sleep(1100);
      }
      for (const [beatIndex, beat] of BEATS.entries()) {
        console.log(
          `[demo] ▶ beat ${beat.name} (${beatIndex + 1}/${BEATS.length}) · loop ${loop}`,
        );
        try {
          await beat.run();
        } catch (err) {
          if (err === STOPPED) throw err; // static mode: unwind the loop
          stats.beatFailures += 1;
          stats.lastBeatFailure = `${beat.name}: ${err instanceof Error ? err.message : String(err)}`;
          console.warn(`[demo] ⚠ beat "${beat.name}" failed, skipping`, err);
          scene("rest");
          navigate("#/home");
          await sleep(500);
        }
      }
      scene("rest");
      await sleep(2800); // camera pulls back before the next loop
      stats.loops.push({ index: loop, ms: Math.round(performance.now() - loopT0) });
      sampleModuleStats();
      if (DEBUG) {
        console.table(stats.loops.slice(-3));
        console.log("[demo] __demoStats", JSON.stringify(stats, null, 2));
      }
      console.log(
        `[demo] ═══ loop ${loop} complete — rest 2.8s, next loop ═══`,
      );
    } catch (err) {
      if (err === STOPPED) {
        console.log("[demo] ═══ static mode — beat loop stopped ═══");
        break;
      }
      console.warn("demo loop aborted, restarting", err);
      await sleep(1000);
    }
    if (loop > 0 && loop % 20 === 0) await reseedDiary(); // safety net
  }
}
