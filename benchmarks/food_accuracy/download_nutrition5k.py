#!/usr/bin/env python3
"""Download Nutrition5k metadata and optional overhead RGB subset."""

from __future__ import annotations

import argparse
import csv
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from io import StringIO
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from schema import DATA_DIR, Sample, write_manifest

GCS_HTTPS = "https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset"
GCS_PREFIX = "gs://nutrition5k_dataset/nutrition5k_dataset"


def fetch_text(url: str) -> str:
    with urllib.request.urlopen(url, timeout=120) as resp:
        return resp.read().decode("utf-8")


def download_metadata(meta_dir: Path) -> None:
    meta_dir.mkdir(parents=True, exist_ok=True)
    files = [
        "metadata/dish_metadata_cafe1.csv",
        "metadata/dish_metadata_cafe2.csv",
        "dish_ids/splits/rgb_train_ids.txt",
        "dish_ids/splits/rgb_test_ids.txt",
    ]
    optional = ["metadata/ingredient_metadata.csv"]
    for rel in files + optional:
        dest = meta_dir / rel
        if dest.exists():
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        url = f"{GCS_HTTPS}/{rel}"
        print(f"Fetching {url}")
        try:
            dest.write_text(fetch_text(url), encoding="utf-8")
        except urllib.error.HTTPError as exc:
            if rel in optional:
                print(f"SKIP optional {rel} ({exc.code})")
                continue
            raise


def load_test_ids(meta_dir: Path) -> list[str]:
    test_path = meta_dir / "dish_ids/splits/rgb_test_ids.txt"
    if not test_path.exists():
        raise FileNotFoundError(f"Missing test split: {test_path}")
    return [line.strip() for line in test_path.read_text().splitlines() if line.strip()]


def parse_dish_row(row_text: str) -> dict[str, str | float]:
    # Nutrition5k dish CSV rows are comma-separated but ingredient names may contain commas.
    # Format: dish_id, total_calories, total_mass, total_fat, total_carb, total_protein, num_ingrs, ...
    reader = csv.reader(StringIO(row_text))
    fields = next(reader)
    if len(fields) < 7:
        raise ValueError(f"Unexpected dish row: {row_text[:120]}...")
    return {
        "dish_id": fields[0],
        "calories": float(fields[1]),
        "mass_g": float(fields[2]),
        "fat_g": float(fields[3]),
        "carbs_g": float(fields[4]),
        "protein_g": float(fields[5]),
    }


def load_dish_metadata(meta_dir: Path) -> dict[str, dict[str, str | float]]:
    dishes: dict[str, dict[str, str | float]] = {}
    for name in ("metadata/dish_metadata_cafe1.csv", "metadata/dish_metadata_cafe2.csv"):
        path = meta_dir / name
        for line in path.read_text().splitlines():
            line = line.strip()
            if not line:
                continue
            parsed = parse_dish_row(line)
            dishes[str(parsed["dish_id"])] = parsed
    return dishes


def gsutil_available() -> bool:
    return shutil.which("gsutil") is not None


def overhead_rgb_url(dish_id: str) -> str:
    return f"{GCS_HTTPS}/imagery/realsense_overhead/{dish_id}/rgb.png"


def overhead_rgb_exists(dish_id: str) -> bool:
    req = urllib.request.Request(overhead_rgb_url(dish_id), method="HEAD")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status == 200
    except urllib.error.HTTPError:
        return False


def download_overhead_rgb(dish_id: str, imagery_dir: Path) -> Path | None:
    """Download rgb.png for a dish when overhead capture exists (~3.5k of 5k dishes)."""
    local_dir = imagery_dir / "realsense_overhead" / dish_id
    local_rgb = local_dir / "rgb.png"
    if local_rgb.exists():
        return local_rgb
    if not overhead_rgb_exists(dish_id):
        return None

    local_dir.mkdir(parents=True, exist_ok=True)
    url = overhead_rgb_url(dish_id)
    if gsutil_available():
        gcs_path = f"{GCS_PREFIX}/imagery/realsense_overhead/{dish_id}/rgb.png"
        cmd = ["gsutil", "-q", "cp", gcs_path, str(local_rgb)]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode == 0 and local_rgb.exists():
            return local_rgb

    try:
        urllib.request.urlretrieve(url, local_rgb)
    except urllib.error.HTTPError:
        return None
    return local_rgb if local_rgb.exists() else None


def dish_ids_with_overhead(candidate_ids: list[str], needed: int) -> list[str]:
    """Return up to `needed` dish IDs that have overhead rgb.png on GCS."""
    found: list[str] = []
    for dish_id in candidate_ids:
        if overhead_rgb_exists(dish_id):
            found.append(dish_id)
        if len(found) >= needed:
            break
    return found


def build_manifest(
    meta_dir: Path,
    imagery_dir: Path,
    out_path: Path,
    *,
    limit: int | None,
    metadata_only: bool,
) -> int:
    test_ids = load_test_ids(meta_dir)
    dishes = load_dish_metadata(meta_dir)
    repo_root = _HERE.parents[1]

    target_ids = test_ids
    if not metadata_only and limit is not None:
        # rgb_test_ids includes dishes without overhead capture; probe until enough exist.
        target_ids = dish_ids_with_overhead(test_ids, limit)
        if len(target_ids) < limit:
            print(
                f"WARN: only {len(target_ids)} test dishes have overhead RGB; "
                "scanning remaining metadata ids",
                file=sys.stderr,
            )
            remaining = [d for d in dishes if d not in set(target_ids)]
            target_ids.extend(dish_ids_with_overhead(remaining, limit - len(target_ids)))

    samples: list[Sample] = []
    for dish_id in target_ids:
        if limit is not None and len(samples) >= limit:
            break
        meta = dishes.get(dish_id)
        if meta is None:
            continue

        image_path: str | None = None
        if not metadata_only:
            rgb = download_overhead_rgb(dish_id, imagery_dir)
            if rgb is None:
                continue
            image_path = str(rgb.relative_to(repo_root))

        samples.append(
            Sample(
                id=f"n5k-{dish_id}",
                modality="image",
                source="nutrition5k",
                image_path=image_path,
                meal_name=dish_id,
                calories=float(meta["calories"]),
                protein_g=float(meta["protein_g"]),
                carbs_g=float(meta["carbs_g"]),
                fat_g=float(meta["fat_g"]),
                mass_g=float(meta["mass_g"]),
                notes="Nutrition5k overhead RGB when available",
            )
        )

    write_manifest(out_path, samples)
    return len(samples)


def main() -> None:
    parser = argparse.ArgumentParser(description="Download Nutrition5k metadata and optional RGB subset")
    parser.add_argument("--limit", type=int, default=20, help="Max test dishes (default 20)")
    parser.add_argument(
        "--metadata-only",
        action="store_true",
        help="Fetch metadata CSVs only; write manifest without image_path",
    )
    parser.add_argument(
        "--out",
        default=str(DATA_DIR / "manifests" / "n5k.jsonl"),
        help="Output manifest path",
    )
    parser.add_argument(
        "--data-dir",
        default=str(DATA_DIR / "nutrition5k"),
        help="Cache directory",
    )
    args = parser.parse_args()

    meta_dir = Path(args.data_dir) / "metadata_cache"
    imagery_dir = Path(args.data_dir)
    download_metadata(meta_dir)

    if not args.metadata_only and not gsutil_available():
        print("WARN: gsutil not found; use --metadata-only or install Google Cloud SDK", file=sys.stderr)

    count = build_manifest(
        meta_dir,
        imagery_dir,
        Path(args.out),
        limit=args.limit,
        metadata_only=args.metadata_only,
    )
    print(f"Wrote {count} samples to {args.out}")


if __name__ == "__main__":
    main()
