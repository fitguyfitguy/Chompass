// @ts-check
// Open Food Facts product lookup for barcode scanning. Returns values
// per-100g (OFF's default basis) as an entry-form prefill — the user always
// reviews/adjusts for their actual portion before saving (see entry-form.js).

/**
 * @param {string} barcode
 * @returns {Promise<{name: string, quantityG: number, calories: number, proteinG: number, carbsG: number, fatG: number, note: string}|null>}
 */
export async function lookupBarcode(barcode) {
  const res = await fetch(`https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(barcode)}.json`);
  if (!res.ok) throw new Error(`Open Food Facts lookup failed (${res.status})`);
  const data = await res.json();
  if (data.status !== 1 || !data.product) return null;

  const n = data.product.nutriments || {};
  return {
    name: data.product.product_name || data.product.generic_name || `Barcode ${barcode}`,
    quantityG: 100,
    calories: round1(n["energy-kcal_100g"] ?? 0),
    proteinG: round1(n.proteins_100g ?? 0),
    carbsG: round1(n.carbohydrates_100g ?? 0),
    fatG: round1(n.fat_100g ?? 0),
    note: `Open Food Facts · barcode ${barcode} · values per 100g, adjust grams for your actual portion`,
  };
}

function round1(x) {
  return Math.round(Number(x) * 10) / 10;
}
