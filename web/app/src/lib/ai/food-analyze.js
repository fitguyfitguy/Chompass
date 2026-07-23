// @ts-check
// Diary-oriented food analysis (photo or text) — separate from Coach chat.
// Always returns a proposal object for entry-form review; never writes.
import { PROVIDERS } from "./providers.js";

const SYSTEM = `You estimate nutrition for a food diary app. Reply with ONLY a single JSON object (no markdown), using this shape:
{"name":"string","mealType":"breakfast"|"lunch"|"dinner"|"snack","calories":number,"proteinG":number,"carbsG":number,"fatG":number,"fiberG":number|null,"quantityG":number|null,"note":string|null}
Estimate honestly; use null when unsure about fiber/grams. Prefer the meal type that fits the current local time if unclear.`;

/**
 * @param {Object} args
 * @param {keyof typeof PROVIDERS} args.providerId
 * @param {{apiKey: string, model?: string, baseUrl?: string}} args.config
 * @param {string} [args.text]
 * @param {{mimeType: string, base64: string}} [args.image]
 */
export async function analyzeFoodEntry({ providerId, config, text, image }) {
  const provider = PROVIDERS[providerId];
  if (!provider) throw new Error(`Unknown AI provider "${providerId}"`);
  if (!text && !image) throw new Error("Provide a photo or a text description.");

  const userText =
    text?.trim() ||
    "Estimate calories and macros for the food in this photo. Return JSON only.";

  const response = await provider.send(config, {
    systemPrompt: SYSTEM,
    messages: [{ role: "user", text: userText, image }],
    tools: [],
  });

  const parsed = parseJsonObject(response.text);
  if (!parsed) throw new Error("Could not parse nutrition estimate from the model. Try again.");

  const mealType = ["breakfast", "lunch", "dinner", "snack"].includes(parsed.mealType)
    ? parsed.mealType
    : guessMealType();

  return {
    name: String(parsed.name || "Food"),
    mealType,
    calories: Math.max(0, Math.round(Number(parsed.calories) || 0)),
    proteinG: Math.max(0, Number(parsed.proteinG) || 0),
    carbsG: Math.max(0, Number(parsed.carbsG) || 0),
    fatG: Math.max(0, Number(parsed.fatG) || 0),
    fiberG: parsed.fiberG == null || parsed.fiberG === "" ? null : Math.max(0, Number(parsed.fiberG)),
    quantityG: parsed.quantityG == null || parsed.quantityG === "" ? null : Math.max(0, Number(parsed.quantityG)),
    note: parsed.note ? String(parsed.note) : null,
    source: "ai_estimated",
  };
}

function guessMealType() {
  const h = new Date().getHours();
  if (h < 11) return "breakfast";
  if (h < 15) return "lunch";
  if (h < 21) return "dinner";
  return "snack";
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
