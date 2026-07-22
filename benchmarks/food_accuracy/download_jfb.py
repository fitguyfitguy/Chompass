#!/usr/bin/env python3
"""Download January Food Benchmark (JFB) dataset and build manifest."""

from __future__ import annotations

import argparse
import ast
import sys
import tarfile
import urllib.request
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from schema import DATA_DIR, Sample, write_manifest

S3_BUCKET = "january-food-image-dataset-public"
S3_KEY = "food-scan-benchmark-dataset.tar.gz"
S3_URL = f"https://{S3_BUCKET}.s3.amazonaws.com/{S3_KEY}"


def download_archive(dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        print(f"Archive already present: {dest}")
        return
    print(f"Downloading JFB dataset from {S3_URL} ...")
    urllib.request.urlretrieve(S3_URL, dest)
    print(f"Saved {dest}")


def extract_archive(archive: Path, root: Path) -> None:
    csv_path = root / "food-scan-benchmark-dataset" / "food_scan_bench_v1.csv"
    if csv_path.exists():
        print(f"Dataset already extracted: {root}")
        return
    print("Extracting archive...")
    with tarfile.open(archive) as tar:
        tar.extractall(path=root)
    print("Extraction complete.")


def parse_ingredients(raw: str | list) -> list:
    if isinstance(raw, list):
        return raw
    try:
        parsed = ast.literal_eval(raw)
        if isinstance(parsed, str):
            parsed = ast.literal_eval(parsed)
        if isinstance(parsed, list):
            return parsed
    except (ValueError, SyntaxError):
        pass
    return []


def ingredient_names_text(ingredients: list) -> str | None:
    names = [item.get("name", "").strip() for item in ingredients if isinstance(item, dict)]
    names = [name for name in names if name]
    if not names:
        return None
    return ", ".join(names)


def write_image_text_variants(samples: list[Sample], manifests_dir: Path) -> None:
    """Write L0 (base), L1 (meal_name as text), L2 (ingredient names as text)."""
    l1: list[Sample] = []
    l2: list[Sample] = []
    for sample in samples:
        l1_text = sample.meal_name
        l2_text = ingredient_names_text(sample.extra.get("ingredients", []))
        l1.append(
            Sample(
                id=sample.id,
                modality=sample.modality,
                source=sample.source,
                calories=sample.calories,
                protein_g=sample.protein_g,
                carbs_g=sample.carbs_g,
                fat_g=sample.fat_g,
                text=l1_text,
                image_path=sample.image_path,
                mass_g=sample.mass_g,
                meal_name=sample.meal_name,
                notes=sample.notes,
                extra=dict(sample.extra),
            )
        )
        l2.append(
            Sample(
                id=sample.id,
                modality=sample.modality,
                source=sample.source,
                calories=sample.calories,
                protein_g=sample.protein_g,
                carbs_g=sample.carbs_g,
                fat_g=sample.fat_g,
                text=l2_text,
                image_path=sample.image_path,
                mass_g=sample.mass_g,
                meal_name=sample.meal_name,
                notes=sample.notes,
                extra=dict(sample.extra),
            )
        )
    write_manifest(manifests_dir / "jfb_image_text_l1.jsonl", l1)
    write_manifest(manifests_dir / "jfb_image_text_l2.jsonl", l2)
    print(f"Wrote {len(l1)} samples to {manifests_dir / 'jfb_image_text_l1.jsonl'}")
    print(f"Wrote {len(l2)} samples to {manifests_dir / 'jfb_image_text_l2.jsonl'}")


def build_manifest(root: Path, out_path: Path, limit: int | None) -> list[Sample]:
    import csv

    csv_path = root / "food-scan-benchmark-dataset" / "food_scan_bench_v1.csv"
    img_dir = root / "food-scan-benchmark-dataset" / "fsb_images"
    if not csv_path.exists():
        raise FileNotFoundError(f"Missing CSV after extract: {csv_path}")

    samples: list[Sample] = []
    with csv_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for idx, row in enumerate(reader):
            if limit is not None and idx >= limit:
                break
            image_filename = row["image_filename"]
            rel_image = img_dir.relative_to(_HERE.parents[1]) / image_filename
            samples.append(
                Sample(
                    id=f"jfb-{row['image_id']}",
                    modality="image",
                    source="jfb",
                    text=None,
                    image_path=str(rel_image),
                    meal_name=row.get("meal_name") or None,
                    calories=float(row["total_calories"]),
                    protein_g=float(row["total_protein"]),
                    carbs_g=float(row["total_carbs"]),
                    fat_g=float(row["total_fat"]),
                    extra={"ingredients": parse_ingredients(row.get("ingredients_list", "[]"))},
                )
            )

    write_manifest(out_path, samples)
    write_image_text_variants(samples, out_path.parent)
    return samples


def main() -> None:
    parser = argparse.ArgumentParser(description="Download JFB and write image manifest")
    parser.add_argument("--limit", type=int, default=50, help="Max images in manifest (default 50)")
    parser.add_argument(
        "--out",
        default=str(DATA_DIR / "manifests" / "jfb.jsonl"),
        help="Output manifest path",
    )
    parser.add_argument(
        "--data-dir",
        default=str(DATA_DIR / "jfb"),
        help="Cache directory for images",
    )
    args = parser.parse_args()

    root = Path(args.data_dir)
    archive = root / "fsb.tar.gz"
    download_archive(archive)
    extract_archive(archive, root)

    count = len(build_manifest(root, Path(args.out), args.limit))
    print(f"Wrote {count} samples to {args.out}")


if __name__ == "__main__":
    main()
