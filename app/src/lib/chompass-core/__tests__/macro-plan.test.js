// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  resolveDay,
  resolveDayJournaled,
  averageForward,
  journalAverage,
  upsertJournalEntry,
  mergeJournal,
  pruneJournal,
  gapFillJournal,
  goalJournalIdFor,
  isoToEpochDay,
  epochDayToIso,
  isoWeekday,
  JOURNAL_KEEP_DAYS,
} from "../macro-plan.js";
import { loadParityFixture } from "../../parity-fixtures.js";

/**
 * Golden vectors from testdata/parity/macro-plan-expected.json — keep in sync
 * with Android MacroPlanParityTest / docs/local/MACRO_PROFILES_DESIGN.md
 * (MACRO-CYCLE-A/B/D).
 */

const fixture = loadParityFixture("macro-plan-expected.json");
const base = fixture.base;

for (const scenario of fixture.scenarios) {
  test(scenario.id, () => {
    if (scenario.kind === "resolve") {
      const r = resolveDay(scenario.plan, base, scenario.date);
      if (scenario.expect.profileId !== undefined) assert.equal(r.profileId, scenario.expect.profileId);
      if (scenario.expect.profileName !== undefined) assert.equal(r.profileName, scenario.expect.profileName);
      assert.deepEqual(r.targets, {
        calories: scenario.expect.calories,
        proteinG: scenario.expect.proteinG,
        carbsG: scenario.expect.carbsG,
        fatG: scenario.expect.fatG,
      });
      return;
    }
    if (scenario.kind === "averageForward") {
      const r = averageForward(scenario.plan, base, scenario.today, scenario.windowDays ?? null);
      assert.deepEqual(r, {
        calories: scenario.expect.calories,
        proteinG: scenario.expect.proteinG,
        carbsG: scenario.expect.carbsG,
        fatG: scenario.expect.fatG,
      });
      return;
    }
    if (scenario.kind === "resolveJournaled") {
      const r = resolveDayJournaled(
        scenario.entries,
        scenario.plan,
        base,
        scenario.date,
        scenario.today,
      );
      if (scenario.expect.profileId !== undefined) assert.equal(r.profileId, scenario.expect.profileId);
      if (scenario.expect.profileName !== undefined) assert.equal(r.profileName, scenario.expect.profileName);
      assert.deepEqual(r.targets, {
        calories: scenario.expect.calories,
        proteinG: scenario.expect.proteinG,
        carbsG: scenario.expect.carbsG,
        fatG: scenario.expect.fatG,
      });
      return;
    }
    if (scenario.kind === "journalAverage") {
      const r = journalAverage(scenario.entries, scenario.from, scenario.to);
      if (scenario.expect.null) {
        assert.equal(r, null);
      } else {
        assert.deepEqual(r, {
          calories: scenario.expect.calories,
          proteinG: scenario.expect.proteinG,
          carbsG: scenario.expect.carbsG,
          fatG: scenario.expect.fatG,
        });
      }
      return;
    }
    assert.fail(`Unknown scenario kind: ${scenario.kind}`);
  });
}

// -- ISO helpers ------------------------------------------------------------------

test("iso helpers round-trip and reject malformed dates", () => {
  assert.equal(epochDayToIso(isoToEpochDay("2026-09-01")), "2026-09-01");
  assert.equal(isoWeekday("2026-09-01"), "TUESDAY");
  assert.equal(isoWeekday("2026-08-31"), "MONDAY");
  assert.ok(Number.isNaN(isoToEpochDay("2026-02-31")));
  assert.ok(Number.isNaN(isoToEpochDay("not-a-date")));
});

// -- Journal helpers (mirror of Android GoalJournalTest) --------------------------

const entry = (date, calories, updatedAtMillis = 0) => ({
  date, calories, proteinG: 160, carbsG: 255, fatG: 78, updatedAtMillis,
});

test("freeze rule: past immutable, today live, new past insertable", () => {
  const journal = [entry("2026-08-31", 2100)];
  assert.deepEqual(
    upsertJournalEntry(journal, entry("2026-08-31", 9999), "2026-09-01"),
    journal,
  );
  const withToday = upsertJournalEntry(journal, entry("2026-09-01", 2800), "2026-09-01");
  assert.equal(withToday.length, 2);
  assert.equal(withToday[1].calories, 2800);
  const filled = upsertJournalEntry(journal, entry("2026-08-30", 2400), "2026-09-01");
  assert.equal(filled.length, 2);
});

test("merge: per-day LWW, argument-order independent", () => {
  const local = [entry("2026-08-30", 2400, 500), entry("2026-08-31", 2100, 900)];
  const remote = [entry("2026-08-30", 2500, 700), entry("2026-08-31", 2800, 300)];
  const a = mergeJournal(local, remote);
  const b = mergeJournal(remote, local);
  assert.deepEqual(a, b);
  assert.equal(a.find((e) => e.date === "2026-08-30").calories, 2500);
  assert.equal(a.find((e) => e.date === "2026-08-31").calories, 2100);
});

test("prune: 400-day window, malformed dropped", () => {
  const pruned = pruneJournal(
    [
      entry("2024-09-02", 1),
      entry("2025-09-03", 2),
      entry("2026-09-01", 3),
      entry("not-a-date", 4),
    ],
    "2026-09-01",
  );
  assert.deepEqual(pruned.map((e) => e.date), ["2025-09-03", "2026-09-01"]);
});

test("gap fill bridges holes between first entry and yesterday", () => {
  const resolver = (iso) => ({
    targets: { calories: 2000, proteinG: 150, carbsG: 250, fatG: 70 },
    profileId: null,
    profileName: null,
  });
  const filled = gapFillJournal([entry("2026-09-01", 2800)], "2026-09-05", 42, resolver);
  assert.deepEqual(
    filled.map((e) => e.date),
    ["2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04"],
  );
  assert.equal(filled[3].source, "GAP_FILL");
  assert.equal(filled[3].updatedAtMillis, 42);
  assert.deepEqual(gapFillJournal([], "2026-09-05", 0, resolver), []);
});

test("keep-days constant matches Android", () => {
  assert.equal(JOURNAL_KEEP_DAYS, 400);
});

test("sync record id mirrors Android GoalJournal.idFor (daily_notes scheme)", () => {
  // 2026-09-01 is epoch day 20697 -> 12 hex digits, low 48 bits.
  assert.equal(isoToEpochDay("2026-09-01"), 20697);
  assert.equal(goalJournalIdFor("2026-09-01"), "00000000-0000-0000-0000-0000000050d9");
  assert.equal(goalJournalIdFor("1970-01-01"), "00000000-0000-0000-0000-000000000000");
  // Malformed input degrades to the epoch id instead of throwing.
  assert.equal(goalJournalIdFor("nope"), "00000000-0000-0000-0000-000000000000");
});
