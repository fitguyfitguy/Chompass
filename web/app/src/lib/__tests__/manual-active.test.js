// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  addActiveGaugeTarget,
  makeManualActiveEntry,
  resolveWebActiveBurn,
} from "../manual-active.js";

describe("manual active burn helpers", () => {
  it("makeManualActiveEntry defaults name and clamps kcal", () => {
    const e = makeManualActiveEntry("2026-08-03", "  ", -5);
    assert.equal(e.name, "Activity");
    assert.equal(e.calories, 0);
    assert.equal(e.date, "2026-08-03");
    assert.ok(e.id);
  });

  it("resolveWebActiveBurn sums estimate and manual", () => {
    const burn = resolveWebActiveBurn(400, 150);
    assert.deepEqual(burn, { calories: 550, source: "estimated" });
    assert.equal(resolveWebActiveBurn(0, 0), null);
    assert.deepEqual(resolveWebActiveBurn(0, 220), { calories: 220, source: "manual" });
  });

  it("addActiveGaugeTarget falls back to full target without burn", () => {
    assert.equal(addActiveGaugeTarget(2200, 1800, null), 2200);
    assert.equal(addActiveGaugeTarget(2200, 1800, { calories: 400 }), 2200);
  });
});
