#!/usr/bin/env python3
"""Compare two eval summary CSV files (prompt/model A/B)."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path


def load_summary(path: Path) -> dict[str, str]:
    with path.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        raise ValueError(f"No rows in {path}")
    return rows[0]


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare food accuracy eval summaries")
    parser.add_argument("baseline", type=Path, help="Baseline summary.csv")
    parser.add_argument("candidates", type=Path, nargs="+", help="Candidate summary.csv file(s)")
    args = parser.parse_args()

    base = load_summary(args.baseline)
    cands = [(path, load_summary(path)) for path in args.candidates]

    keys = [
        "wmape",
        "parse_ok_rate",
        "mae_calories",
        "within_20pct_calories_rate",
        "sum_prompt_tokens",
        "sum_completion_tokens",
        "sum_cached_tokens",
        "mean_prompt_tokens",
        "mean_completion_tokens",
        "cache_hit_rate",
        "sum_cost",
    ]
    print(f"baseline:  {args.baseline}")
    for idx, (path, _) in enumerate(cands, start=1):
        print(f"cand[{idx}]:   {path}")
    print()
    for key in keys:
        row = f"{key:28}  baseline={base.get(key, '')!s:>10}"
        for _, cand in cands:
            row += f"  {cand.get(key, '')!s:>10}"
        print(row)

    try:
        bw = float(base.get("wmape") or "nan")
    except ValueError:
        return
    if bw != bw:  # NaN
        return
    print()
    for idx, (path, cand) in enumerate(cands, start=1):
        try:
            cw = float(cand.get("wmape") or "nan")
        except ValueError:
            continue
        if cw != cw:
            continue
        delta = cw - bw
        winner = "candidate" if delta < 0 else "baseline" if delta > 0 else "tie"
        print(f"wmape delta cand[{idx}] - baseline: {delta:+.4f}  → lower is better ({winner})")


if __name__ == "__main__":
    main()
