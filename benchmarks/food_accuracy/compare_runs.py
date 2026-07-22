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
    parser.add_argument("candidate", type=Path, help="Candidate summary.csv")
    args = parser.parse_args()

    base = load_summary(args.baseline)
    cand = load_summary(args.candidate)

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
    print(f"candidate: {args.candidate}\n")
    for key in keys:
        b = base.get(key, "")
        c = cand.get(key, "")
        print(f"{key:28}  baseline={b!s:>10}  candidate={c!s:>10}")

    try:
        bw = float(base.get("wmape") or "nan")
        cw = float(cand.get("wmape") or "nan")
        if bw == bw and cw == cw:  # not NaN
            delta = cw - bw
            winner = "candidate" if delta < 0 else "baseline" if delta > 0 else "tie"
            print(f"\nwmape delta (candidate - baseline): {delta:+.4f}  → lower is better ({winner})")
    except ValueError:
        pass


if __name__ == "__main__":
    main()
