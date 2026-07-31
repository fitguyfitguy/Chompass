#!/usr/bin/env python3
"""Validate testdata/parity/locales.json against PWA LOCALES + Android values-* dirs."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCALES_JSON = ROOT / "testdata" / "parity" / "locales.json"
PWA_LOCALES = ROOT / "web" / "app" / "src" / "lib" / "i18n" / "locales.js"
RES = ROOT / "android" / "app" / "src" / "main" / "res"


def main() -> int:
    data = json.loads(LOCALES_JSON.read_text(encoding="utf-8"))
    ids = [loc["id"] for loc in data["locales"]]
    if len(ids) != len(set(ids)):
        print("duplicate locale ids", file=sys.stderr)
        return 1
    if data.get("fallbackLocale") != "en" or "en" not in ids:
        print("fallback must be en", file=sys.stderr)
        return 1

    js = PWA_LOCALES.read_text(encoding="utf-8")
    for lid in ids:
        if f'id: "{lid}"' not in js and f"id: '{lid}'" not in js:
            print(f"PWA locales.js missing id {lid}", file=sys.stderr)
            return 1

    for loc in data["locales"]:
        folder = RES / loc["androidValues"]
        if not (folder / "strings.xml").is_file():
            print(f"missing Android {folder}/strings.xml", file=sys.stderr)
            return 1

    # RTL flag consistency
    for loc in data["locales"]:
        if loc["id"] == "ar" and not loc.get("rtl"):
            print("ar must be rtl", file=sys.stderr)
            return 1
        if loc["id"] != "ar" and loc.get("rtl"):
            print(f"unexpected rtl for {loc['id']}", file=sys.stderr)
            return 1

    print(f"locales contract OK ({len(ids)} locales)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
