#!/usr/bin/env python3
"""Scoring functions for macro nutrient accuracy."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Iterable

from schema import GroundTruth, MACRO_FIELDS, MICRO_FIELDS
from parse import ParsedPrediction


@dataclass
class SampleScore:
    id: str
    parse_ok: bool
    mae_calories: float | None = None
    mae_protein_g: float | None = None
    mae_carbs_g: float | None = None
    mae_fat_g: float | None = None
    mape_calories: float | None = None
    mape_protein_g: float | None = None
    mape_carbs_g: float | None = None
    mape_fat_g: float | None = None
    within_20pct_calories: bool | None = None
    abs_error_sum: float | None = None
    gt_sum: float | None = None
    error: str | None = None
    # Micronutrients (GT field name -> value). `mae_micro`/`mape_micro` are
    # only populated for nutrients where GT is present (currently FNDDS text
    # only, see schema.MICRO_FIELDS); `micro_present` tracks whether the
    # *model* returned a non-null value regardless of GT availability, so
    # presence rate can be measured even on datasets/prompts with no GT.
    mae_micro: dict[str, float] = field(default_factory=dict)
    mape_micro: dict[str, float | None] = field(default_factory=dict)
    micro_present: dict[str, bool] = field(default_factory=dict)
    micro_abs_error_sum: float | None = None
    micro_gt_sum: float | None = None

    def to_dict(self) -> dict:
        return asdict(self)


def _mape(abs_err: float, gt: float) -> float | None:
    if gt == 0:
        return None if abs_err == 0 else float("inf")
    return abs_err / abs(gt)


def score_sample(
    sample_id: str,
    gt: GroundTruth,
    pred: ParsedPrediction,
    *,
    micro_gt: dict[str, float | None] | None = None,
) -> SampleScore:
    if not pred.ok:
        return SampleScore(id=sample_id, parse_ok=False, error=pred.error)

    assert pred.calories is not None
    assert pred.protein_g is not None
    assert pred.carbs_g is not None
    assert pred.fat_g is not None

    pred_map = {
        "calories": pred.calories,
        "protein_g": pred.protein_g,
        "carbs_g": pred.carbs_g,
        "fat_g": pred.fat_g,
    }
    gt_map = {
        "calories": gt.calories,
        "protein_g": gt.protein_g,
        "carbs_g": gt.carbs_g,
        "fat_g": gt.fat_g,
    }

    mae: dict[str, float] = {}
    mape: dict[str, float | None] = {}
    abs_sum = 0.0
    gt_sum = 0.0
    for key in MACRO_FIELDS:
        abs_err = abs(pred_map[key] - gt_map[key])
        mae[key] = abs_err
        mape[key] = _mape(abs_err, gt_map[key])
        abs_sum += abs_err
        gt_sum += abs(gt_map[key])

    cal_mape = mape["calories"]
    within_20 = cal_mape is not None and cal_mape <= 0.20

    micro_gt = micro_gt or {}
    mae_micro: dict[str, float] = {}
    mape_micro: dict[str, float | None] = {}
    micro_present: dict[str, bool] = {}
    micro_abs_sum = 0.0
    micro_gt_sum = 0.0
    for gt_key, pred_key in MICRO_FIELDS.items():
        pred_val = pred.micros.get(pred_key)
        micro_present[gt_key] = pred_val is not None
        gt_val = micro_gt.get(gt_key)
        if gt_val is None or pred_val is None:
            continue  # no GT for this nutrient/dataset, or model returned null
        abs_err = abs(pred_val - gt_val)
        mae_micro[gt_key] = abs_err
        mape_micro[gt_key] = _mape(abs_err, gt_val)
        micro_abs_sum += abs_err
        micro_gt_sum += abs(gt_val)

    return SampleScore(
        id=sample_id,
        parse_ok=True,
        mae_calories=mae["calories"],
        mae_protein_g=mae["protein_g"],
        mae_carbs_g=mae["carbs_g"],
        mae_fat_g=mae["fat_g"],
        mape_calories=mape["calories"],
        mape_protein_g=mape["protein_g"],
        mape_carbs_g=mape["carbs_g"],
        mape_fat_g=mape["fat_g"],
        within_20pct_calories=within_20,
        abs_error_sum=abs_sum,
        gt_sum=gt_sum,
        mae_micro=mae_micro,
        mape_micro=mape_micro,
        micro_present=micro_present,
        micro_abs_error_sum=micro_abs_sum if mae_micro else None,
        micro_gt_sum=micro_gt_sum if mae_micro else None,
    )


@dataclass
class AggregateScore:
    n: int
    parse_ok_rate: float
    wmape: float | None
    mae_calories: float | None
    mae_protein_g: float | None
    mae_carbs_g: float | None
    mae_fat_g: float | None
    mape_calories: float | None
    mape_protein_g: float | None
    mape_carbs_g: float | None
    mape_fat_g: float | None
    within_20pct_calories_rate: float | None
    # Per-nutrient means (null-filtered), n_micro = # samples with non-null GT
    # for that nutrient (varies per nutrient; only FNDDS text has GT today),
    # presence_rate = fraction of parsed samples where the model returned a
    # non-null value (independent of GT availability).
    mae_micro: dict[str, float] = field(default_factory=dict)
    mape_micro: dict[str, float] = field(default_factory=dict)
    n_micro: dict[str, int] = field(default_factory=dict)
    presence_rate: dict[str, float] = field(default_factory=dict)
    micro_wmape: float | None = None

    def to_dict(self) -> dict:
        return asdict(self)


def _mean(values: Iterable[float | None]) -> float | None:
    nums = [v for v in values if v is not None and v != float("inf")]
    if not nums:
        return None
    return sum(nums) / len(nums)


def aggregate_scores(scores: list[SampleScore]) -> AggregateScore:
    if not scores:
        return AggregateScore(
            n=0,
            parse_ok_rate=0.0,
            wmape=None,
            mae_calories=None,
            mae_protein_g=None,
            mae_carbs_g=None,
            mae_fat_g=None,
            mape_calories=None,
            mape_protein_g=None,
            mape_carbs_g=None,
            mape_fat_g=None,
            within_20pct_calories_rate=None,
        )

    parsed = [s for s in scores if s.parse_ok]
    abs_total = sum(s.abs_error_sum or 0.0 for s in parsed)
    gt_total = sum(s.gt_sum or 0.0 for s in parsed)
    wmape = abs_total / gt_total if gt_total > 0 else None

    within = [s.within_20pct_calories for s in parsed if s.within_20pct_calories is not None]

    micro_abs_total = sum(s.micro_abs_error_sum or 0.0 for s in parsed)
    micro_gt_total = sum(s.micro_gt_sum or 0.0 for s in parsed)
    micro_wmape = micro_abs_total / micro_gt_total if micro_gt_total > 0 else None

    mae_micro: dict[str, float] = {}
    mape_micro: dict[str, float] = {}
    n_micro: dict[str, int] = {}
    presence_rate: dict[str, float] = {}
    for gt_key in MICRO_FIELDS:
        mae_vals = [s.mae_micro[gt_key] for s in parsed if gt_key in s.mae_micro]
        mape_vals = [s.mape_micro[gt_key] for s in parsed if gt_key in s.mape_micro]
        if mae_vals:
            mae_micro[gt_key] = _mean(mae_vals)  # type: ignore[assignment]
            n_micro[gt_key] = len(mae_vals)
        mape_mean = _mean(mape_vals)
        if mape_mean is not None:
            mape_micro[gt_key] = mape_mean
        present = [s.micro_present.get(gt_key) for s in parsed]
        present = [p for p in present if p is not None]
        if present:
            presence_rate[gt_key] = sum(1 for p in present if p) / len(present)

    return AggregateScore(
        n=len(scores),
        parse_ok_rate=len(parsed) / len(scores),
        wmape=wmape,
        mae_calories=_mean(s.mae_calories for s in parsed),
        mae_protein_g=_mean(s.mae_protein_g for s in parsed),
        mae_carbs_g=_mean(s.mae_carbs_g for s in parsed),
        mae_fat_g=_mean(s.mae_fat_g for s in parsed),
        mape_calories=_mean(s.mape_calories for s in parsed),
        mape_protein_g=_mean(s.mape_protein_g for s in parsed),
        mape_carbs_g=_mean(s.mape_carbs_g for s in parsed),
        mape_fat_g=_mean(s.mape_fat_g for s in parsed),
        within_20pct_calories_rate=(sum(1 for w in within if w) / len(within)) if within else None,
        mae_micro=mae_micro,
        mape_micro=mape_micro,
        n_micro=n_micro,
        presence_rate=presence_rate,
        micro_wmape=micro_wmape,
    )
