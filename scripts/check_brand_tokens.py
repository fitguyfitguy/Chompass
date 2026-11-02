#!/usr/bin/env python3
"""Guard the brand tokens that are duplicated across surfaces.

`docs/brand/logo-system.html` is the source of truth. `BrandPalette.kt` is its
Kotlin transcription; the PWA CSS, the PWA mark gradients and the site CSS each
carry a subset by hand. That duplication already drifted once (the PWA kept the
pre-brand teal values and the site ambient kept a teal-green field), so this
check pins the pairs that must stay byte-identical.

Run from the repo root; stdlib only.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

PALETTE = ROOT / "android/app/src/main/java/app/chompass/ui/theme/BrandPalette.kt"
PWA_CSS = ROOT / "web/app/css/main.css"
PWA_INDEX = ROOT / "web/app/index.html"
SITE_CSS = ROOT / "website/assets/css/main.css"
SITE_MARK = ROOT / "website/layouts/partials/mark.html"

failures: list[str] = []


def read(path: Path) -> str:
    if not path.exists():
        failures.append(f"missing file: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


def hex6(value: str) -> str:
    value = value.strip().lstrip("#").upper()
    if not re.fullmatch(r"[0-9A-F]{6}", value):
        raise ValueError(f"not a 6-digit hex colour: {value!r}")
    return value


def kotlin_val(source: str, name: str) -> str | None:
    match = re.search(rf"val {re.escape(name)} = Color\(0xFF([0-9A-Fa-f]{{6}})\)", source)
    return hex6(match.group(1)) if match else None


def kotlin_ramp(source: str, name: str, palette: dict[str, str]) -> list[str] | None:
    """Stops of a `listOf(offset to Colour, ...)` gradient, in order."""
    block = re.search(rf"val {name}: List<Pair<Float, Color>> = listOf\((.*?)\n    \)", source, re.S)
    if not block:
        return None
    body = block.group(1)
    stops: list[str] = []
    for offset, token in re.findall(r"([0-9.]+f) to (.+?)(?:,|\n|$)", body):
        token = token.strip()
        literal = re.fullmatch(r"Color\(0xFF([0-9A-Fa-f]{6})\)", token)
        if literal:
            stops.append(hex6(literal.group(1)))
        elif token in palette:
            stops.append(palette[token])
        else:
            failures.append(f"BrandPalette.{name}: unknown colour token {token!r} at {offset}")
            return None
    return stops


def css_blocks(source: str, selector: str) -> list[str]:
    """Bodies of every rule whose selector matches exactly (brace-balanced)."""
    bodies: list[str] = []
    needle = f"{selector} {{"
    start = 0
    while True:
        at = source.find(needle, start)
        if at == -1:
            return bodies
        depth = 0
        i = at + len(needle) - 1
        while i < len(source):
            if source[i] == "{":
                depth += 1
            elif source[i] == "}":
                depth -= 1
                if depth == 0:
                    bodies.append(source[at + len(needle):i])
                    start = i + 1
                    break
            i += 1
        else:
            return bodies


def css_value(body: str, prop: str) -> str | None:
    value = re.search(rf"--{re.escape(prop)}:\s*([^;]+);", body)
    return value.group(1).strip() if value else None


def css_root_value(source: str, selector: str, prop: str) -> str | None:
    blocks = css_blocks(source, selector)
    return css_value(blocks[0], prop) if blocks else None


def css_decl(source: str, selector: str, prop: str) -> str | None:
    blocks = css_blocks(source, selector)
    if not blocks:
        return None
    value = re.search(rf"(?m)^\s*{re.escape(prop)}:\s*([^;]+);", blocks[0])
    return value.group(1).strip() if value else None


def css_accent_value(source: str, accent: str, prop: str) -> str | None:
    return css_root_value(source, f':root[data-accent="{accent}"]', prop)


def gradient_stops(source: str, gradient_id: str) -> list[str] | None:
    block = re.search(
        rf'<linearGradient id="{re.escape(gradient_id)}"(.*?)</linearGradient>',
        source,
        re.S,
    )
    if not block:
        return None
    return [hex6(stop) for stop in re.findall(r'stop-color="(#[0-9A-Fa-f]{6})"', block.group(1))]


def expect(label: str, actual: str | None, expected: str) -> None:
    if actual is None:
        failures.append(f"{label}: not found")
    elif hex6(actual) != hex6(expected):
        failures.append(f"{label}: {actual} != {expected}")


def expect_token(label: str, actual: str | None, expected: str) -> None:
    """Compare a token reference (e.g. `var(--bg)`), not a colour literal."""
    if actual is None:
        failures.append(f"{label}: not found")
    elif re.sub(r"\s+", "", actual).lower() != re.sub(r"\s+", "", expected).lower():
        failures.append(f"{label}: {actual} != {expected}")


def main() -> int:
    palette_src = read(PALETTE)
    pwa_css = read(PWA_CSS)
    pwa_index = read(PWA_INDEX)
    site_css = read(SITE_CSS)
    site_mark = read(SITE_MARK)

    palette = {
        name: value
        for name in (
            "Peach200",
            "Peach300",
            "Peach400",
            "Peach700",
            "PeachAccent",
            "Fig300",
            "Fig500",
            "Fig600",
        "Fig900",
            "Fig900",
            "Ink",
            "Cream",
            "Blush",
            "Plum",
        )
        if (value := kotlin_val(palette_src, name))
    }

    required = {
        "Peach200",
        "Peach300",
        "Peach400",
        "Peach700",
        "PeachAccent",
        "Fig300",
        "Fig500",
        "Fig600",
        "Fig900",
        "Ink",
        "Cream",
        "Blush",
        "Plum",
    }
    missing = required - palette.keys()
    for name in sorted(missing):
        failures.append(f"BrandPalette.{name}: missing from {PALETTE.relative_to(ROOT)}")

    ripened = kotlin_ramp(palette_src, "Ripened", palette)
    ripened_glow = kotlin_ramp(palette_src, "RipenedGlow", palette)
    if ripened is None:
        failures.append("BrandPalette.Ripened: could not parse stops")
    if ripened_glow is None:
        failures.append("BrandPalette.RipenedGlow: could not parse stops")

    # --- PWA accent roles ---------------------------------------------------
    if palette:
        expect(
            "PWA --teal (plum fill)",
            css_accent_value(pwa_css, "plum", "teal"),
            palette["Fig300"],
        )
        expect(
            "PWA --teal-deep (plum)",
            css_accent_value(pwa_css, "plum", "teal-deep"),
            palette["Fig600"],
        )
        expect(
            "PWA --accent-light (plum)",
            css_accent_value(pwa_css, "plum", "accent-light"),
            palette["Fig500"],
        )
        expect(
            "PWA --accent-light (peach)",
            css_accent_value(pwa_css, "peach", "accent-light"),
            palette["PeachAccent"],
        )
        expect(
            "PWA --teal (peach fill)",
            css_accent_value(pwa_css, "peach", "teal"),
            palette["Peach400"],
        )
        # The light theme is duplicated (media query + explicit attribute); pin both.
        light_blocks = css_blocks(pwa_css, ':root:not([data-theme="dark"])') + css_blocks(
            pwa_css, ':root[data-theme="light"]'
        )
        if len(light_blocks) != 2:
            failures.append(f"PWA light theme: expected 2 light blocks, found {len(light_blocks)}")
        for index, body in enumerate(light_blocks, start=1):
            expect(
                f"PWA light block {index} --brand-peach",
                css_value(body, "brand-peach"),
                palette["Peach700"],
            )
            expect(f"PWA light block {index} --focus", css_value(body, "focus"), palette["Fig500"])
            expect(f"PWA light block {index} --mark-needle-a", css_value(body, "mark-needle-a"), palette["Ink"])
            expect_token(f"PWA light block {index} --mark-hub", css_value(body, "mark-hub"), "var(--bg)")
            expect(f"PWA light block {index} --mark-pin", css_value(body, "mark-pin"), palette["Ink"])

    # --- PWA mark gradients + palette (root = plum ground) -----------------
    expect("PWA --mark-needle-a (dark)", css_root_value(pwa_css, ":root", "mark-needle-a"), palette["Cream"])
    expect("PWA --mark-needle-b (dark)", css_root_value(pwa_css, ":root", "mark-needle-b"), "FFC9A8")
    expect("PWA --mark-pin (dark)", css_root_value(pwa_css, ":root", "mark-pin"), palette["Cream"])
    expect_token("PWA --mark-hub (dark)", css_root_value(pwa_css, ":root", "mark-hub"), "var(--bg)")
    if ripened:
        expect_gradient = gradient_stops(pwa_index, "cp-mark-ripened-light")
        if expect_gradient is None:
            failures.append("PWA cp-mark-ripened-light: gradient not found")
        elif [hex6(c) for c in expect_gradient] != ripened:
            failures.append(f"PWA cp-mark-ripened-light: {expect_gradient} != {ripened}")
    if ripened_glow:
        expect_gradient = gradient_stops(pwa_index, "cp-mark-ripened-glow")
        if expect_gradient is None:
            failures.append("PWA cp-mark-ripened-glow: gradient not found")
        elif [hex6(c) for c in expect_gradient] != ripened_glow:
            failures.append(f"PWA cp-mark-ripened-glow: {expect_gradient} != {ripened_glow}")

    # --- site --------------------------------------------------------------
    if palette:
        expect("site --brand-peach", css_root_value(site_css, ":root", "brand-peach"), palette["Peach300"])
        expect("site --teal", css_root_value(site_css, ":root", "teal"), palette["Fig300"])
        expect("site mark needle A", css_decl(site_css, ".chompass-mark__needle-a", "fill"), palette["Cream"])
        if ripened_glow:
            site_stops = gradient_stops(site_mark, "chompass-mark-glow")
            if site_stops is None:
                failures.append("site chompass-mark-glow: gradient not found")
            elif [hex6(c) for c in site_stops] != ripened_glow:
                failures.append(f"site chompass-mark-glow: {site_stops} != {ripened_glow}")

    if failures:
        print("Brand token check FAILED:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print("Brand tokens OK (Kotlin / PWA / site)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
