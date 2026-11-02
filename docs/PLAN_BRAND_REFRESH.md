# Brand refresh — bitten-ring mark, cream→plum, mark motion

Status: WIP (2026-11)

Adopts the design study at [`brand/logo-system.html`](brand/logo-system.html) as the Chompass brand:
a bitten compass ring, a two-facet needle pointing into the bite, a cream→plum palette, a 1.6 s mark
intro and an idle sway. No customization is removed — System/Light/Dark/OLED, every fixed accent,
glass blur/strength, ambient controls, fixed-icon toggle and launcher-alias switching all stay.
Plum (fig) is the **temporary** default; Peach ships alongside so the default can be flipped without
a redesign.

## Commit order

- **1 — reference + plan**: study copied to `docs/brand/logo-system.html`, docs index row, this file.
- **2 — mark sources and assets**: `build_icon_mark.py` becomes the single geometry source (light,
  noir, mask SVGs + BIMI `logo.svg`); `generate_icons.py` composes a cream-tile brand icon plus the
  eight themed gradient icons, adaptive/monochrome layers, day+night splash marks, PWA and site
  icons; dead icon sets and per-theme splash styles deleted; onboarding logo points at the brand PNG.
- **3 — warm palette + Plum/Peach**: `BrandPalette.kt`; warm cream/blush/plum neutrals in `Color.kt`;
  fixed accents stop borrowing Material You neutrals; `colors.xml`/widget/ring fallbacks; `PLUM` +
  `PEACH` in `AppThemeColor` with `DEFAULT_KEY = "plum"`; launcher aliases `LauncherPlum` (enabled) /
  `LauncherPeach`; strings in all 18 locales; tests updated.
- **4 — in-app mark and motion**: `SystemMotion.kt` (power-save + animator-scale predicates),
  `Motion.kt` mark tokens, `ChompassMark.kt` (Canvas mark, bitten-o wordmark, brand header), animated
  welcome mark, About header, `ic_logo.png` deleted, geometry test.
- **5 — PWA mirror**: `web/app/css/main.css` palette + plum/peach accents, accent list, default pref,
  manifest colors, i18n keys, parity fixture note.
- **6 — website mirror**: `website` palette, bitten-o wordmark partial in header and hero, OG image
  and blog charts regenerated, BIMI note.
- **7 — screenshots**: capture palette pinned to Plum, published set recaptured on device.

## Device pass

- Launcher: cream squircle, gradient ring, ink needle; circle mask does not clip the ring; Material
  You themed icons show the bitten-ring silhouette; Peach and Plum select the same cream brand icon.
- Cold start: new mark on cream (light) / plum (night); no white flash, no teal, no old fork.
- `reset_onboarding`: ring draw → needle 3 turns with overshoot → chomp → ±2.4° sway; animation
  scale 0 or battery saver leaves the mark static.
- Settings → App & Display → Theme Color: Plum selected, Peach present, other accents keep warm
  neutrals, System still yields Material You, Fixed Launcher Icon yields the cream brand icon.
- Dark/OLED: plum neutrals or true black, blush text, glow-variant mark; no gray-purple surfaces.
- About: brand header (mark sway + bitten-o wordmark in fixed peach); glass chrome and every ambient
  control behave as in 6.0.1.
- PWA `/Chompass/app/`: plum default, Plum/Peach in the accent list, warm surfaces, bitten-o
  wordmark in the site header and hero, plum manifest theme color.
- `release:screenshots` recaptured and `release:screenshots:check` green.
