// @ts-check
// Thin fetch() clients for the three BYOK provider families, each normalized
// to one shape so coach.js doesn't need to know which provider it's talking
// to. No SDKs — these are plain REST calls, matching the public Messages /
// generateContent / chat.completions APIs directly.

/**
 * @typedef {Object} AiTool
 * @property {string} name
 * @property {string} description
 * @property {Object} inputSchema JSON Schema for the tool's input object
 */

/**
 * @typedef {Object} AiToolCall
 * @property {string} id
 * @property {string} name
 * @property {any} input
 */

/**
 * @typedef {Object} AiMessage
 * @property {"user"|"assistant"} role
 * @property {string} [text]
 * @property {{mimeType: string, base64: string}} [image]
 * @property {AiToolCall[]} [toolCalls]           assistant turn requesting tool use
 * @property {{id: string, output: any}[]} [toolResults]  user turn answering tool calls
 */

/** @typedef {Object} AiResponse
 * @property {string} text
 * @property {AiToolCall[]} toolCalls
 */

/**
 * @param {{apiKey: string, model?: string}} config
 * @param {{systemPrompt: string, messages: AiMessage[], tools: AiTool[]}} req
 * @returns {Promise<AiResponse>}
 */
export async function anthropicSend(config, req) {
  const body = {
    model: config.model || "claude-sonnet-5",
    max_tokens: 1024,
    system: req.systemPrompt,
    messages: req.messages.map(anthropicMessage),
    tools: req.tools.map((t) => ({ name: t.name, description: t.description, input_schema: t.inputSchema })),
  };
  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": config.apiKey,
      "anthropic-version": "2023-06-01",
      "anthropic-dangerous-direct-browser-access": "true",
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Anthropic API error ${res.status}: ${await safeText(res)}`);
  const data = await res.json();
  const text = data.content.filter((b) => b.type === "text").map((b) => b.text).join("");
  const toolCalls = data.content
    .filter((b) => b.type === "tool_use")
    .map((b) => ({ id: b.id, name: b.name, input: b.input }));
  return { text, toolCalls };
}

function anthropicMessage(m) {
  const content = [];
  if (m.image) content.push({ type: "image", source: { type: "base64", media_type: m.image.mimeType, data: m.image.base64 } });
  if (m.text) content.push({ type: "text", text: m.text });
  if (m.toolCalls) for (const tc of m.toolCalls) content.push({ type: "tool_use", id: tc.id, name: tc.name, input: tc.input });
  if (m.toolResults) for (const tr of m.toolResults) content.push({ type: "tool_result", tool_use_id: tr.id, content: JSON.stringify(tr.output) });
  return { role: m.role, content };
}

/**
 * @param {{apiKey: string, model?: string}} config
 * @param {{systemPrompt: string, messages: AiMessage[], tools: AiTool[]}} req
 * @returns {Promise<AiResponse>}
 */
export async function geminiSend(config, req) {
  const model = config.model || "gemini-2.5-flash";
  const body = {
    systemInstruction: { parts: [{ text: req.systemPrompt }] },
    contents: req.messages.map(geminiContent),
    tools: req.tools.length ? [{ functionDeclarations: req.tools.map((t) => ({ name: t.name, description: t.description, parameters: t.inputSchema })) }] : undefined,
  };
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(config.apiKey)}`;
  const res = await fetch(url, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
  if (!res.ok) throw new Error(`Gemini API error ${res.status}: ${await safeText(res)}`);
  const data = await res.json();
  const parts = data.candidates?.[0]?.content?.parts ?? [];
  const text = parts.filter((p) => p.text).map((p) => p.text).join("");
  const toolCalls = parts
    .filter((p) => p.functionCall)
    .map((p, i) => ({ id: `${p.functionCall.name}-${i}`, name: p.functionCall.name, input: p.functionCall.args }));
  return { text, toolCalls };
}

function geminiContent(m) {
  const parts = [];
  if (m.image) parts.push({ inline_data: { mime_type: m.image.mimeType, data: m.image.base64 } });
  if (m.text) parts.push({ text: m.text });
  if (m.toolCalls) for (const tc of m.toolCalls) parts.push({ functionCall: { name: tc.name, args: tc.input } });
  if (m.toolResults) for (const tr of m.toolResults) parts.push({ functionResponse: { name: tr.id.split("-")[0], response: tr.output } });
  return { role: m.role === "assistant" ? "model" : "user", parts };
}

/**
 * @param {{apiKey: string, model?: string, baseUrl?: string}} config
 * @param {{systemPrompt: string, messages: AiMessage[], tools: AiTool[]}} req
 * @returns {Promise<AiResponse>}
 */
export async function openAiCompatibleSend(config, req) {
  const base = (config.baseUrl || "https://api.openai.com/v1").replace(/\/$/, "");
  const messages = [{ role: "system", content: req.systemPrompt }, ...req.messages.flatMap(openAiMessages)];
  const body = {
    model: config.model || "gpt-4o-mini",
    messages,
    tools: req.tools.length ? req.tools.map((t) => ({ type: "function", function: { name: t.name, description: t.description, parameters: t.inputSchema } })) : undefined,
  };
  const res = await fetch(`${base}/chat/completions`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${config.apiKey}` },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`OpenAI-compatible API error ${res.status}: ${await safeText(res)}`);
  const data = await res.json();
  const msg = data.choices[0].message;
  const toolCalls = (msg.tool_calls ?? []).map((tc) => ({ id: tc.id, name: tc.function.name, input: JSON.parse(tc.function.arguments || "{}") }));
  return { text: msg.content ?? "", toolCalls };
}

function openAiMessages(m) {
  if (m.role === "assistant" && m.toolCalls?.length) {
    return [{
      role: "assistant",
      content: m.text || null,
      tool_calls: m.toolCalls.map((tc) => ({ id: tc.id, type: "function", function: { name: tc.name, arguments: JSON.stringify(tc.input) } })),
    }];
  }
  if (m.role === "user" && m.toolResults?.length) {
    return m.toolResults.map((tr) => ({ role: "tool", tool_call_id: tr.id, content: JSON.stringify(tr.output) }));
  }
  if (m.image) {
    return [{
      role: m.role,
      content: [
        { type: "text", text: m.text || "" },
        { type: "image_url", image_url: { url: `data:${m.image.mimeType};base64,${m.image.base64}` } },
      ],
    }];
  }
  return [{ role: m.role, content: m.text || "" }];
}

async function safeText(res) {
  try {
    return await res.text();
  } catch {
    return "(no body)";
  }
}

export const PROVIDERS = {
  anthropic: { label: "Anthropic (Claude)", send: anthropicSend, defaultModel: "claude-sonnet-5" },
  gemini: { label: "Google (Gemini)", send: geminiSend, defaultModel: "gemini-2.5-flash" },
  openai_compatible: { label: "OpenAI-compatible", send: openAiCompatibleSend, defaultModel: "gpt-4o-mini" },
};
