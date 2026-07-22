"""Grounded-entry scoring helpers for the food_accuracy harness.

Records identity / source / portion metrics that the single-shot MAE harness
does not cover. Use with a stub or recorded recognition+lookup trace.

Example (stub):
  uv run python benchmarks/food_accuracy/grounded_metrics.py \
    --trace benchmarks/food_accuracy/manifest/grounded_trace_example.jsonl
"""

from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path


def score_trace(rows: list[dict]) -> dict:
    if not rows:
        return {"n": 0}
    identity_hits = sum(1 for r in rows if r.get("identity_top1"))
    identity_top3 = sum(
        1
        for r in rows
        if (topk := r.get("identity_topk")) is not None and topk <= 3
    )
    sources = [r.get("source_kind") for r in rows if r.get("source_kind")]
    gram_errs = [abs(r["gram_error"]) for r in rows if r.get("gram_error") is not None]
    nutrient_wmape = [r["nutrient_wmape"] for r in rows if r.get("nutrient_wmape") is not None]
    fallbacks = sum(1 for r in rows if r.get("source_kind") == "modelEstimate")
    corrections = sum(1 for r in rows if r.get("user_corrected"))
    latencies = [r["latency_ms"] for r in rows if r.get("latency_ms") is not None]
    tool_rounds = [r["tool_rounds"] for r in rows if r.get("tool_rounds") is not None]
    search_counts = [r["search_usda_count"] for r in rows if r.get("search_usda_count") is not None]
    return {
        "n": len(rows),
        "identity_top1_rate": identity_hits / len(rows),
        "identity_top3_rate": identity_top3 / len(rows),
        "source_coverage": {
            k: sources.count(k) / len(rows) for k in sorted(set(sources))
        },
        "mean_abs_gram_error": statistics.mean(gram_errs) if gram_errs else None,
        "mean_nutrient_wmape": statistics.mean(nutrient_wmape) if nutrient_wmape else None,
        "fallback_rate": fallbacks / len(rows),
        "correction_rate": corrections / len(rows),
        "mean_latency_ms": statistics.mean(latencies) if latencies else None,
        "mean_tool_rounds": statistics.mean(tool_rounds) if tool_rounds else None,
        "mean_search_usda_count": statistics.mean(search_counts) if search_counts else None,
        "asset_bytes": rows[0].get("asset_bytes"),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trace", type=Path, required=True, help="JSONL grounded eval traces")
    parser.add_argument("--out", type=Path, help="Write summary JSON here")
    args = parser.parse_args()
    rows = [
        json.loads(line)
        for line in args.trace.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    summary = score_trace(rows)
    text = json.dumps(summary, indent=2)
    print(text)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
