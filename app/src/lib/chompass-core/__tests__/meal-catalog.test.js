import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { defaultCatalog, isValid, mealIdAt, parseCatalog } from "../meal-catalog.js";

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

  it("keeps schedules that wrap midnight", () => {
    const c = parseCatalog({
      version: 1,
      meals: [
        { id: "breakfast", startMinutes: 20 * 60, enabled: true },
        { id: "lunch", startMinutes: 60, enabled: true },
        { id: "dinner", startMinutes: 5 * 60, enabled: true },
        { id: "snack", startMinutes: 9 * 60, enabled: true },
      ],
    });
    assert.equal(c.meals.length, 4); // kept, not replaced by the 5-meal default
    const at = (h, m) => mealIdAt(c, new Date(2026, 0, 1, h, m));
    assert.equal(at(2, 0), "lunch");
    assert.equal(at(23, 0), "breakfast");
    assert.equal(at(0, 30), "breakfast");
  });

  it("falls back when starts duplicate across the wrap", () => {
    const c = parseCatalog({
      version: 1,
      meals: [
        { id: "breakfast", startMinutes: 7 * 60, enabled: true },
        { id: "lunch", startMinutes: 19 * 60, enabled: true },
        { id: "dinner", startMinutes: 7 * 60, enabled: true },
      ],
    });
    assert.equal(c.meals.length, 5); // default
  });

  it("keeps a 2am dinner in default slot order", () => {
    const c = defaultCatalog();
    c.meals.find((m) => m.id === "dinner").startMinutes = 2 * 60;
    assert.equal(isValid(c), true);
    const at = (h, m) => mealIdAt(c, new Date(2026, 0, 1, h, m));
    assert.equal(at(1, 59), "snack");
    assert.equal(at(2, 0), "dinner");
    assert.equal(at(4, 59), "dinner");
  });

});
