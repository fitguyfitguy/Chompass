#!/usr/bin/env python3
"""Validate testdata/parity fixtures against contracts/ JSON Schemas."""
from __future__ import annotations

import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]

PAIRS = [
    ("contracts/diary-1.1.schema.json", "testdata/parity/diary-sample.json"),
    ("contracts/body-metrics-1.0.schema.json", "testdata/parity/body-metrics-sample.json"),
    ("contracts/meal-share-v1.schema.json", "testdata/parity/meal-share-sample.json"),
    ("contracts/sync-1.0.schema.json", "testdata/parity/sync-sample.json"),
]


def main() -> int:
    failed = 0
    for schema_rel, data_rel in PAIRS:
        schema_path = ROOT / schema_rel
        data_path = ROOT / data_rel
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        data = json.loads(data_path.read_text(encoding="utf-8"))
        validator = Draft202012Validator(schema)
        errors = sorted(validator.iter_errors(data), key=lambda e: list(e.path))
        if errors:
            failed += 1
            print(f"FAIL {data_rel} vs {schema_rel}")
            for err in errors[:20]:
                path = "/".join(str(p) for p in err.path) or "(root)"
                print(f"  - {path}: {err.message}")
        else:
            print(f"OK   {data_rel}")
    if failed:
        return 1
    print("All contract fixtures valid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
