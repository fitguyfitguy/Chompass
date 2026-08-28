// @ts-check
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { openSheet } from "../ui/sheet.js";

// --- Minimal browser stubs -------------------------------------------------
// sheet.js (and focus-trap.js) only need: element creation/append/remove,
// classList, listener maps, pointer capture, closest-by-tag, scrollTop,
// matchMedia, requestAnimationFrame, and a session-history stack.

class ClassList {
  constructor() {
    this.set = new Set();
  }
  add(...names) {
    for (const n of names) this.set.add(n);
  }
  remove(...names) {
    for (const n of names) this.set.delete(n);
  }
  contains(n) {
    return this.set.has(n);
  }
}

class FakeElement {
  constructor(tag) {
    this.tagName = tag.toUpperCase();
    this.parent = null;
    this.children = [];
    this.style = {};
    this.scrollTop = 0;
    this.classList = new ClassList();
    /** @type {Map<string, Array<{fn: (ev: any) => void, once: boolean}>>} */
    this.listeners = new Map();
  }
  setAttribute(name, value) {
    this[name] = String(value);
  }
  appendChild(child) {
    child.parent = this;
    this.children.push(child);
    return child;
  }
  remove() {
    this.parent = null;
  }
  contains(node) {
    for (let n = node; n; n = n.parent) if (n === this) return true;
    return false;
  }
  closest(selector) {
    const tags = selector.split(",").map((s) => s.trim().toLowerCase());
    for (let n = this; n; n = n.parent) if (tags.includes(n.tagName.toLowerCase())) return n;
    return null;
  }
  addEventListener(type, fn, opts = {}) {
    const list = this.listeners.get(type) ?? [];
    list.push({ fn, once: Boolean(opts.once) });
    this.listeners.set(type, list);
  }
  removeEventListener(type, fn) {
    this.listeners.set(type, (this.listeners.get(type) ?? []).filter((l) => l.fn !== fn));
  }
  setPointerCapture() {}
  querySelectorAll() {
    return [];
  }
  querySelector() {
    return null;
  }
  focus() {}
  fire(type, ev) {
    for (const l of [...(this.listeners.get(type) ?? [])]) {
      l.fn(ev);
      if (l.once) this.removeEventListener(type, l.fn);
    }
  }
}

// One persistent window per file: the module registers its popstate listener
// on the first open and never re-registers, so the object must survive tests.
const win = {
  /** @type {Map<string, Array<(ev: any) => void>>} */
  listeners: new Map(),
  addEventListener(type, fn) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), fn]);
  },
  removeEventListener(type, fn) {
    this.listeners.set(type, (this.listeners.get(type) ?? []).filter((f) => f !== fn));
  },
  fire(type, ev) {
    for (const fn of [...(this.listeners.get(type) ?? [])]) fn(ev);
  },
  matchMedia: () => ({ matches: false }),
};

function makeDocument() {
  const body = new FakeElement("body");
  return {
    body,
    activeElement: null,
    createElement: (tag) => new FakeElement(tag),
    contains: () => false,
    /** @type {Map<string, Array<(ev: any) => void>>} */
    listeners: new Map(),
    addEventListener(type, fn) {
      this.listeners.set(type, [...(this.listeners.get(type) ?? []), fn]);
    },
    removeEventListener(type, fn) {
      this.listeners.set(type, (this.listeners.get(type) ?? []).filter((f) => f !== fn));
    },
    fire(type, ev) {
      for (const fn of [...(this.listeners.get(type) ?? [])]) fn(ev);
    },
  };
}

// Emulates session history: pushState stacks, back() pops and fires popstate
// with the state of the entry landed on (like a real traversal).
function makeHistory() {
  return {
    stack: [null],
    index: 0,
    backCalls: 0,
    get state() {
      return this.stack[this.index];
    },
    pushState(state) {
      this.stack = this.stack.slice(0, this.index + 1);
      this.stack.push(state);
      this.index += 1;
    },
    back() {
      this.backCalls += 1;
      if (this.index === 0) return;
      this.index -= 1;
      win.fire("popstate", { state: this.state });
    },
  };
}

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));
// focus-trap.js does `instanceof HTMLElement` checks.
globalThis.HTMLElement = /** @type {any} */ (FakeElement);

/** @param {FakeElement} target @param {number} x @param {number} y */
function pointerEv(target, x, y) {
  return {
    type: "pointer",

    target,
    pointerId: 7,
    pointerType: "touch",
    button: 0,
    clientX: x,
    clientY: y,
    prevented: false,
    stopped: false,
    preventDefault() {
      this.prevented = true;
    },
    stopPropagation() {
      this.stopped = true;
    },
  };
}

/** Fire a pointer gesture on the panel as if it bubbled from `target`. */
function gesture(panel, target, type, x, y) {
  panel.fire(type, pointerEv(target, x, y));
}

/** Click `target` under the panel: panel capture listeners run first. */
function clickUnder(panel, target) {
  const ev = pointerEv(target, 0, 0);
  ev.type = "click";
  panel.fire("click", ev);
  if (!ev.stopped) target.fire("click", ev);
  return ev;
}

/** Settle a dismissal: the leaving transition finished. */
function finishDismiss(panel) {
  panel.fire("transitionend", { type: "transitionend" });
}

// --- Suite -----------------------------------------------------------------

let hist;
let doc;
/** @type {ReturnType<typeof openSheet>[]} */
let opened;

beforeEach(() => {
  hist = makeHistory();
  doc = makeDocument();
  opened = [];
  globalThis.document = /** @type {any} */ (doc);
  globalThis.window = /** @type {any} */ (win);
  globalThis.history = /** @type {any} */ (hist);
  globalThis.requestAnimationFrame = /** @type {any} */ ((cb) => cb());
});

afterEach(() => {
  // Sheets left open would leak live history callbacks into the next test.
  for (const sheet of opened) {
    sheet.close();
    finishDismiss(sheet.panel);
  }
  opened = [];
});

/** Track sheets so afterEach can force-close leftovers. */
function open(/** @type {any} */ opts) {
  const sheet = openSheet(opts);
  opened.push(sheet);
  return sheet;
}

describe("sheet back-gesture history", () => {
  it("pushes a same-URL sentinel and back dismisses the sheet without extra history calls", async () => {
    let closes = 0;
    const sheet = open({ body: new FakeElement("div"), onClose: () => (closes += 1) });
    assert.equal(typeof hist.state?.chompassSheet, "number");
    assert.equal(hist.backCalls, 0);

    hist.back(); // user back gesture: lands on the route entry below the sentinel
    finishDismiss(sheet.panel);
    await tick();
    assert.equal(closes, 1, "popstate must dismiss the open sheet");
    assert.equal(hist.backCalls, 1, "dismiss-from-back must not consume another entry");
  });

  it("closing the sheet consumes its sentinel exactly once", async () => {
    const sheet = open({ body: new FakeElement("div") });
    sheet.close();
    await tick();
    assert.equal(hist.backCalls, 1, "programmatic close backs out of the sentinel");
    assert.equal(hist.index, 0, "back lands on the route entry");
    assert.ok(doc.body.classList.contains("sheet-open"), "still open until the transition ends");
    finishDismiss(sheet.panel);
    assert.ok(!doc.body.classList.contains("sheet-open"), "closed after transition");
  });

  it("stacked sheets: back dismisses the top sheet, parent survives, then the parent", () => {
    let aCloses = 0;
    let bCloses = 0;
    const a = open({ body: new FakeElement("div"), onClose: () => (aCloses += 1) });
    const b = open({ body: new FakeElement("div"), onClose: () => (bCloses += 1) });

    hist.back(); // pops B's sentinel, lands on A's sentinel
    finishDismiss(b.panel);
    assert.equal(bCloses, 1, "top sheet dismissed");
    assert.equal(aCloses, 0, "parent must survive");

    hist.back(); // pops A's sentinel, lands on the route entry
    finishDismiss(a.panel);
    assert.equal(aCloses, 1);
    assert.equal(hist.backCalls, 2, "back dismissals never consume extra entries");
  });

  it("closing a stacked sheet via its UI does not dismiss the parent", async () => {
    let aCloses = 0;
    const a = open({ body: new FakeElement("div"), onClose: () => (aCloses += 1) });
    const b = open({ body: new FakeElement("div") });
    b.close();
    await tick();
    assert.equal(hist.backCalls, 1);
    assert.equal(aCloses, 0, "consume-pop landing on the parent sentinel must ignore it");
    finishDismiss(b.panel);

    hist.back(); // next back dismisses the parent
    finishDismiss(a.panel);
    assert.equal(aCloses, 1);
  });

  it("an orphaned sentinel (close raced a navigation) is skipped, not eaten", async () => {
    let closes = 0;
    const sheet = open({ body: new FakeElement("div"), onClose: () => (closes += 1) });
    sheet.close();
    hist.pushState(null); // location.hash navigation lands before the deferred consume
    await tick();
    assert.equal(hist.backCalls, 0, "consume must not run once a navigation sits on top");
    finishDismiss(sheet.panel);
    assert.equal(closes, 1);

    hist.back(); // user back: lands on the stale sentinel
    assert.equal(hist.backCalls, 2, "stale sentinel is auto-skipped with one traversal");
    assert.equal(hist.index, 0, "and the back press continues down to the route entry");
  });
});

describe("sheet drag-to-dismiss", () => {
  it("a drag starting on a button dismisses and suppresses the trailing click", () => {
    let clicks = 0;
    let closes = 0;
    const button = new FakeElement("button");
    button.addEventListener("click", () => (clicks += 1));
    const sheet = open({ body: button, onClose: () => (closes += 1) });
    const panel = sheet.panel;

    gesture(panel, button, "pointerdown", 120, 300);
    gesture(panel, button, "pointermove", 122, 400); // commits past the slop
    assert.equal(panel.style.transform, "translateY(100px)", "panel follows the finger");
    gesture(panel, button, "pointerup", 122, 400);
    finishDismiss(panel);

    assert.equal(closes, 1, "100px down-drag dismisses");
    clickUnder(panel, button);
    assert.equal(clicks, 0, "committed drag must not click the button");
  });

  it("a tap on a button still clicks and keeps the sheet open", () => {
    let clicks = 0;
    let closes = 0;
    const button = new FakeElement("button");
    button.addEventListener("click", () => (clicks += 1));
    const sheet = open({ body: button, onClose: () => (closes += 1) });

    gesture(sheet.panel, button, "pointerdown", 120, 300);
    gesture(sheet.panel, button, "pointerup", 120, 300);
    clickUnder(sheet.panel, button);

    assert.equal(clicks, 1);
    assert.equal(closes, 0);
  });

  it("a short drag on a button springs back and still suppresses the click", () => {
    let clicks = 0;
    let closes = 0;
    const button = new FakeElement("button");
    button.addEventListener("click", () => (clicks += 1));
    const sheet = open({ body: button, onClose: () => (closes += 1) });

    gesture(sheet.panel, button, "pointerdown", 120, 300);
    gesture(sheet.panel, button, "pointermove", 121, 340); // committed, under 80px
    gesture(sheet.panel, button, "pointerup", 121, 340);
    clickUnder(sheet.panel, button);

    assert.equal(closes, 0, "under the dismiss threshold it springs back");
    assert.equal(sheet.panel.style.transform, "", "offset cleared");
    assert.equal(clicks, 0, "a committed drag is not a tap");
  });

  it("inputs keep their own gestures", () => {
    let closes = 0;
    const input = new FakeElement("input");
    const sheet = open({ body: input, onClose: () => (closes += 1) });

    gesture(sheet.panel, input, "pointerdown", 120, 300);
    gesture(sheet.panel, input, "pointermove", 120, 420);
    gesture(sheet.panel, input, "pointerup", 120, 420);

    assert.equal(closes, 0);
    assert.equal(sheet.panel.style.transform, undefined, "panel never moved");
  });

  it("a gap swipe while the content is scrolled is a scroll gesture, not a dismiss", () => {
    let closes = 0;
    const div = new FakeElement("div");
    const sheet = open({ body: div, onClose: () => (closes += 1) });
    sheet.panel.scrollTop = 40;

    gesture(sheet.panel, div, "pointerdown", 120, 300);
    gesture(sheet.panel, div, "pointermove", 120, 400);
    gesture(sheet.panel, div, "pointerup", 120, 400);

    assert.equal(closes, 0);
    assert.equal(sheet.panel.style.transform, undefined);
  });

  it("a gap swipe at the top of the content dismisses", () => {
    let closes = 0;
    const div = new FakeElement("div");
    const sheet = open({ body: div, onClose: () => (closes += 1) });

    gesture(sheet.panel, div, "pointerdown", 120, 300);
    gesture(sheet.panel, div, "pointermove", 120, 400);
    gesture(sheet.panel, div, "pointerup", 120, 400);
    finishDismiss(sheet.panel);

    assert.equal(closes, 1);
  });

  it("horizontal movement on a button does not commit the drag", () => {
    let clicks = 0;
    let closes = 0;
    const button = new FakeElement("button");
    button.addEventListener("click", () => (clicks += 1));
    const sheet = open({ body: button, onClose: () => (closes += 1) });

    gesture(sheet.panel, button, "pointerdown", 120, 300);
    gesture(sheet.panel, button, "pointermove", 220, 302); // horizontal intent
    gesture(sheet.panel, button, "pointerup", 220, 302);
    clickUnder(sheet.panel, button);

    assert.equal(closes, 0);
    assert.equal(clicks, 1, "horizontal gesture stays a tap");
  });

  it("handle drag still dismisses", () => {
    let closes = 0;
    const sheet = open({ body: new FakeElement("div"), onClose: () => (closes += 1) });
    const handle = sheet.panel.children[0];

    gesture(sheet.panel, handle, "pointerdown", 180, 40);
    gesture(sheet.panel, handle, "pointermove", 180, 150);
    gesture(sheet.panel, handle, "pointerup", 180, 150);
    finishDismiss(sheet.panel);

    assert.equal(closes, 1);
  });

  it("Escape still dismisses", () => {
    let closes = 0;
    const sheet = open({ body: new FakeElement("div"), onClose: () => (closes += 1) });

    doc.fire("keydown", { key: "Escape", preventDefault() {} });
    finishDismiss(sheet.panel);

    assert.equal(closes, 1);
  });
});
