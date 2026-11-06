import test from "node:test";
import assert from "node:assert/strict";
import { clampDiaryDate, DIARY_FUTURE_WEEKS, shiftDate } from "../date.js";

test("clampDiaryDate keeps past and near future, caps at eight weeks", () => {
  const today = "2026-09-14";
  assert.equal(clampDiaryDate("2026-09-10", today), "2026-09-10");
  assert.equal(clampDiaryDate("2026-09-15", today), "2026-09-15");
  const max = shiftDate(today, DIARY_FUTURE_WEEKS * 7);
  assert.equal(clampDiaryDate(max, today), max);
  assert.equal(clampDiaryDate(shiftDate(today, DIARY_FUTURE_WEEKS * 7 + 1), today), max);
  assert.equal(clampDiaryDate("not-a-date", today), today);
});
