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
