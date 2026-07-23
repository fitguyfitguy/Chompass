// @ts-check
// Tool schema register for the coach's tool-calling loop.

/** @type {import('./providers.js').AiTool[]} */
export const AI_TOOLS = [
  {
    name: "get_diary_context",
    description: "Read logged food entries, running totals, and calorie/macro targets for a date. Read-only.",
    inputSchema: {
      type: "object",
      properties: { date: { type: "string", description: "ISO date YYYY-MM-DD, defaults to today" } },
    },
  },
  {
    name: "get_data_summary",
    description: "High-level summary: profile targets, recent weight, body-fat count, diary day count. Read-only.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "get_weight_history",
    description: "List recent weight entries (newest first). Read-only.",
    inputSchema: {
      type: "object",
      properties: { limit: { type: "number", description: "Max entries, default 30" } },
    },
  },
  {
    name: "get_body_fat_history",
    description: "List recent body-fat entries (newest first). Read-only.",
    inputSchema: {
      type: "object",
      properties: { limit: { type: "number", description: "Max entries, default 30" } },
    },
  },
  {
    name: "get_calorie_totals",
    description: "Daily calorie totals for a date range. Read-only.",
    inputSchema: {
      type: "object",
      properties: {
        startDate: { type: "string", description: "ISO YYYY-MM-DD" },
        endDate: { type: "string", description: "ISO YYYY-MM-DD" },
      },
    },
  },
  {
    name: "get_food_entries",
    description: "Food entries for a specific date. Read-only.",
    inputSchema: {
      type: "object",
      properties: { date: { type: "string", description: "ISO YYYY-MM-DD" } },
    },
  },
  {
    name: "propose_log_food",
    description: "Propose logging a food entry. Does NOT save — opens a review card the user must confirm.",
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
        fiberG: { type: "number" },
        sugarG: { type: "number" },
        addedSugarG: { type: "number" },
        sodiumMg: { type: "number" },
        saturatedFatG: { type: "number" },
        potassiumMg: { type: "number" },
        calciumMg: { type: "number" },
        ironMg: { type: "number" },
        vitaminCMg: { type: "number" },
        vitaminDMcg: { type: "number" },
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

export const READ_ONLY_TOOLS = new Set([
  "get_diary_context",
  "get_data_summary",
  "get_weight_history",
  "get_body_fat_history",
  "get_calorie_totals",
  "get_food_entries",
]);
export const WRITE_TOOLS = new Set(["propose_log_food", "propose_log_weight", "propose_log_water"]);
