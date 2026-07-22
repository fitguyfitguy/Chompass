#!/usr/bin/env python3
"""Probe OpenRouter free / dynamically routed models on a small text eval split."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from env_local import load_env_local, openrouter_api_key
from openrouter_models import NOFUD_FREE_ROUTER_ID, list_free_models
from parse import parse_food_json
from prompts import build_prompt, list_prompts
from providers import build_provider
from schema import load_manifest
from score import aggregate_scores, score_sample


def _pick_model_ids(
    *,
    explicit: str,
    discovered: list,
    max_models: int,
    router_only: bool,
) -> list[str]:
    if explicit.strip():
        return [m.strip() for m in explicit.split(",") if m.strip()]
    if router_only or max_models <= 0:
        return [NOFUD_FREE_ROUTER_ID]
    extras = [
        m.id
        for m in discovered
        if m.id not in {NOFUD_FREE_ROUTER_ID, "openrouter/free"}
    ][:max_models]
    return [NOFUD_FREE_ROUTER_ID, *extras]


def _write_results(path: Path, results: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(results, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Probe OpenRouter free models for food text accuracy")
    parser.add_argument(
        "--manifest",
        default="docs/benchmarks/food_accuracy/manifest/eval_text.jsonl",
        help="Text manifest (relative to repo root)",
    )
    parser.add_argument(
        "--prompt",
        default="compact",
        choices=list_prompts(),
        help="Prompt variant (default compact — faster on free tier; use production_text for app parity)",
    )
    parser.add_argument("--limit", type=int, default=3, help="Samples per model (default 3)")
    parser.add_argument(
        "--models",
        default="",
        help="Comma-separated model ids (default: nofud/free only)",
    )
    parser.add_argument(
        "--max-models",
        type=int,
        default=0,
        help="Also probe N discovered :free models after the router (default 0 = router only)",
    )
    parser.add_argument(
        "--router-only",
        action="store_true",
        help="Probe only nofud/free (same as --max-models 0)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=90.0,
        help="Per-request timeout in seconds (default 90)",
    )
    parser.add_argument("--list-only", action="store_true", help="Print discovered free models and exit")
    parser.add_argument(
        "--text-only",
        action="store_true",
        default=True,
        help="When discovering extras, skip models without text input (default on)",
    )
    parser.add_argument(
        "--include-vision",
        action="store_true",
        help="When discovering extras, include vision/video models (slower on text-only prompts)",
    )
    args = parser.parse_args()

    load_env_local()
    api_key = openrouter_api_key()
    if not api_key:
        raise SystemExit("OPENROUTER_TOKEN not set. Add it to .env.local or export it in the shell.")

    vision_filter = True if args.include_vision else (False if args.text_only else None)
    discovered = list_free_models(
        api_key=api_key,
        vision=vision_filter,
        include_router=False,
        include_nofud_router=True,
        exclude_filters=True,
    )
    if args.list_only:
        print(f"Discovered {len(discovered)} free model entries:\n")
        for model in discovered:
            mods = ",".join(model.input_modalities)
            print(f"  {model.id:48}  vision={model.supports_vision}  modalities={mods}")
        return

    model_ids = _pick_model_ids(
        explicit=args.models,
        discovered=discovered,
        max_models=args.max_models,
        router_only=args.router_only,
    )

    samples = load_manifest(args.manifest, limit=args.limit)
    if not samples:
        raise SystemExit(f"No samples in {args.manifest}")

    out_path = _HERE / "results" / "openrouter_free_probe.json"
    est_calls = len(model_ids) * len(samples)
    print(
        f"Probing {len(model_ids)} model(s) × {len(samples)} sample(s) = {est_calls} API call(s)\n"
        f"  prompt={args.prompt}  timeout={args.timeout:.0f}s\n"
        f"  models: {', '.join(model_ids)}\n"
        f"  (Free tier can take 30–90s per call with long prompts; progress prints per sample.)\n",
        flush=True,
    )
    results: list[dict] = []

    for model_idx, model_id in enumerate(model_ids, start=1):
        print(f"[model {model_idx}/{len(model_ids)}] {model_id}", flush=True)
        provider = build_provider("openrouter", model=model_id)
        sample_scores = []
        routed_models: set[str] = set()
        errors: list[str] = []

        for sample_idx, sample in enumerate(samples, start=1):
            prompt = build_prompt(sample, args.prompt)
            print(f"  sample {sample_idx}/{len(samples)} {sample.id} ...", end="", flush=True)
            started = time.perf_counter()
            try:
                response = provider.complete(prompt=prompt)
            except Exception as exc:  # noqa: BLE001
                elapsed = time.perf_counter() - started
                msg = f"{sample.id}: {exc}"
                errors.append(msg)
                from score import SampleScore

                sample_scores.append(SampleScore(id=sample.id, parse_ok=False, error=str(exc)))
                print(f" FAIL ({elapsed:.1f}s) {exc}", flush=True)
                continue

            elapsed = time.perf_counter() - started
            if response.routed_model:
                routed_models.add(response.routed_model)

            parsed = parse_food_json(response.text)
            scored = score_sample(sample.id, sample.ground_truth(), parsed)
            sample_scores.append(scored)
            routed = response.routed_model or model_id
            status = "ok" if scored.parse_ok else f"parse_fail({scored.error})"
            print(f" {status} ({elapsed:.1f}s, routed={routed})", flush=True)

        agg = aggregate_scores(sample_scores)
        row = {
            "requested_model": model_id,
            "routed_models": sorted(routed_models),
            "prompt": args.prompt,
            "n": agg.n,
            "parse_ok_rate": agg.parse_ok_rate,
            "wmape": agg.wmape,
            "mae_calories": agg.mae_calories,
            "within_20pct_calories_rate": agg.within_20pct_calories_rate,
            "errors": errors,
        }
        results.append(row)
        _write_results(out_path, results)

        routed = ", ".join(row["routed_models"]) if row["routed_models"] else "(unknown)"
        wmape = f"{row['wmape']:.3f}" if row["wmape"] is not None else "n/a"
        print(
            f"  → parse_ok={row['parse_ok_rate']:.0%}  wmape={wmape}  mae_kcal={row['mae_calories']}\n"
            f"  routed backends: {routed}\n",
            flush=True,
        )

    print(f"Wrote {out_path}")


if __name__ == "__main__":
    main()
