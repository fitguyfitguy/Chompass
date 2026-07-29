#!/usr/bin/env python3
"""Build NutritionVerse-Real L0/L1/L2 manifests from a local (or Kaggle) extract.

NutritionVerse-Real is on Kaggle (``nutritionverse/nutritionverse-real``),
license **CC BY-NC-SA 4.0** — research / non-commercial eval only.

This script does **not** scrape Kaggle anonymously (auth required). Provide data via:

1. ``kaggle datasets download -d nutritionverse/nutritionverse-real -p <dir> --unzip``
   then ``--data-dir <dir>``, or
2. Manual download/unzip to ``--data-dir``.

Expected layout (as published on Kaggle)::

    <data-dir>/
      nutritionverse_dish_metadata3.csv
      nutritionverse-manual/nutritionverse-manual/
        updated-manual-dataset-splits.csv   # optional
        images/
          _annotations.coco.json
          dish_<id>_IMG_*.jpg

L1 = synthesized short title from ingredient/food-type names (no natural dish
titles in the release). L2 = the same names as an unquantified ingredient list
(identical string; L1 is kept for harness parity with JFB). Prefer interpreting
NV results as L0 vs L2.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from image_text_variants import ingredient_names_text, write_image_text_variants
from schema import DATA_DIR, Sample, write_manifest

KAGGLE_DATASET = "nutritionverse/nutritionverse-real"
DISH_ID_RE = re.compile(r"dish_(\d+)_", re.IGNORECASE)

# Flexible column aliases for nutritionverse_dish_metadata3.csv
CAL_KEYS = ("calories", "calorie", "kcal", "energy_kcal", "energy", "total_calories")
MASS_KEYS = ("mass", "mass_g", "weight", "weight_g", "total_mass", "grams")
PROT_KEYS = ("protein", "protein_g", "total_protein")
CARB_KEYS = ("carbohydrate", "carbohydrates", "carb", "carbs", "carbs_g", "total_carb")
FAT_KEYS = ("fat", "fat_g", "total_fat", "fats")
ID_KEYS = ("dish_id", "dish", "id", "scene_id", "image_id")


def _pick(row: dict[str, str], keys: tuple[str, ...]) -> str | None:
    lower = {k.casefold(): (k, v) for k, v in row.items()}
    for key in keys:
        hit = lower.get(key.casefold())
        if hit and str(hit[1]).strip() != "":
            return str(hit[1]).strip()
    return None


def _float(val: str | None, default: float = 0.0) -> float:
    if val is None or val == "":
        return default
    return float(val)


def find_data_root(data_dir: Path) -> Path:
    """Return directory that contains dish metadata CSV and/or images tree."""
    candidates = [
        data_dir,
        data_dir / "nutritionverse-manual",
        data_dir / "nutritionverse-manual" / "nutritionverse-manual",
    ]
    for cand in list(candidates):
        if (cand / "nutritionverse_dish_metadata3.csv").exists():
            return cand
    # Search one level deeper.
    for path in data_dir.rglob("nutritionverse_dish_metadata3.csv"):
        return path.parent
    for path in data_dir.rglob("_annotations.coco.json"):
        return path.parent.parent
    return data_dir


def maybe_kaggle_download(data_dir: Path) -> None:
    if shutil.which("kaggle") is None:
        return
    marker = list(data_dir.rglob("nutritionverse_dish_metadata3.csv"))
    if marker:
        return
    data_dir.mkdir(parents=True, exist_ok=True)
    print(f"Attempting: kaggle datasets download -d {KAGGLE_DATASET} ...")
    cmd = [
        "kaggle",
        "datasets",
        "download",
        "-d",
        KAGGLE_DATASET,
        "-p",
        str(data_dir),
        "--unzip",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr, file=sys.stderr)
        print(
            "WARN: kaggle download failed; place an unzipped extract under --data-dir",
            file=sys.stderr,
        )


def load_dish_metadata(csv_path: Path) -> dict[str, dict[str, float | str | list]]:
    dishes: dict[str, dict[str, float | str | list]] = {}
    with csv_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames:
            raise RuntimeError(f"Empty CSV: {csv_path}")
        print(f"Dish metadata columns: {list(reader.fieldnames)[:12]}...")
        for row in reader:
            dish_id = _pick(row, ID_KEYS)
            if dish_id is None:
                # Sometimes the first column is unnamed index + dish in filename col.
                continue
            dish_id = re.sub(r"^dish_", "", str(dish_id), flags=re.I)
            # Ingredient-like columns: any column not a known macro/id whose values
            # look like food names, or explicit ingredient_* / food_* lists.
            ingredients: list[dict[str, str]] = []
            for col, val in row.items():
                if not val or not str(val).strip():
                    continue
                col_l = col.casefold()
                if col_l in {k.casefold() for k in ID_KEYS + CAL_KEYS + MASS_KEYS + PROT_KEYS + CARB_KEYS + FAT_KEYS}:
                    continue
                if col_l.startswith(("ingr", "food", "item", "label", "class", "category")):
                    # May be a single name or a python/JSON list string.
                    text = str(val).strip()
                    if text.startswith("["):
                        try:
                            parsed = json.loads(text.replace("'", '"'))
                            if isinstance(parsed, list):
                                for name in parsed:
                                    name_s = str(name).strip()
                                    if name_s:
                                        ingredients.append({"name": name_s})
                                continue
                        except json.JSONDecodeError:
                            pass
                    ingredients.append({"name": text})
            dishes[str(dish_id)] = {
                "calories": _float(_pick(row, CAL_KEYS)),
                "mass_g": _float(_pick(row, MASS_KEYS)),
                "protein_g": _float(_pick(row, PROT_KEYS)),
                "carbs_g": _float(_pick(row, CARB_KEYS)),
                "fat_g": _float(_pick(row, FAT_KEYS)),
                "ingredients": ingredients,
                "raw": {k: v for k, v in row.items() if v},
            }
    return dishes


def load_coco_ingredients(coco_path: Path) -> dict[str, list[dict[str, str]]]:
    """Map image file name -> unique category names present in annotations."""
    data = json.loads(coco_path.read_text(encoding="utf-8"))
    cats = {c["id"]: c["name"] for c in data.get("categories", [])}
    img_by_id = {im["id"]: im for im in data.get("images", [])}
    names_by_file: dict[str, list[str]] = defaultdict(list)
    for ann in data.get("annotations", []):
        img = img_by_id.get(ann.get("image_id"))
        if not img:
            continue
        cat = cats.get(ann.get("category_id"))
        if not cat:
            continue
        file_name = img.get("file_name") or ""
        names_by_file[file_name].append(cat)
        # Also key by basename in case paths differ.
        names_by_file[Path(file_name).name].append(cat)

    out: dict[str, list[dict[str, str]]] = {}
    for file_name, names in names_by_file.items():
        seen: set[str] = set()
        unique: list[dict[str, str]] = []
        for name in names:
            key = name.casefold()
            if key in seen:
                continue
            seen.add(key)
            unique.append({"name": name})
        out[file_name] = unique
    return out


def iter_dish_images(images_dir: Path) -> dict[str, list[Path]]:
    """dish_id -> image paths (multi-angle)."""
    by_dish: dict[str, list[Path]] = defaultdict(list)
    for path in sorted(images_dir.rglob("*")):
        if path.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
            continue
        match = DISH_ID_RE.search(path.name)
        if not match:
            continue
        by_dish[match.group(1)].append(path)
    return by_dish


def build_manifest(
    data_dir: Path,
    out_path: Path,
    *,
    limit: int | None,
    one_image_per_dish: bool,
) -> list[Sample]:
    root = find_data_root(data_dir)
    meta_path = root / "nutritionverse_dish_metadata3.csv"
    if not meta_path.exists():
        # Sometimes CSV sits at data_dir root while images are nested.
        alt = list(data_dir.rglob("nutritionverse_dish_metadata3.csv"))
        if not alt:
            raise FileNotFoundError(
                f"Missing nutritionverse_dish_metadata3.csv under {data_dir}. "
                f"Download from Kaggle ({KAGGLE_DATASET}) and pass --data-dir."
            )
        meta_path = alt[0]
        root = meta_path.parent

    dishes = load_dish_metadata(meta_path)
    print(f"Loaded {len(dishes)} dish metadata rows")

    coco_paths = list(data_dir.rglob("_annotations.coco.json"))
    coco_ings: dict[str, list[dict[str, str]]] = {}
    if coco_paths:
        coco_ings = load_coco_ingredients(coco_paths[0])
        print(f"Loaded COCO categories for {len(coco_ings)} images from {coco_paths[0]}")

    images_dirs = list(data_dir.rglob("images"))
    images_dirs = [p for p in images_dirs if p.is_dir()]
    if not images_dirs:
        raise FileNotFoundError(f"No images/ directory under {data_dir}")
    images_dir = max(images_dirs, key=lambda p: sum(1 for _ in p.glob("*.jpg")))
    by_dish = iter_dish_images(images_dir)
    print(f"Found images for {len(by_dish)} dishes under {images_dir}")

    repo_root = _HERE.parents[1]
    samples: list[Sample] = []
    for dish_id in sorted(by_dish, key=lambda x: int(x) if x.isdigit() else x):
        if limit is not None and len(samples) >= limit:
            break
        meta = dishes.get(dish_id) or dishes.get(f"dish_{dish_id}")
        if meta is None:
            # Still allow COCO-only ingredient text if macros missing? Skip — need GT.
            continue
        paths = by_dish[dish_id]
        if one_image_per_dish:
            paths = paths[:1]
        for img_path in paths:
            if limit is not None and len(samples) >= limit:
                break
            ings = list(meta.get("ingredients") or [])
            if not ings:
                ings = coco_ings.get(img_path.name) or coco_ings.get(str(img_path)) or []
            title = ingredient_names_text(ings)
            try:
                rel = str(img_path.resolve().relative_to(repo_root))
            except ValueError:
                rel = str(img_path)
            samples.append(
                Sample(
                    id=f"nvreal-{dish_id}-{img_path.stem}",
                    modality="image",
                    source="nutritionverse_real",
                    image_path=rel,
                    meal_name=title or f"dish_{dish_id}",
                    calories=float(meta["calories"]),
                    protein_g=float(meta["protein_g"]),
                    carbs_g=float(meta["carbs_g"]),
                    fat_g=float(meta["fat_g"]),
                    mass_g=float(meta["mass_g"]) if meta.get("mass_g") else None,
                    notes=(
                        "NutritionVerse-Real phone multi-angle; weighed → Canada "
                        "Nutrient File; CC BY-NC-SA; L1 title synthesized from food types"
                    ),
                    extra={
                        "ingredients": ings,
                        "dish_id": dish_id,
                        "license": "CC BY-NC-SA 4.0",
                    },
                )
            )

    write_manifest(out_path, samples)
    if samples:
        # L1 ≈ synthesized title from food types; L2 = same names as ingredient list.
        write_image_text_variants(samples, out_path.parent, prefix="nvreal")
    return samples


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build NutritionVerse-Real L0/L1/L2 manifests from a local extract"
    )
    parser.add_argument("--limit", type=int, default=50, help="Max images in manifest (default 50)")
    parser.add_argument(
        "--all-angles",
        action="store_true",
        help="Include every camera angle (default: one image per dish)",
    )
    parser.add_argument(
        "--try-kaggle",
        action="store_true",
        help="If metadata missing, run `kaggle datasets download` (needs ~/.kaggle)",
    )
    parser.add_argument(
        "--out",
        default=str(DATA_DIR / "manifests" / "nvreal.jsonl"),
        help="L0 output manifest path",
    )
    parser.add_argument(
        "--data-dir",
        default=str(DATA_DIR / "nutritionverse_real"),
        help="Directory with unzipped Kaggle dataset",
    )
    args = parser.parse_args()

    data_dir = Path(args.data_dir)
    if args.try_kaggle:
        maybe_kaggle_download(data_dir)
    if not data_dir.exists():
        raise SystemExit(
            f"Data dir not found: {data_dir}\n"
            f"Download https://www.kaggle.com/datasets/{KAGGLE_DATASET} "
            f"and unzip into that path (or pass --try-kaggle with Kaggle API creds)."
        )

    samples = build_manifest(
        data_dir,
        Path(args.out),
        limit=args.limit,
        one_image_per_dish=not args.all_angles,
    )
    print(f"Wrote {len(samples)} samples to {args.out}")
    print("License: CC BY-NC-SA 4.0 — research only.")


if __name__ == "__main__":
    main()
