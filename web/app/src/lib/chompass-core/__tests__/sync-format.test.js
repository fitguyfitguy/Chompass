// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { loadParityFixture } from "../../parity-fixtures.js";
import {
  exportSyncDocument,
  parseSyncDocument,
  liveFoodEntriesFromSync,
  appendTombstones,
  UnsupportedSyncFormatError,
} from "../sync-format.js";

const sample = loadParityFixture("sync-sample.json");

test("parity sync-sample parses", () => {
  const doc = parseSyncDocument(sample);
  assert.equal(doc.export.kind, "sync");
  assert.equal(doc.export.format_version, "1.1");
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
  assert.equal(parsed.export.format_version, "1.1");
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
