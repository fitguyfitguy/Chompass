// @ts-check
// Diary-oriented food analysis (photo or text) — separate from Coach chat.
// Always returns a proposal object for entry-form review; never writes.
import { PROVIDERS, resolveProviderModel } from "./providers.js";
import { prefs } from "../db.js";
import { loadProviderKey } from "./key-storage.js";
import { guessMealTypeFromPrefs } from "../meal-schedule.js";
import { ALL_MICRO_KEYS } from "../home-nutrients.js";
import { ensureServingUnits } from "../chompass-core/serving-units.js";
import {
  parseConstituentsFromPrediction,
  reconcileConstituents,
} from "../chompass-core/constituents.js";
import { FoodPartialJsonAssembler } from "./partial-json.js";
import { t } from "../i18n/index.js";
import {
  ANALYSIS_PHASE,
  ANALYSIS_PHASE_STEPS,
  isAbortError,
  phaseLabel,
} from "./analysis-phase.js";

// Re-exported for backwards compatibility (UI overlays import the constants
// from analysis-phase.js directly; tests and callers may still use these).
export { ANALYSIS_PHASE, ANALYSIS_PHASE_STEPS, isAbortError, phaseLabel };

const SYSTEM_BASE = `You estimate nutrition for a food diary app. Reply with ONLY a single JSON object (no markdown), using this shape:
{"name":"string","mealType":"breakfast"|"lunch"|"dinner"|"snack","calories":number,"proteinG":number,"carbsG":number,"fatG":number,"quantityG":number|null,"note":string|null,"fiberG":number|null,"sugarG":number|null,"addedSugarG":number|null,"saturatedFatG":number|null,"sodiumMg":number|null,"potassiumMg":number|null,"calciumMg":number|null,"ironMg":number|null,"vitaminCMg":number|null,"vitaminDMcg":number|null,"cholesterolMg":number|null,"omega3G":number|null}
Include micronutrients when you can estimate them confidently; use null when unsure. Prefer the meal type that fits the current local time if unclear. When multiple photos are provided, treat them as angles of the same meal and produce one estimate.`;

const SYSTEM_CONSTITUENTS = `You estimate nutrition for a food diary app. Reply with ONLY a single JSON object (no markdown), using this shape:
{"name":"string","mealType":"breakfast"|"lunch"|"dinner"|"snack","calories":number,"proteinG":number,"carbsG":number,"fatG":number,"quantityG":number|null,"note":string|null,"fiberG":number|null,"sugarG":number|null,"addedSugarG":number|null,"saturatedFatG":number|null,"sodiumMg":number|null,"potassiumMg":number|null,"calciumMg":number|null,"ironMg":number|null,"vitaminCMg":number|null,"vitaminDMcg":number|null,"cholesterolMg":number|null,"omega3G":number|null,"constituents":[{"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"emoji":"...","unit_options":[]}]}
Include micronutrients when you can estimate them confidently; use null when unsure. Prefer the meal type that fits the current local time if unclear. When multiple photos are provided, treat them as angles of the same meal and produce one estimate.
constituents is optional. For multi-item meals, list each distinct edible item (egg, toast, butter, drink, side) with its own macros, serving_size_grams, and unit_options when a non-gram unit is obvious. Keep top-level fields as the meal total. Constituent grams MUST sum to quantityG within ±5%. Constituent calories/protein/carbs/fat MUST each sum to the matching meal total within ±5%. Include every named or clearly implied edible item; do not invent extras. Use [] for a single undivided food. unit_options entries look like {"unit":"slice","quantity":2,"grams_per_unit":180}; never use g/grams as a unit.`;

/** @param {import('../db.js').AppPrefs} appPrefs */
function mealConstituentsEnabled(appPrefs) {
  return appPrefs.mealConstituentsEnabled !== false;
}

/**
 * @param {Object} args
 * @param {keyof typeof PROVIDERS} args.providerId
 * @param {{apiKey: string, model?: string, baseUrl?: string}} args.config
 * @param {string} [args.text]
 * @param {string} [args.productContext] OFF barcode soft context for the user message
 * @param {{mimeType: string, base64: string}} [args.image]
 * @param {{mimeType: string, base64: string}[]} [args.images]
 * @param {AbortSignal} [args.signal]
 * @param {(phase: string) => void} [args.onPhase]
 * @param {(partial: import('./partial-json.js').PartialFoodEstimate) => void} [args.onPartial]
 * @param {import('../db.js').AppPrefs} [args.prefsOverride] test hook
 */
export async function analyzeFoodEntry({
  providerId,
  config,
  text,
  productContext,
  image,
  images,
  signal,
  onPhase,
  onPartial,
  prefsOverride,
}) {
  const appPrefs = prefsOverride ?? (await prefs.load());
  const imageList = images?.length ? images : image ? [image] : [];
  if (!text && !imageList.length) throw new Error(t("errors.provide_photo_or_text"));

  try {
    return await runAnalyze(providerId, config, text, productContext, imageList, appPrefs, signal, onPhase, onPartial);
  } catch (primaryErr) {
    // Do not burn a fallback provider after the user cancelled / navigated away.
    if (isAbortError(primaryErr) || signal?.aborted) throw primaryErr;
    if (!appPrefs.aiFallbackEnabled || !appPrefs.fallbackAiProvider) throw primaryErr;
    const fbId = /** @type {keyof typeof PROVIDERS} */ (appPrefs.fallbackAiProvider);
    if (fbId === providerId || !PROVIDERS[fbId]) throw primaryErr;
    const fbConfig = await loadProviderKey(/** @type {import('./key-storage.js').ProviderId} */ (fbId));
    if (!fbConfig) throw primaryErr;
    if (appPrefs.fallbackAiModel) fbConfig.model = appPrefs.fallbackAiModel;
    fbConfig.model = resolveProviderModel(fbId, fbConfig.model, "fallback");
    return runAnalyze(fbId, fbConfig, text, productContext, imageList, appPrefs, signal, onPhase, onPartial);
  }
}

/**
 * @param {keyof typeof PROVIDERS} providerId
 * @param {{apiKey: string, model?: string, baseUrl?: string}} config
 * @param {string|undefined} text
 * @param {string|undefined} productContext
 * @param {{mimeType: string, base64: string}[]} imageList
 * @param {import('../db.js').AppPrefs} appPrefs
 * @param {AbortSignal} [signal]
 * @param {(phase: string) => void} [onPhase]
 * @param {(partial: import('./partial-json.js').PartialFoodEstimate) => void} [onPartial]
 */
async function runAnalyze(providerId, config, text, productContext, imageList, appPrefs, signal, onPhase, onPartial) {
  const provider = PROVIDERS[providerId];
  if (!provider) throw new Error(`Unknown AI provider "${providerId}"`);
  if (signal?.aborted) throw abortError();
  config = { ...config, model: resolveProviderModel(providerId, config.model, "primary") };
  let systemPrompt = mealConstituentsEnabled(appPrefs) ? SYSTEM_CONSTITUENTS : SYSTEM_BASE;
  if (appPrefs.userContext?.trim()) {
    systemPrompt += `\n\nUser preferences:\n${appPrefs.userContext.trim()}`;
  }

  let userText =
    text?.trim() ||
    (imageList.length > 1
      ? `Estimate calories and macros for the food across these ${imageList.length} photos of the same meal. Return JSON only.`
      : "Estimate calories and macros for the food in this photo. Return JSON only.");
  if (productContext?.trim()) {
    userText += `\n\n${productContext.trim()}`;
  }

  onPhase?.(ANALYSIS_PHASE.CALLING_AI);
  /** @type {import('./providers.js').AiMessage} */
  const userMessage = {
    role: "user",
    text: userText,
  };
  if (imageList.length === 1) {
    userMessage.image = imageList[0];
  } else if (imageList.length > 1) {
    userMessage.images = imageList;
  }

  const assembler = new FoodPartialJsonAssembler();
  /** @type {(delta: string) => void | undefined} */
  const onDelta = onPartial
    ? (delta) => {
        const partial = assembler.push(delta);
        if (partial) onPartial(partial);
      }
    : undefined;

  const response = await provider.send(config, {
    systemPrompt,
    messages: [userMessage],
    tools: [],
    signal,
    onDelta,
  });

  if (signal?.aborted) throw abortError();
  onPhase?.(ANALYSIS_PHASE.PARSING);
  const parsed = parseJsonObject(response.text);
  if (!parsed) throw new Error(t("errors.parse_estimate"));

  const mealType = ["breakfast", "lunch", "dinner", "snack"].includes(parsed.mealType)
    ? parsed.mealType
    : guessMealTypeFromPrefs(appPrefs);

  /** @param {unknown} v */
  const optNum = (v) => (v == null || v === "" ? null : Math.max(0, Number(v)));

  const name = String(parsed.name || "Food");
  const quantityG = optNum(parsed.quantityG);
  /** @type {"gramsOnly"|"heuristic"|"aiCall"} */
  const inferenceMode =
    appPrefs.servingUnitInferenceMode === "heuristic" || appPrefs.servingUnitInferenceMode === "aiCall"
      ? appPrefs.servingUnitInferenceMode
      : "gramsOnly";
  const fromModel = Array.isArray(parsed.unit_options)
    ? parsed.unit_options
        .map((u) => ({
          unit: String(u?.unit || u?.Unit || ""),
          gramsPerUnit: Number(u?.grams_per_unit ?? u?.gramsPerUnit ?? 0),
          quantity: u?.quantity != null ? Number(u.quantity) : null,
        }))
        .filter((u) => u.unit && u.gramsPerUnit > 0)
    : [];
  const units = ensureServingUnits(
    { name, quantityG, servingUnitOptions: fromModel },
    { inferenceMode },
  );
  const calories = Math.max(0, Math.round(Number(parsed.calories) || 0));
  const proteinG = Math.max(0, Number(parsed.proteinG) || 0);
  const carbsG = Math.max(0, Number(parsed.carbsG) || 0);
  const fatG = Math.max(0, Number(parsed.fatG) || 0);
  const reconciled = reconcileConstituents({
    calories,
    proteinG,
    carbsG,
    fatG,
    quantityG: units.quantityG > 0 ? units.quantityG : 100,
    constituents: mealConstituentsEnabled(appPrefs)
      ? parseConstituentsFromPrediction(parsed)
      : [],
  });

  return {
    name,
    mealType,
    calories,
    proteinG,
    carbsG,
    fatG,
    quantityG: units.quantityG,
    servingUnitOptions: units.servingUnitOptions,
    selectedServingUnit: units.selectedServingUnit,
    selectedServingQuantity: units.selectedServingQuantity,
    constituents: reconciled.constituents,
    note: parsed.note ? String(parsed.note) : null,
    source: "ai_estimated",
    ...Object.fromEntries(ALL_MICRO_KEYS.map((key) => [key, optNum(parsed[key])])),
  };
}

function abortError() {
  const err = new Error("The operation was aborted");
  err.name = "AbortError";
  return err;
}

/** @param {string} text */
function parseJsonObject(text) {
  if (!text) return null;
  const trimmed = text.trim();
  try {
    return JSON.parse(trimmed);
  } catch {
    const match = trimmed.match(/\{[\s\S]*\}/);
    if (!match) return null;
    try {
      return JSON.parse(match[0]);
    } catch {
      return null;
    }
  }
}
