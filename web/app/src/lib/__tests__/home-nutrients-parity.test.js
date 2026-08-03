// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  formatFoodChips,
  chipsForFoodLogDisplay,
  formatMacroChipLine,
} from "../home-nutrients.js";

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

  it("includes fiber in display chips when selected", () => {
    assert.deepEqual(chipsForFoodLogDisplay(["proteinG", "carbsG", "fatG", "fiberG"]), [
      "proteinG",
      "carbsG",
      "fatG",
      "fiberG",
    ]);
  });

  it("emits colored glyph HTML including fiber when selected", () => {
    const html = formatFoodChips(entry, ["proteinG", "carbsG", "fatG", "fiberG"]);
    assert.match(html, /macro-chip--protein/);
    assert.match(html, /macro-chip--carbs/);
    assert.match(html, /macro-chip--fat/);
    assert.match(html, /macro-chip--fiber/);
    assert.match(html, />Fi</);
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
