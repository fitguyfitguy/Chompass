#!/usr/bin/env python3
"""Structural gate for docs/PARITY.md feature matrix.

Does not prove code paths exist — only that the living table stays present,
uses known Status values, and keeps required surface rows.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PARITY_MD = ROOT / "docs" / "PARITY.md"

ALLOWED_STATUS = {
    "shared",
    "android-only",
    "pwa-only",
    "wip",
    "wip / android",
}

REQUIRED_SURFACE_SUBSTRINGS = (
    "AI Coach",
    "Deterministic goal formulas",
    "Diary JSON",
    "Body-metrics",
    "Health Connect",
    "On-device LLM",
    "Grounded entry",
)

ROW_RE = re.compile(
    r"^\|\s*(?P<surface>[^|]+?)\s*\|\s*(?P<status>[^|]+?)\s*\|\s*(?P<android>[^|]*)\|\s*(?P<pwa>[^|]*)\|\s*(?P<notes>[^|]*)\|\s*$"
)


def main() -> int:
    if not PARITY_MD.is_file():
        print(f"missing {PARITY_MD}", file=sys.stderr)
        return 1

    text = PARITY_MD.read_text(encoding="utf-8")
    rows: list[dict[str, str]] = []
    for line in text.splitlines():
        m = ROW_RE.match(line)
        if not m:
            continue
        surface = m.group("surface").strip()
        if surface.lower() == "surface" or set(surface) <= {"-", " "}:
            continue
        status = m.group("status").strip()
        rows.append({"surface": surface, "status": status})

    if not rows:
        print("PARITY.md feature matrix has no data rows", file=sys.stderr)
        return 1

    bad_status = [r for r in rows if r["status"] not in ALLOWED_STATUS]
    if bad_status:
        for r in bad_status:
            print(
                f"unknown Status {r['status']!r} for surface {r['surface']!r}",
                file=sys.stderr,
            )
        print(f"allowed: {sorted(ALLOWED_STATUS)}", file=sys.stderr)
        return 1

    surfaces_blob = "\n".join(r["surface"] for r in rows)
    missing = [s for s in REQUIRED_SURFACE_SUBSTRINGS if s not in surfaces_blob]
    if missing:
        print(
            "PARITY.md matrix missing required surface substring(s): "
            + ", ".join(missing),
            file=sys.stderr,
        )
        return 1

    print(f"PARITY.md matrix OK ({len(rows)} surfaces)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
