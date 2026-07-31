// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";
import { normalizeWebDavUrl } from "../sync.js";

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
