// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import { microsCompositionSignature, microsStaleFor } from "../micros-composition.js";

/** @param {string} name @param {number} servingSizeGrams @returns {import("../models.js").FoodConstituent} */
function row(name, servingSizeGrams) {
  return /** @type {any} */ ({ name, servingSizeGrams });
}

test("empty constituents signature is null", () => {
  assert.equal(microsCompositionSignature([]), null);
});

test("uniform scaling keeps the signature (scale invariance)", () => {
  const base = [row("Rice", 100), row("Chicken", 150)];
  const scaled = [row("Rice", 200), row("Chicken", 300)];
  assert.equal(microsCompositionSignature(base), microsCompositionSignature(scaled));
});

test("row order does not change the signature", () => {
  const a = [row("Rice", 100), row("Chicken", 150)];
  const b = [row("Chicken", 150), row("Rice", 100)];
  assert.equal(microsCompositionSignature(a), microsCompositionSignature(b));
});

test("a changed share changes the signature", () => {
  const base = [row("Rice", 100), row("Chicken", 150)];
  const halved = [row("Rice", 50), row("Chicken", 150)];
  assert.notEqual(microsCompositionSignature(base), microsCompositionSignature(halved));
});

test("adding a constituent changes the signature", () => {
  const base = [row("Rice", 100), row("Chicken", 150)];
  const added = [row("Rice", 100), row("Chicken", 150), row("Sauce", 20)];
  assert.notEqual(microsCompositionSignature(base), microsCompositionSignature(added));
});

test("removing a constituent changes the signature", () => {
  const base = [row("Rice", 100), row("Chicken", 150)];
  const removed = [row("Rice", 100)];
  assert.notEqual(microsCompositionSignature(base), microsCompositionSignature(removed));
});

test("renaming a constituent changes the signature", () => {
  const base = [row("Rice", 100), row("Chicken", 150)];
  const renamed = [row("Brown Rice", 100), row("Chicken", 150)];
  assert.notEqual(microsCompositionSignature(base), microsCompositionSignature(renamed));
});

test("names are trimmed and lowercased", () => {
  const plain = [row("rice", 100), row("chicken", 150)];
  const padded = [row("  Rice ", 100), row("CHICKEN", 150)];
  assert.equal(microsCompositionSignature(plain), microsCompositionSignature(padded));
});

test("null stored signature never reads stale; mismatched one does", () => {
  const mix = [row("Rice", 100)];
  assert.equal(microsStaleFor(null, mix), false);
  assert.equal(microsStaleFor(microsCompositionSignature(mix), mix), false);
  assert.equal(microsStaleFor("other@1000", mix), true);
  assert.equal(microsStaleFor(microsCompositionSignature(mix), [row("Rice", 200), row("Sauce", 50)]), true);
});
