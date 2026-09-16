// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { buildFastingPromptBlock } from "../ai/coach.js";

/**
 * Coach fasting context block (docs/local/PLAN_FASTING_TRACKER.md Phase 6):
 * mirrors the Android ChatService block — present when fasting is on with an
 * active or recorded fast, absent otherwise, and always carrying the
 * no-claims rules line.
 */

/** @param {Partial<import('../db.js').AppPrefs>} p */
function prefs(p = {}) {
  return {
    showFasting: false,
    fastingGoalHours: 0,
    fastingStartedAt: null,
    fastingLastEndedAt: null,
    fastingLastFastStartedAt: null,
    fastingGoalNotified: false,
    ...p,
  };
}

describe("coach fasting context block", () => {
  it("is null when fasting is off", () => {
    assert.equal(buildFastingPromptBlock(prefs()), null);
  });

  it("is null when fasting is on but nothing was ever recorded", () => {
    assert.equal(buildFastingPromptBlock(prefs({ showFasting: true })), null);
  });

  it("describes an active fast with elapsed time and goal", () => {
    const started = Date.now() - 14 * 3_600_000 - 20 * 60_000;
    const block = buildFastingPromptBlock(
      prefs({ showFasting: true, fastingGoalHours: 16, fastingStartedAt: started }),
    );
    assert.ok(block.includes("## Fasting"));
    assert.ok(block.includes("Active fast"));
    assert.ok(block.includes("14h 20m"));
    assert.ok(block.includes("goal 16h"));
    assert.ok(block.includes("Never claim autophagy"));
  });

  it("describes the last completed fast when idle", () => {
    const ended = Date.now() - 3_600_000;
    const started = ended - 15 * 3_600_000 - 40 * 60_000;
    const block = buildFastingPromptBlock(
      prefs({
        showFasting: true,
        fastingStartedAt: null,
        fastingLastEndedAt: ended,
        fastingLastFastStartedAt: started,
      }),
    );
    assert.ok(block.includes("No active fast"));
    assert.ok(block.includes("lasted 15h 40m"));
  });

  it("no-claims rule is always present when the block exists", () => {
    const block = buildFastingPromptBlock(
      prefs({ showFasting: true, fastingStartedAt: Date.now() }),
    );
    assert.ok(block.includes("fasting is a scheduling tool, not medical advice"));
    assert.ok(!block.includes("autophagy") || block.includes("Never claim autophagy"));
  });
});
