#!/usr/bin/env python3
"""Offline unit checks for score_constituents (no API)."""

from __future__ import annotations

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
    test_gate_fails_on_wmape_regression()
    print("score_constituents offline checks OK")
