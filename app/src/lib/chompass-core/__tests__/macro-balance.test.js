// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { rebalanceGoalMacros } from "../macro-balance.js";

const fixturePath = fileURLToPath(
  new URL("../../../../../../testdata/parity/macro-balance-expected.json", import.meta.url),
);
const { scenarios } = JSON.parse(readFileSync(fixturePath, "utf8"));

/** @param {{protein: number, carbs: number, fat: number}} g */
const kcal = (g) => g.protein * 4 + g.carbs * 4 + g.fat * 9;

test("parity golden vectors", () => {
  for (const s of scenarios) {
    const result = rebalanceGoalMacros(
      s.calories,
      s.input.protein,
      s.input.carbs,
      s.input.fat,
      s.mode,
      s.locked,
      s.fatFloorG,
    );
    assert.deepEqual(
      { protein: result.protein, carbs: result.carbs, fat: result.fat },
      s.expected,
      s.id,
    );
  }
});

test("standard cut keeps protein and fat, carbs absorb to the exact total", () => {
  const g = rebalanceGoalMacros(1500, 150, 215, 60, "standard", [], 48);
  assert.deepEqual(g, { protein: 150, carbs: 90, fat: 60 });
  assert.equal(kcal(g), 1500);
});

test("keto infeasible yields protein, keeps the fat floor", () => {
  const g = rebalanceGoalMacros(1000, 150, 30, 70, "keto", [], 57);
  assert.deepEqual(g, { protein: 91, carbs: 30, fat: 57 });
  assert.equal(kcal(g), 997);
});
