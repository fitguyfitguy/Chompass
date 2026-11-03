"""Shared helpers for the offline food-index SQLite builders.

Both ``build_usda_food_index.py`` and ``build_swiss_food_index.py`` emit a compact
read-only SQLite with a ``foods`` table (per-100g nutrient columns plus a ``tokens``
search column), a ``meta`` table, and an external-content FTS5 virtual table over
``foods``. This module holds the parts the two builders share: tokenization, the
foods/meta/FTS5 schema DDL, FTS rebuild, sha256, manifest writing, and meta rows.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path


def tokenize(text: str) -> str:
    return " ".join("".join(ch.lower() if ch.isalnum() else " " for ch in text).split())


def create_schema(
    conn: sqlite3.Connection,
    nutrient_columns: list[str],
    fixed_columns: list[str],
    fts_columns: list[str],
    fts_rowid: str,
    extra_statements: list[str] | None = None,
) -> None:
    """Create the shared ``meta`` + ``foods`` + external-content ``foods_fts`` schema.

    ``fixed_columns`` are the non-nutrient ``foods`` columns as ``"name TYPE"``
    strings (e.g. ``"fdc_id INTEGER PRIMARY KEY"``). ``fts_columns`` are the text
    columns indexed by FTS5 and ``fts_rowid`` the ``foods`` column used as the FTS
    rowid. ``extra_statements`` run after ``foods`` is created (secondary indexes,
    side tables); include any ``DROP`` statements for side tables there too.
    """
    nutrient_ddl = ",\n  ".join(f"{c} REAL" for c in nutrient_columns)
    fixed_ddl = ",\n  ".join(fixed_columns)
    fts_ddl = ",\n  ".join(fts_columns)
    extra = ";\n".join(extra_statements or ())
    conn.executescript(
        f"""
        DROP TABLE IF EXISTS foods_fts;
        DROP TABLE IF EXISTS foods;
        DROP TABLE IF EXISTS meta;
        CREATE TABLE meta (
          key TEXT PRIMARY KEY,
          value TEXT NOT NULL
        );
        CREATE TABLE foods (
          {fixed_ddl},
          {nutrient_ddl}
        );
        {extra}
        CREATE VIRTUAL TABLE foods_fts USING fts5(
          {fts_ddl},
          content='foods',
          content_rowid='{fts_rowid}'
        );
        """
    )


def rebuild_fts(conn: sqlite3.Connection) -> None:
    conn.execute("INSERT INTO foods_fts(foods_fts) VALUES('rebuild')")


def set_meta(conn: sqlite3.Connection, key: str, value: str) -> None:
    conn.execute("INSERT INTO meta(key,value) VALUES (?,?)", (key, value))


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def write_manifest(db_path: Path, manifest: dict) -> Path:
    """Write ``<db_stem>.manifest.json`` next to [db_path] with a fresh sha256."""
    full = {**manifest, "sha256": sha256(db_path)}
    path = db_path.with_name(f"{db_path.stem}.manifest.json")
    path.write_text(json.dumps(full, indent=2) + "\n", encoding="utf-8")
    return path


# -- Downloadable offline barcode packs (see build_usda_gtin_index.py /
# -- build_off_country_index.py): barcode-exact SQLite, no FTS.

BARCODE_PACK_SCHEMA = """
CREATE TABLE barcodes(
  code TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  brand TEXT,
  kcal REAL,
  protein REAL,
  carbs REAL,
  fat REAL,
  serving_g REAL
) WITHOUT ROWID;
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
"""


def create_barcode_pack(conn: sqlite3.Connection) -> None:
    """Create the barcode-pack schema (``barcodes`` + ``meta``, WITHOUT ROWID)."""
    conn.executescript(BARCODE_PACK_SCHEMA)


def insert_barcode_pack_meta(conn: sqlite3.Connection, meta: dict[str, str]) -> None:
    conn.executemany("INSERT INTO meta(key, value) VALUES (?, ?)", sorted(meta.items()))


def insert_barcode_pack_rows(
    conn: sqlite3.Connection,
    rows: list[tuple],
) -> int:
    """Insert ``(code, name, brand, kcal, protein, carbs, fat, serving_g)`` tuples."""
    conn.executemany(
        "INSERT INTO barcodes(code, name, brand, kcal, protein, carbs, fat, serving_g) "
        "VALUES(?,?,?,?,?,?,?,?)",
        rows,
    )
    return len(rows)


def emit_barcode_pack(
    db_path: Path,
    pack_id: str,
    version: str,
    rows: int,
    license_text: str,
    source: str,
    out_dir: Path,
) -> tuple[Path, Path]:
    """Gzip [db_path] into ``<pack-id>-<version>.sqlite.gz`` and write the manifest
    JSON (``sha256``/``bytes`` describe the ``.gz``; ``url`` is the Codeberg
    release asset location by the naming contract). Returns (gz, manifest)."""
    import gzip

    out_dir.mkdir(parents=True, exist_ok=True)
    gz_path = out_dir / f"{pack_id}-{version}.sqlite.gz"
    with db_path.open("rb") as src, gzip.open(gz_path, "wb") as dst:
        for chunk in iter(lambda: src.read(1 << 20), b""):
            dst.write(chunk)
    manifest = {
        "id": pack_id,
        "version": version,
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "rows": rows,
        "sha256": sha256(gz_path),
        "bytes": gz_path.stat().st_size,
        "license": license_text,
        "source": source,
        "url": (
            f"https://codeberg.org/fitguy/chompass/releases/download/"
            f"offline-index-{version}/{pack_id}-{version}.sqlite.gz"
        ),
    }
    manifest_path = out_dir / f"{pack_id}-{version}.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return gz_path, manifest_path
