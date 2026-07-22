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

from schema import Sample, write_manifest

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


def download_fndds_zip(dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        return dest
    print(f"Downloading FNDDS zip from {FNDDS_ZIP_URL} ...")
    with urlopen(FNDDS_ZIP_URL, timeout=120) as resp:
        dest.write_bytes(resp.read())
    return dest


def _open_csv(zip_path: Path, suffix: str) -> csv.DictReader:
    with zipfile.ZipFile(zip_path) as zf:
        name = next(n for n in zf.namelist() if n.endswith(suffix))
        text = io.TextIOWrapper(zf.open(name), encoding="utf-8")
        return csv.DictReader(text)


def build_manifest(zip_path: Path, out_path: Path, limit: int) -> int:
    # Survey foods in FDC zip use fndds-derived filenames in survey_food_csv folder.
    with zipfile.ZipFile(zip_path) as zf:
        food_file = next(n for n in zf.namelist() if n.endswith("food.csv"))
        nutrient_file = next(n for n in zf.namelist() if n.endswith("food_nutrient.csv"))
        portion_file = next(n for n in zf.namelist() if n.endswith("food_portion.csv"))

        foods = {}
        with zf.open(food_file) as handle:
            for row in csv.DictReader(io.TextIOWrapper(handle, encoding="utf-8")):
                foods[row["fdc_id"]] = row.get("description") or row.get("description", "")

        default_portion: dict[str, float] = {}
        with zf.open(portion_file) as handle:
            for row in csv.DictReader(io.TextIOWrapper(handle, encoding="utf-8")):
                fdc_id = row["fdc_id"]
                if fdc_id in default_portion:
                    continue
                gram_weight = row.get("gram_weight")
                if gram_weight:
                    default_portion[fdc_id] = float(gram_weight)

        per100g: dict[str, dict[str, float]] = {}
        with zf.open(nutrient_file) as handle:
            for row in csv.DictReader(io.TextIOWrapper(handle, encoding="utf-8")):
                nid = row["nutrient_id"]
                if nid not in NUTRIENT_IDS:
                    continue
                fdc_id = row["fdc_id"]
                per100g.setdefault(fdc_id, {})[NUTRIENT_IDS[nid]] = float(row["amount"])

    samples: list[Sample] = []
    for fdc_id, description in foods.items():
        nutrients = per100g.get(fdc_id)
        if not nutrients or "calories" not in nutrients:
            continue
        grams = default_portion.get(fdc_id, 100.0)
        scale = grams / 100.0
        text = f"{description}, {grams:.0f} g"
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
