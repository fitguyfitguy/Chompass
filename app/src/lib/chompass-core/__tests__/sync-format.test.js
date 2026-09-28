// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { loadParityFixture } from "../../parity-fixtures.js";
import {
  exportSyncDocument,
  parseSyncDocument,
  SYNC_FORMAT_VERSION,
  SYNC_IMPORT_VERSIONS,
  liveFoodEntriesFromSync,
  liveDailyNotesFromSync,
  liveGoalJournalFromSync,
  liveNicotineFromSync,
  liveCaffeineFromSync,
  appendTombstones,
  UnsupportedSyncFormatError,
} from "../sync-format.js";

const sample = loadParityFixture("sync-sample.json");

test("parity sync-sample parses", () => {
  const doc = parseSyncDocument(sample);
  assert.equal(doc.export.kind, "sync");
  assert.equal(doc.export.format_version, "1.3");
  const foods = liveFoodEntriesFromSync(doc.food_entries);
  assert.equal(foods.length, 2);
  assert.equal(foods[0].id, "11111111-1111-4111-8111-111111111111");
  assert.equal(foods[0].name, "Chicken salad");
  assert.equal(foods[0].source, "manual");
  assert.equal(foods[0].selectedServingUnit, "bowl");
  assert.equal(foods[0].constituents?.length, 2);
  assert.equal(foods[0].constituents?.[0].selectedServingUnit, "piece");
  assert.equal(foods[0].constituents?.[0].servingUnitOptions?.[0].gramsPerUnit, 90);
  // sync 1.3: constituent micros parse from the fixture.
  assert.equal(foods[0].constituents?.[0].sugarG, 0);
  assert.equal(foods[0].constituents?.[0].cholesterolMg, 145);
  assert.equal(foods[0].constituents?.[0].sodiumMg, 220);
  assert.equal(foods[0].constituents?.[0].omega3G, 0.5);
  assert.equal(foods[0].constituents?.[0].caffeineMg, null);
  assert.equal(foods[0].constituents?.[1].sugarG, 2);
  assert.equal(foods[0].constituents?.[1].fiberG, 4);
  assert.equal(foods[1].name, "Black coffee");
  assert.deepEqual(foods[1].constituents, []);
  assert.equal(foods[1].selectedServingUnit, "cup");
  assert.equal(foods[0].caffeineMg, 95);
});

test("parity sync-sample goal_journal rows convert to model entries", () => {
  const doc = parseSyncDocument(sample);
  assert.equal(doc.goal_journal.length, 2);
  const entries = liveGoalJournalFromSync(doc.goal_journal);
  assert.equal(entries.length, 2);
  const rest = entries.find((e) => e.date === "2026-07-24");
  assert.equal(rest.calories, 2100);
  assert.equal(rest.proteinG, 150);
  assert.equal(rest.profileId, "r-2026-07");
  assert.equal(rest.profileName, "Rest day");
  assert.equal(rest.source, "MANUAL_SWITCH");
  assert.equal(rest.updatedAtMillis, Date.parse("2026-07-24T22:10:00.000Z"));
  const training = entries.find((e) => e.date === "2026-07-23");
  assert.equal(training.source, "PLAN");
});

test("exportSyncDocument round-trips goal_journal with per-day ids", () => {
  const doc = exportSyncDocument({
    goalJournal: [
      {
        date: "2026-07-23",
        calories: 2800,
        proteinG: 170,
        carbsG: 350,
        fatG: 78,
        profileId: "t",
        profileName: "Training day",
        updatedAtMillis: 1784192700000,
        source: "PLAN",
      },
    ],
    generatedAt: "2026-07-24T10:00:00.000Z",
  });
  assert.equal(doc.goal_journal.length, 1);
  assert.equal(doc.goal_journal[0].id, "00000000-0000-0000-0000-0000000050b1");
  assert.equal(doc.goal_journal[0].source, "plan");
  const parsed = parseSyncDocument(doc);
  const live = liveGoalJournalFromSync(parsed.goal_journal);
  assert.equal(live[0].date, "2026-07-23");
  assert.equal(live[0].calories, 2800);
  assert.equal(live[0].updatedAtMillis, 1784192700000);
  assert.equal(live[0].source, "PLAN");
});

test("exportSyncDocument round-trips food id", () => {
  const doc = exportSyncDocument({
    foodEntries: [
      {
        id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        name: "Egg",
        emoji: "🥚",
        calories: 70,
        proteinG: 6,
        carbsG: 0.5,
        fatG: 5,
        mealType: "breakfast",
        date: "2026-07-24",
        time: "08:00",
        source: "manual",
        note: null,
        grounding: null,
        servingUnitOptions: [{ unit: "piece", gramsPerUnit: 50, quantity: 1 }],
        selectedServingUnit: "piece",
        selectedServingQuantity: 1,
        constituents: [
          {
            name: "White",
            calories: 70,
            proteinG: 6,
            carbsG: 0.5,
            fatG: 5,
            servingSizeGrams: 50,
            emoji: null,
            servingUnitOptions: [],
            selectedServingUnit: null,
            selectedServingQuantity: null,
            sugarG: 0.5,
            cholesterolMg: 186,
            fiberG: null,
          },
        ],
      },
    ],
    generatedAt: "2026-07-24T10:00:00.000Z",
  });
  const parsed = parseSyncDocument(doc);
  assert.equal(parsed.export.format_version, SYNC_FORMAT_VERSION);
  assert.equal(parsed.food_entries[0].id, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
  assert.equal(parsed.food_entries[0].emoji, "🥚");
  assert.equal(parsed.food_entries[0].source, "manually_edited");
  assert.equal(parsed.food_entries[0].serving_unit_options[0].grams_per_unit, 50);
  // Constituent micros ride sync 1.3 (Codeberg #86); nulls stay null.
  assert.equal(parsed.food_entries[0].constituents.length, 1);
  assert.equal(parsed.food_entries[0].constituents[0].sugar_g, 0.5);
  assert.equal(parsed.food_entries[0].constituents[0].cholesterol_mg, 186);
  assert.equal(parsed.food_entries[0].constituents[0].fiber_g, null);
  const live = liveFoodEntriesFromSync(parsed.food_entries);
  assert.equal(live[0].constituents[0].sugarG, 0.5);
  assert.equal(live[0].constituents[0].cholesterolMg, 186);
  assert.equal(live[0].constituents[0].caffeineMg, null);
});

test("sync 1.3 accepted, newer rejected", () => {
  assert.equal(SYNC_FORMAT_VERSION, "1.3");
  for (const v of ["1.0", "1.1", "1.2", "1.3"]) {
    assert.ok(SYNC_IMPORT_VERSIONS.has(v), v);
  }
  const doc = { export: { app: "Chompass", kind: "sync", format_version: "1.4" } };
  assert.throws(() => parseSyncDocument(doc), UnsupportedSyncFormatError);
});

test("exportSyncDocument round-trips nicotine entries", () => {
  const doc = exportSyncDocument({
    nicotine: [
      { id: "cccccccc-cccc-4ccc-8ccc-cccccccccccc", date: "2026-07-24", kind: "pouch", count: 2, mg: 6.5 },
      { id: "dddddddd-dddd-4ddd-8ddd-dddddddddddd", date: "2026-07-24", kind: "cigarette", count: 1, mg: null },
    ],
    generatedAt: "2026-07-24T10:00:00.000Z",
  });
  const parsed = parseSyncDocument(doc);
  assert.equal(parsed.nicotine_entries.length, 2);
  assert.equal(parsed.nicotine_entries[0].kind, "pouch");
  assert.equal(parsed.nicotine_entries[0].count, 2);
  assert.equal(parsed.nicotine_entries[0].mg, 6.5);
  assert.equal(parsed.nicotine_entries[1].mg, null);
  const live = liveNicotineFromSync(parsed.nicotine_entries);
  assert.equal(live[0].count, 2);
  assert.equal(live[1].kind, "cigarette");
});

test("caffeine-less legacy docs still parse (optional array)", () => {
  const legacy = structuredClone(sample);
  delete legacy.caffeine_entries;
  const doc = parseSyncDocument(legacy);
  assert.deepEqual(doc.caffeine_entries, []);
});

test("1.1-shaped doc parses with daily_notes defaulted", () => {
  // Pre-1.2 remotes (Chompass ≤ 3.23.0) carry only the classic seven arrays;
  // pre-#60 docs have no goal_journal either. Both must default to [].
  const v11 = structuredClone(sample);
  v11.export.format_version = "1.1";
  delete v11.daily_notes;
  delete v11.nicotine_entries;
  delete v11.caffeine_entries;
  delete v11.goal_journal;
  const doc = parseSyncDocument(v11);
  assert.deepEqual(doc.daily_notes, []);
  assert.deepEqual(doc.nicotine_entries, []);
  assert.deepEqual(doc.caffeine_entries, []);
  assert.deepEqual(doc.goal_journal, []);
  assert.equal(doc.food_entries.length, 2);
});

test("1.0-shaped doc parses with daily_notes defaulted", () => {
  const v10 = structuredClone(sample);
  v10.export.format_version = "1.0";
  delete v10.daily_notes;
  delete v10.nicotine_entries;
  delete v10.caffeine_entries;
  // 1.0 predates serving-unit wire fields too.
  delete v10.food_entries[0].serving_unit_options;
  delete v10.food_entries[0].selected_serving_unit;
  delete v10.food_entries[0].selected_serving_quantity;
  delete v10.food_entries[0].constituents;
  const doc = parseSyncDocument(v10);
  assert.deepEqual(doc.daily_notes, []);
  assert.deepEqual(doc.nicotine_entries, []);
  assert.deepEqual(doc.caffeine_entries, []);
});

test("1.2 doc missing only the optional arrays defaults each to []", () => {
  const v12 = structuredClone(sample);
  delete v12.daily_notes;
  delete v12.nicotine_entries;
  v12.export.format_version = "1.2";
  delete v12.caffeine_entries;
  const doc = parseSyncDocument(v12);
  assert.deepEqual(doc.daily_notes, []);
  assert.deepEqual(doc.nicotine_entries, []);
  assert.deepEqual(doc.caffeine_entries, []);
  assert.equal(doc.export.format_version, "1.2");
});

test("exportSyncDocument round-trips caffeine entries", () => {
  const doc = exportSyncDocument({
    caffeine: [
      { id: "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee", date: "2026-07-24", kind: "coffee", mg: 95 },
      { id: "ffffffff-ffff-4fff-8fff-ffffffffffff", date: "2026-07-24", kind: "tea", mg: 28.5 },
    ],
    generatedAt: "2026-07-24T10:00:00.000Z",
  });
  const parsed = parseSyncDocument(doc);
  assert.equal(parsed.caffeine_entries.length, 2);
  assert.equal(parsed.caffeine_entries[0].kind, "coffee");
  assert.equal(parsed.caffeine_entries[0].mg, 95);
  assert.equal(parsed.caffeine_entries[1].mg, 28.5);
  const live = liveCaffeineFromSync(parsed.caffeine_entries);
  assert.equal(live[0].kind, "coffee");
  assert.equal(live[0].mg, 95);
  assert.equal(live[1].mg, 28.5);
});

test("accepts legacy sync format_version 1.0", () => {
  const legacy = structuredClone(sample);
  legacy.export.format_version = "1.0";
  delete legacy.food_entries[0].serving_unit_options;
  delete legacy.food_entries[0].selected_serving_unit;
  delete legacy.food_entries[0].selected_serving_quantity;
  delete legacy.food_entries[0].constituents;
  legacy.food_entries = [legacy.food_entries[0]];
  const doc = parseSyncDocument(legacy);
  const foods = liveFoodEntriesFromSync(doc.food_entries);
  assert.equal(foods.length, 1);
  assert.deepEqual(foods[0].constituents, []);
});

test("appendTombstones adds delete stubs", () => {
  const doc = exportSyncDocument({ foodEntries: [] });
  appendTombstones(doc, {
    "dead-id": { updatedAt: "2026-07-24T11:00:00Z", deletedAt: "2026-07-24T11:00:00Z", kind: "food" },
  });
  assert.equal(doc.food_entries.length, 1);
  assert.equal(doc.food_entries[0].deleted_at, "2026-07-24T11:00:00Z");
});

test("rejects wrong kind", () => {
  assert.throws(
    () => parseSyncDocument({ export: { app: "Chompass", kind: "body_metrics", format_version: "1.0" } }),
    UnsupportedSyncFormatError,
  );
});

test("daily notes round-trip with deterministic ids", async () => {
  const { dailyNoteIdFor } = await import("../models.js");
  const date = "2026-07-24";
  // Mirrors Android DailyNote.idFor: day count since epoch in the low 48 bits.
  assert.equal(dailyNoteIdFor(date), "00000000-0000-0000-0000-0000000050b2");
  assert.equal(dailyNoteIdFor("2026-07-24"), dailyNoteIdFor("2026-07-24"));
  assert.notEqual(dailyNoteIdFor("2026-07-25"), dailyNoteIdFor("2026-07-24"));

  const doc = exportSyncDocument({
    dailyNotes: [{ id: dailyNoteIdFor(date), date, text: "Solid day." }],
    generatedAt: "2026-07-24T10:00:00.000Z",
  });
  assert.equal(doc.daily_notes.length, 1);
  assert.equal(doc.daily_notes[0].date, date);
  assert.equal(doc.daily_notes[0].text, "Solid day.");

  const parsed = parseSyncDocument(doc);

  assert.equal(parsed.export.format_version, SYNC_FORMAT_VERSION);
  const notes = liveDailyNotesFromSync(parsed.daily_notes);
  assert.equal(notes.length, 1);
  assert.equal(notes[0].id, dailyNoteIdFor(date));
  assert.equal(notes[0].date, date);
  assert.equal(notes[0].text, "Solid day.");
});

test("daily-note tombstone rides the wire", () => {
  const date = "2026-07-24";
  const id = "00000000-0000-0000-0000-0000000050b2";
  const doc = exportSyncDocument({ dailyNotes: [] });
  appendTombstones(doc, {
    [id]: { updatedAt: "2026-07-24T21:00:00Z", deletedAt: "2026-07-24T22:00:00Z", kind: "daily_note" },
  });
  assert.equal(doc.daily_notes.length, 1);
  assert.equal(doc.daily_notes[0].id, id);
  assert.equal(doc.daily_notes[0].deleted_at, "2026-07-24T22:00:00Z");
  assert.equal(liveDailyNotesFromSync(doc.daily_notes).length, 0);
});
