// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { openAiCompatibleSend } from "../ai/providers.js";

/**
 * Coach reaches the official OpenAI chat endpoint through the generic
 * openai_compatible client. Chat Completions accepts `function` tools on the
 * GPT-5.4+ and GPT-6 ids only with reasoning off (Codeberg pull #121), and the
 * official endpoint never takes OpenRouter's `reasoning` object.
 */

const TOOLS = [
  {
    name: "get_diary_context",
    description: "read the diary",
    inputSchema: { type: "object", properties: {} },
  },
];

/** @param {{model?: string, baseUrl?: string, reasoningEffort?: string, tools?: any[]}} cfg */
async function captureBody(cfg) {
  const originalFetch = globalThis.fetch;
  /** @type {any[]} */
  const bodies = [];
  globalThis.fetch = /** @type {any} */ (async (_url, init) => {
    bodies.push(JSON.parse(String(init.body)));
    return new Response(JSON.stringify({ choices: [{ message: { content: "ok" } }] }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  });
  try {
    await openAiCompatibleSend(
      {
        apiKey: "test-key",
        model: cfg.model,
        baseUrl: cfg.baseUrl,
        reasoningEffort: cfg.reasoningEffort,
      },
      {
        systemPrompt: "system",
        messages: [{ role: "user", text: "how many calories today?" }],
        tools: cfg.tools ?? TOOLS,
      }
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
  assert.equal(bodies.length, 1);
  return bodies[0];
}

test("openAiCompatibleSend_gpt6LunaWithTools_sendsFlatNone", async () => {
  const body = await captureBody({ model: "gpt-6-luna" });
  assert.equal(body.reasoning_effort, "none");
  assert.equal(body.reasoning, undefined);
});

test("openAiCompatibleSend_gpt56SolWithTools_sendsFlatNone", async () => {
  assert.equal((await captureBody({ model: "gpt-5.6-sol" })).reasoning_effort, "none");
});

test("openAiCompatibleSend_gpt4ToolModel_omitsTheField", async () => {
  assert.equal((await captureBody({ model: "gpt-4o-mini" })).reasoning_effort, undefined);
});

test("openAiCompatibleSend_runtimeLineupId_omitsTheField", async () => {
  assert.equal((await captureBody({ model: "gpt-6-astra" })).reasoning_effort, undefined);
});

test("openAiCompatibleSend_customHost_keepsOpenRouterReasoningObject", async () => {
  const body = await captureBody({
    model: "gpt-6-luna",
    baseUrl: "https://llm.example.test/v1",
    reasoningEffort: "high",
  });
  assert.deepEqual(body.reasoning, { effort: "high", exclude: true });
  assert.equal(body.reasoning_effort, undefined);
});

test("openAiCompatibleSend_officialOpenAiWithoutTools_omitsReasoningObject", async () => {
  const body = await captureBody({ model: "gpt-6-luna", reasoningEffort: "high", tools: [] });
  assert.equal(body.reasoning, undefined);
  assert.equal(body.reasoning_effort, undefined);
});
