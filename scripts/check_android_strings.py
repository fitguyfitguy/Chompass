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
RES = ROOT / "android" / "app" / "src" / "main" / "res"
PLACEHOLDER_RE = re.compile(r"%(\d+\$)?[sdif]")


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
        "--max-missing",
        type=int,
        default=None,
        help="Fail if any locale exceeds this missing-key count",
    )
    args = ap.parse_args()

    contract = json.loads(LOCALES_JSON.read_text(encoding="utf-8"))
    en = load_strings(RES / "values" / "strings.xml")
    print(f"English keys: {len(en)}")

    ph_errors = 0
    missing_report: list[tuple[str, int]] = []
    copy_report: list[tuple[str, list[str]]] = []

    def is_neutral_copy(value: str) -> bool:
        """Format strings, URLs, and bare units/keys are legitimately identical."""
        return "%" in value or value.startswith("http") or len(value) <= 3

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
            if k in en and text == en[k] and not is_neutral_copy(text)
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

    for lid, n in missing_report:
        print(f"  {lid}: {n} missing keys (fallback to EN)")
    for lid, copies in copy_report:
        print(f"  {lid}: {len(copies)} EN-identical copy/copies: {copies[:5]}", file=sys.stderr)

    if ph_errors:
        print(f"{ph_errors} placeholder mismatch(es)", file=sys.stderr)
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
