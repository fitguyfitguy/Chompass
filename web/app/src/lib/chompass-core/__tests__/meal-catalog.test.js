import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { defaultCatalog, mealIdAt, parseCatalog } from "../meal-catalog.js";

describe("meal catalog", () => {
  it("defaults match legacy four windows", () => {
    const c = defaultCatalog();
    const at = (h, m) => mealIdAt(c, new Date(2026, 0, 1, h, m));
    assert.equal(at(4, 59), "snack");
    assert.equal(at(5, 0), "breakfast");
    assert.equal(at(11, 0), "lunch");
    assert.equal(at(15, 0), "dinner");
    assert.equal(at(21, 0), "snack");
  });

  it("falls back when catalog is empty", () => {
    const c = parseCatalog({ meals: [] });
    assert.equal(c.meals[0].id, "breakfast");
  });
});
