// @ts-check
/**
 * Serializer/validator for the sync-1.0 JSON document.
 * Compatible with android/.../export/SyncDocument.kt.
 */

export const SYNC_FORMAT_VERSION = "1.0";
export const SYNC_KIND = "sync";

/** @param {import('./models.js').Grounding|null|undefined} g */
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

const WIRE_TO_SOURCE = {
  manually_edited: "manual",
  barcode: "barcode",
  grounded: "grounded",
  ai_estimated: "ai_estimated",
};

const MEASUREMENT_FIELDS = [
  ["neck_cm", "neckCm"],
  ["waist_cm", "waistCm"],
  ["hips_cm", "hipsCm"],
  ["chest_cm", "chestCm"],
  ["upper_arm_cm", "upperArmCm"],
  ["thigh_cm", "thighCm"],
  ["calf_cm", "calfCm"],
  ["wrist_cm", "wristCm"],
];

function round1(n) {
  if (n == null) return null;
  return Math.round(n * 10) / 10;
}

function round2(n) {
  if (n == null) return null;
  return Math.round(n * 100) / 100;
}

/**
 * @param {import('./models.js').FoodEntry} item
 * @param {string} updatedAt
 * @param {string|null} [deletedAt]
 */
export function foodEntryToSyncWire(item, updatedAt, deletedAt = null) {
  /** @type {Record<string, any>} */
  const wire = {
    id: item.id,
    updated_at: updatedAt,
    deleted_at: deletedAt,
    name: item.name,
    date: item.date,
    time: item.time,
    meal_type: item.mealType,
    quantity_g: item.quantityG ?? null,
    calories: Math.round(item.calories),
    protein_g: round1(item.proteinG),
    carbs_g: round1(item.carbsG),
    fat_g: round1(item.fatG),
    source: SOURCE_TO_WIRE[item.source] ?? "ai_estimated",
    note: item.note ?? null,
    recipe_log_id: item.recipeLogId ?? null,
    grounding: groundingToWire(item.grounding ?? null),
  };
  for (const [wireKey, modelKey] of MICRO_FIELDS) {
    wire[wireKey] = round1(item[modelKey] ?? null);
  }
  return wire;
}

/**
 * @param {any} wire
 * @returns {import('./models.js').FoodEntry}
 */
export function foodEntryFromSyncWire(wire) {
  /** @type {import('./models.js').FoodEntry} */
  const entry = {
    id: String(wire.id),
    name: String(wire.name ?? ""),
    quantityG: wire.quantity_g ?? null,
    calories: Number(wire.calories) || 0,
    proteinG: Number(wire.protein_g) || 0,
    carbsG: Number(wire.carbs_g) || 0,
    fatG: Number(wire.fat_g) || 0,
    mealType: wire.meal_type || "snack",
    date: String(wire.date ?? ""),
    time: String(wire.time ?? "12:00"),
    source: WIRE_TO_SOURCE[wire.source] ?? "ai_estimated",
    note: wire.note ?? null,
    recipeLogId: wire.recipe_log_id ?? null,
    grounding: groundingFromWire(wire.grounding ?? null),
  };
  for (const [wireKey, modelKey] of MICRO_FIELDS) {
    entry[modelKey] = wire[wireKey] ?? null;
  }
  return entry;
}

/**
 * @param {{
 *   foodEntries?: import('./models.js').FoodEntry[],
 *   favorites?: import('./models.js').FoodEntry[],
 *   weights?: import('./models.js').WeightEntry[],
 *   bodyFat?: import('./models.js').BodyFatEntry[],
 *   measurements?: import('./models.js').BodyMeasurement[],
 *   water?: import('./models.js').WaterEntry[],
 *   recipes?: import('./models.js').Recipe[],
 *   profile?: { updatedAt: string, deletedAt?: string|null, payload: object }|null,
 *   prefs?: { updatedAt: string, deletedAt?: string|null, payload: object }|null,
 *   revisions?: Record<string, { updatedAt: string, deletedAt?: string|null }>,
 *   generatedAt?: string,
 * }} input
 */
export function exportSyncDocument(input) {
  const revisions = input.revisions ?? {};
  const generatedAt = input.generatedAt ?? new Date().toISOString();

  /** @param {string} id @param {string} fallback */
  const metaFor = (id, fallback) => {
    const rev = revisions[id];
    return {
      updated_at: rev?.updatedAt ?? fallback,
      deleted_at: rev?.deletedAt ?? null,
    };
  };

  const foodWire = (/** @type {import('./models.js').FoodEntry} */ e) => {
    const meta = metaFor(e.id, `${e.date}T${e.time}:00Z`);
    return foodEntryToSyncWire(e, meta.updated_at, meta.deleted_at);
  };

  const tombstones = Object.entries(revisions)
    .filter(([, rev]) => rev.deletedAt)
    .map(([id, rev]) => ({ id, updated_at: rev.updatedAt, deleted_at: rev.deletedAt }));

  /** @type {any[]} */
  const foodEntries = (input.foodEntries ?? []).map(foodWire);
  for (const t of tombstones) {
    if (foodEntries.some((e) => e.id === t.id)) continue;
    // Unknown-kind tombstones are attached to food_entries only when the id
    // is not already represented; clients also emit typed tombstone rows.
  }

  return {
    export: {
      app: "Chompass",
      kind: SYNC_KIND,
      format_version: SYNC_FORMAT_VERSION,
      generated_at: generatedAt,
    },
    food_entries: foodEntries,
    favorites: (input.favorites ?? []).map(foodWire),
    weights: (input.weights ?? []).map((w) => {
      const meta = metaFor(w.id, w.date);
      return {
        id: w.id,
        updated_at: meta.updated_at,
        deleted_at: meta.deleted_at,
        date: w.date,
        weight_kg: round2(w.weightKg),
      };
    }),
    body_fat: (input.bodyFat ?? []).map((b) => {
      const meta = metaFor(b.id, b.date);
      return {
        id: b.id,
        updated_at: meta.updated_at,
        deleted_at: meta.deleted_at,
        date: b.date,
        body_fat_percent: round1(b.bodyFatPercent),
      };
    }),
    measurements: (input.measurements ?? []).map((m) => {
      const meta = metaFor(m.id, m.date);
      /** @type {Record<string, any>} */
      const wire = {
        id: m.id,
        updated_at: meta.updated_at,
        deleted_at: meta.deleted_at,
        date: m.date,
      };
      for (const [wireKey, modelKey] of MEASUREMENT_FIELDS) {
        wire[wireKey] = round1(m[modelKey] ?? null);
      }
      return wire;
    }),
    water: (input.water ?? []).map((w) => {
      const meta = metaFor(w.id, `${w.date}T00:00:00Z`);
      return {
        id: w.id,
        updated_at: meta.updated_at,
        deleted_at: meta.deleted_at,
        date: w.date,
        amount_ml: Math.round(w.amountMl),
      };
    }),
    recipes: (input.recipes ?? []).map((r) => {
      const meta = metaFor(r.id, r.createdAt);
      return {
        id: r.id,
        updated_at: meta.updated_at,
        deleted_at: meta.deleted_at,
        name: r.name,
        meal_type: r.mealType,
        created_at: r.createdAt,
        ingredients: (r.ingredients ?? []).map((ing) => ({
          id: ing.id,
          name: ing.name,
          base_calories: Math.round(ing.baseCalories),
          base_protein_g: round1(ing.baseProteinG),
          base_carbs_g: round1(ing.baseCarbsG),
          base_fat_g: round1(ing.baseFatG),
          quantity_scale: ing.quantityScale ?? 1,
          base_fiber_g: round1(ing.baseFiberG ?? null),
          base_sugar_g: round1(ing.baseSugarG ?? null),
          base_sodium_mg: round1(ing.baseSodiumMg ?? null),
          base_quantity_g: round1(ing.baseQuantityG ?? null),
        })),
      };
    }),
    profile: input.profile
      ? {
          updated_at: input.profile.updatedAt,
          deleted_at: input.profile.deletedAt ?? null,
          payload: input.profile.payload ?? {},
        }
      : null,
    prefs: input.prefs
      ? {
          updated_at: input.prefs.updatedAt,
          deleted_at: input.prefs.deletedAt ?? null,
          payload: input.prefs.payload ?? {},
        }
      : null,
  };
}

/**
 * Append typed tombstone stubs for ids present in revisions but missing from live lists.
 * Call after exportSyncDocument when revisions may contain deletes.
 * @param {any} doc
 * @param {Record<string, { updatedAt: string, deletedAt?: string|null, kind?: string }>} revisions
 */
export function appendTombstones(doc, revisions) {
  const buckets = {
    food: "food_entries",
    favorite: "favorites",
    weight: "weights",
    bodyfat: "body_fat",
    measure: "measurements",
    water: "water",
    recipe: "recipes",
  };
  for (const [id, rev] of Object.entries(revisions)) {
    if (!rev.deletedAt) continue;
    const key = buckets[/** @type {keyof typeof buckets} */ (rev.kind ?? "food")] ?? "food_entries";
    const list = doc[key] ?? (doc[key] = []);
    if (list.some((/** @type {any} */ r) => r.id === id)) {
      const existing = list.find((/** @type {any} */ r) => r.id === id);
      existing.updated_at = rev.updatedAt;
      existing.deleted_at = rev.deletedAt;
      continue;
    }
    list.push({ id, updated_at: rev.updatedAt, deleted_at: rev.deletedAt });
  }
  return doc;
}

export class UnsupportedSyncFormatError extends Error {}

/**
 * Validate and return a sync document (pass-through after gate checks).
 * @param {any} doc
 */
export function parseSyncDocument(doc) {
  if (!doc || typeof doc !== "object") {
    throw new UnsupportedSyncFormatError("Not a JSON object");
  }
  const exp = doc.export;
  if (!exp || typeof exp !== "object") {
    throw new UnsupportedSyncFormatError("Missing export header");
  }
  const app = String(exp.app ?? "").trim().toLowerCase();
  if (app !== "chompass" && app !== "nofud" && app !== "fud ai") {
    throw new UnsupportedSyncFormatError(`Unsupported app: ${exp.app}`);
  }
  if (exp.kind !== SYNC_KIND) {
    throw new UnsupportedSyncFormatError(`Unsupported kind: ${exp.kind}`);
  }
  if (exp.format_version !== SYNC_FORMAT_VERSION) {
    throw new UnsupportedSyncFormatError(`Unsupported format_version: ${exp.format_version}`);
  }
  for (const key of [
    "food_entries",
    "favorites",
    "weights",
    "body_fat",
    "measurements",
    "water",
    "recipes",
  ]) {
    if (!Array.isArray(doc[key])) {
      throw new UnsupportedSyncFormatError(`Missing array: ${key}`);
    }
  }
  return doc;
}

/**
 * Convert sync food wires (live only) to FoodEntry models.
 * @param {any[]} wires
 */
export function liveFoodEntriesFromSync(wires) {
  return wires
    .filter((w) => w && w.id && !w.deleted_at)
    .map((w) => foodEntryFromSyncWire(w));
}

/**
 * @param {any[]} wires
 * @returns {import('./models.js').WeightEntry[]}
 */
export function liveWeightsFromSync(wires) {
  return wires
    .filter((w) => w && w.id && !w.deleted_at)
    .map((w) => ({
      id: String(w.id),
      date: String(w.date),
      weightKg: Number(w.weight_kg) || 0,
    }));
}

/**
 * @param {any[]} wires
 * @returns {import('./models.js').BodyFatEntry[]}
 */
export function liveBodyFatFromSync(wires) {
  return wires
    .filter((w) => w && w.id && !w.deleted_at)
    .map((w) => ({
      id: String(w.id),
      date: String(w.date),
      bodyFatPercent: Number(w.body_fat_percent) || 0,
    }));
}

/**
 * @param {any[]} wires
 * @returns {import('./models.js').BodyMeasurement[]}
 */
export function liveMeasurementsFromSync(wires) {
  return wires
    .filter((w) => w && w.id && !w.deleted_at)
    .map((w) => {
      /** @type {import('./models.js').BodyMeasurement} */
      const m = { id: String(w.id), date: String(w.date) };
      for (const [wireKey, modelKey] of MEASUREMENT_FIELDS) {
        m[modelKey] = w[wireKey] ?? null;
      }
      return m;
    });
}

/**
 * @param {any[]} wires
 * @returns {import('./models.js').WaterEntry[]}
 */
export function liveWaterFromSync(wires) {
  return wires
    .filter((w) => w && w.id && !w.deleted_at)
    .map((w) => ({
      id: String(w.id),
      date: String(w.date).slice(0, 10),
      amountMl: Number(w.amount_ml) || 0,
    }));
}

/**
 * @param {any[]} wires
 * @returns {import('./models.js').Recipe[]}
 */
export function liveRecipesFromSync(wires) {
  return wires
    .filter((w) => w && w.id && !w.deleted_at)
    .map((w) => ({
      id: String(w.id),
      name: String(w.name ?? ""),
      mealType: w.meal_type || "snack",
      createdAt: String(w.created_at ?? w.updated_at ?? new Date().toISOString()),
      ingredients: (w.ingredients ?? []).map((/** @type {any} */ ing) => ({
        id: String(ing.id ?? crypto.randomUUID()),
        name: String(ing.name ?? ""),
        baseCalories: Number(ing.base_calories) || 0,
        baseProteinG: Number(ing.base_protein_g) || 0,
        baseCarbsG: Number(ing.base_carbs_g) || 0,
        baseFatG: Number(ing.base_fat_g) || 0,
        quantityScale: Number(ing.quantity_scale) || 1,
        baseFiberG: ing.base_fiber_g ?? null,
        baseSugarG: ing.base_sugar_g ?? null,
        baseSodiumMg: ing.base_sodium_mg ?? null,
        baseQuantityG: ing.base_quantity_g ?? null,
      })),
    }));
}
