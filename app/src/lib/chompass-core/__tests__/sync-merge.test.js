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
import { exportSyncDocument } from "../sync-format.js";

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

test("daily notes merge collapses same-day to last-write-wins and stamps 1.2", () => {
  const id = "00000000-0000-0000-0000-0000000050b2";
  const base = {
    export: { app: "Chompass", kind: "sync", format_version: "1.2" },
    food_entries: [],
    favorites: [],
    weights: [],
    body_fat: [],
    measurements: [],
    water: [],
    daily_notes: [],
    recipes: [],
    profile: null,
    prefs: null,
  };
  const phone = {
    ...base,
    daily_notes: [
      { id, updated_at: "2026-07-24T18:00:00Z", deleted_at: null, date: "2026-07-24", text: "phone draft" },
    ],
  };
  const desktop = {
    ...base,
    daily_notes: [
      { id, updated_at: "2026-07-24T19:00:00Z", deleted_at: null, date: "2026-07-24", text: "desktop wins" },
    ],
  };
  const merged = mergeSyncDocuments(phone, desktop);
  assert.equal(merged.export.format_version, "1.2");
  assert.equal(merged.daily_notes.length, 1);
  assert.equal(merged.daily_notes[0].text, "desktop wins");

  // A tombstone on the same id wins when newer.
  const deleted = {
    ...base,
    daily_notes: [{ id, updated_at: "2026-07-24T20:00:00Z", deleted_at: "2026-07-24T20:00:00Z" }],
  };
  const merged2 = mergeSyncDocuments(desktop, deleted);
  assert.equal(merged2.daily_notes.length, 1);
  assert.ok(merged2.daily_notes[0].deleted_at);
});

test("merge local 1.2 with remote 1.1 keeps daily_notes tombstones", () => {
  const id = "00000000-0000-0000-0000-0000000050b2";
  const local = {
    export: { app: "Chompass", kind: "sync", format_version: "1.2" },
    food_entries: [],
    favorites: [],
    weights: [],
    body_fat: [],
    measurements: [],
    water: [],
    daily_notes: [
      { id, updated_at: "2026-07-24T18:00:00Z", deleted_at: "2026-07-24T18:00:00Z" },
    ],
    nicotine_entries: [],
    caffeine_entries: [],
    recipes: [],
    profile: null,
    prefs: null,
  };
  // Remote written by Chompass ≤ 3.23.0: the classic seven arrays only.
  const remote11 = {
    export: { app: "Chompass", kind: "sync", format_version: "1.1" },
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
  const merged = mergeSyncDocuments(local, remote11);
  assert.equal(merged.export.format_version, "1.2");
  // The local tombstone survives; the absent remote arrays come back empty.
  assert.deepEqual(merged.daily_notes, [
    { id, updated_at: "2026-07-24T18:00:00Z", deleted_at: "2026-07-24T18:00:00Z" },
  ]);
  assert.deepEqual(merged.nicotine_entries, []);
  assert.deepEqual(merged.caffeine_entries, []);
});

test("exportSyncDocument emits daily_notes array", () => {
  const doc = exportSyncDocument({
    dailyNotes: [{ id: "00000000-0000-0000-0000-0000000050b2", date: "2026-07-24", text: "x" }],
  });
  assert.deepEqual(doc.daily_notes, [
    {
      id: "00000000-0000-0000-0000-0000000050b2",
      updated_at: "2026-07-24T00:00:00Z",
      deleted_at: null,
      date: "2026-07-24",
      text: "x",
    },
  ]);
});

test("goal journal merge collapses same-day to last-write-wins", () => {
  // #60: ids are deterministic per date (goalJournalIdFor), so merge-by-id is
  // per-day LWW across devices even when a row rides through a PWA that does
  // not consume the array (pass-through merge).
  const id = "00000000-0000-0000-0000-0000000050b2";
  const base = {
    export: { app: "Chompass", kind: "sync", format_version: "1.2" },
    food_entries: [],
    favorites: [],
    weights: [],
    body_fat: [],
    measurements: [],
    water: [],
    daily_notes: [],
    recipes: [],
    goal_journal: [],
    profile: null,
    prefs: null,
  };
  const phone = {
    ...base,
    goal_journal: [
      {
        id, updated_at: "2026-07-24T18:00:00Z", deleted_at: null, date: "2026-07-24",
        calories: 2800, protein_g: 170, carbs_g: 350, fat_g: 78,
        profile_id: "t", profile_name: "Training day", source: "plan",
      },
    ],
  };
  const desktop = {
    ...base,
    goal_journal: [
      {
        id, updated_at: "2026-07-24T19:00:00Z", deleted_at: null, date: "2026-07-24",
        calories: 2100, protein_g: 150, carbs_g: 160, fat_g: 78,
        profile_id: "r", profile_name: "Rest day", source: "manual_switch",
      },
      {
        id: "00000000-0000-0000-0000-0000000050b1",
        updated_at: "2026-07-23T21:00:00Z", deleted_at: null, date: "2026-07-23",
        calories: 2800, protein_g: 170, carbs_g: 350, fat_g: 78,
        profile_id: "t", profile_name: "Training day", source: "plan",
      },
    ],
  };
  const merged = mergeSyncDocuments(phone, desktop);
  assert.equal(merged.goal_journal.length, 2);
  const day24 = merged.goal_journal.find((r) => r.date === "2026-07-24");
  assert.equal(day24.calories, 2100);
  assert.equal(day24.source, "manual_switch");
  assert.ok(merged.goal_journal.some((r) => r.date === "2026-07-23"));
});
