#!/usr/bin/env python3
"""Build text-only Lq / L1 manifests from image+note clones.

Same diary strings as ``*_image_text_lq.jsonl`` / ``*_image_text_l1.jsonl``, but
``modality=text`` and no ``image_path`` — scores typed entry with vague
quantity language (no photo), the common diary-style logging path.

Usage::

    uv run python docs/benchmarks/food_accuracy/build_text_lq.py
    uv run python docs/benchmarks/food_accuracy/build_text_lq.py --prefix jfb --levels lq l1
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from schema import DATA_DIR, Sample, load_manifest, write_manifest

MANIFEST_DIR = DATA_DIR / "manifests"

GRAM_IN_TEXT = re.compile(
    r"(?i)(?:^|[\s,;(])\d+(?:\.\d+)?\s*(?:g|gram|grams)\b|\(\s*\d+(?:\.\d+)?\s*g\s*\)"
)

LEVEL_NOTES = {
    "lq": "text-only Lq vague quantity; no grams in text; derived from image Lq",
    "l1": "text-only L1 meal title / coarse identity; derived from image L1",
}


def assert_no_grams(sample_id: str, text: str) -> None:
    if GRAM_IN_TEXT.search(text):
        raise SystemExit(f"{sample_id}: text-only prompt still contains grams: {text!r}")


def to_text_only(sample: Sample, *, level: str) -> Sample | None:
    text = (sample.text or "").strip()
    if not text:
        return None
    if level == "lq":
        assert_no_grams(sample.id, text)
    note = LEVEL_NOTES[level]
    notes = sample.notes or ""
    if note not in notes:
        notes = f"{notes}; {note}" if notes else note
    extra = dict(sample.extra)
    extra["note_level"] = level
    extra["derived_from_image_manifest"] = True
    return Sample(
        id=sample.id,
        modality="text",
        source=sample.source,
        calories=sample.calories,
        protein_g=sample.protein_g,
        carbs_g=sample.carbs_g,
        fat_g=sample.fat_g,
        text=text,
        image_path=None,
        mass_g=sample.mass_g,
        meal_name=sample.meal_name,
        notes=notes,
        extra=extra,
    )


def build_level(
    samples: list[Sample],
    out_path: Path,
    *,
    level: str,
) -> int:
    out: list[Sample] = []
    for sample in samples:
        converted = to_text_only(sample, level=level)
        if converted is None:
            continue
        out.append(converted)
    write_manifest(out_path, out)
    return len(out)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--prefix",
        nargs="*",
        default=["jfb", "n5k"],
        help="Manifest stems under data/manifests (default: jfb n5k)",
    )
    parser.add_argument(
        "--levels",
        nargs="*",
        default=["lq", "l1"],
        choices=["lq", "l1"],
        help="Which note levels to convert (default: lq l1)",
    )
    parser.add_argument(
        "--manifests-dir",
        type=Path,
        default=MANIFEST_DIR,
    )
    args = parser.parse_args()

    for prefix in args.prefix:
        for level in args.levels:
            src = args.manifests_dir / f"{prefix}_image_text_{level}.jsonl"
            if not src.exists():
                print(f"SKIP {prefix}/{level}: missing {src}", file=sys.stderr)
                continue
            samples = load_manifest(src)
            out_path = args.manifests_dir / f"{prefix}_text_{level}.jsonl"
            n = build_level(samples, out_path, level=level)
            print(f"{prefix}/{level}: wrote {n} text-only samples -> {out_path}")


if __name__ == "__main__":
    main()
