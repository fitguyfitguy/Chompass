// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { importDiary, exportDiary, DIARY_FORMAT_VERSION } from "../diary-format.js";
import { loadParityFixture } from "../../parity-fixtures.js";

let seq = 0;
const idGen = () => `test-id-${seq++}`;

test("imports the parity diary fixture without throwing", () => {
  const doc = loadParityFixture("diary-sample.json");
  assert.equal(doc.export.format_version, DIARY_FORMAT_VERSION);
  const entries = importDiary(doc, idGen);
  assert.ok(entries.length > 0);
  for (const e of entries) {
    assert.equal(typeof e.name, "string");
    assert.equal(typeof e.calories, "number");
    assert.ok(["breakfast", "lunch", "dinner", "snack"].includes(e.mealType));
  }
});

test("round-trips totals for parity diary fixture days", () => {
  const doc = loadParityFixture("diary-sample.json");
  const entries = importDiary(doc, idGen);
  const targets = {};
  for (const day of doc.days) {
    targets[day.date] = {
      calories: day.targets.calories,
      proteinG: day.targets.protein_g,
      carbsG: day.targets.carbs_g,
      fatG: day.targets.fat_g,
    };
  }
  const dateRange = doc.export.date_range;
  const reExported = exportDiary({ entries, targets, dateRange });

  assert.equal(reExported.export.app, "Chompass");
  assert.equal(reExported.export.format_version, DIARY_FORMAT_VERSION);
  assert.equal(reExported.days.length, doc.days.length);

  // Totals are re-derived by summing item macros; tolerate a single 0.1 step.
  const close = (a, b, tol = 0.11) => Math.abs(a - b) <= tol;

  for (let i = 0; i < doc.days.length; i++) {
    const original = doc.days[i];
    const roundTripped = reExported.days.find((d) => d.date === original.date);
    assert.ok(roundTripped, `missing round-tripped day ${original.date}`);
    assert.equal(roundTripped.totals.calories, original.totals.calories);
    assert.ok(close(roundTripped.totals.protein_g, original.totals.protein_g), `protein drift on ${original.date}`);
    assert.ok(close(roundTripped.totals.carbs_g, original.totals.carbs_g), `carbs drift on ${original.date}`);
    assert.ok(close(roundTripped.totals.fat_g, original.totals.fat_g), `fat drift on ${original.date}`);
  }
});

test("grounding field passes through import->export untouched when present", () => {
  const doc = {
    export: { app: "nofud", format_version: DIARY_FORMAT_VERSION, date_range: { start: "2026-01-01", end: "2026-01-01" } },
    days: [{
      date: "2026-01-01",
      totals: { calories: 100, protein_g: 1, carbs_g: 1, fat_g: 1 },
      targets: { calories: 100, protein_g: 1, carbs_g: 1, fat_g: 1 },
      remaining: { calories: 0, protein_g: 0, carbs_g: 0, fat_g: 0 },
      meals: [{
        type: "breakfast",
        items: [{
          name: "Test Item", quantity_g: 50, calories: 100,
          protein_g: 1, carbs_g: 1, fat_g: 1,
          sugar_g: null, added_sugar_g: null, fiber_g: null, saturated_fat_g: null,
          monounsaturated_fat_g: null, polyunsaturated_fat_g: null, cholesterol_mg: null,
          sodium_mg: null, potassium_mg: null, trans_fat_g: null, calcium_mg: null,
          iron_mg: null, magnesium_mg: null, zinc_mg: null, vitamin_a_mcg: null,
          vitamin_c_mg: null, vitamin_d_mcg: null, vitamin_b12_mcg: null, vitamin_e_mg: null,
          vitamin_k_mcg: null, folate_mcg: null, omega3_g: null,
          time: "12:00", source: "barcode", note: null,
          grounding: {
            source_kind: "openFoodFacts", source_id: "123", source_name: "Test Brand",
            dataset_version: null, identity_confirmed: true, portion_confirmed: false,
            user_corrected: false, identity_evidence: null, portion_evidence: null,
            validation_notes: null, components: null,
          },
        }],
      }],
    }],
  };

  const entries = importDiary(doc, idGen);
  assert.equal(entries[0].grounding.sourceKind, "openFoodFacts");
  assert.equal(entries[0].grounding.sourceId, "123");

  const reExported = exportDiary({
    entries,
    targets: { "2026-01-01": { calories: 100, proteinG: 1, carbsG: 1, fatG: 1 } },
    dateRange: { start: "2026-01-01", end: "2026-01-01" },
  });
  const roundTripped = reExported.days[0].meals[0].items[0];
  assert.deepEqual(roundTripped.grounding, doc.days[0].meals[0].items[0].grounding);
});

test("rejects unsupported format_version", () => {
  const doc = { export: { app: "nofud", format_version: "0.9" }, days: [] };
  assert.throws(() => importDiary(doc), /format_version/);
});

test("accepts legacy format_version 1.0 macros-only", () => {
  const doc = {
    export: { app: "Fud AI", format_version: "1.0", date_range: { start: "2026-01-01", end: "2026-01-01" } },
    days: [{
      date: "2026-01-01",
      totals: { calories: 100, protein_g: 10, carbs_g: 5, fat_g: 2 },
      targets: { calories: 2000, protein_g: 150, carbs_g: 200, fat_g: 60 },
      remaining: { calories: 1900, protein_g: 140, carbs_g: 195, fat_g: 58 },
      meals: [{
        type: "breakfast",
        items: [{
          name: "Oats", quantity_g: 80, calories: 100,
          protein_g: 10, carbs_g: 5, fat_g: 2,
          time: "08:00", source: "manually_edited", note: null,
        }],
      }],
    }],
  };
  const entries = importDiary(doc, idGen);
  assert.equal(entries.length, 1);
  assert.equal(entries[0].name, "Oats");
  assert.equal(entries[0].calories, 100);
  assert.equal(entries[0].proteinG, 10);
  assert.equal(entries[0].mealType, "breakfast");
});

test("accepts NoFUD app stamp with format 1.1", () => {
  const doc = {
    export: { app: "NoFUD", format_version: DIARY_FORMAT_VERSION, date_range: { start: "2026-07-20", end: "2026-07-20" } },
    days: [{
      date: "2026-07-20",
      totals: { calories: 200, protein_g: 22, carbs_g: 0, fat_g: 12 },
      targets: { calories: 2000, protein_g: 150, carbs_g: 200, fat_g: 60 },
      remaining: { calories: 1800, protein_g: 128, carbs_g: 200, fat_g: 48 },
      meals: [{
        type: "lunch",
        items: [{
          name: "Salmon", quantity_g: 150, calories: 200,
          protein_g: 22, carbs_g: 0, fat_g: 12,
          sugar_g: null, added_sugar_g: null, fiber_g: 1.2, saturated_fat_g: null,
          monounsaturated_fat_g: null, polyunsaturated_fat_g: null, cholesterol_mg: null,
          sodium_mg: null, potassium_mg: null, trans_fat_g: null, calcium_mg: null,
          iron_mg: null, magnesium_mg: null, zinc_mg: null, vitamin_a_mcg: null,
          vitamin_c_mg: null, vitamin_d_mcg: null, vitamin_b12_mcg: null, vitamin_e_mg: null,
          vitamin_k_mcg: null, folate_mcg: null, omega3_g: null,
          time: "12:30", source: "ai_estimated", note: null,
        }],
      }],
    }],
  };
  const entries = importDiary(doc, idGen);
  assert.equal(entries.length, 1);
  assert.equal(entries[0].name, "Salmon");
  assert.equal(entries[0].fiberG, 1.2);
});

test("accepts app value case-insensitively", () => {
  const doc = { export: { app: "Fud AI", format_version: DIARY_FORMAT_VERSION, date_range: {} }, days: [] };
  assert.doesNotThrow(() => importDiary(doc));
});
