#!/usr/bin/env python3
"""Run L0 baselines or LLM cells on a Track A JSONL manifest."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from baselines import FORMULAS, check_navy_goldens, ridge_oof, us_navy
from prompts import SYSTEM, user_prompt
from schema import RESULTS_DIR, Subject, read_jsonl
from score import aggregate, slice_key, write_json

_FOOD = Path(__file__).resolve().parent.parent / "food_accuracy"
if str(_FOOD) not in sys.path:
    sys.path.insert(0, str(_FOOD))


def parse_bf_json(text: str) -> float | None:
    if not text:
        return None
    try:
        obj = json.loads(text)
    except json.JSONDecodeError:
        m = re.search(r"\{.*\}", text, re.S)
        if not m:
            return None
        try:
            obj = json.loads(m.group(0))
        except json.JSONDecodeError:
            return None
    if not isinstance(obj, dict):
        return None
    v = obj.get("bf_percent")
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return None
    if v < 0 or v > 100:
        return None
    return float(v)


def _bmi(s: Subject) -> float:
    return s.weight_kg / (s.height_cm / 100.0) ** 2


def _summarize(name: str, subjects: list[Subject], preds: list[float | None]) -> dict:
    pairs = [(s.bf_percent, p) for s, p in zip(subjects, preds)]
    overall = aggregate(pairs)
    slices: dict[str, dict] = {}
    buckets: dict[str, list[tuple[float, float | None]]] = defaultdict(list)
    for s, p in zip(subjects, preds):
        keys = slice_key(s.sex, _bmi(s), s.age)
        for dim, val in keys.items():
            buckets[f"{dim}={val}"].append((s.bf_percent, p))
        if s.ethnicity:
            buckets[f"ethnicity={s.ethnicity}"].append((s.bf_percent, p))
        buckets[f"source={s.source}"].append((s.bf_percent, p))
    for k, ps in sorted(buckets.items()):
        slices[k] = aggregate(ps).__dict__
    return {"method": name, "overall": overall.__dict__, "slices": slices}


def run_baselines(subjects: list[Subject]) -> dict[str, list[float | None]]:
    out: dict[str, list[float | None]] = {}
    for name, fn in FORMULAS.items():
        out[name] = [fn(s) for s in subjects]
    out["ridge"] = ridge_oof(subjects)
    return out


def run_llm(
    subjects: list[Subject],
    *,
    variant: str,
    provider_name: str,
    model: str,
    limit: int | None,
) -> tuple[list[float | None], dict]:
    from providers import OpenRouterProvider

    n = len(subjects) if limit is None else min(limit, len(subjects))
    subset = subjects[:n]
    provider = OpenRouterProvider(model=model)
    preds: list[float | None] = []
    n_ok = 0
    for s in subset:
        navy = us_navy(s)
        user = user_prompt(s, variant, navy)
        try:
            resp = provider.complete(prompt=f"{SYSTEM}\n\n{user}")
            text = resp.text
        except Exception:
            text = ""
        parsed = parse_bf_json(text)
        if parsed is not None:
            n_ok += 1
        preds.append(parsed)
    meta = {
        "provider": provider_name,
        "model": model,
        "variant": variant,
        "n": n,
        "parse_ok": n_ok,
    }
    # pad so zip with full subjects still works if we only scored a subset
    if n < len(subjects):
        preds.extend([None] * (len(subjects) - n))
        subjects_note = subset
        return preds, meta | {"scored_ids": [s.id for s in subjects_note]}
    return preds, meta


def kill_criteria(tables: dict[str, dict]) -> dict:
    """A1–A4 vs RFM / Navy / ridge. LLM methods keyed llm:*."""
    rfm = tables.get("rfm", {}).get("overall", {})
    navy = tables.get("navy", {}).get("overall", {})
    ridge = tables.get("ridge", {}).get("overall", {})
    llm_rows = {k: v for k, v in tables.items() if k.startswith("llm:")}
    out = {}
    for name, row in llm_rows.items():
        ov = row.get("overall", {})
        llm_mae = ov.get("mae")
        parse = ov.get("parse_rate")
        a1 = None
        if llm_mae is not None and rfm.get("mae") is not None:
            a1 = (rfm["mae"] - llm_mae) >= 0.5
        a2 = None
        if llm_mae is not None and navy.get("mae") is not None and navy.get("n_pred"):
            a2 = llm_mae < navy["mae"]
        a3 = None
        if llm_mae is not None and ridge.get("mae") is not None:
            a3 = ridge["mae"] <= llm_mae
        a4 = parse is not None and parse >= 0.95
        out[name] = {"A1_beats_rfm_0.5pp": a1, "A2_beats_navy": a2, "A3_ridge_le_llm": a3, "A4_parse_ge_95": a4}
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--baseline", action="store_true")
    parser.add_argument("--provider", default=None)
    parser.add_argument("--model", default=None)
    parser.add_argument("--variant", default="raw", choices=("raw", "navy_anchor", "missing_neck"))
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--tag", default=None)
    args = parser.parse_args()

    check_navy_goldens()
    subjects = list(read_jsonl(args.manifest))
    if args.limit and args.baseline:
        subjects = subjects[: args.limit]

    tables: dict[str, dict] = {}
    extra: dict = {}
    if args.baseline:
        preds = run_baselines(subjects)
        for name, col in preds.items():
            tables[name] = _summarize(name, subjects, col)
    elif args.provider:
        if not args.model:
            raise SystemExit("--model required with --provider")
        col, meta = run_llm(
            subjects,
            variant=args.variant,
            provider_name=args.provider,
            model=args.model,
            limit=args.limit,
        )
        extra = meta
        scored = subjects[: meta["n"]]
        name = f"llm:{args.model}:{args.variant}"
        tables[name] = _summarize(name, scored, col[: len(scored)])
    else:
        raise SystemExit("pass --baseline or --provider")

    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    tag = args.tag or ("baseline" if args.baseline else "llm")
    out = RESULTS_DIR / f"{tag}_{args.manifest.stem}_{stamp}.json"
    payload = {
        "manifest": str(args.manifest),
        "n": len(subjects),
        "tables": tables,
        "kill": kill_criteria(tables),
        "extra": extra,
    }
    write_json(out, payload)
    print(json.dumps(payload, indent=2))
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
