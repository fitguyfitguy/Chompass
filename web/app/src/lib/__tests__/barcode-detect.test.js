// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import { FORMATS, ZXING_FORMAT_MAP, chooseStrategy, ean13Modules } from "../barcode-detect.js";

test("chooseStrategy_nativeOnlyWhenProbedOk", () => {
  assert.equal(chooseStrategy({ hasNative: true, probeResult: "ok" }), "native");
  assert.equal(chooseStrategy({ hasNative: true, probeResult: "broken" }), "wasm");
  assert.equal(chooseStrategy({ hasNative: false, probeResult: "ok" }), "wasm");
  assert.equal(chooseStrategy({ hasNative: false, probeResult: "broken" }), "wasm");
});

test("zxingFormatMap_coversAllScannerFormats", () => {
  for (const f of FORMATS) {
    assert.ok(ZXING_FORMAT_MAP[f], `missing zxing mapping for ${f}`);
  }
  assert.deepEqual(Object.keys(ZXING_FORMAT_MAP).sort(), [...FORMATS].sort());
});

test("ean13Modules_structure", () => {
  const m = ean13Modules("4006381333931");
  assert.equal(m.length, 95);
  assert.ok(m.startsWith("101"));
  assert.ok(m.endsWith("101"));
  assert.equal(m.slice(45, 50), "01010"); // middle guard
  // EAN-13 invariant: left-half digit codes start light and end dark;
  // right-half codes start dark and end light.
  for (let i = 0; i < 6; i++) {
    const left = m.slice(3 + i * 7, 3 + (i + 1) * 7);
    assert.equal(left[0], "0");
    assert.equal(left[6], "1");
    const right = m.slice(50 + i * 7, 50 + (i + 1) * 7);
    assert.equal(right[0], "1");
    assert.equal(right[6], "0");
  }
});

test("ean13Modules_knownDigitEncoding", () => {
  // 0000000000000: parity LLLLLL, left digits all L("0")=0001101,
  // right digits all R("0")=1110010.
  const m = ean13Modules("0000000000000");
  assert.equal(m, "101" + "0001101".repeat(6) + "01010" + "1110010".repeat(6) + "101");
});

test("ean13Modules_rejectsInvalidInput", () => {
  assert.throws(() => ean13Modules("123"));
  assert.throws(() => ean13Modules("abcdefghijklm"));
});
