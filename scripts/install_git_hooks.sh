#!/usr/bin/env bash
# Point this clone at scripts/git-hooks (commit-msg rejects Cursor/AI trailers)
# and pin the Codeberg maintainer git identity for Chompass commits.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOKS_DIR="$ROOT/scripts/git-hooks"

if [[ ! -d "$ROOT/.git" ]]; then
  echo "Not a git checkout: $ROOT" >&2
  exit 1
fi

chmod +x "$HOOKS_DIR"/* 2>/dev/null || true
git -C "$ROOT" config core.hooksPath scripts/git-hooks
git -C "$ROOT" config user.name fitguy
git -C "$ROOT" config user.email fit.guy@mailfence.com
echo "core.hooksPath -> scripts/git-hooks"
echo "user.name/email -> fitguy <fit.guy@mailfence.com>"
