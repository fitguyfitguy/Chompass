// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { computeFastingState, nextFastStartMillis, FastingPhase, MILLIS_PER_HOUR } from "../fasting-state.js";

// Fixed "now" so the tests are deterministic: 2026-07-24 12:00 local.
const NOW = new Date(2026, 6, 24, 12, 0, 0, 0).getTime();
const H = MILLIS_PER_HOUR;

test("fresh auto user is IDLE with a clock anchor and no bar (P0 #2)", () => {
  const s = computeFastingState(
    { fastingAutoWindows: true, fastingGoalHours: 16, fastingEatHours: 8 },
    NOW,
  );
  assert.equal(s.phase, FastingPhase.IDLE);
  // Anchor = today 20:00 (start hour default 20).
  assert.equal(s.anchor, new Date(2026, 6, 24, 20, 0, 0, 0).getTime());
  assert.equal(s.elapsed, 0);
  assert.equal(s.eatElapsed, 0); // never derived from a null lastEnded anchor
  assert.equal(s.reached, false);
  assert.equal(s.showStart, false); // auto cycle is self-driving
  assert.equal(s.showStop, false);
});

test("auto with goal and last-ended: EATING until the next clock start", () => {
  // Last fast ended 10:00 today; start 20:00 → 10h eating phase so far.
  const lastEnded = new Date(2026, 6, 24, 10, 0, 0, 0).getTime();
  const s = computeFastingState(
    {
      fastingAutoWindows: true,
      fastingGoalHours: 16,
      fastingEatHours: 8,
      fastingStartHour: 20,
      fastingStartMinute: 0,
      fastingLastEndedAt: lastEnded,
    },
    NOW,
  );
  assert.equal(s.phase, FastingPhase.EATING);
  assert.equal(s.anchor, new Date(2026, 6, 24, 20, 0, 0, 0).getTime());
  assert.equal(s.eatElapsed, 2 * H); // 12:00 - 10:00
  assert.equal(s.showStart, false);
});

test("auto without goal falls back to the manual eating-window anchor", () => {
  // goal = 0: the cycle can never act (Android heal guard), so the card must
  // show the relative window countdown + Start — never a clock-time anchor.
  const lastEnded = new Date(2026, 6, 24, 9, 0, 0, 0).getTime();
  const s = computeFastingState(
    {
      fastingAutoWindows: true,
      fastingGoalHours: 0,
      fastingEatHours: 8,
      fastingStartHour: 20,
      fastingLastEndedAt: lastEnded,
    },
    NOW,
  );
  assert.equal(s.phase, FastingPhase.EATING);
  assert.equal(s.anchor, lastEnded + 8 * H); // window end, not clock time
  assert.equal(s.autoEffective, false);
  assert.equal(s.showStart, true); // Start button visible in manual fallback
});

test("manual eating window counts down with relative anchor", () => {
  const lastEnded = new Date(2026, 6, 24, 9, 0, 0, 0).getTime();
  const s = computeFastingState(
    { fastingAutoWindows: false, fastingGoalHours: 16, fastingEatHours: 8, fastingLastEndedAt: lastEnded },
    NOW,
  );
  assert.equal(s.phase, FastingPhase.EATING);
  assert.equal(s.anchor, lastEnded + 8 * H);
  assert.equal(s.eatElapsed, 3 * H);
  assert.equal(s.showStart, true);
});

test("manual idle after the eating window lapses", () => {
  const lastEnded = new Date(2026, 6, 23, 9, 0, 0, 0).getTime(); // 27h ago
  const s = computeFastingState(
    { fastingAutoWindows: false, fastingGoalHours: 16, fastingEatHours: 8, fastingLastEndedAt: lastEnded },
    NOW,
  );
  assert.equal(s.phase, FastingPhase.IDLE);
  assert.equal(s.anchor, null);
  assert.equal(s.eatElapsed, 0);
  assert.equal(s.showStart, true);
});

test("running fast: FASTING phase with progress + goal reached", () => {
  const started = new Date(2026, 6, 23, 20, 0, 0, 0).getTime(); // 16h ago
  const s = computeFastingState(
    { fastingAutoWindows: false, fastingGoalHours: 16, fastingEatHours: 8, fastingStartedAt: started },
    NOW,
  );
  assert.equal(s.phase, FastingPhase.FASTING);
  assert.equal(s.elapsed, 16 * H);
  assert.equal(s.reached, true); // exactly at the goal
  assert.equal(s.anchor, null);

  const before = computeFastingState(
    { fastingAutoWindows: false, fastingGoalHours: 16, fastingEatHours: 8, fastingStartedAt: started + 2 * H },
    NOW,
  );
  assert.equal(before.reached, false); // 14h of a 16h goal
});

test("auto-started fast hides Stop (self-driving cycle)", () => {
  const started = NOW - 2 * H;
  const s = computeFastingState(
    {
      fastingAutoWindows: true,
      fastingGoalHours: 16,
      fastingEatHours: 8,
      fastingStartedAt: started,
      fastingAutoStarted: true,
    },
    NOW,
  );
  assert.equal(s.phase, FastingPhase.FASTING);
  assert.equal(s.showStop, false);
  assert.equal(s.showStart, false);
});

test("manual-started fast keeps Stop", () => {
  const started = NOW - 2 * H;
  const s = computeFastingState(
    {
      fastingAutoWindows: true,
      fastingGoalHours: 16,
      fastingEatHours: 8,
      fastingStartedAt: started,
      fastingAutoStarted: false,
    },
    NOW,
  );
  assert.equal(s.phase, FastingPhase.FASTING);
  assert.equal(s.showStop, true); // user can end it
  assert.equal(s.showStart, false);
});

test("nextFastStartMillis clamps hour/minute like Android", () => {
  const now = new Date(2026, 6, 24, 12, 0, 0, 0).getTime();
  assert.equal(nextFastStartMillis(20, 0, now), new Date(2026, 6, 24, 20, 0, 0, 0).getTime());
  // Later today → tomorrow.
  const late = new Date(2026, 6, 24, 21, 0, 0, 0).getTime();
  assert.equal(nextFastStartMillis(20, 0, late), new Date(2026, 6, 25, 20, 0, 0, 0).getTime());
  // Out-of-range values coerce to the nearest bound (Android coerceIn); the
  // 00:00 instant already passed at noon → next occurrence is tomorrow.
  assert.equal(nextFastStartMillis(99, 0, now), new Date(2026, 6, 24, 23, 0, 0, 0).getTime());
  assert.equal(nextFastStartMillis(-1, 0, now), new Date(2026, 6, 25, 0, 0, 0, 0).getTime());
});
