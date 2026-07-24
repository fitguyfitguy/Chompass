// @ts-check
/**
 * Serializer/validator for the diary JSON export shape, byte-for-byte
 * compatible with android/app/src/main/java/app/chompass/export/
 * DiaryExporter.kt and DiaryImporter.kt.
 */

export const DIARY_FORMAT_VERSION = "1.1";

/** Micronutrient wire-key <-> model-field pairs, in ItemDto declaration order. */
const MICRO_FIELDS = [
  ["sugar_g", "sugarG"],
  ["added_sugar_g", "addedSugarG"],
  ["fiber_g", "fiberG"],
  ["saturated_fat_g", "saturatedFatG"],
  ["monounsaturated_fat_g", "monounsaturatedFatG"],
  ["polyunsaturated_fat_g", "polyunsaturatedFatG"],
  ["cholesterol_mg", "cholesterolMg"],
  ["sodium_mg", "sodiumMg"],
  ["potassium_mg", "potassiumMg"],
  ["trans_fat_g", "transFatG"],
  ["calcium_mg", "calciumMg"],
  ["iron_mg", "ironMg"],
  ["magnesium_mg", "magnesiumMg"],
  ["zinc_mg", "zincMg"],
  ["vitamin_a_mcg", "vitaminAMcg"],
  ["vitamin_c_mg", "vitaminCMg"],
  ["vitamin_d_mcg", "vitaminDMcg"],
  ["vitamin_b12_mcg", "vitaminB12Mcg"],
  ["vitamin_e_mg", "vitaminEMg"],
  ["vitamin_k_mcg", "vitaminKMcg"],
  ["folate_mcg", "folateMcg"],
  ["omega3_g", "omega3G"],
];

const SOURCE_TO_WIRE = {
  manual: "manually_edited",
  grounded: "grounded",
  barcode: "barcode",
  ai_estimated: "ai_estimated",
};

/** Import: anything unrecognized falls back to text-input equivalent, matching DiaryImporter.parseSource. */
const WIRE_TO_SOURCE = {
  manually_edited: "manual",
  barcode: "barcode",
  grounded: "grounded",
};

function round1(n) {
  if (n == null) return null;
  return Math.round(n * 10) / 10;
}

/** @param {import('./models.js').Grounding} g */
function groundingToWire(g) {
  if (!g) return null;
  return {
    source_kind: g.sourceKind,
    source_id: g.sourceId ?? null,
    source_name: g.sourceName ?? null,
    dataset_version: g.datasetVersion ?? null,
    identity_confirmed: !!g.identityConfirmed,
    portion_confirmed: !!g.portionConfirmed,
    user_corrected: !!g.userCorrected,
    identity_evidence: g.identityEvidence ?? null,
    portion_evidence: g.portionEvidence ?? null,
    validation_notes: g.validationNotes ?? null,
    components: (g.components ?? null) && g.components.map((c) => ({
      name: c.name,
      grams: c.grams,
      source_kind: c.sourceKind,
      source_id: c.sourceId ?? null,
      source_name: c.sourceName ?? null,
      matched_by: c.matchedBy ?? null,
    })),
  };
}

/** @param {any} w */
function groundingFromWire(w) {
  if (!w) return null;
  return {
    sourceKind: w.source_kind,
    sourceId: w.source_id ?? null,
    sourceName: w.source_name ?? null,
    datasetVersion: w.dataset_version ?? null,
    identityConfirmed: !!w.identity_confirmed,
    portionConfirmed: !!w.portion_confirmed,
    userCorrected: !!w.user_corrected,
    identityEvidence: w.identity_evidence ?? null,
    portionEvidence: w.portion_evidence ?? null,
    validationNotes: w.validation_notes ?? null,
    components: (w.components ?? null) && w.components.map((c) => ({
      name: c.name,
      grams: c.grams,
      sourceKind: c.source_kind,
      sourceId: c.source_id ?? null,
      sourceName: c.source_name ?? null,
      matchedBy: c.matched_by ?? null,
    })),
  };
}

/** @param {import('./models.js').FoodEntry} item */
function itemToWire(item) {
  const wire = {
    name: item.name,
    quantity_g: item.quantityG ?? null,
    calories: Math.round(item.calories),
    protein_g: round1(item.proteinG),
    carbs_g: round1(item.carbsG),
    fat_g: round1(item.fatG),
  };
  for (const [wireKey, modelKey] of MICRO_FIELDS) {
    wire[wireKey] = round1(item[modelKey] ?? null);
  }
  wire.time = item.time;
  wire.source = SOURCE_TO_WIRE[item.source] ?? "ai_estimated";
  wire.note = item.note ?? null;
  wire.grounding = groundingToWire(item.grounding ?? null);
  return wire;
}

/**
 * @param {any} wire
 * @param {string} date
 * @param {string} mealType
 * @returns {import('./models.js').FoodEntry}
 */
function itemFromWire(wire, date, mealType, idGen) {
  const entry = {
    id: idGen(),
    name: wire.name,
    quantityG: wire.quantity_g ?? null,
    calories: wire.calories,
    proteinG: wire.protein_g,
    carbsG: wire.carbs_g,
    fatG: wire.fat_g,
    mealType,
    date,
    time: wire.time,
    source: WIRE_TO_SOURCE[wire.source] ?? "ai_estimated",
    note: wire.note ?? null,
    grounding: groundingFromWire(wire.grounding ?? null),
  };
  for (const [wireKey, modelKey] of MICRO_FIELDS) {
    entry[modelKey] = wire[wireKey] ?? null;
  }
  return entry;
}

const MEAL_TYPES = ["breakfast", "lunch", "dinner", "snack"];

/**
 * Serialize a day-grouped set of food entries + targets into the diary
 * export document shape.
 * @param {{entries: import('./models.js').FoodEntry[], targets: Record<string, {calories:number, proteinG:number, carbsG:number, fatG:number}>, dateRange: {start: string, end: string}}} input
 */
export function exportDiary({ entries, targets, dateRange }) {
  const byDate = new Map();
  for (const e of entries) {
    if (!byDate.has(e.date)) byDate.set(e.date, []);
    byDate.get(e.date).push(e);
  }

  const days = [...byDate.keys()].sort().map((date) => {
    const dayEntries = byDate.get(date);
    const totals = dayEntries.reduce(
      (acc, e) => {
        acc.calories += e.calories;
        acc.protein_g += e.proteinG;
        acc.carbs_g += e.carbsG;
        acc.fat_g += e.fatG;
        return acc;
      },
      { calories: 0, protein_g: 0, carbs_g: 0, fat_g: 0 }
    );
    totals.calories = Math.round(totals.calories);
    totals.protein_g = round1(totals.protein_g);
    totals.carbs_g = round1(totals.carbs_g);
    totals.fat_g = round1(totals.fat_g);

    const target = targets[date] ?? { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 };
    const targetWire = {
      calories: Math.round(target.calories),
      protein_g: round1(target.proteinG),
      carbs_g: round1(target.carbsG),
      fat_g: round1(target.fatG),
    };
    const remaining = {
      calories: targetWire.calories - totals.calories,
      protein_g: round1(targetWire.protein_g - totals.protein_g),
      carbs_g: round1(targetWire.carbs_g - totals.carbs_g),
      fat_g: round1(targetWire.fat_g - totals.fat_g),
    };

    const meals = MEAL_TYPES.filter((t) => dayEntries.some((e) => e.mealType === t)).map((type) => ({
      type,
      items: dayEntries.filter((e) => e.mealType === type).map(itemToWire),
    }));

    return { date, totals, targets: targetWire, remaining, meals };
  });

  return {
    export: { app: "Chompass", format_version: DIARY_FORMAT_VERSION, date_range: dateRange },
    days,
  };
}

/** Thrown when a document fails the app/format_version gate on import. */
export class UnsupportedFormatError extends Error {}

/**
 * Parse a diary export document into flat FoodEntry records.
 * @param {any} doc
 * @param {() => string} [idGen] defaults to crypto.randomUUID
 * @returns {import('./models.js').FoodEntry[]}
 */
export function importDiary(doc, idGen = () => crypto.randomUUID()) {
  const exp = doc?.export;
  if (!exp || typeof exp.app !== "string") throw new UnsupportedFormatError("missing export.app");
  const app = exp.app.trim().toLowerCase();
  if (app !== "chompass" && app !== "nofud" && app !== "fud ai") throw new UnsupportedFormatError(`unrecognized app "${exp.app}"`);
  if (exp.format_version !== DIARY_FORMAT_VERSION) {
    throw new UnsupportedFormatError(`unsupported format_version "${exp.format_version}"`);
  }

  /** @type {import('./models.js').FoodEntry[]} */
  const entries = [];
  for (const day of doc.days ?? []) {
    for (const meal of day.meals ?? []) {
      for (const item of meal.items ?? []) {
        entries.push(itemFromWire(item, day.date, meal.type, idGen));
      }
    }
  }
  return entries;
}
