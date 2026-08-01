#!/usr/bin/env python3
"""Generate a 1200x630 Open Graph card for the Chompass project site."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
LOGO = ROOT / "website" / "static" / "img" / "logo.png"
OUT = ROOT / "website" / "static" / "img" / "og.png"

WIDTH, HEIGHT = 1200, 630
BG = (18, 17, 22)
TEAL = (77, 182, 172)
TEAL_DEEP = (0, 107, 94)
TEXT = (230, 225, 229)
MUTED = (169, 164, 173)


def load_font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    candidates = [
        "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/TTF/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/noto/NotoSans-Bold.ttf" if bold else "/usr/share/fonts/noto/NotoSans-Regular.ttf",
    ]
    for path in candidates:
        if Path(path).is_file():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


def main() -> None:
    img = Image.new("RGB", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(img)

    # Soft teal wash (top-left / bottom-right)
    overlay = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    ov = ImageDraw.Draw(overlay)
    ov.ellipse((-200, -280, 700, 420), fill=(*TEAL_DEEP, 90))
    ov.ellipse((700, 280, 1400, 900), fill=(*TEAL, 40))
    img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
    draw = ImageDraw.Draw(img)

    logo = Image.open(LOGO).convert("RGBA")
    logo = logo.resize((160, 160), Image.Resampling.LANCZOS)
    img.paste(logo, (80, 200), logo)

    title_font = load_font(96, bold=True)
    tag_font = load_font(36, bold=False)
    sub_font = load_font(28, bold=False)

    draw.text((280, 200), "Chompass", font=title_font, fill=TEXT)
    draw.text((280, 320), "Private calorie tracking", font=tag_font, fill=TEAL)
    draw.text(
        (280, 390),
        "Android and browser. Your AI key. No ads.",
        font=sub_font,
        fill=MUTED,
    )
    draw.text((80, 560), "chompass.app", font=sub_font, fill=MUTED)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, format="PNG", optimize=True)
    print(f"Wrote {OUT} ({WIDTH}x{HEIGHT})")


if __name__ == "__main__":
    main()
