// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  anthropicMessage,
  geminiContent,
  messageImages,
  openAiMessages,
} from "../ai/providers.js";

const imgA = { mimeType: "image/jpeg", base64: "aaa" };
const imgB = { mimeType: "image/png", base64: "bbb" };

test("messageImages_prefersImagesArrayOverSingular", () => {
  assert.deepEqual(messageImages({ role: "user", images: [imgA, imgB], image: imgA }), [imgA, imgB]);
  assert.deepEqual(messageImages({ role: "user", image: imgA }), [imgA]);
  assert.deepEqual(messageImages({ role: "user", text: "hi" }), []);
});

test("anthropicMessage_emitsOnePartPerImage", () => {
  const mapped = anthropicMessage({ role: "user", text: "estimate", images: [imgA, imgB] });
  assert.equal(mapped.content.length, 3);
  assert.equal(mapped.content[0].type, "image");
  assert.equal(mapped.content[0].source.data, "aaa");
  assert.equal(mapped.content[1].type, "image");
  assert.equal(mapped.content[1].source.data, "bbb");
  assert.equal(mapped.content[2].type, "text");
});

test("geminiContent_emitsOnePartPerImage", () => {
  const mapped = geminiContent({ role: "user", text: "estimate", images: [imgA, imgB] });
  assert.equal(mapped.parts.length, 3);
  assert.equal(mapped.parts[0].inline_data.data, "aaa");
  assert.equal(mapped.parts[1].inline_data.data, "bbb");
  assert.equal(mapped.parts[2].text, "estimate");
});

// Codeberg #115: gemini-3 thoughtSignature must survive the normalized
// assistant tool-call turn and be re-emitted on the follow-up request.
test("geminiContent_roundTripsThoughtSignatureOnToolCalls", () => {
  const mapped = geminiContent({
    role: "assistant",
    text: "checking",
    toolCalls: [{ id: "log_meal-0", name: "log_meal", input: { kcal: 500 }, thoughtSignature: "sig-abc" }],
  });
  assert.equal(mapped.role, "model");
  assert.equal(mapped.parts.length, 2);
  assert.equal(mapped.parts[1].functionCall.name, "log_meal");
  assert.equal(mapped.parts[1].thoughtSignature, "sig-abc");
});

test("geminiContent_omitsThoughtSignatureWhenAbsent", () => {
  const mapped = geminiContent({
    role: "assistant",
    toolCalls: [{ id: "log_meal-0", name: "log_meal", input: { kcal: 500 } }],
  });
  assert.equal(mapped.parts[0].thoughtSignature, undefined);
});

test("openAiMessages_emitsOneImageUrlPerImage", () => {
  const [mapped] = openAiMessages({ role: "user", text: "estimate", images: [imgA, imgB] });
  assert.equal(mapped.content.length, 3);
  assert.equal(mapped.content[0].type, "text");
  assert.equal(mapped.content[1].type, "image_url");
  assert.match(mapped.content[1].image_url.url, /^data:image\/jpeg;base64,aaa$/);
  assert.match(mapped.content[2].image_url.url, /^data:image\/png;base64,bbb$/);
});
