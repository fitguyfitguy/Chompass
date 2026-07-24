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
 * @property {{mimeType: string, base64: string}} [image] single image (coach / legacy)
 * @property {{mimeType: string, base64: string}[]} [images] multi-angle meal photos
 * @property {AiToolCall[]} [toolCalls]           assistant turn requesting tool use
 * @property {{id: string, output: any}[]} [toolResults]  user turn answering tool calls
 */

/**
 * Resolve image list from a message (`images` wins; else singular `image`).
 * @param {AiMessage} m
 * @returns {{mimeType: string, base64: string}[]}
 */
export function messageImages(m) {
  if (m.images?.length) return m.images;
  if (m.image) return [m.image];
  return [];
}

/** @typedef {Object} AiResponse
 * @property {string} text
 * @property {AiToolCall[]} toolCalls
 */

/**
 * @typedef {Object} AiRequest
 * @property {string} systemPrompt
 * @property {AiMessage[]} messages
 * @property {AiTool[]} tools
 * @property {AbortSignal} [signal]
 */

/**
 * @param {{apiKey: string, model?: string}} config
 * @param {AiRequest} req
 * @returns {Promise<AiResponse>}
 */
export async function anthropicSend(config, req) {
  const body = {
    model: config.model || PROVIDERS.anthropic.defaultModel,
    max_tokens: 1024,
    system: req.systemPrompt,
    messages: req.messages.map(anthropicMessage),
  };
  if (req.tools.length) {
    body.tools = req.tools.map((t) => ({ name: t.name, description: t.description, input_schema: t.inputSchema }));
  }
  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": config.apiKey,
      "anthropic-version": "2023-06-01",
      "anthropic-dangerous-direct-browser-access": "true",
    },
    body: JSON.stringify(body),
    signal: req.signal,
  });
  if (!res.ok) throw new Error(`Anthropic API error ${res.status}: ${await safeText(res)}`);
  const data = await res.json();
  const text = data.content.filter((b) => b.type === "text").map((b) => b.text).join("");
  const toolCalls = data.content
    .filter((b) => b.type === "tool_use")
    .map((b) => ({ id: b.id, name: b.name, input: b.input }));
  return { text, toolCalls };
}

/** @param {AiMessage} m */
export function anthropicMessage(m) {
  const content = [];
  for (const img of messageImages(m)) {
    content.push({ type: "image", source: { type: "base64", media_type: img.mimeType, data: img.base64 } });
  }
  if (m.text) content.push({ type: "text", text: m.text });
  if (m.toolCalls) for (const tc of m.toolCalls) content.push({ type: "tool_use", id: tc.id, name: tc.name, input: tc.input });
  if (m.toolResults) for (const tr of m.toolResults) content.push({ type: "tool_result", tool_use_id: tr.id, content: JSON.stringify(tr.output) });
  return { role: m.role, content };
}

/**
 * @param {{apiKey: string, model?: string}} config
 * @param {AiRequest} req
 * @returns {Promise<AiResponse>}
 */
export async function geminiSend(config, req) {
  const model = config.model || PROVIDERS.gemini.defaultModel;
  const body = {
    systemInstruction: { parts: [{ text: req.systemPrompt }] },
    contents: req.messages.map(geminiContent),
    tools: req.tools.length ? [{ functionDeclarations: req.tools.map((t) => ({ name: t.name, description: t.description, parameters: t.inputSchema })) }] : undefined,
  };
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
  const res = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json", "x-goog-api-key": config.apiKey },
    body: JSON.stringify(body),
    signal: req.signal,
  });
  if (!res.ok) throw new Error(`Gemini API error ${res.status}: ${await safeText(res)}`);
  const data = await res.json();
  const parts = data.candidates?.[0]?.content?.parts ?? [];
  const text = parts.filter((p) => p.text).map((p) => p.text).join("");
  const toolCalls = parts
    .filter((p) => p.functionCall)
    .map((p, i) => ({ id: `${p.functionCall.name}-${i}`, name: p.functionCall.name, input: p.functionCall.args }));
  return { text, toolCalls };
}

/** @param {AiMessage} m */
export function geminiContent(m) {
  const parts = [];
  for (const img of messageImages(m)) {
    parts.push({ inline_data: { mime_type: img.mimeType, data: img.base64 } });
  }
  if (m.text) parts.push({ text: m.text });
  if (m.toolCalls) for (const tc of m.toolCalls) parts.push({ functionCall: { name: tc.name, args: tc.input } });
  if (m.toolResults) for (const tr of m.toolResults) parts.push({ functionResponse: { name: tr.id.split("-")[0], response: tr.output } });
  return { role: m.role === "assistant" ? "model" : "user", parts };
}

/**
 * @param {{apiKey: string, model?: string, baseUrl?: string}} config
 * @param {AiRequest} req
 * @returns {Promise<AiResponse>}
 */
export async function openAiCompatibleSend(config, req) {
  const base = (config.baseUrl || "https://api.openai.com/v1").replace(/\/$/, "");
  const messages = [{ role: "system", content: req.systemPrompt }, ...req.messages.flatMap(openAiMessages)];
  const body = {
    model: config.model || PROVIDERS.openai_compatible.defaultModel,
    messages,
    tools: req.tools.length ? req.tools.map((t) => ({ type: "function", function: { name: t.name, description: t.description, parameters: t.inputSchema } })) : undefined,
  };
  const res = await fetch(`${base}/chat/completions`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${config.apiKey}` },
    body: JSON.stringify(body),
    signal: req.signal,
  });
  if (!res.ok) throw new Error(`OpenAI-compatible API error ${res.status}: ${await safeText(res)}`);
  const data = await res.json();
  const msg = data.choices[0].message;
  const toolCalls = (msg.tool_calls ?? []).map((tc) => ({ id: tc.id, name: tc.function.name, input: JSON.parse(tc.function.arguments || "{}") }));
  return { text: msg.content ?? "", toolCalls };
}

/**
 * @param {AiMessage} m
 * @returns {any[]}
 */
export function openAiMessages(m) {
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
  const imgs = messageImages(m);
  if (imgs.length) {
    return [{
      role: m.role,
      content: [
        { type: "text", text: m.text || "" },
        ...imgs.map((img) => ({
          type: "image_url",
          image_url: { url: `data:${img.mimeType};base64,${img.base64}` },
        })),
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

/**
 * @typedef {Object} ProviderMeta
 * @property {string} label
 * @property {(config: any, req: any) => Promise<AiResponse>} send
 * @property {string} defaultModel
 * @property {string} [defaultFallbackModel]
 * @property {string[]} models
 * @property {boolean} [supportsCustomModel]
 */

/** @type {Record<string, ProviderMeta>} */
export const PROVIDERS = {
  anthropic: {
    label: "Anthropic (Claude)",
    send: anthropicSend,
    defaultModel: "claude-haiku-4-5",
    defaultFallbackModel: "claude-sonnet-5",
    models: [
      "claude-haiku-4-5",
      "claude-sonnet-5",
      "claude-opus-4-8",
      "claude-sonnet-4-6",
      "claude-opus-4-7",
    ],
  },
  gemini: {
    label: "Google (Gemini)",
    send: geminiSend,
    defaultModel: "gemini-3.6-flash",
    defaultFallbackModel: "gemini-3.5-flash-lite",
    models: [
      "gemini-3.6-flash",
      "gemini-3.5-flash-lite",
      "gemini-3.5-flash",
      "gemini-3.1-flash-lite",
      "gemini-3.1-pro-preview",
      "gemini-2.5-flash",
      "gemini-2.5-pro",
    ],
  },
  openai_compatible: {
    label: "OpenAI-compatible",
    send: openAiCompatibleSend,
    defaultModel: "gpt-5.4-mini",
    defaultFallbackModel: "gpt-5.4-nano",
    supportsCustomModel: true,
    models: ["gpt-5.4-mini", "gpt-5.4-nano", "gpt-5.5", "gpt-4.1", "gpt-4.1-mini", "gpt-4o-mini"],
  },
};

/**
 * Resolve a model id against a provider's curated list (Android-style).
 * @param {keyof typeof PROVIDERS|string} providerId
 * @param {string|null|undefined} model
 * @param {"primary"|"fallback"} [role]
 */
export function resolveProviderModel(providerId, model, role = "primary") {
  const meta = PROVIDERS[providerId];
  if (!meta) return model || "";
  const fallbackDefault = meta.defaultFallbackModel || meta.models[1] || meta.defaultModel;
  const preferred = role === "fallback" ? fallbackDefault : meta.defaultModel;
  const trimmed = (model || "").trim();
  if (!trimmed) return preferred;
  if (meta.supportsCustomModel) return trimmed;
  if (meta.models.includes(trimmed)) return trimmed;
  return preferred;
}

/**
 * @param {keyof typeof PROVIDERS|string} providerId
 * @param {string|null|undefined} selected
 * @param {"primary"|"fallback"} [role]
 */
export function modelSelectOptionsHtml(providerId, selected, role = "primary") {
  const meta = PROVIDERS[providerId];
  if (!meta) return "";
  const current = resolveProviderModel(providerId, selected, role);
  const opts = meta.models.map(
    (id) => `<option value="${id}" ${id === current ? "selected" : ""}>${id}</option>`
  );
  if (meta.supportsCustomModel && selected && !meta.models.includes(selected)) {
    opts.push(`<option value="${escapeAttr(selected)}" selected>${escapeAttr(selected)} (custom)</option>`);
  }
  if (meta.supportsCustomModel) {
    opts.push(`<option value="__custom__">Custom…</option>`);
  }
  return opts.join("");
}

function escapeAttr(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
