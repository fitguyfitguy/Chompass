// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  formatFoodChips,
  chipsForFoodLogDisplay,
  formatMacroChipLine,
} from "../home-nutrients.js";
import { PROVIDERS, resolveProviderModel } from "../ai/providers.js";

describe("food log macro chips", () => {
  const entry = {
    id: "1",
    name: "Oats",
    date: "2026-07-23",
    time: "07:30",
    mealType: "breakfast",
    calories: 330,
    proteinG: 13,
    carbsG: 58,
    fatG: 8,
    fiberG: 6,
    sugarG: 12,
    source: "manual",
  };

  it("excludes fiber from display chips even when selected", () => {
    assert.deepEqual(chipsForFoodLogDisplay(["proteinG", "carbsG", "fatG", "fiberG"]), [
      "proteinG",
      "carbsG",
      "fatG",
    ]);
  });

  it("emits colored glyph HTML without fiber", () => {
    const html = formatFoodChips(entry, ["proteinG", "carbsG", "fatG", "fiberG"]);
    assert.match(html, /macro-chip--protein/);
    assert.match(html, /macro-chip--carbs/);
    assert.match(html, /macro-chip--fat/);
    assert.doesNotMatch(html, /macro-chip--fiber/);
    assert.doesNotMatch(html, />Fi</);
  });

  it("formats meal header totals with colored chips", () => {
    const html = formatMacroChipLine(
      { proteinG: 13, carbsG: 58, fatG: 8 },
      ["proteinG", "carbsG", "fatG"]
    );
    assert.match(html, /13<span class="macro-chip macro-chip--protein">P<\/span>/);
    assert.match(html, /58<span class="macro-chip macro-chip--carbs">C<\/span>/);
    assert.match(html, /8<span class="macro-chip macro-chip--fat">F<\/span>/);
  });
});

describe("AI provider model defaults", () => {
  it("uses Android Gemini primary and fallback defaults", () => {
    assert.equal(PROVIDERS.gemini.defaultModel, "gemini-3.6-flash");
    assert.equal(PROVIDERS.gemini.defaultFallbackModel, "gemini-3.5-flash-lite");
    assert.ok(PROVIDERS.gemini.models.includes("gemini-3.6-flash"));
    assert.ok(PROVIDERS.gemini.models.includes("gemini-3.5-flash-lite"));
  });

  it("resolves blank / unknown models to defaults", () => {
    assert.equal(resolveProviderModel("gemini", null, "primary"), "gemini-3.6-flash");
    assert.equal(resolveProviderModel("gemini", "", "fallback"), "gemini-3.5-flash-lite");
    assert.equal(resolveProviderModel("gemini", "not-a-real-model", "primary"), "gemini-3.6-flash");
    assert.equal(resolveProviderModel("gemini", "gemini-2.5-pro", "primary"), "gemini-2.5-pro");
  });
});
