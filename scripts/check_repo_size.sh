#!/usr/bin/env bash
# Repository size guard.
#
# 1. Blob check: reject new blobs larger than 1 MiB outside the image/data
#    allowlist (published screenshots, store listing images, the USDA sqlite,
#    JVM preview refs). Catches accidental APK/zip/db/model commits before they
#    are immortalized in history — Codeberg charges every fork the full
#    history, and a fork is born at the repo's size against the contributor's
#    own 750 MiB quota (see PR #140 / the 2026-11 history rewrite).
# 2. Budget check (opt-in via --budget-mib): fail when the packed repository
#    exceeds the budget so growth is caught at CI, not after a fork breaks.
#
# Used by scripts/git-hooks/pre-push and .codeberg/workflows/repo-size.yml.
#
# Usage: check_repo_size.sh [--range <rev-range>] [--budget-mib <n>]
set -euo pipefail

RANGE=""
BUDGET_MIB=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --range) RANGE="${2:?--range needs a value}"; shift ;;
    --budget-mib) BUDGET_MIB="${2:?--budget-mib needs a value}"; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

LIMIT_BYTES=$((1024 * 1024))
fail=0

if [[ -n "$RANGE" ]]; then
  # New objects introduced by the range. Paths allowed to exceed the per-blob
  # limit: published screenshots, store listing images, the offline food
  # databases (USDA + Swiss, main and debug variants), the vendored zxing
  # barcode reader, the brand icon source, and JVM screenshotTest references.
  offenders="$(
    git rev-list --objects "$RANGE" 2>/dev/null \
      | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' 2>/dev/null \
      | awk -v limit="$LIMIT_BYTES" \
          '$1 == "blob" && $3 > limit && ($4 == "" || $4 !~ /^(docs\/screenshots\/|metadata\/en-US\/images\/|android\/app\/src\/(main|debug)\/assets\/(usda|swiss)\/|web\/app\/vendor\/zxing\/|appicon\.png|android\/app\/src\/screenshotTest(Debug|PlayDebug)\/reference\/)/) \
           { printf "%7.2f MiB  %s\n", $3 / 1048576, ($4 == "" ? "(anonymous blob)" : $4) }'
  )"
  if [[ -n "$offenders" ]]; then
    echo "check_repo_size: blobs over $(( LIMIT_BYTES / 1024 )) KiB outside the allowlist in $RANGE:" >&2
    printf '  %s\n' "$offenders" >&2
    echo "Allowed heavy paths: docs/screenshots/, metadata/en-US/images/, usda+swiss sqlite, zxing wasm, appicon, screenshotTest refs." >&2
    fail=1
  fi
fi

if [[ -n "$BUDGET_MIB" ]]; then
  # Loose + packed object size (fresh CI clones arrive packed; local repos
  # may still have loose objects).
  size_total_kib="$(git count-objects -v | awk '/^(size|size-pack):/ { s += $2 } END { print s }')"
  pack_mib=$(( size_total_kib / 1024 ))
  echo "packed repository size: ${pack_mib} MiB (budget ${BUDGET_MIB} MiB)"
  if (( pack_mib > BUDGET_MIB )); then
    echo "check_repo_size: pack exceeds the ${BUDGET_MIB} MiB budget — trim history (binary churn) before pushing." >&2
    fail=1
  fi
fi

exit "$fail"
