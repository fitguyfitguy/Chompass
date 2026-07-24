#!/usr/bin/env python3
"""Two-stage ask-then-answer clarification eval.

Stage 1 uses the `compact_clarify_ask` prompt: the model estimates macros and
names the one clarification (`portion` / `added_fat` / `none`) that would most
improve its estimate. When it asks and the oracle answer exists in the manifest
(build_clarify_manifests.py), stage 2 re-runs the compact prompt with the answer
injected. Reports ask_rate, answered_rate, and stage-1 vs final accuracy —
the ask-precision counterpart to the oracle-ceiling conditions in run_eval.py.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from env_local import load_env_local
from parse import parse_food_json
from prompts import build_prompt, compact_clarify_prompt
from providers import aggregate_usage, build_provider, normalize_usage
from schema import RESULTS_DIR, Sample, load_manifest, validate_sample
from score import SampleScore, aggregate_scores, score_sample

ASK_VALUES = {"portion", "added_fat"}
ORACLE_KEYS = {"portion": "clarify_portion", "added_fat": "clarify_fat"}


def _call(provider, *, prompt: str, image_path: Path | None, retries: int):
    last_error: Exception | None = None
    for attempt in range(retries + 1):
        try:
            return provider.complete(prompt=prompt, image_path=image_path), None
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            msg = str(exc)
            retryable = "429" in msg or "rate-limited" in msg or "502" in msg
            if attempt < retries and retryable:
                wait = min(60.0, 5.0 * (2**attempt))
                print(f" retryable ({exc}); sleep {wait:.0f}s ...", end="", flush=True)
                time.sleep(wait)
                continue
            return None, exc
    return None, last_error


def _evaluate_one(sample: Sample, *, provider, retries: int) -> dict:
    image_path = sample.resolved_image_path() if sample.modality == "image" else None
    if sample.modality == "image" and (image_path is None or not image_path.exists()):
        err = f"missing_image: {sample.image_path}"
        scored = SampleScore(id=sample.id, parse_ok=False, error=err)
        return {
            "id": sample.id,
            "parse_ok": False,
            "error": err,
            "asked": None,
            "answered": False,
            "score_stage1": scored.to_dict(),
            "score": scored.to_dict(),
        }

    stage1_prompt = build_prompt(sample, "compact_clarify_ask")
    response, error = _call(provider, prompt=stage1_prompt, image_path=image_path, retries=retries)
    if response is None:
        scored = SampleScore(id=sample.id, parse_ok=False, error=str(error))
        return {
            "id": sample.id,
            "parse_ok": False,
            "error": str(error),
            "asked": None,
            "answered": False,
            "score_stage1": scored.to_dict(),
            "score": scored.to_dict(),
        }

    parsed1 = parse_food_json(response.text)
    score1 = score_sample(sample.id, sample.ground_truth(), parsed1)
    raw_request = (parsed1.raw or {}).get("clarify_request")
    request = raw_request if isinstance(raw_request, str) else None
    request = request.strip().lower() if request else None
    request_missing = request not in ASK_VALUES | {"none"}
    if request_missing:
        request = "none"

    record = {
        "id": sample.id,
        "modality": sample.modality,
        "source": sample.source,
        "prompt": "compact_clarify_ask",
        "model": response.routed_model or response.model,
        "asked": request if request in ASK_VALUES else None,
        "clarify_request_missing": request_missing,
        "answered": False,
        "parse_ok": score1.parse_ok,
        "score_stage1": score1.to_dict(),
        "score": score1.to_dict(),
        "raw_response_stage1": response.text[:2000],
        "usage": response.usage,
        **normalize_usage(response.usage),
    }

    if request in ASK_VALUES and sample.extra.get(ORACLE_KEYS[request]):
        stage2_prompt = compact_clarify_prompt(
            sample, portion=request == "portion", fat=request == "added_fat"
        )
        response2, error2 = _call(provider, prompt=stage2_prompt, image_path=image_path, retries=retries)
        if response2 is not None:
            parsed2 = parse_food_json(response2.text)
            score2 = score_sample(sample.id, sample.ground_truth(), parsed2)
            record.update(
                answered=True,
                parse_ok=score2.parse_ok,
                score=score2.to_dict(),
                raw_response_stage2=response2.text[:2000],
            )
        else:
            record["stage2_error"] = str(error2)
    return record


def _score_from(payload: dict) -> SampleScore:
    return SampleScore(**{k: payload.get(k) for k in SampleScore.__dataclass_fields__})


def main() -> None:
    parser = argparse.ArgumentParser(description="Two-stage clarification eval runner")
    parser.add_argument("--manifest", required=True, help="Clarify-enriched JSONL manifest")
    parser.add_argument("--provider", default="openrouter", choices=["stub", "openai", "ollama", "openrouter"])
    parser.add_argument("--model", default=None)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--out", default=None, help="Output directory (default: results/clarify_<timestamp>)")
    parser.add_argument("--ids", default="", help="Comma-separated sample ids to run only")
    parser.add_argument("--sleep", type=float, default=0.0)
    parser.add_argument("--retries", type=int, default=2)
    args = parser.parse_args()

    load_env_local()
    default_models = {"openai": "gpt-4o-mini", "openrouter": "nofud/free", "ollama": "llama3.2", "stub": "stub"}
    model = args.model or default_models[args.provider]

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

    id_filter = {s.strip() for s in args.ids.split(",") if s.strip()}
    if id_filter:
        samples = [s for s in samples if s.id in id_filter]

    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out_dir = Path(args.out) if args.out else RESULTS_DIR / f"clarify_{run_id}"
    out_dir.mkdir(parents=True, exist_ok=True)
    per_sample_path = out_dir / "samples.jsonl"

    provider = build_provider(args.provider, model=model)
    records: list[dict] = []
    for idx, sample in enumerate(samples, start=1):
        if args.sleep > 0 and idx > 1:
            time.sleep(args.sleep)
        print(f"[{idx}/{len(samples)}] {sample.id} ...", end="", flush=True)
        record = _evaluate_one(sample, provider=provider, retries=args.retries)
        records.append(record)
        with per_sample_path.open("w", encoding="utf-8") as handle:
            for row in records:
                handle.write(json.dumps(row, ensure_ascii=False) + "\n")
        status = "ok" if record.get("parse_ok") else f"FAIL ({record.get('error')})"
        asked = record.get("asked") or "none"
        print(f" {status} asked={asked} answered={record.get('answered')}", flush=True)

    stage1_scores = [_score_from(r["score_stage1"]) for r in records]
    final_scores = [_score_from(r["score"]) for r in records]
    agg1 = aggregate_scores(stage1_scores)
    agg_final = aggregate_scores(final_scores)
    usage_agg = aggregate_usage(records)

    n = len(records)
    asked_n = sum(1 for r in records if r.get("asked"))
    answered_n = sum(1 for r in records if r.get("answered"))
    summary = {
        "run_id": run_id,
        "manifest": args.manifest,
        "prompt": "compact_clarify_ask+answer",
        "provider": args.provider,
        "model": model,
        "n": n,
        "ask_rate": f"{asked_n / n:.4f}" if n else "",
        "answered_rate": f"{answered_n / asked_n:.4f}" if asked_n else "",
        "request_missing_rate": f"{sum(1 for r in records if r.get('clarify_request_missing')) / n:.4f}" if n else "",
        "parse_ok_rate": f"{agg_final.parse_ok_rate:.4f}",
        "wmape_stage1": "" if agg1.wmape is None else f"{agg1.wmape:.4f}",
        "wmape": "" if agg_final.wmape is None else f"{agg_final.wmape:.4f}",
        "within_20pct_calories_rate_stage1": ""
        if agg1.within_20pct_calories_rate is None
        else f"{agg1.within_20pct_calories_rate:.4f}",
        "within_20pct_calories_rate": ""
        if agg_final.within_20pct_calories_rate is None
        else f"{agg_final.within_20pct_calories_rate:.4f}",
        "mae_calories": "" if agg_final.mae_calories is None else f"{agg_final.mae_calories:.2f}",
        "usage_n": usage_agg.get("usage_n") or 0,
    }

    summary_path = out_dir / "summary.csv"
    with summary_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(summary.keys()))
        writer.writeheader()
        writer.writerow(summary)
    (out_dir / "summary.json").write_text(
        json.dumps(
            {
                **summary,
                "aggregate_stage1": agg1.to_dict(),
                "aggregate_final": agg_final.to_dict(),
                "usage": usage_agg,
            },
            indent=2,
        )
    )

    print("\n--- summary ---")
    print(json.dumps(summary, indent=2))
    print(f"\nWrote {per_sample_path}")
    print(f"Wrote {summary_path}")


if __name__ == "__main__":
    main()
