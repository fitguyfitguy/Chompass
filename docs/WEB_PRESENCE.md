# Web presence

Maintainer checklist for discovering NoFUD outside Codeberg. Canonical end-user URL: [fitguy.codeberg.page/NoFUD](https://fitguy.codeberg.page/NoFUD/).

## Already covered

- Hugo project site on Codeberg Pages
- README install CTAs (Obtainium, Codeberg Releases)
- F-Droid metadata submitted (package `org.codeberg.fitguy.nofud`)
- Social preview card (`website/static/img/og.png`) and sitemap

## Companion PWA

Scoped-down companion web app at [fitguy.codeberg.page/NoFUD/app/](https://fitguy.codeberg.page/NoFUD/app/) — diary logging, manual/photo/barcode food entry, BYOK AI coach, progress charts, and JSON export/import compatible with the Android app. Source in [`web/`](../web/README.md); no SPA framework/bundler, hand-rolled service worker, dev-only TypeScript.

Deploy: `./scripts/deploy_pages.sh` copies `web/app/` into `website/public/app/` (via `rsync --delete`) before the same orphan-branch push used for the marketing site — no separate deploy step to remember.

**Parity:** feature matrix in [`PARITY.md`](PARITY.md); `devenv tasks run release:check-parity` before treating export/formula changes as done.

**Soft-launched**: linked from the marketing site nav (“Web app”) and Download page. Canonical URL: `fitguy.codeberg.page/NoFUD/app/`.

## Do not pursue

- **IzzyOnDroid** — rejected because of AI features; do not re-submit without a policy change

## Outreach (manual)

Use the Hugo site as the link people should open first. Point builders at the Codeberg repo.

| Channel | Notes |
|---------|--------|
| [AlternativeTo](https://alternativeto.net/) | Add/claim NoFUD as alternative to MyFitnessPal / Cronometer / Fud AI |
| Lemmy (e.g. `!opensource`, Android communities) | Short post: ad-free fork, Obtainium install, privacy stance |
| Reddit `r/opensource`, `r/androidapps`, `r/fossdroid` | Same pitch; respect self-promo rules |
| Mastodon / Fediverse | Link site + Obtainium; boost FOSS Android hashtags |
| Privacy Guides–style lists / forums | Only if listing criteria fit (local-first, no analytics) |

## After each release

1. Bump `website/hugo.toml` `params.version` with `versionName` (guarded by `release:check-metadata`) — usually done in the release commit
2. Site redeploy is automatic: `./scripts/publish_release.sh <version>` runs [`deploy_pages.sh`](../scripts/deploy_pages.sh) after uploading APKs. Manual redeploy: `devenv tasks run site:deploy` or `./scripts/deploy_pages.sh`. Skip with `--skip-pages` on publish.
3. Optionally refresh OG art: `uv run --with pillow python scripts/generate_og_image.py`

See also [`DEVELOPMENT.md`](DEVELOPMENT.md) (site build/deploy) and [`FDROID_SUBMISSION.md`](FDROID_SUBMISSION.md).
