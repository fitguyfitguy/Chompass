// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { buildIntakeAverageBlock } from "../ai/coach.js";

/**
 * Concise default intake context (#60 phase 6 follow-up): mirrors the Android
 * ChatService intakeAverageLines — 7-day and 30-day mean kcal + P/C/F over
 * complete logged days (today excluded), absent when a window has no logged
 * days.
 */

const mk = (date, kcal, p, c, f) => ({ date, calories: kcal, proteinG: p, carbsG: c, fatG: f });

describe("coach intake average block", () => {
  it("reports 7-day and 30-day windows with logged-day counts", () => {
    const block = buildIntakeAverageBlock(
      [
        mk("2026-08-24", 2000, 150, 200, 60),
        mk("2026-08-23", 3000, 150, 300, 80),
        mk("2026-08-22", 4000, 150, 400, 100),
        mk("2026-08-01", 1000, 101, 100, 31), // inside 30d, outside 7d
        mk("2026-08-25", 9000, 900, 900, 900), // today: excluded
      ],
      "2026-08-25",
    );
    assert.ok(block);
    assert.match(block, /Average intake last 7 days \(3 logged days\): 3000 kcal, 150g protein, 300g carbs, 80g fat/);
    assert.match(block, /Average intake last 30 days \(4 logged days\): 2500 kcal, 138g protein, 250g carbs, 68g fat/);
    assert.match(block, /Judge intake questions against these/);
  });

  it("omits the 7-day line when only older logs exist", () => {
    const block = buildIntakeAverageBlock([mk("2026-08-05", 1800, 120, 180, 55)], "2026-08-25");
    assert.ok(block);
    assert.doesNotMatch(block, /last 7 days/);
    assert.match(block, /Average intake last 30 days \(1 logged day\): 1800 kcal/);
  });

  it("is null with no complete logged days", () => {
    assert.equal(buildIntakeAverageBlock([mk("2026-08-25", 1000, 1, 1, 1)], "2026-08-25"), null);
    assert.equal(buildIntakeAverageBlock([], "2026-08-25"), null);
  });
});
