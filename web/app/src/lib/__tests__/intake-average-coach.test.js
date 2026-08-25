// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { buildIntakeAverageBlock } from "../ai/coach.js";

/**
 * Concise default intake context (#60 phase 6 follow-up): mirrors the Android
 * ChatService intakeAverageLine — mean daily kcal + P/C/F over complete logged
 * days (today excluded), absent when there are none.
 */

const mk = (date, kcal, p, c, f) => ({ date, calories: kcal, proteinG: p, carbsG: c, fatG: f });

describe("coach intake average block", () => {
  it("averages kcal and macros over complete logged days, excluding today", () => {
    const block = buildIntakeAverageBlock(
      [mk("2026-08-24", 2000, 150, 200, 60), mk("2026-08-23", 3000, 150, 300, 80), mk("2026-08-25", 9000, 900, 900, 900)],
      "2026-08-25",
    );
    assert.ok(block);
    assert.match(block, /Average intake over 2 logged days: 2500 kcal, 150g protein, 250g carbs, 70g fat/);
    assert.match(block, /Judge intake questions against this/);
  });

  it("is null with no complete logged days", () => {
    assert.equal(buildIntakeAverageBlock([mk("2026-08-25", 1000, 1, 1, 1)], "2026-08-25"), null);
    assert.equal(buildIntakeAverageBlock([], "2026-08-25"), null);
  });

  it("uses the singular form for exactly one logged day", () => {
    const block = buildIntakeAverageBlock([mk("2026-08-24", 1800, 120, 180, 55)], "2026-08-25");
    assert.match(block ?? "", /over 1 logged day: 1800 kcal/);
  });
});
