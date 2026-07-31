#!/usr/bin/env python3
"""Offline unit checks for score_constituents / reconcile_constituents (no API)."""

from __future__ import annotations

from reconcile_constituents import reconcile_prediction
from score_constituents import aggregate_constituent_run, evaluate_gate, score_constituent_record


def _record(
    sample_id: str,
    *,
    parse_ok: bool,
    prediction: dict | None,
    abs_err: float = 10.0,
    gt: float = 100.0,
) -> dict:
    return {
        "id": sample_id,
        "parse_ok": parse_ok,
        "prediction": prediction,
        "error": None if parse_ok else "fail",
        "score": {
            "parse_ok": parse_ok,
            "abs_error_sum": abs_err if parse_ok else None,
            "gt_sum": gt if parse_ok else None,
            "within_20pct_calories": True if parse_ok else None,
        },
        "latency_ms": 1000,
        "prompt_tokens": 500,
    }


def test_happy_path() -> None:
    pred = {
        "calories": 300,
        "protein": 20.0,
        "carbs": 30.0,
        "fat": 10.0,
        "serving_size_grams": 200.0,
        "constituents": [
            {
                "name": "scrambled eggs",
                "calories": 180,
                "protein": 14.0,
                "carbs": 2.0,
                "fat": 8.0,
                "serving_size_grams": 100.0,
            },
            {
                "name": "toast",
                "calories": 120,
                "protein": 6.0,
                "carbs": 28.0,
                "fat": 2.0,
                "serving_size_grams": 100.0,
            },
        ],
    }
    scored = score_constituent_record(
        _record("s1", parse_ok=True, prediction=pred),
        {"min_components": 2, "expected_components": ["egg", "toast", "butter"]},
    )
    assert scored.parse_ok
    assert scored.meets_min_components
    assert scored.grams_reconcile_ok is True
    assert scored.macros_reconcile_ok is True
    assert scored.expected_hit_count == 2  # egg + toast; butter missing
    assert scored.has_constituents


def test_normalize_recovers_within_bound() -> None:
    pred = {
        "calories": 400,
        "protein": 20.0,
        "carbs": 40.0,
        "fat": 10.0,
        "serving_size_grams": 200.0,
        "constituents": [
            {
                "name": "egg",
                "calories": 100,
                "protein": 8.0,
                "carbs": 1.0,
                "fat": 5.0,
                "serving_size_grams": 80.0,
            },
            {
                "name": "toast",
                "calories": 220,
                "protein": 8.0,
                "carbs": 35.0,
                "fat": 4.0,
                "serving_size_grams": 90.0,
            },
        ],
    }
    # Pre-normalize grams err ~15%; macros also off — within 50% bound.
    reconciled = reconcile_prediction(pred)
    assert reconciled is not None
    rows = reconciled["constituents"]
    assert len(rows) == 2
    assert abs(sum(r["serving_size_grams"] for r in rows) - 200.0) < 0.15
    assert sum(int(r["calories"]) for r in rows) == 400
    scored = score_constituent_record(
        _record("s1", parse_ok=True, prediction=pred),
        {"min_components": 2, "expected_components": ["egg", "toast"]},
    )
    assert scored.grams_reconcile_ok is True
    assert scored.macros_reconcile_ok is True


def test_normalize_drops_when_too_far() -> None:
    pred = {
        "calories": 400,
        "protein": 20.0,
        "carbs": 40.0,
        "fat": 10.0,
        "serving_size_grams": 200.0,
        "constituents": [
            {
                "name": "egg",
                "calories": 10,
                "protein": 1.0,
                "carbs": 1.0,
                "fat": 1.0,
                "serving_size_grams": 10.0,
            },
            {
                "name": "toast",
                "calories": 10,
                "protein": 1.0,
                "carbs": 1.0,
                "fat": 1.0,
                "serving_size_grams": 10.0,
            },
        ],
    }
    reconciled = reconcile_prediction(pred)
    assert reconciled is not None
    assert reconciled["constituents"] == []
    scored = score_constituent_record(
        _record("s1", parse_ok=True, prediction=pred),
        {"min_components": 2, "expected_components": ["egg", "toast"]},
    )
    assert scored.has_constituents is False
    assert scored.meets_min_components is False


def test_omission_fails_min_components() -> None:
    pred = {
        "calories": 300,
        "protein": 20.0,
        "carbs": 30.0,
        "fat": 10.0,
        "serving_size_grams": 200.0,
        "constituents": [
            {
                "name": "burrito",
                "calories": 300,
                "protein": 20.0,
                "carbs": 30.0,
                "fat": 10.0,
                "serving_size_grams": 200.0,
            }
        ],
    }
    scored = score_constituent_record(
        _record("s1", parse_ok=True, prediction=pred),
        {"min_components": 3, "expected_components": ["burrito", "chip", "salsa"]},
    )
    assert scored.meets_min_components is False
    assert scored.expected_hit_count == 1


def test_gate_fails_on_wmape_regression() -> None:
    extras = {"s1": {"min_components": 2, "expected_components": ["egg", "toast"]}}
    good_pred = {
        "calories": 300,
        "protein": 20,
        "carbs": 30,
        "fat": 10,
        "serving_size_grams": 200,
        "constituents": [
            {"name": "egg", "calories": 150, "protein": 10, "carbs": 15, "fat": 5, "serving_size_grams": 100},
            {"name": "toast", "calories": 150, "protein": 10, "carbs": 15, "fat": 5, "serving_size_grams": 100},
        ],
    }
    base = aggregate_constituent_run(
        [_record("s1", parse_ok=True, prediction=good_pred, abs_err=5, gt=100)],
        extras,
    )
    cand = aggregate_constituent_run(
        [_record("s1", parse_ok=True, prediction=good_pred, abs_err=20, gt=100)],
        extras,
    )
    gate = evaluate_gate(
        strong=cand,
        weak=cand,
        baseline_strong_wmape=base.wmape,
        baseline_weak_wmape=base.wmape,
    )
    assert gate["passed"] is False
    names = {c["name"]: c["ok"] for c in gate["checks"]}
    assert names["strong_wmape_vs_baseline"] is False


if __name__ == "__main__":
    test_happy_path()
    test_normalize_recovers_within_bound()
    test_normalize_drops_when_too_far()
    test_omission_fails_min_components()
    test_gate_fails_on_wmape_regression()
    print("score_constituents offline checks OK")
