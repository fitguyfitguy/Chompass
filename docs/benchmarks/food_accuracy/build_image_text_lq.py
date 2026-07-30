#!/usr/bin/env python3
"""Build image + vague-quantity (Lq) manifests — and N5k L1 identity notes.

Lq notes mimic a diary ``description`` with portion language but **no** exact
grams (``\\d+ g``). Sources:

- **N5k** (has ``mass_g``): ``{bucket} plate of {top ingredients}``
- **JFB** (stated amounts): meal title + coarsened ingredient quantities

Also writes Nutrition5k L1 (short identity from top ingredients by mass) because
dish IDs are not human meal titles.

Usage::

    uv run python docs/benchmarks/food_accuracy/build_image_text_lq.py
    uv run python docs/benchmarks/food_accuracy/build_image_text_lq.py --prefix jfb
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from clarify import portion_bucket
from image_text_variants import clone_sample
from schema import DATA_DIR, Sample, load_manifest, write_manifest

MANIFEST_DIR = DATA_DIR / "manifests"

GRAM_IN_TEXT = re.compile(
    r"(?i)(?:^|[\s,;(])\d+(?:\.\d+)?\s*(?:g|gram|grams)\b|\(\s*\d+(?:\.\d+)?\s*g\s*\)"
)

# Coarsen numeric quantities into diary-like vague language (no exact counts).
_QTY_VAGUE: list[tuple[float, float, str]] = [
    (0.0, 0.35, "a little"),
    (0.35, 0.75, "some"),
    (0.75, 1.25, "a"),
    (1.25, 1.75, "a couple"),
    (1.75, 2.75, "a couple"),
    (2.75, 4.5, "a few"),
    (4.5, float("inf"), "several"),
]


def assert_no_grams(sample_id: str, text: str) -> None:
    if GRAM_IN_TEXT.search(text):
        raise SystemExit(f"{sample_id}: Lq prompt still contains grams: {text!r}")


def vague_qty_word(quantity: float) -> str:
    for low, high, word in _QTY_VAGUE:
        if low <= quantity < high:
            return word
    return "some"


def _weighed_ingredients(sample: Sample) -> list[dict]:
    weighed = sample.extra.get("ingredients_weighed")
    if isinstance(weighed, list) and weighed:
        return [i for i in weighed if isinstance(i, dict)]
    ings = sample.extra.get("ingredients")
    if isinstance(ings, list):
        return [i for i in ings if isinstance(i, dict)]
    return []


def _name_only_ingredients(sample: Sample) -> list[str]:
    names: list[str] = []
    for item in _weighed_ingredients(sample):
        name = str(item.get("name") or "").strip()
        if name:
            names.append(name)
    # Prefer name-only list when weighed missing names
    if not names:
        raw = sample.extra.get("ingredients")
        if isinstance(raw, list):
            for item in raw:
                if isinstance(item, dict):
                    name = str(item.get("name") or "").strip()
                else:
                    name = str(item).strip()
                if name:
                    names.append(name)
    # Dedupe preserving order
    seen: set[str] = set()
    out: list[str] = []
    for name in names:
        key = name.casefold()
        if key in seen:
            continue
        seen.add(key)
        out.append(name)
    return out


def top_ingredient_names(sample: Sample, *, limit: int = 3) -> list[str]:
    weighed = _weighed_ingredients(sample)
    with_grams = [
        (float(i["grams"]), str(i.get("name") or "").strip())
        for i in weighed
        if i.get("name") and i.get("grams") is not None
    ]
    if with_grams:
        with_grams.sort(key=lambda t: t[0], reverse=True)
        names: list[str] = []
        seen: set[str] = set()
        for _, name in with_grams:
            key = name.casefold()
            if key in seen:
                continue
            seen.add(key)
            names.append(name)
            if len(names) >= limit:
                break
        return names
    return _name_only_ingredients(sample)[:limit]


def n5k_l1_text(sample: Sample) -> str | None:
    """Coarse identity without quantities — not the dish_id."""
    names = top_ingredient_names(sample, limit=2)
    if not names:
        return None
    if len(names) == 1:
        return names[0]
    return f"{names[0]} and {names[1]}"


def n5k_lq_text(sample: Sample) -> str | None:
    if sample.mass_g is None or sample.mass_g <= 0:
        return None
    bucket = portion_bucket(float(sample.mass_g))
    names = top_ingredient_names(sample, limit=3)
    if not names:
        return f"{bucket} portion"
    if len(names) == 1:
        dish = names[0]
    elif len(names) == 2:
        dish = f"{names[0]} and {names[1]}"
    else:
        dish = f"{names[0]}, {names[1]}, and {names[2]}"
    # "restaurant-size" reads awkwardly before "plate of"
    if bucket == "restaurant-size":
        return f"restaurant-size plate of {dish}"
    return f"{bucket} plate of {dish}"


def _coarsen_stated_line(quantity: object, unit: str, name: str) -> str:
    try:
        q = float(quantity)
    except (TypeError, ValueError):
        return name
    vague = vague_qty_word(q)
    unit_l = unit.strip().lower()
    # Diary tone: avoid "a bacon"; prefer "some …" or unit-aware phrasing
    if vague == "a":
        if unit_l in {"slice", "slices"}:
            return f"a slice of {name}"
        if unit_l in {"piece", "pieces"}:
            return f"a piece of {name}"
        if unit_l in {"tbsp", "tablespoon", "tablespoons"}:
            return f"a spoonful of {name}"
        if unit_l in {"tsp", "teaspoon", "teaspoons"}:
            return f"a dash of {name}"
        if unit_l in {"cup", "cups"}:
            return f"a cup of {name}"
        return f"some {name}"
    if vague in {"a little", "some"}:
        return f"{vague} {name}".strip()
    if unit_l in {"slice", "slices", "piece", "pieces"}:
        return f"{vague} {name}"
    return f"{vague} {name}"


def jfb_lq_text(sample: Sample) -> str | None:
    title = (sample.meal_name or "").strip()
    ings = _weighed_ingredients(sample)
    # JFB stores qty/unit on the same ingredients list used by clarify
    parts: list[str] = []
    for item in ings:
        name = str(item.get("name") or "").strip()
        if not name:
            continue
        qty = item.get("quantity")
        unit = str(item.get("unit") or "").strip()
        if qty is not None and unit:
            parts.append(_coarsen_stated_line(qty, unit, name))
        else:
            parts.append(name)
        if len(parts) >= 4:
            break
    if not parts and not title:
        return None
    if not parts:
        return title or None
    body = ", ".join(parts)
    if title:
        return f"{title} — {body}"
    return body


def lq_text_for(sample: Sample) -> str | None:
    if sample.source == "nutrition5k" or (sample.id or "").startswith("n5k-"):
        return n5k_lq_text(sample)
    if sample.source == "jfb" or (sample.id or "").startswith("jfb-"):
        return jfb_lq_text(sample)
    # Generic fallback: bucket if mass else meal name
    if sample.mass_g is not None and sample.mass_g > 0:
        return n5k_lq_text(sample)
    return jfb_lq_text(sample)


def build_lq(
    samples: list[Sample],
    out_path: Path,
) -> int:
    out: list[Sample] = []
    for sample in samples:
        text = lq_text_for(sample)
        if not text:
            # Still emit a row with empty text skipped → clone without text loses Lq;
            # prefer skip so eval only scores samples with notes.
            continue
        assert_no_grams(sample.id, text)
        cloned = clone_sample(sample, text=text)
        cloned.notes = (cloned.notes or "")
        note = "Lq vague quantity note; no grams in text"
        cloned.notes = f"{cloned.notes}; {note}" if cloned.notes else note
        cloned.extra = dict(cloned.extra)
        cloned.extra["note_level"] = "lq"
        out.append(cloned)
    write_manifest(out_path, out)
    return len(out)


def build_n5k_l1(samples: list[Sample], out_path: Path) -> int:
    out: list[Sample] = []
    for sample in samples:
        text = n5k_l1_text(sample)
        if not text:
            continue
        assert_no_grams(sample.id, text)
        cloned = clone_sample(sample, text=text)
        cloned.extra = dict(cloned.extra)
        cloned.extra["note_level"] = "l1"
        out.append(cloned)
    write_manifest(out_path, out)
    return len(out)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--prefix",
        nargs="*",
        default=["jfb", "n5k"],
        help="Manifest stems under data/manifests (default: jfb n5k)",
    )
    parser.add_argument(
        "--manifests-dir",
        type=Path,
        default=MANIFEST_DIR,
    )
    args = parser.parse_args()

    for prefix in args.prefix:
        l0_path = args.manifests_dir / f"{prefix}.jsonl"
        if not l0_path.exists():
            print(f"SKIP {prefix}: missing {l0_path}", file=sys.stderr)
            continue
        samples = load_manifest(l0_path)
        lq_path = args.manifests_dir / f"{prefix}_image_text_lq.jsonl"
        n = build_lq(samples, lq_path)
        print(f"{prefix}: wrote {n} Lq samples -> {lq_path}")
        if prefix == "n5k":
            l1_path = args.manifests_dir / "n5k_image_text_l1.jsonl"
            n1 = build_n5k_l1(samples, l1_path)
            print(f"n5k: wrote {n1} L1 samples -> {l1_path}")


if __name__ == "__main__":
    main()
