// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  computeDayTypeActiveStats,
  typicalForProfile,
  resolveActiveTypical,
  mergeDayTotals,
  pruneActiveHistory,
} from "../day-type-active.js";
import { loadParityFixture } from "../../parity-fixtures.js";

const fixture = loadParityFixture("day-type-active-expected.json");
const today = fixture.today;
const windowDays = fixture.windowDays;
const minSamples = fixture.minSamples;

for (const scenario of fixture.scenarios) {
  test(scenario.id, () => {
    if (scenario.kind === "compute" || scenario.kind === "mergeThenCompute") {
      const active =
        scenario.kind === "mergeThenCompute"
          ? mergeDayTotals(scenario.healthConnectByDay, scenario.manualByDay)
          : scenario.activeByDay;
      const result = computeDayTypeActiveStats(scenario.journal, active, today, windowDays);
      const ids = Object.keys(scenario.expect.averages).sort();
      assert.deepEqual(Object.keys(result.byProfileId).sort(), ids);
      for (const [id, row] of Object.entries(scenario.expect.averages)) {
        assert.equal(result.byProfileId[id].averageKcal, row.averageKcal, `${id} avg`);
        assert.equal(result.byProfileId[id].sampleCount, row.sampleCount, `${id} n`);
      }
      for (const [id, kcal] of Object.entries(scenario.expect.typical || {})) {
        assert.equal(typicalForProfile(result, id, minSamples), kcal);
      }
      for (const id of Object.keys(result.byProfileId)) {
        if (!(id in (scenario.expect.typical || {}))) {
          assert.equal(typicalForProfile(result, id, minSamples), null);
        }
      }
      return;
    }
    if (scenario.kind === "resolveTypical") {
      const stats = computeDayTypeActiveStats(scenario.journal, scenario.activeByDay, today, windowDays);
      for (const c of scenario.cases) {
        const r = resolveActiveTypical(c.viewedProfileId, stats, c.blendedMeasured, c.palEstimate, minSamples);
        assert.equal(r.kcal, c.expectKcal);
        assert.equal(r.typicalIsDayType, c.typicalIsDayType);
      }
      return;
    }
    if (scenario.kind === "prune") {
      const pruned = pruneActiveHistory(scenario.map, today, scenario.keepDays);
      assert.deepEqual(Object.keys(pruned).sort(), [...scenario.expectKeys].sort());
      return;
    }
    assert.fail(`Unknown scenario kind: ${scenario.kind}`);
  });
}
