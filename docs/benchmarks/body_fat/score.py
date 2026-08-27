#!/usr/bin/env python3
"""Score BF% predictions in percentage points (not food WMAPE)."""

from __future__ import annotations

import json
import math
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass
class Agg:
    n: int
    n_pred: int
    parse_rate: float
    mae: float | None
    rmse: float | None
    bias: float | None
    ccc: float | None
    within_2: float | None
    within_3: float | None
    within_5: float | None


def _ccc(y: list[float], yhat: list[float]) -> float | None:
    n = len(y)
    if n < 2:
        return None
    my = sum(y) / n
    mh = sum(yhat) / n
    vy = sum((v - my) ** 2 for v in y) / n
    vh = sum((v - mh) ** 2 for v in yhat) / n
    cov = sum((a - my) * (b - mh) for a, b in zip(y, yhat)) / n
    den = vy + vh + (my - mh) ** 2
    if den == 0:
        return None
    return 2 * cov / den


def aggregate(pairs: list[tuple[float, float | None]]) -> Agg:
    n = len(pairs)
    valid = [(g, p) for g, p in pairs if p is not None and math.isfinite(p)]
    n_pred = len(valid)
    parse_rate = n_pred / n if n else 0.0
    if not valid:
        return Agg(n, n_pred, parse_rate, None, None, None, None, None, None, None)
    errs = [p - g for g, p in valid]
    abs_e = [abs(e) for e in errs]
    mae = sum(abs_e) / n_pred
    rmse = math.sqrt(sum(e * e for e in errs) / n_pred)
    bias = sum(errs) / n_pred
    y = [g for g, _ in valid]
    yhat = [p for _, p in valid]
    return Agg(
        n=n,
        n_pred=n_pred,
        parse_rate=parse_rate,
        mae=mae,
        rmse=rmse,
        bias=bias,
        ccc=_ccc(y, yhat),
        within_2=sum(1 for e in abs_e if e <= 2) / n_pred,
        within_3=sum(1 for e in abs_e if e <= 3) / n_pred,
        within_5=sum(1 for e in abs_e if e <= 5) / n_pred,
    )


def slice_key(sex: str, bmi: float, age: int) -> dict[str, str]:
    if bmi < 25:
        tert = "bmi_lt25"
    elif bmi < 30:
        tert = "bmi_25_30"
    else:
        tert = "bmi_ge30"
    if age < 40:
        aband = "age_18_39"
    elif age < 60:
        aband = "age_40_59"
    else:
        aband = "age_60p"
    return {"sex": sex, "bmi": tert, "age": aband}


def write_json(path: Path, obj: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, default=asdict), encoding="utf-8")
