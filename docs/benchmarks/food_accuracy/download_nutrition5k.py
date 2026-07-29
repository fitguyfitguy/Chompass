#!/usr/bin/env python3
"""Download Nutrition5k metadata and optional overhead RGB/depth/video subset."""

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

from image_text_variants import write_image_text_variants
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


def parse_dish_row(row_text: str) -> dict[str, str | float | list]:
    # Nutrition5k dish CSV rows are comma-separated but ingredient names may contain commas.
    # Format: dish_id, total_calories, total_mass, total_fat, total_carb, total_protein,
    # then repeating 7-field groups: ingr_id, name, grams, calories, fat, carbs, protein.
    reader = csv.reader(StringIO(row_text))
    fields = next(reader)
    if len(fields) < 7:
        raise ValueError(f"Unexpected dish row: {row_text[:120]}...")
    ingredients: list[dict[str, str | float]] = []
    for i in range(6, len(fields) - 6, 7):
        group = fields[i : i + 7]
        if len(group) < 7:
            break
        try:
            ingredients.append(
                {
                    "name": group[1],
                    "grams": float(group[2]),
                    "calories": float(group[3]),
                    "fat": float(group[4]),
                    "carbs": float(group[5]),
                    "protein": float(group[6]),
                }
            )
        except ValueError:
            # Occasional malformed group (e.g. stray comma in a name); skip it.
            continue
    return {
        "dish_id": fields[0],
        "calories": float(fields[1]),
        "mass_g": float(fields[2]),
        "fat_g": float(fields[3]),
        "carbs_g": float(fields[4]),
        "protein_g": float(fields[5]),
        "ingredients": ingredients,
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


def depth_raw_url(dish_id: str) -> str:
    return f"{GCS_HTTPS}/imagery/realsense_overhead/{dish_id}/depth_raw.png"


def download_depth_raw(dish_id: str, imagery_dir: Path) -> Path | None:
    """Download the aligned 16-bit RealSense depth map (mm) for a dish, when present."""
    local_dir = imagery_dir / "realsense_overhead" / dish_id
    local_depth = local_dir / "depth_raw.png"
    if local_depth.exists():
        return local_depth

    local_dir.mkdir(parents=True, exist_ok=True)
    url = depth_raw_url(dish_id)
    if gsutil_available():
        gcs_path = f"{GCS_PREFIX}/imagery/realsense_overhead/{dish_id}/depth_raw.png"
        cmd = ["gsutil", "-q", "cp", gcs_path, str(local_depth)]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode == 0 and local_depth.exists():
            return local_depth

    try:
        urllib.request.urlretrieve(url, local_depth)
    except urllib.error.HTTPError:
        return None
    return local_depth if local_depth.exists() else None


def side_angle_video_url(dish_id: str, camera: str) -> str:
    return f"{GCS_HTTPS}/imagery/side_angles/{dish_id}/camera_{camera}.h264"


def download_side_angle_video(dish_id: str, imagery_dir: Path, camera: str) -> Path | None:
    """Download one fixed-camera turntable clip (raw h264, ~30-40MB) for a dish."""
    local_dir = imagery_dir / "side_angles" / dish_id
    local_video = local_dir / f"camera_{camera}.h264"
    if local_video.exists():
        return local_video

    local_dir.mkdir(parents=True, exist_ok=True)
    url = side_angle_video_url(dish_id, camera)
    if gsutil_available():
        gcs_path = f"{GCS_PREFIX}/imagery/side_angles/{dish_id}/camera_{camera}.h264"
        cmd = ["gsutil", "-q", "cp", gcs_path, str(local_video)]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode == 0 and local_video.exists():
            return local_video

    try:
        urllib.request.urlretrieve(url, local_video)
    except urllib.error.HTTPError:
        return None
    return local_video if local_video.exists() else None


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
    with_depth: bool = False,
    with_video: str | None = None,
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

        extra: dict[str, object] = {}

        if not metadata_only and with_depth:
            depth = download_depth_raw(dish_id, imagery_dir)
            if depth is not None:
                extra["depth_path"] = str(depth.relative_to(repo_root))

        if not metadata_only and with_video:
            video = download_side_angle_video(dish_id, imagery_dir, with_video)
            if video is not None:
                extra["video_path"] = str(video.relative_to(repo_root))
                extra["video_camera"] = with_video

        # Strip per-ingredient grams/macros from the L2-facing list; keep full
        # rows under extra["ingredients_weighed"] for portion/clarify oracles.
        weighed = list(meta.get("ingredients") or [])
        name_only = [{"name": str(ing.get("name", "")).strip()} for ing in weighed if ing.get("name")]
        extra["ingredients"] = name_only
        extra["ingredients_weighed"] = weighed

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
                notes="Nutrition5k overhead RGB when available; no natural meal title (L2 only)",
                extra=extra,
            )
        )

    write_manifest(out_path, samples)
    # Nutrition5k dish IDs are not human meal titles — skip L1; L2 = ingredient names.
    if not metadata_only and samples:
        write_image_text_variants(
            samples,
            out_path.parent,
            prefix="n5k",
            write_l1=False,
            write_l2=True,
        )
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
    parser.add_argument(
        "--with-depth",
        action="store_true",
        help="Also fetch the aligned 16-bit RealSense depth_raw.png per dish",
    )
    parser.add_argument(
        "--with-video",
        nargs="?",
        const="A",
        default=None,
        metavar="CAMERA",
        help="Also fetch one side_angles turntable clip per dish (camera A-D, default A)",
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
        with_depth=args.with_depth,
        with_video=args.with_video,
    )
    print(f"Wrote {count} samples to {args.out}")


if __name__ == "__main__":
    main()
