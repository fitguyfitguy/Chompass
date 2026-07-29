#!/usr/bin/env python3
"""Oracle clarification answers for the simulated-clarification eval.

Derives ground-truth answers to the two clarification questions the app could
ask after a photo entry — portion size and hidden added fat — from manifest
ground truth, and formats them as user-tap answer strings for prompt injection.

Portion answers are split into three independently scoreable signals so product
claims match the information actually supplied:

- ``grams`` — exact total edible mass (Nutrition5k ``mass_g``)
- ``bucket`` — qualitative size chip only (small / regular / large / restaurant-size)
- ``amounts`` — stated per-ingredient quantity + unit (JFB ingredient list)

The historical ``compact_clarify_portion`` prompt injects the richest available
oracle for the sample (grams+bucket on N5k, stated amounts on JFB). Prefer the
split prompts when A/B-ing chip UX vs exact-weight correction.

Pure functions, no I/O. See docs/UNCERTAINTY_DRIVEN_ENTRY.md for the bet and
docs/FOOD_ACCURACY_BENCHMARK_STATUS.md for pre-registered thresholds.
"""

from __future__ import annotations

import re
from typing import Any, Literal

# Grams thresholds for the portion chip buckets (small / regular / large /
# restaurant-size). Boundaries are inclusive on the lower edge.
PORTION_BUCKETS: tuple[tuple[float, float, str], ...] = (
    (0.0, 150.0, "small"),
    (150.0, 350.0, "regular"),
    (350.0, 600.0, "large"),
    (600.0, float("inf"), "restaurant-size"),
)

PortionMode = Literal["full", "grams", "bucket", "amounts"]

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


def stated_amounts(sample: Any) -> list[str]:
    return [
        f"{_fmt_qty(i['quantity'])} {i['unit']} {i['name']}"
        for i in _ingredients(sample)
        if i.get("quantity") and i.get("unit") and i.get("name")
    ]


def portion_oracle(sample: Any) -> dict | None:
    """Ground-truth portion answer (richest available signal for the sample).

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
    stated = stated_amounts(sample)
    if stated:
        return {"kind": "stated_amounts", "amounts": stated}
    return None


def portion_signal(sample: Any, mode: PortionMode) -> dict | None:
    """Return the portion oracle restricted to one product-shaped signal.

    ``full`` keeps historical behavior (richest available). Split modes return
    None when that signal is unavailable for the sample.
    """
    if mode == "full":
        return portion_oracle(sample)

    if mode == "grams":
        if sample.mass_g is None or sample.mass_g <= 0:
            return None
        grams = round(float(sample.mass_g))
        return {
            "kind": "grams",
            "grams": grams,
            "bucket": portion_bucket(float(sample.mass_g)),
        }

    if mode == "bucket":
        if sample.mass_g is not None and sample.mass_g > 0:
            return {
                "kind": "bucket",
                "bucket": portion_bucket(float(sample.mass_g)),
            }
        return None

    if mode == "amounts":
        stated = stated_amounts(sample)
        if not stated:
            return None
        return {"kind": "stated_amounts", "amounts": stated}

    raise ValueError(f"unknown portion mode: {mode}")


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
    """Extras to merge into an enriched manifest (`clarify_portion` / `clarify_fat`).

    Also stores split signals when available so covered-id lists and prompts can
    score grams / bucket / amounts independently.
    """
    out: dict[str, dict] = {}
    portion = portion_oracle(sample)
    if portion is not None:
        out["clarify_portion"] = portion
    for mode in ("grams", "bucket", "amounts"):
        signal = portion_signal(sample, mode)  # type: ignore[arg-type]
        if signal is not None:
            out[f"clarify_portion_{mode}"] = signal
    fat = fat_oracle(sample)
    if fat is not None:
        out["clarify_fat"] = fat
    return out


def format_portion_answer(portion: dict, *, mode: PortionMode = "full") -> str:
    """Format a portion oracle dict as the injected user-answer string."""
    kind = portion.get("kind")
    if mode == "bucket" or kind == "bucket":
        bucket = portion.get("bucket") or "regular"
        return f"Portion size: {bucket}."
    if mode == "grams" or (mode == "full" and kind == "grams"):
        if portion.get("grams") is not None:
            if mode == "grams":
                return (
                    f"Portion: the whole portion weighs about {portion['grams']} g."
                )
            return (
                f"Portion: the whole portion weighs about {portion['grams']} g "
                f"({portion['bucket']})."
            )
    if mode == "amounts" or kind == "stated_amounts":
        amounts = ", ".join(portion.get("amounts") or [])
        return f"Portion: the amounts were {amounts}."
    # Fallback for unexpected shapes.
    if portion.get("grams") is not None:
        return f"Portion: the whole portion weighs about {portion['grams']} g."
    return "Portion: regular."


def format_fat_answer(fat: dict) -> str:
    # Qualitative on purpose: naming exact fat grams would leak the fat macro.
    if fat.get("present"):
        names = ", ".join(fat.get("names") or []) or "oil/dressing"
        return f"Added fat: yes, it includes {names}."
    return "Added fat: no added oil, butter, or dressing."


def clarify_answer_block(
    sample: Any,
    *,
    portion: bool = False,
    fat: bool = False,
    portion_mode: PortionMode = "full",
) -> str:
    """Injected block simulating chip taps; empty when no requested oracle exists."""
    lines: list[str] = []
    if portion:
        p = _resolve_portion_extra(sample, portion_mode)
        if p:
            lines.append(f"- {format_portion_answer(p, mode=portion_mode)}")
    if fat and (f := sample.extra.get("clarify_fat")):
        lines.append(f"- {format_fat_answer(f)}")
    if not lines:
        return ""
    return (
        "\nThe user answered a clarification question in the app:\n"
        + "\n".join(lines)
        + "\nTreat these answers as ground truth; adjust portion size and hidden-ingredient calories accordingly."
    )


def _resolve_portion_extra(sample: Any, portion_mode: PortionMode) -> dict | None:
    if portion_mode == "full":
        return sample.extra.get("clarify_portion") or portion_oracle(sample)

    keyed = sample.extra.get(f"clarify_portion_{portion_mode}")
    if keyed:
        return keyed

    signal = portion_signal(sample, portion_mode)
    if signal:
        return signal

    # Fixture / legacy manifests may only store the richest clarify_portion.
    full = sample.extra.get("clarify_portion")
    if not isinstance(full, dict):
        return None
    kind = full.get("kind")
    if portion_mode == "grams" and kind == "grams" and full.get("grams") is not None:
        return full
    if portion_mode == "bucket" and full.get("bucket"):
        return {"kind": "bucket", "bucket": full["bucket"]}
    if portion_mode == "amounts" and kind == "stated_amounts":
        return full
    return None
