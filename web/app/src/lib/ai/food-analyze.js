// @ts-check
// Diary-oriented food analysis (photo or text) — separate from Coach chat.
// Always returns a proposal object for entry-form review; never writes.
import { PROVIDERS } from "./providers.js";
import { prefs } from "../db.js";
import { loadProviderKey } from "./key-storage.js";
import { guessMealTypeFromPrefs } from "../meal-schedule.js";

const SYSTEM = `You estimate nutrition for a food diary app. Reply with ONLY a single JSON object (no markdown), using this shape:
{"name":"string","mealType":"breakfast"|"lunch"|"dinner"|"snack","calories":number,"proteinG":number,"carbsG":number,"fatG":number,"fiberG":number|null,"quantityG":number|null,"note":string|null,"sugarG":number|null,"sodiumMg":number|null}
Estimate honestly; use null when unsure. Prefer the meal type that fits the current local time if unclear. When multiple photos are provided, treat them as angles of the same meal and produce one estimate.`;

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
    const fbConfig = await loadProviderKey(fbId);
    if (!fbConfig) throw primaryErr;
    if (appPrefs.fallbackAiModel) fbConfig.model = appPrefs.fallbackAiModel;
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

  let systemPrompt = SYSTEM;
  if (appPrefs.userContext?.trim()) {
    systemPrompt += `\n\nUser preferences:\n${appPrefs.userContext.trim()}`;
  }

  const userText =
    text?.trim() ||
    (imageList.length > 1
      ? `Estimate calories and macros for the food across these ${imageList.length} photos of the same meal. Return JSON only.`
      : "Estimate calories and macros for the food in this photo. Return JSON only.");

  // Providers accept a single image on the message; attach the first and note extras in text.
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

  return {
    name: String(parsed.name || "Food"),
    mealType,
    calories: Math.max(0, Math.round(Number(parsed.calories) || 0)),
    proteinG: Math.max(0, Number(parsed.proteinG) || 0),
    carbsG: Math.max(0, Number(parsed.carbsG) || 0),
    fatG: Math.max(0, Number(parsed.fatG) || 0),
    fiberG: parsed.fiberG == null || parsed.fiberG === "" ? null : Math.max(0, Number(parsed.fiberG)),
    sugarG: parsed.sugarG == null || parsed.sugarG === "" ? null : Math.max(0, Number(parsed.sugarG)),
    sodiumMg: parsed.sodiumMg == null || parsed.sodiumMg === "" ? null : Math.max(0, Number(parsed.sodiumMg)),
    quantityG: parsed.quantityG == null || parsed.quantityG === "" ? null : Math.max(0, Number(parsed.quantityG)),
    note: parsed.note ? String(parsed.note) : null,
    source: "ai_estimated",
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
