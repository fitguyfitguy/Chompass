#!/usr/bin/env bash
# Copy the debug USDA offline index into src/main/assets for a release build.
# Run ONLY after grounded readiness checklist in docs/GROUNDED_ENTRY.md is green.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/android/app/src/debug/assets/usda"
DST="$ROOT/android/app/src/main/assets/usda"

if [[ "${1:-}" != "--confirm" ]]; then
  echo "Usage: $0 --confirm"
  echo "Copies $SRC → $DST for release/F-Droid packaging."
  echo "Do not run until GroundedEntryFeature readiness gates pass."
  exit 1
fi

test -f "$SRC/usda_foods.sqlite"
test -f "$SRC/usda_foods.manifest.json"
mkdir -p "$DST"
cp -f "$SRC/usda_foods.sqlite" "$DST/usda_foods.sqlite"
cp -f "$SRC/usda_foods.manifest.json" "$DST/usda_foods.manifest.json"
echo "Packaged USDA index into $DST"
python3 - <<'PY'
import json, hashlib, pathlib
p = pathlib.Path("android/app/src/main/assets/usda")
m = json.loads((p/"usda_foods.manifest.json").read_text())
digest = hashlib.sha256((p/"usda_foods.sqlite").read_bytes()).hexdigest()
assert digest == m["sha256"], (digest, m["sha256"])
print(m["food_count"], "foods ok", m["dataset_version"])
PY
