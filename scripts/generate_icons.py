#!/usr/bin/env python3
"""Generate Chompass launcher, PWA, and splash logos from the SVG brand mark."""

from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
MASTER_SVG = ROOT / "scripts" / "assets" / "chompass_icon_mark.svg"
# Generated preview (teal squircle) — not an editable source.
MASTER_PREVIEW = ROOT / "scripts" / "chompass_icon_master.png"
RES = ROOT / "android" / "app" / "src" / "main" / "res"
PWA_ICONS = ROOT / "web" / "app" / "icons"
METADATA_ICON = ROOT / "metadata" / "en-US" / "images" / "icon.png"

# Rasterize the SVG well above the largest consumer size, then LANCZOS-down.
RASTER_PX = 2048
# Squircle corner radius as a fraction of canvas (matches prior master look).
SQUIRCLE_CORNER_RATIO = 0.215

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Adaptive icon layers are 108dp; xxxhdpi (4x) → 432px. Safe zone is the
# inner ~66% so OEM masks (circle / squircle / teardrop / …) keep the mark.
ADAPTIVE_PX = 432
SAFE_ZONE_RATIO = 0.66

# Matches AppThemeColor in Color.kt: (suffix, start_rgb, end_rgb, ic_logo_name or None)
# Unsuffixed theme is the default brand (teal), same as AppThemeColor.TEAL / PWA / website.
THEMES: list[tuple[str, tuple[int, int, int], tuple[int, int, int], str | None]] = [
    ("", (0x00, 0x6B, 0x5E), (0x5C, 0xC4, 0x8F), "ic_logo.png"),
    ("_red", (0xFF, 0x3B, 0x30), (0xFF, 0x69, 0x61), "ic_logo_red.png"),
    ("_orange", (0xFF, 0x95, 0x00), (0xFF, 0xB3, 0x40), "ic_logo_orange.png"),
    ("_green", (0x34, 0xC7, 0x59), (0x62, 0xD4, 0x6F), "ic_logo_green.png"),
    ("_mint", (0x00, 0xC7, 0xBE), (0x66, 0xD4, 0xCF), "ic_logo_mint.png"),
    ("_teal", (0x00, 0x6B, 0x5E), (0x5C, 0xC4, 0x8F), "ic_logo_teal.png"),
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

LOGO_SIZE = 512


def lerp(a: int, b: int, t: float) -> int:
    return int(round(a + (b - a) * t))


def rgb_hex(color: tuple[int, int, int]) -> str:
    return f"#{color[0]:02X}{color[1]:02X}{color[2]:02X}"


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


def _resvg_command() -> list[str]:
    """Prefer a PATH resvg; otherwise invoke via nix shell."""
    found = shutil.which("resvg")
    if found:
        return [found]
    if shutil.which("nix"):
        return ["nix", "shell", "nixpkgs#resvg", "-c", "resvg"]
    raise SystemExit(
        "resvg not found. Install resvg, or ensure `nix` can run "
        "`nix shell nixpkgs#resvg -c resvg`."
    )


def rasterize_svg(svg_path: Path, size: int) -> Image.Image:
    """Render the brand-mark SVG to an RGBA PIL image at `size` px."""
    if not svg_path.is_file():
        raise SystemExit(f"Master SVG missing: {svg_path}")

    with tempfile.TemporaryDirectory(prefix="chompass-icon-") as tmp:
        out = Path(tmp) / "mark.png"
        cmd = [
            *_resvg_command(),
            "-w",
            str(size),
            "-h",
            str(size),
            str(svg_path),
            str(out),
        ]
        try:
            subprocess.run(cmd, check=True, capture_output=True, text=True)
        except subprocess.CalledProcessError as exc:
            detail = (exc.stderr or exc.stdout or "").strip()
            raise SystemExit(f"resvg failed ({exc.returncode}): {detail}") from exc
        return Image.open(out).convert("RGBA").copy()


def logo_mask_from_raster(raster: Image.Image) -> Image.Image:
    """Preserve resvg antialiased alpha (do not binary-threshold — that jagged every size)."""
    alpha = raster.getchannel("A")
    # Drop near-zero noise only; keep soft edge coverage for LANCZOS downscales.
    return alpha.point(lambda v: 0 if v < 8 else v)


def make_squircle_mask(size: int, corner_ratio: float = SQUIRCLE_CORNER_RATIO) -> Image.Image:
    """Programmatic rounded-rect mask with supersampled soft edges."""
    scale = 4
    big = size * scale
    mask = Image.new("L", (big, big), 0)
    radius = max(1, int(round(big * corner_ratio)))
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=radius, fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


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


def compose_round_icon(
    size: int,
    logo_mask: Image.Image,
    start: tuple[int, int, int],
    end: tuple[int, int, int],
) -> Image.Image:
    """True circle: full gradient disk + logo (no squircle intersection)."""
    circle = Image.new("L", (size, size), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, size - 1, size - 1), fill=255)

    bg = make_gradient(size, start, end).convert("RGBA")
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(bg, mask=circle)

    logo = logo_mask.resize((size, size), Image.Resampling.LANCZOS)
    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer.paste(white, mask=logo)
    out = Image.alpha_composite(out, layer)

    final = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    final.paste(out, mask=circle)
    return final


def compose_maskable(
    size: int,
    rounded_mask: Image.Image,
    logo_mask: Image.Image,
    icon_start: tuple[int, int, int],
    icon_end: tuple[int, int, int],
    bleed: tuple[int, int, int],
    content_ratio: float = 0.70,
) -> Image.Image:
    """Full-bleed maskable icon: solid bleed + inset squircle (safe zone)."""
    out = Image.new("RGBA", (size, size), (*bleed, 255))
    inset = compose_icon(size, rounded_mask, logo_mask, icon_start, icon_end)
    content = max(1, int(round(size * content_ratio)))
    scaled = inset.resize((content, content), Image.Resampling.LANCZOS)
    offset = (size - content) // 2
    out.alpha_composite(scaled, (offset, offset))
    return out


def compose_adaptive_foreground(
    size: int,
    logo_mask: Image.Image,
    content_ratio: float = SAFE_ZONE_RATIO,
) -> Image.Image:
    """White logo on transparent canvas, inset to the adaptive safe zone."""
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    content = max(1, int(round(size * content_ratio)))
    logo = logo_mask.resize((content, content), Image.Resampling.LANCZOS)
    white = Image.new("RGBA", (content, content), (255, 255, 255, 255))
    layer = Image.new("RGBA", (content, content), (0, 0, 0, 0))
    layer.paste(white, mask=logo)
    offset = (size - content) // 2
    out.alpha_composite(layer, (offset, offset))
    return out


def background_drawable_name(suffix: str) -> str:
    if not suffix:
        return "ic_launcher_background"
    return f"ic_launcher_background{suffix}"


def write_gradient_background_xml(
    path: Path,
    start: tuple[int, int, int],
    end: tuple[int, int, int],
) -> None:
    # angle 270 = top → bottom, matching make_gradient().
    path.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<shape xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:shape="rectangle">\n'
        "    <gradient\n"
        '        android:type="linear"\n'
        '        android:angle="270"\n'
        f'        android:startColor="{rgb_hex(start)}"\n'
        f'        android:endColor="{rgb_hex(end)}" />\n'
        "</shape>\n",
        encoding="utf-8",
    )


def write_adaptive_icon_xml(path: Path, background_name: str) -> None:
    path.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="@drawable/{background_name}" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n",
        encoding="utf-8",
    )


def write_adaptive_icons(logo_mask: Image.Image) -> None:
    """Emit adaptive layers + anydpi-v26 XML so the system icon mask applies."""
    drawable = RES / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    nodpi = RES / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)

    foreground = compose_adaptive_foreground(ADAPTIVE_PX, logo_mask)
    foreground.save(nodpi / "ic_launcher_foreground.png", optimize=True)
    # Same silhouette; Android themed icons tint via the alpha channel.
    foreground.save(nodpi / "ic_launcher_monochrome.png", optimize=True)

    for suffix, start, end, _logo_name in THEMES:
        bg_name = background_drawable_name(suffix)
        write_gradient_background_xml(drawable / f"{bg_name}.xml", start, end)
        base = "ic_launcher" + suffix
        write_adaptive_icon_xml(anydpi / f"{base}.xml", bg_name)


def write_pwa_icons(rounded_mask: Image.Image, logo_mask: Image.Image) -> None:
    """Write PWA / store icons with transparent corners (no white canvas padding).

    Default brand matches Android teal (#006B5E → #5CC48F), same as AppThemeColor.TEAL
    and the website / F-Droid listing icons.
    """
    PWA_ICONS.mkdir(parents=True, exist_ok=True)
    teal = next(t for t in THEMES if t[0] == "_teal")
    teal_start, teal_end = teal[1], teal[2]
    bleed = teal_start  # matches manifest theme_color

    for name, px in (
        ("icon-192.png", 192),
        ("icon-512.png", 512),
        ("apple-touch-icon.png", 180),
    ):
        compose_icon(px, rounded_mask, logo_mask, teal_start, teal_end).save(
            PWA_ICONS / name, optimize=True
        )

    compose_maskable(
        512,
        rounded_mask,
        logo_mask,
        teal_start,
        teal_end,
        bleed,
    ).save(PWA_ICONS / "icon-maskable-512.png", optimize=True)

    # F-Droid / store listing: teal brand squircle, transparent corners.
    METADATA_ICON.parent.mkdir(parents=True, exist_ok=True)
    compose_icon(512, rounded_mask, logo_mask, teal_start, teal_end).save(
        METADATA_ICON, optimize=True
    )

    # Hugo / Codeberg Pages favicon + header logo (same teal brand mark).
    website_logo = ROOT / "website" / "static" / "img" / "logo.png"
    website_logo.parent.mkdir(parents=True, exist_ok=True)
    compose_icon(512, rounded_mask, logo_mask, teal_start, teal_end).save(
        website_logo, optimize=True
    )


def main() -> None:
    raster = rasterize_svg(MASTER_SVG, RASTER_PX)
    logo_mask = logo_mask_from_raster(raster)
    rounded_mask = make_squircle_mask(RASTER_PX)

    teal = next(t for t in THEMES if t[0] == "_teal")
    # Generated preview for quick eyeballing; edit the SVG, not this PNG.
    compose_icon(1024, rounded_mask, logo_mask, teal[1], teal[2]).save(
        MASTER_PREVIEW, optimize=True
    )

    drawable = RES / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)

    for suffix, start, end, logo_name in THEMES:
        icon_512 = compose_icon(LOGO_SIZE, rounded_mask, logo_mask, start, end)
        if logo_name:
            icon_512.save(drawable / logo_name, optimize=True)

        base = "ic_launcher" + suffix
        for folder, px in DENSITIES.items():
            out_dir = RES / folder
            out_dir.mkdir(parents=True, exist_ok=True)
            # Legacy density PNGs (pre-masked) remain as fallbacks; API 26+
            # prefers mipmap-anydpi-v26 adaptive XML written below.
            square = compose_icon(px, rounded_mask, logo_mask, start, end)
            round_icon = compose_round_icon(px, logo_mask, start, end)
            square.save(out_dir / f"{base}.png", optimize=True)
            round_icon.save(out_dir / f"{base}_round.png", optimize=True)

    write_adaptive_icons(logo_mask)
    write_pwa_icons(rounded_mask, logo_mask)
    print(
        f"Generated icons from {MASTER_SVG.relative_to(ROOT)} "
        f"({RASTER_PX}px raster) for {len(THEMES)} themes across "
        f"{len(DENSITIES)} densities + adaptive anydpi-v26 + PWA icons in "
        f"{PWA_ICONS.relative_to(ROOT)}."
    )


if __name__ == "__main__":
    main()
