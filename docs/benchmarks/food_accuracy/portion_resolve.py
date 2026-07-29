"""Android-aligned portion resolution for grounded eval.

Mirrors `PortionResolver.kt` precedence (never silent 100 g / GT mass fill).
"""

from __future__ import annotations

import re
from typing import Any

VAGUE_UNITS = {
    "slice",
    "slices",
    "piece",
    "pieces",
    "large",
    "medium",
    "small",
}

HOUSEHOLD_GRAMS: dict[str, float] = {
    "g": 1.0,
    "gram": 1.0,
    "grams": 1.0,
    "kg": 1000.0,
    "ml": 1.0,
    "l": 1000.0,
    "liter": 1000.0,
    "litre": 1000.0,
    "oz": 28.35,
    "ounce": 28.35,
    "ounces": 28.35,
    "lb": 453.6,
    "pound": 453.6,
    "tbsp": 15.0,
    "tablespoon": 15.0,
    "tablespoons": 15.0,
    "tsp": 5.0,
    "teaspoon": 5.0,
    "teaspoons": 5.0,
    "cup": 240.0,
    "cups": 240.0,
    "can": 330.0,
    "cans": 330.0,
    "glass": 240.0,
    "glasses": 240.0,
    "scoop": 30.0,
    "scoops": 30.0,
    "slice": 30.0,
    "slices": 30.0,
    "piece": 50.0,
    "pieces": 50.0,
    "bar": 50.0,
    "bars": 50.0,
    "large": 50.0,
    "medium": 118.0,
    "small": 80.0,
}


def canonical_unit(unit: str) -> str:
    u = unit.strip().lower()
    aliases = {
        "tbs": "tbsp",
        "tbl": "tbsp",
        "tbls": "tbsp",
        "tablespoon": "tbsp",
        "tablespoons": "tbsp",
        "teaspoon": "tsp",
        "teaspoons": "tsp",
        "ounce": "oz",
        "ounces": "oz",
        "pound": "lb",
        "pounds": "lb",
        "lbs": "lb",
        "gram": "g",
        "grams": "g",
        "liter": "l",
        "litre": "l",
        "liters": "l",
        "litres": "l",
    }
    return aliases.get(u, u)


def units_match(a: str, b: str) -> bool:
    ca, cb = canonical_unit(a), canonical_unit(b)
    if ca == cb:
        return True
    if ca in cb or cb in ca:
        return True
    if ca.rstrip("s") == cb.rstrip("s"):
        return True
    return False


def _unit_grams_from_candidate(
    unit: str,
    candidate_unit: str | None,
    candidate_grams: float | None,
) -> float | None:
    if candidate_grams is None or candidate_grams <= 0 or not candidate_unit:
        return None
    if units_match(unit, candidate_unit):
        return float(candidate_grams)
    return None


def _parse_hint_quantity(hint: str | None) -> tuple[float, str] | None:
    if not hint or not str(hint).strip():
        return None
    m = re.search(r"(?i)\b(\d+(?:\.\d+)?)\s*([a-zA-Z]+)", str(hint).strip())
    if not m:
        return None
    try:
        qty = float(m.group(1))
    except ValueError:
        return None
    if qty <= 0:
        return None
    return qty, m.group(2).lower()


def resolve_portion(
    *,
    quantity: float | None = None,
    unit: str | None = None,
    estimated_grams: float | None = None,
    portion_hint: str | None = None,
    gram_override: float | None = None,
    candidate_serving_grams: float | None = None,
    candidate_serving_unit: str | None = None,
) -> dict[str, Any]:
    """Return {grams, source, needs_user_confirmation, evidence}.

    grams is None when unresolved — never invent 100 g or fill from GT mass.
    """
    if gram_override is not None and gram_override > 0:
        return {
            "grams": float(gram_override),
            "source": "override",
            "needs_user_confirmation": False,
            "evidence": f"user_override={gram_override}g",
        }

    if quantity is not None and quantity > 0 and unit and str(unit).strip():
        unit_c = canonical_unit(str(unit))
        from_cand = _unit_grams_from_candidate(
            unit_c, candidate_serving_unit, candidate_serving_grams
        )
        if from_cand is not None:
            return {
                "grams": float(quantity) * from_cand,
                "source": "quantity_unit",
                "needs_user_confirmation": unit_c in VAGUE_UNITS,
                "evidence": f"quantity={quantity}×{unit_c} via candidate serving {from_cand}g",
            }
        per = HOUSEHOLD_GRAMS.get(unit_c)
        if per is not None:
            return {
                "grams": float(quantity) * per,
                "source": "quantity_unit",
                "needs_user_confirmation": unit_c in VAGUE_UNITS,
                "evidence": f"quantity={quantity}×{unit_c} household {per}g",
            }

    hint = _parse_hint_quantity(portion_hint)
    if hint is not None:
        hint_qty, hint_unit_raw = hint
        hint_unit = canonical_unit(hint_unit_raw)
        grams_per = _unit_grams_from_candidate(
            hint_unit, candidate_serving_unit, candidate_serving_grams
        ) or HOUSEHOLD_GRAMS.get(hint_unit)
        if grams_per is not None:
            return {
                "grams": hint_qty * grams_per,
                "source": "heuristic",
                "needs_user_confirmation": True,
                "evidence": f"portion_hint {hint_qty}×{hint_unit}",
            }

    if estimated_grams is not None and estimated_grams > 0:
        return {
            "grams": float(estimated_grams),
            "source": "estimated_grams",
            "needs_user_confirmation": False,
            "evidence": f"estimated_grams={estimated_grams}",
        }

    if candidate_serving_grams is not None and candidate_serving_grams > 0:
        return {
            "grams": float(candidate_serving_grams),
            "source": "candidate_serving",
            "needs_user_confirmation": True,
            "evidence": f"candidate_serving={candidate_serving_grams}g",
        }

    return {
        "grams": None,
        "source": "unresolved",
        "needs_user_confirmation": True,
        "evidence": portion_hint or "portion unresolved",
    }
