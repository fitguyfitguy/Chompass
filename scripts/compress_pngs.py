#!/usr/bin/env python
"""Compress published PNGs in place with pngquant, Pillow quantize fallback.

Keeps the repository small: recaptured screenshots never delta in git, so every
saved byte counts twice (working tree + history). Only published images are
compressed — never the JVM screenshotTest references (they are the pixel gate
and must stay exactly as rendered).

Usage:
  uv run --with pillow python scripts/compress_pngs.py DIR [DIR ...]

Each file is replaced only when it gets smaller; already-palette PNGs are kept.
"""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image

MIN_SAVING_BYTES = 2048  # do not churn bytes for negligible wins


def _pngquant(path: Path) -> bool | None:
    """Quantize in place. True/False = changed or not, None = pngquant absent."""
    if shutil.which("pngquant") is None:
        return None
    before = path.stat().st_size
    result = subprocess.run(  # noqa: S603 - fixed argv, no shell
        [
            "pngquant",
            "--force",  # overwrite the input path
            "--output", str(path),
            "--skip-if-larger",
            "--strip",
            "--quality=70-95",
            "--speed=1",
            str(path),
        ],
        capture_output=True,
    )
    if result.returncode not in (0, 98, 99):  # 98=skipped larger, 99=quality out of range
        print(f"    pngquant failed ({result.returncode}): {path}", file=sys.stderr)
        return False
    return path.stat().st_size < before


def _quantize(path: Path) -> bool:
    """Pillow fallback: FASTOCTREE keeps alpha and needs no optional deps."""
    before = path.stat().st_size
    tmp = path.with_suffix(".tmp.png")
    try:
        with Image.open(path) as im:
            if im.mode == "P":
                return False
            out = im.quantize(colors=256, method=2, dither=Image.Dither.FLOYDSTEINBERG)
            out.save(tmp, optimize=True)
    except ValueError:
        # Pillow build without quantize support: skip, pngquant does the work
        # in devenv shells.
        tmp.unlink(missing_ok=True)
        return False
    if tmp.stat().st_size + MIN_SAVING_BYTES < before:
        tmp.replace(path)
        return True
    tmp.unlink()
    return False


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    pngs: list[Path] = []
    for root in sys.argv[1:]:
        pngs.extend(sorted(Path(root).rglob("*.png")))
    if not pngs:
        return 0
    before_total = sum(p.stat().st_size for p in pngs)

    changed = 0
    for path in pngs:
        result = _pngquant(path)
        if result is None:  # no pngquant on PATH
            result = _quantize(path)
        changed += bool(result)
    after_total = sum(p.stat().st_size for p in pngs)

    saved = before_total - after_total
    print(
        f"    compressed {changed}/{len(pngs)} PNGs, "
        f"{before_total / 1048576:.1f} -> {after_total / 1048576:.1f} MiB "
        f"(-{saved / 1024:.0f} KiB)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
