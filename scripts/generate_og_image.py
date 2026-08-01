#!/usr/bin/env python3
"""Generate a 1200x630 Open Graph card for the Chompass project site."""

from __future__ import annotations

import os
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


def _font_candidates(bold: bool) -> list[Path]:
    """Prefer real TTFs — Pillow's default bitmap font ignores size and is ~10px."""
    pick = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    candidates: list[Path] = []

    # WSL: Windows host fonts
    win = Path("/mnt/c/Windows/Fonts")
    if bold:
        candidates.extend(
            [
                win / "segoeuib.ttf",
                win / "arialbd.ttf",
                win / "calibrib.ttf",
            ]
        )
    else:
        candidates.extend(
            [
                win / "segoeui.ttf",
                win / "arial.ttf",
                win / "calibri.ttf",
            ]
        )

    # Common Linux packaging layouts
    for base in (
        Path("/usr/share/fonts/TTF"),
        Path("/usr/share/fonts/truetype/dejavu"),
        Path("/usr/share/fonts/dejavu"),
        Path("/usr/share/fonts/noto"),
        Path("/usr/share/fonts/truetype/noto"),
    ):
        candidates.append(base / pick)
        if bold:
            candidates.append(base / "NotoSans-Bold.ttf")
        else:
            candidates.append(base / "NotoSans-Regular.ttf")

    # Nix: dejavu_fonts from a prior nix shell / store
    nix_store = Path("/nix/store")
    if nix_store.is_dir():
        for dejavu_root in sorted(nix_store.glob("*-dejavu-fonts-*/share/fonts/truetype")):
            candidates.append(dejavu_root / pick)

    # Optional explicit override
    override = os.environ.get("CHOMPASS_OG_FONT_BOLD" if bold else "CHOMPASS_OG_FONT")
    if override:
        candidates.insert(0, Path(override))

    return candidates


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    for path in _font_candidates(bold):
        if path.is_file():
            return ImageFont.truetype(str(path), size=size)
    raise SystemExit(
        "No scalable TTF found for OG text (Pillow default bitmap is ~10px and "
        "ignores size). Install DejaVu/Noto, or on WSL ensure "
        "/mnt/c/Windows/Fonts is readable, or set CHOMPASS_OG_FONT / "
        "CHOMPASS_OG_FONT_BOLD."
    )


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

    # Keep the lockup large enough to read in Discord/Twitter thumbs.
    logo_size = 260
    logo = Image.open(LOGO).convert("RGBA")
    logo = logo.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
    logo_x, logo_y = 64, (HEIGHT - logo_size) // 2
    img.paste(logo, (logo_x, logo_y), logo)

    title_font = load_font(112, bold=True)
    tag_font = load_font(44, bold=False)
    sub_font = load_font(34, bold=False)
    url_font = load_font(30, bold=False)

    text_x = logo_x + logo_size + 44
    title = "Chompass"
    tag = "Private calorie tracking"
    sub_line = "Android and browser. Your AI key. No ads."

    # Top-left anchoring + textbbox so descenders (the "p" in Chompass) clear
    # the next line instead of colliding with "tracking".
    anchor = "lt"
    title_h = (
        draw.textbbox((0, 0), title, font=title_font, anchor=anchor)[3]
        - draw.textbbox((0, 0), title, font=title_font, anchor=anchor)[1]
    )
    tag_h = (
        draw.textbbox((0, 0), tag, font=tag_font, anchor=anchor)[3]
        - draw.textbbox((0, 0), tag, font=tag_font, anchor=anchor)[1]
    )
    sub_h = (
        draw.textbbox((0, 0), sub_line, font=sub_font, anchor=anchor)[3]
        - draw.textbbox((0, 0), sub_line, font=sub_font, anchor=anchor)[1]
    )
    gap1, gap2 = 28, 18
    block_h = title_h + gap1 + tag_h + gap2 + sub_h
    text_y = (HEIGHT - block_h) // 2

    draw.text((text_x, text_y), title, font=title_font, fill=TEXT, anchor=anchor)
    draw.text(
        (text_x, text_y + title_h + gap1),
        tag,
        font=tag_font,
        fill=TEAL,
        anchor=anchor,
    )
    draw.text(
        (text_x, text_y + title_h + gap1 + tag_h + gap2),
        sub_line,
        font=sub_font,
        fill=MUTED,
        anchor=anchor,
    )
    draw.text((64, HEIGHT - 56), "chompass.app", font=url_font, fill=MUTED, anchor=anchor)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, format="PNG", optimize=True)
    print(f"Wrote {OUT} ({WIDTH}x{HEIGHT})")
    print(f"Title glyph height: {title_h}px (font size 112), gap after title: {gap1}px")


if __name__ == "__main__":
    main()
