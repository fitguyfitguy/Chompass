// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { PROVIDERS, assertCleartextAllowed, isInsecureHttpUrl } from "../ai/providers.js";
import { fillFoodEntry } from "../ai/food-analyze.js";

/**
 * Cleartext gate (Android AiHttp parity): http:// custom endpoints to
 * non-loopback hosts refuse to send the API key unless the user opted in;
 * loopback stays allowed unconditionally.
 */

test("isInsecureHttpUrl_flagsOnlyNonLoopbackHttp", () => {
  assert.equal(isInsecureHttpUrl("https://api.example.com/v1"), false);
  assert.equal(isInsecureHttpUrl("http://localhost:1234/v1"), false);
  assert.equal(isInsecureHttpUrl("http://127.0.0.1:8080"), false);
  assert.equal(isInsecureHttpUrl("http://[::1]:8080"), false);
  assert.equal(isInsecureHttpUrl(undefined), false);
  assert.equal(isInsecureHttpUrl(""), false);
  assert.equal(isInsecureHttpUrl("http://192.168.1.50:1234/v1"), true);
  assert.equal(isInsecureHttpUrl("http://llm.corp.internal:8000"), true);
  // Unparsable URLs fail closed.
  assert.equal(isInsecureHttpUrl("http://not a url"), true);
});

test("assertCleartextAllowed_allowsLoopbackAndOptIn_blocksOthers", () => {
  assert.doesNotThrow(() => assertCleartextAllowed("http://localhost:1234/v1", false));
  assert.doesNotThrow(() => assertCleartextAllowed("http://192.168.1.50:1234/v1", true));
  assert.doesNotThrow(() => assertCleartextAllowed("https://api.example.com/v1", false));
  assert.throws(
    () => assertCleartextAllowed("http://192.168.1.50:1234/v1", false),
    /Insecure HTTP is blocked/,
  );
});

/** @param {Partial<import("../chompass-core/models.js").FoodEntry>} [overrides] */
function gappedEntry(overrides = {}) {
  return {
    id: "e1",
    name: "Overnight oats",
    mealType: "breakfast",
    date: "2026-06-15",
    time: "08:00",
    source: "manual",
    calories: 0,
    proteinG: 0,
    carbsG: 0,
    fatG: 12.5,
    quantityG: 300,
    ...overrides,
  };
}

/** @param {string} baseUrl @param {boolean} allowInsecureHttp */
async function captureFill(baseUrl, allowInsecureHttp) {
  const original = PROVIDERS.openai_compatible.send;
  /** @type {{called: number, error: unknown}} */
  const state = { called: 0, error: null };
  PROVIDERS.openai_compatible.send = async () => {
    state.called += 1;
    return {
      text: JSON.stringify({ name: "Overnight oats", calories: 540, proteinG: 42, carbsG: 60, fatG: 12.5, quantityG: 300 }),
      toolCalls: [],
    };
  };
  try {
    await fillFoodEntry({
      providerId: "openai_compatible",
      config: { apiKey: "test-key", baseUrl },
      entry: gappedEntry(),
      prefsOverride: /** @type {any} */ ({
        aiFeaturesEnabled: true,
        aiFallbackEnabled: false,
        allowInsecureHttp,
      }),
    });
  } catch (err) {
    state.error = err;
  } finally {
    PROVIDERS.openai_compatible.send = original;
  }
  return state;
}

test("fillFoodEntry_blocksInsecureHttpBeforeAnySend", async () => {
  const state = await captureFill("http://192.168.1.50:1234/v1", false);
  assert.match(String(state.error), /Insecure HTTP is blocked/);
  assert.equal(state.called, 0);
});

test("fillFoodEntry_allowsLoopbackHttpWithoutOptIn", async () => {
  const state = await captureFill("http://localhost:1234/v1", false);
  assert.equal(state.error, null);
  assert.equal(state.called, 1);
});

test("fillFoodEntry_allowsInsecureHttpWhenOptedIn", async () => {
  const state = await captureFill("http://192.168.1.50:1234/v1", true);
  assert.equal(state.error, null);
  assert.equal(state.called, 1);
});
