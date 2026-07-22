#!/usr/bin/env python3
"""Parse model JSON output into macro predictions."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any


@dataclass
class ParsedPrediction:
    ok: bool
    calories: float | None = None
    protein_g: float | None = None
    carbs_g: float | None = None
    fat_g: float | None = None
    serving_size_grams: float | None = None
    raw: dict[str, Any] | None = None
    error: str | None = None


_JSON_BLOCK_RE = re.compile(r"```(?:json)?\s*(\{.*?\})\s*```", re.DOTALL | re.IGNORECASE)
_JSON_OBJECT_RE = re.compile(r"\{.*\}", re.DOTALL)


def _coerce_float(value: Any) -> float | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        stripped = value.strip()
        if not stripped or stripped.lower() == "null":
            return None
        return float(stripped)
    raise TypeError(f"Cannot coerce {value!r} to float")


def extract_json_text(text: str) -> str:
    text = text.strip()
    block = _JSON_BLOCK_RE.search(text)
    if block:
        return block.group(1).strip()
    obj = _JSON_OBJECT_RE.search(text)
    if obj:
        return obj.group(0).strip()
    return text


def parse_food_json(text: str) -> ParsedPrediction:
    try:
        payload = json.loads(extract_json_text(text))
    except (json.JSONDecodeError, TypeError) as exc:
        return ParsedPrediction(ok=False, error=f"json_decode: {exc}")

    if not isinstance(payload, dict):
        return ParsedPrediction(ok=False, error="root_not_object")

    try:
        calories = _coerce_float(payload.get("calories"))
        protein = _coerce_float(payload.get("protein"))
        carbs = _coerce_float(payload.get("carbs"))
        fat = _coerce_float(payload.get("fat"))
        serving = _coerce_float(payload.get("serving_size_grams"))
    except (TypeError, ValueError) as exc:
        return ParsedPrediction(ok=False, error=f"coerce: {exc}", raw=payload)

    if calories is None or protein is None or carbs is None or fat is None:
        return ParsedPrediction(
            ok=False,
            error="missing_macros",
            calories=calories,
            protein_g=protein,
            carbs_g=carbs,
            fat_g=fat,
            serving_size_grams=serving,
            raw=payload,
        )

    return ParsedPrediction(
        ok=True,
        calories=calories,
        protein_g=protein,
        carbs_g=carbs,
        fat_g=fat,
        serving_size_grams=serving,
        raw=payload,
    )
