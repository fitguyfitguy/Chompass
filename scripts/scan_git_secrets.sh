#!/usr/bin/env bash
# Full-history secret / PII scan for NoFUD (gitleaks + trufflehog + path checks).
#
# Usage:
#   ./scripts/scan_git_secrets.sh
#   ./scripts/scan_git_secrets.sh --outdir /tmp/nofud-secret-scan
#   ./scripts/scan_git_secrets.sh --deep   # also run slow all-history content greps
#
# Requires: nix (ephemeral nixpkgs#gitleaks / #trufflehog), uv (for JSON count).
# Default OUTDIR: android/build/secret-scan/<timestamp>/ (gitignored via android/build/).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTDIR=""
DEEP=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --outdir)
      OUTDIR="${2:?--outdir requires a path}"
      shift 2
      ;;
    --deep)
      DEEP=1
      shift
      ;;
    -h|--help)
      sed -n '2,14p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$OUTDIR" ]]; then
  OUTDIR="$ROOT/android/build/secret-scan/$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$OUTDIR"
cd "$ROOT"

echo "==> Report dir: $OUTDIR"

echo "==> gitleaks (all refs)"
nix shell nixpkgs#gitleaks -c gitleaks detect \
  --source "$ROOT" \
  --log-opts="--all" \
  --no-banner \
  --config "$ROOT/.gitleaks.toml" \
  --report-path "$OUTDIR/gitleaks.json" \
  --report-format json \
  --exit-code 0 \
  >"$OUTDIR/gitleaks.log" 2>&1

GITLEAKS_COUNT=0
if [[ -f "$OUTDIR/gitleaks.json" ]]; then
  GITLEAKS_COUNT="$(
    uv run python -c "import json; d=json.load(open(r'$OUTDIR/gitleaks.json')); print(len(d) if isinstance(d, list) else 0)"
  )"
else
  echo "WARN: gitleaks.json missing; see $OUTDIR/gitleaks.log" >&2
fi

echo "==> trufflehog (verified only)"
nix shell nixpkgs#trufflehog -c trufflehog git "file://$ROOT" \
  --only-verified \
  --json \
  >"$OUTDIR/trufflehog.json" 2>"$OUTDIR/trufflehog.err" || true

TRUFFLE_VERIFIED=0
if rg -q 'verified_secrets":[0-9]+' "$OUTDIR/trufflehog.err" 2>/dev/null; then
  TRUFFLE_VERIFIED="$(rg -o 'verified_secrets":[0-9]+' "$OUTDIR/trufflehog.err" | head -1 | cut -d: -f2)"
fi

echo "==> sensitive filename history"
{
  echo "=== sensitive filenames ==="
  for p in \
    'keystore.properties' 'secrets.properties' '.env.local' '.env' \
    '*.jks' '*.keystore' 'Fud-Food-Diary*' 'FudAI-Weight*' \
    'SHA256SUMS' 'NoFUD-*.apk' 'diary-surrogate*'
  do
    hits="$(git log --all --full-history --oneline -- "$p" 2>/dev/null || true)"
    if [[ -n "$hits" ]]; then
      echo "HIT: $p"
      echo "$hits"
    else
      echo "clean: $p"
    fi
  done
} >"$OUTDIR/filenames.txt"
FILENAME_HITS="$(rg -c '^HIT:' "$OUTDIR/filenames.txt" || true)"
FILENAME_HITS="${FILENAME_HITS:-0}"

PATTERN_SUSPECT=0
if [[ "$DEEP" -eq 1 ]]; then
  echo "==> deep content patterns (all commits; slow)"
  {
    echo "=== content patterns (deduped path:line) ==="
    patterns=(
      'AIza[0-9A-Za-z_-]{20,}'
      'sk-or-[A-Za-z0-9_-]{20,}'
      'glpat-[A-Za-z0-9_-]{12,}'
      'ghs_[A-Za-z0-9_]{20,}'
      'ghp_[A-Za-z0-9_]{20,}'
      'CODEBERG_TOKEN=[^[:space:]'\''\"]{8,}'
      'OPENROUTER_TOKEN=[^[:space:]'\''\"]{8,}'
      'GITLAB_TOKEN=[^[:space:]'\''\"]{8,}'
      '-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----'
    )
    for pat in "${patterns[@]}"; do
      echo "-- $pat"
      # shellcheck disable=SC2046
      git grep -n -E -I "$pat" $(git rev-list --all) 2>/dev/null \
        | sed 's/^[0-9a-f]*://' \
        | sort -u \
        | head -30 \
        || true
    done
  } >"$OUTDIR/patterns.txt"

  if rg -q 'AIza[0-9A-Za-z_-]{20,}|sk-or-[A-Za-z0-9_-]{20,}|gh[ps]_[A-Za-z0-9_]{20,}|-----BEGIN' "$OUTDIR/patterns.txt" 2>/dev/null; then
    PATTERN_SUSPECT=1
  fi
  # Real GitLab PAT shape (placeholder is glpat-... with dots — excluded by class)
  if rg -q 'glpat-[A-Za-z0-9_-]{12,}' "$OUTDIR/patterns.txt" 2>/dev/null; then
    PATTERN_SUSPECT=1
  fi
  # Assigned tokens that are not known doc placeholders
  if rg -q "CODEBERG_TOKEN=['\"][^'\"]{12,}['\"]" "$OUTDIR/patterns.txt" 2>/dev/null \
    && ! rg -q "paste-token-here|your-token" "$OUTDIR/patterns.txt" 2>/dev/null; then
    PATTERN_SUSPECT=1
  fi
else
  echo "==> skipping deep content greps (pass --deep to enable)"
  echo "skipped (--deep not set)" >"$OUTDIR/patterns.txt"
fi

SUMMARY="$OUTDIR/SUMMARY.txt"
{
  echo "NoFUD secret scan summary"
  echo "outdir: $OUTDIR"
  echo "gitleaks_findings: $GITLEAKS_COUNT"
  echo "trufflehog_verified: $TRUFFLE_VERIFIED"
  echo "sensitive_filename_hits: $FILENAME_HITS"
  echo "pattern_suspect: $PATTERN_SUSPECT"
  echo "deep: $DEEP"
  echo ""
  if [[ "$GITLEAKS_COUNT" == "0" && "$TRUFFLE_VERIFIED" == "0" && "$FILENAME_HITS" == "0" && "$PATTERN_SUSPECT" == "0" ]]; then
    echo "VERDICT: clean"
  else
    echo "VERDICT: review required — inspect gitleaks.json / patterns.txt / filenames.txt"
  fi
} | tee "$SUMMARY"

echo ""
echo "Artifacts under $OUTDIR"

if [[ "$GITLEAKS_COUNT" != "0" || "$TRUFFLE_VERIFIED" != "0" || "$FILENAME_HITS" != "0" || "$PATTERN_SUSPECT" != "0" ]]; then
  exit 1
fi
exit 0
