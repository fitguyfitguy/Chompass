// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { normalizeWebDavUrl, webDavBasicAuthHeader } from "../sync.js";

test("normalizeWebDavUrl adds https when scheme missing", () => {
  assert.equal(
    normalizeWebDavUrl("u123-sub1.your-storagebox.de/sync.json"),
    "https://u123-sub1.your-storagebox.de/sync.json",
  );
});

test("normalizeWebDavUrl collapses stacked schemes", () => {
  assert.equal(
    normalizeWebDavUrl("https://https://u123-sub1.your-storagebox.de/sync.json"),
    "https://u123-sub1.your-storagebox.de/sync.json",
  );
  assert.equal(
    normalizeWebDavUrl("http://https://u123-sub1.your-storagebox.de/sync.json"),
    "https://u123-sub1.your-storagebox.de/sync.json",
  );
});

test("normalizeWebDavUrl preserves explicit http", () => {
  assert.equal(
    normalizeWebDavUrl("http://192.168.1.10/sync.json"),
    "http://192.168.1.10/sync.json",
  );
});

test("webDavBasicAuthHeader uses UTF-8 for non-ASCII passwords", () => {
  const user = "u123-sub1";
  const password = "eK9ßSThq6CTW§jß";
  const header = webDavBasicAuthHeader(user, password);
  const utf8 = Buffer.from(`${user}:${password}`, "utf8").toString("base64");
  const latin1 = Buffer.from(`${user}:${password}`, "latin1").toString("base64");
  assert.equal(header, `Basic ${utf8}`);
  assert.notEqual(utf8, latin1);
});
