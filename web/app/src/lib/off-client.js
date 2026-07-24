// @ts-check
// Open Food Facts product lookup for barcode scanning.
// Prefers per-serving nutriments when present, else scales per-100g by serving
// size — mirrors Android OpenFoodFactsService.

import { ensureServingUnits, normalizedOptions, heuristicOptions } from "./chompass-core/serving-units.js";

/**
 * @param {string} barcode
 * @returns {Promise<Record<string, unknown>|null>}
 */
export async function lookupBarcode(barcode) {
  const fields = "product_name,generic_name,brands,quantity,serving_size,serving_quantity,nutriments";
  const res = await fetch(
    `https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(barcode)}.json?fields=${fields}`
  );
  if (!res.ok) throw new Error(`Open Food Facts lookup failed (${res.status})`);
  const data = await res.json();
  if (data.status !== 1 || !data.product) return null;
  return mapProduct(data.product, barcode);
}

/**
 * @param {Record<string, any>} product
 * @param {string} barcode
 */
export function mapProduct(product, barcode) {
  const n = product.nutriments || {};
  const servingGrams = Math.max(
    flexibleNumber(product.serving_quantity) ??
      gramsFromServingSize(product.serving_size) ??
      100,
    1
  );
  const scale = servingGrams / 100;

  /** @param {string} key */
  function servingValue(key) {
    const serving = flexibleNumber(n[`${key}_serving`]);
    if (serving != null) return serving;
    const per100 = flexibleNumber(n[`${key}_100g`]);
    return per100 != null ? per100 * scale : null;
  }

  const calories =
    servingValue("energy-kcal") ??
    (servingValue("energy") != null ? servingValue("energy") * 0.23900573614 : null);
  const protein = servingValue("proteins");
  const carbs = servingValue("carbohydrates");
  const fat = servingValue("fat");

  if (calories == null && protein == null && carbs == null && fat == null) {
    return null;
  }

  /** @param {number|null|undefined} v */
  const round1 = (v) => (v == null || !Number.isFinite(v) ? null : Math.round(v * 10) / 10);
  /** @param {number|null|undefined} v */
  const milligrams = (v) => (v == null ? null : round1(v * 1000));
  /** @param {number|null|undefined} v */
  const micrograms = (v) => (v == null ? null : round1(v * 1e6));

  const name = productName(product, barcode);
  const quantityG = round1(servingGrams) ?? 100;
  /** Prefer a "serving" unit when OFF gave a real serving size; merge heuristics. */
  const servingOpt =
    servingGrams > 1
      ? [{ unit: "serving", gramsPerUnit: servingGrams, quantity: 1 }]
      : [];
  const units = ensureServingUnits({
    name,
    quantityG,
    servingUnitOptions: servingOpt,
    selectedServingUnit: servingGrams > 1 ? "serving" : null,
    selectedServingQuantity: servingGrams > 1 ? 1 : null,
  });
  // Merge OFF serving with name heuristics (slice/ml/etc.)
  units.servingUnitOptions = normalizedOptions(
    [...servingOpt, ...heuristicOptions(name, quantityG), ...units.servingUnitOptions],
    quantityG
  );
  if (servingGrams > 1) {
    units.selectedServingUnit = "serving";
    units.selectedServingQuantity = 1;
  }

  return {
    name,
    quantityG,
    servingUnitOptions: units.servingUnitOptions,
    selectedServingUnit: units.selectedServingUnit,
    selectedServingQuantity: units.selectedServingQuantity,
    calories: Math.round(calories ?? 0),
    proteinG: round1(protein) ?? 0,
    carbsG: round1(carbs) ?? 0,
    fatG: round1(fat) ?? 0,
    sugarG: round1(servingValue("sugars")),
    addedSugarG: round1(servingValue("added-sugars")),
    fiberG: round1(servingValue("fiber")),
    saturatedFatG: round1(servingValue("saturated-fat")),
    monounsaturatedFatG: round1(servingValue("monounsaturated-fat")),
    polyunsaturatedFatG: round1(servingValue("polyunsaturated-fat")),
    cholesterolMg: milligrams(servingValue("cholesterol")),
    sodiumMg: milligrams(servingValue("sodium")),
    potassiumMg: milligrams(servingValue("potassium")),
    transFatG: round1(servingValue("trans-fat")),
    calciumMg: milligrams(servingValue("calcium")),
    ironMg: milligrams(servingValue("iron")),
    magnesiumMg: milligrams(servingValue("magnesium")),
    zincMg: milligrams(servingValue("zinc")),
    vitaminAMcg: micrograms(servingValue("vitamin-a")),
    vitaminCMg: milligrams(servingValue("vitamin-c")),
    vitaminDMcg: micrograms(servingValue("vitamin-d")),
    vitaminB12Mcg: micrograms(servingValue("vitamin-b12")),
    vitaminEMg: milligrams(servingValue("vitamin-e")),
    vitaminKMcg: micrograms(servingValue("vitamin-k")),
    folateMcg: micrograms(servingValue("folates")),
    omega3G: round1(servingValue("omega-3-fat")),
    note: `Open Food Facts · barcode ${barcode} · values for ${Math.round(servingGrams)}g serving — adjust if needed`,
    source: "barcode",
  };
}

/** @param {unknown} v */
function flexibleNumber(v) {
  if (v == null || v === "") return null;
  const n = typeof v === "number" ? v : Number(String(v).replace(",", "."));
  return Number.isFinite(n) ? n : null;
}

/** @param {string|undefined|null} size */
function gramsFromServingSize(size) {
  if (!size) return null;
  const m = String(size).match(/([\d.,]+)\s*g\b/i);
  return m ? flexibleNumber(m[1]) : null;
}

/**
 * @param {Record<string, any>} product
 * @param {string} barcode
 */
function productName(product, barcode) {
  const primary = firstNonEmpty(product.product_name, product.generic_name);
  const brand = String(product.brands || "")
    .split(",")
    .map((s) => s.trim())
    .find(Boolean);
  if (primary && brand && !primary.toLowerCase().includes(brand.toLowerCase())) {
    return `${brand} ${primary}`;
  }
  return primary || brand || `Barcode ${barcode}`;
}

/** @param {...(string|undefined|null)} parts */
function firstNonEmpty(...parts) {
  for (const p of parts) {
    const s = String(p || "").trim();
    if (s) return s;
  }
  return null;
}
