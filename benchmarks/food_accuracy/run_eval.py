#!/usr/bin/env python3
"""Run food accuracy eval against a manifest."""

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
from prompts import build_prompt, list_prompts
from providers import aggregate_usage, build_provider, normalize_usage
from schema import RESULTS_DIR, Sample, load_manifest, validate_sample
from score import SampleScore, aggregate_scores, score_sample


def _write_summary_csv(path: Path, summary: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(summary.keys()))
        writer.writeheader()
        writer.writerow(summary)


def _load_existing_records(path: Path) -> dict[str, dict]:
    if not path.exists():
        return {}
    records: dict[str, dict] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        row = json.loads(line)
        records[str(row["id"])] = row
    return records


def _record_needs_retry(record: dict | None) -> bool:
    return record is None or record.get("parse_ok") is not True


def _score_from_record(record: dict) -> SampleScore:
    score = dict(record.get("score") or {})
    score["id"] = record["id"]
    score["parse_ok"] = bool(record.get("parse_ok"))
    if not score["parse_ok"] and not score.get("error"):
        score["error"] = record.get("error")
    return SampleScore(**{k: score.get(k) for k in SampleScore.__dataclass_fields__})


def _write_results(path: Path, ordered_ids: list[str], records: dict[str, dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for sample_id in ordered_ids:
            if sample_id in records:
                handle.write(json.dumps(records[sample_id], ensure_ascii=False) + "\n")


def _evaluate_one(
    sample: Sample,
    *,
    provider,
    prompt_name: str,
    retries: int,
) -> tuple[dict, SampleScore]:
    prompt = build_prompt(sample, prompt_name)
    image_path = sample.resolved_image_path() if sample.modality == "image" else None
    if sample.modality == "image" and (image_path is None or not image_path.exists()):
        err = f"missing_image: {sample.image_path}"
        scored = SampleScore(id=sample.id, parse_ok=False, error=err)
        return {"id": sample.id, "parse_ok": False, "error": err, "score": scored.to_dict()}, scored

    last_error: Exception | None = None
    response = None
    for attempt in range(retries + 1):
        try:
            response = provider.complete(prompt=prompt, image_path=image_path)
            break
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            msg = str(exc)
            retryable = "429" in msg or "rate-limited" in msg or "502" in msg
            if attempt < retries and retryable:
                wait = min(60.0, 5.0 * (2**attempt))
                print(f" retryable ({exc}); sleep {wait:.0f}s ...", end="", flush=True)
                time.sleep(wait)
                continue
            scored = SampleScore(id=sample.id, parse_ok=False, error=str(exc))
            return {
                "id": sample.id,
                "modality": sample.modality,
                "source": sample.source,
                "prompt": prompt_name,
                "parse_ok": False,
                "error": str(exc),
                "score": scored.to_dict(),
            }, scored

    if response is None:
        assert last_error is not None
        scored = SampleScore(id=sample.id, parse_ok=False, error=str(last_error))
        return {
            "id": sample.id,
            "parse_ok": False,
            "error": str(last_error),
            "score": scored.to_dict(),
        }, scored

    parsed = parse_food_json(response.text)
    scored = score_sample(sample.id, sample.ground_truth(), parsed)
    usage_fields = normalize_usage(response.usage)
    record = {
        "id": sample.id,
        "modality": sample.modality,
        "source": sample.source,
        "prompt": prompt_name,
        "model": response.routed_model or response.model,
        "latency_ms": response.latency_ms,
        "parse_ok": scored.parse_ok,
        "prediction": parsed.raw,
        "ground_truth": sample.ground_truth().as_dict(),
        "score": scored.to_dict(),
        "raw_response": response.text[:4000],
        "usage": response.usage,
        **usage_fields,
    }
    return record, scored


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
    parser.add_argument(
        "--model",
        default=None,
        help="Model id (default: gpt-4o-mini for openai, nofud/free for openrouter, llama3.2 for ollama)",
    )
    parser.add_argument("--limit", type=int, default=None, help="Max samples to evaluate")
    parser.add_argument("--out", default=None, help="Output directory (default: results/<timestamp>)")
    parser.add_argument(
        "--resume",
        default=None,
        help=(
            "Results directory (or samples.jsonl) to resume. "
            "Only re-runs missing/failed samples and merges into that file."
        ),
    )
    parser.add_argument(
        "--ids",
        default="",
        help="Comma-separated sample ids to (re)run only",
    )
    parser.add_argument(
        "--sleep",
        type=float,
        default=0.0,
        help="Seconds to sleep between API calls (helps free-tier rate limits)",
    )
    parser.add_argument(
        "--retries",
        type=int,
        default=2,
        help="Retries on 429/502 before recording failure (default 2)",
    )
    parser.add_argument("--dry-run", action="store_true", help="Validate manifest / show resume plan and exit")
    args = parser.parse_args()

    load_env_local()

    default_models = {
        "openai": "gpt-4o-mini",
        "openrouter": "nofud/free",
        "ollama": "llama3.2",
        "stub": "stub",
    }
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

    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    records: dict[str, dict] = {}

    if args.resume:
        resume_path = Path(args.resume)
        if resume_path.is_dir():
            out_dir = resume_path
            per_sample_path = out_dir / "samples.jsonl"
        elif resume_path.name.endswith(".jsonl"):
            per_sample_path = resume_path
            out_dir = Path(args.out) if args.out else resume_path.parent
        else:
            raise SystemExit(f"--resume must be a results dir or samples.jsonl: {resume_path}")
        records = _load_existing_records(per_sample_path)
        print(f"Resuming from {per_sample_path} ({len(records)} existing records)", flush=True)
    else:
        out_dir = Path(args.out) if args.out else RESULTS_DIR / run_id
        per_sample_path = out_dir / "samples.jsonl"

    out_dir.mkdir(parents=True, exist_ok=True)
    ordered_ids = [s.id for s in samples]

    id_filter = {s.strip() for s in args.ids.split(",") if s.strip()}
    todo: list[Sample] = []
    kept = 0
    for sample in samples:
        if id_filter and sample.id not in id_filter:
            continue
        if args.resume and not _record_needs_retry(records.get(sample.id)):
            kept += 1
            continue
        todo.append(sample)

    if args.resume:
        print(f"Keeping {kept} ok samples; will re-run {len(todo)}: {[s.id for s in todo]}", flush=True)

    if args.dry_run:
        if not args.resume:
            print(f"Manifest OK: {len(samples)} samples from {args.manifest}")
        return

    if todo:
        provider = build_provider(args.provider, model=model)
        for idx, sample in enumerate(todo, start=1):
            if args.sleep > 0 and idx > 1:
                time.sleep(args.sleep)
            print(f"[{idx}/{len(todo)}] {sample.id} ...", end="", flush=True)
            record, scored = _evaluate_one(
                sample,
                provider=provider,
                prompt_name=args.prompt,
                retries=args.retries,
            )
            records[sample.id] = record
            _write_results(per_sample_path, ordered_ids, records)
            if scored.parse_ok:
                tok = ""
                if record.get("prompt_tokens") is not None:
                    cached = record.get("cached_tokens")
                    cached_s = f" cached={cached}" if cached is not None else ""
                    tok = (
                        f" tok={record.get('prompt_tokens')}→{record.get('completion_tokens')}"
                        f"{cached_s}"
                    )
                print(
                    f" ok abs={scored.abs_error_sum:.1f} "
                    f"cal_mape={scored.mape_calories:.2%} "
                    f"latency={record.get('latency_ms', 0):.0f}ms"
                    f"{tok}",
                    flush=True,
                )
            else:
                print(f" FAIL ({scored.error})", flush=True)
    else:
        print("Nothing to run.")

    _write_results(per_sample_path, ordered_ids, records)
    sample_scores = [_score_from_record(records[i]) for i in ordered_ids if i in records]

    agg = aggregate_scores(sample_scores)
    ordered_records = [records[i] for i in ordered_ids if i in records]
    usage_agg = aggregate_usage(ordered_records)

    def _fmt(value: int | float | None, *, digits: int = 4) -> str:
        if value is None:
            return ""
        if isinstance(value, int) or (isinstance(value, float) and value.is_integer() and digits == 0):
            return str(int(value))
        return f"{value:.{digits}f}"

    summary = {
        "run_id": run_id,
        "manifest": args.manifest,
        "prompt": args.prompt,
        "provider": args.provider,
        "model": model,
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
        "sum_prompt_tokens": _fmt(usage_agg.get("sum_prompt_tokens"), digits=0),
        "sum_completion_tokens": _fmt(usage_agg.get("sum_completion_tokens"), digits=0),
        "sum_cached_tokens": _fmt(usage_agg.get("sum_cached_tokens"), digits=0),
        "sum_total_tokens": _fmt(usage_agg.get("sum_total_tokens"), digits=0),
        "mean_prompt_tokens": _fmt(usage_agg.get("mean_prompt_tokens"), digits=1),
        "mean_completion_tokens": _fmt(usage_agg.get("mean_completion_tokens"), digits=1),
        "mean_cached_tokens": _fmt(usage_agg.get("mean_cached_tokens"), digits=1),
        "cache_hit_rate": _fmt(usage_agg.get("cache_hit_rate")),
        "sum_cost": _fmt(usage_agg.get("sum_cost"), digits=6),
        "usage_n": usage_agg.get("usage_n") or 0,
    }

    summary_path = out_dir / "summary.csv"
    _write_summary_csv(summary_path, summary)
    (out_dir / "summary.json").write_text(
        json.dumps(
            {**summary, "aggregate": agg.to_dict(), "usage": usage_agg},
            indent=2,
        )
    )

    print("\n--- aggregate ---")
    print(json.dumps(agg.to_dict(), indent=2))
    print("\n--- usage ---")
    print(json.dumps(usage_agg, indent=2))
    print(f"\nWrote {per_sample_path}")
    print(f"Wrote {summary_path}")


if __name__ == "__main__":
    main()
