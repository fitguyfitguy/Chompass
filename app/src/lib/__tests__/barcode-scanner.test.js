// @ts-check
import { test } from "node:test";
import assert from "node:assert/strict";

// The component module defines its custom element at import time; node tests
// run without a DOM, so stub the two globals it touches and exercise the pure
// not-found status builder (the anchor assembly in renderNotFoundStatus is
// six lines of DOM on top of this data).
globalThis.HTMLElement = class HTMLElement {};
globalThis.customElements = { define() {} };

const { notFoundStatus } = await import("../../components/barcode-scanner.js");

test("notFoundStatus_linksThePinnedOffAddForm", () => {
  const nf = notFoundStatus("4999999999996");
  assert.equal(
    nf.linkHref,
    "https://world.openfoodfacts.org/cgi/product.pl?code=4999999999996"
  );
  assert.equal(nf.linkText, "Add to Open Food Facts");
  assert.ok(nf.text.includes("No product found for 4999999999996"));
});

test("notFoundStatus_encodesUntrustedDecodedText", () => {
  const nf = notFoundStatus('12"&/<script>');
  assert.equal(
    nf.linkHref,
    "https://world.openfoodfacts.org/cgi/product.pl?code=12%22%26%2F%3Cscript%3E"
  );
});
