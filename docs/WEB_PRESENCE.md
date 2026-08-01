# Web presence

Maintainer checklist for discovering Chompass outside Codeberg. Canonical end-user URL: [chompass.app](https://chompass.app/).

## Already covered

- Hugo project site on Codeberg Pages
- Blog section at [chompass.app/blog/](https://chompass.app/blog/) (first post: AI food-logging accuracy; draft until `draft: false` in Hugo front matter). Regen charts: `uv run --with matplotlib python scripts/generate_blog_accuracy_charts.py` → `website/static/img/blog/accuracy/`
- README install CTAs (Obtainium, Codeberg Releases, web app)
- F-Droid metadata submitted (package `app.chompass`)
- Social preview card (`website/static/img/og.png`) and sitemap
- Browser PWA promoted from site hero (primary CTA), Download (PWA first), Features, footer, and README (3-column Fud AI / Android / PWA table). Any modern browser; Chromium works best.

## Companion PWA

PWA at [chompass.app/app/](https://chompass.app/app/). Runs in any modern browser (Chromium-based browsers work best for install, camera barcode, and speech). Diary logging, manual/photo/barcode food entry, BYOK AI coach, progress charts, and JSON export/import compatible with the Android app. Source in [`web/`](../web/README.md); no SPA framework/bundler, hand-rolled service worker, dev-only TypeScript.

Deploy: `./scripts/deploy_pages.sh` copies `web/app/` into `website/public/app/` (via `rsync --delete`) before the same orphan-branch push used for the marketing site. No separate deploy step.

**Parity:** feature matrix in [`PARITY.md`](PARITY.md); `devenv tasks run release:check-parity` before treating export/formula changes as done.

**Public promotion:** hero primary CTA is “Try web app”; Download leads with PWA then Android; README / Features / Codeberg About (see [`DEVELOPMENT.md`](DEVELOPMENT.md) § Codeberg repo settings). Canonical URL: `chompass.app/app/`.

## Do not pursue

- **IzzyOnDroid:** rejected because of AI features; do not re-submit without a policy change

## Outreach (manual)

Use the Hugo site as the link people should open first. Point builders at the Codeberg repo. Lead with **private calorie tracking on Android and in the browser** (try the PWA, or install via Obtainium). Mention meal components, recipes, and progressive AI feedback when space allows. Keep Fud AI as upstream credit, not the headline. Mention Chromium works best without claiming Chromium-only.

| Channel | Notes |
|---------|--------|
| [AlternativeTo](https://alternativeto.net/) | Add/claim Chompass as alternative to MyFitnessPal / Cronometer / Fud AI |
| Lemmy (e.g. `!opensource`, Android / FOSS communities) | Short post: no ads or analytics, browser PWA or Obtainium, meal components / on-device AI |
| Reddit `r/opensource`, `r/androidapps`, `r/fossdroid`, `r/PWA` | Same pitch; respect self-promo rules; PWA works cross-platform |
| Mastodon / Fediverse | Link site + `/app/` or Obtainium; boost FOSS / PWA hashtags |
| Privacy Guides-style lists / forums | Only if listing criteria fit (local storage, no analytics) |

## After each release

1. Bump `website/hugo.toml` `params.version` with `versionName` (guarded by `release:check-metadata`). Usually done in the release commit.
2. Site redeploy is automatic: `./scripts/publish_release.sh <version>` runs [`deploy_pages.sh`](../scripts/deploy_pages.sh) after uploading APKs. Manual redeploy: `devenv tasks run site:deploy` or `./scripts/deploy_pages.sh`. Skip with `--skip-pages` on publish.
3. Optionally refresh OG art: `uv run --with pillow python scripts/generate_og_image.py`

See also [`DEVELOPMENT.md`](DEVELOPMENT.md) (site build/deploy) and [`FDROID_SUBMISSION.md`](FDROID_SUBMISSION.md).
