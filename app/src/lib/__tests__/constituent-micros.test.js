// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  MAX_MICRO_UNITS,
  microOrNull,
  parseConstituentsFromPrediction,
  reconcileConstituents,
} from "../chompass-core/constituents.js";

// #86 review parity: Android InputSanitizer.micro semantics — clamp, don't drop.
test("microOrNull_clampsLikeAndroidInputSanitizer", () => {
  assert.equal(microOrNull(null), null);
  assert.equal(microOrNull(undefined), null);
  assert.equal(microOrNull("abc"), null);
  assert.equal(microOrNull(Number.NaN), null);
  assert.equal(microOrNull(Infinity), null);
  assert.equal(microOrNull(-5), 0);
  assert.equal(microOrNull("-3.2"), 0);
  assert.equal(microOrNull("5.5"), 5.5);
  assert.equal(microOrNull(2e9), MAX_MICRO_UNITS);
  assert.equal(microOrNull(12.34), 12.34);
});

test("parseConstituentsFromPrediction_clampsOutOfRangeMicros", () => {
  const rows = parseConstituentsFromPrediction({
    constituents: [
      {
        name: "Bread",
        calories: 100,
        protein: 4,
        carbs: 20,
        fat: 1,
        serving_size_grams: 50,
        sodium: -5,
        potassium: 250000,
        fiber: "3.3",
      },
    ],
  });
  assert.equal(rows.length, 1);
  assert.equal(rows[0].sodiumMg, 0); // clamped, still present (Android parity)
  assert.equal(rows[0].potassiumMg, MAX_MICRO_UNITS);
  assert.equal(rows[0].fiberG, 3.3);
});

test("reconcileConstituents_factorOneKeepsMicroPrecision", () => {
  const meal = {
    name: "Toast",
    calories: 100,
    proteinG: 4,
    carbsG: 20,
    fatG: 1,
    quantityG: 50,
    constituents: [
      {
        name: "Toast",
        calories: 100,
        proteinG: 4,
        carbsG: 20,
        fatG: 1,
        servingSizeGrams: 50,
        fiberG: 0.45,
      },
    ],
  };
  const reconciled = reconcileConstituents(meal);
  assert.equal(reconciled.constituents.length, 1);
  // Grams factor is exactly 1: no rounding drift (Android microsScaled(1.0)
  // is the identity — 0.45 must survive, not round to 0.5).
  assert.equal(reconciled.constituents[0].fiberG, 0.45);
});
