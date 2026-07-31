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
  const lunch = entries.find((e) => e.name === "Sample Lunch Item 1");
  assert.ok(lunch);
  assert.equal(lunch.selectedServingUnit, "bowl");
  assert.equal(lunch.constituents?.length, 3);
  assert.equal(lunch.constituents?.[0].selectedServingUnit, "piece");
  assert.equal(lunch.constituents?.[0].servingUnitOptions?.[0].gramsPerUnit, 110);
  const dinner = entries.find((e) => e.name === "Sample Dinner Item 1");
  assert.deepEqual(dinner?.constituents, []);
  assert.equal(dinner?.selectedServingUnit, "cup");
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

test("accepts legacy format_version 1.1 with micros", () => {
  const doc = {
    export: { app: "NoFUD", format_version: "1.1", date_range: { start: "2026-07-20", end: "2026-07-20" } },
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
  assert.deepEqual(entries[0].constituents, []);
});

test("round-trips constituents and serving units", () => {
  const doc = {
    export: { app: "Chompass", format_version: DIARY_FORMAT_VERSION, date_range: { start: "2026-01-02", end: "2026-01-02" } },
    days: [{
      date: "2026-01-02",
      totals: { calories: 500, protein_g: 40, carbs_g: 30, fat_g: 20 },
      targets: { calories: 2000, protein_g: 150, carbs_g: 200, fat_g: 60 },
      remaining: { calories: 1500, protein_g: 110, carbs_g: 170, fat_g: 40 },
      meals: [{
        type: "lunch",
        items: [{
          name: "Bowl", quantity_g: 400, calories: 500,
          protein_g: 40, carbs_g: 30, fat_g: 20,
          sugar_g: null, added_sugar_g: null, fiber_g: null, saturated_fat_g: null,
          monounsaturated_fat_g: null, polyunsaturated_fat_g: null, cholesterol_mg: null,
          sodium_mg: null, potassium_mg: null, trans_fat_g: null, calcium_mg: null,
          iron_mg: null, magnesium_mg: null, zinc_mg: null, vitamin_a_mcg: null,
          vitamin_c_mg: null, vitamin_d_mcg: null, vitamin_b12_mcg: null, vitamin_e_mg: null,
          vitamin_k_mcg: null, folate_mcg: null, omega3_g: null,
          time: "12:00", source: "manually_edited", note: null, grounding: null,
          serving_unit_options: [{ unit: "bowl", grams_per_unit: 400, quantity: 1 }],
          selected_serving_unit: "bowl",
          selected_serving_quantity: 1,
          constituents: [
            {
              name: "A", calories: 300, protein_g: 30, carbs_g: 10, fat_g: 15, quantity_g: 200,
              emoji: null,
              serving_unit_options: [{ unit: "piece", grams_per_unit: 100, quantity: 2 }],
              selected_serving_unit: "piece",
              selected_serving_quantity: 2,
            },
            {
              name: "B", calories: 200, protein_g: 10, carbs_g: 20, fat_g: 5, quantity_g: 200,
              emoji: null, serving_unit_options: [], selected_serving_unit: null, selected_serving_quantity: null,
            },
          ],
        }],
      }],
    }],
  };
  const entries = importDiary(doc, idGen);
  const reExported = exportDiary({
    entries,
    targets: { "2026-01-02": { calories: 2000, proteinG: 150, carbsG: 200, fatG: 60 } },
    dateRange: { start: "2026-01-02", end: "2026-01-02" },
  });
  const item = reExported.days[0].meals[0].items[0];
  assert.equal(item.selected_serving_unit, "bowl");
  assert.equal(item.serving_unit_options[0].grams_per_unit, 400);
  assert.equal(item.constituents.length, 2);
  assert.equal(item.constituents[0].selected_serving_unit, "piece");
  assert.equal(item.constituents[0].serving_unit_options[0].grams_per_unit, 100);
});

test("accepts NoFUD app stamp with current format", () => {
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
          serving_unit_options: [], selected_serving_unit: null, selected_serving_quantity: null,
          constituents: [],
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
