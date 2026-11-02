# Brand refresh — bitten-ring mark, cream→plum, mark motion

Status: done (2026-11)

Adopts the design study at [`brand/logo-system.html`](brand/logo-system.html) as the Chompass brand:
a bitten compass ring, a two-facet needle pointing into the bite, a cream→plum palette, a 1.6 s mark
intro and an idle sway. No customization is removed — System/Light/Dark/OLED, every fixed accent,
glass blur/strength, ambient controls, fixed-icon toggle and launcher-alias switching all stay.
Plum (fig) is the **temporary** default; Peach ships alongside so the default can be flipped without
a redesign (the swap steps are enumerated in `AppThemeColor.DEFAULT_KEY`'s call sites).

## Brand defaults (decided 2026-11-02)

- **Mark**: Ripened. Light ground cream `#FFF6EC` with ink needle (`#1C131A` / `#4A3346`) and cream
  hub; dark ground plum `#170E16` with the `RipenedGlow` ring, blush needle (`#FFF6EC` / `#FFC9A8`)
  and plum hub. The hub is an opaque disc that matches the ground, so the pivot reads as a hole.
- **Rule**: the light mark never sits on plum. Everything switches with its ground — in-app Canvas by
  background luminance, Android splash by `drawable-night`, and the static art explicitly: the site
  header logo and OG image use the noir asset; launcher/favicon/PWA/BIMI tiles carry the light mark
  on cream.
- **Accents**: decoupled. Plum and Peach change chrome only; one cream launcher icon serves both, so
  flipping `AppThemeColor.DEFAULT_KEY` needs no asset regeneration.
- Rejected for now: Classic (peach) and Fig (plum) mark gradients — both stay in the study
  (`docs/brand/logo-system.html`, Mark gradient switch) and in the local preview page.

## Commits

- **1 — reference**: study copied to `docs/brand/logo-system.html`, docs index row, this file.
- **2 — mark and assets**: `build_icon_mark.py` is the single geometry source (light/noir/mask SVGs +
  BIMI); `generate_icons.py` composes the cream-tile brand icon, the eight themed gradient icons,
  adaptive/monochrome layers, day+night splash marks, PWA and site icons; dead icon sets and
  per-theme splash styles deleted.
- **3 — palette + mark in app**: `BrandPalette.kt`; warm cream/blush/plum neutrals; fixed accents stop
  borrowing Material You neutrals; `PLUM` + `PEACH` with `DEFAULT_KEY = "plum"`; launcher aliases
  `LauncherPlum`/`LauncherPeach`; 18 locales; `ChompassMark.kt` Canvas mark + wordmark, `SystemMotion`
  gate, welcome intro, About header; contrast + geometry tests.
- **4 — web mirror**: PWA palette/accents/default pref/manifest/i18n, site palette + bitten-o wordmark
  partial, OG image and blog charts.
- **5 — screenshots**: capture palette pinned to Plum, published set recaptured on device.

## Device pass (2026-11-02, OnePlus DN2103)

Verified: welcome plays ring draw → needle turns → chomp then sways (frame diff shows hard-edge motion
at the mark only); a fresh install defaults to Plum; Home/Settings/Display/About render the warm
palette; Theme Color lists Plum first, Peach beside it, gradients on every fixed accent; splash is the
new mark on plum with no white flash; About shows the mark above the bitten-o wordmark. This launcher
applies Material You themed icons, which renders the monochrome layer — the cream tile is verified
from the generated resources and the PWA/README renders.

Re-pass checklist: launcher shape mask does not clip the ring; cream tile in light; `reset_onboarding`
timeline with animation scale 0 / battery saver leaving the mark static; System still yields Material
You; Fixed Launcher Icon yields the cream brand icon; OLED true black; PWA `/Chompass/app/` plum
default and accent list; `release:screenshots:check` green.
