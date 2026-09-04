// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { isSmallCloudModel } from "../weak-model.js";

test("isSmallCloudModel_matchesLiteNanoHaikuMiniFree", () => {
  assert.equal(isSmallCloudModel("gemini-3.5-flash-lite"), true);
  assert.equal(isSmallCloudModel("gpt-5.4-nano"), true);
  assert.equal(isSmallCloudModel("claude-haiku-4-5"), true);
  assert.equal(isSmallCloudModel("gpt-5.4-mini"), true);
  assert.equal(isSmallCloudModel("google/gemini-3.5-flash-lite:free"), true);
  assert.equal(isSmallCloudModel("openrouter/free"), true);
});

test("isSmallCloudModel_doesNotMatchStrongOrGeminiPrefix", () => {
  assert.equal(isSmallCloudModel("gemini-3.8-flash"), false);
  assert.equal(isSmallCloudModel("gemini-3.7-flash"), false);
  assert.equal(isSmallCloudModel("claude-sonnet-5"), false);
  assert.equal(isSmallCloudModel(""), false);
  assert.equal(isSmallCloudModel(undefined), false);
});
