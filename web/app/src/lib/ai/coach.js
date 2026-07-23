// @ts-check
// Tool-calling orchestration for the AI coach. Read-only tool calls
// (get_diary_context) are executed locally and fed back to the model in a
// loop; write-tool calls (propose_log_*) are never executed here — they're
// returned as proposals for coach-view.js to render as confirm cards, and
// only committed if the user explicitly confirms (see applyProposal below,
// and entry-form.js for the food-proposal review path).
import { foodEntries, weights, water, profile as profileStore } from "../db.js";
import { dailyTargets } from "../nofud-core/formulas.js";
import { PROVIDERS } from "./providers.js";
import { AI_TOOLS, READ_ONLY_TOOLS, WRITE_TOOLS } from "./tools.js";

const SYSTEM_PROMPT = `You are the NoFUD coach: a concise, encouraging calorie and macro tracking assistant embedded in a food diary app.

Use get_diary_context to see what the user has already logged today before estimating anything new — don't guess totals you can look up.

When the user describes food they ate (by text or photo), estimate calories and macros as best you can and call propose_log_food. When they mention a body weight or water intake, call propose_log_weight / propose_log_water. These tools never save automatically — the user always reviews and confirms in the UI, so it's fine (expected, even) to propose your best estimate rather than asking clarifying questions first, as long as you say what you're unsure about in your reply.

Keep replies short — a sentence or two plus the tool call, not an essay.`;

const MAX_TOOL_ITERATIONS = 4;

/**
 * @param {Object} args
 * @param {keyof typeof PROVIDERS} args.providerId
 * @param {{apiKey: string, model?: string, baseUrl?: string}} args.config
 * @param {import('./providers.js').AiMessage[]} args.history
 * @param {string} args.userText
 * @param {{mimeType: string, base64: string}} [args.image]
 */
export async function runCoachTurn({ providerId, config, history, userText, image }) {
  const provider = PROVIDERS[providerId];
  if (!provider) throw new Error(`Unknown AI provider "${providerId}"`);

  const messages = [...history, { role: "user", text: userText, image }];

  for (let iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
    const response = await provider.send(config, { systemPrompt: SYSTEM_PROMPT, messages, tools: AI_TOOLS });
    const readCalls = response.toolCalls.filter((tc) => READ_ONLY_TOOLS.has(tc.name));
    const writeCalls = response.toolCalls.filter((tc) => WRITE_TOOLS.has(tc.name));

    if (writeCalls.length > 0 || response.toolCalls.length === 0) {
      messages.push({ role: "assistant", text: response.text, toolCalls: response.toolCalls });
      return { messages, text: response.text, proposals: writeCalls };
    }

    messages.push({ role: "assistant", text: response.text, toolCalls: readCalls });
    const toolResults = [];
    for (const tc of readCalls) toolResults.push({ id: tc.id, output: await executeReadTool(tc) });
    messages.push({ role: "user", toolResults });
  }

  throw new Error("Coach exceeded the tool-call iteration limit — try rephrasing.");
}

async function executeReadTool(tc) {
  if (tc.name !== "get_diary_context") throw new Error(`Unknown read-only tool "${tc.name}"`);
  const date = tc.input?.date || new Date().toISOString().slice(0, 10);
  const [entries, prof] = await Promise.all([foodEntries.byDate(date), profileStore.load()]);
  const totals = entries.reduce(
    (acc, e) => ({
      calories: acc.calories + e.calories,
      proteinG: acc.proteinG + e.proteinG,
      carbsG: acc.carbsG + e.carbsG,
      fatG: acc.fatG + e.fatG,
    }),
    { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 }
  );
  return {
    date,
    entries: entries.map(({ id, name, mealType, calories, proteinG, carbsG, fatG }) => ({ id, name, mealType, calories, proteinG, carbsG, fatG })),
    totals,
    targets: prof ? dailyTargets(prof) : null,
  };
}

/** Commit a confirmed weight/water proposal. Food proposals route through entry-form.js instead (see coach-view.js). */
export async function applyProposal(tc) {
  if (tc.name === "propose_log_weight") {
    await weights.put({ id: crypto.randomUUID(), date: new Date().toISOString(), weightKg: tc.input.weightKg });
    return;
  }
  if (tc.name === "propose_log_water") {
    await water.put({ id: crypto.randomUUID(), date: tc.input.date || new Date().toISOString().slice(0, 10), amountMl: tc.input.amountMl });
    return;
  }
  throw new Error(`applyProposal does not handle "${tc.name}" — route it through entry-form.js instead`);
}
