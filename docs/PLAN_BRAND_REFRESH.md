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
- **Launch**: API 31+ shows an animated splash icon (ring trim + the study's 1.45 s needle spin +
  the dial chomp at 1.08 s); pre-31 keeps the static PNG. Nothing gates the first frame on it.
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

## Second pass — coverage audit (2026-11-02)

The first pass landed the mark on Android and the static assets, but the PWA had no mark or brand
motion at all and several accent roles were still the pre-brand teal system wearing fig values.
What changed here, per surface:

| Surface | Gap found against the study | Fix |
|---|---|---|
| PWA welcome | Launcher-icon PNG (cream rounded tile) instead of the mark; no intro | `lib/brand-mark.js` renders the study geometry inline; the welcome plays the 1.6 s intro, then sways. Reduced-motion starts at rest |
| PWA About | No lockup | Mark above the bitten-o wordmark, plus the fixed brand peach |
| PWA light ground | `a`/icons/ring labels used `--teal` = fig-300 → **2.01:1** on cream; `--focus` was `#FFB994` → **1.56:1** | New `--accent-text` role (fig-500 light / fig-300 dark, per accent); `--focus` follows it. The pale `--teal` stays the fill tone |
| PWA filled controls | Selected day, active metric toggle and `.btn--gradient` put `#fff` on a fig-300→fig-600 gradient → **2.15:1** at the pale end | Solid deep accent + `--accent-ink` (the study's filled CTA) |
| PWA chrome | Static `theme-color` was still `#006b5e` (retired teal); manifest said `#6e2f5c` | Both follow the ground (`#170e16` default); `app.js` re-syncs from `--bg` |
| PWA peach accent | `#c44428` → 4.49:1 | `#C24327` (Android's `PeachAccent`, 4.57:1) |
| PWA shell | cache-first SW kept the old stylesheet | `chompass-shell-v14`; README now documents the bump rule |
| PWA welcome copy | "Track food. Keep control." vs study/Android "Track meals, / Stay balanced" | **Open** — needs the split key in 15 catalogs; CSS already renders the accent line solid |
| Site ambient | WebGL wax field still used the pre-brand teal/green/blue triad (`#5CC48F` + hue shifts) under a plum page | Ported the app's ACCENT rule: fig-500 + hueShift(+60, ·0.85) + hueShift(−75, ·0.90) |
| Site header | 32 px raster `logo.png` | Inline noir mark (`partials/mark.html`), same 2 rem box |
| Site fallback | `#0f0e13` file-system/demo grounds (pre-brand near-black) | Plum `#170e16` / blush muted |
| Android mark | Idle sway/float ignored the "Ambient motion" switch | `ChompassTraitsDefaults.ambientMotion()` gates the idle loops; the one-shot intro still plays. Battery saver / animations-off still win |
| Android wordmark | Placeholder 0.60 × 0.66 em left the bitten "o" riding high | Square 0.60 em box, matching the study's 60-unit viewBox |
| Android dark ring | `RipenedGlow` ended at Fig900 `#1F0C1B` (1.06:1 on plum) while the splash/launcher/static art end at the study's `#5E1F45` (1.58:1) | Canvas matches the study and the generated assets |
| Drift guard | Brand hexes are duplicated across Kotlin, PWA CSS/defs and site CSS/partial; the first pass already drifted | `scripts/check_brand_tokens.py`, wired into `release:check-parity` |

Deliberate divergences (not bugs):

- **Splash ring trim is 400 ms**, not the study's 900 ms: the system splash is capped at ~1 s and the
  app usually draws its first frame well before that; the full 900 ms would be cut mid-draw.
- **The light bitten "o" is Peach700 `#C44428`**, not the study CSS's Peach600 `#E85D3A` (3.24:1):
  at 13–26 sp the stroke-only glyph needs the extra step (4.67:1). Dark ground matches the study
  (Peach300).
- **Pointer-tracked needle and per-letter wordmark intro** stay study-only. The site's hero is a
  phone demo, not a brand stage; the PWA has one intro surface (welcome) like Android.

Re-run the check matrix: `web` typecheck + unit tests, `hugo --minify -s website`, Android
`:app:testDebugUnitTest`, and the PWA visual pass (welcome light/dark, About light/dark,
reduced-motion).

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
