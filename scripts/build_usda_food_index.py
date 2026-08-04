#!/usr/bin/env python3
"""Build a compact USDA Foundation + FNDDS SQLite index for Chompass.

Downloads a pinned FoodData Central bulk CSV release (or uses --fixture to emit a
tiny committed seed without network), normalizes Chompass-supported nutrients to
per-100g, and writes (main assets — ships in all build types):

  android/app/src/main/assets/usda/usda_foods.sqlite
  android/app/src/main/assets/usda/usda_foods.manifest.json

Usage (from repo root):
  uv run python scripts/build_usda_food_index.py --fixture
  uv run --with requests python scripts/build_usda_food_index.py

Raw downloads land under build/usda-fdc/ (gitignored). USDA FoodData Central is
CC0 / public domain; cite USDA when redistributing.
"""

from __future__ import annotations

import argparse
import csv
import sqlite3
import sys
import zipfile
from pathlib import Path
from urllib.request import urlretrieve

from _food_index_common import (
    create_schema,
    rebuild_fts,
    set_meta,
    tokenize,
    write_manifest,
)

REPO = Path(__file__).resolve().parents[1]
OUT_DIR = REPO / "android" / "app" / "src" / "main" / "assets" / "usda"
CACHE_DIR = REPO / "build" / "usda-fdc"

# Pin a known public FDC bulk dump. Override with --zip-url if USDA moves the file.
DEFAULT_ZIP_URL = (
    "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_csv_2024-10-31.zip"
)
DATASET_VERSION = "fdc-2024-10-31-foundation-fndds"

# FoodData Central nutrient IDs → Chompass column names (per 100 g).
NUTRIENT_MAP = {
    "1008": "calories",  # Energy (kcal)
    "1003": "protein",
    "1005": "carbs",
    "1004": "fat",
    "1079": "fiber",
    "2000": "sugar",
    "1235": "added_sugar",
    "1258": "saturated_fat",
    "1292": "monounsaturated_fat",
    "1293": "polyunsaturated_fat",
    "1253": "cholesterol",  # mg
    "1093": "sodium",  # mg
    "1092": "potassium",  # mg
    "1257": "trans_fat",
    "1087": "calcium",  # mg
    "1089": "iron",  # mg
    "1090": "magnesium",  # mg
    "1095": "zinc",  # mg
    "1106": "vitamin_a",  # mcg RAE
    "1162": "vitamin_c",  # mg
    "1114": "vitamin_d",  # mcg
    "1178": "vitamin_b12",  # mcg
    "1109": "vitamin_e",  # mg
    "1185": "vitamin_k",  # mcg
    "1177": "folate",  # mcg DFE
    # Omega-3 fatty acids (g) — EPA + DHA when present; ALA as fallback.
    "1278": "omega_3",  # EPA
    "1280": "omega_3_dha",  # DHA (merged below)
    "1404": "omega_3_ala",  # ALA (merged below)
}

NUTRIENT_COLUMNS = [
    "calories",
    "protein",
    "carbs",
    "fat",
    "fiber",
    "sugar",
    "added_sugar",
    "saturated_fat",
    "monounsaturated_fat",
    "polyunsaturated_fat",
    "cholesterol",
    "sodium",
    "potassium",
    "trans_fat",
    "calcium",
    "iron",
    "magnesium",
    "zinc",
    "vitamin_a",
    "vitamin_c",
    "vitamin_d",
    "vitamin_b12",
    "vitamin_e",
    "vitamin_k",
    "folate",
    "omega_3",
]

# Minimal fixture used for offline CI / first boot without a full download.
FIXTURE_FOODS = [
    {
        "fdc_id": 167765,
        "description": "Egg, whole, raw, fresh",
        "data_type": "foundation_food",
        "food_category": "Dairy and Egg Products",
        "tokens": "egg whole raw fresh",
        "calories": 143.0,
        "protein": 12.6,
        "carbs": 0.7,
        "fat": 9.5,
        "fiber": 0.0,
        "sugar": 0.4,
        "sodium": 142.0,
        "cholesterol": 372.0,
        "saturated_fat": 3.1,
        "serving_unit": "large",
        "serving_grams": 50.0,
    },
    {
        "fdc_id": 168462,
        "description": "Banana, raw",
        "data_type": "foundation_food",
        "food_category": "Fruits and Fruit Juices",
        "tokens": "banana raw",
        "calories": 89.0,
        "protein": 1.1,
        "carbs": 22.8,
        "fat": 0.3,
        "fiber": 2.6,
        "sugar": 12.2,
        "potassium": 358.0,
        "vitamin_c": 8.7,
        "serving_unit": "medium",
        "serving_grams": 118.0,
    },
    {
        "fdc_id": 171705,
        "description": "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw",
        "data_type": "foundation_food",
        "food_category": "Poultry Products",
        "tokens": "chicken breast skinless boneless raw",
        "calories": 120.0,
        "protein": 22.5,
        "carbs": 0.0,
        "fat": 2.6,
        "sodium": 45.0,
        "cholesterol": 73.0,
        "serving_unit": "piece",
        "serving_grams": 120.0,
    },
    {
        "fdc_id": 168874,
        "description": "Rice, white, long-grain, regular, enriched, cooked",
        "data_type": "foundation_food",
        "food_category": "Cereal Grains and Pasta",
        "tokens": "rice white cooked",
        "calories": 130.0,
        "protein": 2.7,
        "carbs": 28.2,
        "fat": 0.3,
        "fiber": 0.4,
        "serving_unit": "cup",
        "serving_grams": 158.0,
    },
    {
        "fdc_id": 173944,
        "description": "Milk, whole, 3.25% milkfat, with added vitamin D",
        "data_type": "foundation_food",
        "food_category": "Dairy and Egg Products",
        "tokens": "milk whole",
        "calories": 61.0,
        "protein": 3.2,
        "carbs": 4.8,
        "fat": 3.3,
        "sugar": 5.1,
        "calcium": 113.0,
        "sodium": 43.0,
        "serving_unit": "cup",
        "serving_grams": 244.0,
    },
    {
        "fdc_id": 170379,
        "description": "Oats, whole grain, rolled, old fashioned",
        "data_type": "foundation_food",
        "food_category": "Cereal Grains and Pasta",
        "tokens": "oats oatmeal rolled",
        "calories": 379.0,
        "protein": 13.2,
        "carbs": 67.7,
        "fat": 6.5,
        "fiber": 10.1,
        "serving_unit": "cup dry",
        "serving_grams": 81.0,
    },
    {
        "fdc_id": 175034,
        "description": "Apple, raw, with skin",
        "data_type": "foundation_food",
        "food_category": "Fruits and Fruit Juices",
        "tokens": "apple raw",
        "calories": 52.0,
        "protein": 0.3,
        "carbs": 13.8,
        "fat": 0.2,
        "fiber": 2.4,
        "sugar": 10.4,
        "serving_unit": "medium",
        "serving_grams": 182.0,
    },
    {
        "fdc_id": 170567,
        "description": "Bread, white, commercially prepared",
        "data_type": "foundation_food",
        "food_category": "Baked Products",
        "tokens": "bread white",
        "calories": 265.0,
        "protein": 9.0,
        "carbs": 49.0,
        "fat": 3.2,
        "fiber": 2.7,
        "sodium": 490.0,
        "serving_unit": "slice",
        "serving_grams": 28.0,
    },
    {
        "fdc_id": 172470,
        "description": "Yogurt, Greek, plain, nonfat",
        "data_type": "foundation_food",
        "food_category": "Dairy and Egg Products",
        "tokens": "yogurt greek plain nonfat",
        "calories": 59.0,
        "protein": 10.2,
        "carbs": 3.6,
        "fat": 0.4,
        "sugar": 3.3,
        "calcium": 111.0,
        "serving_unit": "container",
        "serving_grams": 170.0,
    },
    {
        "fdc_id": 169124,
        "description": "Potato, boiled, cooked in skin, flesh, without salt",
        "data_type": "foundation_food",
        "food_category": "Vegetables and Vegetable Products",
        "tokens": "potato boiled cooked",
        "calories": 87.0,
        "protein": 1.9,
        "carbs": 20.1,
        "fat": 0.1,
        "fiber": 1.8,
        "potassium": 379.0,
        "serving_unit": "medium",
        "serving_grams": 173.0,
    },
    {
        "fdc_id": 16846201,
        "description": "Pizza, cheese, regular crust",
        "data_type": "survey_fndds_food",
        "food_category": "Mixed Dishes",
        "tokens": "pizza cheese",
        "calories": 266.0,
        "protein": 11.4,
        "carbs": 33.0,
        "fat": 9.8,
        "saturated_fat": 4.5,
        "sodium": 598.0,
        "serving_unit": "slice",
        "serving_grams": 107.0,
    },
    {
        "fdc_id": 171688,
        "description": "Salmon, Atlantic, farmed, cooked, dry heat",
        "data_type": "foundation_food",
        "food_category": "Finfish and Shellfish Products",
        "tokens": "salmon atlantic cooked",
        "calories": 206.0,
        "protein": 22.1,
        "carbs": 0.0,
        "fat": 12.4,
        "omega_3": 2.2,
        "serving_unit": "fillet",
        "serving_grams": 154.0,
    },
]


def atwater_kcal(
    protein: float | None, carbs: float | None, fat: float | None
) -> float | None:
    """Fill missing energy from macros using classic Atwater factors (4/4/9)."""
    if protein is None and carbs is None and fat is None:
        return None
    return 4.0 * (protein or 0.0) + 4.0 * (carbs or 0.0) + 9.0 * (fat or 0.0)


def merge_omega3(nuts: dict[str, float]) -> None:
    epa = nuts.pop("omega_3", None)
    dha = nuts.pop("omega_3_dha", None)
    ala = nuts.pop("omega_3_ala", None)
    total = sum(v for v in (epa, dha, ala) if v is not None)
    if total > 0:
        nuts["omega_3"] = total


def insert_food(conn: sqlite3.Connection, row: dict) -> None:
    cols = [
        "fdc_id",
        "description",
        "data_type",
        "food_category",
        "tokens",
        "serving_unit",
        "serving_grams",
        *NUTRIENT_COLUMNS,
    ]
    values = []
    for c in cols:
        if c == "tokens":
            values.append(row.get("tokens") or tokenize(row["description"]))
        else:
            values.append(row.get(c))
    placeholders = ",".join("?" for _ in cols)
    conn.execute(
        f"INSERT OR REPLACE INTO foods ({','.join(cols)}) VALUES ({placeholders})",
        values,
    )


def insert_portions(
    conn: sqlite3.Connection, fdc_id: int, portions: list[tuple[str, float]]
) -> None:
    for unit, grams in portions:
        if not unit or grams <= 0:
            continue
        conn.execute(
            "INSERT OR REPLACE INTO food_portions(fdc_id, unit, grams) VALUES (?,?,?)",
            (fdc_id, unit[:48], grams),
        )


def write_fixture(db_path: Path) -> int:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()
    conn = sqlite3.connect(db_path)
    try:
        create_schema(
            conn,
            nutrient_columns=NUTRIENT_COLUMNS,
            fixed_columns=[
                "fdc_id INTEGER PRIMARY KEY",
                "description TEXT NOT NULL",
                "data_type TEXT NOT NULL",
                "food_category TEXT",
                "tokens TEXT NOT NULL",
                "serving_unit TEXT",
                "serving_grams REAL",
            ],
            fts_columns=["description", "tokens", "food_category"],
            fts_rowid="fdc_id",
            extra_statements=[
                "DROP TABLE IF EXISTS food_portions;",
                """
                CREATE TABLE food_portions (
                  fdc_id INTEGER NOT NULL,
                  unit TEXT NOT NULL,
                  grams REAL NOT NULL,
                  PRIMARY KEY (fdc_id, unit),
                  FOREIGN KEY (fdc_id) REFERENCES foods(fdc_id)
                );
                """,
                "CREATE INDEX foods_desc_idx ON foods(description);",
                "CREATE INDEX foods_category_idx ON foods(food_category);",
            ],
        )
        for food in FIXTURE_FOODS:
            insert_food(conn, food)
            if food.get("serving_unit") and food.get("serving_grams"):
                insert_portions(
                    conn,
                    int(food["fdc_id"]),
                    [(str(food["serving_unit"]), float(food["serving_grams"]))],
                )
        rebuild_fts(conn)
        set_meta(conn, "dataset_version", DATASET_VERSION + "-fixture")
        set_meta(conn, "source", "USDA FoodData Central (fixture subset)")
        set_meta(conn, "license", "CC0 / public domain")
        set_meta(conn, "features", "fts5,portions,atwater_fill,wweia_category")
        conn.commit()
        return len(FIXTURE_FOODS)
    finally:
        conn.close()


def read_csv_dict(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def build_from_zip(zip_path: Path, db_path: Path) -> int:
    extract = CACHE_DIR / "extracted"
    extract.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(extract)

    # FoodData Central CSV layout nests files one level deep.
    csv_root = (
        next(p for p in extract.iterdir() if p.is_dir())
        if any(p.is_dir() for p in extract.iterdir())
        else extract
    )

    food_path = csv_root / "food.csv"
    nutrient_path = csv_root / "food_nutrient.csv"
    nutrient_def = csv_root / "nutrient.csv"
    category_path = csv_root / "food_category.csv"
    portion_path = csv_root / "food_portion.csv"
    wweia_path = csv_root / "wweia_food_category.csv"

    if not food_path.exists() or not nutrient_path.exists():
        raise SystemExit(f"Unexpected FDC CSV layout under {csv_root}")

    allowed_types = {"foundation_food", "survey_fndds_food"}
    foods = {
        int(r["fdc_id"]): r
        for r in read_csv_dict(food_path)
        if r.get("data_type") in allowed_types
    }
    categories = {
        r["id"]: r.get("description") or r.get("code") or ""
        for r in (read_csv_dict(category_path) if category_path.exists() else [])
    }
    # WWEIA category code → description for survey foods when present.
    wweia_cats: dict[str, str] = {}
    if wweia_path.exists():
        for r in read_csv_dict(wweia_path):
            code = (
                r.get("wweia_food_category")
                or r.get("wweia_food_category_code")
                or r.get("code")
            )
            desc = (
                r.get("wweia_food_category_description") or r.get("description") or ""
            )
            if code:
                wweia_cats[str(code)] = desc

    # nutrient_id in food_nutrient may be the row id in nutrient.csv — map to nutrient_nbr.
    nutrient_id_to_nbr: dict[str, str] = {}
    if nutrient_def.exists():
        for r in read_csv_dict(nutrient_def):
            nutrient_id_to_nbr[r["id"]] = r.get("nutrient_nbr") or r["id"]

    nutrients: dict[int, dict[str, float]] = {fid: {} for fid in foods}
    wanted_nbrs = set(NUTRIENT_MAP)
    with nutrient_path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        for r in reader:
            try:
                fid = int(r["fdc_id"])
            except (KeyError, ValueError):
                continue
            if fid not in nutrients:
                continue
            raw_id = r.get("nutrient_id") or ""
            nbr = nutrient_id_to_nbr.get(raw_id, raw_id)
            # Some dumps store nutrient_nbr directly.
            if nbr not in wanted_nbrs and raw_id in wanted_nbrs:
                nbr = raw_id
            col = NUTRIENT_MAP.get(nbr)
            if not col:
                continue
            try:
                amount = float(r["amount"])
            except (KeyError, ValueError, TypeError):
                continue
            # Accumulate EPA/DHA/ALA into temporary keys; merge later.
            if col in nutrients[fid] and col.startswith("omega_3"):
                nutrients[fid][col] = nutrients[fid][col] + amount
            else:
                nutrients[fid][col] = amount

    portions: dict[int, list[tuple[str, float]]] = {}
    if portion_path.exists():
        for r in read_csv_dict(portion_path):
            try:
                fid = int(r["fdc_id"])
                grams = float(r["gram_weight"])
            except (KeyError, ValueError, TypeError):
                continue
            if fid not in foods or grams <= 0:
                continue
            unit = (
                r.get("portion_description") or r.get("modifier") or "serving"
            ).strip()
            portions.setdefault(fid, [])
            # Keep up to 6 distinct household measures per food.
            if len(portions[fid]) >= 6:
                continue
            if any(u == unit[:48] for u, _ in portions[fid]):
                continue
            portions[fid].append((unit[:48], grams))

    # Optional survey food → WWEIA category via food.csv wweia_category_code when present.
    survey_wweia: dict[int, str] = {}
    for fid, meta in foods.items():
        code = (
            meta.get("wweia_category_code")
            or meta.get("wweia_food_category_code")
            or ""
        )
        if code and str(code) in wweia_cats:
            survey_wweia[fid] = wweia_cats[str(code)]

    if db_path.exists():
        db_path.unlink()
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    try:
        create_schema(
            conn,
            nutrient_columns=NUTRIENT_COLUMNS,
            fixed_columns=[
                "fdc_id INTEGER PRIMARY KEY",
                "description TEXT NOT NULL",
                "data_type TEXT NOT NULL",
                "food_category TEXT",
                "tokens TEXT NOT NULL",
                "serving_unit TEXT",
                "serving_grams REAL",
            ],
            fts_columns=["description", "tokens", "food_category"],
            fts_rowid="fdc_id",
            extra_statements=[
                "DROP TABLE IF EXISTS food_portions;",
                """
                CREATE TABLE food_portions (
                  fdc_id INTEGER NOT NULL,
                  unit TEXT NOT NULL,
                  grams REAL NOT NULL,
                  PRIMARY KEY (fdc_id, unit),
                  FOREIGN KEY (fdc_id) REFERENCES foods(fdc_id)
                );
                """,
                "CREATE INDEX foods_desc_idx ON foods(description);",
                "CREATE INDEX foods_category_idx ON foods(food_category);",
            ],
        )
        count = 0
        for fid, meta in foods.items():
            nuts = dict(nutrients.get(fid) or {})
            merge_omega3(nuts)
            if "calories" not in nuts:
                filled = atwater_kcal(
                    nuts.get("protein"), nuts.get("carbs"), nuts.get("fat")
                )
                if filled is not None and filled > 0:
                    nuts["calories"] = round(filled, 1)
            if "calories" not in nuts and "protein" not in nuts:
                continue
            cat_id = meta.get("food_category_id") or ""
            food_category = survey_wweia.get(fid) or categories.get(cat_id) or None
            food_portions = portions.get(fid) or []
            unit, grams = food_portions[0] if food_portions else (None, None)
            row = {
                "fdc_id": fid,
                "description": meta.get("description") or f"FDC {fid}",
                "data_type": meta.get("data_type") or "",
                "food_category": food_category,
                "tokens": tokenize(meta.get("description") or ""),
                "serving_unit": unit,
                "serving_grams": grams,
            }
            for col in NUTRIENT_COLUMNS:
                row[col] = nuts.get(col)
            insert_food(conn, row)
            insert_portions(conn, fid, food_portions)
            count += 1
        rebuild_fts(conn)
        set_meta(conn, "dataset_version", DATASET_VERSION)
        set_meta(conn, "source", "USDA FoodData Central Foundation + FNDDS")
        set_meta(conn, "license", "CC0 / public domain")
        set_meta(conn, "features", "fts5,portions,atwater_fill,wweia_category,omega3")
        conn.commit()
        return count
    finally:
        conn.close()


def build_manifest(food_count: int, fixture: bool) -> dict:
    return {
        "dataset_version": DATASET_VERSION + ("-fixture" if fixture else ""),
        "food_count": food_count,
        "license": "CC0 / public domain (USDA FoodData Central)",
        "attribution": "U.S. Department of Agriculture, Agricultural Research Service, FoodData Central",
        "nutrients": NUTRIENT_COLUMNS,
        "fixture": fixture,
    }


def download_zip(url: str, dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 1_000_000:
        print(f"Using cached zip: {dest}", file=sys.stderr)
        return dest
    print(f"Downloading {url} …", file=sys.stderr)
    urlretrieve(url, dest)
    return dest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--fixture",
        action="store_true",
        help="Write the small committed fixture instead of downloading USDA CSVs",
    )
    parser.add_argument("--zip-url", default=DEFAULT_ZIP_URL)
    parser.add_argument(
        "--zip-path",
        type=Path,
        help="Local FoodData_Central_csv_*.zip (skips download)",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=OUT_DIR / "usda_foods.sqlite",
        help="Output SQLite path",
    )
    args = parser.parse_args()

    if args.fixture:
        count = write_fixture(args.out)
        manifest = write_manifest(args.out, build_manifest(count, fixture=True))
        print(f"Wrote fixture {count} foods → {args.out}")
        print(f"Manifest → {manifest}")
        return

    zip_path = args.zip_path or (CACHE_DIR / Path(args.zip_url).name)
    if args.zip_path is None:
        zip_path = download_zip(args.zip_url, zip_path)
    count = build_from_zip(zip_path, args.out)
    manifest = write_manifest(args.out, build_manifest(count, fixture=False))
    print(f"Wrote {count} foods → {args.out}")
    print(f"Manifest → {manifest}")


if __name__ == "__main__":
    main()
