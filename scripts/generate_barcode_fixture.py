#!/usr/bin/env python3
"""Generate a scannable EAN-13 barcode PNG for the usage-video camera segment.

Displays a real product barcode (Open Food Facts lookup works over the network
from the phone). Run with uv (repo convention):

  uv run --with pillow python scripts/generate_barcode_fixture.py

Output: android/build/usage-video/barcode.png — open it full-screen on a
monitor, then point the phone's barcode scanner at it.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "android" / "build" / "usage-video" / "barcode.png"

# Nutella 400g — valid EAN-13 that resolves on Open Food Facts.
DEFAULT_EAN = "3017620422003"


def check_digit(first12: str) -> int:
    total = sum(int(d) * (1 if i % 2 == 0 else 3) for i, d in enumerate(first12))
    return (10 - total % 10) % 10


def _dejavu_font():
    import glob

    for pattern in (
        "/nix/store/*-dejavu-fonts-*/share/fonts/truetype/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        hits = sorted(glob.glob(pattern))
        if hits:
            return ImageFont.truetype(hits[0], 44)
    return None


def draw_ean13(ean: str, width: int = 1200, module: int = 12) -> Image.Image:
    assert len(ean) == 13 and ean.isdigit(), f"need a 13-digit EAN, got {ean!r}"
    assert ean[-1] == str(check_digit(ean[:12])), f"{ean} has a wrong check digit"
    # L-code (first digit = 0): seven digits, seven bits each.
    L = {
        "0": "0001101",
        "1": "0011001",
        "2": "0010011",
        "3": "0111101",
        "4": "0100011",
        "5": "0110001",
        "6": "0101111",
        "7": "0111011",
        "8": "0110111",
        "9": "0001011",
    }
    guards = "101", "01010", "101"
    bits: list[str] = []
    for i, ch in enumerate(ean[1:7]):
        bits.append(L[ch])
    for i, ch in enumerate(ean[7:]):
        bits.append(L[ch])
    patterns = [guards[0]] + bits[:6] + [guards[1]] + bits[6:] + [guards[2]]
    bars = 0
    for p in patterns:
        bars += len(p)
    img_w = bars * module
    img_h = 620
    img = Image.new("RGB", (img_w, img_h), "white")
    d = ImageDraw.Draw(img)
    x = 0
    for p in patterns:
        for bit in p:
            if bit == "1":
                d.rectangle((x, 0, x + module - 1, img_h - 130), fill="black")
            x += module
    # Digits under the bars: first digit left of the left guard, then 6 per half.
    font = _dejavu_font()
    if font is not None:
        guard = 3 * module
        half = 42 * module
        for i, ch in enumerate(ean):
            char_w = d.textlength(ch, font=font)
            if i == 0:
                cx = (guard - char_w) // 2
            elif i < 7:
                cx = guard + (i - 1) * 7 * module + (7 * module - char_w) // 2
            else:
                cx = (
                    guard
                    + half
                    + 5 * module
                    + (i - 7) * 7 * module
                    + (7 * module - char_w) // 2
                )
            d.text((cx, img_h - 120), ch, font=font, fill="black")
    return img


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--ean", default=DEFAULT_EAN, help="13-digit EAN-13 (default: %(default)s)"
    )
    ap.add_argument("--out", type=Path, default=OUT, help="output PNG path")
    args = ap.parse_args()
    if len(args.ean) != 13:
        print(f"EAN must be 13 digits, got {len(args.ean)}", file=sys.stderr)
        return 1
    img = draw_ean13(args.ean)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    img.save(args.out)
    print(f"Wrote {args.out} ({img.size[0]}x{img.size[1]}) for EAN {args.ean}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
