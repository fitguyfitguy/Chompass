# /// script
# requires-python = ">=3.11"
# dependencies = ["pyarrow"]
# ///
#!/usr/bin/env python3
"""Build the Open Food Facts country barcode pack (``off-de``) — Chompass offline pack.

Streams the nightly OFF product dataset — an ~8 GB Parquet file on Hugging
Face (``openfoodfacts/product-database`` / ``food.parquet``) — in row-group
batches, reading only the columns the pack needs. Filters to one country tag
(pilot: ``de``), keeps rows with a numeric code, a product name and per-100g
energy, and emits the shared barcode-pack schema (see
``build_usda_gtin_index.py``).

If the upstream schema drifts, re-run with ``--revision <sha>`` pinned to a
known-good dataset revision (the sha is recorded in the manifest's ``source``)
and adjust the column/field lists only when a column was truly renamed.

Publishing (maintainer, one-time per refresh): see the header of
``build_usda_gtin_index.py`` — one ``offline-index-<version>`` Codeberg
release carries both packs.

Usage:
    uv run scripts/build_off_country_index.py                    # latest nightly (large download)
    uv run scripts/build_off_country_index.py --revision <sha>   # pin a dataset revision
    uv run scripts/build_off_country_index.py --fixture          # tiny committed test pack

Note: plain ``uv run scripts/...`` (no ``python``) so uv honours the PEP 723
dependency header above; ``uv run python scripts/...`` skips it and dies on
``import pyarrow``.
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
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

OUT_DIR = REPO / "build" / "offline-index"
FIXTURE_OUT = REPO / "android/app/src/test/resources/offlinepack/fixture_de.sqlite"
CACHE_DIR = REPO / "build" / "off-parquet"

PACK_ID = "off-de"
LICENSE_TEXT = "Open Database Licence (ODbL) — Open Food Facts"
DATASET_BASE = "https://huggingface.co/datasets/openfoodfacts/product-database/resolve"

# countries_tags carry language prefixes (`en:germany`) and the English slug
# differs from the CLI's ISO code (`de`). Bare and prefix-stripped matches both
# count; near-misses like `en:east-germany` stay out. Extend per new pilot.
COUNTRY_TAG_SLUGS = {"de": "germany"}


def matches_country(tags: list[str] | None, country_tag: str) -> bool:
    slug = COUNTRY_TAG_SLUGS.get(country_tag, country_tag)
    for tag in tags or []:
        if tag == country_tag:
            return True
        local = tag.split(":", 1)[-1]
        if local == country_tag or local == slug:
            return True
    return False

# Columns read from the parquet (nothing else is pulled off disk).
COLUMNS = ["code", "product_name", "brands", "countries_tags", "serving_quantity", "nutriments"]
# Fields inside the ``nutriments`` struct. energy_100g (kJ) backs kcal up,
# mirroring the app's OFF mapping.
NUTRIENT_FIELDS = ("energy-kcal_100g", "energy_100g", "proteins_100g", "carbohydrates_100g", "fat_100g")
KJ_TO_KCAL = 0.23900573614

# ~a dozen hard-coded rows for the committed reader test (offline CI).
FIXTURE_ROWS = [
    ("4006381333931", "Sprite Zitrone", "Coca-Cola", 42.0, 0.0, 10.3, 0.0, 330.0),
    ("3017620422003", "Pâte à tartiner chocolat noisettes", "Ferrero", 539.0, 6.3, 57.5, 30.9, 15.0),
    ("4056489307517", "Haferdrink Barista", "Alpro", 47.0, 1.1, 6.7, 1.6, 200.0),
    ("4008400402223", "Vollkornbrot Roggen", "Mestemacher", 211.0, 6.9, 36.0, 1.9, 50.0),
    ("4000417025006", "Erdbeeren", "Gut & Günstig", None, None, None, None, 500.0),
    ("4104420207505", "Gouda Mittelalt", "Bergader", 356.0, 25.0, 0.0, 28.0, 30.0),
    ("4066600674701", "Linsen Toru", "Seeberger", 340.0, 24.0, 37.0, 1.5, 40.0),
    ("8710398523516", "Kaffee Milch Mischgetränk", "Jacobs", 47.0, 1.2, 6.0, 1.7, 180.0),
    ("4001686310917", "Mandelmus", "Rapunzel", 610.0, 21.0, 8.0, 54.0, 15.0),
    ("40131669", "Bionade Litschi", "Bionade", 21.0, 0.0, 5.0, 0.0, 330.0),
    ("4260244550112", "Röstzwiebeln", "BioCenter", 0.0, 0.0, 0.0, 0.0, 25.0),
    ("5449000000996", "Coca-Cola Zero", "Coca-Cola", 0.3, 0.0, 0.0, 0.0, 330.0),
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--countries", default="de", help="countries_tags entry to keep (pilot: de)")
    parser.add_argument(
        "--revision",
        default="main",
        help="dataset revision (branch, tag or commit sha) of openfoodfacts/product-database",
    )
    parser.add_argument(
        "--version",
        default=datetime.now(timezone.utc).strftime("%Y%m%d"),
        help="pack version token (yyyymmdd; becomes the release asset name)",
    )
    parser.add_argument("--fixture", action="store_true", help="emit the tiny committed test pack and exit")
    return parser.parse_args()


def build_from_parquet(
    parquet_path: Path,
    db_path: Path,
    country_tag: str,
    source: str,
    dataset_version: str,
) -> int:
    import pyarrow.parquet as pq

    pf = pq.ParquetFile(parquet_path)
    schema = pf.schema_arrow
    names = set(schema.names)
    missing = [c for c in COLUMNS if c not in names]
    if missing:
        raise SystemExit(
            f"OFF parquet schema drift: missing columns {missing}. "
            f"Present: {sorted(names)}. Pin --revision to a known-good dataset "
            "revision, or update COLUMNS/NUTRIENT_FIELDS for a real rename."
        )
    nutr_type = schema.field("nutriments").type
    # The 2026-11 nightly moved nutriments from a flat struct keyed by
    # `energy-kcal_100g`-style fields to a list of per-nutrient structs
    # (`name` + `100g`/`serving`/…), and product_name to a localized list of
    # {lang, text} structs. Anything else is drift worth pinning.
    elem_names = set(getattr(getattr(nutr_type, "value_type", None), "names", []) or [])
    if nutr_type.__class__.__name__ != "ListType" or not {"name", "100g"} <= elem_names:
        raise SystemExit(
            "OFF parquet nutriments column changed shape: expected "
            f"list<struct> with name/100g fields, got {nutr_type}. "
            "Pin --revision to a known-good dataset revision or update the reader."
        )
    pname_type = schema.field("product_name").type
    pname_elem_names = set(getattr(getattr(pname_type, "value_type", None), "names", []) or [])
    if pname_type.__class__.__name__ != "ListType" or not {"lang", "text"} <= pname_elem_names:
        raise SystemExit(
            "OFF parquet product_name column changed shape: expected "
            f"list<struct> with lang/text fields, got {pname_type}. "
            "Pin --revision to a known-good dataset revision or update the reader."
        )

    if db_path.exists():
        db_path.unlink()
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    by_code: dict[str, tuple] = {}
    try:
        create_barcode_pack(conn)
        for batch in pf.iter_batches(batch_size=65536, columns=COLUMNS):
            codes = batch.column("code").to_pylist()
            # product_name is a localized list of {lang, text} structs; resolve
            # the row name to its `main` entry, else the first non-empty text.
            pname_col = batch.column("product_name")
            pname_elems = pname_col.flatten()
            pname_langs = pname_elems.field("lang").to_pylist()
            pname_texts = pname_elems.field("text").to_pylist()
            pname_offsets = pname_col.offsets.to_pylist()
            product_names: list[str | None] = []
            for i in range(len(pname_offsets) - 1):
                main = None
                first = None
                for lang, text in zip(
                    pname_langs[pname_offsets[i]:pname_offsets[i + 1]],
                    pname_texts[pname_offsets[i]:pname_offsets[i + 1]],
                ):
                    if not text:
                        continue
                    if first is None:
                        first = text
                    if lang == "main":
                        main = text
                        break
                product_names.append(main or first)
            brands = batch.column("brands").to_pylist()
            countries = batch.column("countries_tags").to_pylist()
            servings = batch.column("serving_quantity").to_pylist()
            nutr_lists: dict[str, list] = {f: [] for f in NUTRIENT_FIELDS}
            # nutriments is a list of {name, value, 100g, serving, …} structs per
            # row; the per-100g value of old flat key `energy-kcal_100g` is the
            # entry named `energy-kcal` with its `100g` field set. RecordBatch
            # columns are plain Arrays (no chunks).
            nutr_col = batch.column("nutriments")
            elems = nutr_col.flatten()
            entry_names = elems.field("name").to_pylist()
            per100 = elems.field("100g").to_pylist()
            offsets = nutr_col.offsets.to_pylist()
            for i in range(len(offsets) - 1):
                row_values: dict[str, float] = {}
                for name, value in zip(
                    entry_names[offsets[i]:offsets[i + 1]],
                    per100[offsets[i]:offsets[i + 1]],
                ):
                    if name and value is not None and name not in row_values:
                        row_values[name] = value
                for field in NUTRIENT_FIELDS:
                    nutr_lists[field].append(row_values.get(field[:-5]))

            for i, raw_code in enumerate(codes):
                code = (raw_code or "").strip() if isinstance(raw_code, str) else ""
                if not code.isdigit():
                    continue
                tags = countries[i] or []
                if not matches_country(tags, country_tag):
                    continue
                raw_name = product_names[i]
                name = raw_name.strip() if isinstance(raw_name, str) else ""
                if not name:
                    continue
                kcal = nutr_lists["energy-kcal_100g"][i]
                if kcal is None and nutr_lists["energy_100g"][i] is not None:
                    kcal = nutr_lists["energy_100g"][i] * KJ_TO_KCAL
                if kcal is None:
                    continue
                if code in by_code:
                    continue
                raw_brand = brands[i]
                brand = raw_brand.split(",")[0].strip() or None if isinstance(raw_brand, str) else None
                serving = servings[i]
                serving_g = float(serving) if serving is not None and float(serving) > 0 else None
                by_code[code] = (
                    name,
                    brand,
                    float(kcal),
                    nutr_lists["proteins_100g"][i],
                    nutr_lists["carbohydrates_100g"][i],
                    nutr_lists["fat_100g"][i],
                    serving_g,
                )

        insert_barcode_pack_rows(conn, [(code, *row) for code, row in by_code.items()])
        insert_barcode_pack_meta(conn, {
            "pack_id": PACK_ID,
            "dataset_version": dataset_version,
            "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "row_count": str(len(by_code)),
            "license": LICENSE_TEXT,
            "source": source,
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

    import urllib.request

    url = f"{DATASET_BASE}/{args.revision}/food.parquet"
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    parquet_path = CACHE_DIR / f"food-{args.revision}.parquet"
    if not parquet_path.exists():
        print(f"Downloading {url} (~8 GB) -> {parquet_path}")
        urllib.request.urlretrieve(url, parquet_path)

    work_db = OUT_DIR / f"{PACK_ID}-{args.version}.sqlite"
    source = f"{url} revision={args.revision}"
    rows = build_from_parquet(parquet_path, work_db, args.countries, source, "nightly")
    gz_path, manifest_path = emit_barcode_pack(
        work_db,
        PACK_ID,
        args.version,
        rows,
        LICENSE_TEXT,
        source,
        OUT_DIR,
    )
    work_db.unlink()
    print(f"{rows} barcodes -> {gz_path}")
    print(f"Manifest -> {manifest_path}")


if __name__ == "__main__":
    main()
