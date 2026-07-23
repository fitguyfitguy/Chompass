// @ts-check
// Diary-oriented food analysis (photo or text) — separate from Coach chat.
// Always returns a proposal object for entry-form review; never writes.
import { PROVIDERS, resolveProviderModel } from "./providers.js";
import { prefs } from "../db.js";
import { loadProviderKey } from "./key-storage.js";
import { guessMealTypeFromPrefs } from "../meal-schedule.js";
import { ALL_MICRO_KEYS } from "../home-nutrients.js";

const SYSTEM = `You estimate nutrition for a food diary app. Reply with ONLY a single JSON object (no markdown), using this shape:
{"name":"string","mealType":"breakfast"|"lunch"|"dinner"|"snack","calories":number,"proteinG":number,"carbsG":number,"fatG":number,"quantityG":number|null,"note":string|null,"fiberG":number|null,"sugarG":number|null,"addedSugarG":number|null,"saturatedFatG":number|null,"sodiumMg":number|null,"potassiumMg":number|null,"calciumMg":number|null,"ironMg":number|null,"vitaminCMg":number|null,"vitaminDMcg":number|null,"cholesterolMg":number|null,"omega3G":number|null}
Include micronutrients when you can estimate them confidently; use null when unsure. Prefer the meal type that fits the current local time if unclear. When multiple photos are provided, treat them as angles of the same meal and produce one estimate.`;

/**
 * @param {Object} args
 * @param {keyof typeof PROVIDERS} args.providerId
 * @param {{apiKey: string, model?: string, baseUrl?: string}} args.config
 * @param {string} [args.text]
 * @param {{mimeType: string, base64: string}} [args.image]
 * @param {{mimeType: string, base64: string}[]} [args.images]
 */
export async function analyzeFoodEntry({ providerId, config, text, image, images }) {
  const appPrefs = await prefs.load();
  const imageList = images?.length ? images : image ? [image] : [];
  if (!text && !imageList.length) throw new Error("Provide a photo or a text description.");

  try {
    return await runAnalyze(providerId, config, text, imageList, appPrefs);
  } catch (primaryErr) {
    if (!appPrefs.aiFallbackEnabled || !appPrefs.fallbackAiProvider) throw primaryErr;
    const fbId = /** @type {keyof typeof PROVIDERS} */ (appPrefs.fallbackAiProvider);
    if (fbId === providerId || !PROVIDERS[fbId]) throw primaryErr;
    const fbConfig = await loadProviderKey(/** @type {import('./key-storage.js').ProviderId} */ (fbId));
    if (!fbConfig) throw primaryErr;
    if (appPrefs.fallbackAiModel) fbConfig.model = appPrefs.fallbackAiModel;
    fbConfig.model = resolveProviderModel(fbId, fbConfig.model, "fallback");
    return runAnalyze(fbId, fbConfig, text, imageList, appPrefs);
  }
}

/**
 * @param {keyof typeof PROVIDERS} providerId
 * @param {{apiKey: string, model?: string, baseUrl?: string}} config
 * @param {string|undefined} text
 * @param {{mimeType: string, base64: string}[]} imageList
 * @param {import('../db.js').AppPrefs} appPrefs
 */
async function runAnalyze(providerId, config, text, imageList, appPrefs) {
  const provider = PROVIDERS[providerId];
  if (!provider) throw new Error(`Unknown AI provider "${providerId}"`);
  config = { ...config, model: resolveProviderModel(providerId, config.model, "primary") };
  let systemPrompt = SYSTEM;
  if (appPrefs.userContext?.trim()) {
    systemPrompt += `\n\nUser preferences:\n${appPrefs.userContext.trim()}`;
  }

  const userText =
    text?.trim() ||
    (imageList.length > 1
      ? `Estimate calories and macros for the food across these ${imageList.length} photos of the same meal. Return JSON only.`
      : "Estimate calories and macros for the food in this photo. Return JSON only.");

  const response = await provider.send(config, {
    systemPrompt,
    messages: [
      {
        role: "user",
        text:
          imageList.length > 1
            ? `${userText}\n(${imageList.length} photos attached; first image included in request.)`
            : userText,
        image: imageList[0],
      },
    ],
    tools: [],
  });

  const parsed = parseJsonObject(response.text);
  if (!parsed) throw new Error("Could not parse nutrition estimate from the model. Try again.");

  const mealType = ["breakfast", "lunch", "dinner", "snack"].includes(parsed.mealType)
    ? parsed.mealType
    : guessMealTypeFromPrefs(appPrefs);

  /** @param {unknown} v */
  const optNum = (v) => (v == null || v === "" ? null : Math.max(0, Number(v)));

  return {
    name: String(parsed.name || "Food"),
    mealType,
    calories: Math.max(0, Math.round(Number(parsed.calories) || 0)),
    proteinG: Math.max(0, Number(parsed.proteinG) || 0),
    carbsG: Math.max(0, Number(parsed.carbsG) || 0),
    fatG: Math.max(0, Number(parsed.fatG) || 0),
    quantityG: optNum(parsed.quantityG),
    note: parsed.note ? String(parsed.note) : null,
    source: "ai_estimated",
    ...Object.fromEntries(ALL_MICRO_KEYS.map((key) => [key, optNum(parsed[key])])),
  };
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
