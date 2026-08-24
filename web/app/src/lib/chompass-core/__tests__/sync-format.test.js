// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { loadParityFixture } from "../../parity-fixtures.js";
import {
  exportSyncDocument,
  parseSyncDocument,
  liveFoodEntriesFromSync,
  liveDailyNotesFromSync,
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
