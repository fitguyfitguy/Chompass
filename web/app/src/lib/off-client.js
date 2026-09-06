// @ts-check
// Open Food Facts product lookup for barcode scanning.
// Prefers per-serving nutriments when present, else scales per-100g by serving
// size — mirrors Android OpenFoodFactsService.

import { ensureServingUnits, normalizedOptions, heuristicOptions } from "./chompass-core/serving-units.js";
import { normalizeBarcodeCode } from "./chompass-core/barcode-code.js";

/**
 * @param {string} barcode raw decoded text (may be a GS1 / QR / URL form)
 * @returns {Promise<Record<string, unknown>|null>} null when the text is not a
 *   product code or the product is not in OFF (callers already handle null).
 */
export async function lookupBarcode(barcode) {
  const code = normalizeBarcodeCode(barcode);
  if (!code) return null;
  const fields = "product_name,generic_name,brands,quantity,product_quantity,product_quantity_unit,serving_size,serving_quantity,nutriments,ingredients_text,allergens_tags,traces_tags,nutriscore_grade,nova_group,ecoscore_grade,labels_tags,categories_tags,image_front_url";
  const res = await fetch(
    `https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(code)}.json?fields=${fields}`
  );
  if (!res.ok) throw new Error(`Open Food Facts lookup failed (${res.status})`);
  const data = await res.json();
  if (data.status !== 1 || !data.product) return null;
  return mapProduct(data.product, code);
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
  const carbs = servingValue("carbohydrates") ?? servingValue("carbohydrates-total");
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
  // OFF package size (structured quantity beats the display string): offered
  // as a second unit only when it differs from the serving (mirrors Android).
  const pkgGrams = packageGrams(product);
  const packageOpt =
    pkgGrams != null && Math.abs(pkgGrams - servingGrams) > 0.01
      ? [{ unit: "package", gramsPerUnit: pkgGrams, quantity: 1 }]
      : [];
  const units = ensureServingUnits({
    name,
    quantityG,
    servingUnitOptions: servingOpt,
    selectedServingUnit: servingGrams > 1 ? "serving" : null,
    selectedServingQuantity: servingGrams > 1 ? 1 : null,
  });
  // Merge OFF serving + package with name heuristics (slice/ml/etc.)
  units.servingUnitOptions = normalizedOptions(
    [...servingOpt, ...packageOpt, ...heuristicOptions(name, quantityG), ...units.servingUnitOptions],
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
    caffeineMg: round1(servingValue("caffeine")),
    note: `Open Food Facts · barcode ${barcode} · values for ${Math.round(servingGrams)}g serving; adjust if needed`,
    productMetadata: buildProductMetadata(product, barcode),
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

/**
 * Display-only product enrichment (mirrors Android FoodProductMetadata):
 * package size, scores, allergens, labels, ingredients and the front photo URL.
 * @param {Record<string, any>} product
 * @param {string} barcode
 */
function buildProductMetadata(product, barcode) {
  const meta = {
    barcode,
    packageQuantity: nonEmptyString(product.quantity),
    ingredientsText: nonEmptyString(product.ingredients_text),
    allergens: displayTags(product.allergens_tags, 16),
    traces: displayTags(product.traces_tags, 16),
    nutriScore: normalizedScore(product.nutriscore_grade),
    novaGroup: normalizedNovaGroup(product.nova_group),
    ecoScore: normalizedScore(product.ecoscore_grade),
    labels: displayTags(product.labels_tags, 12),
    categories: displayTags(product.categories_tags, 8),
    imageUrl: nonEmptyString(product.image_front_url),
  };
  meta.hasDisplayDetails =
    Boolean(barcode) ||
    meta.packageQuantity != null ||
    meta.ingredientsText != null ||
    meta.allergens.length > 0 ||
    meta.traces.length > 0 ||
    meta.nutriScore != null ||
    meta.novaGroup != null ||
    meta.ecoScore != null ||
    meta.labels.length > 0 ||
    meta.categories.length > 0;
  return meta;
}

/** OFF tag ids (`en:milk`) turned into display names, deduped + titlecased. */
/** @param {unknown} tags @param {number} limit */
function displayTags(tags, limit) {
  if (!Array.isArray(tags)) return [];
  const seen = new Set();
  const out = [];
  for (const raw of tags) {
    const cleaned = String(raw).replace(/^[a-z]{2}:/, "").replace(/[-_]/g, " ").trim();
    if (!cleaned) continue;
    const key = cleaned.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(cleaned.charAt(0).toUpperCase() + cleaned.slice(1));
    if (out.length >= limit) break;
  }
  return out;
}

/** Nutri-/Eco-Score grade letter; null for `unknown` / `not-applicable`. */
/** @param {unknown} v */
function normalizedScore(v) {
  const s = nonEmptyString(v);
  if (s == null) return null;
  const lower = s.toLowerCase();
  if (lower === "unknown" || lower === "not-applicable") return null;
  return s.toUpperCase();
}

/** @param {unknown} v */
function normalizedNovaGroup(v) {
  const n = flexibleNumber(v);
  if (n == null) return null;
  const i = Math.round(n);
  return i >= 1 && i <= 4 ? i : null;
}

/** Package size in grams: structured quantity, else a strict display-string parse. */
/** @param {Record<string, any>} product */
function packageGrams(product) {
  const structured = flexibleNumber(product.product_quantity);
  if (structured != null && structured > 0) {
    const unit = nonEmptyString(product.product_quantity_unit)?.toLowerCase() ?? null;
    if (unit === "kg") return structured * 1000;
    if (unit === "mg") return structured / 1000;
    if (unit === "g" || unit == null) return structured;
    if (unit === "l") return structured * 1000;
    if (unit === "ml") return structured;
  }
  const display = nonEmptyString(product.quantity);
  if (display == null) return null;
  const m = display.match(/^([0-9]+(?:[.,][0-9]+)?)\s*(kg|mg|g|oz|ml|l)$/i);
  if (!m) return null;
  const value = flexibleNumber(m[1]);
  if (value == null) return null;
  switch (m[2].toLowerCase()) {
    case "kg":
      return value * 1000;
    case "mg":
      return value / 1000;
    case "oz":
      return value * 28.3495;
    case "ml":
      return value;
    case "l":
      return value * 1000;
    default:
      return value;
  }
}

/** @param {unknown} v */
function nonEmptyString(v) {
  if (v == null) return null;
  const s = String(v).trim();
  return s ? s : null;
}
