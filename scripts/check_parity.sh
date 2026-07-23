#!/usr/bin/env bash
# Cross-app parity gate: PWA tests + typecheck + JSON Schema validation of
# committed fixtures. Android unit tests (including shared formula goldens) run
# via package_release.sh / `./gradlew test`.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

run_in_devenv() {
  local cmd="$1"
  if [[ -n "${DEVENV_IN_SHELL:-}" ]]; then
    bash -lc "$cmd"
  elif command -v devenv >/dev/null 2>&1; then
    devenv shell bash -lc "$cmd"
  else
    bash -lc "$cmd"
  fi
}

echo "==> PWA unit tests (nofud-core + lib parity)"
run_in_devenv 'cd web && node --test app/src/lib/nofud-core/__tests__/*.test.js app/src/lib/__tests__/*.test.js'

echo "==> PWA typecheck"
run_in_devenv 'cd web && tsc --checkJs --noEmit -p tsconfig.json'

echo "==> Validate parity fixtures against contracts/ JSON Schemas"
uv run --with jsonschema python "$ROOT/scripts/validate_parity_contracts.py"

echo "==> Parity checks passed"
