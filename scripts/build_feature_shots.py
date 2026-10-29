#!/usr/bin/env python3
"""Compose README feature shots from the full device captures.

Input:  docs/screenshots/*.png   (native captures from capture_device_screenshots.sh,
        status bar already cropped)
Output: docs/screenshots/features/*.png  (transparent-background framed panels the
        README embeds; the full captures stay the source of truth for the F-Droid
        store listing and the chompass.app gallery)

Design: every panel is a *feature*, not a screen dump. A panel crops the capture
to the region that tells the story, rounds the panel corners, adds a device bezel
and a soft shadow. hero.png is a three-phone collage (rotate + overlap).

Usage:
  uv run --with pillow python scripts/build_feature_shots.py
  uv run --with pillow python scripts/build_feature_shots.py hero only
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "docs" / "screenshots"
OUT = SRC / "features"

# Framed phone look, fractions of the crop so panel resolution never matters.
BEZEL = 14            # px at 1080w, scaled with the crop
RADIUS_FRAC = 0.055   # corner radius as a fraction of crop width
SHADOW_BLUR = 34
SHADOW_ALPHA = 110
SHADOW_OFFSET = 16
EDGE_HIGHLIGHT = (255, 255, 255, 26)

# Feature panels: name -> (source file, crop fractions l,t,r,b).
# Crop fractions are of the FULL capture; keep them in [0, 1].
PANELS = {
    "meal-components": ("meal-components.png", (0.0, 0.02, 1.0, 0.84)),
    "ai-analysis": ("ai-analysis.png", (0.0, 0.04, 1.0, 0.67)),
    "recipes": ("recipes.png", (0.0, 0.0, 1.0, 0.68)),
    "coach": ("coach.png", (0.0, 0.0, 1.0, 0.79)),
    "settings": ("settings.png", (0.0, 0.0, 1.0, 0.53)),
    "progress-plots": ("progress-plots.png", (0.0, 0.06, 1.0, 0.80)),
    "scale-metrics": ("scale-metrics.png", (0.0, 0.0, 1.0, 0.86)),
}

# Hero collage: three phones telling the core story. Left/right lean outward.
HERO = [
    ("add-food.png", (0.0, 0.0, 1.0, 0.55), -7.0, 0.82),
    ("home.png", (0.0, 0.0, 1.0, 0.43), 0.0, 1.0),
    ("progress.png", (0.0, 0.0, 1.0, 0.69), 7.0, 0.82),
]
HERO_GAP = -40  # px overlap between phones at base height 1300


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255
    )
    return mask


def framed_phone(img: Image.Image, out_h: int, rot: float = 0.0) -> Image.Image:
    """Crop already applied; wrap in bezel + shadow, rotate, return RGBA."""
    scale = (out_h - 2 * BEZEL) / img.height
    crop = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    radius = round(crop.width * RADIUS_FRAC)

    body_w, body_h = crop.width + 2 * BEZEL, crop.height + 2 * BEZEL
    body = Image.new("RGBA", (body_w, body_h), (0, 0, 0, 0))
    frame = Image.new("RGBA", (body_w, body_h), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle(
        (0, 0, body_w - 1, body_h - 1), radius=radius + BEZEL, fill=(16, 18, 22, 255)
    )
    body.alpha_composite(frame)
    body.paste(crop, (BEZEL, BEZEL), rounded_mask(crop.size, radius))
    # Faint inner edge so the panel separates from dark page backgrounds too.
    ImageDraw.Draw(body).rounded_rectangle(
        (BEZEL, BEZEL, body_w - BEZEL - 1, body_h - BEZEL - 1),
        radius=radius,
        outline=EDGE_HIGHLIGHT,
        width=2,
    )

    # Soft drop shadow behind the body.
    margin = SHADOW_BLUR * 3
    shadow = Image.new("RGBA", (body_w + 2 * margin, body_h + 2 * margin), (0, 0, 0, 0))
    sh_mask = rounded_mask((body_w, body_h), radius + BEZEL)
    black = Image.new("RGBA", sh_mask.size, (0, 0, 0, SHADOW_ALPHA))
    shadow.paste(black, (margin, margin + SHADOW_OFFSET), sh_mask)
    shadow = shadow.filter(ImageFilter.GaussianBlur(SHADOW_BLUR))
    shadow.alpha_composite(body, (margin, margin))

    if rot:
        shadow = shadow.rotate(rot, resample=Image.BICUBIC, expand=True)
    return shadow


def place(canvas: Image.Image, phone: Image.Image, cx: int, bottom: int) -> None:
    canvas.alpha_composite(phone, (cx - phone.width // 2, bottom - phone.height))


def build_hero() -> None:
    base_h = 1300
    phones = []
    for src, (l, t, r, b), rot, h_frac in HERO:
        img = Image.open(SRC / src).convert("RGBA")
        w, h = img.size
        crop = img.crop((round(l * w), round(t * h), round(r * w), round(b * h)))
        phones.append(framed_phone(crop, round(base_h * h_frac), rot=rot))

    total_w = sum(p.width for p in phones) + 2 * HERO_GAP
    max_h = max(p.height for p in phones)
    canvas = Image.new("RGBA", (total_w, max_h), (0, 0, 0, 0))
    place(canvas, phones[0], phones[0].width // 2, max_h)
    place(canvas, phones[2], total_w - phones[2].width // 2, max_h)
    place(canvas, phones[1], total_w // 2, max_h)
    canvas = canvas.crop(canvas.getbbox())
    canvas.save(OUT / "hero.png")
    print(f"    features/hero.png {canvas.width}x{canvas.height}")


def build_panel(name: str) -> None:
    src, (l, t, r, b) = PANELS[name]
    img = Image.open(SRC / src).convert("RGBA")
    w, h = img.size
    crop = img.crop((round(l * w), round(t * h), round(r * w), round(b * h)))
    out_h = 1240 if crop.height / crop.width > 1.4 else 1100
    phone = framed_phone(crop, out_h)
    phone = phone.crop(phone.getbbox())
    phone.save(OUT / f"{name}.png")
    print(f"    features/{name}.png {phone.width}x{phone.height}")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    only = set(sys.argv[1:])
    if "hero" in only or not only:
        build_hero()
    for name in PANELS:
        if only and name not in only:
            continue
        if not (SRC / PANELS[name][0]).exists():
            print(f"    skip {name}: {PANELS[name][0]} not captured yet")
            continue
        build_panel(name)


if __name__ == "__main__":
    main()
