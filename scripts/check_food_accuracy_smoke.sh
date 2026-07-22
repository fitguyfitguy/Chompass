#!/usr/bin/env bash
# Stage-0 food-accuracy smoke: no network, deterministic stub + golden retrieval.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== stub single-shot smoke =="
uv run python benchmarks/food_accuracy/run_eval.py \
  --manifest benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt compact \
  --provider stub \
  --limit 5 \
  --out /tmp/nofud_food_acc_smoke

echo "== grounded metrics on example trace =="
uv run python benchmarks/food_accuracy/grounded_metrics.py \
  --trace benchmarks/food_accuracy/manifest/grounded_trace_example.jsonl \
  --out /tmp/nofud_grounded_metrics_smoke.json

echo "== retrieval golden vectors =="
uv run python benchmarks/food_accuracy/check_retrieval_golden.py

echo "== query normalize unit checks =="
uv run python -c "
from benchmarks.food_accuracy.query_normalize import normalize_tokens, normalize_query
assert '150' not in normalize_tokens('Chicken breast, roasted, 150 g')
assert 'yogurt' in normalize_tokens('plain yoghurt')
assert normalize_query('2 tbsp peanut butter') == 'peanut butter'
print('query_normalize ok')
"

echo "Food accuracy smoke OK"
