#!/usr/bin/env python3
"""Download Penrose / StatLib bodyfat (252 men, hydrostatic + Siri %fat).

Abdomen circumference is treated as Navy waist (umbilicus). Site mismatch is
left as-is; do not 'correct' the formula.

Source: OpenML did 560 (Johnson 1996 / StatLib columns). Weight pounds,
height inches, circumferences cm. class = Siri BF%.
"""

from __future__ import annotations

import argparse
import urllib.request
from pathlib import Path

from schema import DATA_DIR, Subject, write_jsonl

OPENML_ARFF = "https://www.openml.org/data/v1/download/52738/bodyfat.arff"
LB_TO_KG = 0.45359237
IN_TO_CM = 2.54


def _parse_arff(text: str) -> list[list[float]]:
    rows: list[list[float]] = []
    in_data = False
    for line in text.splitlines():
        if line.strip().lower().startswith("@data"):
            in_data = True
            continue
        if not in_data or not line.strip() or line.startswith("%"):
            continue
        parts = [float(p.strip()) for p in line.split(",")]
        rows.append(parts)
    return rows


def download(dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 1000:
        return dest
    urllib.request.urlretrieve(OPENML_ARFF, dest)
    return dest


def to_subjects(rows: list[list[float]]) -> list[Subject]:
    subjects: list[Subject] = []
    for i, r in enumerate(rows, start=1):
        # Density, Age, Weight, Height, Neck, Chest, Abdomen, Hip, Thigh,
        # Knee, Ankle, Biceps, Forearm, Wrist, class
        subjects.append(
            Subject(
                id=f"penrose-{i:03d}",
                source="penrose",
                sex="male",
                age=int(round(r[1])),
                height_cm=r[3] * IN_TO_CM,
                weight_kg=r[2] * LB_TO_KG,
                neck_cm=r[4],
                chest_cm=r[5],
                waist_cm=r[6],
                hips_cm=r[7],
                thigh_cm=r[8],
                upper_arm_cm=r[11],
                wrist_cm=r[13],
                bf_percent=r[14],
                bf_method="hydrostatic",
            )
        )
    return subjects


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=DATA_DIR / "eval_penrose.jsonl")
    args = parser.parse_args()
    arff = download(DATA_DIR / "penrose" / "bodyfat.arff")
    rows = _parse_arff(arff.read_text(encoding="utf-8", errors="replace"))
    subjects = to_subjects(rows)
    write_jsonl(args.out, subjects)
    print(f"wrote {len(subjects)} rows -> {args.out}")


if __name__ == "__main__":
    main()
