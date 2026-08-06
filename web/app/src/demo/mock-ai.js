// @ts-check
// Demo-only scripted AI reply for the marketing hero (web/app/demo.html).
// Mirrors the streaming contract of the real provider path: phase callbacks
// and JSON deltas through FoodPartialJsonAssembler (the PWA counterpart of
// Android's DemoFoodAnalysis.kt). Never imported by the production app.
import { FoodPartialJsonAssembler } from "../lib/ai/partial-json.js";

// Phase ids match ANALYSIS_PHASE in lib/ai/food-analyze.js (kept literal here
// to avoid an import cycle).
const PHASE_PREPARING = "preparing";
const PHASE_CALLING_AI = "calling_ai";
const PHASE_PARSING = "parsing";

/** Scripted "Chicken burrito bowl" estimate; constituents sum to the totals. */
const DEMO_ESTIMATE = Object.freeze({
  name: "Chicken burrito bowl",
  mealType: "lunch",
  calories: 640,
  proteinG: 42,
  carbsG: 70,
  fatG: 18,
  quantityG: 450,
  servingUnitOptions: [{ unit: "bowl", gramsPerUnit: 450, quantity: 1 }],
  selectedServingUnit: "bowl",
  selectedServingQuantity: 1,
  constituents: [
    {
      name: "Chicken",
      emoji: "🍗",
      calories: 280,
      protein: 38,
      carbs: 0,
      fat: 14,
      serving_size_grams: 160,
      unit_options: [],
    },
    {
      name: "Brown rice",
      emoji: "🍚",
      calories: 220,
      protein: 5,
      carbs: 45,
      fat: 2,
      serving_size_grams: 180,
      unit_options: [],
    },
    {
      name: "Beans & salsa",
      emoji: "🫘",
      calories: 140,
      protein: 7,
      carbs: 22,
      fat: 3,
      serving_size_grams: 110,
      unit_options: [],
    },
  ],
  note: null,
  source: "ai_estimated",
  fiberG: 9,
  sugarG: 4,
  addedSugarG: 0,
  saturatedFatG: 5,
  sodiumMg: 880,
  potassiumMg: 480,
  calciumMg: 120,
  ironMg: 2.4,
  vitaminCMg: 6,
  vitaminDMcg: 0,
  cholesterolMg: 70,
  omega3G: 0.1,
});

/**
 * @param {Object} args
 * @param {AbortSignal} [args.signal]
 * @param {(phase: string) => void} [args.onPhase]
 * @param {(partial: import('../lib/ai/partial-json.js').PartialFoodEstimate) => void} [args.onPartial]
 * @returns {Promise<Record<string, any>>} final estimate (analyzeFoodEntry shape)
 */
export async function runDemoAnalyze({ signal, onPhase, onPartial }) {
  onPhase?.(PHASE_PREPARING);
  await demoDelay(450, signal);
  onPhase?.(PHASE_CALLING_AI);

  const json = JSON.stringify(DEMO_ESTIMATE);
  const assembler = new FoodPartialJsonAssembler();
  const chunkSize = Math.ceil(json.length / 14);
  for (let i = 0; i < json.length; i += chunkSize) {
    if (signal?.aborted) throw demoAbortError();
    const partial = assembler.push(json.slice(i, i + chunkSize));
    if (partial) onPartial?.(partial);
    await demoDelay(110, signal);
  }

  onPhase?.(PHASE_PARSING);
  await demoDelay(280, signal);
  if (signal?.aborted) throw demoAbortError();
  return JSON.parse(json);
}

/**
 * @param {number} ms
 * @param {AbortSignal} [signal]
 */
function demoDelay(ms, signal) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener(
      "abort",
      () => {
        clearTimeout(timer);
        reject(demoAbortError());
      },
      { once: true },
    );
  });
}

function demoAbortError() {
  const err = new Error("The operation was aborted");
  err.name = "AbortError";
  return err;
}
