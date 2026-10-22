// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { gapReport, fillMissingFrom, missingGroups } from "../entry-gaps.js";
import { MICRO_FIELDS } from "../diary-format.js";

/**
 * Fill-missing scan + merge semantics — PWA mirror of Android
 * FoodEntryGapScanTest / FillMissingMergeTest (FoodEntryGaps.kt parity).
 */

/** @param {Partial<import("../models.js").FoodEntry>} [overrides] */
function entry(overrides = {}) {
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
    fatG: 0,
    // Store rows carry every micro key (diary-format import shape); 0 counts
    // as present, null as a gap.
    sugarG: 0,
    addedSugarG: 0,
    fiberG: 0,
    saturatedFatG: 0,
    monounsaturatedFatG: 0,
    polyunsaturatedFatG: 0,
    cholesterolMg: 0,
    sodiumMg: 0,
    potassiumMg: 0,
    transFatG: 0,
    calciumMg: 0,
    ironMg: 0,
    magnesiumMg: 0,
    zincMg: 0,
    vitaminAMcg: 0,
    vitaminCMg: 0,
    vitaminDMcg: 0,
    vitaminB12Mcg: 0,
    vitaminEMg: 0,
    vitaminKMcg: 0,
    folateMcg: 0,
    omega3G: 0,
    caffeineMg: 0,
    ...overrides,
  };
}

/** @param {Partial<import("../models.js").FoodConstituent>} [overrides] */
function constituent(overrides = {}) {
  return {
    name: "Rolled oats",
    calories: 0,
    proteinG: 0,
    carbsG: 0,
    fatG: 0,
    servingSizeGrams: 80,
    ...overrides,
  };
}

test("scan truth table: zero macros, null micros, fillable rows flagged", () => {
  const gaps = gapReport(
    entry({
      calories: 0,
      proteinG: 0,
      carbsG: 12,
      fatG: 0,
      fiberG: null,
      sugarG: 6,
      sodiumMg: 320,
      constituents: [
        constituent({ name: "Rolled oats" }),
        constituent({ name: "Butter", calories: 100, proteinG: 0, carbsG: 0, fatG: 11 }),
        constituent({ name: "   " }),
      ],
    }),
  );
  assert.deepEqual(gaps.missingMacros, ["calories", "proteinG", "fatG"]);
  assert.ok(gaps.missingMicros.includes("fiberG"));
  assert.ok(!gaps.missingMicros.includes("sugarG"));
  assert.ok(!gaps.missingMicros.includes("sodiumMg"));
  assert.equal(gaps.zeroMacroConstituents.length, 1);
  assert.equal(gaps.zeroMacroConstituents[0].name, "Rolled oats");
  assert.equal(gaps.hasGaps, true);
  assert.equal(gaps.hasMacroGaps, true);
});

test("scan: fully filled entry has no gaps", () => {
  const gaps = gapReport(
    entry({
      calories: 300,
      proteinG: 10,
      carbsG: 40,
      fatG: 8,
      sugarG: 5,
      addedSugarG: 0,
      fiberG: 4,
      saturatedFatG: 1,
      monounsaturatedFatG: 2,
      polyunsaturatedFatG: 1,
      cholesterolMg: 0,
      sodiumMg: 100,
      potassiumMg: 200,
      transFatG: 0,
      calciumMg: 50,
      ironMg: 2,
      magnesiumMg: 30,
      zincMg: 1,
      vitaminAMcg: 10,
      vitaminCMg: 3,
      vitaminDMcg: 1,
      vitaminB12Mcg: 0.5,
      vitaminEMg: 1,
      vitaminKMcg: 5,
      folateMcg: 20,
      omega3G: 0.2,
      caffeineMg: 0,
    }),
  );
  assert.deepEqual(gaps.missingMacros, []);
  assert.deepEqual(gaps.missingMicros, []);
  assert.equal(gaps.hasGaps, false);
  assert.equal(gaps.hasMacroGaps, false);
});

test("scan: micro-only gaps → hasGaps true, hasMacroGaps false", () => {
  const gaps = gapReport(entry({ calories: 300, proteinG: 10, carbsG: 40, fatG: 8, fiberG: null }));
  assert.deepEqual(gaps.missingMacros, []);
  assert.deepEqual(gaps.missingMicros, ["fiberG"]);
  assert.equal(gaps.hasGaps, true);
  assert.equal(gaps.hasMacroGaps, false);
});

test("merge fills zero macros + null micros, keeps stored values", () => {
  const before = entry({ fatG: 12.5, fiberG: null, sodiumMg: 320 });
  const estimate = {
    calories: 540,
    proteinG: 42,
    carbsG: 60,
    fatG: 30,
    fiberG: 4.2,
    sodiumMg: 999,
    quantityG: null,
  };
  const merged = fillMissingFrom(before, estimate);
  assert.equal(merged.calories, 540);
  assert.equal(merged.proteinG, 42);
  assert.equal(merged.carbsG, 60);
  assert.equal(merged.fatG, 12.5);
  assert.equal(merged.fiberG, 4.2);
  assert.equal(merged.sodiumMg, 320);
});

test("merge: zero estimate answers stamp the fields without writing", () => {
  const before = entry({ calories: 0, proteinG: 0, fiberG: null });
  const merged = fillMissingFrom(before, {
    calories: 0,
    proteinG: 0,
    fiberG: null,
    quantityG: null,
  });
  // Nothing numerically written — zeros and nulls stay as they were.
  assert.equal(merged.calories, 0);
  assert.equal(merged.proteinG, 0);
  assert.equal(merged.fiberG, null);
  // But every asked field is now answered: the gaps close (the entry stops
  // re-appearing in scans) without fabricating values.
  assert.deepEqual(gapReport(merged).missingMacros, []);
  assert.ok(!gapReport(merged).missingMicros.includes("fiberG"));
  assert.ok(merged.gapAnsweredFields.includes("calories"));
  assert.ok(merged.gapAnsweredFields.includes("fiberG"));
});

test("merge: explicit zero answers close gaps (vodka soda)", () => {
  // #135: an entry that genuinely has no fat and no micros. The model
  // answers 0/null; the merge keeps the values but closes every gap, so
  // fill-missing completes after one run.
  const microsNull = Object.fromEntries(MICRO_FIELDS.map(([, modelKey]) => [modelKey, null]));
  const before = entry({ calories: 120, proteinG: 0, carbsG: 0, fatG: 0, ...microsNull });
  const merged = fillMissingFrom(before, { calories: 120, quantityG: null });

  assert.equal(merged.proteinG, 0);
  assert.equal(merged.carbsG, 0);
  assert.equal(merged.fatG, 0);
  const gaps = gapReport(merged);
  assert.deepEqual(gaps.missingMacros, []);
  assert.deepEqual(gaps.missingMicros, []);
  assert.equal(gaps.hasMacroGaps, false);
  assert.ok(merged.gapAnsweredFields.includes("fatG"));
  assert.ok(!merged.gapAnsweredFields.includes("calories")); // stored non-zero, never asked
  // Re-running the fill is now a no-op: nothing left to ask.
  assert.strictEqual(merged, fillMissingFrom(merged, { calories: 120, quantityG: null }));
});

test("scan: answered fields stop counting as gaps", () => {
  // #135: fields the fill-missing AI explicitly answered (zero macro, null
  // micro) leave the scan; the rest keep reporting.
  const microsNull = Object.fromEntries(MICRO_FIELDS.map(([, modelKey]) => [modelKey, null]));
  const gaps = gapReport(
    entry({
      calories: 2,
      proteinG: 0,
      carbsG: 0,
      fatG: 0,
      ...microsNull,
      gapAnsweredFields: ["proteinG", "fatG", "sodiumMg"],
    }),
  );
  // Calories are non-zero (never missing); proteinG/fatG answered; carbsG
  // unanswered → the only macro gap left, so the scan still lists it.
  assert.deepEqual(gaps.missingMacros, ["carbsG"]);
  assert.ok(!gaps.missingMicros.includes("sodiumMg"));
  assert.ok(gaps.missingMicros.includes("caffeineMg"));
  assert.equal(gaps.hasMacroGaps, true);
});

test("merge: grams factor scales written values, 1dp micros", () => {
  const before = entry({ quantityG: 200, fiberG: null });
  const merged = fillMissingFrom(
    before,
    { calories: 540, proteinG: 42, carbsG: 60.4, fatG: 30, fiberG: 4.22, quantityG: 100 },
  );
  assert.equal(merged.calories, 1080);
  assert.equal(merged.proteinG, 84);
  assert.equal(merged.carbsG, 120.8);
  assert.equal(merged.fatG, 60);
  assert.equal(merged.fiberG, 8.4);
});

test("merge: quantityG absent on either side → factor 1 identity", () => {
  const a = fillMissingFrom(entry({}), { calories: 100, proteinG: 5, quantityG: 300 });
  assert.equal(a.calories, 100);
  assert.equal(a.proteinG, 5);
  const b = fillMissingFrom(entry({ quantityG: 250 }), { calories: 100, proteinG: 5, quantityG: null });
  assert.equal(b.calories, 100);
  assert.equal(b.proteinG, 5);
});

test("merge: constituent rows fill by normalized name, unmatched dropped", () => {
  const before = entry({
    constituents: [
      constituent({ name: "Rolled Oats  " }),
      constituent({ name: "Skimmed milk", calories: 0, proteinG: 0, carbsG: 0, fatG: 0 }),
      constituent({ name: "Honey", calories: 60, proteinG: 0, carbsG: 17, fatG: 0 }),
    ],
  });
  const merged = fillMissingFrom(before, {
    calories: 0,
    proteinG: 0,
    carbsG: 0,
    fatG: 0,
    quantityG: null,
    constituents: [
      { name: "rolled oats", calories: 300, proteinG: 10, carbsG: 54, fatG: 6 },
      { name: "Blueberries", calories: 40, proteinG: 0.5, carbsG: 9, fatG: 0.2 },
      { name: "Skimmed milk", calories: 0, proteinG: 0, carbsG: 0, fatG: 0 },
    ],
  });
  assert.equal(merged.constituents.length, 3);
  const oats = merged.constituents[0];
  assert.equal(oats.calories, 300);
  assert.equal(oats.proteinG, 10);
  assert.equal(oats.carbsG, 54);
  assert.equal(oats.fatG, 6);
  assert.equal(oats.servingSizeGrams, 80);
  // All-zero candidate row: stored all-zero row stays all zero.
  assert.equal(merged.constituents[1].calories, 0);
  // Partially filled stored row untouched; unmatched estimate row not appended.
  assert.equal(merged.constituents[2].calories, 60);
  assert.equal(merged.constituents[2].carbsG, 17);
});

test("merge: nothing fillable returns the SAME object", () => {
  const before = entry({
    calories: 300,
    proteinG: 10,
    carbsG: 40,
    fatG: 8,
    sodiumMg: 320,
  });
  const merged = fillMissingFrom(before, {
    calories: 999,
    proteinG: 99,
    carbsG: 99,
    fatG: 99,
    sodiumMg: 1,
    quantityG: 100,
  });
  assert.strictEqual(merged, before);
});

test("merge: signature re-stamped after a real fill, untouched otherwise", () => {
  const before = entry({ calories: 0, proteinG: 0, carbsG: 0, fatG: 0 });
  const filled = fillMissingFrom(before, { calories: 300, proteinG: 10, carbsG: 40, fatG: 8, quantityG: null });
  assert.notEqual(filled, before);
  assert.equal(filled.microsCompositionSignature, null);

  const withRows = entry({
    calories: 0,
    proteinG: 0,
    carbsG: 0,
    fatG: 0,
    microsCompositionSignature: "stale",
    constituents: [constituent({ name: "Rolled oats" })],
  });
  const filledRows = fillMissingFrom(
    withRows,
    {
      calories: 300,
      proteinG: 10,
      carbsG: 40,
      fatG: 8,
      quantityG: null,
      constituents: [{ name: "Rolled oats", calories: 300, proteinG: 10, carbsG: 54, fatG: 6, servingSizeGrams: 80 }],
    },
  );
  assert.notEqual(filledRows.microsCompositionSignature, "stale");
  assert.ok(typeof filledRows.microsCompositionSignature === "string");
  assert.ok(filledRows.microsCompositionSignature.includes("rolled oats@1000"));
});

test("merge: containment fallback fills Mealie-style prefixed names", () => {
  const before = entry({
    constituents: [
      constituent({ name: "200 g flour", servingSizeGrams: 200 }),
      constituent({ name: "2 eggs", servingSizeGrams: 100 }),
    ],
  });
  const merged = fillMissingFrom(before, {
    calories: 0,
    proteinG: 0,
    carbsG: 0,
    fatG: 0,
    quantityG: null,
    constituents: [
      { name: "flour", calories: 720, proteinG: 26, carbsG: 152, fatG: 2 },
      { name: "eggs", calories: 140, proteinG: 12, carbsG: 1, fatG: 10 },
    ],
  });
  // Exact pass finds nothing (stored names carry grams prefixes); containment
  // pairs "200 g flour" ↔ "flour" and "2 eggs" ↔ "eggs".
  assert.equal(merged.constituents[0].calories, 720);
  assert.equal(merged.constituents[0].proteinG, 26);
  assert.equal(merged.constituents[0].carbsG, 152);
  assert.equal(merged.constituents[1].calories, 140);
  assert.equal(merged.constituents[1].proteinG, 12);
});

test("merge: containment needs a word boundary and a ≥3-char shorter side", () => {
  // "rice" sits inside "price" but not at a word boundary — no fill. (The
  // zero answers still stamp the entry-level macros, so compare rows, not
  // object identity.)
  const boundaryBlocked = entry({ constituents: [constituent({ name: "Rice" })] });
  const boundaryMerged = fillMissingFrom(boundaryBlocked, {
    calories: 0,
    proteinG: 0,
    carbsG: 0,
    fatG: 0,
    quantityG: null,
    constituents: [{ name: "Price", calories: 200, proteinG: 5, carbsG: 40, fatG: 1 }],
  });
  assert.deepEqual(boundaryMerged.constituents, boundaryBlocked.constituents);
  // Shorter side under 3 characters: boundary hit, still no fill.
  const shortBlocked = entry({ constituents: [constituent({ name: "Rye bread" })] });
  const shortMerged = fillMissingFrom(shortBlocked, {
    calories: 0,
    proteinG: 0,
    carbsG: 0,
    fatG: 0,
    quantityG: null,
    constituents: [{ name: "Ry", calories: 250, proteinG: 8, carbsG: 48, fatG: 2 }],
  });
  assert.deepEqual(shortMerged.constituents, shortBlocked.constituents);
});

test("scan: answered constituent names stop counting as gaps", () => {
  const both = gapReport(
    entry({
      calories: 100,
      proteinG: 1,
      carbsG: 2,
      fatG: 3,
      constituents: [constituent({ name: "Wodka" }), constituent({ name: "Soda" })],
      gapAnsweredConstituentNames: ["wodka", "soda"],
    }),
  );
  assert.equal(both.zeroMacroConstituents.length, 0);
  assert.equal(both.hasMacroGaps, false);

  const partial = gapReport(
    entry({
      constituents: [constituent({ name: "Wodka" }), constituent({ name: "Soda" })],
      gapAnsweredConstituentNames: ["wodka"],
    }),
  );
  assert.deepEqual(partial.zeroMacroConstituents.map((row) => row.name), ["Soda"]);
  assert.ok(partial.hasMacroGaps);
});

test("merge: constituents-only gap stamps unmatched rows (whiskey sour)", () => {
  // The re-offer fix: macros/micros answered, all-zero ingredient rows
  // matched nothing (the cocktail shape). One fill must close the scan.
  const before = entry({
    calories: 160,
    proteinG: 0.1,
    carbsG: 14.5,
    fatG: 12.5,
    constituents: [
      constituent({ name: "Whiskey", servingSizeGrams: 40 }),
      constituent({ name: "Zitronensaft", servingSizeGrams: 25 }),
    ],
  });
  assert.ok(gapReport(before).hasMacroGaps);

  const merged = fillMissingFrom(before, {});

  assert.deepEqual(merged.constituents, before.constituents);
  assert.deepEqual(merged.gapAnsweredConstituentNames, ["whiskey", "zitronensaft"]);
  assert.equal(gapReport(merged).zeroMacroConstituents.length, 0);
  assert.equal(gapReport(merged).hasMacroGaps, false);
  // Re-running the fill is now a no-op: nothing left to ask.
  assert.strictEqual(merged, fillMissingFrom(merged, {}));
});

test("merge: matched constituent row fills, unmatched row stamped", () => {
  const before = entry({
    constituents: [constituent({ name: "Rolled oats" }), constituent({ name: "Soda water" })],
  });
  const merged = fillMissingFrom(before, {
    constituents: [constituent({ name: "Rolled oats", calories: 380, proteinG: 13, carbsG: 58, fatG: 7 })],
  });

  assert.equal(merged.constituents[0].calories, 380);
  assert.equal(merged.constituents[1].calories, 0);
  assert.deepEqual(merged.gapAnsweredConstituentNames, ["soda water"]);
});

test("missingGroups: macro keys map to groups, MICROS iff any micro missing", () => {
  const report = gapReport(
    entry({
      calories: 0,
      proteinG: 0,
      carbsG: 12,
      fatG: 3,
      fiberG: null,
      gapAnsweredFields: ["carbsG"],
    }),
  );
  assert.deepEqual(missingGroups(report), ["CALORIES", "PROTEIN", "MICROS"]);

  const noGaps = gapReport(entry({ calories: 100, proteinG: 1, carbsG: 2, fatG: 3 }));
  assert.deepEqual(missingGroups(noGaps), []);
});

test("merge: constituentsAsked=false leaves unasked rows unstamped and scannable", () => {
  // WP6 per-nutrient focus: a macro-only run never asked for the ingredient
  // rows, so the one-attempt stamp must not fire — the row stays scannable
  // for a later full ask (Android FillMissingMergeTest twin).
  const before = entry({ constituents: [constituent({ name: "Rolled oats" })] });
  const merged = fillMissingFrom(before, { calories: 540, proteinG: 42, carbsG: 60 }, { constituentsAsked: false });

  assert.deepEqual(merged.gapAnsweredConstituentNames, []);
  assert.deepEqual(gapReport(merged).zeroMacroConstituents.map((row) => row.name), ["Rolled oats"]);
  // Asked fields still stamp as before.
  assert.ok(merged.gapAnsweredFields.includes("calories"));

  // The default (no options) keeps the full-ask stamp semantics.
  const mergedFull = fillMissingFrom(before, { calories: 540 });
  assert.deepEqual(mergedFull.gapAnsweredConstituentNames, ["rolled oats"]);
});
