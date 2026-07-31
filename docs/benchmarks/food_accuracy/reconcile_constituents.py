#!/usr/bin/env python3
"""Deterministic constituent reconciliation (research + production contract).

Top-level meal nutrition is authoritative. When a model emits constituents whose
grams/macros do not sum to the meal totals, apply a bounded proportional scale
so the breakdown matches. If the relative mismatch exceeds MAX_REL_ERROR, drop
the optional breakdown rather than inventing a misleading correction.

Shared conceptually with Android/PWA client helpers; keep the bounds and
rounding rules in sync.
"""

from __future__ import annotations

from copy import deepcopy
from typing import Any

# Scorer gate tolerance after normalization (exact match within float noise).
RECONCILE_TOL = 0.05
# Drop constituents when pre-correction relative error exceeds this bound.
MAX_REL_ERROR = 0.50
# Cap how many constituent rows we keep from a model response.
MAX_CONSTITUENTS = 12


def _float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        out = float(value)
    except (TypeError, ValueError):
        return None
    if out != out or out in (float("inf"), float("-inf")):
        return None
    return out


def extract_constituent_list(prediction: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not isinstance(prediction, dict):
        return []
    for key in ("constituents", "ingredients", "components", "items"):
        raw = prediction.get(key)
        if isinstance(raw, list):
            return [c for c in raw if isinstance(c, dict)]
    return []


def _valid_row(raw: dict[str, Any]) -> dict[str, Any] | None:
    name = str(raw.get("name") or "").strip()
    if not name:
        return None
    grams = _float(raw.get("serving_size_grams"))
    cal = _float(raw.get("calories"))
    protein = _float(raw.get("protein"))
    carbs = _float(raw.get("carbs"))
    fat = _float(raw.get("fat"))
    if None in (grams, cal, protein, carbs, fat):
        return None
    if grams <= 0 or cal < 0 or protein < 0 or carbs < 0 or fat < 0:
        return None
    row = {
        "name": name,
        "calories": cal,
        "protein": protein,
        "carbs": carbs,
        "fat": fat,
        "serving_size_grams": grams,
    }
    emoji = raw.get("emoji")
    if isinstance(emoji, str) and emoji.strip():
        row["emoji"] = emoji.strip()
    units = raw.get("unit_options")
    if isinstance(units, list):
        row["unit_options"] = units
    return row


def _rel_error(sum_v: float, total: float) -> float | None:
    if total <= 0:
        return None if sum_v == 0 else float("inf")
    return abs(sum_v - total) / total


def _macro_rel_error(
    sum_cal: float,
    sum_p: float,
    sum_c: float,
    sum_f: float,
    meal_cal: float,
    meal_p: float,
    meal_c: float,
    meal_f: float,
) -> float | None:
    denom = abs(meal_cal) + abs(meal_p) + abs(meal_c) + abs(meal_f)
    if denom <= 0:
        return None if (sum_cal + sum_p + sum_c + sum_f) == 0 else float("inf")
    err = (
        abs(sum_cal - meal_cal)
        + abs(sum_p - meal_p)
        + abs(sum_c - meal_c)
        + abs(sum_f - meal_f)
    )
    return err / denom


def _scale_field(rows: list[dict[str, Any]], key: str, target: float, as_int: bool = False) -> None:
    total = sum(float(r[key]) for r in rows)
    if total <= 0:
        if target <= 0:
            return
        # Distribute evenly when model emitted zeros but meal has a value.
        each = target / len(rows)
        for r in rows:
            r[key] = int(round(each)) if as_int else each
        # Fix residual on last row.
        if as_int:
            residual = int(round(target)) - sum(int(r[key]) for r in rows)
            rows[-1][key] = int(rows[-1][key]) + residual
        else:
            residual = target - sum(float(r[key]) for r in rows)
            rows[-1][key] = float(rows[-1][key]) + residual
        return

    factor = target / total
    scaled = []
    for r in rows:
        value = float(r[key]) * factor
        if as_int:
            value = int(round(value))
        r[key] = value
        scaled.append(value)
    residual = (int(round(target)) if as_int else target) - sum(scaled)
    if as_int:
        rows[-1][key] = int(rows[-1][key]) + int(residual)
    else:
        rows[-1][key] = float(rows[-1][key]) + float(residual)


def reconcile_prediction(
    prediction: dict[str, Any] | None,
    *,
    max_rel_error: float = MAX_REL_ERROR,
) -> dict[str, Any] | None:
    """Return a copy of prediction with constituents reconciled or cleared.

    Top-level calories/protein/carbs/fat/serving_size_grams are never changed.
    """
    if not isinstance(prediction, dict):
        return prediction
    out = deepcopy(prediction)
    raw_rows = extract_constituent_list(out)
    rows = []
    for raw in raw_rows:
        valid = _valid_row(raw)
        if valid is not None:
            rows.append(valid)
        if len(rows) >= MAX_CONSTITUENTS:
            break

    # Canonical key only on the output.
    for alias in ("ingredients", "components", "items"):
        out.pop(alias, None)

    if not rows:
        out["constituents"] = []
        return out

    meal_g = _float(out.get("serving_size_grams"))
    meal_cal = _float(out.get("calories"))
    meal_p = _float(out.get("protein"))
    meal_c = _float(out.get("carbs"))
    meal_f = _float(out.get("fat"))
    if None in (meal_g, meal_cal, meal_p, meal_c, meal_f) or meal_g <= 0:
        out["constituents"] = []
        return out

    sum_g = sum(float(r["serving_size_grams"]) for r in rows)
    sum_cal = sum(float(r["calories"]) for r in rows)
    sum_p = sum(float(r["protein"]) for r in rows)
    sum_c = sum(float(r["carbs"]) for r in rows)
    sum_f = sum(float(r["fat"]) for r in rows)

    g_err = _rel_error(sum_g, meal_g)
    m_err = _macro_rel_error(sum_cal, sum_p, sum_c, sum_f, meal_cal, meal_p, meal_c, meal_f)
    if g_err is None or m_err is None or g_err > max_rel_error or m_err > max_rel_error:
        out["constituents"] = []
        return out

    _scale_field(rows, "serving_size_grams", meal_g, as_int=False)
    _scale_field(rows, "calories", meal_cal, as_int=True)
    _scale_field(rows, "protein", meal_p, as_int=False)
    _scale_field(rows, "carbs", meal_c, as_int=False)
    _scale_field(rows, "fat", meal_f, as_int=False)

    # Round macros to 1dp after residual fix for stable wire values.
    for r in rows:
        r["serving_size_grams"] = round(float(r["serving_size_grams"]), 1)
        r["protein"] = round(float(r["protein"]), 1)
        r["carbs"] = round(float(r["carbs"]), 1)
        r["fat"] = round(float(r["fat"]), 1)
        r["calories"] = int(r["calories"])

    # Final residual fix after rounding (put remainder on last row).
    rows[-1]["serving_size_grams"] = round(
        meal_g - sum(float(r["serving_size_grams"]) for r in rows[:-1]), 1
    )
    rows[-1]["protein"] = round(meal_p - sum(float(r["protein"]) for r in rows[:-1]), 1)
    rows[-1]["carbs"] = round(meal_c - sum(float(r["carbs"]) for r in rows[:-1]), 1)
    rows[-1]["fat"] = round(meal_f - sum(float(r["fat"]) for r in rows[:-1]), 1)
    rows[-1]["calories"] = int(round(meal_cal)) - sum(int(r["calories"]) for r in rows[:-1])

    # Guard against a negative last-row residual from rounding; drop if so.
    last = rows[-1]
    if (
        float(last["serving_size_grams"]) <= 0
        or int(last["calories"]) < 0
        or float(last["protein"]) < 0
        or float(last["carbs"]) < 0
        or float(last["fat"]) < 0
    ):
        out["constituents"] = []
        return out

    out["constituents"] = rows
    return out
