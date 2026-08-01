#!/usr/bin/env python3
"""Emit scripts/assets/chompass_icon_mark.svg from the CC0 SVG Repo fork needle.

Ring geometry is fixed (outer R=344, inner R=297). The needle path comes from
scripts/assets/fork_needle_svgrepo.svg (https://www.svgrepo.com/svg/203809/fork,
CC0). Transform centers the fork's diagonal midpoint and scales so both tips
sit just inside the inner ring.
"""

from __future__ import annotations

import re
from pathlib import Path

ASSETS = Path(__file__).resolve().parent
OUT = ASSETS / "chompass_icon_mark.svg"
FORK = ASSETS / "fork_needle_svgrepo.svg"

# Measured so tips ≈ R 285 on the 1024 viewBox (inner ring R=297).
FORK_SCALE = 0.863064
FORK_OX = 242.5
FORK_OY = 269.5


def fork_path_d() -> str:
    text = FORK.read_text(encoding="utf-8")
    match = re.search(r'<path\s+d="([^"]+)"', text)
    if not match:
        raise SystemExit(f"no path d= in {FORK}")
    return match.group(1)


def main() -> None:
    d = fork_path_d()
    svg = f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" fill="#FFFFFF">
  <!--
    Chompass brand mark. Ring outer R=344, inner R=297 (~47px thick).
    Needle: CC0 fork from SVG Repo https://www.svgrepo.com/svg/203809/fork
    (see scripts/assets/fork_needle_svgrepo.svg). Regenerate via build_icon_mark.py.
  -->
  <path fill-rule="evenodd" d="
    M 512 168
    A 344 344 0 1 1 511.9 168
    Z
    M 512 215
    A 297 297 0 1 0 512.1 215
    Z
  "/>
  <path d="M 512 252 L 530 282 L 512 312 L 494 282 Z"/>
  <path d="M 780 512 L 753.5 529 L 727 512 L 753.5 495 Z"/>
  <path d="M 512 780 L 529 753.5 L 512 727 L 495 753.5 Z"/>
  <path d="M 244 512 L 270.5 529 L 297 512 L 270.5 495 Z"/>
  <g transform="translate(512 512) scale({FORK_SCALE}) translate({-FORK_OX} {-FORK_OY})">
    <path d="{d}"/>
  </g>
</svg>
"""
    OUT.write_text(svg, encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ASSETS.parents[1])}")


if __name__ == "__main__":
    main()
