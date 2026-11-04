#!/usr/bin/env python3
"""Build the USDA GTIN barcode pack (``usda-gtins``) — Chompass offline pack.

Joins FoodData Central's branded foods (``branded_food.csv``, rows with a
non-empty ``gtin_upc``) with ``food.csv`` descriptions and the per-100g
energy/protein/carb/fat nutrients from ``food_nutrient.csv`` — the same
pinned FDC zip and nutrient IDs as ``build_usda_food_index.py``. Dedupes by
barcode, keeping the first row. Emits the shared barcode-pack schema:

    barcodes(code TEXT PRIMARY KEY, name TEXT NOT NULL, brand TEXT,
             kcal REAL, protein REAL, carbs REAL, fat REAL, serving_g REAL)
    meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)  -- pack_id, dataset_version,
                                                     -- generated_at, row_count,
                                                     -- license, source

Outputs into ``build/offline-index/`` (gitignored):
    usda-gtins-<version>.sqlite.gz
    usda-gtins-<version>.json    -- manifest; sha256/bytes describe the .gz

Publishing (maintainer, one-time per refresh): create a Codeberg release
tagged ``offline-index-<version>`` on fitguy/Chompass, attach the pack
pair (plus the ``off-de`` pair from ``build_off_country_index.py``), and
mark it as a **pre-release** so it never becomes the repo's "latest
release" (the README badge and Obtainium's stable track use latest =
newest stable release). The app resolves packs by tag prefix + asset
naming contract and ignores the flag; nothing ships in the APK.

    nix shell nixpkgs#tea -c tea releases create --login codeberg-release \
      --repo fitguy/chompass --tag offline-index-<version> \
      --title "Offline barcode packs <version>" --prerelease \
      --asset build/offline-index/usda-gtins-<version>.sqlite.gz \
      --asset build/offline-index/usda-gtins-<version>.json \
      --asset build/offline-index/off-de-<version>.sqlite.gz \
      --asset build/offline-index/off-de-<version>.json

Usage:
    uv run python scripts/build_usda_gtin_index.py              # full build (downloads the pinned FDC zip)
    uv run python scripts/build_usda_gtin_index.py --version 20261104
    uv run python scripts/build_usda_gtin_index.py --fixture    # tiny committed test pack
"""

from __future__ import annotations

import argparse
import csv
import sqlite3
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))

from _food_index_common import (
    create_barcode_pack,
    emit_barcode_pack,
    insert_barcode_pack_meta,
    insert_barcode_pack_rows,
)
from build_usda_food_index import CACHE_DIR, DEFAULT_ZIP_URL, download_zip

OUT_DIR = REPO / "build" / "offline-index"
FIXTURE_OUT = REPO / "android/app/src/test/resources/offlinepack/fixture_usda.sqlite"

PACK_ID = "usda-gtins"
LICENSE_TEXT = "USDA FoodData Central (public domain)"

# FDC nutrient IDs for the four pack macros (same IDs as NUTRIENT_MAP in
# build_usda_food_index.py; energy 1008 is kcal).
MACRO_KEYS = {"1008": "kcal", "1003": "protein", "1005": "carbs", "1004": "fat"}

# ~a dozen hard-coded rows for the committed reader test (offline CI). Macros
# are per 100 g; serving_g is grams (None = unlabeled).
FIXTURE_ROWS = [
    ("016000147725", "Vegetable Baked Snacks", "PepsiCo", 536.0, 6.7, 60.0, 30.0, 28.0),
    ("041631000566", "Creamy Peanut Butter", "J.M. Smucker", 588.0, 22.5, 20.0, 50.0, 32.0),
    ("070032024557", "Plain Whole Milk Yogurt", "Stonyfield", 61.0, 3.3, 4.7, 3.3, 170.0),
    ("085239034926", "Sparkling Water Lime", "Spindrift", 0.0, 0.0, 0.0, 0.0, 355.0),
    ("072216598463", "Dark Chocolate 70%", "Equal Exchange", 598.0, 7.8, 45.9, 43.1, 28.0),
    ("084315320136", "Sprouted Grain Bread", "Food for Life", 243.0, 13.0, 39.0, 2.0, 42.0),
    ("014100096693", "Chunk Light Tuna in Water", "StarKist", 98.0, 23.0, 0.0, 0.8, 56.0),
    ("06038320236", "Extra Virgin Olive Oil", "California Olive Ranch", 884.0, 0.0, 0.0, 100.0, 14.0),
    ("077341001144", "Organic Rolled Oats", "Bob's Red Mill", 379.0, 13.0, 68.0, 6.5, 40.0),
    ("090815501013", "Firm Tofu", "House Foods", 76.0, 8.0, 2.0, 4.5, 113.0),
    ("006232711300", "Marinara Pasta Sauce", "Rao's", 60.0, 1.0, 8.0, 3.0, 125.0),
    ("0001600054720", "Whole Wheat Spaghetti", "Ronzio", 354.0, 13.0, 71.0, 2.5, 56.0),
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip-url", default=DEFAULT_ZIP_URL, help="FDC bulk dump zip (pinned default)")
    parser.add_argument(
        "--version",
        default=datetime.now(timezone.utc).strftime("%Y%m%d"),
        help="pack version token (yyyymmdd; becomes the release asset name)",
    )
    parser.add_argument("--fixture", action="store_true", help="emit the tiny committed test pack and exit")
    return parser.parse_args()


def load_nutrient_nbr_map(csv_root: Path) -> dict[str, str]:
    """food_nutrient.csv's nutrient_id is the row id in nutrient.csv; map to nutrient_nbr."""
    mapping: dict[str, str] = {}
    nutrient_def = csv_root / "nutrient.csv"
    if nutrient_def.exists():
        with nutrient_def.open(newline="", encoding="utf-8") as fh:
            for r in csv.DictReader(fh):
                mapping[r["id"]] = r.get("nutrient_nbr") or r["id"]
    return mapping


def build_from_zip(zip_path: Path, db_path: Path, dataset_version: str) -> int:
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

    branded_path = csv_root / "branded_food.csv"
    food_path = csv_root / "food.csv"
    nutrient_path = csv_root / "food_nutrient.csv"
    for path in (branded_path, food_path, nutrient_path):
        if not path.exists():
            raise SystemExit(f"Unexpected FDC CSV layout under {csv_root}: missing {path.name}")

    # 1. Branded rows carrying a barcode: fdc_id -> (code, brand, serving_g).
    #    Keep the first row per fdc_id; serving only for g/ml labels.
    branded: dict[int, tuple[str, str | None, float | None]] = {}
    with branded_path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        # Schema-first: print the join columns so upstream drift is loud.
        print(f"branded_food.csv columns: {reader.fieldnames}")
        missing = {"fdc_id", "gtin_upc", "brand_owner"} - set(reader.fieldnames or [])
        if missing:
            raise SystemExit(f"branded_food.csv is missing columns {sorted(missing)}")
        for r in reader:
            code = (r.get("gtin_upc") or "").strip()
            if not code.isdigit():
                continue
            try:
                fid = int(r["fdc_id"])
            except (KeyError, ValueError):
                continue
            if fid in branded:
                continue
            serving: float | None = None
            if (r.get("serving_size_unit") or "").strip().lower() in {"g", "ml"}:
                try:
                    value = float(r.get("serving_size") or "")
                    serving = value if value > 0 else None
                except ValueError:
                    serving = None
            brand = (r.get("brand_owner") or "").strip() or None
            branded[fid] = (code, brand, serving)

    # 2. Descriptions for the branded subset.
    names: dict[int, str] = {}
    with food_path.open(newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            try:
                fid = int(r["fdc_id"])
            except (KeyError, ValueError):
                continue
            if fid in branded:
                names[fid] = r.get("description") or ""

    # 3. Per-100g macros for the branded subset only.
    nbr_map = load_nutrient_nbr_map(csv_root)
    macros: dict[int, dict[str, float]] = {}
    with nutrient_path.open(newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            try:
                fid = int(r["fdc_id"])
            except (KeyError, ValueError):
                continue
            if fid not in branded:
                continue
            raw_id = r.get("nutrient_id") or ""
            nbr = nbr_map.get(raw_id, raw_id)
            key = MACRO_KEYS.get(nbr) or MACRO_KEYS.get(raw_id)
            if not key:
                continue
            try:
                amount = float(r["amount"])
            except (KeyError, ValueError, TypeError):
                continue
            macros.setdefault(fid, {})[key] = amount

    # 4. Dedupe by code (first wins); require a name and at least one macro.
    by_code: dict[str, tuple] = {}
    for fid, (code, brand, serving) in branded.items():
        if code in by_code:
            continue
        name = names.get(fid, "").strip()
        if not name:
            continue
        nuts = macros.get(fid) or {}
        kcal = nuts.get("kcal")
        protein = nuts.get("protein")
        carbs = nuts.get("carbs")
        fat = nuts.get("fat")
        if kcal is None and protein is None and carbs is None and fat is None:
            continue
        by_code[code] = (name, brand, kcal, protein, carbs, fat, serving)

    if db_path.exists():
        db_path.unlink()
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    try:
        create_barcode_pack(conn)
        insert_barcode_pack_rows(conn, [(code, *row) for code, row in by_code.items()])
        insert_barcode_pack_meta(conn, {
            "pack_id": PACK_ID,
            "dataset_version": dataset_version,
            "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "row_count": str(len(by_code)),
            "license": LICENSE_TEXT,
            "source": DEFAULT_ZIP_URL,
        })
        conn.commit()
        return len(by_code)
    finally:
        conn.close()


def write_fixture(db_path: Path) -> int:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()
    conn = sqlite3.connect(db_path)
    try:
        create_barcode_pack(conn)
        insert_barcode_pack_rows(conn, FIXTURE_ROWS)
        insert_barcode_pack_meta(conn, {
            "pack_id": PACK_ID,
            "dataset_version": "fixture",
            "license": LICENSE_TEXT,
            "row_count": str(len(FIXTURE_ROWS)),
        })
        conn.commit()
        return len(FIXTURE_ROWS)
    finally:
        conn.close()


def main() -> None:
    args = parse_args()
    if args.fixture:
        count = write_fixture(FIXTURE_OUT)
        print(f"Fixture pack -> {FIXTURE_OUT} ({count} rows)")
        return

    dest = CACHE_DIR / Path(args.zip_url).name
    zip_path = download_zip(args.zip_url, dest)
    work_db = OUT_DIR / f"{PACK_ID}-{args.version}.sqlite"
    dataset_version = "fdc-" + Path(args.zip_url).stem.replace("FoodData_Central_csv_", "")
    rows = build_from_zip(zip_path, work_db, dataset_version)
    gz_path, manifest_path = emit_barcode_pack(
        work_db,
        PACK_ID,
        args.version,
        rows,
        LICENSE_TEXT,
        DEFAULT_ZIP_URL,
        OUT_DIR,
    )
    work_db.unlink()
    print(f"{rows} barcodes -> {gz_path}")
    print(f"Manifest -> {manifest_path}")


if __name__ == "__main__":
    main()
