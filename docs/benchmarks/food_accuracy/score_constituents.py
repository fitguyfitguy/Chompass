#!/usr/bin/env python3
"""Score constituent-aware predictions against a labeled composite manifest.

Gate criteria (P1 meal constituents — see FOOD_ACCURACY_BENCHMARK_STATUS.md):
- parse_ok_rate ≥ 95% for both strong and weak models
- candidate aggregate WMAPE ≤ baseline WMAPE + 2pp
- min-component coverage ≥ 90% (strong) / ≥ 75% (weak)
- grams + macro reconciliation within 5% on samples that emit constituents
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

from schema import load_manifest

RECONCILE_TOL = 0.05


def _norm(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def _extract_constituents(prediction: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not isinstance(prediction, dict):
        return []
    for key in ("constituents", "ingredients", "components", "items"):
        raw = prediction.get(key)
        if isinstance(raw, list):
            return [c for c in raw if isinstance(c, dict)]
    return []


def _float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _token_hit(name: str, expected_tokens: list[str]) -> bool:
    n = _norm(name)
    return any(tok and tok in n for tok in (_norm(t) for t in expected_tokens))


@dataclass
class ConstituentSampleScore:
    id: str
    parse_ok: bool
    has_constituents: bool
    constituent_count: int
    min_components: int
    meets_min_components: bool
    expected_hit_count: int
    expected_token_count: int
    expected_coverage: float | None
    grams_reconcile_ok: bool | None
    macros_reconcile_ok: bool | None
    grams_rel_error: float | None = None
    calories_rel_error: float | None = None
    false_inclusion_count: int = 0
    error: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class ConstituentAggregate:
    n: int
    parse_ok_rate: float
    wmape: float | None
    within_20pct_calories_rate: float | None
    constituents_presence_rate: float
    min_components_rate: float
    expected_coverage_mean: float | None
    grams_reconcile_rate: float | None
    macros_reconcile_rate: float | None
    mean_latency_ms: float | None = None
    mean_prompt_tokens: float | None = None
    samples: list[dict[str, Any]] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        out = asdict(self)
        return out


def score_constituent_record(record: dict[str, Any], sample_extra: dict[str, Any]) -> ConstituentSampleScore:
    sample_id = str(record["id"])
    parse_ok = bool(record.get("parse_ok"))
    prediction = record.get("prediction") if isinstance(record.get("prediction"), dict) else None
    constituents = _extract_constituents(prediction)
    min_components = int(sample_extra.get("min_components") or 2)
    expected = [str(t) for t in (sample_extra.get("expected_components") or [])]

    if not parse_ok:
        return ConstituentSampleScore(
            id=sample_id,
            parse_ok=False,
            has_constituents=False,
            constituent_count=0,
            min_components=min_components,
            meets_min_components=False,
            expected_hit_count=0,
            expected_token_count=len(expected),
            expected_coverage=None,
            grams_reconcile_ok=None,
            macros_reconcile_ok=None,
            error=record.get("error") or "parse_failed",
        )

    names = [str(c.get("name") or "") for c in constituents]
    hits = sum(1 for tok in expected if any(_token_hit(name, [tok]) for name in names))
    # False inclusion: constituent name shares no token with any expected token
    # and is not a trivial empty name.
    false_incl = 0
    if expected and names:
        for name in names:
            if not name.strip():
                continue
            if not _token_hit(name, expected):
                false_incl += 1

    grams_ok = None
    macros_ok = None
    grams_rel = None
    cal_rel = None
    if constituents and prediction is not None:
        total_g = _float(prediction.get("serving_size_grams"))
        sum_g = 0.0
        sum_cal = 0.0
        sum_p = 0.0
        sum_c = 0.0
        sum_f = 0.0
        for c in constituents:
            g = _float(c.get("serving_size_grams"))
            if g is None or g <= 0:
                grams_ok = False
            else:
                sum_g += g
            cal = _float(c.get("calories"))
            p = _float(c.get("protein"))
            carb = _float(c.get("carbs"))
            fat = _float(c.get("fat"))
            if None in (cal, p, carb, fat):
                macros_ok = False
            else:
                sum_cal += cal or 0.0
                sum_p += p or 0.0
                sum_c += carb or 0.0
                sum_f += fat or 0.0

        if grams_ok is not False and total_g and total_g > 0:
            grams_rel = abs(sum_g - total_g) / total_g
            grams_ok = grams_rel <= RECONCILE_TOL
        elif grams_ok is None:
            grams_ok = False

        meal_cal = _float(prediction.get("calories"))
        meal_p = _float(prediction.get("protein"))
        meal_c = _float(prediction.get("carbs"))
        meal_f = _float(prediction.get("fat"))
        if macros_ok is not False and None not in (meal_cal, meal_p, meal_c, meal_f):
            denom = abs(meal_cal or 0) + abs(meal_p or 0) + abs(meal_c or 0) + abs(meal_f or 0)
            if denom > 0:
                err = (
                    abs(sum_cal - (meal_cal or 0))
                    + abs(sum_p - (meal_p or 0))
                    + abs(sum_c - (meal_c or 0))
                    + abs(sum_f - (meal_f or 0))
                )
                cal_rel = err / denom
                macros_ok = cal_rel <= RECONCILE_TOL
            else:
                macros_ok = False
        elif macros_ok is None:
            macros_ok = False

    coverage = (hits / len(expected)) if expected else None
    return ConstituentSampleScore(
        id=sample_id,
        parse_ok=True,
        has_constituents=len(constituents) > 0,
        constituent_count=len(constituents),
        min_components=min_components,
        meets_min_components=len(constituents) >= min_components,
        expected_hit_count=hits,
        expected_token_count=len(expected),
        expected_coverage=coverage,
        grams_reconcile_ok=grams_ok,
        macros_reconcile_ok=macros_ok,
        grams_rel_error=grams_rel,
        calories_rel_error=cal_rel,
        false_inclusion_count=false_incl,
    )


def aggregate_constituent_run(
    records: list[dict[str, Any]],
    extras_by_id: dict[str, dict[str, Any]],
) -> ConstituentAggregate:
    scores = [score_constituent_record(r, extras_by_id.get(str(r["id"]), {})) for r in records]
    n = len(scores)
    parsed = [s for s in scores if s.parse_ok]
    parse_ok_rate = (len(parsed) / n) if n else 0.0

    # Aggregate macros from the original score block written by run_eval.py
    abs_total = 0.0
    gt_total = 0.0
    within = []
    for r in records:
        sc = r.get("score") or {}
        if not r.get("parse_ok"):
            continue
        abs_total += float(sc.get("abs_error_sum") or 0.0)
        gt_total += float(sc.get("gt_sum") or 0.0)
        if sc.get("within_20pct_calories") is not None:
            within.append(bool(sc["within_20pct_calories"]))
    wmape = (abs_total / gt_total) if gt_total > 0 else None
    within_rate = (sum(within) / len(within)) if within else None

    presence = (sum(1 for s in parsed if s.has_constituents) / len(parsed)) if parsed else 0.0
    min_rate = (sum(1 for s in parsed if s.meets_min_components) / len(parsed)) if parsed else 0.0
    cov_vals = [s.expected_coverage for s in parsed if s.expected_coverage is not None]
    cov_mean = (sum(cov_vals) / len(cov_vals)) if cov_vals else None

    with_const = [s for s in parsed if s.has_constituents]
    grams_vals = [s.grams_reconcile_ok for s in with_const if s.grams_reconcile_ok is not None]
    macro_vals = [s.macros_reconcile_ok for s in with_const if s.macros_reconcile_ok is not None]
    grams_rate = (sum(1 for v in grams_vals if v) / len(grams_vals)) if grams_vals else None
    macros_rate = (sum(1 for v in macro_vals if v) / len(macro_vals)) if macro_vals else None

    latencies = [float(r["latency_ms"]) for r in records if r.get("latency_ms") is not None]
    prompts = [float(r["prompt_tokens"]) for r in records if r.get("prompt_tokens") is not None]

    return ConstituentAggregate(
        n=n,
        parse_ok_rate=parse_ok_rate,
        wmape=wmape,
        within_20pct_calories_rate=within_rate,
        constituents_presence_rate=presence,
        min_components_rate=min_rate,
        expected_coverage_mean=cov_mean,
        grams_reconcile_rate=grams_rate,
        macros_reconcile_rate=macros_rate,
        mean_latency_ms=(sum(latencies) / len(latencies)) if latencies else None,
        mean_prompt_tokens=(sum(prompts) / len(prompts)) if prompts else None,
        samples=[s.to_dict() for s in scores],
    )


def evaluate_gate(
    *,
    strong: ConstituentAggregate,
    weak: ConstituentAggregate,
    baseline_strong_wmape: float | None,
    baseline_weak_wmape: float | None,
) -> dict[str, Any]:
    checks: list[dict[str, Any]] = []

    def check(name: str, ok: bool, detail: str) -> None:
        checks.append({"name": name, "ok": ok, "detail": detail})

    check(
        "strong_parse_ok",
        strong.parse_ok_rate >= 0.95,
        f"{strong.parse_ok_rate:.1%} (need ≥95%)",
    )
    check(
        "weak_parse_ok",
        weak.parse_ok_rate >= 0.95,
        f"{weak.parse_ok_rate:.1%} (need ≥95%)",
    )

    def wmape_ok(candidate: float | None, baseline: float | None) -> tuple[bool, str]:
        if candidate is None:
            return False, "candidate wmape missing"
        if baseline is None:
            return False, "baseline wmape missing"
        # WMAPE stored as fraction (0.25 = 25%); allow +2 percentage points.
        limit = baseline + 0.02
        return candidate <= limit, f"candidate={candidate:.1%} baseline={baseline:.1%} limit={limit:.1%}"

    ok, detail = wmape_ok(strong.wmape, baseline_strong_wmape)
    check("strong_wmape_vs_baseline", ok, detail)
    ok, detail = wmape_ok(weak.wmape, baseline_weak_wmape)
    check("weak_wmape_vs_baseline", ok, detail)

    check(
        "strong_min_components",
        strong.min_components_rate >= 0.90,
        f"{strong.min_components_rate:.1%} (need ≥90%)",
    )
    check(
        "weak_min_components",
        weak.min_components_rate >= 0.75,
        f"{weak.min_components_rate:.1%} (need ≥75%)",
    )

    def reconcile_ok(agg: ConstituentAggregate, label: str) -> None:
        grams = agg.grams_reconcile_rate
        macros = agg.macros_reconcile_rate
        if grams is None and macros is None:
            check(f"{label}_reconcile", False, "no constituents emitted to reconcile")
            return
        g_ok = grams is not None and grams >= 0.95
        m_ok = macros is not None and macros >= 0.95
        check(
            f"{label}_reconcile",
            g_ok and m_ok,
            f"grams={grams!s} macros={macros!s} (need ≥95% within 5%)",
        )

    reconcile_ok(strong, "strong")
    reconcile_ok(weak, "weak")

    passed = all(c["ok"] for c in checks)
    return {"passed": passed, "checks": checks}


def _load_records(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            rows.append(json.loads(line))
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description="Score / gate constituent benchmark runs")
    parser.add_argument("--manifest", required=True, help="Composite constituent manifest")
    parser.add_argument("--strong", required=True, help="Candidate strong-model samples.jsonl")
    parser.add_argument("--weak", required=True, help="Candidate weak-model samples.jsonl")
    parser.add_argument("--baseline-strong", required=True, help="Baseline strong samples.jsonl")
    parser.add_argument("--baseline-weak", required=True, help="Baseline weak samples.jsonl")
    parser.add_argument("--out", required=True, help="Gate summary JSON path")
    args = parser.parse_args()

    samples = load_manifest(args.manifest)
    extras = {s.id: s.extra for s in samples}

    strong_recs = _load_records(Path(args.strong))
    weak_recs = _load_records(Path(args.weak))
    base_strong = aggregate_constituent_run(_load_records(Path(args.baseline_strong)), extras)
    base_weak = aggregate_constituent_run(_load_records(Path(args.baseline_weak)), extras)
    strong = aggregate_constituent_run(strong_recs, extras)
    weak = aggregate_constituent_run(weak_recs, extras)

    gate = evaluate_gate(
        strong=strong,
        weak=weak,
        baseline_strong_wmape=base_strong.wmape,
        baseline_weak_wmape=base_weak.wmape,
    )
    summary = {
        "gate": gate,
        "candidate_strong": strong.to_dict(),
        "candidate_weak": weak.to_dict(),
        "baseline_strong": {k: v for k, v in base_strong.to_dict().items() if k != "samples"},
        "baseline_weak": {k: v for k, v in base_weak.to_dict().items() if k != "samples"},
    }
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"passed": gate["passed"], "checks": gate["checks"]}, indent=2))
    raise SystemExit(0 if gate["passed"] else 1)


if __name__ == "__main__":
    main()
