// @ts-check
// Incremental JSON field extraction for streamed food-analysis responses.
// Only complete, type-valid top-level values are accepted (Android FoodPartialJsonAssembler parity).

/**
 * @typedef {Object} PartialFoodEstimate
 * @property {string|null} [name]
 * @property {number|null} [calories]
 * @property {number|null} [proteinG]
 * @property {number|null} [carbsG]
 * @property {number|null} [fatG]
 * @property {number|null} [quantityG]
 * @property {number|null} [fiberG]
 * @property {number} [micronutrientCount]
 * @property {boolean} [streaming]
 * @property {boolean} [hasAnyField]
 */

const MICRO_KEYS = [
  "fiberG",
  "sugarG",
  "addedSugarG",
  "saturatedFatG",
  "sodiumMg",
  "potassiumMg",
  "calciumMg",
  "ironMg",
  "vitaminCMg",
  "vitaminDMcg",
  "cholesterolMg",
  "omega3G",
];

export class FoodPartialJsonAssembler {
  constructor() {
    /** @type {string} */
    this.buffer = "";
    /** @type {PartialFoodEstimate|null} */
    this.lastEmitted = null;
  }

  reset() {
    this.buffer = "";
    this.lastEmitted = null;
  }

  /**
   * @param {string} chunk
   * @returns {PartialFoodEstimate|null}
   */
  push(chunk) {
    if (!chunk) return this.lastEmitted;
    this.buffer += chunk;
    const next = extractPartial(this.buffer);
    if (!next) return this.lastEmitted;
    if (partialEqual(next, this.lastEmitted)) return this.lastEmitted;
    this.lastEmitted = next;
    return next;
  }

  /** @returns {PartialFoodEstimate|null} */
  current() {
    return this.lastEmitted;
  }
}

/**
 * @param {PartialFoodEstimate|null} a
 * @param {PartialFoodEstimate|null} b
 */
function partialEqual(a, b) {
  if (a === b) return true;
  if (!a || !b) return false;
  return (
    a.name === b.name &&
    a.calories === b.calories &&
    a.proteinG === b.proteinG &&
    a.carbsG === b.carbsG &&
    a.fatG === b.fatG &&
    a.quantityG === b.quantityG &&
    a.fiberG === b.fiberG &&
    a.micronutrientCount === b.micronutrientCount
  );
}

/**
 * @param {string} text
 * @returns {PartialFoodEstimate|null}
 */
export function extractPartial(text) {
  const jsonSpan = extractJsonSpan(text);
  if (!jsonSpan || !jsonSpan.includes("{")) return null;

  const name = completeString(jsonSpan, "name");
  const calories = completeNumber(jsonSpan, "calories");
  const proteinG = completeNumber(jsonSpan, "proteinG");
  const carbsG = completeNumber(jsonSpan, "carbsG");
  const fatG = completeNumber(jsonSpan, "fatG");
  const quantityG = completeNumber(jsonSpan, "quantityG");
  const fiberG = completeNumber(jsonSpan, "fiberG");
  const micronutrientCount = MICRO_KEYS.filter((k) => completeNumber(jsonSpan, k) != null).length;

  /** @type {PartialFoodEstimate} */
  const partial = {
    name,
    calories: calories != null ? Math.max(0, Math.round(calories)) : null,
    proteinG: proteinG != null && proteinG >= 0 ? proteinG : null,
    carbsG: carbsG != null && carbsG >= 0 ? carbsG : null,
    fatG: fatG != null && fatG >= 0 ? fatG : null,
    quantityG: quantityG != null && quantityG > 0 ? quantityG : null,
    fiberG: fiberG != null && fiberG >= 0 ? fiberG : null,
    micronutrientCount,
    streaming: true,
  };
  partial.hasAnyField = Boolean(
    partial.name ||
      partial.calories != null ||
      partial.proteinG != null ||
      partial.carbsG != null ||
      partial.fatG != null ||
      partial.quantityG != null ||
      partial.fiberG != null ||
      (partial.micronutrientCount ?? 0) > 0
  );
  return partial.hasAnyField ? partial : null;
}

/**
 * @param {Record<string, any>} estimate
 * @param {boolean} [streaming]
 * @returns {PartialFoodEstimate}
 */
export function partialFromEstimate(estimate, streaming = false) {
  const micros = MICRO_KEYS.filter((k) => estimate[k] != null).length;
  return {
    name: estimate.name ? String(estimate.name) : null,
    calories: estimate.calories != null ? Number(estimate.calories) : null,
    proteinG: estimate.proteinG != null ? Number(estimate.proteinG) : null,
    carbsG: estimate.carbsG != null ? Number(estimate.carbsG) : null,
    fatG: estimate.fatG != null ? Number(estimate.fatG) : null,
    quantityG: estimate.quantityG != null ? Number(estimate.quantityG) : null,
    fiberG: estimate.fiberG != null ? Number(estimate.fiberG) : null,
    micronutrientCount: micros,
    streaming,
    hasAnyField: true,
  };
}

/** @param {string} text */
function extractJsonSpan(text) {
  let cleaned = text.trim();
  const fence = cleaned.indexOf("```");
  if (fence >= 0) {
    const after = cleaned.indexOf("\n", fence);
    cleaned = cleaned.slice(after >= 0 ? after + 1 : fence + 3);
    const close = cleaned.lastIndexOf("```");
    if (close >= 0) cleaned = cleaned.slice(0, close);
    cleaned = cleaned.trim();
  }
  const firstBrace = cleaned.indexOf("{");
  if (firstBrace < 0) return cleaned;
  let depth = 0;
  let inString = false;
  let escape = false;
  for (let i = firstBrace; i < cleaned.length; i++) {
    const ch = cleaned[i];
    if (escape) {
      escape = false;
      continue;
    }
    if (ch === "\\") {
      escape = true;
      continue;
    }
    if (ch === '"') {
      inString = !inString;
      continue;
    }
    if (inString) continue;
    if (ch === "{") depth++;
    else if (ch === "}") {
      depth--;
      if (depth === 0) return cleaned.slice(firstBrace, i + 1);
    }
  }
  return cleaned.slice(firstBrace);
}

/**
 * @param {string} json
 * @param {string} key
 */
function completeString(json, key) {
  const start = keyValueStart(json, key);
  if (start == null || start >= json.length || json[start] !== '"') return null;
  let i = start + 1;
  let escape = false;
  while (i < json.length) {
    const ch = json[i];
    if (escape) {
      escape = false;
      i++;
      continue;
    }
    if (ch === "\\") {
      escape = true;
      i++;
      continue;
    }
    if (ch === '"') return unescapeJson(json.slice(start + 1, i));
    i++;
  }
  return null;
}

/**
 * @param {string} json
 * @param {string} key
 */
function completeNumber(json, key) {
  const start = keyValueStart(json, key);
  if (start == null || start >= json.length) return null;
  const ch0 = json[start];
  if (ch0 !== "-" && ch0 !== "+" && !(ch0 >= "0" && ch0 <= "9")) return null;
  let i = start;
  if (json[i] === "-" || json[i] === "+") i++;
  let sawDigit = false;
  while (i < json.length && json[i] >= "0" && json[i] <= "9") {
    sawDigit = true;
    i++;
  }
  if (i < json.length && json[i] === ".") {
    i++;
    while (i < json.length && json[i] >= "0" && json[i] <= "9") {
      sawDigit = true;
      i++;
    }
  }
  if (!sawDigit) return null;
  if (i >= json.length) return null;
  const next = json[i];
  if (!",}] \t\r\n".includes(next)) return null;
  const n = Number(json.slice(start, i));
  return Number.isFinite(n) ? n : null;
}

/**
 * @param {string} json
 * @param {string} key
 */
function keyValueStart(json, key) {
  const needle = `"${key}"`;
  let searchFrom = 0;
  while (true) {
    const keyIdx = json.indexOf(needle, searchFrom);
    if (keyIdx < 0) return null;
    if (!isTopLevelKey(json, keyIdx)) {
      searchFrom = keyIdx + needle.length;
      continue;
    }
    let i = keyIdx + needle.length;
    while (i < json.length && /\s/.test(json[i])) i++;
    if (i >= json.length || json[i] !== ":") {
      searchFrom = keyIdx + needle.length;
      continue;
    }
    i++;
    while (i < json.length && /\s/.test(json[i])) i++;
    return i;
  }
}

/**
 * @param {string} json
 * @param {number} keyIdx
 */
function isTopLevelKey(json, keyIdx) {
  let depth = 0;
  let inString = false;
  let escape = false;
  for (let i = 0; i < keyIdx; i++) {
    const ch = json[i];
    if (escape) {
      escape = false;
      continue;
    }
    if (ch === "\\" && inString) {
      escape = true;
      continue;
    }
    if (ch === '"') {
      inString = !inString;
      continue;
    }
    if (inString) continue;
    if (ch === "{" || ch === "[") depth++;
    else if (ch === "}" || ch === "]") depth--;
  }
  return depth === 1;
}

/** @param {string} raw */
function unescapeJson(raw) {
  let out = "";
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (ch !== "\\" || i + 1 >= raw.length) {
      out += ch;
      continue;
    }
    const next = raw[++i];
    if (next === "n") out += "\n";
    else if (next === "t") out += "\t";
    else if (next === "r") out += "\r";
    else if (next === '"' || next === "\\" || next === "/") out += next;
    else out += next;
  }
  return out;
}
