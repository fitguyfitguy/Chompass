// @ts-check
/**
 * Normalizes raw decoded barcode / 2D matrix-code text into a code that can be
 * looked up on Open Food Facts (GTIN/EAN family), or null when the text is not
 * a product code (internal factory codes, brand URLs, junk). `null` means
 * "not a product code" — callers must not hit the network.
 *
 * Mirror of Android `BarcodeCodeNormalizer`; both sides are pinned to
 * `testdata/parity/barcode-codes.json`.
 *
 * Rules, first match wins:
 *  1. Trim; empty → null.
 *  2. Bare 8–14 digit strings with a valid mod-10 check digit (EAN-8, UPC-A,
 *     EAN-13, GTIN-14) pass through unchanged (current 1D behavior).
 *  3. GS1 AI (01) prefix — HRI `(01)…` (wasm default) or raw `01…` (Android
 *     `TextMode.PLAIN`) — followed by 14 digits → GTIN; a leading zero is
 *     dropped to get the 13-digit EAN-13, otherwise the 14 digits are kept
 *     (case-level GTIN; OFF will 404 honestly if not indexed).
 *  4. URL forms: GS1 Digital Link `/01/<14 digits>` (path segment, `?01=` or
 *     `?gtin=` query) and OFF-style `/product/<digits>` → same GTIN handling.
 *  5. Anything else → null.
 */

const BARE_DIGITS = /^\d{8,14}$/;
const GS1_PREFIX = /^(?:\(01\)|01)(\d{14})/;
const DIGITAL_LINK_PATH = /\/01\/(\d{14})/;
const DIGITAL_LINK_QUERY_AI = /[?&]01=(\d{14})/;
const DIGITAL_LINK_QUERY_GTIN = /[?&]gtin=(\d{8,14})/;
const OFF_PRODUCT_PATH = /\/product\/(\d{8,14})/;

/**
 * @param {string} raw
 * @returns {string | null}
 */
export function normalizeBarcodeCode(raw) {
  const text = String(raw).trim();
  if (!text) return null;

  if (BARE_DIGITS.test(text) && validCheckDigit(text)) return text;

  const gs1 = GS1_PREFIX.exec(text);
  if (gs1) return normalizeGtin(gs1[1]);

  for (const re of [DIGITAL_LINK_PATH, DIGITAL_LINK_QUERY_AI, DIGITAL_LINK_QUERY_GTIN, OFF_PRODUCT_PATH]) {
    const m = re.exec(text);
    if (m) return normalizeGtin(m[1]);
  }
  return null;
}

/** Check-digit-validates the extracted digits, then applies GTIN → EAN-13 leading-zero drop. */
function normalizeGtin(digits) {
  if (!validCheckDigit(digits)) return null;
  return digits.length === 14 && digits.startsWith("0") ? digits.slice(1) : digits;
}

/**
 * GS1 mod-10 check digit (weights 3/1 from the right; same algorithm for
 * EAN-8 / UPC-A / EAN-13 / GTIN-14). Guards against junk digit strings
 * pulled out of URLs.
 */
function validCheckDigit(digits) {
  if (digits.length < 2) return false;
  let sum = 0;
  let weight = 3;
  for (let i = digits.length - 2; i >= 0; i--) {
    sum += Number(digits[i]) * weight;
    weight = weight === 3 ? 1 : 3;
  }
  return (10 - (sum % 10)) % 10 === Number(digits[digits.length - 1]);
}
