// @ts-check
import { test, mock } from "node:test";
import assert from "node:assert/strict";
import { lookupBarcode, resetLookupCooldown } from "../off-client.js";

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

/**
 * Install a Map-backed localStorage (node tests run without one) and return
 * the backing store plus a restore function.
 */
function stubLocalStorage() {
  /** @type {Map<string, string>} */
  const store = new Map();
  const api = {
    getItem: (/** @type {string} */ key) => (store.has(key) ? store.get(key) : null),
    setItem: (/** @type {string} */ key, /** @type {string} */ value) => {
      store.set(key, String(value));
    },
    removeItem: (/** @type {string} */ key) => {
      store.delete(key);
    },
  };
  const original = globalThis.localStorage;
  globalThis.localStorage = /** @type {any} */ (api);
  return {
    store,
    restore() {
      globalThis.localStorage = /** @type {any} */ (original);
    },
  };
}

/** EAN-13 with a valid check digit around the 2000000xxxxxx in-store range. */
function ean13(n) {
  const body = `2000000${String(n).padStart(5, "0")}`;
  let sum = 0;
  for (let i = 0; i < 12; i++) sum += Number(body[i]) * (i % 2 ? 3 : 1);
  return body + String((10 - (sum % 10)) % 10);
}

/** @param {string} name */
function foundProductResponse(name) {
  return new Response(
    JSON.stringify({
      status: 1,
      product: {
        product_name: name,
        serving_quantity: 50,
        nutriments: { "energy-kcal_100g": 200, proteins_100g: 10, carbohydrates_100g: 20, fat_100g: 5 },
      },
    }),
    { status: 200 }
  );
}

const NOT_FOUND_RESPONSE = () => new Response(JSON.stringify({ status: 0 }), { status: 200 });

test("lookupBarcode_cachesProductsForInstantRepeatScans", async () => {
  const originalFetch = globalThis.fetch;
  const ls = stubLocalStorage();
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      calls++;
      return foundProductResponse("Cached Bar");
    }
  );
  try {
    const first = await lookupBarcode("9339687206605");
    assert.ok(first);
    // Repeat scan works offline: the network is gone and the cache still answers.
    globalThis.fetch = /** @type {typeof fetch} */ (
      async () => {
        throw new Error("offline");
      }
    );
    const second = await lookupBarcode("9339687206605");
    assert.deepEqual(second, first);
    assert.equal(calls, 1);
    const stored = JSON.parse(ls.store.get("chompass.offLookupCache.v1") ?? "{}");
    assert.equal(typeof stored["9339687206605"].ts, "number");
    assert.equal(stored["9339687206605"].product.name, "Cached Bar");
  } finally {
    globalThis.fetch = originalFetch;
    ls.restore();
  }
});

test("lookupBarcode_negativeCacheHoldsFor24h", async () => {
  const originalFetch = globalThis.fetch;
  const ls = stubLocalStorage();
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      calls++;
      return NOT_FOUND_RESPONSE();
    }
  );
  try {
    assert.equal(await lookupBarcode("9339687206605"), null);
    globalThis.fetch = /** @type {typeof fetch} */ (
      async () => {
        throw new Error("offline");
      }
    );
    assert.equal(await lookupBarcode("9339687206605"), null);
    assert.equal(calls, 1);
    const stored = JSON.parse(ls.store.get("chompass.offLookupCache.v1") ?? "{}");
    assert.equal(stored["9339687206605"].product, null);
  } finally {
    globalThis.fetch = originalFetch;
    ls.restore();
  }
});

test("lookupBarcode_negativeCacheExpiresAfter24h", async () => {
  const originalFetch = globalThis.fetch;
  const ls = stubLocalStorage();
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      calls++;
      return calls === 1 ? NOT_FOUND_RESPONSE() : foundProductResponse("Late Bar");
    }
  );
  try {
    assert.equal(await lookupBarcode("9339687206605"), null);
    // Age the negative entry past its 24 h TTL.
    const stored = JSON.parse(ls.store.get("chompass.offLookupCache.v1") ?? "{}");
    stored["9339687206605"].ts -= 24 * 60 * 60 * 1000 + 1000;
    ls.store.set("chompass.offLookupCache.v1", JSON.stringify(stored));
    const product = await lookupBarcode("9339687206605");
    assert.ok(product);
    assert.equal(product.name, "Late Bar");
    assert.equal(calls, 2);
  } finally {
    globalThis.fetch = originalFetch;
    ls.restore();
  }
});

test("lookupBarcode_evictsLeastRecentlyUsedPast200", async () => {
  const originalFetch = globalThis.fetch;
  const ls = stubLocalStorage();
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async (/** @type {any} */ input) => {
      calls++;
      const code = String(input).match(/product\/(\d+)\.json/)?.[1] ?? "";
      return foundProductResponse(`Bar ${code}`);
    }
  );
  try {
    const codes = Array.from({ length: 201 }, (_, i) => ean13(i));
    for (const code of codes) await lookupBarcode(code);
    const stored = JSON.parse(ls.store.get("chompass.offLookupCache.v1") ?? "{}");
    assert.equal(Object.keys(stored).length, 200);
    assert.ok(!(codes[0] in stored), "oldest entry evicted");
    assert.ok(codes[200] in stored, "newest entry kept");
    // The evicted oldest code refetches; the newest is still served from cache.
    await lookupBarcode(codes[0]);
    assert.equal(calls, 202);
    await lookupBarcode(codes[200]);
    assert.equal(calls, 202);
  } finally {
    globalThis.fetch = originalFetch;
    ls.restore();
  }
});

/**
 * Drain the microtask queue so promise chains parked on a mocked timer
 * progress to the next timer between ticks. Uses plain microtasks only —
 * mock.timers also mocks setImmediate, which would hang here.
 */
async function flushMicrotasks(turns = 25) {
  for (let i = 0; i < turns; i++) await Promise.resolve();
}

test("lookupBarcode_retries5xxWithBackoffThenSucceeds", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      calls++;
      return calls < 3 ? new Response(null, { status: 500 }) : foundProductResponse("Retry Bar");
    }
  );
  try {
    mock.timers.enable({ now: 0 });
    const pending = lookupBarcode("9339687206605");
    await flushMicrotasks(); // attempt 1 settles, 300 ms backoff scheduled
    mock.timers.tick(300);
    await flushMicrotasks(); // attempt 2 settles, 600 ms backoff scheduled
    mock.timers.tick(600);
    const product = await pending;
    assert.ok(product);
    assert.equal(product.name, "Retry Bar");
    assert.equal(calls, 3);
  } finally {
    mock.timers.reset();
    globalThis.fetch = originalFetch;
  }
});

test("lookupBarcode_5xxRetriesExhaustedThrows", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      calls++;
      return new Response(null, { status: 503 });
    }
  );
  try {
    mock.timers.enable({ now: 0 });
    const pending = lookupBarcode("9339687206605");
    await flushMicrotasks();
    mock.timers.tick(300);
    await flushMicrotasks();
    mock.timers.tick(600);
    await assert.rejects(pending, /Open Food Facts lookup failed \(503\)/);
    assert.equal(calls, 3);
  } finally {
    mock.timers.reset();
    globalThis.fetch = originalFetch;
  }
});

test("lookupBarcode_retries429RespectingRetryAfter", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      calls++;
      return calls === 1
        ? new Response(null, { status: 429, headers: { "retry-after": "1" } })
        : foundProductResponse("Throttled Bar");
    }
  );
  try {
    mock.timers.enable({ now: 0 });
    const pending = lookupBarcode("9339687206605");
    await flushMicrotasks(); // 429 settles, Retry-After wait scheduled
    mock.timers.tick(1000);
    const product = await pending;
    assert.ok(product);
    assert.equal(product.name, "Throttled Bar");
    assert.equal(calls, 2);
  } finally {
    mock.timers.reset();
    globalThis.fetch = originalFetch;
    resetLookupCooldown();
  }
});

test("lookupBarcode_429TripsCooldownAndFailsFastWithoutNetwork", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      calls++;
      return new Response(null, { status: 429, headers: { "retry-after": "60" } });
    }
  );
  try {
    // Retry-After 60 s exceeds the in-lookup retry budget: fail now, and the
    // module cooldown makes the next lookup fail without touching the network.
    await assert.rejects(lookupBarcode("9339687206605"), /asked to slow down/);
    await assert.rejects(lookupBarcode(ean13(42)), /asked to slow down/);
    assert.equal(calls, 1);
  } finally {
    globalThis.fetch = originalFetch;
    resetLookupCooldown();
  }
});

test("lookupBarcode_cooldownCappedAt120s", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      calls++;
      return calls === 1
        ? new Response(null, { status: 429, headers: { "retry-after": "300" } })
        : foundProductResponse("After Cooldown");
    }
  );
  try {
    mock.timers.enable({ now: 0 });
    await assert.rejects(lookupBarcode("9339687206605"), /asked to slow down/);
    // Retry-After was 300 s but the cooldown caps at 120 s.
    mock.timers.tick(121_000);
    const product = await lookupBarcode("9339687206605");
    assert.ok(product);
    assert.equal(product.name, "After Cooldown");
    assert.equal(calls, 2);
  } finally {
    mock.timers.reset();
    globalThis.fetch = originalFetch;
    resetLookupCooldown();
  }
});

test("lookupBarcode_timeoutYieldsPlainMessageAndSendsSignal", async () => {
  const originalFetch = globalThis.fetch;
  /** @type {any} */
  let lastOptions;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async (/** @type {any} */ _input, options) => {
      lastOptions = options;
      throw new DOMException("signal timed out", "TimeoutError");
    }
  );
  try {
    await assert.rejects(lookupBarcode("9339687206605"), /timed out/);
    assert.ok(lastOptions?.signal instanceof AbortSignal);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("lookupBarcode_nonRetryableStatusThrowsImmediately", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = /** @type {typeof fetch} */ (
    async () => {
      calls++;
      return new Response(null, { status: 404 });
    }
  );
  try {
    await assert.rejects(lookupBarcode("9339687206605"), /Open Food Facts lookup failed \(404\)/);
    assert.equal(calls, 1);
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
