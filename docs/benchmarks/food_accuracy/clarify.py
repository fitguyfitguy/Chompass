#!/usr/bin/env python3
"""Oracle clarification answers for the simulated-clarification eval.

Derives ground-truth answers to the two clarification questions the app could
ask after a photo entry — portion size and hidden added fat — from manifest
ground truth, and formats them as user-tap answer strings for prompt injection.

Pure functions, no I/O. See docs/UNCERTAINTY_DRIVEN_ENTRY.md for the bet and
docs/FOOD_ACCURACY_BENCHMARK_STATUS.md for pre-registered thresholds.
"""

from __future__ import annotations

import re
from typing import Any

# Grams thresholds for the portion chip buckets (small / regular / large /
# restaurant-size). Boundaries are inclusive on the lower edge.
PORTION_BUCKETS: tuple[tuple[float, float, str], ...] = (
    (0.0, 150.0, "small"),
    (150.0, 350.0, "regular"),
    (350.0, 600.0, "large"),
    (600.0, float("inf"), "restaurant-size"),
)

# Ingredient names that indicate added fat the camera cannot quantify
# (cooking oil, dressings, spreads, fried coatings).
FAT_LEXICON = re.compile(
    r"\b(oil|butter|ghee|margarine|mayo(?:nnaise)?|aioli|dressing|vinaigrette|"
    r"tahini|hummus|pesto|peanut butter|almond butter|nut butter|sour cream|"
    r"heavy cream|whipped cream|cream cheese|cheese sauce|gravy|fried|batter(?:ed)?|"
    r"tempura|lard|shortening|bacon fat|drippings)\b",
    re.IGNORECASE,
)


def portion_bucket(grams: float) -> str:
    for low, high, name in PORTION_BUCKETS:
        if low <= grams < high:
            return name
    return PORTION_BUCKETS[-1][2]


def _ingredients(sample: Any) -> list[dict]:
    ings = sample.extra.get("ingredients")
    return [i for i in ings if isinstance(i, dict)] if isinstance(ings, list) else []


def _fmt_qty(quantity: Any) -> str:
    if isinstance(quantity, float) and quantity.is_integer():
        return str(int(quantity))
    return str(quantity)


def portion_oracle(sample: Any) -> dict | None:
    """Ground-truth portion answer.

    Nutrition5k has true total mass → grams + bucket. JFB has no total mass;
    the oracle is the stated per-ingredient amounts (quantity + unit), the shape
    a user could answer with. The two are reported per-dataset, never pooled.
    """
    if sample.mass_g is not None and sample.mass_g > 0:
        return {
            "kind": "grams",
            "grams": round(float(sample.mass_g)),
            "bucket": portion_bucket(float(sample.mass_g)),
        }
    stated = [
        f"{_fmt_qty(i['quantity'])} {i['unit']} {i['name']}"
        for i in _ingredients(sample)
        if i.get("quantity") and i.get("unit") and i.get("name")
    ]
    if stated:
        return {"kind": "stated_amounts", "amounts": stated}
    return None


def fat_oracle(sample: Any) -> dict | None:
    """Ground-truth added-fat answer from the ingredient list.

    Returns None when no ingredient list exists (oracle unavailable — the item
    is excluded from fat conditions rather than answered "no added fat").
    """
    ings = _ingredients(sample)
    if not ings:
        return None
    hits = [i for i in ings if i.get("name") and FAT_LEXICON.search(str(i["name"]))]
    return {
        "present": bool(hits),
        "names": [str(i["name"]) for i in hits],
    }


def derive_clarify_fields(sample: Any) -> dict[str, dict]:
    """Extras to merge into an enriched manifest (`clarify_portion` / `clarify_fat`)."""
    out: dict[str, dict] = {}
    portion = portion_oracle(sample)
    if portion is not None:
        out["clarify_portion"] = portion
    fat = fat_oracle(sample)
    if fat is not None:
        out["clarify_fat"] = fat
    return out


def format_portion_answer(portion: dict) -> str:
    if portion.get("kind") == "grams":
        return (
            f"Portion: the whole portion weighs about {portion['grams']} g "
            f"({portion['bucket']})."
        )
    amounts = ", ".join(portion.get("amounts") or [])
    return f"Portion: the amounts were {amounts}."


def format_fat_answer(fat: dict) -> str:
    # Qualitative on purpose: naming exact fat grams would leak the fat macro.
    if fat.get("present"):
        names = ", ".join(fat.get("names") or []) or "oil/dressing"
        return f"Added fat: yes, it includes {names}."
    return "Added fat: no added oil, butter, or dressing."


def clarify_answer_block(
    sample: Any, *, portion: bool = False, fat: bool = False
) -> str:
    """Injected block simulating chip taps; empty when no requested oracle exists."""
    lines: list[str] = []
    if portion and (p := sample.extra.get("clarify_portion")):
        lines.append(f"- {format_portion_answer(p)}")
    if fat and (f := sample.extra.get("clarify_fat")):
        lines.append(f"- {format_fat_answer(f)}")
    if not lines:
        return ""
    return (
        "\nThe user answered a clarification question in the app:\n"
        + "\n".join(lines)
        + "\nTreat these answers as ground truth; adjust portion size and hidden-ingredient calories accordingly."
    )
