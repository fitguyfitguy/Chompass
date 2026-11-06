// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { scanGaps, scanSignature, bannerVisible } from "../fill-gaps.js";
import { todayIso } from "../date.js";

/**
 * Scan shaping + banner gate (FillGapsEngine.computeScan / bannerState
 * parity). localStorage-dependent branches run against an in-memory stub.
 */

/** In-memory localStorage stub (node has none). */
class MemStorage {
  constructor() {
    /** @type {Map<string, string>} */
    this.map = new Map();
  }
  getItem(k) {
    return this.map.has(k) ? this.map.get(k) : null;
  }
  setItem(k, v) {
    this.map.set(k, String(v));
  }
  removeItem(k) {
    this.map.delete(k);
  }
}

/** @param {Partial<import("../chompass-core/models.js").FoodEntry>} [overrides] */
function entry(overrides = {}) {
  return {
    id: crypto.randomUUID(),
    name: "Food",
    mealType: "lunch",
    date: "2026-06-15",
    time: "12:00",
    source: "manual",
    calories: 0,
    proteinG: 0,
    carbsG: 0,
    fatG: 0,
    sugarG: 0,
    addedSugarG: 0,
    fiberG: 0,
    saturatedFatG: 0,
    monounsaturatedFatG: 0,
    polyunsaturatedFatG: 0,
    cholesterolMg: 0,
    sodiumMg: 0,
    potassiumMg: 0,
    transFatG: 0,
    calciumMg: 0,
    ironMg: 0,
    magnesiumMg: 0,
    zincMg: 0,
    vitaminAMcg: 0,
    vitaminCMg: 0,
    vitaminDMcg: 0,
    vitaminB12Mcg: 0,
    vitaminEMg: 0,
    vitaminKMcg: 0,
    folateMcg: 0,
    omega3G: 0,
    caffeineMg: 0,
    ...overrides,
  };
}

test("scanGaps: favorites first (name-sorted), then month entries (newest first)", () => {
  const favorites = [
    entry({ id: "f2", name: "banana bread" }),
    entry({ id: "f1", name: "Apple pie" }),
  ];
  const monthEntries = [
    entry({ id: "d1", name: "Older", date: "2026-06-01", time: "09:00" }),
    entry({ id: "d2", name: "Newer", date: "2026-06-14", time: "20:30" }),
  ];
  const scan = scanGaps({ favorites, monthEntries });
  assert.equal(scan.count, 4);
  assert.deepEqual(
    scan.items.map((it) => [it.favorite, it.entry.id]),
    [
      [true, "f1"],
      [true, "f2"],
      [false, "d2"],
      [false, "d1"],
    ],
  );
});

test("scanGaps: mass gate — micro-only gaps excluded, macro gaps included", () => {
  const microOnly = entry({ id: "d-micro", calories: 100, proteinG: 5, carbsG: 10, fatG: 2, fiberG: null });
  const macroGap = entry({ id: "d-macro", calories: 0, proteinG: 0, carbsG: 10, fatG: 2 });
  const complete = entry({
    id: "d-full",
    name: "Black coffee",
    calories: 2,
    proteinG: 0.3,
    carbsG: 0.4,
    fatG: 0.1,
  });
  const scan = scanGaps({ favorites: [], monthEntries: [microOnly, macroGap, complete] });
  assert.deepEqual(scan.items.map((it) => it.entry.id), ["d-macro"]);
});

test("scanSignature: (kind, id) pairs, changes with the set", () => {
  const a = entry({ id: "x1" });
  const sig = scanSignature([{ favorite: true, entry: a }, { favorite: false, entry: entry({ id: "x2" }) }]);
  assert.equal(sig, "f:x1|d:x2");
  const sig2 = scanSignature([{ favorite: false, entry: a }]);
  assert.notEqual(sig, sig2);
});

test("bannerVisible: empty scan hidden, dismissed hash hidden, shown-today hidden pre-latch", () => {
  const original = globalThis.localStorage;
  globalThis.localStorage = /** @type {any} */ (new MemStorage());
  try {
    const items = [{ favorite: false, entry: entry({ id: "x1" }) }];
    const scan = scanGaps({ favorites: [], monthEntries: items.map((i) => i.entry) });
    assert.equal(scan.count, 1);
    assert.equal(bannerVisible(scan), true);

    // Dismissed for THIS scan hash.
    globalThis.localStorage.setItem("chompass-fill-gaps-banner-dismissed", scan.signature);
    assert.equal(bannerVisible(scan), false);
    globalThis.localStorage.removeItem("chompass-fill-gaps-banner-dismissed");

    // Shown earlier today, no session latch yet (fresh page load).
    globalThis.localStorage.setItem(
      "chompass-fill-gaps-banner-last-shown",
      todayIso(),
    );
    assert.equal(bannerVisible(scan), false);
  } finally {
    globalThis.localStorage = original;
  }
});
