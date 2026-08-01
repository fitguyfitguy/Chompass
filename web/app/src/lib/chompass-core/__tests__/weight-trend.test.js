// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  computeWeightTrend,
  resolveProgressRangeId,
  splitTrendSegments,
} from "../weight-trend.js";

const here = dirname(fileURLToPath(import.meta.url));
const fixture = JSON.parse(
  readFileSync(join(here, "../../../../../../testdata/parity/weight-trend-expected.json"), "utf8"),
);

describe("weight trend (parity fixture)", () => {
  it("resolveProgressRangeId prefers last viewed", () => {
    assert.equal(resolveProgressRangeId("1M", "1Y"), "1M");
    assert.equal(resolveProgressRangeId(null, "1Y"), "1Y");
    assert.equal(resolveProgressRangeId(undefined, undefined), "1W");
    assert.equal(resolveProgressRangeId("nope", "also-nope"), "1W");
  });

  it("matches shared golden cases", () => {
    for (const c of fixture.cases) {
      const actual = computeWeightTrend(c.inputs, {
        windowDays: fixture.windowDays,
        minDaysInWindow: fixture.minDaysInWindow,
      });
      assert.equal(actual.length, c.expected.length, c.id);
      for (let i = 0; i < c.expected.length; i++) {
        assert.equal(actual[i].date, c.expected[i].date, `${c.id}[${i}].date`);
        assert.ok(
          Math.abs(actual[i].valueKg - c.expected[i].valueKg) < 1e-9,
          `${c.id}[${i}].valueKg ${actual[i].valueKg} vs ${c.expected[i].valueKg}`,
        );
      }
    }
  });

  it("splitTrendSegments breaks large gaps", () => {
    const segments = splitTrendSegments(
      [
        { date: "2026-03-02", valueKg: 80 },
        { date: "2026-03-03", valueKg: 79.5 },
        { date: "2026-03-20", valueKg: 78 },
        { date: "2026-03-21", valueKg: 77.5 },
      ],
      7,
    );
    assert.equal(segments.length, 2);
    assert.equal(segments[0].length, 2);
    assert.equal(segments[1].length, 2);
  });
});
