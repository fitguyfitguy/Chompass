// @ts-check
/**
 * Serving unit options + zero-network heuristics — port of Android
 * ServingUnitOption.kt and ServingUnitHeuristics.kt for the PWA entry form.
 */

import { ALL_MICRO_KEYS } from "../home-nutrients.js";

/**
 * @typedef {Object} ServingUnitOption
 * @property {string} unit
 * @property {number} gramsPerUnit
 * @property {number|null} [quantity]
 */

/**
 * @typedef {Object} ServingUnitHeuristicRule
 * @property {string} id
 * @property {string[]} keywords
 * @property {string} unit
 * @property {number} defaultGramsPerUnit
 * @property {string} label
 */

/** @type {ServingUnitOption} */
export const GRAMS_OPTION = { unit: "g", gramsPerUnit: 1.0 };

const GRAM_UNITS = new Set(["g", "gram", "grams"]);

/** App-generated "serving" unit ids (OFF barcode lookup / AI fallback). */
const SERVING_UNITS = new Set(["serving", "servings"]);

/** @param {ServingUnitOption} option */
export function normalizedUnit(option) {
  return String(option.unit ?? "")
    .trim()
    .toLowerCase();
}

/** @param {ServingUnitOption} option */
export function optionId(option) {
  return normalizedUnit(option);
}

/** @param {ServingUnitOption} option */
export function isGramUnit(option) {
  return GRAM_UNITS.has(normalizedUnit(option));
}

/** @param {ServingUnitOption} option */
export function isValidOption(option) {
  return normalizedUnit(option).length > 0 && option.gramsPerUnit > 0;
}

/**
 * @param {ServingUnitOption} option
 * @param {number} totalGrams
 */
export function quantityFor(option, totalGrams) {
  if (option.quantity != null && option.quantity > 0) return option.quantity;
  return option.gramsPerUnit > 0 ? totalGrams / option.gramsPerUnit : totalGrams;
}

/** Canonical key for a known English culinary unit (and common aliases). */
export function culinaryUnitKey(optionOrId) {
  const id = typeof optionOrId === "string" ? optionOrId : normalizedUnit(optionOrId);
  if (id === "cup" || id === "cups" || id === "c") return "cup";
  if (id === "tbsp" || id === "tblsp" || id === "tablespoon" || id === "tablespoons") return "tbsp";
  if (id === "tsp" || id === "teaspoon" || id === "teaspoons") return "tsp";
  return null;
}

/**
 * @param {ServingUnitOption} option
 * @param {number|null|undefined} quantity
 * @param {string} [servingLabel] localized "serving" label (app-generated unit)
 * @param {string} [servingPluralLabel] localized plural form
 * @param {Record<string, string[]>} [culinaryLabels] cup/tbsp/tsp labels
 */
export function displayUnit(option, quantity, servingLabel, servingPluralLabel, culinaryLabels) {
  const id = normalizedUnit(option);
  const singular = quantity == null || Math.abs(quantity - 1.0) <= 0.0001;
  const culinaryKey = culinaryUnitKey(id);
  if (culinaryKey && culinaryLabels?.[culinaryKey]) {
    const [label, plural] = culinaryLabels[culinaryKey];
    return singular ? label : plural;
  }
  // App-generated "serving" option (OFF barcode / AI fallback): the raw unit
  // string is English, so the UI passes the localized label(s).
  if (SERVING_UNITS.has(id) && servingLabel) {
    return singular ? servingLabel : (servingPluralLabel || servingLabel);
  }
  if (singular) return option.unit;
  if (GRAM_UNITS.has(id) || id === "kg" || id === "mg" || id === "ml" || id === "l" || id === "oz" || id === "fl oz" || id === "tbsp" || id === "tsp") {
    return option.unit;
  }
  if (id === "piece") return "pieces";
  return option.unit.endsWith("s") ? option.unit : `${option.unit}s`;
}

/**
 * @param {ServingUnitOption[]} options
 * @param {number} totalGrams
 * @returns {ServingUnitOption[]}
 */
export function normalizedOptions(options, totalGrams) {
  /** @type {Set<string>} */
  const seen = new Set();
  /** @type {ServingUnitOption[]} */
  const normalized = [];
  for (const raw of options ?? []) {
    const option =
      raw.quantity == null && raw.gramsPerUnit > 0
        ? { ...raw, quantity: totalGrams / raw.gramsPerUnit }
        : { ...raw };
    if (!isValidOption(option) || isGramUnit(option)) continue;
    const id = optionId(option);
    if (seen.has(id)) continue;
    seen.add(id);
    normalized.push(option);
  }
  return normalized.slice(0, 4);
}

/**
 * @param {ServingUnitOption[]} options
 * @returns {ServingUnitOption[]}
 */
export function pickerOptions(options) {
  /** @type {Set<string>} */
  const seen = new Set([optionId(GRAMS_OPTION)]);
  const nonGram = (options ?? []).filter((option) => {
    if (!isValidOption(option) || isGramUnit(option)) return false;
    const id = optionId(option);
    if (seen.has(id)) return false;
    seen.add(id);
    return true;
  });
  return [GRAMS_OPTION, ...nonGram];
}

/**
 * @param {string} id
 * @param {ServingUnitOption[]} options
 */
export function optionMatching(id, options) {
  const needle = String(id ?? "")
    .trim()
    .toLowerCase();
  return pickerOptions(options).find((o) => optionId(o) === needle) ?? GRAMS_OPTION;
}

/**
 * @param {string|null|undefined} preferredUnit
 * @param {ServingUnitOption[]} options
 */
export function initialUnitId(preferredUnit, options) {
  const picker = pickerOptions(options);
  const preferredId = preferredUnit?.trim().toLowerCase();
  if (preferredId && picker.some((o) => optionId(o) === preferredId)) return preferredId;
  const first = options?.find((o) => isValidOption(o) && !isGramUnit(o));
  return first ? optionId(first) : optionId(GRAMS_OPTION);
}

/**
 * @param {number} totalGrams
 * @param {string} selectedUnitId
 * @param {number|null|undefined} selectedQuantity
 * @param {ServingUnitOption[]} options
 */
export function initialQuantityText(totalGrams, selectedUnitId, selectedQuantity, options) {
  const option = optionMatching(selectedUnitId, options);
  if (selectedQuantity != null && selectedQuantity > 0 && !isGramUnit(option)) {
    return formatQuantity(selectedQuantity);
  }
  const quantity = option.gramsPerUnit > 0 ? totalGrams / option.gramsPerUnit : totalGrams;
  return formatQuantity(quantity);
}

/** @param {number} value */
export function formatQuantity(value) {
  if (!Number.isFinite(value)) return "";
  if (value === Math.trunc(value)) return String(Math.trunc(value));
  const formatted = Math.abs(value) < 10 ? value.toFixed(2) : value.toFixed(1);
  return formatted.replace(/\.?0+$/, "");
}

/** @param {string} value */
export function parseQuantity(value) {
  const trimmed = String(value ?? "").trim();
  if (!trimmed) return null;
  const direct = Number(trimmed);
  if (Number.isFinite(direct)) return direct;
  if (trimmed.includes(",") && !trimmed.includes(".")) {
    const comma = Number(trimmed.replace(",", "."));
    if (Number.isFinite(comma)) return comma;
  }
  return null;
}

/**
 * Operator characters recognised inside quantity expressions (infix only).
 * Mirrors Android ServingUnitOption.EXPRESSION_OPERATORS.
 */
const EXPRESSION_OPERATORS = new Set(["+", "-", "−", "×", "÷", "*", "/"]);

/**
 * True when [value] is a plain-number input that also contains an infix
 * operator (e.g. "50×2", "200−30") — i.e. something applyQuantityInput
 * evaluates as an arithmetic expression rather than an absolute number.
 * A leading sign is a delta, not an expression.
 * @param {string} value
 */
export function isQuantityExpression(value) {
  const trimmed = String(value ?? "").trim();
  if (!trimmed) return false;
  const first = trimmed.charAt(0);
  if (first === "+" || first === "-" || first === "−") return false;
  return [...trimmed].some((ch) => EXPRESSION_OPERATORS.has(ch));
}

/**
 * Left-to-right arithmetic with `× ÷` binding tighter than `+ −`. Tokens
 * are locale-ish numbers (parseQuantity) and single-char operators.
 * Malformed chains, empty operands, and division by zero return null.
 * @param {string} value
 */
function evaluateExpression(value) {
  const tokens = [];
  let number = "";
  for (const ch of value) {
    if (EXPRESSION_OPERATORS.has(ch)) {
      if (number.length > 0) {
        const parsed = parseQuantity(number);
        if (parsed == null) return null;
        tokens.push(parsed);
        number = "";
      }
      tokens.push(ch);
    } else {
      number += ch;
    }
  }
  if (number.length > 0) {
    const parsed = parseQuantity(number);
    if (parsed == null) return null;
    tokens.push(parsed);
  }
  if (tokens.length === 0 || typeof tokens[0] !== "number" || typeof tokens[tokens.length - 1] !== "number") return null;

  const values = [];
  const ops = [];
  for (const token of tokens) {
    if (typeof token === "number") values.push(token);
    else ops.push(token);
  }
  if (values.length !== ops.length + 1) return null;

  // Pass 1: × ÷ * / (left to right).
  let i = 0;
  while (i < ops.length) {
    const op = ops[i];
    if (op === "×" || op === "*" || op === "÷" || op === "/") {
      const right = values[i + 1];
      let result;
      if (op === "÷" || op === "/") {
        if (right === 0) return null;
        result = values[i] / right;
      } else {
        result = values[i] * right;
      }
      values[i] = result;
      values.splice(i + 1, 1);
      ops.splice(i, 1);
    } else {
      i++;
    }
  }

  // Pass 2: + - − (left to right).
  let acc = values[0];
  for (let j = 0; j < ops.length; j++) {
    const op = ops[j];
    const right = values[j + 1];
    acc = op === "-" || op === "−" ? acc - right : acc + right;
  }
  return acc;
}

/**
 * Parse a quantity-field input that may be a relative edit or a small
 * arithmetic expression — port of Android ServingUnitOption.applyDeltaInput:
 *
 *  - a leading `+` / `-` (ASCII or U+2212 minus) is a delta on [current];
 *  - a string containing an infix operator (`+ - × ÷ * /`) is an absolute
 *    expression ("50×2" → 100, "200−30" → 170);
 *  - anything else parses as a plain quantity.
 *
 * A lone sign, empty delta, malformed expression, or division by zero
 * returns null. Callers ignore non-positive results.
 * @param {string} value
 * @param {number|null|undefined} current
 */
export function applyQuantityInput(value, current) {
  const trimmed = String(value ?? "").trim();
  if (!trimmed) return null;
  const first = trimmed.charAt(0);
  if (first === "+" || first === "-" || first === "−") {
    const rest = trimmed.slice(1).trim();
    if (!rest) return null;
    const delta = parseQuantity(rest);
    if (delta == null) return null;
    const base = current != null ? current : 0;
    return first === "-" || first === "−" ? base - delta : base + delta;
  }
  if (isQuantityExpression(trimmed)) return evaluateExpression(trimmed);
  return parseQuantity(trimmed);
}

/** @type {ServingUnitHeuristicRule[]} */
export const HEURISTIC_RULES = [
  { id: "pizza", keywords: ["pizza"], unit: "slice", defaultGramsPerUnit: 120.0, label: "Pizza → slice" },
  { id: "cake", keywords: ["cake", "pie", "quiche", "tart"], unit: "slice", defaultGramsPerUnit: 90.0, label: "Cake / pie / tart → slice" },
  { id: "bread", keywords: ["bread", "toast"], unit: "slice", defaultGramsPerUnit: 30.0, label: "Bread / toast → slice" },
  { id: "cheese", keywords: ["cheese"], unit: "slice", defaultGramsPerUnit: 20.0, label: "Cheese → slice" },
  { id: "cookie", keywords: ["cookie", "biscuit", "cracker"], unit: "piece", defaultGramsPerUnit: 15.0, label: "Cookie / biscuit / cracker → piece" },
  { id: "nugget", keywords: ["nugget"], unit: "piece", defaultGramsPerUnit: 20.0, label: "Nugget → piece" },
  {
    id: "dumpling",
    keywords: ["dumpling", "gyoza", "samosa", "springroll", "spring roll"],
    unit: "piece",
    defaultGramsPerUnit: 25.0,
    label: "Dumpling / samosa / spring roll → piece",
  },
  { id: "donut", keywords: ["donut", "doughnut", "muffin"], unit: "piece", defaultGramsPerUnit: 70.0, label: "Donut / muffin → piece" },
  { id: "waffle", keywords: ["waffle", "pancake"], unit: "piece", defaultGramsPerUnit: 40.0, label: "Waffle / pancake → piece" },
  { id: "bagel", keywords: ["bagel"], unit: "piece", defaultGramsPerUnit: 90.0, label: "Bagel → piece" },
  { id: "egg", keywords: ["egg"], unit: "piece", defaultGramsPerUnit: 50.0, label: "Egg → piece" },
  { id: "apple", keywords: ["apple"], unit: "piece", defaultGramsPerUnit: 180.0, label: "Apple → piece" },
  { id: "banana", keywords: ["banana"], unit: "piece", defaultGramsPerUnit: 120.0, label: "Banana → piece" },
  { id: "orange", keywords: ["orange", "mandarin"], unit: "piece", defaultGramsPerUnit: 150.0, label: "Orange / mandarin → piece" },
  { id: "burger", keywords: ["burger", "hamburger", "cheeseburger"], unit: "piece", defaultGramsPerUnit: 200.0, label: "Burger → piece" },
  { id: "sandwich", keywords: ["sandwich", "sandwiches", "wrap"], unit: "piece", defaultGramsPerUnit: 220.0, label: "Sandwich / wrap → piece" },
  { id: "taco", keywords: ["taco"], unit: "piece", defaultGramsPerUnit: 90.0, label: "Taco → piece" },
  { id: "burrito", keywords: ["burrito"], unit: "piece", defaultGramsPerUnit: 280.0, label: "Burrito → piece" },
  { id: "hotdog", keywords: ["hotdog", "hot dog"], unit: "piece", defaultGramsPerUnit: 100.0, label: "Hot dog → piece" },
  { id: "icecream", keywords: ["ice cream"], unit: "scoop", defaultGramsPerUnit: 60.0, label: "Ice cream → scoop" },
  {
    id: "drinksMl",
    keywords: ["milk", "juice", "smoothie", "soup", "yogurt", "yoghurt", "broth", "coffee", "tea", "latte", "cappuccino"],
    unit: "ml",
    defaultGramsPerUnit: 1.03,
    label: "Milk / juice / soup / coffee / tea → ml",
  },
  {
    id: "spoonedTbsp",
    keywords: ["peanut butter", "honey", "chutney", "ghee", "jam", "syrup", "mayonnaise", "mustard"],
    unit: "tbsp",
    defaultGramsPerUnit: 15.0,
    label: "Peanut butter / honey / jam / sauces → tbsp",
  },
  { id: "can", keywords: ["soda", "cola", "beer"], unit: "can", defaultGramsPerUnit: 330.0, label: "Soda / cola / beer → can" },
  { id: "wine", keywords: ["wine"], unit: "glass", defaultGramsPerUnit: 150.0, label: "Wine → glass" },
  {
    id: "bar",
    keywords: ["candy bar", "chocolate bar", "protein bar", "granola bar"],
    unit: "bar",
    defaultGramsPerUnit: 50.0,
    label: "Candy / protein / granola bar → bar",
  },
];

/** @param {string} foodName */
export function matchingHeuristicRule(foodName) {
  const words = String(foodName ?? "")
    .toLowerCase()
    .split(/[^a-z]+/)
    .filter(Boolean);
  if (words.length === 0) return null;
  /** @type {Set<string>} */
  const wordForms = new Set(words);
  for (const word of words) {
    if (word.length > 3 && word.endsWith("s") && !word.endsWith("ss")) {
      wordForms.add(word.slice(0, -1));
    }
  }
  const normalized = words.join(" ");
  return (
    HEURISTIC_RULES.find((rule) =>
      rule.keywords.some((keyword) => (keyword.includes(" ") ? normalized.includes(keyword) : wordForms.has(keyword)))
    ) ?? null
  );
}

/**
 * @param {string} foodName
 * @param {number} totalGrams
 * @returns {ServingUnitOption[]}
 */
export function heuristicOptions(foodName, totalGrams) {
  const rule = matchingHeuristicRule(foodName);
  if (!rule) return [];
  const grams = totalGrams > 0 ? totalGrams : rule.defaultGramsPerUnit;
  return normalizedOptions(
    [{ unit: rule.unit, gramsPerUnit: rule.defaultGramsPerUnit, quantity: grams / rule.defaultGramsPerUnit }],
    grams
  );
}

/**
 * Ensure serving unit options based on inference mode (Android ServingUnitInferenceMode).
 * Default without opts keeps heuristic fill for callers that predate the mode pref.
 * @param {{ name?: string, quantityG?: number|null, servingUnitOptions?: ServingUnitOption[], selectedServingUnit?: string|null, selectedServingQuantity?: number|null }} entry
 * @param {{ preferHeuristicUnit?: boolean, inferenceMode?: "gramsOnly"|"heuristic"|"aiCall" }} [opts]
 */
export function ensureServingUnits(entry, opts = {}) {
  const mode = opts.inferenceMode || (opts.preferHeuristicUnit === false ? "gramsOnly" : "heuristic");
  const totalGrams = entry.quantityG != null && entry.quantityG > 0 ? entry.quantityG : 100;
  let options = normalizedOptions(entry.servingUnitOptions ?? [], totalGrams);
  if (options.length === 0 && entry.name && mode === "heuristic") {
    options = heuristicOptions(entry.name, totalGrams);
  }
  // aiCall: keep model-provided options only (no second AI call on PWA; no heuristic fill)
  const preferred =
    entry.selectedServingUnit ??
    (options[0] && mode !== "gramsOnly" ? optionId(options[0]) : optionId(GRAMS_OPTION));
  const selectedServingUnit = initialUnitId(preferred, options);
  const selectedServingQuantity =
    entry.selectedServingQuantity != null && entry.selectedServingQuantity > 0
      ? entry.selectedServingQuantity
      : parseQuantity(initialQuantityText(totalGrams, selectedServingUnit, null, options));
  return {
    servingUnitOptions: options,
    selectedServingUnit,
    selectedServingQuantity,
    quantityG: totalGrams,
  };
}

/**
 * Scale nutrition fields from a base snapshot.
 * @param {Record<string, unknown>} base
 * @param {number} scale
 */
export function scaleNutrition(base, scale) {
  const s = Number.isFinite(scale) && scale > 0 ? scale : 1;
  /** @type {Record<string, number|null>} */
  const out = {
    calories: Math.round(Number(base.calories ?? 0) * s),
    proteinG: Number(base.proteinG ?? 0) * s,
    carbsG: Number(base.carbsG ?? 0) * s,
    fatG: Number(base.fatG ?? 0) * s,
  };
  for (const key of ALL_MICRO_KEYS) {
    const v = base[key];
    out[key] = v == null || v === "" ? null : Number(v) * s;
  }
  return out;
}

/**
 * Format grams for the "~Ng" total row.
 * @param {number} grams
 */
export function formatGramsDisplay(grams) {
  if (!Number.isFinite(grams)) return "0";
  if (Math.abs(grams - Math.round(grams)) < 0.05) return String(Math.round(grams));
  return formatQuantity(grams);
}
