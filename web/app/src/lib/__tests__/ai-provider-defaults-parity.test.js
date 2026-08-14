// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { PROVIDERS, resolveProviderModel } from "../ai/providers.js";
import { loadParityFixture } from "../parity-fixtures.js";

const fixture = loadParityFixture("ai-provider-defaults.json");

/** @type {Record<string, keyof typeof PROVIDERS>} */
const PWA_IDS = {
  gemini: "gemini",
  anthropic: "anthropic",
  openai: "openai_compatible",
};

describe("AI provider model defaults (parity fixture)", () => {
  for (const [fid, pwaId] of Object.entries(PWA_IDS)) {
    it(`${fid} matches testdata/parity/ai-provider-defaults.json`, () => {
      const expected = fixture.providers[fid];
      const meta = PROVIDERS[pwaId];
      assert.equal(meta.defaultModel, expected.defaultModel);
      assert.equal(meta.defaultFallbackModel, expected.defaultFallbackModel);
      assert.deepEqual(meta.models, expected.models);
      assert.deepEqual(meta.modelTiers ?? {}, expected.modelTiers ?? {});
    });
  }

  it("resolves blank / unknown models to defaults", () => {
    assert.equal(resolveProviderModel("gemini", null, "primary"), "gemini-3.7-flash");
    assert.equal(resolveProviderModel("gemini", "", "fallback"), "gemini-3.5-flash-lite");
    assert.equal(resolveProviderModel("anthropic", null, "primary"), "claude-haiku-4-5");
    assert.equal(resolveProviderModel("anthropic", "", "fallback"), "claude-sonnet-5");
    assert.equal(resolveProviderModel("openai_compatible", null, "primary"), "gpt-5.4-mini");
    assert.equal(resolveProviderModel("openai_compatible", "", "fallback"), "gpt-5.4-nano");
    assert.equal(resolveProviderModel("gemini", "not-a-real-model", "primary"), "gemini-3.7-flash");
    assert.equal(resolveProviderModel("gemini", "gemini-2.5-pro", "primary"), "gemini-2.5-pro");
  });
});
