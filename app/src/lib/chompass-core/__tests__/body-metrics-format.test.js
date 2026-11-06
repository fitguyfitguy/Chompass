// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { importBodyMetrics, exportBodyMetrics, deterministicFileId, BODY_METRICS_FORMAT_VERSION } from "../body-metrics-format.js";
import { loadParityFixture } from "../../parity-fixtures.js";

test("imports the parity body-metrics fixture without throwing", async () => {
  const doc = loadParityFixture("body-metrics-sample.json");
  assert.equal(doc.export.kind, "body_metrics");
  assert.equal(doc.export.format_version, BODY_METRICS_FORMAT_VERSION);
  const { weights, bodyFat, measurements } = await importBodyMetrics(doc);
  assert.ok(Array.isArray(weights));
  assert.ok(Array.isArray(bodyFat));
  assert.ok(Array.isArray(measurements));
  assert.ok(weights.length > 0);
  assert.ok(bodyFat.length > 0);
  assert.ok(measurements.length > 0);
  for (const w of weights) {
    assert.equal(typeof w.id, "string");
    assert.equal(typeof w.weightKg, "number");
  }
});

test("round-trips weights/bodyFat/measurements through export->import", async () => {
  const doc = loadParityFixture("body-metrics-sample.json");
  const parsed = await importBodyMetrics(doc);
  const reExported = exportBodyMetrics(parsed);
  assert.equal(reExported.export.kind, "body_metrics");
  assert.equal(reExported.export.format_version, BODY_METRICS_FORMAT_VERSION);
  assert.equal(reExported.weights.length, doc.weights?.length ?? 0);
  assert.equal(reExported.body_fat.length, doc.body_fat?.length ?? 0);
  assert.equal(reExported.measurements.length, doc.measurements?.length ?? 0);
});

test("deterministic id generation is stable for the same kind:timestamp input", async () => {
  const idA = await deterministicFileId("weight", 1234567890123);
  const idB = await deterministicFileId("weight", 1234567890123);
  assert.equal(idA, idB);
  // RFC 4122 v3 shape: version nibble '3', variant bits '8','9','a','b'
  assert.match(idA, /^[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});

test("missing-id records get re-upserted to the same id on repeated import (no duplication)", async () => {
  const doc = {
    export: { app: "nofud", kind: "body_metrics", format_version: BODY_METRICS_FORMAT_VERSION },
    weights: [{ date: "2026-01-01T00:00:00Z", weight_kg: 80 }],
    body_fat: [],
    measurements: [],
  };
  const first = await importBodyMetrics(doc);
  const second = await importBodyMetrics(doc);
  assert.equal(first.weights[0].id, second.weights[0].id);
});

test("rejects unsupported format_version", async () => {
  const doc = { export: { app: "nofud", kind: "body_metrics", format_version: "0.1" }, weights: [], body_fat: [], measurements: [] };
  await assert.rejects(() => importBodyMetrics(doc));
});

test("drops body-fat readings outside 3-65 percent on import", async () => {
  const doc = {
    export: { app: "nofud", kind: "body_metrics", format_version: BODY_METRICS_FORMAT_VERSION },
    weights: [],
    body_fat: [
      { date: "2026-01-01T00:00:00Z", body_fat_percent: 2.0 },
      { date: "2026-01-02T00:00:00Z", body_fat_percent: 70.0 },
      { date: "2026-01-03T00:00:00Z", body_fat_percent: 22.0 },
    ],
    measurements: [],
  };
  const { bodyFat } = await importBodyMetrics(doc);
  assert.equal(bodyFat.length, 1);
  assert.equal(bodyFat[0].bodyFatPercent, 22.0);
});

// --- Scale composition (body-metrics 1.1) ---

test("imports the 1.1 composition fixture and round-trips every field", async () => {
  const doc = loadParityFixture("body-metrics-composition-sample.json");
  assert.equal(doc.export.format_version, "1.1");
  const parsed = await importBodyMetrics(doc);
  assert.equal(parsed.bodyComposition.length, 3);
  const full = parsed.bodyComposition.find((c) => c.date === "2026-01-01T07:10:00Z");
  assert.ok(full);
  assert.equal(full.boneMassKg, 3.2);
  assert.equal(full.totalBodyWaterKg, 46.0);
  assert.equal(full.muscleMassKg, 30.4);
  assert.equal(full.leanMassKg, 65.2);
  assert.equal(full.visceralFatRating, 8.0);

  const reExported = exportBodyMetrics(parsed);
  assert.equal(reExported.export.format_version, "1.1");
  // Field-level round-trip: the wire allows a missing key or an explicit null for an
  // absent metric (Android omits, the PWA writes null), so compare values, not keys.
  const COMPOSITION_KEYS = [
    "bone_mass_kg",
    "total_body_water_kg",
    "muscle_mass_kg",
    "lean_mass_kg",
    "visceral_fat_rating",
  ];
  assert.equal(reExported.body_composition.length, doc.body_composition.length);
  reExported.body_composition.forEach((row, i) => {
    const original = doc.body_composition[i];
    assert.equal(row.id, original.id);
    assert.equal(row.date, original.date);
    for (const key of COMPOSITION_KEYS) {
      assert.equal(row[key] ?? null, original[key] ?? null, `${original.date} ${key}`);
    }
  });
});

test("a document without composition rows stays 1.0", () => {
  const doc = exportBodyMetrics({ weights: [], bodyFat: [], measurements: [] });
  assert.equal(doc.export.format_version, "1.0");
  assert.equal("body_composition" in doc, false);
});

test("re-import of a composition fixture is idempotent (deterministic ids)", async () => {
  const doc = loadParityFixture("body-metrics-composition-sample.json");
  const withIdsStripped = {
    ...doc,
    body_composition: doc.body_composition.map(({ id, ...rest }) => rest),
  };
  const first = await importBodyMetrics(withIdsStripped);
  const second = await importBodyMetrics(withIdsStripped);
  assert.deepEqual(first.bodyComposition.map((c) => c.id), second.bodyComposition.map((c) => c.id));
});

test("drops out-of-bounds composition values and empty rows", async () => {
  const doc = {
    export: { app: "Chompass", kind: "body_metrics", format_version: "1.1" },
    weights: [],
    body_fat: [],
    measurements: [],
    body_composition: [
      { date: "2026-01-01T07:00:00Z", lean_mass_kg: 999, visceral_fat_rating: 80 },
      { date: "2026-01-02T07:00:00Z", lean_mass_kg: 64.5, bone_mass_kg: -1 },
    ],
  };
  const { bodyComposition } = await importBodyMetrics(doc);
  // The first row loses both fields and never lands; the second keeps lean only.
  assert.equal(bodyComposition.length, 1);
  assert.equal(bodyComposition[0].leanMassKg, 64.5);
  assert.equal(bodyComposition[0].boneMassKg, null);
});
