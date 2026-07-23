// @ts-check
// Tool schema register for the coach's tool-calling loop. `get_diary_context`
// is read-only and auto-executed by coach.js. The propose_* tools never
// write anything themselves — they only ever produce a card in coach-view.js
// that the user must explicitly confirm (food proposals route through the
// same entry-form review screen manual entries use).

/** @type {import('./providers.js').AiTool[]} */
export const AI_TOOLS = [
  {
    name: "get_diary_context",
    description: "Read the user's logged food entries, running totals, and calorie/macro targets for a given date. Read-only — use this before proposing anything so estimates account for what's already logged.",
    inputSchema: {
      type: "object",
      properties: { date: { type: "string", description: "ISO date YYYY-MM-DD, defaults to today" } },
    },
  },
  {
    name: "propose_log_food",
    description: "Propose logging a food entry with estimated calories/macros. This does NOT save anything — it opens a review card the user must confirm (and can edit) before it's written to their diary.",
    inputSchema: {
      type: "object",
      required: ["name", "mealType", "date", "calories"],
      properties: {
        name: { type: "string" },
        mealType: { type: "string", enum: ["breakfast", "lunch", "dinner", "snack"] },
        date: { type: "string", description: "ISO date YYYY-MM-DD" },
        time: { type: "string", description: "HH:mm, defaults to now" },
        quantityG: { type: "number" },
        calories: { type: "number" },
        proteinG: { type: "number" },
        carbsG: { type: "number" },
        fatG: { type: "number" },
        note: { type: "string" },
      },
    },
  },
  {
    name: "propose_log_weight",
    description: "Propose logging a body weight measurement. Does not save — the user must confirm.",
    inputSchema: { type: "object", required: ["weightKg"], properties: { weightKg: { type: "number" } } },
  },
  {
    name: "propose_log_water",
    description: "Propose logging a water intake amount. Does not save — the user must confirm.",
    inputSchema: {
      type: "object",
      required: ["amountMl"],
      properties: { amountMl: { type: "number" }, date: { type: "string" } },
    },
  },
];

export const READ_ONLY_TOOLS = new Set(["get_diary_context"]);
export const WRITE_TOOLS = new Set(["propose_log_food", "propose_log_weight", "propose_log_water"]);
