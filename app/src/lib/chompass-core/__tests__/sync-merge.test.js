// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  dedupeRecordLists,
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

// #39: weights merge also collapses identical (date, weight_kg) rows written
// under different ids. Mirror of SyncMergeTest.dedupeRecordLists*.
const weightKey = (row) => (row.date ? `${row.date}|${Number(row.weight_kg)}` : "");

function dedupeWeights(local, remote) {
  return dedupeRecordLists(local, remote, weightKey);
}

test("dedupeRecordLists collapses same date+value weights to the newest", () => {
  const local = [
    { id: "a", updated_at: "2026-07-20T08:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 79.4 },
  ];
  const remote = [
    { id: "b", updated_at: "2026-07-21T08:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 79.4 },
  ];
  const merged = dedupeWeights(local, remote);
  assert.equal(merged.length, 1);
  assert.equal(merged[0].id, "b");
});

test("dedupeRecordLists prefers the remote row on equal updated_at", () => {
  const local = [
    { id: "a", updated_at: "2026-07-20T08:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 80 },
  ];
  const remote = [
    { id: "b", updated_at: "2026-07-20T08:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 80 },
  ];
  const merged = dedupeWeights(local, remote);
  assert.equal(merged.length, 1);
  assert.equal(merged[0].id, "b");
});

test("dedupeRecordLists passes tombstones through untouched", () => {
  const local = [{ id: "t", updated_at: "2026-07-20T08:00:00Z", deleted_at: "2026-07-20T08:00:00Z" }];
  const remote = [
    { id: "b", updated_at: "2026-07-20T08:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 80 },
    { id: "c", updated_at: "2026-07-20T08:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 80 },
  ];
  const merged = dedupeWeights(local, remote);
  // Tombstone survives untouched; the two live dupes collapse to one.
  assert.equal(merged.length, 2);
  assert.ok(merged.some((r) => r.id === "t" && r.deleted_at));
  assert.equal(merged.filter((r) => !r.deleted_at).length, 1);
});

test("dedupeRecordLists leaves distinct rows unaffected", () => {
  const local = [
    { id: "a", updated_at: "2026-07-20T08:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 79.4 },
  ];
  const remote = [
    { id: "b", updated_at: "2026-07-21T08:00:00Z", deleted_at: null, date: "2026-07-21T08:00:00Z", weight_kg: 79.4 },
    { id: "c", updated_at: "2026-07-21T09:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 80.1 },
  ];
  const merged = dedupeWeights(local, remote);
  assert.equal(merged.length, 3);
});

test("mergeSyncDocuments collapses duplicate weights under different ids", () => {
  const base = {
    export: { app: "Chompass", kind: "sync", format_version: "1.0" },
    food_entries: [],
    favorites: [],
    weights: [],
    body_fat: [],
    measurements: [],
    water: [],
    recipes: [],
    profile: null,
    prefs: null,
  };
  const phone = {
    ...base,
    weights: [
      { id: "w1", updated_at: "2026-07-20T08:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 80.0 },
      { id: "w3", updated_at: "2026-07-19T08:00:00Z", deleted_at: null, date: "2026-07-19T08:00:00Z", weight_kg: 78.9 },
      { id: "w4", updated_at: "2026-07-20T08:00:00Z", deleted_at: "2026-07-20T08:00:00Z" },
    ],
  };
  const desktop = {
    ...base,
    weights: [
      { id: "w2", updated_at: "2026-07-21T08:00:00Z", deleted_at: null, date: "2026-07-20T08:00:00Z", weight_kg: 80 },
    ],
  };
  const merged = mergeSyncDocuments(phone, desktop);
  assert.equal(merged.weights.length, 3);
  assert.ok(!merged.weights.some((w) => w.id === "w1"));
  assert.ok(merged.weights.some((w) => w.id === "w2"));
  assert.ok(merged.weights.some((w) => w.id === "w3"));
  assert.ok(merged.weights.some((w) => w.id === "w4" && w.deleted_at));
});
