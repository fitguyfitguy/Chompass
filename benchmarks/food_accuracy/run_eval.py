#!/usr/bin/env python3
"""Run food accuracy eval against a manifest."""

from __future__ import annotations

import argparse
import csv
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from parse import parse_food_json
from prompts import build_prompt, list_prompts
from providers import build_provider
from schema import RESULTS_DIR, load_manifest, validate_sample
from score import aggregate_scores, score_sample


def _write_summary_csv(path: Path, summary: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(summary.keys()))
        writer.writeheader()
        writer.writerow(summary)


def main() -> None:
    parser = argparse.ArgumentParser(description="Food accuracy benchmark runner")
    parser.add_argument(
        "--manifest",
        default="benchmarks/food_accuracy/manifest/eval_text.jsonl",
        help="JSONL manifest path (relative to repo root)",
    )
    parser.add_argument(
        "--prompt",
        default="production_text",
        choices=list_prompts(),
        help="Prompt variant",
    )
    parser.add_argument("--provider", default="openai", choices=["stub", "openai", "ollama", "openrouter"])
    parser.add_argument("--model", default="gpt-4o-mini", help="Model id for the provider")
    parser.add_argument("--limit", type=int, default=None, help="Max samples to evaluate")
    parser.add_argument("--out", default=None, help="Output directory (default: results/<timestamp>)")
    parser.add_argument("--dry-run", action="store_true", help="Validate manifest and exit")
    args = parser.parse_args()

    samples = load_manifest(args.manifest, limit=args.limit)
    if not samples:
        raise SystemExit(f"No samples loaded from {args.manifest}")

    errors: list[str] = []
    for sample in samples:
        errors.extend(validate_sample(sample))
    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        raise SystemExit(1)

    if args.dry_run:
        print(f"Manifest OK: {len(samples)} samples from {args.manifest}")
        return

    provider = build_provider(args.provider, model=args.model)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out_dir = Path(args.out) if args.out else RESULTS_DIR / run_id
    out_dir.mkdir(parents=True, exist_ok=True)

    per_sample_path = out_dir / "samples.jsonl"
    sample_scores = []

    with per_sample_path.open("w", encoding="utf-8") as handle:
        for sample in samples:
            prompt = build_prompt(sample, args.prompt)
            image_path = sample.resolved_image_path() if sample.modality == "image" else None
            if sample.modality == "image" and (image_path is None or not image_path.exists()):
                record = {
                    "id": sample.id,
                    "parse_ok": False,
                    "error": f"missing_image: {sample.image_path}",
                }
                handle.write(json.dumps(record) + "\n")
                from score import SampleScore

                sample_scores.append(SampleScore(id=sample.id, parse_ok=False, error=record["error"]))
                print(f"{sample.id}: SKIP missing image")
                continue

            try:
                response = provider.complete(prompt=prompt, image_path=image_path)
            except Exception as exc:  # noqa: BLE001 — record and continue
                record = {"id": sample.id, "parse_ok": False, "error": str(exc)}
                handle.write(json.dumps(record) + "\n")
                from score import SampleScore

                sample_scores.append(SampleScore(id=sample.id, parse_ok=False, error=str(exc)))
                print(f"{sample.id}: FAIL provider ({exc})")
                continue

            parsed = parse_food_json(response.text)
            scored = score_sample(sample.id, sample.ground_truth(), parsed)
            sample_scores.append(scored)

            record = {
                "id": sample.id,
                "modality": sample.modality,
                "source": sample.source,
                "prompt": args.prompt,
                "model": response.model,
                "latency_ms": response.latency_ms,
                "parse_ok": scored.parse_ok,
                "prediction": parsed.raw,
                "ground_truth": sample.ground_truth().as_dict(),
                "score": scored.to_dict(),
                "raw_response": response.text[:4000],
            }
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")

            if scored.parse_ok:
                print(
                    f"{sample.id}: wmape-contrib abs={scored.abs_error_sum:.1f} "
                    f"cal_mape={scored.mape_calories:.2%} latency={response.latency_ms:.0f}ms"
                )
            else:
                print(f"{sample.id}: parse_fail ({scored.error})")

    agg = aggregate_scores(sample_scores)
    summary = {
        "run_id": run_id,
        "manifest": args.manifest,
        "prompt": args.prompt,
        "provider": args.provider,
        "model": args.model,
        "n": agg.n,
        "parse_ok_rate": f"{agg.parse_ok_rate:.4f}",
        "wmape": "" if agg.wmape is None else f"{agg.wmape:.4f}",
        "mae_calories": "" if agg.mae_calories is None else f"{agg.mae_calories:.2f}",
        "mae_protein_g": "" if agg.mae_protein_g is None else f"{agg.mae_protein_g:.2f}",
        "mae_carbs_g": "" if agg.mae_carbs_g is None else f"{agg.mae_carbs_g:.2f}",
        "mae_fat_g": "" if agg.mae_fat_g is None else f"{agg.mae_fat_g:.2f}",
        "mape_calories": "" if agg.mape_calories is None else f"{agg.mape_calories:.4f}",
        "within_20pct_calories_rate": ""
        if agg.within_20pct_calories_rate is None
        else f"{agg.within_20pct_calories_rate:.4f}",
    }

    summary_path = out_dir / "summary.csv"
    _write_summary_csv(summary_path, summary)
    (out_dir / "summary.json").write_text(json.dumps({**summary, "aggregate": agg.to_dict()}, indent=2))

    print("\n--- aggregate ---")
    print(json.dumps(agg.to_dict(), indent=2))
    print(f"\nWrote {per_sample_path}")
    print(f"Wrote {summary_path}")


if __name__ == "__main__":
    main()
