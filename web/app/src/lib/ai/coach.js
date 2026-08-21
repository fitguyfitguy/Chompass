// @ts-check
import { foodEntries, weights, water, bodyFat, profile as profileStore, prefs } from "../db.js";
import { dailyTargets, bmr, tdee } from "../chompass-core/formulas.js";
import { PROVIDERS, resolveVisionModel, resolveProviderModel } from "./providers.js";
import { AI_TOOLS, READ_ONLY_TOOLS, WRITE_TOOLS } from "./tools.js";
import { t } from "../i18n/index.js";

const BASE_SYSTEM = `You are the Chompass coach: a concise, encouraging calorie and macro tracking assistant embedded in a food diary app.

Use read tools (get_diary_context, get_weight_history, get_data_summary, etc.) before estimating anything new. Don't guess totals you can look up.

When the user describes food they ate (by text or photo), estimate calories and macros and call propose_log_food. When they mention a body weight or water intake, call propose_log_weight / propose_log_water. These tools never save automatically. The user always reviews and confirms.

Keep replies short: a sentence or two plus the tool call, not an essay. Never use em dashes.`;

const MAX_TOOL_ITERATIONS = 4;

/**
 * @param {Object} args
 * @param {keyof typeof PROVIDERS} args.providerId
 * @param {{apiKey: string, model?: string, baseUrl?: string, reasoningEffort?: string, visionModel?: string}} args.config
 * @param {import('./providers.js').AiMessage[]} args.history
 * @param {string} args.userText
 * @param {{mimeType: string, base64: string}} [args.image]
 * @param {import('../db.js').AppPrefs} [args.prefsOverride] test hook
 */
export async function runCoachTurn({ providerId, config, history, userText, image, prefsOverride }) {
  const provider = PROVIDERS[providerId];
  if (!provider) throw new Error(`Unknown AI provider "${providerId}"`);

  const appPrefs = prefsOverride ?? (await prefs.load());
  // Codeberg #20 phase 2: the master AI-features switch gates the coach
  // before the system prompt (profile + diary) is assembled.
  if (appPrefs.aiFeaturesEnabled === false) throw new Error(t("errors.ai_features_disabled"));
  if (providerId === "openai_compatible") {
    config = { ...config, reasoningEffort: appPrefs.openrouterReasoningEffort || "auto" };
  }
  if (image) {
    config = {
      ...config,
      model: resolveVisionModel(providerId, config.visionModel, resolveProviderModel(providerId, config.model, "primary")),
    };
  }
  let systemPrompt = BASE_SYSTEM;
  if (appPrefs.userContext?.trim()) {
    systemPrompt += `\n\nUser preferences:\n${appPrefs.userContext.trim()}`;
  }

  const messages = /** @type {import('./providers.js').AiMessage[]} */ ([
    ...history,
    { role: "user", text: userText, image },
  ]);

  for (let iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
    const response = await provider.send(config, { systemPrompt, messages, tools: AI_TOOLS });
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

  throw new Error(t("errors.coach_tool_limit"));
}

async function executeReadTool(tc) {
  const today = new Date().toISOString().slice(0, 10);
  if (tc.name === "get_diary_context") {
    const date = tc.input?.date || today;
    const [entries, prof] = await Promise.all([foodEntries.byDate(date), profileStore.load()]);
    const totals = sumMacros(entries);
    return {
      date,
      entries: entries.map(({ id, name, mealType, calories, proteinG, carbsG, fatG }) => ({
        id,
        name,
        mealType,
        calories,
        proteinG,
        carbsG,
        fatG,
      })),
      totals,
      targets: prof ? dailyTargets(prof) : null,
    };
  }
  if (tc.name === "get_food_entries") {
    const date = tc.input?.date || today;
    const entries = await foodEntries.byDate(date);
    return { date, entries };
  }
  if (tc.name === "get_weight_history") {
    const limit = Math.min(100, Math.max(1, Number(tc.input?.limit) || 30));
    const all = (await weights.all()).slice().sort((a, b) => b.date.localeCompare(a.date));
    return all.slice(0, limit);
  }
  if (tc.name === "get_body_fat_history") {
    const limit = Math.min(100, Math.max(1, Number(tc.input?.limit) || 30));
    const all = (await bodyFat.all()).slice().sort((a, b) => b.date.localeCompare(a.date));
    return all.slice(0, limit).map((e) => ({
      ...e,
      bodyFatPercent: e.bodyFatPercent > 1 ? e.bodyFatPercent : e.bodyFatPercent * 100,
    }));
  }
  if (tc.name === "get_calorie_totals") {
    const end = tc.input?.endDate || today;
    const start = tc.input?.startDate || end;
    const all = await foodEntries.all();
    /** @type {Record<string, number>} */
    const byDate = {};
    for (const e of all) {
      const day = String(e.date).slice(0, 10);
      if (day < start || day > end) continue;
      byDate[day] = (byDate[day] || 0) + e.calories;
    }
    const complete = Object.entries(byDate).filter(([d]) => d < today);
    const average =
      complete.length === 0
        ? null
        : Math.trunc(complete.reduce((s, [, kcal]) => s + kcal, 0) / complete.length);
    return {
      days_with_data: Object.keys(byDate).length,
      average_kcal_logged_days: average,
      totals: Object.entries(byDate)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([date, kcal]) => ({ date, kcal })),
    };
  }
  if (tc.name === "get_data_summary") {
    const [prof, allFood, allW, allBf] = await Promise.all([
      profileStore.load(),
      foodEntries.all(),
      weights.all(),
      bodyFat.all(),
    ]);
    const days = new Set(allFood.map((e) => e.date));
    return {
      profile: prof
        ? {
            goal: prof.goal,
            ketoMode: !!prof.ketoMode,
            weightKg: prof.weightKg,
            targets: dailyTargets(prof),
            bmr: Math.round(bmr(prof)),
            tdee: Math.round(tdee(prof)),
          }
        : null,
      diaryDays: days.size,
      foodEntries: allFood.length,
      weightEntries: allW.length,
      bodyFatEntries: allBf.length,
      latestWeightKg: allW.slice().sort((a, b) => b.date.localeCompare(a.date))[0]?.weightKg ?? null,
    };
  }
  throw new Error(`Unknown read-only tool "${tc.name}"`);
}

function sumMacros(entries) {
  return entries.reduce(
    (acc, e) => ({
      calories: acc.calories + e.calories,
      proteinG: acc.proteinG + e.proteinG,
      carbsG: acc.carbsG + e.carbsG,
      fatG: acc.fatG + e.fatG,
    }),
    { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 }
  );
}

/** Commit a confirmed weight/water proposal. Food proposals route through entry-form.js. */
export async function applyProposal(tc) {
  if (tc.name === "propose_log_weight") {
    await weights.put({ id: crypto.randomUUID(), date: new Date().toISOString(), weightKg: tc.input.weightKg });
    return;
  }
  if (tc.name === "propose_log_water") {
    await water.put({
      id: crypto.randomUUID(),
      date: tc.input.date || new Date().toISOString().slice(0, 10),
      amountMl: tc.input.amountMl,
    });
    return;
  }
  throw new Error(`applyProposal does not handle "${tc.name}"; route it through entry-form.js instead`);
}
