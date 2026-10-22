// @ts-check
/**
 * Open Food Facts barcode hits → structured AI prompt context (soft hint).
 * Wording mirrors Android OffPromptContext.
 */
import { detectBarcodesFromBlobs } from "../barcode-detect.js";
import { lookupBarcode } from "../off-client.js";

const LOOKUP_TIMEOUT_MS = 8000;
const MAX_IMAGES = 10;

const INSTRUCTIONS = `Use these values as authoritative package label data for identity and nutrient density.
Scale to the portion actually visible in the photo (may differ from one serving).
If other foods are visible that are not covered by these products, estimate them separately and include them in the total.
Do not invent conflicting macros for the matched packaged item when this data is present.`;

/**
 * @typedef {{
 *   barcode: string,
 *   name: string,
 *   servingGrams: number,
 *   calories: number,
 *   proteinG: number,
 *   carbsG: number,
 *   fatG: number,
 *   sugarG?: number | null,
 *   fiberG?: number | null,
 *   sodiumMg?: number | null,
 * }} OffProductHit
 */

/**
 * @param {OffProductHit[]} hits
 * @returns {string}
 */
export function formatOffPromptContext(hits) {
  if (!hits?.length) return "";
  const header =
    hits.length === 1
      ? "Open Food Facts match detected in the attached photo(s):"
      : "Open Food Facts matches detected in the attached photo(s):";
  const blocks = hits
    .map((hit) => {
      const lines = [
        `- barcode: ${hit.barcode}`,
        `  name: ${hit.name}`,
        `  nutrition for one labeled serving (${formatGrams(hit.servingGrams)}): ` +
          `${hit.calories} kcal, P ${formatMacro(hit.proteinG)} g, ` +
          `C ${formatMacro(hit.carbsG)} g, F ${formatMacro(hit.fatG)} g`,
      ];
      const micros = [];
      if (hit.sugarG != null) micros.push(`sugar ${formatMacro(hit.sugarG)} g`);
      if (hit.fiberG != null) micros.push(`fiber ${formatMacro(hit.fiberG)} g`);
      if (hit.sodiumMg != null) micros.push(`sodium ${formatMacro(hit.sodiumMg)} mg`);
      if (micros.length) lines.push(`  also: ${micros.join(", ")}`);
      const per100 = per100Line(hit);
      if (per100) lines.push(`  per 100 g (derived from labeled serving): ${per100}`);
      return lines.join("\n");
    })
    .join("\n");
  return `${header}\n${blocks}\n\n${INSTRUCTIONS}`;
}

/**
 * Decode barcodes from original image files, look up OFF, return prompt block or "".
 * Fail-soft: never throws.
 * @param {Blob[]} files
 * @returns {Promise<string>}
 */
export async function collectOffPromptContext(files) {
  try {
    if (!files?.length) return "";
    const codes = await detectBarcodesFromBlobs(files, MAX_IMAGES);
    if (!codes.length) return "";
    const hits = await withTimeout(
      Promise.all(
        codes.map(async (barcode) => {
          try {
            const product = await lookupBarcode(barcode);
            if (!product) return null;
            return productToHit(product, barcode);
          } catch {
            return null;
          }
        })
      ),
      LOOKUP_TIMEOUT_MS
    );
    const found = (hits || []).filter(Boolean);
    return formatOffPromptContext(/** @type {OffProductHit[]} */ (found));
  } catch {
    return "";
  }
}

/**
 * @param {Record<string, any>} product mapProduct result
 * @param {string} barcode
 * @returns {OffProductHit}
 */
function productToHit(product, barcode) {
  return {
    barcode,
    name: String(product.name || `Barcode ${barcode}`),
    servingGrams: Number(product.quantityG) || 100,
    calories: Math.round(Number(product.calories) || 0),
    proteinG: Number(product.proteinG) || 0,
    carbsG: Number(product.carbsG) || 0,
    fatG: Number(product.fatG) || 0,
    sugarG: product.sugarG ?? null,
    fiberG: product.fiberG ?? null,
    sodiumMg: product.sodiumMg ?? null,
  };
}

/**
 * @param {OffProductHit} hit
 * @returns {string | null}
 */
function per100Line(hit) {
  const g = hit.servingGrams;
  if (!(g > 0)) return null;
  const scale = 100 / g;
  return `${Math.round(hit.calories * scale)} kcal, P ${formatMacro(hit.proteinG * scale)} g, C ${formatMacro(hit.carbsG * scale)} g, F ${formatMacro(hit.fatG * scale)} g`;
}

/** @param {number} g */
function formatGrams(g) {
  return Number.isInteger(g) ? `${g}g` : `${g.toFixed(1)}g`;
}

/** @param {number} v */
function formatMacro(v) {
  return (Math.round(v * 10) / 10).toFixed(1);
}

/**
 * @template T
 * @param {Promise<T>} promise
 * @param {number} ms
 * @returns {Promise<T | null>}
 */
function withTimeout(promise, ms) {
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(null), ms);
    promise
      .then((v) => {
        clearTimeout(timer);
        resolve(v);
      })
      .catch(() => {
        clearTimeout(timer);
        resolve(null);
      });
  });
}
