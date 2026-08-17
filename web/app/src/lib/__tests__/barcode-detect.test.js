// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import {
  FORMATS,
  ZXING_FORMAT_MAP,
  chooseStrategy,
  ean13Modules,
  pickNormalizable,
  pickPreferredCode,
  shouldDemoteNative,
  NATIVE_FAILURE_LIMIT,
  NATIVE_EMPTY_DEMOTE_MS,
} from "../barcode-detect.js";

test("chooseStrategy_nativeOnlyWhenProbedOk", () => {
  assert.equal(chooseStrategy({ hasNative: true, probeResult: "ok" }), "native");
  assert.equal(chooseStrategy({ hasNative: true, probeResult: "broken" }), "wasm");
  assert.equal(chooseStrategy({ hasNative: false, probeResult: "ok" }), "wasm");
  assert.equal(chooseStrategy({ hasNative: false, probeResult: "broken" }), "wasm");
});

test("shouldDemoteNative_onThrowStreak", () => {
  assert.equal(shouldDemoteNative({ emptyMs: 0, throwCount: NATIVE_FAILURE_LIMIT - 1 }), false);
  assert.equal(shouldDemoteNative({ emptyMs: 0, throwCount: NATIVE_FAILURE_LIMIT }), true);
});

test("shouldDemoteNative_onSustainedEmpty", () => {
  assert.equal(shouldDemoteNative({ emptyMs: NATIVE_EMPTY_DEMOTE_MS - 1, throwCount: 0 }), false);
  assert.equal(shouldDemoteNative({ emptyMs: NATIVE_EMPTY_DEMOTE_MS, throwCount: 0 }), true);
  assert.equal(shouldDemoteNative({ emptyMs: NATIVE_EMPTY_DEMOTE_MS + 500, throwCount: 0 }), true);
});

test("shouldDemoteNative_respectsCustomLimits", () => {
  assert.equal(shouldDemoteNative({ emptyMs: 100, throwCount: 2, emptyLimitMs: 50, throwLimit: 10 }), true);
  assert.equal(shouldDemoteNative({ emptyMs: 10, throwCount: 5, emptyLimitMs: 100, throwLimit: 5 }), true);
  assert.equal(shouldDemoteNative({ emptyMs: 10, throwCount: 4, emptyLimitMs: 100, throwLimit: 5 }), false);
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

test("pickNormalizable_junkOnlyReturnsNull", () => {
  // Regression for the Bolognese repro (#24 follow-up): a frame with only
  // non-normalizable decodes used to stop the scan with a lookup error.
  assert.equal(pickNormalizable(["1111201I", "https://brand.example.com/recipes"]), null);
});

test("pickNormalizable_prefersNormalizableOverJunk", () => {
  // Jar case: EAN-13 + internal DataMatrix in the same frame.
  assert.equal(pickNormalizable(["1111201I", "9339687206605"]), "9339687206605");
  assert.equal(pickNormalizable(["9339687206605", "1111201I"]), "9339687206605");
});

test("pickNormalizable_gs1PrefixedReturnsRawText", () => {
  // The raw decoded text is passed on; lookupBarcode re-normalizes (idempotent).
  const raw = "(01)09400597028233(15)260821(10)96735717";
  assert.equal(pickNormalizable([raw]), raw);
});

test("pickNormalizable_firstNormalizableWins", () => {
  assert.equal(pickNormalizable(["9339687206605", "9421011990608"]), "9339687206605");
});

test("pickNormalizable_emptyReturnsNull", () => {
  assert.equal(pickNormalizable([]), null);
  assert.equal(pickNormalizable(["  "]), null);
});

test("pickPreferredCode_mixedFramePrefersEanOverQr", () => {
  // Jar case (#24 follow-up): a GS1 Digital Link QR (case-level GTIN) and the
  // EAN-13 in the same frame — the retail 1D code is the one OFF indexes.
  const qr = { text: "https://id.gs1.org/01/19300645111122", format: "QRCode" };
  const ean = { text: "9300645111125", format: "EAN13" };
  assert.equal(pickPreferredCode([qr, ean]), "9300645111125");
  assert.equal(pickPreferredCode([ean, qr]), "9300645111125");
});

test("pickPreferredCode_qrOnlyFrameUsesQrGtin", () => {
  // 2D-only frames keep working (e.g. the mince DataMatrix from #24). The raw
  // GS1 text is returned; lookupBarcode normalizes it to the GTIN.
  const raw = "https://id.gs1.org/01/19300645111122";
  assert.equal(pickPreferredCode([{ text: raw, format: "QRCode" }]), raw);
});

test("pickPreferredCode_junkPlusEanPrefersEan", () => {
  // Regression from the 3.16.0 fix: junk-only frames still return null.
  assert.equal(
    pickPreferredCode([
      { text: "1111201I", format: "DataMatrix" },
      { text: "9300645111125", format: "EAN13" },
    ]),
    "9300645111125"
  );
  assert.equal(pickPreferredCode([{ text: "1111201I", format: "DataMatrix" }]), null);
});

test("pickPreferredCode_twoEansFirstWins", () => {
  // Order preserved within a tier.
  assert.equal(
    pickPreferredCode([
      { text: "9339687206605", format: "EAN13" },
      { text: "9421011990608", format: "EAN13" },
    ]),
    "9339687206605"
  );
});

test("pickPreferredCode_nativeFormatNames", () => {
  // Native BarcodeDetector uses snake_case format names; both spellings work.
  assert.equal(
    pickPreferredCode([
      { text: "https://id.gs1.org/01/19300645111122", format: "qr_code" },
      { text: "9300645111125", format: "ean_13" },
    ]),
    "9300645111125"
  );
});

test("detectFromBlob_emptyReturnsNull", async () => {
  const { detectFromBlob } = await import("../barcode-detect.js");
  assert.equal(await detectFromBlob(new Blob([])), null);
});
