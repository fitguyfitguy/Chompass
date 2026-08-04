#!/usr/bin/env python3
"""Build a compact Swiss Food Composition Database SQLite index for Chompass.

Downloads the four localized CSVs published by the Swiss federal authorities
(naehrwertdaten.ch) via the pinned mirror bundle used by the open-source Food You
project (the same per-100g dataset), normalizes nutrients to Chompass units, and
writes a compact multi-language index:

  android/app/src/main/assets/swiss/swiss_foods.sqlite
  android/app/src/main/assets/swiss/swiss_foods.manifest.json

The four CSVs (en / de / fr / it) contain the same foods but are NOT row-aligned
(each file is sorted in its own language), and carry no shared ID. Each language
file is therefore indexed as its own set of rows tagged with a `lang`, matching
how the source publisher ships them. The app ranks hits for the device language
first and falls back to other languages.

Usage (from repo root):
  uv run python scripts/build_swiss_food_index.py

Raw downloads land under build/swiss-sfdc/ (gitignored). Source: Federal
authorities of the Swiss Confederation, https://naehrwertdaten.ch — see the
bundled COPYRIGHT in ASSET_CREDITS.md. Per-100g values; minerals/vitamins are
stored as grams per 100 g in the CSV and converted here.
"""

from __future__ import annotations

import argparse
import csv
import io
import sqlite3
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
OUT_DIR = REPO / "android" / "app" / "src" / "main" / "assets" / "swiss"
CACHE_DIR = REPO / "build" / "swiss-sfdc"

DATASET_VERSION = "swiss-sfdc-2026-08"
SOURCE_LABEL = "Swiss Food Composition Database (naehrwertdaten.ch)"

# Food You bundles the four per-100g CSVs published by the Swiss federal
# authorities. Keep the mirror URL pinned; the CSV schema is the official one.
FOOD_YOU_BASE = (
    "https://raw.githubusercontent.com/maksimowiczm/FoodYou/main/app/src/"
    "commonMain/composeResources/files/swiss-food-composition-database"
)
FILES = {
    "en": "data.csv",
    "de": "data-de-DE.csv",
    "fr": "data-fr-FR.csv",
    "it": "data-it-IT.csv",
}

# CSV column (per 100 g) -> (Chompass column, conversion to Chompass unit).
# Gram-based columns here are grams per 100 g in the CSV; mg/µg columns carry
# their explicit "_milli"/"_micro" suffix in the CSV header.
NUTRIENT_MAP = {
    "calories": ("energy", 1.0),
    "protein": ("proteins", 1.0),
    "carbs": ("carbohydrates", 1.0),
    "fat": ("fats", 1.0),
    "fiber": ("dietary_fiber", 1.0),
    "sugar": ("sugars", 1.0),
    "saturated_fat": ("saturated_fats", 1.0),
    "monounsaturated_fat": ("monounsaturated_fats", 1.0),
    "polyunsaturated_fat": ("polyunsaturated_fats", 1.0),
    "cholesterol": ("cholesterol", 1000.0),  # g -> mg
    "omega3": ("omega3", 1.0),
    "sodium": ("sodium", 1000.0),  # g -> mg
    "potassium": ("potassium", 1000.0),  # g -> mg
    "calcium": ("calcium", 1000.0),  # g -> mg
    "iron": ("iron", 1000.0),  # g -> mg
    "magnesium": ("magnesium", 1000.0),  # g -> mg
    "zinc": ("zinc", 1000.0),  # g -> mg
    "vitamin_a": ("vitamin_a", 1_000_000.0),  # g -> µg
    "vitamin_c": ("vitamin_c", 1000.0),  # g -> mg
    "vitamin_d": ("vitamin_d", 1_000_000.0),  # g -> µg
    "vitamin_b12": ("vitamin_b12", 1_000_000.0),  # g -> µg
    "vitamin_e": ("vitamin_e", 1000.0),  # g -> mg
    "vitamin_k": ("vitamin_k_micro", 1.0),  # already µg
    "folate": ("vitamin_b9", 1_000_000.0),  # g -> µg
}

NUTRIENT_COLUMNS = list(NUTRIENT_MAP.keys())
LANGS = ["en", "de", "fr", "it"]


def parse_float(value: str | None) -> float | None:
    if value is None or value.strip() == "":
        return None
    try:
        return float(value)
    except ValueError:
        return None


def fetch_csvs(cache_dir: Path, out: list[tuple[str, Path]]) -> list[tuple[str, Path]]:
    """Download missing CSVs; return (lang, path) pairs in place."""
    cache_dir.mkdir(parents=True, exist_ok=True)
    ready = []
    for lang, fname in FILES.items():
        path = cache_dir / fname
        if not path.exists() or path.stat().st_size == 0:
            url = f"{FOOD_YOU_BASE}/{fname}"
            print(f"  downloading {lang} <- {url}")
            urlretrieve(url, path)  # noqa: S310 (pinned https mirror)
        ready.append((lang, path))
    if not (cache_dir / "COPYRIGHT").exists():
        urlretrieve(f"{FOOD_YOU_BASE}/COPYRIGHT", cache_dir / "COPYRIGHT")
    return ready


def load_lang_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as fh:
        raw = fh.read()
        # The CSV uses a bare newline inside some quoted names; csv handles it
        # only if the file is read as text with newline="".
        fh2 = io.StringIO(raw, newline="")
        return list(csv.DictReader(fh2))


def build(db_path: Path, cache_dir: Path) -> int:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()

    lang_paths = fetch_csvs(cache_dir, [])
    lang_rows = {lang: load_lang_rows(path) for lang, path in lang_paths}
    counts = {lang: len(rows) for lang, rows in lang_rows.items()}
    if len(set(counts.values())) != 1:
        raise SystemExit(f"CSV row counts differ across languages: {counts}")
    n = counts["en"]

    headers = set(lang_rows["en"][0].keys()) if n else set()
    expected = {"name", "energy", "proteins", "carbohydrates", "fats"}
    if not expected.issubset(headers):
        raise SystemExit(f"Unexpected CSV headers: {sorted(headers)[:10]} …")

    conn = sqlite3.connect(db_path)
    try:
        create_schema(
            conn,
            nutrient_columns=NUTRIENT_COLUMNS,
            fixed_columns=[
                "id INTEGER PRIMARY KEY",
                "lang TEXT NOT NULL",
                "name TEXT NOT NULL",
                "tokens TEXT NOT NULL",
            ],
            fts_columns=["name", "tokens"],
            fts_rowid="id",
            extra_statements=[
                "CREATE INDEX foods_name_idx ON foods(name);",
                "CREATE INDEX foods_lang_idx ON foods(lang);",
            ],
        )
        food_id = 0
        for lang in LANGS:
            for row in lang_rows[lang]:
                cols = ["id", "lang", "name", "tokens"]
                values: list = [food_id, lang, row["name"], tokenize(row["name"])]
                for col in NUTRIENT_COLUMNS:
                    src, mult = NUTRIENT_MAP[col]
                    value = parse_float(row.get(src))
                    cols.append(col)
                    values.append(value * mult if value is not None else None)
                placeholders = ",".join("?" for _ in cols)
                conn.execute(
                    f"INSERT OR REPLACE INTO foods ({','.join(cols)}) "
                    f"VALUES ({placeholders})",
                    values,
                )
                food_id += 1
        rebuild_fts(conn)
        set_meta(conn, "dataset_version", DATASET_VERSION)
        set_meta(conn, "source", SOURCE_LABEL)
        set_meta(conn, "langs", ",".join(LANGS))
        conn.commit()
    finally:
        conn.close()
    return food_id


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out-dir", type=Path, default=OUT_DIR)
    parser.add_argument("--cache-dir", type=Path, default=CACHE_DIR)
    args = parser.parse_args()
    out_dir = args.out_dir
    cache_dir = args.cache_dir

    print(f"Building {DATASET_VERSION} from {len(FILES)} CSVs into {out_dir}")
    db_path = out_dir / "swiss_foods.sqlite"
    count = build(db_path, cache_dir)
    manifest = write_manifest(
        db_path,
        {
            "dataset_version": DATASET_VERSION,
            "food_count": count,
            "langs": LANGS,
            "source": SOURCE_LABEL,
        },
    )
    print(f"  wrote {db_path} ({db_path.stat().st_size} bytes, {count} foods)")
    print(f"  wrote {manifest}")


if __name__ == "__main__":
    main()
