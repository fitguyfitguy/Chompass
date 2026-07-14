#!/usr/bin/env python3
"""Convert real food/label photos into debug Tier B JPEG fixtures.

Sources (repo root — add your own photos here):
  Chicken-and-Rice-Bowl-with-Sesame-Dressing-and-Broccoli-Featured-Image.jpg → food_plate.jpg
  authentic-phone-photo-pizza-served-260nw-2707958021.webp → pizza_slices.jpg
  label.jpg → nutrition_label.jpg
  french_fries_chicken_leg_nuggets_onion_rings_ketchup.avif → fast_food_combo.jpg

Output: android/app/src/debug/assets/ondevice_llm/*.jpg (bundled in debug APK only).

Run:
  uv run --with pillow --with pillow-heif python scripts/prepare_ondevice_llm_fixtures.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

try:
    from pillow_heif import register_heif_opener

    register_heif_opener()
except ImportError:
    pass

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "android/app/src/debug/assets/ondevice_llm"
MAX_DIMENSION = 1600
JPEG_QUALITY = 85

SOURCES: dict[str, Path] = {
    "food_plate.jpg": ROOT
    / "Chicken-and-Rice-Bowl-with-Sesame-Dressing-and-Broccoli-Featured-Image.jpg",
    "pizza_slices.jpg": ROOT / "authentic-phone-photo-pizza-served-260nw-2707958021.webp",
    "nutrition_label.jpg": ROOT / "label.jpg",
    "fast_food_combo.jpg": ROOT / "french_fries_chicken_leg_nuggets_onion_rings_ketchup.avif",
}


def convert(src: Path, dest: Path) -> None:
    img = Image.open(src).convert("RGB")
    img.thumbnail((MAX_DIMENSION, MAX_DIMENSION), Image.Resampling.LANCZOS)
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.save(dest, "JPEG", quality=JPEG_QUALITY, optimize=True)
    print(f"{dest.name}: {dest.stat().st_size} bytes from {src.name} ({img.width}x{img.height})")


def main() -> None:
    missing = [name for name, path in SOURCES.items() if not path.exists()]
    if missing:
        names = ", ".join(missing)
        raise SystemExit(
            f"Missing source file(s) in repo root for: {names}. "
            "Copy real food/label photos to the paths listed in this script's docstring."
        )
    for dest_name, src in SOURCES.items():
        convert(src, OUT_DIR / dest_name)
    print(f"Done — {len(SOURCES)} fixtures in {OUT_DIR}")


if __name__ == "__main__":
    main()
