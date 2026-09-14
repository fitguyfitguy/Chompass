// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { setActiveLocale } from "../i18n/index.js";
import {
  energyUnitFromPrefs,
  energyQuantity,
  energyToKcal,
  energyUnitLabel,
  formatEnergy,
} from "../energy-format.js";

describe("energy format conversions", () => {
  it("kcal → kJ vectors (Codeberg #100)", () => {
    assert.equal(energyQuantity(0, "kj"), 0);
    assert.equal(energyQuantity(1, "kj"), 4);
    assert.equal(energyQuantity(2000, "kj"), 8368);
    assert.equal(energyQuantity(1200, "kj"), 5021);
    assert.equal(energyQuantity(239, "kj"), 1000);
  });

  it("kcal mode is the identity", () => {
    assert.equal(energyQuantity(2000, "kcal"), 2000);
    assert.equal(energyQuantity(0, "kcal"), 0);
    assert.equal(energyToKcal(2000, "kcal"), 2000);
  });

  it("kJ → kcal round trip", () => {
    assert.equal(energyToKcal(8368, "kj"), 2000);
    assert.equal(energyToKcal(5021, "kj"), 1200);
    for (let kcal = 0; kcal <= 6000; kcal += 25) {
      const kj = energyQuantity(kcal, "kj");
      // Within 1 kJ of the exact conversion after the round trip.
      assert.ok(Math.abs(energyQuantity(energyToKcal(kj, "kj"), "kj") - kj) <= 1);
    }
  });

  it("pref resolution defaults to kcal", () => {
    assert.equal(energyUnitFromPrefs(undefined), "kcal");
    assert.equal(energyUnitFromPrefs({}), "kcal");
    assert.equal(energyUnitFromPrefs({ energyUnit: "kcal" }), "kcal");
    assert.equal(energyUnitFromPrefs({ energyUnit: "kj" }), "kj");
    assert.equal(energyUnitFromPrefs({ energyUnit: "KJ" }), "kcal"); // exact match only
  });

  it("formatEnergy renders number + localized unit", () => {
    setActiveLocale("en");
    assert.equal(energyUnitLabel("kcal"), "kcal");
    assert.equal(energyUnitLabel("kj"), "kJ");
    assert.equal(formatEnergy(2000), "2,000 kcal");
    assert.equal(formatEnergy(2000, "kcal"), "2,000 kcal");
    assert.equal(formatEnergy(2000, "kj"), "8,368 kJ");
  });
});
