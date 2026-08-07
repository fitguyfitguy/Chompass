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

/** Scripted "Grilled salmon plate" estimate for the plate-scan beat. */
export const DEMO_PLATE_ESTIMATE = Object.freeze({
  name: "Grilled salmon, rice & broccoli",
  mealType: "dinner",
  calories: 560,
  proteinG: 38,
  carbsG: 46,
  fatG: 24,
  quantityG: 420,
  servingUnitOptions: [{ unit: "plate", gramsPerUnit: 420, quantity: 1 }],
  selectedServingUnit: "plate",
  selectedServingQuantity: 1,
  constituents: [
    {
      name: "Grilled salmon",
      emoji: "🐟",
      calories: 310,
      protein: 34,
      carbs: 0,
      fat: 19,
      serving_size_grams: 160,
      unit_options: [],
    },
    {
      name: "Steamed rice",
      emoji: "🍚",
      calories: 180,
      protein: 4,
      carbs: 40,
      fat: 0,
      serving_size_grams: 150,
      unit_options: [],
    },
    {
      name: "Broccoli",
      emoji: "🥦",
      calories: 70,
      protein: 5,
      carbs: 11,
      fat: 1,
      serving_size_grams: 110,
      unit_options: [],
    },
  ],
  note: null,
  source: "ai_estimated",
  fiberG: 8,
  sugarG: 3,
  addedSugarG: 0,
  saturatedFatG: 4,
  sodiumMg: 420,
  potassiumMg: 720,
  calciumMg: 80,
  ironMg: 1.8,
  vitaminCMg: 70,
  vitaminDMcg: 9,
  cholesterolMg: 75,
  omega3G: 1.9,
});

/**
 * @param {Object} args
 * @param {AbortSignal} [args.signal]
 * @param {(phase: string) => void} [args.onPhase]
 * @param {(partial: import('../lib/ai/partial-json.js').PartialFoodEstimate) => void} [args.onPartial]
 * @param {Readonly<Record<string, any>>} [args.estimate] which scripted meal to stream
 * @returns {Promise<Record<string, any>>} final estimate (analyzeFoodEntry shape)
 */
export async function runDemoAnalyze({
  signal,
  onPhase,
  onPartial,
  estimate = DEMO_ESTIMATE,
}) {
  onPhase?.(PHASE_PREPARING);
  await demoDelay(450, signal);
  onPhase?.(PHASE_CALLING_AI);

  const json = JSON.stringify(estimate);
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
