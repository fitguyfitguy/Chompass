#!/usr/bin/env python3
"""Scoring functions for macro nutrient accuracy."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Iterable

from schema import GroundTruth, MACRO_FIELDS
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

    def to_dict(self) -> dict:
        return asdict(self)


def _mape(abs_err: float, gt: float) -> float | None:
    if gt == 0:
        return None if abs_err == 0 else float("inf")
    return abs_err / abs(gt)


def score_sample(sample_id: str, gt: GroundTruth, pred: ParsedPrediction) -> SampleScore:
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
    )
