#!/usr/bin/env python3
"""Per-slice breakdown for eval_grounded_realistic_text runs.

Reads grounded and/or ungrounded samples.jsonl (+ optional summary.csv) and prints
JSON with overall + per-slice metrics.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from schema import load_manifest


def _load_samples(path: Path) -> dict[str, dict]:
    rows: dict[str, dict] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        rows[str(row["id"])] = row
    return rows


def _wmape_from_scores(rows: list[dict]) -> float | None:
    abs_sum = 0.0
    gt_sum = 0.0
    n = 0
    for row in rows:
        sc = row.get("score") or {}
        if not row.get("parse_ok"):
            continue
        a = sc.get("abs_error_sum")
        g = sc.get("gt_sum")
        if a is None or g is None or g <= 0:
            continue
        abs_sum += float(a)
        gt_sum += float(g)
        n += 1
    if n == 0 or gt_sum <= 0:
        return None
    return abs_sum / gt_sum


def _within20(rows: list[dict]) -> float | None:
    vals = []
    for row in rows:
        sc = row.get("score") or {}
        if not row.get("parse_ok"):
            continue
        w = sc.get("within_20pct_calories")
        if w is None:
            continue
        vals.append(1.0 if w else 0.0)
    if not vals:
        return None
    return sum(vals) / len(vals)


def _parse_rate(rows: list[dict]) -> float:
    if not rows:
        return 0.0
    return sum(1 for r in rows if r.get("parse_ok")) / len(rows)


def summarize(rows: list[dict], slice_by_id: dict[str, str]) -> dict:
    by_slice: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        sid = str(row.get("id"))
        sl = (
            (row.get("grounded") or {}).get("slice")
            or slice_by_id.get(sid)
            or "unknown"
        )
        by_slice[str(sl)].append(row)

    def block(items: list[dict]) -> dict:
        return {
            "n": len(items),
            "parse_ok_rate": round(_parse_rate(items), 4),
            "wmape": (round(w, 4) if (w := _wmape_from_scores(items)) is not None else None),
            "within_20pct_calories_rate": (
                round(w, 4) if (w := _within20(items)) is not None else None
            ),
            "off_source_rate": round(
                sum(
                    1
                    for r in items
                    if (r.get("grounded") or {}).get("source_kind") == "openFoodFacts"
                )
                / len(items),
                4,
            )
            if items
            else 0.0,
        }

    out = {"overall": block(rows), "by_slice": {}}
    for sl in sorted(by_slice.keys()):
        out["by_slice"][sl] = block(by_slice[sl])
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--manifest",
        type=Path,
        default=_HERE / "manifest" / "eval_grounded_realistic_text.jsonl",
    )
    parser.add_argument("--grounded", type=Path, required=True, help="grounded samples.jsonl or run dir")
    parser.add_argument("--ungrounded", type=Path, default=None, help="ungrounded samples.jsonl or run dir")
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    def resolve(p: Path) -> Path:
        if p.is_dir():
            return p / "samples.jsonl"
        return p

    slice_by_id = {
        s.id: str(s.extra.get("slice") or "unknown") for s in load_manifest(args.manifest)
    }
    grounded_rows = list(_load_samples(resolve(args.grounded)).values())
    report = {
        "manifest": str(args.manifest),
        "grounded": summarize(grounded_rows, slice_by_id),
    }
    if args.ungrounded:
        ungrounded_rows = list(_load_samples(resolve(args.ungrounded)).values())
        report["ungrounded"] = summarize(ungrounded_rows, slice_by_id)

    text = json.dumps(report, indent=2)
    print(text)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
