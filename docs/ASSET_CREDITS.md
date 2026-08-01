# Asset Credits

## Upstream

Chompass is forked from [Fud AI](https://github.com/apoorvdarshan/fud-ai) by Apoorv Darshan (MIT License).

## Barcode nutrition

Barcode product lookups are powered by the [Open Food Facts](https://world.openfoodfacts.org) database, queried live via its public API. Open Food Facts data is available under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/). Chompass does not bundle the database — nutrition facts are fetched per scanned barcode.

## App icon

Chompass launcher icons and splash logos are original artwork by fitguy, distinct from upstream Fud AI artwork (which was provided by the original project owner). The master source is the SVG brand mark `scripts/assets/chompass_icon_mark.svg` (compass ring + cardinal diamonds by fitguy; needle path from the CC0 fork below); regenerate the mark with `uv run python scripts/assets/build_icon_mark.py`, then themed and density variants with `uv run --with pillow python scripts/generate_icons.py` (via `resvg`). `scripts/chompass_icon_master.png` is a generated teal preview only — edit the SVG, not the PNG. Default brand (Android teal launcher, PWA install icons, website favicon, F-Droid listing) uses the teal gradient `#006B5E` → `#5CC48F`.

The compass-needle silhouette is adapted from [Fork SVG](https://www.svgrepo.com/svg/203809/fork) on SVG Repo (`scripts/assets/fork_needle_svgrepo.svg`), dedicated to the public domain under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).

On Android API 26+, home-screen icons use adaptive XML (`mipmap-anydpi-v26`) with a full-bleed theme background, a safe-zone foreground logo, and a monochrome layer so the system icon-shape mask and Material You themed icons apply. PWA and store listing icons remain pre-shaped squircles.
