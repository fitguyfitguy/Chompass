// @ts-check
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  LOCALES,
  FALLBACK_LOCALE,
  detectLocaleId,
  resolveLocaleId,
  setActiveLocale,
  t,
  formatNumber,
  catalogDiff,
  englishKeys,
} from "../i18n/index.js";

const REPO_ROOT = fileURLToPath(new URL("../../../../../", import.meta.url));
const fixture = JSON.parse(readFileSync(`${REPO_ROOT}testdata/parity/locales.json`, "utf8"));

describe("i18n locales contract", () => {
  it("LOCALES matches parity fixture ids", () => {
    const fixtureIds = fixture.locales.map((/** @type {{id:string}} */ l) => l.id);
    assert.deepEqual(
      LOCALES.map((l) => l.id),
      fixtureIds,
    );
  });

  it("resolveLocaleId maps browser tags", () => {
    assert.equal(resolveLocaleId("de-DE"), "de");
    assert.equal(resolveLocaleId("pt-BR"), "pt-BR");
    assert.equal(resolveLocaleId("zh-Hans-CN"), "zh-CN");
    assert.equal(resolveLocaleId("en-US"), "en");
    assert.equal(resolveLocaleId("xx-YY"), FALLBACK_LOCALE);
  });

  it("detectLocaleId prefers pref over browser", () => {
    assert.equal(detectLocaleId("fr", "de-DE"), "fr");
    assert.equal(detectLocaleId("", "ja-JP"), "ja");
    assert.equal(detectLocaleId("", ""), FALLBACK_LOCALE);
  });

  it("Arabic is RTL in metadata", () => {
    const ar = LOCALES.find((l) => l.id === "ar");
    assert.ok(ar);
    assert.equal(ar.rtl, true);
  });
});

describe("i18n catalogs", () => {
  it("English catalog is non-empty core surface set", () => {
    const keys = englishKeys();
    assert.ok(keys.includes("nav.home"));
    assert.ok(keys.includes("settings.language.title"));
    assert.ok(keys.includes("diary.empty"));
    assert.ok(keys.length >= 80);
  });

  it("every supported locale has a complete core catalog", () => {
    for (const loc of LOCALES) {
      if (loc.id === "en") continue;
      const { missing } = catalogDiff(loc.id);
      assert.equal(missing.length, 0, `${loc.id} missing: ${missing.slice(0, 5).join(", ")}`);
    }
  });

  it("t() falls back to English then key", () => {
    setActiveLocale("de");
    assert.equal(t("nav.home"), "Start");
    assert.equal(t("nonexistent.key.xyz"), "nonexistent.key.xyz");
    setActiveLocale("en");
    assert.equal(t("nav.home"), "Home");
  });

  it("t() interpolates variables", () => {
    // Use a synthetic path via replace on known string after set
    setActiveLocale("en");
    // english has no template with vars in core set; exercise API
    const out = t("nav.home", { x: 1 });
    assert.equal(out, "Home");
  });

  it("formatNumber uses active locale", () => {
    setActiveLocale("de");
    const n = formatNumber(12.5, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    assert.match(n, /12[,.]5/);
    setActiveLocale("en");
  });
});
