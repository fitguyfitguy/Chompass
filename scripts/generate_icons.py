#!/usr/bin/env python3
"""Generate Chompass launcher, PWA, splash, and site icons from the mark SVGs.

The mark SVGs are emitted by scripts/assets/build_icon_mark.py; edit geometry
there (or in docs/brand/logo-system.html), never here.

Two icon languages come out of this script:

* **brand** — cream tile (`#FFF6EC`) with the full-colour Ripened mark. Every
  brand accent (Plum default, Peach) shares this icon, so changing the default
  accent never requires regenerating assets.
* **themed** — a gradient tile with the white mask silhouette, one per fixed
  accent that still offers a launcher icon (`AppThemeColor` in Color.kt).
"""

from __future__ import annotations

import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "scripts" / "assets"
MARK_SVG = ASSETS / "chompass_icon_mark.svg"
MARK_NOIR_SVG = ASSETS / "chompass_icon_mark_noir.svg"
MARK_MASK_SVG = ASSETS / "chompass_icon_mark_mask.svg"
RING_SVG = ASSETS / "chompass_icon_mark_ring.svg"
# Generated preview (brand tile) — not an editable source.
MASTER_PREVIEW = ROOT / "scripts" / "chompass_icon_master.png"
RES = ROOT / "android" / "app" / "src" / "main" / "res"
PWA_ICONS = ROOT / "web" / "app" / "icons"
WEBSITE_IMG = ROOT / "website" / "static" / "img"
WEBSITE_STATIC = ROOT / "website" / "static"
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

# Brand tile: cream background, mark box as a fraction of the canvas.
CREAM = (0xFF, 0xF6, 0xEC)
# Tab-icon ring: Ripened t=0.68 (see build_icon_mark.py FAVICON_RING note).
RING_TONE = (0xE0, 0x68, 0x5E)
BRAND_BOX_RATIO = 0.76
MASKABLE_CONTENT_RATIO = 0.70
SPLASH_BOX_RATIO = 0.88

LOGO_SIZE = 512


@dataclass(frozen=True)
class ThemeSpec:
    """One launcher-icon variant. `brand` uses the cream tile, not the gradient."""

    suffix: str
    start: tuple[int, int, int]
    end: tuple[int, int, int]
    brand: bool = False


# Matches AppThemeColor in Color.kt. The brand entry is the shared cream tile;
# the rest are the fixed accents that still offer a colored launcher icon.
THEMES: list[ThemeSpec] = [
    ThemeSpec("_brand", CREAM, CREAM, brand=True),
    ThemeSpec("_teal", (0x00, 0x6B, 0x5E), (0x5C, 0xC4, 0x8F)),
    ThemeSpec("_blue", (0x0A, 0x84, 0xFF), (0x5E, 0xAE, 0xFF)),
    ThemeSpec("_green", (0x34, 0xC7, 0x59), (0x62, 0xD4, 0x6F)),
    ThemeSpec("_purple", (0xAF, 0x52, 0xDE), (0xBF, 0x5A, 0xF2)),
    ThemeSpec("_baby_pink", (0xFF, 0x8F, 0xAB), (0xFF, 0xB3, 0xC6)),
    ThemeSpec("_orange", (0xFF, 0x95, 0x00), (0xFF, 0xB3, 0x40)),
    ThemeSpec("_indigo", (0x58, 0x56, 0xD6), (0x7D, 0x7A, 0xFF)),
    ThemeSpec("_graphite", (0x8E, 0x8E, 0x93), (0xB8, 0xB8, 0xBE)),
]


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
    """Render a mark SVG to an RGBA PIL image at `size` px."""
    if not svg_path.is_file():
        raise SystemExit(f"Mark SVG missing: {svg_path}")

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


def make_circle_mask(size: int) -> Image.Image:
    """True-circle mask with the same supersampled edge treatment as the squircle."""
    scale = 4
    big = size * scale
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def compose_mark_image(size: int, color_raster: Image.Image, box_ratio: float) -> Image.Image:
    """Transparent canvas with the full-colour mark centred at `box_ratio`."""
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    content = max(1, int(round(size * box_ratio)))
    mark = color_raster.resize((content, content), Image.Resampling.LANCZOS)
    offset = (size - content) // 2
    out.alpha_composite(mark, (offset, offset))
    return out


def compose_brand_icon(
    size: int,
    mask: Image.Image,
    color_raster: Image.Image,
    box_ratio: float = BRAND_BOX_RATIO,
) -> Image.Image:
    """Cream tile + full-colour mark, intersected with the launcher mask."""
    tile = compose_mark_image(size, color_raster, box_ratio)
    base = Image.new("RGBA", (size, size), (*CREAM, 255))
    base.alpha_composite(tile)

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, mask=mask.resize((size, size), Image.Resampling.LANCZOS))
    return out


def compose_ring_icon(
    size: int,
    rounded_mask: Image.Image,
    ring_mask: Image.Image,
    box_ratio: float = BRAND_BOX_RATIO,
) -> Image.Image:
    """Cream tile + ring only: the needle/hub are mush below ~32px, so the tab
    icon drops them and carries the same single RING_TONE as favicon.svg."""
    base = Image.new("RGBA", (size, size), (*CREAM, 255))
    ring_px = round(size * box_ratio)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer.paste(
        Image.new("RGBA", (ring_px, ring_px), (*RING_TONE, 255)),
        mask=ring_mask.resize((ring_px, ring_px), Image.Resampling.LANCZOS),
    )
    base.alpha_composite(layer, ((size - ring_px) // 2, (size - ring_px) // 2))

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, mask=rounded_mask.resize((size, size), Image.Resampling.LANCZOS))
    return out


def compose_themed_icon(
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
    return Image.alpha_composite(out, layer)


def compose_themed_round_icon(
    size: int,
    logo_mask: Image.Image,
    start: tuple[int, int, int],
    end: tuple[int, int, int],
) -> Image.Image:
    """True circle: full gradient disk + logo (no squircle intersection)."""
    circle = make_circle_mask(size)

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
    color_raster: Image.Image,
    content_ratio: float = MASKABLE_CONTENT_RATIO,
) -> Image.Image:
    """Full-bleed cream maskable icon with an inset brand tile (safe zone)."""
    out = Image.new("RGBA", (size, size), (*CREAM, 255))
    tile = compose_brand_icon(size, rounded_mask, color_raster)
    content = max(1, int(round(size * content_ratio)))
    scaled = tile.resize((content, content), Image.Resampling.LANCZOS)
    out.alpha_composite(scaled, ((size - content) // 2, (size - content) // 2))
    return out


def compose_adaptive_foreground(
    size: int,
    source: Image.Image,
    *,
    as_mask: bool = False,
    content_ratio: float = SAFE_ZONE_RATIO,
) -> Image.Image:
    """Transparent adaptive layer, inset to the OEM safe zone.

    `as_mask` paints the source's alpha white (the themed/monochrome layer);
    otherwise the full-colour mark is used as-is (the brand layer).
    """
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    content = max(1, int(round(size * content_ratio)))
    if as_mask:
        mask = source.resize((content, content), Image.Resampling.LANCZOS)
        layer = Image.new("RGBA", (content, content), (0, 0, 0, 0))
        white = Image.new("RGBA", (content, content), (255, 255, 255, 255))
        layer.paste(white, mask=mask)
    else:
        layer = source.resize((content, content), Image.Resampling.LANCZOS)
    out.alpha_composite(layer, ((size - content) // 2, (size - content) // 2))
    return out


def background_drawable_name(theme: ThemeSpec) -> str:
    return "ic_launcher_background_cream" if theme.brand else f"ic_launcher_background{theme.suffix}"


def foreground_drawable_name(theme: ThemeSpec) -> str:
    return "ic_launcher_foreground_brand" if theme.brand else "ic_launcher_foreground"


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


def write_solid_background_xml(path: Path, color: tuple[int, int, int]) -> None:
    path.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<shape xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:shape="rectangle">\n'
        f'    <solid android:color="{rgb_hex(color)}" />\n'
        "</shape>\n",
        encoding="utf-8",
    )


def write_adaptive_icon_xml(path: Path, background_name: str, foreground_name: str) -> None:
    path.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="@drawable/{background_name}" />\n'
        f'    <foreground android:drawable="@drawable/{foreground_name}" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n",
        encoding="utf-8",
    )


def write_density_icons(
    color_raster: Image.Image,
    logo_mask: Image.Image,
    rounded_mask: Image.Image,
    circle_mask: Image.Image,
) -> None:
    """Legacy pre-masked density PNGs (API < 26 / launcher shortcuts)."""
    for theme in THEMES:
        base = "ic_launcher" + theme.suffix
        for folder, px in DENSITIES.items():
            out_dir = RES / folder
            out_dir.mkdir(parents=True, exist_ok=True)
            if theme.brand:
                square = compose_brand_icon(px, rounded_mask, color_raster)
                round_icon = compose_brand_icon(px, circle_mask, color_raster)
            else:
                square = compose_themed_icon(px, rounded_mask, logo_mask, theme.start, theme.end)
                round_icon = compose_themed_round_icon(px, logo_mask, theme.start, theme.end)
            square.save(out_dir / f"{base}.png", optimize=True)
            round_icon.save(out_dir / f"{base}_round.png", optimize=True)


def write_adaptive_icons(color_raster: Image.Image, logo_mask: Image.Image) -> None:
    """Emit adaptive layers + anydpi-v26 XML so the system icon mask applies."""
    drawable = RES / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    nodpi = RES / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)

    # Brand layer keeps the palette; themed layers are white silhouettes.
    compose_adaptive_foreground(ADAPTIVE_PX, color_raster).save(
        nodpi / "ic_launcher_foreground_brand.png", optimize=True
    )
    compose_adaptive_foreground(ADAPTIVE_PX, logo_mask, as_mask=True).save(
        nodpi / "ic_launcher_foreground.png", optimize=True
    )
    # Same silhouette; Android themed icons tint via the alpha channel.
    compose_adaptive_foreground(ADAPTIVE_PX, logo_mask, as_mask=True).save(
        nodpi / "ic_launcher_monochrome.png", optimize=True
    )

    for theme in THEMES:
        bg_name = background_drawable_name(theme)
        if theme.brand:
            write_solid_background_xml(drawable / f"{bg_name}.xml", CREAM)
        else:
            write_gradient_background_xml(drawable / f"{bg_name}.xml", theme.start, theme.end)
        write_adaptive_icon_xml(
            anydpi / f"ic_launcher{theme.suffix}.xml",
            bg_name,
            foreground_drawable_name(theme),
        )


def write_splash_marks(color_raster: Image.Image, noir_raster: Image.Image) -> None:
    """Android 12+ splash icon source: transparent mark, no tile, no inset.

    drawable/splash_icon.xml applies the 25% inset the SplashScreen API needs.
    """
    for folder, raster in (("drawable-nodpi", color_raster), ("drawable-night-nodpi", noir_raster)):
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        compose_mark_image(LOGO_SIZE, raster, SPLASH_BOX_RATIO).save(
            out_dir / "ic_splash_mark.png", optimize=True
        )


def write_pwa_icons(
    rounded_mask: Image.Image,
    color_raster: Image.Image,
    noir_raster: Image.Image,
    logo_mask: Image.Image,
    ring_mask: Image.Image,
) -> None:
    """PWA, store-listing, and website icons.

    Tiles carry the light mark on cream; the bare site mark is the noir variant,
    because every ground it sits on is plum.
    """
    PWA_ICONS.mkdir(parents=True, exist_ok=True)
    for name, px in (
        ("icon-192.png", 192),
        ("icon-512.png", 512),
        ("apple-touch-icon.png", 180),
    ):
        compose_brand_icon(px, rounded_mask, color_raster).save(PWA_ICONS / name, optimize=True)

    compose_maskable(512, rounded_mask, color_raster).save(
        PWA_ICONS / "icon-maskable-512.png", optimize=True
    )

    # F-Droid / store listing: cream brand squircle, transparent corners.
    METADATA_ICON.parent.mkdir(parents=True, exist_ok=True)
    compose_brand_icon(512, rounded_mask, color_raster).save(METADATA_ICON, optimize=True)

    # Website header/OG art: transparent mark, no tile. Both grounds on the site are
    # plum, so this is the noir (glow) mark with a transparent hub — the light mark
    # only ever sits on cream.
    WEBSITE_IMG.mkdir(parents=True, exist_ok=True)
    compose_mark_image(LOGO_SIZE, noir_raster, 1.0).save(WEBSITE_IMG / "logo.png", optimize=True)

    # Browser tab: ring-only tile (needle drops below ~32px; see compose_ring_icon).
    # Root .ico: Google and several browsers fetch /favicon.ico regardless of <link>
    # tags, so it must come from the same generator, not a hand-made relic.
    WEBSITE_IMG.mkdir(parents=True, exist_ok=True)
    compose_ring_icon(64, rounded_mask, ring_mask).save(
        WEBSITE_IMG / "favicon.png", optimize=True
    )
    compose_ring_icon(64, rounded_mask, ring_mask).save(
        WEBSITE_STATIC / "favicon.ico",
        sizes=[(16, 16), (32, 32), (48, 48)],
    )


def main() -> None:
    color_raster = rasterize_svg(MARK_SVG, RASTER_PX)
    noir_raster = rasterize_svg(MARK_NOIR_SVG, RASTER_PX)
    mask_raster = rasterize_svg(MARK_MASK_SVG, RASTER_PX)
    ring_raster = rasterize_svg(RING_SVG, RASTER_PX)
    logo_mask = logo_mask_from_raster(mask_raster)
    ring_mask = logo_mask_from_raster(ring_raster)
    rounded_mask = make_squircle_mask(RASTER_PX)
    circle_mask = make_circle_mask(RASTER_PX)

    # Generated preview for quick eyeballing; edit the SVG, not this PNG.
    compose_brand_icon(1024, rounded_mask, color_raster).save(MASTER_PREVIEW, optimize=True)

    write_density_icons(color_raster, logo_mask, rounded_mask, circle_mask)
    write_adaptive_icons(color_raster, logo_mask)
    write_splash_marks(color_raster, noir_raster)
    write_pwa_icons(rounded_mask, color_raster, noir_raster, logo_mask, ring_mask)

    print(
        f"Generated icons from {MARK_SVG.relative_to(ROOT)} "
        f"({RASTER_PX}px raster) for {len(THEMES)} themes across "
        f"{len(DENSITIES)} densities + adaptive anydpi-v26, splash marks, and "
        f"PWA/website icons."
    )


if __name__ == "__main__":
    main()
