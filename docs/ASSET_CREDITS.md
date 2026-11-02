# Asset Credits

## Upstream

Chompass is forked from [Fud AI](https://github.com/apoorvdarshan/fud-ai) by Apoorv Darshan (MIT License).

## Barcode nutrition

Barcode product lookups are powered by the [Open Food Facts](https://world.openfoodfacts.org) database, queried live via its public API. Open Food Facts data is available under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/). Chompass does not bundle the database: nutrition facts are fetched per scanned barcode. The Add Food sheet can also query Open Food Facts live by product/brand search string when that source is on (on by default; switch it off in Settings or on the search's databases heading; same ODbL terms, query only: never diary data). Product photos shown in the barcode review sheet are fetched per barcode from Open Food Facts' image CDN (`images.openfoodfacts.org`) and are CC BY-SA 3.0; Chompass displays them privately on your device with in-app attribution (the "Data & photo: Open Food Facts" line links to the product page) and never redistributes them.

## Food databases (offline search)

The Add Food sheet reads two bundled offline databases, each under its own license: results keep a provenance badge so the source is never ambiguous:

- **USDA FoodData Central** (Foundation + FNDDS subset, CC0 / public domain): `android/app/src/main/assets/usda/usda_foods.sqlite`, rebuilt from the pinned FoodData Central bulk CSV release by `scripts/build_usda_food_index.py`. Cite USDA when redistributing.
- **Swiss Food Composition Database**: `android/app/src/main/assets/swiss/swiss_foods.sqlite`, built by `scripts/build_swiss_food_index.py`. Source: Federal authorities of the Swiss Confederation, [naehrwertdaten.ch](https://naehrwertdaten.ch). The four localized CSV files (en/de/fr/it) are downloaded into `build/swiss-sfdc/` (gitignored) from the mirror bundle shipped by the [Food You](https://github.com/maksimowiczm/FoodYou) open-source project (GPL-3.0): the underlying data is the official federal dataset, not Food You code.

## App icon

The Chompass mark — a bitten compass ring with a two-facet needle pointing into the bite — is original artwork by fitguy, distinct from upstream Fud AI artwork (which was provided by the original project owner). Geometry, palette, and motion are transcribed from the design study [`brand/logo-system.html`](brand/logo-system.html), which is the reference for the mark.

`scripts/assets/build_icon_mark.py` emits the three mark SVGs (light, noir, mask) plus the BIMI logo `website/static/bimi/logo.svg`; `scripts/generate_icons.py` then emits the launcher density set, adaptive layers, splash marks, and the PWA/website/store-listing icons (via `resvg`). The script also writes a generated brand preview (`scripts/chompass_icon_master.png`); edit the SVG, not the PNG. The default brand is a cream `#FFF6EC` tile carrying the Ripened ring `#FFD9C2 → #FF9A6C → #E0685E → #8C2E4A` with the ink needle. Plum (the default accent) and Peach share that icon, so changing the default accent needs no asset regeneration.

The noir (glow) variant is the dark-ground asset: app dark scheme, night splash, and the site
header/OG mark. Tiles — launcher, favicon, PWA, BIMI — carry the light mark on cream.

On Android API 26+, home-screen icons use adaptive XML (`mipmap-anydpi-v26`) with a cream or theme-gradient background, a safe-zone foreground logo, and a monochrome layer so the system icon-shape mask and Material You themed icons apply. PWA and store listing icons remain pre-shaped squircles.

## Display numerals

Home, progress, and water numerals use [Doto](https://github.com/oliverlalan/Doto) by Óliver Lalan (The Doto Project Authors), licensed under the [SIL Open Font License 1.1](https://openfontlicense.org). The variable file is `android/app/src/main/res/font/doto.ttf`, taken from [google/fonts `ofl/doto`](https://github.com/google/fonts/tree/main/ofl/doto) (`Doto[ROND,wght].ttf`). Numerals pin wght 700 and ROND 0. Body text does not use this font.
