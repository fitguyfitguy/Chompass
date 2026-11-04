#!/usr/bin/env bash
# Verifies the vendored openScale copies in android/app/ match the pinned
# upstream commit, modulo the recorded deltas in third_party/openscale/SNAPSHOT.md.
# Usage: scripts/vendor_openscale_check.sh [--help]
# Exit: 0 all PASS, 1 any FAIL / manifest or tree error.
set -u

usage() {
  echo "usage: $(basename "$0")   # compare vendored files against the pinned upstream commit"
  echo "  manifest: third_party/openscale/SNAPSHOT.md"
  exit "${1:-0}"
}

case "${1:-}" in
  -h|--help) usage 0 ;;
  "") ;;
  *) usage 1 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SNAP="$ROOT/third_party/openscale/SNAPSHOT.md"
[ -f "$SNAP" ] || { echo "FAIL: missing manifest $SNAP"; exit 1; }

PIN=$(grep -m1 '^- Commit:' "$SNAP" | grep -oE '[0-9a-f]{40}')
[ -n "$PIN" ] || { echo "FAIL: no upstream commit pin in $SNAP"; exit 1; }
BASE="https://raw.githubusercontent.com/oliexdev/openScale/$PIN"
echo "pin: $PIN"

fail=0
rows=$(grep -cE '^\| android_app/' "$SNAP")
[ "$rows" -gt 0 ] || { echo "FAIL: no checksum rows in $SNAP"; exit 1; }

while IFS='|' read -r _ up delta sha _; do
  up=$(echo "$up" | xargs)
  delta=$(echo "$delta" | xargs)
  sha=$(echo "$sha" | xargs)
  [ -n "$up" ] || continue
  tree="$ROOT/android/${up#android_app/}"
  if [ ! -f "$tree" ]; then
    echo "FAIL $up (missing tree copy: $tree)"
    fail=$((fail + 1))
    continue
  fi
  tmp=$(mktemp)
  if ! curl -fsSL "$BASE/$up" -o "$tmp"; then
    echo "FAIL $up (upstream fetch failed)"
    rm -f "$tmp"
    fail=$((fail + 1))
    continue
  fi
  case "$delta" in
    R-IMPORT) sed -i 's|^import com\.health\.openscale\.R$|import app.chompass.R|' "$tmp" ;;
    -) ;;
    *) echo "FAIL $up (unknown delta: $delta)"; rm -f "$tmp"; fail=$((fail + 1)); continue ;;
  esac
  got=$(sha256sum "$tmp" | cut -d' ' -f1)
  rm -f "$tmp"
  if [ "$got" = "$sha" ]; then
    echo "PASS $up"
  else
    echo "FAIL $up (expected $sha, got $got)"
    fail=$((fail + 1))
  fi
done < <(grep -E '^\| android_app/' "$SNAP")

if [ "$fail" -gt 0 ]; then
  echo "RESULT: $fail FAIL"
  exit 1
fi
echo "RESULT: all $rows PASS"
