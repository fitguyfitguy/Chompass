#!/usr/bin/env python3
"""Generate NoFUD launcher icons and splash logos from the master artwork."""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
MASTER = ROOT / "scripts" / "nofud_icon_master.png"
RES = ROOT / "android" / "app" / "src" / "main" / "res"

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Matches AppThemeColor in Color.kt: (suffix, start_rgb, end_rgb, ic_logo_name or None)
THEMES: list[tuple[str, tuple[int, int, int], tuple[int, int, int], str | None]] = [
    ("", (0xFF, 0x37, 0x5F), (0xFF, 0x6B, 0x8A), "ic_logo.png"),
    ("_red", (0xFF, 0x3B, 0x30), (0xFF, 0x69, 0x61), "ic_logo_red.png"),
    ("_orange", (0xFF, 0x95, 0x00), (0xFF, 0xB3, 0x40), "ic_logo_orange.png"),
    ("_green", (0x34, 0xC7, 0x59), (0x62, 0xD4, 0x6F), "ic_logo_green.png"),
    ("_mint", (0x00, 0xC7, 0xBE), (0x66, 0xD4, 0xCF), "ic_logo_mint.png"),
    ("_teal", (0x30, 0xB0, 0xC7), (0x64, 0xD2, 0xFF), "ic_logo_teal.png"),
    ("_blue", (0x0A, 0x84, 0xFF), (0x5E, 0xAE, 0xFF), "ic_logo_blue.png"),
    ("_purple", (0xAF, 0x52, 0xDE), (0xBF, 0x5A, 0xF2), "ic_logo_purple.png"),
    ("_yellow", (0xFF, 0xCC, 0x00), (0xFF, 0xD6, 0x0A), None),
    ("_coral", (0xFF, 0x7F, 0x50), (0xFF, 0xA3, 0x82), None),
    ("_rose_gold", (0xC9, 0x80, 0x7C), (0xE8, 0xB4, 0xB0), None),
    ("_mocha_brown", (0xA2, 0x84, 0x5E), (0xC9, 0xA5, 0x7E), None),
    ("_indigo", (0x58, 0x56, 0xD6), (0x7D, 0x7A, 0xFF), None),
    ("_lavender", (0xB5, 0x7E, 0xDC), (0xD0, 0xA9, 0xF5), None),
    ("_sky_cyan", (0x32, 0xAD, 0xE6), (0x70, 0xCF, 0xFF), None),
    ("_graphite", (0x8E, 0x8E, 0x93), (0xB8, 0xB8, 0xBE), None),
    ("_baby_pink", (0xFF, 0x8F, 0xAB), (0xFF, 0xB3, 0xC6), None),
    ("_lime", (0xA0, 0xD9, 0x11), (0xC3, 0xE9, 0x56), None),
]

LOGO_SIZE = 2048


def lerp(a: int, b: int, t: float) -> int:
    return int(round(a + (b - a) * t))


def make_gradient(size: int, start: tuple[int, int, int], end: tuple[int, int, int]) -> Image.Image:
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        t = y / max(size - 1, 1)
        color = (
            lerp(start[0], end[0], t),
            lerp(start[1], end[1], t),
            lerp(start[2], end[2], t),
        )
        for x in range(size):
            px[x, y] = color
    return img


def extract_masks(master: Image.Image) -> tuple[Image.Image, Image.Image]:
    """Return (rounded_square_alpha, white_logo_alpha) from the master icon."""
    rgba = master.convert("RGBA")
    w, h = rgba.size
    rounded = Image.new("L", (w, h), 0)
    logo = Image.new("L", (w, h), 0)
    pixels = rgba.load()
    rounded_px = rounded.load()
    logo_px = logo.load()

    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a < 16:
                continue
            rounded_px[x, y] = 255
            # White / near-white mark (NF monogram + check cutout uses background show-through)
            if r > 210 and g > 210 and b > 210:
                logo_px[x, y] = 255

    return rounded, logo


def compose_icon(
    size: int,
    rounded_mask: Image.Image,
    logo_mask: Image.Image,
    start: tuple[int, int, int],
    end: tuple[int, int, int],
) -> Image.Image:
    rounded = rounded_mask.resize((size, size), Image.Resampling.LANCZOS)
    logo = logo_mask.resize((size, size), Image.Resampling.LANCZOS)
    bg = make_gradient(size, start, end).convert("RGBA")
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(bg, mask=rounded)

    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer.paste(white, mask=logo)
    out = Image.alpha_composite(out, layer)
    return out


def make_round(square: Image.Image) -> Image.Image:
    size = square.size[0]
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(square, mask=mask)
    return out


def main() -> None:
    if not MASTER.exists():
        raise SystemExit(f"Master icon missing: {MASTER}")

    master = Image.open(MASTER)
    rounded_mask, logo_mask = extract_masks(master)

    drawable = RES / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)

    for suffix, start, end, logo_name in THEMES:
        icon_2048 = compose_icon(LOGO_SIZE, rounded_mask, logo_mask, start, end)
        if logo_name:
            icon_2048.save(drawable / logo_name, optimize=True)

        base = "ic_launcher" + suffix
        for folder, px in DENSITIES.items():
            out_dir = RES / folder
            out_dir.mkdir(parents=True, exist_ok=True)
            square = compose_icon(px, rounded_mask, logo_mask, start, end)
            round_icon = make_round(square)
            square.save(out_dir / f"{base}.png", optimize=True)
            round_icon.save(out_dir / f"{base}_round.png", optimize=True)

    print(f"Generated icons for {len(THEMES)} themes across {len(DENSITIES)} densities.")


if __name__ == "__main__":
    main()
