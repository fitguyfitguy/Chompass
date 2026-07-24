// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  activityMultipliersLine,
  proteinPerKgLine,
  calorieAdjustmentLine,
  moderateActivityMultiplierRationale,
} from "../chompass-core/goal-formula-reference.js";
import { loadParityFixture } from "../parity-fixtures.js";

const fixture = loadParityFixture("goal-formula-prompt-fragments.json");

describe("goal formula prompt fragments (parity fixture)", () => {
  it("activityMultipliersLine matches fixture", () => {
    assert.equal(activityMultipliersLine(), fixture.activityMultipliersLine);
  });

  it("proteinPerKgLine matches fixture", () => {
    assert.equal(proteinPerKgLine(), fixture.proteinPerKgLine);
  });

  it("calorieAdjustmentLine matches fixture", () => {
    assert.equal(calorieAdjustmentLine(), fixture.calorieAdjustmentLine);
  });

  it("moderateActivityMultiplierRationale matches fixture", () => {
    assert.equal(moderateActivityMultiplierRationale(), fixture.moderateActivityMultiplierRationale);
  });
});
