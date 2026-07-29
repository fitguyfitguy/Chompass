#!/usr/bin/env python3
"""Build text manifest from USDA FNDDS CSV download."""

from __future__ import annotations

import argparse
import csv
import io
import sys
import zipfile
from pathlib import Path
from urllib.request import urlopen

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from schema import MICRO_FIELDS, Sample, write_manifest

# FNDDS 2021-2023 CSV zip (USDA FoodData Central)
FNDDS_ZIP_URL = (
    "https://fdc.nal.usda.gov/fdc-datasets/"
    "FoodData_Central_survey_food_csv_2024-10-31.zip"
)

NUTRIENT_IDS = {
    "208": "calories",  # Energy (kcal)
    "203": "protein_g",
    "205": "carbs_g",
    "204": "fat_g",
}

# USDA nutrient_nbr (the id food_nutrient.csv actually references, distinct
# from nutrient.csv's `id` column) -> micronutrient GT field name, matching
# schema.MICRO_FIELDS. Verified against the FNDDS 2024-10-31 nutrient.csv.
# folate_mcg uses "Folate, total" (417), not "Folate, DFE" (435), to match the
# plain "folate" the model is prompted for.
MICRO_NUTRIENT_IDS = {
    "269": "sugar_g",  # Total Sugars
    "539": "added_sugar_g",  # Sugars, added
    "291": "fiber_g",  # Fiber, total dietary
    "606": "saturated_fat_g",
    "645": "monounsaturated_fat_g",
    "646": "polyunsaturated_fat_g",
    "605": "trans_fat_g",
    "601": "cholesterol_mg",
    "307": "sodium_mg",
    "306": "potassium_mg",
    "301": "calcium_mg",
    "303": "iron_mg",
    "304": "magnesium_mg",
    "309": "zinc_mg",
    "401": "vitamin_c_mg",
    "323": "vitamin_e_mg",
    "320": "vitamin_a_mcg",
    "328": "vitamin_d_mcg",
    "418": "vitamin_b12_mcg",
    "430": "vitamin_k_mcg",
    "417": "folate_mcg",
}

# omega_3 has no single FDC nutrient_nbr; it's summed from these fatty-acid
# components (ALA/EPA/DHA/DPA). Best-effort composite: undercounts foods where
# only some components were measured, since a missing row means "not
# measured," not "zero" -- see docs/benchmarks/food_accuracy/manifest/schema.md.
OMEGA_3_COMPONENT_IDS = {"851", "629", "621", "631"}  # ALA, EPA, DHA, DPA


def download_fndds_zip(dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        return dest
    print(f"Downloading FNDDS zip from {FNDDS_ZIP_URL} ...")
    with urlopen(FNDDS_ZIP_URL, timeout=120) as resp:
        dest.write_bytes(resp.read())
    return dest


def _find_csv(zf: zipfile.ZipFile, basename: str) -> str:
    # Exact basename match: `n.endswith("food.csv")` also matches
    # "input_food.csv" (which sorts before "food.csv" in the zip), silently
    # picking the wrong file.
    return next(n for n in zf.namelist() if n.rsplit("/", 1)[-1] == basename)


def _open_csv(zip_path: Path, suffix: str) -> csv.DictReader:
    with zipfile.ZipFile(zip_path) as zf:
        name = _find_csv(zf, suffix)
        text = io.TextIOWrapper(zf.open(name), encoding="utf-8")
        return csv.DictReader(text)


def build_manifest(zip_path: Path, out_path: Path, limit: int) -> int:
    # Survey foods in FDC zip use fndds-derived filenames in survey_food_csv folder.
    with zipfile.ZipFile(zip_path) as zf:
        food_file = _find_csv(zf, "food.csv")
        nutrient_file = _find_csv(zf, "food_nutrient.csv")
        portion_file = _find_csv(zf, "food_portion.csv")

        foods = {}
        with zf.open(food_file) as handle:
            for row in csv.DictReader(io.TextIOWrapper(handle, encoding="utf-8")):
                foods[row["fdc_id"]] = row.get("description") or row.get("description", "")

        # food_portion.csv lists several alternate portions per food (e.g. "1 cup",
        # "1 fl oz", "guideline amount per fl oz of beverage") in no guaranteed
        # order; seq_num==1 is FNDDS's designated primary serving (usually a
        # sensible "1 cup"/"1 serving" amount) and must be preferred over
        # whichever row happens to appear first in the CSV -- picking file order
        # previously landed on tiny guideline portions (e.g. 2.5g) for some foods.
        default_portion: dict[str, float] = {}
        portion_rows: dict[str, list[dict]] = {}
        with zf.open(portion_file) as handle:
            for row in csv.DictReader(io.TextIOWrapper(handle, encoding="utf-8")):
                if row.get("gram_weight"):
                    portion_rows.setdefault(row["fdc_id"], []).append(row)
        for fdc_id, rows in portion_rows.items():
            primary = next((r for r in rows if r.get("seq_num") == "1"), rows[0])
            default_portion[fdc_id] = float(primary["gram_weight"])

        per100g: dict[str, dict[str, float]] = {}
        omega3_components: dict[str, float] = {}
        with zf.open(nutrient_file) as handle:
            for row in csv.DictReader(io.TextIOWrapper(handle, encoding="utf-8")):
                nid = row["nutrient_id"]
                fdc_id = row["fdc_id"]
                if nid in NUTRIENT_IDS:
                    per100g.setdefault(fdc_id, {})[NUTRIENT_IDS[nid]] = float(row["amount"])
                elif nid in MICRO_NUTRIENT_IDS:
                    per100g.setdefault(fdc_id, {})[MICRO_NUTRIENT_IDS[nid]] = float(row["amount"])
                elif nid in OMEGA_3_COMPONENT_IDS:
                    key = f"{fdc_id}:omega_3_g"
                    omega3_components[key] = omega3_components.get(key, 0.0) + float(row["amount"])
        for key, total in omega3_components.items():
            fdc_id, _, _ = key.partition(":")
            per100g.setdefault(fdc_id, {})["omega_3_g"] = total

    samples: list[Sample] = []
    for fdc_id, description in foods.items():
        nutrients = per100g.get(fdc_id)
        if not nutrients or "calories" not in nutrients:
            continue
        grams = default_portion.get(fdc_id, 100.0)
        scale = grams / 100.0
        text = f"{description}, {grams:.0f} g"
        micros_extra = {
            micro_key: round(nutrients[micro_key] * scale, 2)
            for micro_key in MICRO_FIELDS
            if micro_key in nutrients  # absent = not measured for this food, not zero
        }
        samples.append(
            Sample(
                id=f"fndds-fdc-{fdc_id}",
                modality="text",
                source="fndds",
                text=text,
                calories=round(nutrients["calories"] * scale),
                protein_g=round(nutrients.get("protein_g", 0) * scale, 1),
                carbs_g=round(nutrients.get("carbs_g", 0) * scale, 1),
                fat_g=round(nutrients.get("fat_g", 0) * scale, 1),
                mass_g=grams,
                notes="Generated from USDA FNDDS CSV",
                extra=micros_extra,
            )
        )
        if len(samples) >= limit:
            break

    write_manifest(out_path, samples)
    return len(samples)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build FNDDS text manifest from USDA CSV")
    parser.add_argument("--limit", type=int, default=200, help="Max foods (default 200)")
    parser.add_argument(
        "--out",
        default=str(_HERE / "manifest" / "fndds_generated.jsonl"),
        help="Output manifest",
    )
    parser.add_argument(
        "--zip",
        default=str(_HERE / "data" / "fndds" / "fndds.zip"),
        help="Cached FNDDS zip path",
    )
    args = parser.parse_args()

    zip_path = download_fndds_zip(Path(args.zip))
    count = build_manifest(zip_path, Path(args.out), args.limit)
    print(f"Wrote {count} samples to {args.out}")


if __name__ == "__main__":
    main()
