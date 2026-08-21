# Vendored: zxing-wasm (reader-only build)

- Package: `zxing-wasm` v3.1.2 (https://github.com/Sec-ant/zxing-wasm)
- License: MIT (wrapper) / Apache-2.0 (embedded zxing-cpp)
- Files (downloaded 2026-07-23 from jsDelivr, pinned version):
  - `zxing-reader.js` — `https://cdn.jsdelivr.net/npm/zxing-wasm@3.1.2/dist/iife/reader/index.js`
    - sha256 `bddd65fa7160b050111eaa5417f238715bd31e7d5703211d6e5b15576d3a3532`
  - `zxing_reader.wasm` — `https://cdn.jsdelivr.net/npm/zxing-wasm@3.1.2/dist/reader/zxing_reader.wasm`
    - sha256 `0e8d688d71932ebb6b8b33f700d43d3cb997f59ed9cab3c05102d7f10288a392`

Why the IIFE build: the ES build (`dist/es/reader/`) is not self-contained (imports
`../share.js`, `../bindings/*`), which would mean vendoring a file tree and pulling
minified code into `tsc --checkJs` via the import graph. The IIFE build is a single
file exposing the `ZXingWASM` global (`readBarcodes`, `prepareZXingModule`), loaded
lazily via a `<script>` tag by `src/lib/barcode-detect.js` only when the native
BarcodeDetector API is missing or broken.

Single-threaded build: no SharedArrayBuffer, no COOP/COEP headers required.

To upgrade: bump the version in both URLs, re-download, update the hashes above,
and re-verify the fallback path on a browser without working BarcodeDetector
(Firefox desktop is the easiest).
