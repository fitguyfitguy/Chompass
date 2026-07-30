#!/usr/bin/env python3
"""Summarize a photo-adjacent entry matrix (L0 / L1 / Lq / bucket) + hard-tail.

Reads run directories that each contain ``summary.json`` and ``samples.jsonl``.
Optional ``--hard-ids`` restricts a second hard-tail row per condition.

Example::

    uv run python docs/benchmarks/food_accuracy/summarize_entry_matrix.py \\
      --label L0=.../jfb_l0_flashlite \\
      --label L1=.../jfb_l1_flashlite \\
      --label Lq=.../jfb_lq_flashlite \\
      --label bucket=.../jfb_bucket_flashlite \\
      --hard-ids docs/benchmarks/food_accuracy/manifest/jfb_hard_ids.txt
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


def load_hard_ids(path: Path | None) -> set[str]:
    if path is None or not path.exists():
        return set()
    ids: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        ids.add(line.split()[0])
    return ids


def summarize_samples(samples_path: Path, hard_ids: set[str]) -> dict:
    rows = [
        json.loads(line)
        for line in samples_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    scored = [r for r in rows if (r.get("score") or {}).get("parse_ok")]
    def wmape_proxy(r: dict) -> float | None:
        sc = r.get("score") or {}
        # Prefer stored wmape if present; else mean of macro MAPEs
        if sc.get("wmape") is not None:
            return float(sc["wmape"])
        mapes = [
            sc[k]
            for k in ("mape_calories", "mape_protein_g", "mape_carbs_g", "mape_fat_g")
            if sc.get(k) is not None
        ]
        if not mapes:
            return None
        return sum(mapes) / len(mapes)

    def within(r: dict) -> bool:
        return bool((r.get("score") or {}).get("within_20pct_calories"))

    def subset_stats(subset: list[dict]) -> dict:
        if not subset:
            return {"n": 0, "wmape": None, "within_20": None, "parse_ok": 0}
        parse_ok = sum(1 for r in subset if (r.get("score") or {}).get("parse_ok"))
        ok = [r for r in subset if (r.get("score") or {}).get("parse_ok")]
        wmapes = [wmape_proxy(r) for r in ok]
        wmapes = [w for w in wmapes if w is not None]
        within_n = sum(1 for r in ok if within(r))
        return {
            "n": len(subset),
            "parse_ok": parse_ok,
            "wmape": (sum(wmapes) / len(wmapes)) if wmapes else None,
            "within_20": (within_n / len(ok)) if ok else None,
        }

    overall = subset_stats(rows)
    hard_rows = [r for r in rows if r.get("id") in hard_ids] if hard_ids else []
    hard = subset_stats(hard_rows) if hard_ids else None
    return {"overall": overall, "hard": hard, "n_rows": len(rows)}


def fmt_pct(x: float | None) -> str:
    if x is None:
        return "—"
    return f"{100.0 * x:.1f}%"


def fmt_wmape(x: float | None) -> str:
    if x is None:
        return "—"
    # samples store MAPE as fraction; summary.json often stores percent string
    if x > 2:  # already percent-like
        return f"{x:.1f}%"
    return f"{100.0 * x:.1f}%"


def load_summary_json(run_dir: Path) -> dict:
    path = run_dir / "summary.json"
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--label",
        action="append",
        default=[],
        help="NAME=run_dir (repeatable). NAME is L0/L1/Lq/bucket/…",
    )
    parser.add_argument(
        "--hard-ids",
        type=Path,
        default=None,
        help="Optional hard-tail ID list (comments allowed)",
    )
    args = parser.parse_args()
    if not args.label:
        raise SystemExit("Pass at least one --label NAME=run_dir")

    hard_ids = load_hard_ids(args.hard_ids)
    print(
        f"{'condition':12} {'n':>4} {'parse':>7} {'WMAPE':>8} {'±20%':>7}"
        + (f"  {'hard_n':>6} {'hard_W':>8} {'hard±20':>7}" if hard_ids else "")
    )
    for spec in args.label:
        if "=" not in spec:
            raise SystemExit(f"Bad --label {spec!r}; expected NAME=run_dir")
        name, path_s = spec.split("=", 1)
        run_dir = Path(path_s)
        samples_path = run_dir / "samples.jsonl"
        if not samples_path.exists():
            print(f"{name:12} MISSING {samples_path}")
            continue
        stats = summarize_samples(samples_path, hard_ids)
        # Prefer summary.json wmape when available (authoritative aggregate)
        summary = load_summary_json(run_dir)
        wmape = summary.get("wmape")
        within = summary.get("within_20pct_calories_rate")
        parse = summary.get("parse_ok_rate")
        n = summary.get("n") or stats["overall"]["n"]
        if wmape is not None:
            try:
                wmape_f = float(wmape)
            except (TypeError, ValueError):
                # strip percent
                m = re.search(r"[\d.]+", str(wmape))
                wmape_f = float(m.group()) if m else stats["overall"]["wmape"]
        else:
            wmape_f = stats["overall"]["wmape"]
            if wmape_f is not None and wmape_f <= 2:
                wmape_f = 100.0 * wmape_f
        if within is not None:
            try:
                within_f = float(within)
                if within_f > 1:
                    within_f = within_f / 100.0
            except (TypeError, ValueError):
                within_f = stats["overall"]["within_20"]
        else:
            within_f = stats["overall"]["within_20"]
        if parse is not None:
            try:
                parse_f = float(parse)
                if parse_f > 1:
                    parse_f = parse_f / 100.0
            except (TypeError, ValueError):
                parse_f = (
                    stats["overall"]["parse_ok"] / stats["overall"]["n"]
                    if stats["overall"]["n"]
                    else None
                )
        else:
            parse_f = (
                stats["overall"]["parse_ok"] / stats["overall"]["n"]
                if stats["overall"]["n"]
                else None
            )

        line = (
            f"{name:12} {int(n):4d} {fmt_pct(parse_f):>7} "
            f"{fmt_wmape(wmape_f):>8} {fmt_pct(within_f):>7}"
        )
        if hard_ids and stats["hard"]:
            h = stats["hard"]
            hw = h["wmape"]
            if hw is not None and hw <= 2:
                hw = 100.0 * hw
            line += (
                f"  {h['n']:6d} {fmt_wmape(hw):>8} {fmt_pct(h['within_20']):>7}"
            )
        print(line)


if __name__ == "__main__":
    main()
