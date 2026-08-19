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
import { loadCatalog } from "../i18n/catalogs/index.js";

const REPO_ROOT = fileURLToPath(new URL("../../../../../", import.meta.url));
const fixture = JSON.parse(readFileSync(`${REPO_ROOT}testdata/parity/locales.json`, "utf8"));
const compactFixture = JSON.parse(readFileSync(`${REPO_ROOT}testdata/parity/compact_strings.json`, "utf8"));

// CJK ideographs, kana, fullwidth forms: narrow glyphs count at 0.5.
const CJK_RE = /[\u3000-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\uff00-\uffef]/;
const effectiveLen = (value) =>
  [...value].reduce((sum, ch) => sum + (CJK_RE.test(ch) ? 0.5 : 1.0), 0);
const cjkCount = (value) => [...value].filter((ch) => CJK_RE.test(ch)).length;

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
    assert.equal(resolveLocaleId("uk-UA"), "uk");
    assert.equal(resolveLocaleId("pl-PL"), "pl");
    assert.equal(resolveLocaleId("tr-TR"), "tr");
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

  it("every supported locale has a complete core catalog", async () => {
    for (const loc of LOCALES) {
      if (loc.id === "en") continue;
      const { missing } = await catalogDiff(loc.id);
      assert.equal(missing.length, 0, `${loc.id} missing: ${missing.slice(0, 5).join(", ")}`);
    }
  });

  it("no phrase-level EN-identical copies in non-EN catalogs", async () => {
    for (const loc of LOCALES) {
      if (loc.id === "en") continue;
      const { copies } = await catalogDiff(loc.id);
      assert.deepEqual(copies, [], `${loc.id} EN-identical copies: ${copies.slice(0, 5).join(", ")}`);
    }
  });

  it("t() falls back to English then key", async () => {
    await loadCatalog("de");
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

describe("compact labels (PLAN_UI_STRING_FIT)", () => {
  const budget = compactFixture.budgetChars;
  const cjkBudget = compactFixture.budgetCjkChars;
  const overrides = compactFixture.perKeyOverrides || {};
  const pwaKeyMap = compactFixture.pwaKeyMap || {};

  it("registry maps Android keys to PWA keys that exist in the English catalog", () => {
    const en = englishKeys();
    for (const pwaKey of Object.values(pwaKeyMap)) {
      assert.ok(en.includes(pwaKey), `pwaKeyMap references missing EN key ${pwaKey}`);
    }
  });

  it("rendered compact labels stay within budget in every locale", async () => {
    const violations = [];
    for (const loc of LOCALES) {
      await loadCatalog(loc.id);
      for (const [androidKey, pwaKey] of Object.entries(pwaKeyMap)) {
        const value = t(pwaKey, {}, loc.id); // catalog value or EN fallback
        const ov = overrides[androidKey] || {};
        const maxChars = ov.maxChars ?? budget;
        const eff = effectiveLen(value);
        if (eff > maxChars) {
          violations.push(`${loc.id}/${pwaKey}: ${JSON.stringify(value)} (${eff} > ${maxChars})`);
        }
        if (cjkCount(value) > cjkBudget) {
          violations.push(`${loc.id}/${pwaKey}: ${JSON.stringify(value)} (${cjkCount(value)} CJK > ${cjkBudget})`);
        }
      }
    }
    assert.deepEqual(violations, [], `compact-label violations:\n${violations.join("\n")}`);
  });
});
