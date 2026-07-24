#!/usr/bin/env bash
# Stage-0 food-accuracy smoke: no network, deterministic stub + golden retrieval.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== stub single-shot smoke =="
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/manifest/eval_text.jsonl \
  --prompt compact \
  --provider stub \
  --limit 5 \
  --out /tmp/chompass_food_acc_smoke

echo "== grounded metrics on example trace =="
uv run python docs/benchmarks/food_accuracy/grounded_metrics.py \
  --trace docs/benchmarks/food_accuracy/manifest/grounded_trace_example.jsonl \
  --out /tmp/chompass_grounded_metrics_smoke.json

echo "== retrieval golden vectors =="
uv run python docs/benchmarks/food_accuracy/check_retrieval_golden.py

echo "== query normalize unit checks =="
uv run python -c "
import sys
sys.path.insert(0, 'docs/benchmarks/food_accuracy')
from query_normalize import normalize_tokens, normalize_query
assert '150' not in normalize_tokens('Chicken breast, roasted, 150 g')
assert 'yogurt' in normalize_tokens('plain yoghurt')
assert normalize_query('2 tbsp peanut butter') == 'peanut butter'
print('query_normalize ok')
"

echo "== clarify oracle unit checks =="
uv run python -c "
import sys
sys.path.insert(0, 'docs/benchmarks/food_accuracy')
from clarify import FAT_LEXICON, clarify_answer_block, derive_clarify_fields, portion_bucket
from schema import load_manifest
assert portion_bucket(149) == 'small'
assert portion_bucket(150) == 'regular'
assert portion_bucket(350) == 'large'
assert portion_bucket(600) == 'restaurant-size'
assert FAT_LEXICON.search('olive oil')
assert FAT_LEXICON.search('ranch dressing')
assert not FAT_LEXICON.search('strawberries')
samples = {s.id: s for s in load_manifest('docs/benchmarks/food_accuracy/manifest/clarify_fixture.jsonl')}
# Fixture extras must match what derive_clarify_fields would produce from GT.
s1 = samples['clar-001']
derived = derive_clarify_fields(s1)
assert derived['clarify_portion'] == s1.extra['clarify_portion'], derived
block = clarify_answer_block(s1, portion=True, fat=True)
assert '350 g' in block and 'olive oil dressing' in block, block
assert 'stated_amounts' == samples['clar-003'].extra['clarify_portion']['kind']
assert 'amounts were 2 slices pepperoni pizza' in clarify_answer_block(samples['clar-003'], portion=True)
assert 'clarify_fat' not in samples['clar-005'].extra
assert clarify_answer_block(samples['clar-005'], fat=True) == ''
print('clarify oracle ok')
"

echo "== stub clarify oracle-injection smoke =="
uv run python docs/benchmarks/food_accuracy/run_eval.py \
  --manifest docs/benchmarks/food_accuracy/manifest/clarify_fixture.jsonl \
  --prompt compact_clarify_both \
  --provider stub \
  --out /tmp/chompass_clarify_smoke

echo "== stub two-stage clarify smoke =="
uv run python docs/benchmarks/food_accuracy/run_clarify_eval.py \
  --manifest docs/benchmarks/food_accuracy/manifest/clarify_fixture.jsonl \
  --provider stub \
  --out /tmp/chompass_clarify_twostage_smoke
uv run python -c "
import json
summary = json.load(open('/tmp/chompass_clarify_twostage_smoke/summary.json'))
ask = float(summary['ask_rate'])
assert 0.0 < ask < 1.0, f'stub ask_rate should mix branches, got {ask}'
assert summary['aggregate_final']['parse_ok_rate'] == 1.0, summary
print('two-stage clarify ok (ask_rate=%s)' % summary['ask_rate'])
"

echo "Food accuracy smoke OK"
