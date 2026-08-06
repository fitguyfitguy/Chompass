#!/usr/bin/env python3
"""Generate overlay assets for the marketing usage video (compose_usage_video.sh).

Produces, under android/build/usage-video/assets/ (gitignored):

  backdrop.png   1920x1080 dark teal-tinted backdrop with vignette
  glow.png       soft teal radial blob (slow-drifting accent in the composer)
  phone-frame.png    bezel frame matching the composer's phone screen geometry
  callout-<id>.png   lower-third text strips per video segment

Run from the repo root with uv (repo convention, never bare python):

  uv run --with pillow python scripts/generate_video_overlays.py

Geometry constants must match compose_usage_video.sh (WIDTH/HEIGHT/PHONE_W).
"""

from __future__ import annotations

import glob
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ASSET_DIR = ROOT / "android" / "build" / "usage-video" / "assets"
ASSET_DIR.mkdir(parents=True, exist_ok=True)

WIDTH = 1920
HEIGHT = 1080
PHONE_W = 413
PHONE_H = 918  # 1080x2400 clip scaled to PHONE_W
BEZEL = 10
CORNER = 28

TEAL = (79, 209, 197, 255)  # site accent
BG_DARK = (17, 16, 21, 255)  # matches compose BG default 0x111015
BG_MID = (24, 23, 32, 255)
TEXT_MAIN = (255, 255, 255, 255)
TEXT_SUB = (196, 199, 208, 255)


def find_font() -> str:
    candidates = []
    for pattern in (
        "/nix/store/*-dejavu-fonts-*/share/fonts/truetype/DejaVuSans-Bold.ttf",
        "/nix/store/*-dejavu-fonts*/share/fonts/truetype/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
    ):
        candidates.extend(sorted(glob.glob(pattern)))
    env = os.environ.get("VIDEO_FONT")
    if env and Path(env).is_file():
        candidates.insert(0, env)
    if not candidates:
        print(
            "WARNING: no font found; callouts will be blank. Set VIDEO_FONT.",
            file=sys.stderr,
        )
        return ""
    return candidates[0]


def backdrop() -> None:
    img = Image.new("RGB", (WIDTH, HEIGHT), BG_DARK)
    # Radial lift behind the phone.
    glow = Image.new("L", (WIDTH, HEIGHT), 0)
    gd = ImageDraw.Draw(glow)
    gd.ellipse(
        (WIDTH / 2 - 620, HEIGHT / 2 - 620, WIDTH / 2 + 620, HEIGHT / 2 + 620), fill=70
    )
    glow = glow.filter(ImageFilter.GaussianBlur(320))
    dark = Image.new("RGB", (WIDTH, HEIGHT), (26, 24, 34, 255))
    img = Image.composite(dark, img, glow)
    # Vignette.
    vig = Image.new("L", (WIDTH, HEIGHT), 0)
    vd = ImageDraw.Draw(vig)
    vd.ellipse((-600, -400, WIDTH + 600, HEIGHT + 400), fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(420))
    dark = Image.new("RGB", (WIDTH, HEIGHT), (8, 8, 12, 255))
    img = Image.composite(img, dark, vig)
    img.save(ASSET_DIR / "backdrop.png")
    print(f"  backdrop.png {img.size}")


def glow() -> None:
    size = 900
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((0, 0, size, size), fill=TEAL[:3] + (255,))
    img = img.filter(ImageFilter.GaussianBlur(240))
    # Reduce overall alpha so it reads as a soft light sweep.
    a = img.getchannel("A").point(lambda v: int(v * 0.20))
    img.putalpha(a)
    img.save(ASSET_DIR / "glow.png")
    print(f"  glow.png {img.size}")


def phone_frame() -> None:
    fw, fh = PHONE_W + 2 * BEZEL, PHONE_H + 2 * BEZEL
    img = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, fw - 1, fh - 1), radius=CORNER, fill=(30, 29, 38, 255))
    # Inner edge highlight (top-left light, bottom-right dark) for a metallic feel.
    edge = Image.new("L", (fw, fh), 0)
    ed = ImageDraw.Draw(edge)
    ed.rounded_rectangle((0, 0, fw - 1, fh - 1), radius=CORNER, outline=120, width=2)
    edge = edge.filter(ImageFilter.GaussianBlur(1.5))
    hl = Image.new("RGBA", (fw, fh), (255, 255, 255, 0))
    hl.putalpha(edge.point(lambda v: v // 3))
    img = Image.alpha_composite(img, hl)
    # Screen cutout (transparent) with rounded corners.
    cut = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cut)
    cd.rounded_rectangle(
        (BEZEL, BEZEL, PHONE_W + BEZEL - 1, PHONE_H + BEZEL - 1),
        radius=CORNER - 4,
        fill=(0, 0, 0, 255),
    )
    img = Image.composite(img, cut, cut.split()[3])
    img.save(ASSET_DIR / "phone-frame.png")
    print(f"  phone-frame.png {img.size}")


CALLOUTS: dict[str, tuple[str, str]] = {
    "ai": ("AI fills in the macros", "Photo or text in — macros out, on device"),
    "barcode": ("Scan a barcode", "Resolves against Open Food Facts"),
    "trend": ("Watch the trend", "Weight and body fat over time"),
    "diary": ("One-tap relogging", "Saved meals back in seconds"),
}


def callouts(font_path: str) -> None:
    if not font_path:
        return
    title_font = ImageFont.truetype(font_path, 44)
    sub_font = ImageFont.truetype(font_path, 26)
    for cid, (title, sub) in CALLOUTS.items():
        # Measure with a scratch image.
        scratch = ImageDraw.Draw(Image.new("RGBA", (10, 10)))
        t_box = scratch.textbbox((0, 0), title, font=title_font)
        s_box = scratch.textbbox((0, 0), sub, font=sub_font)
        pad_x, pad_y = 40, 26
        gap = 12
        w = max(t_box[2] - t_box[0], s_box[2] - s_box[0]) + pad_x * 2 + 26
        h = (t_box[3] - t_box[1]) + gap + (s_box[3] - s_box[1]) + pad_y * 2
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((0, 0, w - 1, h - 1), radius=22, fill=(16, 15, 21, 216))
        d.rounded_rectangle((0, 0, 8, h - 1), radius=4, fill=TEAL)
        d.text((pad_x + 20, pad_y), title, font=title_font, fill=TEXT_MAIN)
        d.text(
            (pad_x + 20, pad_y + (t_box[3] - t_box[1]) + gap),
            sub,
            font=sub_font,
            fill=TEXT_SUB,
        )
        img.save(ASSET_DIR / f"callout-{cid}.png")
        print(f"  callout-{cid}.png {img.size}")


def main() -> None:
    print(f"Writing overlay assets to {ASSET_DIR.relative_to(ROOT)}")
    backdrop()
    glow()
    phone_frame()
    callouts(find_font())


if __name__ == "__main__":
    main()
