// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { importBodyMetrics, exportBodyMetrics, deterministicFileId, BODY_METRICS_FORMAT_VERSION } from "../body-metrics-format.js";

const REPO_ROOT = fileURLToPath(new URL("../../../../../../", import.meta.url));

function loadFixture(name) {
  return JSON.parse(readFileSync(`${REPO_ROOT}${name}`, "utf8"));
}

test("imports the real repo body-metrics fixture without throwing", async () => {
  const doc = loadFixture("FudAI-Weight-Import.json");
  assert.equal(doc.export.kind, "body_metrics");
  assert.equal(doc.export.format_version, BODY_METRICS_FORMAT_VERSION);
  const { weights, bodyFat, measurements } = await importBodyMetrics(doc);
  assert.ok(Array.isArray(weights));
  assert.ok(Array.isArray(bodyFat));
  assert.ok(Array.isArray(measurements));
  for (const w of weights) {
    assert.equal(typeof w.id, "string");
    assert.equal(typeof w.weightKg, "number");
  }
});

test("round-trips weights/bodyFat/measurements through export->import", async () => {
  const doc = loadFixture("FudAI-Weight-Import.json");
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
