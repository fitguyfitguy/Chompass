// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { loadParityFixture } from "../../parity-fixtures.js";
import {
  exportSyncDocument,
  parseSyncDocument,
  liveFoodEntriesFromSync,
  liveDailyNotesFromSync,
  liveNicotineFromSync,
  liveCaffeineFromSync,
  appendTombstones,
  UnsupportedSyncFormatError,
} from "../sync-format.js";

const sample = loadParityFixture("sync-sample.json");

test("parity sync-sample parses", () => {
  const doc = parseSyncDocument(sample);
  assert.equal(doc.export.kind, "sync");
  assert.equal(doc.export.format_version, "1.2");
  const foods = liveFoodEntriesFromSync(doc.food_entries);
  assert.equal(foods.length, 2);
  assert.equal(foods[0].id, "11111111-1111-4111-8111-111111111111");
  assert.equal(foods[0].name, "Chicken salad");
  assert.equal(foods[0].source, "manual");
  assert.equal(foods[0].selectedServingUnit, "bowl");
  assert.equal(foods[0].constituents?.length, 2);
  assert.equal(foods[0].constituents?.[0].selectedServingUnit, "piece");
  assert.equal(foods[0].constituents?.[0].servingUnitOptions?.[0].gramsPerUnit, 90);
  assert.equal(foods[1].name, "Black coffee");
  assert.deepEqual(foods[1].constituents, []);
  assert.equal(foods[1].selectedServingUnit, "cup");
  assert.equal(foods[0].caffeineMg, 95);
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
        constituents: [],
      },
    ],
    generatedAt: "2026-07-24T10:00:00.000Z",
  });
  const parsed = parseSyncDocument(doc);
  assert.equal(parsed.export.format_version, "1.2");
  assert.equal(parsed.food_entries[0].id, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
  assert.equal(parsed.food_entries[0].emoji, "🥚");
  assert.equal(parsed.food_entries[0].source, "manually_edited");
  assert.equal(parsed.food_entries[0].selected_serving_unit, "piece");
  assert.equal(parsed.food_entries[0].serving_unit_options[0].grams_per_unit, 50);
  assert.deepEqual(parsed.food_entries[0].constituents, []);
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
  // Pre-1.2 remotes (Chompass ≤ 3.23.0) carry only the classic seven arrays.
  const v11 = structuredClone(sample);
  v11.export.format_version = "1.1";
  delete v11.daily_notes;
  delete v11.nicotine_entries;
  delete v11.caffeine_entries;
  const doc = parseSyncDocument(v11);
  assert.deepEqual(doc.daily_notes, []);
  assert.deepEqual(doc.nicotine_entries, []);
  assert.deepEqual(doc.caffeine_entries, []);
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
  assert.equal(parsed.export.format_version, "1.2");
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
