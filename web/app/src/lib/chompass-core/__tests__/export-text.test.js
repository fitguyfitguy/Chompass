import test from "node:test";
import assert from "node:assert/strict";
import {
  exportDiaryCsv,
  exportDiaryMarkdown,
  exportBodyMetricsCsv,
  filterDiaryRange,
} from "../export-text.js";
import { encodeMealShare, decodeMealShare } from "../../meal-share.js";
import { guessMealTypeFromPrefs, weekDates, timeInputToMinutes, minutesToTimeInput } from "../../meal-schedule.js";

const sampleEntries = [
  {
    id: "1",
    name: "Oats",
    date: "2026-07-20",
    time: "08:00",
    mealType: "breakfast",
    calories: 300,
    proteinG: 12,
    carbsG: 45,
    fatG: 8,
    fiberG: 5,
    quantityG: 80,
    source: "manual",
    note: null,
  },
  {
    id: "2",
    name: "Chicken",
    date: "2026-07-21",
    time: "12:30",
    mealType: "lunch",
    calories: 450,
    proteinG: 40,
    carbsG: 10,
    fatG: 18,
    source: "barcode",
    note: "grilled",
  },
];

test("exportDiaryCsv includes header and rows", () => {
  const csv = exportDiaryCsv(sampleEntries);
  assert.match(csv, /^date,meal,time,food,/);
  assert.match(csv, /Oats/);
  assert.match(csv, /Chicken/);
  assert.match(csv, /barcode/);
});

test("exportDiaryMarkdown has day headings and totals", () => {
  const md = exportDiaryMarkdown(sampleEntries, { start: "2026-07-20", end: "2026-07-21" }, {
    calories: 2000,
    proteinG: 150,
    carbsG: 200,
    fatG: 70,
  });
  assert.match(md, /# Food diary export/);
  assert.match(md, /## 2026-07-20/);
  assert.match(md, /Breakfast/);
});

test("exportBodyMetricsCsv long format", () => {
  const csv = exportBodyMetricsCsv({
    weights: [{ id: "w1", date: "2026-07-01T10:00:00.000Z", weightKg: 80.5 }],
    bodyFat: [{ id: "b1", date: "2026-07-01T10:00:00.000Z", bodyFatPercent: 0.18 }],
    measurements: [
      {
        id: "m1",
        date: "2026-07-01T10:00:00.000Z",
        waistCm: 85,
        neckCm: 38,
        calfCm: 36,
        wristCm: 16,
      },
    ],
  });
  assert.match(csv, /^metric,timestamp,value,unit/);
  assert.match(csv, /weight,/);
  assert.match(csv, /body_fat,/);
  assert.match(csv, /calf,/);
  assert.match(csv, /wrist,/);
});

test("filterDiaryRange today/week/all", () => {
  const today = "2026-07-21";
  assert.equal(filterDiaryRange(sampleEntries, "today", today).length, 1);
  assert.equal(filterDiaryRange(sampleEntries, "week", today).length, 2);
  assert.equal(filterDiaryRange(sampleEntries, "all", today).length, 2);
});

test("meal share round-trip", () => {
  const hash = encodeMealShare(sampleEntries);
  assert.match(hash, /^#\/add-meal\?d=/);
  const decoded = decodeMealShare(hash);
  assert.ok(decoded);
  assert.equal(decoded.length, 2);
  assert.equal(decoded[0].name, "Oats");
  assert.equal(decoded[1].calories, 450);
});

test("meal schedule helpers", () => {
  assert.equal(timeInputToMinutes("05:00"), 300);
  assert.equal(minutesToTimeInput(660), "11:00");
  assert.equal(
    guessMealTypeFromPrefs(
      { mealBreakfastStart: 300, mealLunchStart: 660, mealDinnerStart: 900, mealSnackStart: 1260 },
      new Date("2026-07-21T08:00:00")
    ),
    "breakfast"
  );
  const monWeek = weekDates("2026-07-22", true); // Wednesday
  assert.equal(monWeek[0], "2026-07-20"); // Monday
  const sunWeek = weekDates("2026-07-22", false);
  assert.equal(sunWeek[0], "2026-07-19"); // Sunday
});
