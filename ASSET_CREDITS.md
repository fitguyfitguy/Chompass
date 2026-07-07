# Asset Credits

## Upstream

NoFUD is forked from [Fud AI](https://github.com/apoorvdarshan/fud-ai) by Apoorv Darshan (MIT License).

## Exercise library

Exercise data, names, instructions, and images in the Workouts tab come from the [Free Exercise DB](https://github.com/yuhonas/free-exercise-db) and are bundled locally under `android/app/src/main/assets/exercises/` (see `LICENSE.md` and `README.md` in that folder).

## Barcode nutrition

Barcode product lookups are powered by the [Open Food Facts](https://world.openfoodfacts.org) database, queried live via its public API. Open Food Facts data is available under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/). NoFUD does not bundle the database — nutrition facts are fetched per scanned barcode.

## App icon

The NoFUD launcher icon is a temporary placeholder distinct from upstream Fud AI artwork (which was provided by the original project owner).

## Muscle glyphs

Muscle glyph assets (the muscle-filter icons in the Workouts tab) are cropped/rasterized derivatives of SVG muscle paths from [`react-muscle-highlighter`](https://github.com/soroojshehryar/react-muscle-highlighter) 1.2.0, MIT License. The generated app assets are bundled locally (`android/app/src/main/assets/muscle/`) and do not depend on the upstream repository at runtime.

Copyright (c) 2024 My Muscle Contributors — MIT License (see upstream package).
