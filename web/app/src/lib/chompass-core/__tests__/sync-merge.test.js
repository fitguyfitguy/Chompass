// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  mergeRecordLists,
  mergeSyncDocuments,
  pickNewer,
  partitionLiveAndDeleted,
} from "../sync-merge.js";

test("pickNewer prefers higher updated_at", () => {
  const a = { id: "1", updated_at: "2026-01-01T00:00:00Z", name: "a" };
  const b = { id: "1", updated_at: "2026-01-02T00:00:00Z", name: "b" };
  assert.equal(pickNewer(a, b)?.name, "b");
  assert.equal(pickNewer(b, a)?.name, "b");
});

test("pickNewer prefers delete on equal updated_at", () => {
  const live = { id: "1", updated_at: "2026-01-01T00:00:00Z", deleted_at: null };
  const dead = { id: "1", updated_at: "2026-01-01T00:00:00Z", deleted_at: "2026-01-01T00:00:00Z" };
  assert.equal(pickNewer(live, dead)?.deleted_at, "2026-01-01T00:00:00Z");
});

test("mergeRecordLists unions by id with LWW", () => {
  const local = [
    { id: "a", updated_at: "2026-01-01T00:00:00Z", v: 1 },
    { id: "b", updated_at: "2026-01-01T00:00:00Z", v: 1 },
  ];
  const remote = [
    { id: "b", updated_at: "2026-01-03T00:00:00Z", v: 2 },
    { id: "c", updated_at: "2026-01-02T00:00:00Z", v: 1 },
  ];
  const merged = mergeRecordLists(local, remote);
  assert.equal(merged.length, 3);
  assert.equal(merged.find((r) => r.id === "b")?.v, 2);
  assert.ok(merged.find((r) => r.id === "c"));
});

test("mergeSyncDocuments merges lunch from desktop with phone breakfast", () => {
  const phone = {
    export: { app: "Chompass", kind: "sync", format_version: "1.0" },
    food_entries: [
      {
        id: "breakfast",
        updated_at: "2026-07-24T08:00:00Z",
        deleted_at: null,
        name: "Oats",
        date: "2026-07-24",
        time: "08:00",
        meal_type: "breakfast",
        calories: 300,
        protein_g: 10,
        carbs_g: 50,
        fat_g: 5,
      },
    ],
    favorites: [],
    weights: [],
    body_fat: [],
    measurements: [],
    water: [],
    recipes: [],
    profile: null,
    prefs: null,
  };
  const desktop = {
    export: { app: "Chompass", kind: "sync", format_version: "1.0" },
    food_entries: [
      {
        id: "lunch",
        updated_at: "2026-07-24T12:30:00Z",
        deleted_at: null,
        name: "Salad",
        date: "2026-07-24",
        time: "12:30",
        meal_type: "lunch",
        calories: 420,
        protein_g: 38,
        carbs_g: 12,
        fat_g: 22,
      },
    ],
    favorites: [],
    weights: [],
    body_fat: [],
    measurements: [],
    water: [],
    recipes: [],
    profile: null,
    prefs: null,
  };
  const merged = mergeSyncDocuments(phone, desktop);
  assert.equal(merged.food_entries.length, 2);
  assert.ok(merged.food_entries.some((e) => e.id === "breakfast"));
  assert.ok(merged.food_entries.some((e) => e.id === "lunch"));
});

test("partitionLiveAndDeleted separates tombstones", () => {
  const { live, deletedIds } = partitionLiveAndDeleted([
    { id: "a", updated_at: "1", deleted_at: null },
    { id: "b", updated_at: "2", deleted_at: "2" },
  ]);
  assert.equal(live.length, 1);
  assert.deepEqual(deletedIds, ["b"]);
});
