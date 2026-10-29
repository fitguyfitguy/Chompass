// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import { normalizeBarcodeCode } from "../chompass-core/barcode-code.js";
import { loadParityFixture } from "../parity-fixtures.js";

test("normalizeBarcodeCode_matchesSharedFixture", () => {
  const fixture = loadParityFixture("barcode-codes.json");
  for (const c of fixture.cases) {
    assert.equal(normalizeBarcodeCode(c.raw), c.expected, `raw=${JSON.stringify(c.raw)}`);
  }
});
