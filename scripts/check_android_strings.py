#!/usr/bin/env python3
"""Validate Android string resources against the shared locale contract.

Checks:
  - Every locale in testdata/parity/locales.json has a values-* directory
  - Placeholders (%1$s / %d / etc., incl. width/precision like %1$.2f) in
    translations match English when present
  - Plural quantities: every <plurals> carries the CLDR-required quantities for
    its language (ar 6 forms; ru/uk/pl 4; ro 3; ja/ko/zh other-only; default 2)
  - Untranslated copies: <string>/<plurals> verbatim-EN values, and
    <string-array> item lists that mirror EN while holding translatable items
  - Reports missing keys (informational by default; --strict fails on any missing)

Exit 0 unless --strict and missing keys exist, placeholders mismatch, plural
quantities are incomplete, or untranslated copies remain.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LOCALES_JSON = ROOT / "testdata" / "parity" / "locales.json"
COPY_EXEMPTIONS_JSON = ROOT / "testdata" / "parity" / "copy_exemptions.json"
COMPACT_JSON = ROOT / "testdata" / "parity" / "compact_strings.json"
RES = ROOT / "android" / "app" / "src" / "main" / "res"
# Width/precision variants (%1$.2f, %02d) must participate in the equality
# check; %BF-style prose must not match (letter B is outside the class).
PLACEHOLDER_RE = re.compile(r"%(\d+\$)?(?:\d+)?(?:\.\d+)?[sdfi]")
# CLDR-required plural quantities by primary language subtag. Extra quantities
# are allowed; a missing one is a hard failure.
PLURAL_REQUIRED = {
    "ar": ("zero", "one", "two", "few", "many", "other"),
    "ru": ("one", "few", "many", "other"),
    "uk": ("one", "few", "many", "other"),
    "pl": ("one", "few", "many", "other"),
    "ro": ("one", "few", "other"),
    "ja": ("other",),
    "ko": ("other",),
    "zh": ("other",),
}


def required_quantities(locale_id: str) -> tuple[str, ...]:
    return PLURAL_REQUIRED.get(locale_id.split("-")[0], ("one", "other"))
# CJK ideographs, kana, fullwidth forms: narrow glyphs count at 0.5.
CJK_RE = re.compile(r"[\u3000-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\uff00-\uffef]")


def effective_len(value: str) -> float:
    """Latin chars count 1.0, CJK glyphs 0.5 (they are narrow)."""
    return sum(0.5 if CJK_RE.match(ch) else 1.0 for ch in value)


def cjk_count(value: str) -> int:
    return sum(1 for ch in value if CJK_RE.match(ch))


def load_strings(path: Path) -> tuple[dict[str, str], dict[str, list[str]], dict[str, set[str]]]:
    """Load <string>, <plurals> AND <string-array> keys.

    Returns (texts, array_items, plural_quantities): plurals concatenate their
    quantity items and string-arrays join item texts with newlines, so the
    missing/copy/placeholder checks cover every element type. array_items keeps
    each array's per-item list for the EN-copy rule; plural_quantities maps
    each <plurals> to its quantity tokens for the CLDR gate.
    """
    tree = ET.parse(path)
    texts: dict[str, str] = {}
    array_items: dict[str, list[str]] = {}
    plural_quantities: dict[str, set[str]] = {}
    for el in tree.getroot():
        if el.tag not in ("string", "plurals", "string-array"):
            continue
        name = el.attrib.get("name")
        if not name:
            continue
        if el.tag == "string-array":
            items = ["".join(item.itertext()) for item in el]
            array_items[name] = items
            texts[name] = "\n".join(items)
        else:
            texts[name] = "".join(el.itertext())
            if el.tag == "plurals":
                plural_quantities[name] = {
                    item.attrib.get("quantity") for item in el
                }
    return texts, array_items, plural_quantities


def ph_set(text: str) -> set[str]:
    """Numbered / typed format args only — ignore literal '%%' percent signs."""
    return set(m.group(0) for m in PLACEHOLDER_RE.finditer(text.replace("%%", "")))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="Fail if any locale is missing keys")
    ap.add_argument(
        "--strict-compact",
        action="store_true",
        help="Fail on compact-label violations (length / placeholder position); missing keys stay informational",
    )
    ap.add_argument(
        "--max-missing",
        type=int,
        default=None,
        help="Fail if any locale exceeds this missing-key count",
    )
    args = ap.parse_args()

    plural_gaps = 0
    contract = json.loads(LOCALES_JSON.read_text(encoding="utf-8"))
    en, en_arrays, en_plural_qs = load_strings(RES / "values" / "strings.xml")
    for name, qs in en_plural_qs.items():
        missing_q = sorted(set(required_quantities("en")) - qs)
        if missing_q:
            print(f"plural gap en/{name}: missing {missing_q}", file=sys.stderr)
            plural_gaps += 1
    print(f"English keys: {len(en)}")

    compact = json.loads(COMPACT_JSON.read_text(encoding="utf-8"))
    budget = compact["budgetChars"]
    cjk_budget = compact["budgetCjkChars"]
    compact_keys = set(compact["keys"])
    compact_overrides = compact.get("perKeyOverrides", {})

    ph_errors = 0
    missing_report: list[tuple[str, int]] = []
    copy_report: list[tuple[str, list[str]]] = []
    compact_violations: list[tuple[str, str, str, str]] = []
    compact_missing: list[tuple[str, str]] = []
    compact_rendered_warnings: list[tuple[str, str, str]] = []

    def is_neutral_copy(value: str) -> bool:
        """Format strings, URLs, and bare units/keys are legitimately identical."""
        return "%" in value or value.startswith("http") or len(value) <= 3

    def load_copy_exemptions() -> dict[str, set[str]]:
        """Key-scoped EN-copy exemptions (testdata/parity/copy_exemptions.json v2).

        A locale pack's value for the key is exempt iff the entry lists the
        locale id or "*". Fails fast on an unexpected schema version."""
        data = json.loads(COPY_EXEMPTIONS_JSON.read_text(encoding="utf-8"))
        version = data.get("version")
        if version != 2:
            raise SystemExit(
                f"copy_exemptions.json: expected version 2 (key-scoped entries), got {version!r}"
            )
        return {e["key"]: set(e["locales"]) for e in data.get("entries", [])}

    copy_exemptions = load_copy_exemptions()

    def is_exempt_copy(locale_id: str, key: str, value: str) -> bool:
        if is_neutral_copy(value):
            return True
        locales = copy_exemptions.get(key, ())
        return locale_id in locales or "*" in locales

    for loc in contract["locales"]:
        folder = loc["androidValues"]
        path = RES / folder / "strings.xml"
        if not path.is_file():
            print(f"MISSING directory/file for {loc['id']}: {path}", file=sys.stderr)
            return 1
        if folder == "values":
            continue
        strings, arrays, plural_qs = load_strings(path)
        missing = sorted(k for k in en if k not in strings)
        missing_report.append((loc["id"], len(missing)))
        # CLDR plural quantities: getQuantityText falls through to <item
        # quantity="other"> when a form is absent, which renders wrong text for
        # whole numeric ranges — so the language's required set must be present.
        for name, qs in plural_qs.items():
            missing_q = sorted(set(required_quantities(loc["id"])) - qs)
            if missing_q:
                print(
                    f"plural gap {loc['id']}/{name}: missing {missing_q}",
                    file=sys.stderr,
                )
                plural_gaps += 1
        # Verbatim English copies: the key is "present" but adds nothing over the
        # EN fallback, hides the real gap, and blocks translators. Formats, URLs,
        # and bare units are language-neutral and allowed.
        copies = sorted(
            k for k, text in strings.items()
            if k not in arrays
            and k in en and text == en[k] and not is_exempt_copy(loc["id"], k, text)
        )
        # Arrays are search keyword lists (never displayed): flag only when the
        # item list mirrors EN item-for-item AND holds at least one translatable
        # item (brand tokens like gemini/openai/bmr are neutral and legit).
        copies += sorted(
            name for name, items in arrays.items()
            if name in en_arrays and items == en_arrays[name]
            and any(not is_neutral_copy(item) for item in items)
            and not is_exempt_copy(loc["id"], name, "\n".join(items))
        )
        if copies:
            copy_report.append((loc["id"], copies))
        for name, text in strings.items():
            if name not in en:
                continue
            if ph_set(text) != ph_set(en[name]):
                print(
                    f"placeholder mismatch {loc['id']}/{name}: "
                    f"{sorted(ph_set(text))} vs EN {sorted(ph_set(en[name]))}",
                    file=sys.stderr,
                )
                ph_errors += 1
        # Compact-label gate (testdata/parity/compact_strings.json): fixed-width
        # chips/tabs/status lines/buttons must stay within budget. Warnings by
        # default; --strict / --strict-compact fail (release checklist).
        for name in compact_keys:
            if name not in en:
                continue  # registry key not defined in EN at all
            if name not in strings:
                compact_missing.append((loc["id"], name))  # EN fallback: fine, report
                continue
            text = strings[name]
            ov = compact_overrides.get(name, {})
            max_chars = ov.get("maxChars", budget)
            eff = effective_len(text)
            if eff > max_chars:
                compact_violations.append(
                    (loc["id"], name, "length", f"{text!r} ({eff:.1f} > {max_chars})")
                )
            if cjk_count(text) > cjk_budget:
                compact_violations.append(
                    (loc["id"], name, "cjk-length", f"{text!r} ({cjk_count(text)} CJK > {cjk_budget})")
                )
            if ov.get("valueFirst") and text.lstrip():
                m = PLACEHOLDER_RE.search(text.lstrip())
                if m and m.start() != 0:
                    compact_violations.append(
                        (loc["id"], name, "valueFirst", f"{text!r} puts the word before the value")
                    )
            # Rendered worst-case check for placeholder keys (macro status lines):
            # the value + unit + suffix must fit the card at max font scale.
            # Warning only — MacroCard auto-sizes the status line, so this is a
            # translator tripwire ("consider a shorter suffix"), not a hard gate.
            if "%1$s" in text and "%2$s" in text:
                rendered = PLACEHOLDER_RE.sub("", text.replace("%1$s", "91,5").replace("%2$s", "g"))
                if effective_len(rendered) > 13:
                    compact_rendered_warnings.append(
                        (loc["id"], name, f"{rendered!r} ({effective_len(rendered):.1f} > 13)")
                    )

    for lid, n in missing_report:
        print(f"  {lid}: {n} missing keys (fallback to EN)")
    for lid, copies in copy_report:
        print(f"  {lid}: {len(copies)} EN-identical copy/copies: {copies[:5]}", file=sys.stderr)

    if compact_missing:
        by_loc: dict[str, list[str]] = {}
        for lid, name in compact_missing:
            by_loc.setdefault(lid, []).append(name)
        for lid, names in sorted(by_loc.items()):
            print(f"  compact: {lid} falls back to EN for {len(names)} key(s): {names[:5]}")
    for lid, name, kind, detail in compact_violations:
        print(f"compact {kind} {lid}/{name}: {detail}", file=sys.stderr)
    for lid, name, detail in compact_rendered_warnings:
        print(f"compact rendered-warning {lid}/{name}: {detail}", file=sys.stderr)

    if ph_errors:
        print(f"{ph_errors} placeholder mismatch(es)", file=sys.stderr)
        return 1

    if plural_gaps:
        print(
            f"{plural_gaps} plural quantity gap(s) — add the missing "
            "CLDR-required <item quantity> forms",
            file=sys.stderr,
        )
        return 1

    if compact_violations:
        print(
            f"{len(compact_violations)} compact-label violation(s) "
            "(see testdata/parity/compact_strings.json; --strict-compact fails)",
            file=sys.stderr,
        )
        if args.strict or args.strict_compact:
            return 1

    if copy_report:
        print("EN-identical copies present (translate or delete them)", file=sys.stderr)
        return 1

    if args.strict and any(n > 0 for _, n in missing_report):
        print("--strict: missing translation keys", file=sys.stderr)
        return 1

    if args.max_missing is not None:
        bad = [(lid, n) for lid, n in missing_report if n > args.max_missing]
        if bad:
            print(f"locales over --max-missing {args.max_missing}: {bad}", file=sys.stderr)
            return 1

    # Per-locale caps from the parity contract (locales.json maxMissing):
    # complete locales (de/es/fr) must stay at 0; the rest carry a cap set to
    # the current debt + a small buffer, so adding English strings without
    # translations fails the parity gate instead of silently growing the gap.
    # Bump caps down as translations land; bump up only when English grows.
    over_cap = []
    for loc in contract["locales"]:
        if loc["id"] == "en":
            continue
        cap = loc.get("maxMissing")
        if cap is None:
            continue
        n = next((n for lid, n in missing_report if lid == loc["id"]), 0)
        if n > cap:
            over_cap.append((loc["id"], n, cap))
    if over_cap:
        for lid, n, cap in over_cap:
            print(
                f"{lid}: {n} missing keys exceeds contract cap {cap} "
                "(translate new strings or bump maxMissing in testdata/parity/locales.json)",
                file=sys.stderr,
            )
        return 1

    print("Android string check OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
