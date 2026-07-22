"""Grounded-entry scoring helpers for the food_accuracy harness.

Records identity / source / portion metrics that the single-shot MAE harness
does not cover. Classifies per-sample failure modes for attribution.

Example (stub):
  uv run python docs/benchmarks/food_accuracy/grounded_metrics.py \
    --trace docs/benchmarks/food_accuracy/manifest/grounded_trace_example.jsonl
"""

from __future__ import annotations

import argparse
import json
import statistics
from collections import Counter
from pathlib import Path

FAILURE_CLASSES = (
    "identity",
    "portion",
    "data",
    "orchestration",
    "parse",
    "fallback",
    "ok",
)


def classify_failure(row: dict) -> str:
    """Attribute a grounded sample error to one primary class."""
    if row.get("parse_ok") is False and row.get("error") in {
        "no_finalize_grounding",
        "no_usda_match",
        "empty_finalize",
        "invalid_source_id",
    }:
        err = row.get("error")
        if err in {"no_finalize_grounding", "empty_finalize"}:
            return "orchestration"
        if err == "invalid_source_id":
            return "orchestration"
        if err == "no_usda_match":
            return "fallback"
        return "parse"
    if row.get("parse_ok") is False and not row.get("identity_top1") and not row.get("source_kind"):
        return "parse"
    if row.get("silent_zero"):
        return "orchestration"
    if row.get("source_kind") == "modelEstimate" or row.get("fallback"):
        return "fallback"
    if row.get("incomplete_energy") or row.get("data_gap"):
        return "data"
    gram_err = row.get("gram_error")
    if gram_err is not None and abs(float(gram_err)) > 40 and row.get("identity_top1"):
        return "portion"
    if row.get("identity_top1") is False and (row.get("identity_topk") is None or row.get("identity_topk") > 3):
        return "identity"
    if row.get("identity_top1") is False:
        return "identity"
    wmape = row.get("nutrient_wmape")
    if wmape is not None and float(wmape) > 0.25 and row.get("identity_top1"):
        return "portion"
    return "ok"


def enrich_row(row: dict) -> dict:
    out = dict(row)
    out["failure_class"] = row.get("failure_class") or classify_failure(row)
    return out


def score_trace(rows: list[dict]) -> dict:
    if not rows:
        return {"n": 0, "failure_class_counts": {}}
    enriched = [enrich_row(r) for r in rows]
    identity_hits = sum(1 for r in enriched if r.get("identity_top1"))
    identity_top3 = sum(
        1
        for r in enriched
        if (topk := r.get("identity_topk")) is not None and topk <= 3
    )
    sources = [r.get("source_kind") for r in enriched if r.get("source_kind")]
    gram_errs = [abs(r["gram_error"]) for r in enriched if r.get("gram_error") is not None]
    nutrient_wmape = [r["nutrient_wmape"] for r in enriched if r.get("nutrient_wmape") is not None]
    fallbacks = sum(1 for r in enriched if r.get("source_kind") == "modelEstimate" or r.get("fallback"))
    corrections = sum(1 for r in enriched if r.get("user_corrected"))
    latencies = [r["latency_ms"] for r in enriched if r.get("latency_ms") is not None]
    tool_rounds = [r["tool_rounds"] for r in enriched if r.get("tool_rounds") is not None]
    search_counts = [r["search_usda_count"] for r in enriched if r.get("search_usda_count") is not None]
    silent_zeros = sum(1 for r in enriched if r.get("silent_zero"))
    class_counts = Counter(r["failure_class"] for r in enriched)
    return {
        "n": len(enriched),
        "identity_top1_rate": identity_hits / len(enriched),
        "identity_top3_rate": identity_top3 / len(enriched),
        "source_coverage": {
            k: sources.count(k) / len(enriched) for k in sorted(set(sources))
        },
        "mean_abs_gram_error": statistics.mean(gram_errs) if gram_errs else None,
        "mean_nutrient_wmape": statistics.mean(nutrient_wmape) if nutrient_wmape else None,
        "fallback_rate": fallbacks / len(enriched),
        "correction_rate": corrections / len(enriched),
        "silent_zero_rate": silent_zeros / len(enriched),
        "mean_latency_ms": statistics.mean(latencies) if latencies else None,
        "mean_tool_rounds": statistics.mean(tool_rounds) if tool_rounds else None,
        "mean_search_usda_count": statistics.mean(search_counts) if search_counts else None,
        "failure_class_counts": dict(sorted(class_counts.items())),
        "failure_class_rates": {
            k: class_counts.get(k, 0) / len(enriched) for k in FAILURE_CLASSES
        },
        "asset_bytes": enriched[0].get("asset_bytes"),
    }


def check_thresholds(summary: dict, thresholds: dict, *, use_readiness: bool = False) -> list[str]:
    """Return human-readable failures against readiness or regression floors."""
    key = "readiness_targets" if use_readiness else "regression_floors"
    floors = thresholds.get(key) or {}
    macro = summary.get("macro") or summary
    failures: list[str] = []
    wmape = macro.get("wmape")
    if floors.get("wmape_max") is not None and wmape is not None and wmape > floors["wmape_max"]:
        failures.append(f"wmape {wmape:.3f} > {floors['wmape_max']}")
    w20 = macro.get("within_20pct_calories_rate")
    if (
        floors.get("within_20pct_calories_rate_min") is not None
        and w20 is not None
        and w20 < floors["within_20pct_calories_rate_min"]
    ):
        failures.append(
            f"within_20pct_calories_rate {w20:.3f} < {floors['within_20pct_calories_rate_min']}"
        )
    parse_ok = macro.get("parse_ok_rate")
    if (
        floors.get("parse_ok_rate_min") is not None
        and parse_ok is not None
        and parse_ok < floors["parse_ok_rate_min"]
    ):
        failures.append(f"parse_ok_rate {parse_ok:.3f} < {floors['parse_ok_rate_min']}")
    silent = summary.get("silent_zero_rate")
    if (
        floors.get("silent_zero_rate_max") is not None
        and silent is not None
        and silent > floors["silent_zero_rate_max"]
    ):
        failures.append(f"silent_zero_rate {silent:.3f} > {floors['silent_zero_rate_max']}")
    return failures


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trace", type=Path, required=True, help="JSONL grounded eval traces")
    parser.add_argument("--out", type=Path, help="Write summary JSON here")
    parser.add_argument(
        "--thresholds",
        type=Path,
        help="Optional baselines/grounded_text_thresholds.json to validate against",
    )
    parser.add_argument(
        "--readiness",
        action="store_true",
        help="Check readiness_targets instead of regression_floors",
    )
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
    if args.thresholds:
        thresholds = json.loads(args.thresholds.read_text(encoding="utf-8"))
        failures = check_thresholds(summary, thresholds, use_readiness=args.readiness)
        if failures:
            print("THRESHOLD FAILURES:")
            for f in failures:
                print(f"  - {f}")
            raise SystemExit(1)
        print("Thresholds OK")


if __name__ == "__main__":
    main()
