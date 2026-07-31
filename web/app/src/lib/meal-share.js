// @ts-check
/**
 * Meal share encode/decode — Android MealShare.kt compatible payload.
 * Web bridge uses hash route `#/add-meal?d=…` (custom scheme is native-only).
 * Wire is camelCase (v1 convention). New encodes use v: 2; decode accepts 1 and 2.
 */
import { ALL_MICRO_KEYS } from "./home-nutrients.js";

export const MEAL_SHARE_VERSION = 2;
export const MEAL_SHARE_IMPORT_VERSIONS = new Set([1, 2]);

/** Share wire key → FoodEntry field */
const SHARE_TO_ENTRY = {
  sugar: "sugarG",
  addedSugar: "addedSugarG",
  fiber: "fiberG",
  saturatedFat: "saturatedFatG",
  monounsaturatedFat: "monounsaturatedFatG",
  polyunsaturatedFat: "polyunsaturatedFatG",
  cholesterol: "cholesterolMg",
  sodium: "sodiumMg",
  potassium: "potassiumMg",
  transFat: "transFatG",
  calcium: "calciumMg",
  iron: "ironMg",
  magnesium: "magnesiumMg",
  zinc: "zincMg",
  vitaminA: "vitaminAMcg",
  vitaminC: "vitaminCMg",
  vitaminD: "vitaminDMcg",
  vitaminB12: "vitaminB12Mcg",
  vitaminE: "vitaminEMg",
  vitaminK: "vitaminKMcg",
  folate: "folateMcg",
  omega3: "omega3G",
};

const ENTRY_TO_SHARE = Object.fromEntries(Object.entries(SHARE_TO_ENTRY).map(([a, b]) => [b, a]));

/**
 * @param {import('./chompass-core/models.js').ServingUnitOption[]} options
 */
function servingUnitsToShare(options) {
  if (!options?.length) return undefined;
  return options.map((o) => {
    /** @type {Record<string, unknown>} */
    const row = { unit: o.unit, gramsPerUnit: o.gramsPerUnit };
    if (o.quantity != null) row.quantity = o.quantity;
    return row;
  });
}

/**
 * @param {any} arr
 * @returns {import('./chompass-core/models.js').ServingUnitOption[]}
 */
function servingUnitsFromShare(arr) {
  if (!Array.isArray(arr)) return [];
  /** @type {import('./chompass-core/models.js').ServingUnitOption[]} */
  const out = [];
  for (const o of arr) {
    const unit = String(o?.unit ?? "").trim();
    const grams = Number(o?.gramsPerUnit);
    if (!unit || !(grams > 0)) continue;
    out.push({
      unit,
      gramsPerUnit: grams,
      quantity: o?.quantity != null ? Number(o.quantity) : null,
    });
  }
  return out;
}

/**
 * @param {import('./chompass-core/models.js').FoodConstituent[]} rows
 */
function constituentsToShare(rows) {
  if (!rows?.length) return [];
  return rows.map((c) => {
    /** @type {Record<string, unknown>} */
    const d = {
      name: c.name,
      calories: c.calories,
      protein: c.proteinG,
      carbs: c.carbsG,
      fat: c.fatG,
      servingSizeGrams: c.servingSizeGrams,
    };
    if (c.emoji) d.emoji = c.emoji;
    const units = servingUnitsToShare(c.servingUnitOptions ?? []);
    if (units) d.servingUnitOptions = units;
    if (c.selectedServingUnit) d.selectedServingUnit = c.selectedServingUnit;
    if (c.selectedServingQuantity != null) d.selectedServingQuantity = c.selectedServingQuantity;
    return d;
  });
}

/**
 * @param {any} arr
 * @returns {import('./chompass-core/models.js').FoodConstituent[]}
 */
function constituentsFromShare(arr) {
  if (!Array.isArray(arr)) return [];
  /** @type {import('./chompass-core/models.js').FoodConstituent[]} */
  const out = [];
  for (const d of arr) {
    const name = String(d?.name ?? "").trim();
    if (!name) continue;
    out.push({
      name,
      calories: Math.round(Number(d.calories) || 0),
      proteinG: Number(d.protein) || 0,
      carbsG: Number(d.carbs) || 0,
      fatG: Number(d.fat) || 0,
      servingSizeGrams: Number(d.servingSizeGrams) || 0,
      emoji: d.emoji ? String(d.emoji) : null,
      servingUnitOptions: servingUnitsFromShare(d.servingUnitOptions),
      selectedServingUnit: d.selectedServingUnit ? String(d.selectedServingUnit) : null,
      selectedServingQuantity: d.selectedServingQuantity != null ? Number(d.selectedServingQuantity) : null,
    });
  }
  return out;
}

/**
 * @param {Array<Partial<import('./chompass-core/models.js').FoodEntry>>} entries
 */
export function encodeMealShare(entries) {
  const meals = entries.map((e) => {
    /** @type {Record<string, unknown>} */
    const d = {
      name: e.name,
      calories: e.calories,
      protein: e.proteinG,
      carbs: e.carbsG,
      fat: e.fatG,
      mealType: e.mealType ?? "snack",
    };
    const put = (key, v) => {
      if (v != null) d[key] = v;
    };
    for (const key of ALL_MICRO_KEYS) {
      const shareKey = ENTRY_TO_SHARE[key];
      if (shareKey) put(shareKey, /** @type {Record<string, unknown>} */ (e)[key]);
    }
    put("servingSizeGrams", e.quantityG);
    const units = servingUnitsToShare(e.servingUnitOptions ?? []);
    if (units) d.servingUnitOptions = units;
    put("selectedServingUnit", e.selectedServingUnit);
    put("selectedServingQuantity", e.selectedServingQuantity);
    if (e.note) d.customNote = e.note;
    d.constituents = constituentsToShare(e.constituents ?? []);
    return d;
  });
  const payload = JSON.stringify({ v: MEAL_SHARE_VERSION, meals });
  const b64 = btoa(unescape(encodeURIComponent(payload)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  return `#/add-meal?d=${b64}`;
}

/** @param {string} linkOrHash */
export function decodeMealShare(linkOrHash) {
  let encoded = "";
  try {
    if (linkOrHash.includes("d=")) {
      const q = linkOrHash.includes("?") ? linkOrHash.split("?")[1] : linkOrHash;
      encoded = new URLSearchParams(q.replace(/^#\/add-meal\?/, "").replace(/^d=/, "d=")).get("d") || "";
      if (!encoded && /[?&]d=([^&]+)/.test(linkOrHash)) {
        encoded = RegExp.$1;
      }
    } else {
      encoded = linkOrHash.trim();
    }
  } catch {
    return null;
  }
  if (!encoded) return null;
  try {
    const padded = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(escape(atob(padded)));
    const doc = JSON.parse(json);
    const version = Number(doc.v ?? 1);
    if (!MEAL_SHARE_IMPORT_VERSIONS.has(version)) return null;
    const meals = Array.isArray(doc.meals) ? doc.meals : [];
    return meals
      .map((d) => {
        if (!d?.name || d.calories == null) return null;
        const mealType = ["breakfast", "lunch", "dinner", "snack"].includes(String(d.mealType).toLowerCase())
          ? String(d.mealType).toLowerCase()
          : "snack";
        /** @type {Record<string, unknown>} */
        const entry = {
          name: String(d.name),
          calories: Math.round(Number(d.calories) || 0),
          proteinG: Number(d.protein) || 0,
          carbsG: Number(d.carbs) || 0,
          fatG: Number(d.fat) || 0,
          quantityG: d.servingSizeGrams != null ? Number(d.servingSizeGrams) : null,
          mealType,
          note: d.customNote ? String(d.customNote) : null,
          source: "manual",
          servingUnitOptions: servingUnitsFromShare(d.servingUnitOptions),
          selectedServingUnit: d.selectedServingUnit ? String(d.selectedServingUnit) : null,
          selectedServingQuantity: d.selectedServingQuantity != null ? Number(d.selectedServingQuantity) : null,
          constituents: constituentsFromShare(d.constituents),
        };
        for (const [shareKey, entryKey] of Object.entries(SHARE_TO_ENTRY)) {
          entry[entryKey] = d[shareKey] != null ? Number(d[shareKey]) : null;
        }
        return entry;
      })
      .filter(Boolean);
  } catch {
    return null;
  }
}

/**
 * @param {Array<Partial<import('./chompass-core/models.js').FoodEntry>>} entries
 */
export function mealShareText(entries) {
  const lines = entries.map((e) => {
    const macros = `${Math.round(e.proteinG || 0)}P · ${Math.round(e.carbsG || 0)}C · ${Math.round(e.fatG || 0)}F`;
    return `${e.name}: ${Math.round(e.calories || 0)} kcal · ${macros}`;
  });
  const origin = typeof location !== "undefined" ? `${location.origin}${location.pathname}` : "";
  const hash = encodeMealShare(entries);
  lines.push("", "Open in Chompass to add:", `${origin}${hash}`);
  return lines.join("\n");
}
