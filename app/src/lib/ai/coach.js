// @ts-check
import { foodEntries, weights, water, bodyFat, profile as profileStore, prefs, goalJournal } from "../db.js";
import { dailyTargets, bmr, tdee } from "../chompass-core/formulas.js";
import { averageForward, resolveDayJournaled } from "../chompass-core/macro-plan.js";
import { PROVIDERS, resolveVisionModel, resolveProviderModel } from "./providers.js";
import { AI_TOOLS, READ_ONLY_TOOLS, WRITE_TOOLS } from "./tools.js";
import { t } from "../i18n/index.js";
import { localIsoDate, todayIso } from "../date.js";

const BASE_SYSTEM = `You are the Chompass coach: a concise, encouraging calorie and macro tracking assistant embedded in a food diary app.

Use read tools (get_diary_context, get_weight_history, get_data_summary, etc.) before estimating anything new. Don't guess totals you can look up.

When the user describes food they ate (by text or photo), estimate calories and macros and call propose_log_food. When they mention a body weight or water intake, call propose_log_weight / propose_log_water. These tools never save automatically. The user always reviews and confirms.

Keep replies short: a sentence or two plus the tool call, not an essay. Never use em dashes.`;

const MAX_TOOL_ITERATIONS = 4;

/**
 * Local-only fasting timer snapshot for the coach prompt (mirrors the Android
 * ChatService block): differ "should I eat now?" at hour 2 vs hour 15, with an
 * explicit no-claims rule (no autophagy / fat-burning zones, no medical advice).
 * Returns null when fasting is off and there is no recorded fast.
 * @param {import('../db.js').AppPrefs} p
 * @returns {string|null}
 */
export function buildFastingPromptBlock(p) {
  if (p.showFasting !== true) return null;
  const goal = p.fastingGoalHours ?? 0;
  const now = Date.now();
  let body;
  if (p.fastingStartedAt != null) {
    const started = new Date(p.fastingStartedAt).toISOString();
    const elapsed = Math.max(0, now - p.fastingStartedAt);
    body = `- Active fast: started ${started}, elapsed ${fmtFastDuration(elapsed)}, goal ${goal}h`;
  } else if (p.fastingLastEndedAt != null && p.fastingLastFastStartedAt != null) {
    const ended = new Date(p.fastingLastEndedAt).toISOString();
    const lasted = Math.max(0, p.fastingLastEndedAt - p.fastingLastFastStartedAt);
    body = `- No active fast; last fast ended ${ended}, lasted ${fmtFastDuration(lasted)}`;
  } else {
    return null;
  }
  return [
    "## Fasting (intermittent fasting tracker - user-optional, local-only)",
    body,
    "- When the user asks about eating timing, weigh elapsed time against the goal. Never claim autophagy, fat-burning zones, or health effects of fasting duration; fasting is a scheduling tool, not medical advice. Suggest a clinician for fasting-related health questions.",
  ].join("\n");
}

/**
 * Macro day-types block (#60, PWA mirror of the Android ChatService lines):
 * schedule summary + weekly average + today's day type, so "why did I gain
 * weight this week" answers against the average, not a single day's target.
 * Returns null while the plan is off or paused (keto).
 * @param {import('../chompass-core/models.js').UserProfile} profile
 * @param {string} isoToday
 * @returns {string|null}
 */
export function buildDayTypesPromptBlock(profile, isoToday) {
  const plan = profile.macroPlan;
  if (!plan || plan.enabled !== true || !plan.profiles?.length) return null;
  const base = dailyTargets(profile);
  const avg = averageForward(plan, base, isoToday);
  const schedule =
    plan.mode === "WEEKDAYS"
      ? "weekday map"
      : plan.mode === "CYCLE"
        ? `repeating cycle anchored ${plan.cycleAnchorDay ?? "?"}`
        : "manual default";
  const profiles = plan.profiles
    .map((x) => `- ${x.name}: ${x.calories} kcal, ${x.proteinG}P/${x.carbsG}C/${x.fatG}F`)
    .join("\n");
  const today = resolveDayJournaled([], plan, base, isoToday, isoToday);
  const todayLine = today.profileName
    ? `Today (${isoToday}) is a ${today.profileName}: ${today.targets.calories} kcal, ${today.targets.proteinG}P/${today.targets.carbsG}C/${today.targets.fatG}F`
    : `Today (${isoToday}) uses the base targets: ${base.calories} kcal`;
  return [
    "## Day types (different calorie/macro targets per day)",
    `- Assignment: ${schedule}. Weekly average target: ${avg.calories} kcal/day, ${avg.proteinG}P/${avg.carbsG}C/${avg.fatG}F.`,
    "- Judge single days against that day's target and whole weeks against the weekly average.",
    profiles,
    `- ${todayLine}`,
  ].join("\n");
}

/**
 * Concise default intake context (#60 phase 6 follow-up, mirrors the Android
 * ChatService intakeAverageLine): mean daily kcal + P/C/F over the last 14
 * complete logged days (today excluded), or null when there are none.
 * @param {import('../chompass-core/models.js').FoodEntry[]} entries
 * @param {string} isoToday
 * @returns {string|null}
 */
export function buildIntakeAverageBlock(entries, isoToday) {
  const days = new Map();
  for (const e of entries) {
    const day = e.date;
    if (!day || day >= isoToday) continue;
    const acc = days.get(day) ?? { kcal: 0, p: 0, c: 0, f: 0 };
    acc.kcal += e.calories ?? 0;
    acc.p += e.proteinG ?? 0;
    acc.c += e.carbsG ?? 0;
    acc.f += e.fatG ?? 0;
    days.set(day, acc);
  }
  if (days.size === 0) return null;
  /** @param {number} window */
  const windowLine = (window) => {
    const from = new Date(`${isoToday}T00:00:00Z`);
    from.setUTCDate(from.getUTCDate() - window);
    const inWindow = [...days.entries()].filter(([d]) => d >= localIsoDate(from));
    if (inWindow.length === 0) return null;
    const n = inWindow.length;
    const sum = inWindow.reduce(
      (acc, [, v]) => ({ kcal: acc.kcal + v.kcal, p: acc.p + v.p, c: acc.c + v.c, f: acc.f + v.f }),
      { kcal: 0, p: 0, c: 0, f: 0 },
    );
    const r = (x) => Math.round(x / n);
    return `- Average intake last ${window} days (${n} logged day${n === 1 ? "" : "s"}): ${Math.round(sum.kcal / n)} kcal, ${r(sum.p)}g protein, ${r(sum.c)}g carbs, ${r(sum.f)}g fat.`;
  };
  const lines = [windowLine(7), windowLine(30)].filter((x) => x != null);
  if (lines.length === 0) return null;
  return [
    "## Data available",
    ...lines,
    "- Judge intake questions against these (and, with day types, the weekly average target); use the tools for ranges and details.",
  ].join("\n");
}

/** @param {number} millis */
function fmtFastDuration(millis) {
  const totalMinutes = Math.max(0, Math.floor(millis / 60_000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours > 0 && minutes > 0) return `${hours}h ${minutes}m`;
  if (hours > 0) return `${hours}h`;
  return `${minutes}m`;
}

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
  const fastingBlock = buildFastingPromptBlock(appPrefs);
  if (fastingBlock) {
    systemPrompt += `\n\n${fastingBlock}`;
  }
  if (appPrefs.userContext?.trim()) {
    systemPrompt += `\n\nUser preferences:\n${appPrefs.userContext.trim()}`;
  }
  const prof = await profileStore.load();
  if (prof) {
    const dayTypesBlock = buildDayTypesPromptBlock(prof, todayIso());
    if (dayTypesBlock) systemPrompt += `\n\n${dayTypesBlock}`;
  }
  const allEntries = await foodEntries.all();
  const intakeBlock = buildIntakeAverageBlock(allEntries, todayIso());
  if (intakeBlock) systemPrompt += `\n\n${intakeBlock}`;

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
  const today = todayIso();
  if (tc.name === "get_diary_context") {
    const date = tc.input?.date || today;
    const [entries, prof, journal] = await Promise.all([
      foodEntries.byDate(date),
      profileStore.load(),
      goalJournal.all(),
    ]);
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
      // Day types (#60): journal-first (frozen actuals for past days), live
      // resolution for today/future.
      targets: prof
        ? resolveDayJournaled(
            journal,
            prof.macroPlan ?? null,
            dailyTargets(prof),
            String(date).slice(0, 10),
            today,
          ).targets
        : null,
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
      date: tc.input.date || todayIso(),
      amountMl: tc.input.amountMl,
    });
    return;
  }
  throw new Error(`applyProposal does not handle "${tc.name}"; route it through entry-form.js instead`);
}
