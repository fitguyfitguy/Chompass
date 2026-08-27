#!/usr/bin/env python3
"""Track A JSONL subject schema (tabular only)."""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterator

HERE = Path(__file__).resolve().parent
DATA_DIR = HERE / "data"
RESULTS_DIR = HERE / "results"
MANIFEST_DIR = HERE / "manifest"
SEED_MANIFEST = MANIFEST_DIR / "eval_tabular_seed.jsonl"

SITE_FIELDS = (
    "neck_cm",
    "waist_cm",
    "hips_cm",
    "chest_cm",
    "upper_arm_cm",
    "thigh_cm",
    "calf_cm",
    "wrist_cm",
)


@dataclass
class Subject:
    id: str
    source: str
    sex: str
    age: int
    height_cm: float
    weight_kg: float
    bf_percent: float
    bf_method: str
    neck_cm: float | None = None
    waist_cm: float | None = None
    hips_cm: float | None = None
    chest_cm: float | None = None
    upper_arm_cm: float | None = None
    thigh_cm: float | None = None
    calf_cm: float | None = None
    wrist_cm: float | None = None
    ethnicity: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def write_jsonl(path: Path, rows: list[Subject]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row.to_dict(), ensure_ascii=True) + "\n")


def read_jsonl(path: Path) -> Iterator[Subject]:
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            raw = json.loads(line)
            yield Subject(**{k: raw.get(k) for k in Subject.__dataclass_fields__})
