#!/usr/bin/env bash
# Next free-tier benchmark batch after baseline_compact_free analysis.
#
# Baseline issue: openrouter/free routed ~17% of samples to
# nvidia/nemotron-3.5-content-safety:free which returns "User Safety: safe"
# instead of JSON (7/8 parse failures). Macro accuracy on parse-ok was strong
# (wmape ~4.9%). This batch pins models that returned valid JSON.
#
# Usage (from repo root):
#   bash benchmarks/food_accuracy/run_next_free_pins.sh
#   bash benchmarks/food_accuracy/run_next_free_pins.sh --quick   # 10 samples each
#   bash benchmarks/food_accuracy/run_next_free_pins.sh --prod    # also production_text on gemma

set -euo pipefail
cd "$(dirname "$0")/../.."

LIMIT=""
DO_PROD=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --quick) LIMIT="--limit 10"; shift ;;
    --prod) DO_PROD=1; shift ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

MANIFEST="benchmarks/food_accuracy/manifest/eval_text.jsonl"
OUT_ROOT="benchmarks/food_accuracy/results/next_free_pins"

# Pinned from baseline: 100% parse_ok on n>=2, exclude content-safety + truncated VL.
MODELS=(
  "google/gemma-4-26b-a4b-it:free"
  "cohere/north-mini-code:free"
  "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"
)

echo "=== Next free pins (compact) ==="
echo "manifest=$MANIFEST  out=$OUT_ROOT  limit=${LIMIT:-full}"
echo

for model in "${MODELS[@]}"; do
  slug="${model//\//_}"
  slug="${slug//:/_}"
  out="$OUT_ROOT/compact__${slug}"
  echo ">>> compact  $model  -> $out"
  uv run python benchmarks/food_accuracy/run_eval.py \
    --provider openrouter \
    --model "$model" \
    --prompt compact \
    --manifest "$MANIFEST" \
    $LIMIT \
    --out "$out"
  echo
done

if [[ "$DO_PROD" -eq 1 ]]; then
  model="google/gemma-4-26b-a4b-it:free"
  out="$OUT_ROOT/production_text__google_gemma-4-26b-a4b-it_free"
  echo ">>> production_text  $model  -> $out"
  uv run python benchmarks/food_accuracy/run_eval.py \
    --provider openrouter \
    --model "$model" \
    --prompt production_text \
    --manifest "$MANIFEST" \
    $LIMIT \
    --out "$out"
  echo
fi

echo "=== Compare vs baseline (compact free router) ==="
BASE="benchmarks/food_accuracy/results/baseline_compact_free/summary.csv"
for summary in "$OUT_ROOT"/compact__*/summary.csv; do
  [[ -f "$summary" ]] || continue
  echo "--- $(dirname "$summary") ---"
  uv run python benchmarks/food_accuracy/compare_runs.py "$BASE" "$summary" || true
  echo
done

echo "Done. Summaries under $OUT_ROOT/"
