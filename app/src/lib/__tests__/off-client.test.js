// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { lookupBarcode } from "../off-client.js";

/**
 * OFF lookup request shape (product photo + metadata enrichment): the fields
 * parameter carries the new OFF fields, and a not-found product still maps to
 * null (callers already handle null).
 */

test("lookupBarcode_requestsEnrichedFields", async () => {
  const originalFetch = globalThis.fetch;
  const urls = [];
  globalThis.fetch = /** @type {typeof fetch} */ (
    async (/** @type {any} */ input) => {
      urls.push(String(input));
      return new Response(
        JSON.stringify({
          status: 1,
          product: {
            product_name: "Test Bar",
            serving_quantity: 50,
            nutriments: { "energy-kcal_100g": 200, proteins_100g: 10, carbohydrates_100g: 20, fat_100g: 5 },
          },
        }),
        { status: 200 }
      );
    }
  );
  try {
    const prefill = await lookupBarcode("9339687206605");
    assert.ok(prefill);
    assert.equal(urls.length, 1);
    const fields = new URL(urls[0]).searchParams.get("fields") ?? "";
    assert.ok(fields.includes("image_front_url"));
    assert.ok(fields.includes("ingredients_text"));
    assert.ok(fields.includes("nutriscore_grade"));
    assert.ok(fields.includes("labels_tags"));
    assert.ok(fields.includes("product_quantity"));
    assert.ok(prefill.productMetadata);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("lookupBarcode_notFoundReturnsNull", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => new Response(JSON.stringify({ status: 0 }), { status: 200 })
  );
  try {
    assert.equal(await lookupBarcode("9339687206605"), null);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("lookupBarcode_nonProductTextReturnsNullWithoutFetch", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      throw new Error("fetch must not be called for non-product text");
    }
  );
  try {
    assert.equal(await lookupBarcode("https://brand.example/promo"), null);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
