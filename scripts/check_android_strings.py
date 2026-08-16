#!/usr/bin/env python3
"""Validate Android string resources against the shared locale contract.

Checks:
  - Every locale in testdata/parity/locales.json has a values-* directory
  - Placeholders (%1$s / %d / etc.) in translations match English when present
  - Reports missing keys (informational by default; --strict fails on any missing)

Exit 0 unless --strict and missing keys exist, or placeholders mismatch.
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
PLACEHOLDER_RE = re.compile(r"%(\d+\$)?[sdif]")
# CJK ideographs, kana, fullwidth forms: narrow glyphs count at 0.5.
CJK_RE = re.compile(r"[\u3000-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\uff00-\uffef]")


def effective_len(value: str) -> float:
    """Latin chars count 1.0, CJK glyphs 0.5 (they are narrow)."""
    return sum(0.5 if CJK_RE.match(ch) else 1.0 for ch in value)


def cjk_count(value: str) -> int:
    return sum(1 for ch in value if CJK_RE.match(ch))


def load_strings(path: Path) -> dict[str, str]:
    tree = ET.parse(path)
    out: dict[str, str] = {}
    for el in tree.getroot():
        if el.tag != "string":
            continue
        name = el.attrib.get("name")
        if not name:
            continue
        out[name] = "".join(el.itertext())
    return out


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

    contract = json.loads(LOCALES_JSON.read_text(encoding="utf-8"))
    en = load_strings(RES / "values" / "strings.xml")
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

    def load_copy_exemptions() -> tuple[set[str], dict[str, set[str]]]:
        """Curated per-locale exemption values (see testdata/parity/copy_exemptions.json)."""
        data = json.loads(COPY_EXEMPTIONS_JSON.read_text(encoding="utf-8"))
        shared = set(data.get("shared", []))
        per_locale = {
            k: set(v) for k, v in data.items() if k not in ("version", "notes", "shared")
        }
        return shared, per_locale

    shared_exempt, locale_exempt = load_copy_exemptions()

    def is_exempt_copy(locale_id: str, value: str) -> bool:
        return (
            is_neutral_copy(value)
            or value in shared_exempt
            or value in locale_exempt.get(locale_id, set())
        )

    for loc in contract["locales"]:
        folder = loc["androidValues"]
        path = RES / folder / "strings.xml"
        if not path.is_file():
            print(f"MISSING directory/file for {loc['id']}: {path}", file=sys.stderr)
            return 1
        if folder == "values":
            continue
        strings = load_strings(path)
        missing = sorted(k for k in en if k not in strings)
        missing_report.append((loc["id"], len(missing)))
        # Verbatim English copies: the key is "present" but adds nothing over the
        # EN fallback, hides the real gap, and blocks translators. Formats, URLs,
        # and bare units are language-neutral and allowed.
        copies = sorted(
            k for k, text in strings.items()
            if k in en and text == en[k] and not is_exempt_copy(loc["id"], text)
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

    print("Android string check OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
