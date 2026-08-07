# Web presence

Maintainer checklist for discovering Chompass outside Codeberg. Canonical end-user URL: [chompass.app](https://chompass.app/).

## Already covered

- Hugo project site on Codeberg Pages
- Blog section at [chompass.app/blog/](https://chompass.app/blog/) (first post: AI food-logging accuracy; draft until `draft: false` in Hugo front matter). Regen charts: `uv run --with matplotlib python scripts/generate_blog_accuracy_charts.py` → `website/static/img/blog/accuracy/`
- README install CTAs (Obtainium, Codeberg Releases, web app)
- F-Droid metadata submitted (package `app.chompass`)
- Social preview card (`website/static/img/og.png`) and sitemap
- Hero usage video (`website/static/video/chompass-usage.mp4` + poster) on the site header
- Browser PWA promoted from site hero (primary CTA), Download (PWA first), Features, footer, and README (3-column Fud AI / Android / PWA table). Any modern browser; Chromium works best.

## Companion PWA

PWA at [chompass.app/app/](https://chompass.app/app/). Runs in any modern browser (Chromium-based browsers work best for install, camera barcode, and speech). Diary logging, manual/photo/barcode food entry, BYOK AI coach, progress charts, and JSON export/import compatible with the Android app. Source in [`web/`](../web/README.md); no SPA framework/bundler, hand-rolled service worker, dev-only TypeScript.

Deploy: `./scripts/deploy_pages.sh` copies `web/app/` into `website/public/app/` (via `rsync --delete`) before the same orphan-branch push used for the marketing site. No separate deploy step.

**Parity:** feature matrix in [`PARITY.md`](PARITY.md); `devenv tasks run release:check-parity` before treating export/formula changes as done.

**Public promotion:** hero primary CTA is “Try web app”; Download leads with PWA then Android; README / Features / Codeberg About (see [`DEVELOPMENT.md`](DEVELOPMENT.md) § Codeberg repo settings). Canonical URL: `chompass.app/app/`.

## Hero usage video

The site-header hero is a **cinematic live demo, not a video file**: `website/assets/js/hero.js` swaps the `<video>` for a stage running the **real PWA** (`/app/demo.html`) inside a phone-sized canvas, with a CSS camera that smoothly zooms/crops into the app as the demo driver announces scenes — AI note → live macro fill → review → ring rise, barcode lookup against a canned product, a **warp-speed weight beat** (close-up on noisy daily readings that rapidly expands 1M → 3M → 6M → 1Y → All across 2y of history, ending on the stats badges and the weight forecast), one-tap relog. Lower-third callouts accompany each scene.

- Camera: single WAAPI-transform layer over a phone-proportioned 620×1330 canvas — every zoom stays ≤ 1:1 of the raster, so motion is compositor-only and text stays crisp (zoom ceiling = `devicePixelRatio`). The demo pauses offscreen/backgrounded; reduced-motion shows a static frame. GPU-composited at any DPI, no encoding, no mp4 download for JS-capable browsers.
- **Wide screens (≥64rem)** use a split stage: the app demo on the left, a per-scene description panel on the right (`SCENES` copy in `hero.js`); below that the single full-width stage keeps the lower-third callout pill. Rest state is a **hero-crop of the app's top region** (day nav + ring + macros) instead of the whole tall phone, so the zoomed-out view stays readable on wide desktop and mobile; the full phone frame appears **once per page load** (intro) and never during steady-state loops. The stage fades in as soon as the app home paints — the demo shell renders the home route immediately and seeds the throwaway DB in the background — so there is no blank first paint. Scenes whose target is announced mid-transition are **re-resolved by the camera on a short interval** (falling back to the hero-crop rest) instead of flashing an unreadable frame; tall sheets (entry review, barcode card) are framed top-first; the AI-analysis beat **quick-cuts** to the overlay (short camera duration, no pan across the opaque full-screen layer) and then crops the final-size partial card, so streaming never shows a blank screen.
- Demo shell: `web/app/demo.html` + `web/app/src/demo/` (`demo-main.js` entry, `demo-driver.js` beat sequencer + scene announcements, `demo-seed.js` seeding, `mock-ai.js` scripted AI reply). Runs against a throwaway `chompass-pwa-demo` IndexedDB (see `web/app/src/lib/db.js`) — never touches real user data. The PWA source is a **Hugo static mount** (`hugo.toml` → `static/app`), so the dev server and builds always serve the live `web/app/` with no rsync step.
- If the built page is opened straight from disk (`file://`), an inline guard in `website/layouts/index.html` replaces the stage with a "needs a web server" hint — the absolute asset URLs of a build would otherwise resolve to blocked `file://` references ("may not load or link to file:///"). The demo iframe src carries a `?v=<site version>` query so embedded previews (IDE webviews cache aggressively) pick up new demo files after a release.
- The mp4 (`website/static/video/chompass-usage.mp4` + poster) remains as the no-JS fallback (`preload="none"`, poster only).
- Regen the fallback mp4 (optional, needs a USB Android device — Pixel 9a coordinates are the defaults):
  1. Install the debug build and seed the app: `./scripts/install_debug.sh && ./scripts/install_debug.sh --reseed`
  2. Record clips (sets animation scales to 2.0 first; hold a real product barcode up at the `[3/4] barcode` step): `./scripts/capture_usage_video.sh` — retake one segment with `--only <trend|ai|barcode|diary>`; check raw clip durations ≥ ~17/27/26/20s.
  3. Overlay assets once: `uv run --with pillow python scripts/generate_video_overlays.py`
  4. Compose + poster: `./scripts/compose_usage_video.sh` (picks the newest clips; needs ffmpeg, auto re-execs under `nix shell nixpkgs#ffmpeg`).

## Do not pursue

- **IzzyOnDroid:** rejected because of AI features; do not re-submit without a policy change

## Outreach (manual)

Use the Hugo site as the link people should open first. Point builders at the Codeberg repo. Lead with **private calorie tracking on Android and in the browser** (try the PWA, or install via Obtainium). Keep the calorie budget and scanning details for the feature list, not the pitch: after a big run, log the burn manually in Add Food or let a wearable feed it through Health Connect; barcode scans resolve against Open Food Facts (4.6M+ products). Mention meal components, recipes, and progressive AI feedback when space allows. Keep Fud AI as upstream credit, not the headline. Mention Chromium works best without claiming Chromium-only.

| Channel                                                        | Notes                                                                                     |
| -------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| [AlternativeTo](https://alternativeto.net/)                    | Add/claim Chompass as alternative to MyFitnessPal / Cronometer / Fud AI                   |
| Lemmy (e.g. `!opensource`, Android / FOSS communities)         | Short post: no ads or analytics, browser PWA or Obtainium, meal components / on-device AI |
| Reddit `r/opensource`, `r/androidapps`, `r/fossdroid`, `r/PWA` | Same pitch; respect self-promo rules; PWA works cross-platform                            |
| Mastodon / Fediverse                                           | Link site + `/app/` or Obtainium; boost FOSS / PWA hashtags                               |
| Privacy Guides-style lists / forums                            | Only if listing criteria fit (local storage, no analytics)                                |

## After each release

1. Bump `website/hugo.toml` `params.version` with `versionName` (guarded by `release:check-metadata`). Usually done in the release commit.
2. Site redeploy is automatic: `./scripts/publish_release.sh <version>` runs [`deploy_pages.sh`](../scripts/deploy_pages.sh) after uploading APKs. Manual redeploy: `devenv tasks run site:deploy` or `./scripts/deploy_pages.sh`. Skip with `--skip-pages` on publish.
3. Optionally refresh OG art: `uv run --with pillow python scripts/generate_og_image.py`

See also [`DEVELOPMENT.md`](DEVELOPMENT.md) (site build/deploy) and [`FDROID_SUBMISSION.md`](FDROID_SUBMISSION.md).
