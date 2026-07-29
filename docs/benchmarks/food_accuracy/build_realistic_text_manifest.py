#!/usr/bin/env python3
"""Build eval_grounded_realistic_text.jsonl — readiness gate without gram-rich prompts.

Derives vague/household lines from eval_text.jsonl (strip mass from prompts; keep
macros/mass_g for scoring). Adds curated multi + branded slices. Offline: no
network fetch at build time.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from schema import Sample, load_manifest, validate_sample, write_manifest

GRAM_IN_TEXT = re.compile(
    r"(?i)(?:^|[\s,;(])\d+(?:\.\d+)?\s*(?:g|gram|grams)\b|\(\s*\d+(?:\.\d+)?\s*g\s*\)"
)

# Strip explicit mass / paren mass from FNDDS prompts.
_MASS_PAREN = re.compile(r"(?i)\s*\(\s*\d+(?:\.\d+)?\s*(?:g|ml|oz|fl\s*oz)\s*\)")
_MASS_TRAIL = re.compile(r"(?i),?\s*\d+(?:\.\d+)?\s*(?:g|grams?)\b")
_MASS_ML_TRAIL = re.compile(r"(?i),?\s*\d+(?:\.\d+)?\s*ml\b")

# For vague titles: also drop leading household quantities.
_LEADING_QTY = re.compile(
    r"(?i)^\s*(?:\d+(?:\.\d+)?\s+)?"
    r"(?:large|medium|small|half|cup|cups|tbsp|tablespoons?|tsp|teaspoons?|"
    r"oz|ounces?|fl\s*oz|slices?|piece|pieces|can)\s+(?:of\s+)?"
)
_LEADING_NUM = re.compile(r"(?i)^\s*\d+(?:\.\d+)?\s+")


def strip_mass(text: str) -> str:
    t = _MASS_PAREN.sub("", text)
    t = _MASS_TRAIL.sub("", t)
    t = _MASS_ML_TRAIL.sub("", t)
    t = re.sub(r"\s+,", ",", t)
    t = re.sub(r",\s*$", "", t.strip())
    t = re.sub(r"\s{2,}", " ", t).strip(" ,")
    return t


def to_vague_title(text: str) -> str:
    t = strip_mass(text)
    # Drop "1 Brezn …" style leading counts when followed by words
    t = re.sub(r"(?i)^\d+\s+(?=[A-Za-zÄÖÜäöüß])", "", t)
    t = _LEADING_QTY.sub("", t)
    # "Cola soft drink, 12 fl oz can" → already stripped ml; clean trailing size words
    t = re.sub(r"(?i),?\s*\d+(?:\.\d+)?\s*fl\s*oz\s*(?:can)?", "", t)
    t = re.sub(r"(?i),?\s*\d+(?:\.\d+)?\s*oz\b", "", t)
    t = re.sub(r"\s{2,}", " ", t).strip(" ,")
    return t


def assert_no_grams(sample_id: str, text: str) -> None:
    if GRAM_IN_TEXT.search(text):
        raise SystemExit(f"{sample_id}: prompt still contains grams: {text!r}")


# FNDDS ids → vague slice (identity without qty)
VAGUE_IDS = [
    "fndds-001",  # chicken breast roasted
    "fndds-002",  # white rice cooked
    "fndds-006",  # greek yogurt
    "fndds-011",  # salmon baked
    "fndds-012",  # pasta cooked
    "fndds-014",  # avocado
    "fndds-019",  # baked potato
    "fndds-027",  # sweet potato
    "fndds-031",  # sirloin steak
    "fndds-033",  # mixed green salad
    "fndds-004",  # banana → "banana"
    "fndds-007",  # apple
]

# Household: keep unit language, strip paren grams
HOUSEHOLD_FROM_EVAL = [
    ("fndds-009", "Peanut butter, 2 tbsp"),
    ("fndds-015", "Whole wheat bread, 2 slices"),
    ("fndds-008", "Whole milk, 1 cup"),
    ("fndds-005", "Oatmeal, cooked, 1 cup"),
    ("fndds-010", "Broccoli, steamed, 1 cup"),
    ("fndds-013", "Cheddar cheese, 1 oz"),
    ("fndds-016", "Black coffee, 1 cup"),
    ("fndds-017", "Orange juice, 1 cup"),
    ("fndds-022", "Almonds, 1 oz"),
    ("fndds-023", "Cheese pizza, 1 slice"),
    ("fndds-025", "Butter, 1 tbsp"),
    ("fndds-029", "Hummus, 2 tbsp"),
]

MULTI_CURATED = [
    {
        "id": "real-multi-001",
        "text": "scrambled eggs with toast and butter",
        "calories": 320,
        "protein_g": 14.0,
        "carbs_g": 15.0,
        "fat_g": 22.0,
        "mass_g": None,
        "notes": "multi-item; no grams in prompt",
        "min_components": 2,
    },
    {
        "id": "real-multi-002",
        "text": "grilled chicken rice bowl with steamed broccoli",
        "calories": 467,
        "protein_g": 43.0,
        "carbs_g": 58.0,
        "fat_g": 5.0,
        "mass_g": 400,
        "notes": "from fndds-040 without gram counts",
        "min_components": 2,
    },
    {
        "id": "real-multi-003",
        "text": "2 slices pepperoni pizza and a can of cola",
        "calories": 710,
        "protein_g": 24.0,
        "carbs_g": 95.0,
        "fat_g": 20.0,
        "mass_g": 549,
        "notes": "from fndds-042 without ml/g",
        "min_components": 2,
    },
    {
        "id": "real-multi-004",
        "text": "oatmeal with blueberries and almonds",
        "calories": 418,
        "protein_g": 13.1,
        "carbs_g": 56.0,
        "fat_g": 18.1,
        "mass_g": 416,
        "notes": "composite oatmeal+blueberries+almonds approx",
        "min_components": 2,
    },
    {
        "id": "real-multi-005",
        "text": "tuna salad sandwich",
        "calories": 350,
        "protein_g": 28.0,
        "carbs_g": 30.0,
        "fat_g": 12.0,
        "mass_g": None,
        "notes": "vague multi; identity+portion hard",
        "min_components": 2,
    },
    {
        "id": "real-multi-006",
        "text": "eggs toast and orange juice",
        "calories": 350,
        "protein_g": 16.0,
        "carbs_g": 40.0,
        "fat_g": 12.0,
        "mass_g": None,
        "notes": "breakfast multi without quantities",
        "min_components": 2,
    },
]

# Branded: GT aligned with off_fixtures.json (reproducible offline).
BRANDED_CURATED = [
    {
        "id": "real-brand-001",
        "text": "Nutella",
        "calories": 80,
        "protein_g": 0.9,
        "carbs_g": 8.6,
        "fat_g": 4.6,
        "mass_g": 15,
        "barcode": "3017620425035",
        "expect_source": "openFoodFacts",
        "notes": "Ferrero Nutella ~15g serving from OFF fixture",
    },
    {
        "id": "real-brand-002",
        "text": "Ferrero Nutella",
        "calories": 80,
        "protein_g": 0.9,
        "carbs_g": 8.6,
        "fat_g": 4.6,
        "mass_g": 15,
        "barcode": "3017620425035",
        "expect_source": "openFoodFacts",
        "notes": "brand+name; same OFF fixture as Nutella",
    },
    {
        "id": "real-brand-003",
        "text": "Coca-Cola",
        "calories": 139,
        "protein_g": 0.0,
        "carbs_g": 35.0,
        "fat_g": 0.0,
        "mass_g": 330,
        "barcode": "5449000000996",
        "expect_source": "openFoodFacts",
        "notes": "330ml serving fixture",
    },
    {
        "id": "real-brand-004",
        "text": "Coca-Cola can",
        "calories": 139,
        "protein_g": 0.0,
        "carbs_g": 35.0,
        "fat_g": 0.0,
        "mass_g": 330,
        "barcode": "5449000000996",
        "expect_source": "openFoodFacts",
        "notes": "can wording; same OFF fixture",
    },
    {
        "id": "real-brand-005",
        "text": "Red Bull Energy Drink",
        "calories": 110,
        "protein_g": 1.3,
        "carbs_g": 27.5,
        "fat_g": 0.0,
        "mass_g": 250,
        "barcode": "9002490100070",
        "expect_source": "openFoodFacts",
        "notes": "250ml can fixture",
    },
    {
        "id": "real-brand-006",
        "text": "Heinz Tomato Ketchup",
        "calories": 20,
        "protein_g": 0.2,
        "carbs_g": 4.8,
        "fat_g": 0.0,
        "mass_g": 17,
        "barcode": "0001300000128",
        "expect_source": "openFoodFacts",
        "notes": "~1 tbsp ketchup fixture",
    },
    {
        "id": "real-brand-007",
        "text": "Oreo cookies",
        "calories": 160,
        "protein_g": 1.0,
        "carbs_g": 25.0,
        "fat_g": 7.0,
        "mass_g": 34,
        "barcode": "0044000004533",
        "expect_source": "openFoodFacts",
        "notes": "3-cookie serving fixture",
    },
    {
        "id": "real-brand-008",
        "text": "Chobani Plain Non-Fat Greek Yogurt",
        "calories": 90,
        "protein_g": 16.0,
        "carbs_g": 6.0,
        "fat_g": 0.0,
        "mass_g": 170,
        "barcode": "0818290010016",
        "expect_source": "openFoodFacts",
        "notes": "single cup fixture",
    },
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--eval-text",
        type=Path,
        default=_HERE / "manifest" / "eval_text.jsonl",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=_HERE / "manifest" / "eval_grounded_realistic_text.jsonl",
    )
    args = parser.parse_args()

    by_id = {s.id: s for s in load_manifest(args.eval_text)}
    out: list[Sample] = []

    for fid in VAGUE_IDS:
        src = by_id[fid]
        text = to_vague_title(src.text or "")
        assert_no_grams(f"vague:{fid}", text)
        out.append(
            Sample(
                id=f"real-vague-{fid.split('-')[1]}",
                modality="text",
                source="grounded_realistic",
                calories=src.calories,
                protein_g=src.protein_g,
                carbs_g=src.carbs_g,
                fat_g=src.fat_g,
                text=text,
                mass_g=src.mass_g,
                notes=f"vague title from {fid}; mass held out of prompt",
                extra={"slice": "vague", "derived_from": fid},
            )
        )

    for fid, prompt in HOUSEHOLD_FROM_EVAL:
        src = by_id[fid]
        text = strip_mass(prompt)
        # Prefer curated prompt (already unit-only) over stripping src.text
        assert_no_grams(f"household:{fid}", text)
        out.append(
            Sample(
                id=f"real-house-{fid.split('-')[1]}",
                modality="text",
                source="grounded_realistic",
                calories=src.calories,
                protein_g=src.protein_g,
                carbs_g=src.carbs_g,
                fat_g=src.fat_g,
                text=text,
                mass_g=src.mass_g,
                notes=f"household units from {fid}; no grams in prompt",
                extra={"slice": "household", "derived_from": fid},
            )
        )

    for row in MULTI_CURATED:
        text = row["text"]
        assert_no_grams(row["id"], text)
        out.append(
            Sample(
                id=row["id"],
                modality="text",
                source="grounded_realistic",
                calories=float(row["calories"]),
                protein_g=float(row["protein_g"]),
                carbs_g=float(row["carbs_g"]),
                fat_g=float(row["fat_g"]),
                text=text,
                mass_g=float(row["mass_g"]) if row.get("mass_g") is not None else None,
                notes=row["notes"],
                extra={
                    "slice": "multi",
                    "min_components": row.get("min_components", 2),
                },
            )
        )

    for row in BRANDED_CURATED:
        text = row["text"]
        assert_no_grams(row["id"], text)
        out.append(
            Sample(
                id=row["id"],
                modality="text",
                source="grounded_realistic",
                calories=float(row["calories"]),
                protein_g=float(row["protein_g"]),
                carbs_g=float(row["carbs_g"]),
                fat_g=float(row["fat_g"]),
                text=text,
                mass_g=float(row["mass_g"]) if row.get("mass_g") is not None else None,
                notes=row["notes"],
                extra={
                    "slice": "branded",
                    "barcode": row["barcode"],
                    "expect_source": row["expect_source"],
                },
            )
        )

    for s in out:
        validate_sample(s)

    counts: dict[str, int] = {}
    for s in out:
        counts[str(s.extra.get("slice"))] = counts.get(str(s.extra.get("slice")), 0) + 1

    write_manifest(args.out, out)
    print(f"Wrote {len(out)} samples → {args.out}")
    print("slice counts:", counts)


if __name__ == "__main__":
    main()
