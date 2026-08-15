#!/usr/bin/env python3
"""Excursory scorer: constituents carry FULL macro + micro breakdown.

Extends score_constituents.py (macro-side gate metrics unchanged) with three
micro-side probes:

- micro presence:    fraction of emitted constituents that carry all 21
                     schema.MICRO_FIELDS non-null
- micros reconcile:  per-micro fraction where sum(constituent micros) matches
                     the meal-level micro within ±20% (looser than the ±5%
                     grams/macro gate — micros are noisier per item)
- FNDDS micro GT:    approximate per-constituent micro accuracy against USDA
                     FNDDS food_nutrient.csv via token-overlap name matching;
                     per-100g values scaled by the constituent's grams.
                     Coverage is reported honestly — match is approximate.

Usage:
  uv run --with httpx --with pandas python score_constituents_micro.py \
      results/<run_dir> [results/<run_dir2> ...]

Prints one table per run plus a JSON report next to each run dir.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import re
import sys
import zipfile
from dataclasses import asdict, dataclass, field
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from reconcile_constituents import extract_constituent_list
from schema import MICRO_FIELDS
from score_constituents import aggregate_constituent_run

FNDDS_ZIP = _HERE / "data" / "fndds" / "fndds.zip"
# Same USDA nutrient_nbr -> field map as build_fndds_manifest.py.
MICRO_NUTRIENT_IDS = {
    "269": "sugar_g",
    "539": "added_sugar_g",
    "291": "fiber_g",
    "606": "saturated_fat_g",
    "645": "monounsaturated_fat_g",
    "646": "polyunsaturated_fat_g",
    "605": "trans_fat_g",
    "601": "cholesterol_mg",
    "307": "sodium_mg",
    "306": "potassium_mg",
    "301": "calcium_mg",
    "303": "iron_mg",
    "304": "magnesium_mg",
    "309": "zinc_mg",
    "401": "vitamin_c_mg",
    "323": "vitamin_e_mg",
    "320": "vitamin_a_mcg",
    "328": "vitamin_d_mcg",
    "418": "vitamin_b12_mcg",
    "430": "vitamin_k_mcg",
    "417": "folate_mcg",
}
OMEGA_3_COMPONENT_IDS = {"851", "629", "621", "631"}

MICRO_SUM_TOL = 0.20  # per-micro constituent-sum vs meal tolerance (excursory)
MIN_MATCH_TOKENS = 2  # FNDDS name-match quality floor
# JSON schema uses short names ("sugar"); MICRO_FIELDS maps long GT name ->
# short name. Keep the reverse map for prediction lookups.
SHORT_TO_FIELD = {short: long for long, short in MICRO_FIELDS.items()}
_fndds_cache: dict[str, dict] | None = None


def _norm(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def _float(value) -> float | None:
    if value is None:
        return None
    try:
        out = float(value)
    except (TypeError, ValueError):
        return None
    if out != out or out in (float("inf"), float("-inf")):
        return None
    return out


def load_fndds_micros() -> dict[str, dict]:
    """food description -> {micro_field: per-100g value} (survey foods only)."""
    global _fndds_cache
    if _fndds_cache is not None:
        return _fndds_cache
    out: dict[str, dict] = {}
    if not FNDDS_ZIP.exists():
        print(f"  (FNDDS zip missing at {FNDDS_ZIP}; micro-GT section skipped)", file=sys.stderr)
        _fndds_cache = {}
        return out
    with zipfile.ZipFile(FNDDS_ZIP) as zf:
        names = {n.rsplit("/", 1)[-1]: n for n in zf.namelist()}
        foods = {}
        with io.TextIOWrapper(zf.open(names["food.csv"]), encoding="utf-8") as fh:
            for row in csv.DictReader(fh):
                foods[row["fdc_id"]] = row["description"]
        per_food: dict[str, dict] = {}
        with io.TextIOWrapper(zf.open(names["food_nutrient.csv"]), encoding="utf-8") as fh:
            for row in csv.DictReader(fh):
                fdc = row.get("fdc_id")
                if fdc not in foods:
                    continue
                nbr = row.get("nutrient_nbr") or row.get("nutrient_id")
                if nbr in MICRO_NUTRIENT_IDS:
                    per_food.setdefault(fdc, {})[MICRO_NUTRIENT_IDS[nbr]] = _float(row.get("amount"))
                elif nbr in OMEGA_3_COMPONENT_IDS:
                    # composite omega-3: sum ALA/EPA/DHA/DPA where measured
                    d = per_food.setdefault(fdc, {})
                    d["omega_3_g"] = (d.get("omega_3_g") or 0.0) + (_float(row.get("amount")) or 0.0)
        for fdc, desc in foods.items():
            if fdc in per_food:
                out[_norm(desc)] = per_food[fdc]
    _fndds_cache = out
    return out


def match_fndds(name: str, lookup: dict[str, dict]) -> tuple[str | None, float]:
    """Best token-overlap match; returns (norm description, dice score)."""
    toks = set(_norm(name).split())
    if not toks:
        return None, 0.0
    best, best_score = None, 0.0
    for desc, micros in lookup.items():
        d = set(desc.split())
        inter = len(toks & d)
        if inter == 0:
            continue
        dice = 2.0 * inter / (len(toks) + len(d))
        if dice > best_score:
            best, best_score = desc, dice
    return best, best_score


@dataclass
class MicroConstituentStats:
    n_samples: int
    n_constituents: int
    micro_presence_rate: float | None  # constituents with all 21 micros non-null
    micros_reconcile_rate: float | None  # per-micro sum-vs-meal within ±20%
    fndds_match_coverage: float | None  # constituents matched to FNDDS
    fndds_micro_wmape: float | None  # blended micro WMAPE on matched constituents
    per_field: dict[str, dict] = field(default_factory=dict)


def score_micro_constituents(records: list[dict]) -> MicroConstituentStats:
    parsed = [r for r in records if r.get("parse_ok")]
    constituents: list[tuple[dict, dict]] = []  # (constituent, meal prediction)
    n_micro_ok = 0
    micro_sum_ok: dict[str, int] = {}
    micro_sum_n: dict[str, int] = {}
    for r in parsed:
        pred = r.get("prediction") if isinstance(r.get("prediction"), dict) else None
        if not pred:
            continue
        consts = extract_constituent_list(pred)
        for c in consts:
            constituents.append((c, pred))
            present = all(c.get(short) is not None for short in MICRO_FIELDS.values())
            if present:
                n_micro_ok += 1
        if consts:
            for k, short in MICRO_FIELDS.items():
                meal_v = _float(pred.get(short))
                sum_v = sum(_float(c.get(short)) or 0.0 for c in consts)
                if meal_v is None or meal_v <= 0:
                    continue
                micro_sum_n[k] = micro_sum_n.get(k, 0) + 1
                if abs(sum_v - meal_v) / meal_v <= MICRO_SUM_TOL:
                    micro_sum_ok[k] = micro_sum_ok.get(k, 0) + 1

    # FNDDS approximate micro GT
    lookup = load_fndds_micros()
    matched = []
    unmatched = 0
    for c, pred in constituents:
        best, dice = match_fndds(str(c.get("name") or ""), lookup)
        g = _float(c.get("serving_size_grams"))
        if best is None or dice < 0.5 or g is None or g <= 0:
            unmatched += 1
            continue
        gt = lookup[best]
        # scaled per-100g GT vs model per-constituent micros
        matched.append((c, gt, g))
    fndds_abs = 0.0
    fndds_gt_sum = 0.0
    if matched:
        for c, gt, g in matched:
            for long_k, short in MICRO_FIELDS.items():
                gt100 = gt.get(long_k)
                if gt100 is None:
                    continue
                gt_v = gt100 * g / 100.0
                pred_v = _float(c.get(short))
                if pred_v is None:
                    continue
                fndds_abs += abs(pred_v - gt_v)
                fndds_gt_sum += abs(gt_v)
    total = len(constituents)
    return MicroConstituentStats(
        n_samples=len(parsed),
        n_constituents=total,
        micro_presence_rate=(n_micro_ok / total) if total else None,
        micros_reconcile_rate=(
            sum(micro_sum_ok.values()) / sum(micro_sum_n.values())
        )
        if micro_sum_n
        else None,
        fndds_match_coverage=(len(matched) / total) if total else None,
        fndds_micro_wmape=(fndds_abs / fndds_gt_sum) if fndds_gt_sum > 0 else None,
        per_field={
            k: {
                "ok": micro_sum_ok.get(k, 0),
                "n": micro_sum_n.get(k, 0),
                "rate": (
                    micro_sum_ok.get(k, 0) / micro_sum_n[k]
                    if micro_sum_n.get(k)
                    else None
                ),
            }
            for k in MICRO_FIELDS
        },
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Excursory macro+micro constituents scorer")
    parser.add_argument("run_dirs", nargs="+", help="results/<run> directories")
    args = parser.parse_args()

    rows = []
    for d in args.run_dirs:
        path = Path(d)
        samples = path / "samples.jsonl"
        if not samples.exists():
            print(f"SKIP {d}: no samples.jsonl")
            continue
        records = [json.loads(l) for l in samples.open(encoding="utf-8")]
        macro = aggregate_constituent_run(records, {})
        micro = score_micro_constituents(records)
        name = path.name
        print(f"== {name} ==")
        print(f"  parse_ok            {macro.parse_ok_rate:.0%}")
        print(f"  macro wmape         {macro.wmape:.1%}   ±20% kcal {macro.within_20pct_calories_rate:.0%}")
        print(f"  constituents        presence {macro.constituents_presence_rate:.0%}  min-comp {macro.min_components_rate:.0%}  cov {macro.expected_coverage_mean:.0%}" if macro.expected_coverage_mean is not None else f"  constituents        presence {macro.constituents_presence_rate:.0%}  min-comp {macro.min_components_rate:.0%}")
        print(f"  reconcile           grams {macro.grams_reconcile_rate:.0%}" if macro.grams_reconcile_rate is not None else "  reconcile           grams n/a", end="")
        print(f"  macros {macro.macros_reconcile_rate:.0%}  (n={len([s for s in macro.samples if s['has_constituents']])})" if macro.macros_reconcile_rate is not None else f"  macros n/a  (n={len([s for s in macro.samples if s['has_constituents']])})")
        print(f"  micro presence      {micro.micro_presence_rate:.0%} of {micro.n_constituents} constituents carry all 21 micros" if micro.micro_presence_rate is not None else f"  micro presence      n/a ({micro.n_constituents} constituents)")
        print(f"  micros reconcile    {micro.micros_reconcile_rate:.0%} of per-micro sums within ±{MICRO_SUM_TOL:.0%} of meal" if micro.micros_reconcile_rate is not None else "  micros reconcile    n/a")
        print(f"  FNDDS micro GT      match {micro.fndds_match_coverage:.0%} | blended wmape {micro.fndds_micro_wmape:.1%}" if micro.fndds_micro_wmape is not None else "  FNDDS micro GT      n/a")
        rows.append((name, macro, micro))
        report = {
            "macro": macro.to_dict(),
            "micro": asdict(micro),
        }
        (path / "constituents_micro_summary.json").write_text(
            json.dumps(report, indent=2), encoding="utf-8"
        )

    if len(rows) > 1:
        print("\n== side-by-side ==")
        print(
            "%-22s %6s %6s %6s %6s %6s %7s %7s"
            % ("run", "parse", "wmape", "±20%", "grec", "mrec", "micpre", "micwm")
        )
        for name, macro, micro in rows:
            print(
                "%-22s %5.0f%% %5.1f%% %5.0f%% %5.0f%% %5.0f%% %6.0f%% %6.1f%%"
                % (
                    name,
                    100 * macro.parse_ok_rate,
                    100 * (macro.wmape or 0),
                    100 * (macro.within_20pct_calories_rate or 0),
                    100 * (macro.grams_reconcile_rate or 0),
                    100 * (macro.macros_reconcile_rate or 0),
                    100 * (micro.micro_presence_rate or 0),
                    100 * (micro.fndds_micro_wmape or 0),
                )
            )


if __name__ == "__main__":
    main()
