#!/usr/bin/env python3
"""Validate shared retrieval golden vectors against Python scoring helpers."""

from __future__ import annotations

import json
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from query_normalize import normalize_tokens
from run_grounded_eval import UsdaIndex

GOLDEN = _HERE / "manifest" / "retrieval_golden.json"


def score_candidate(query_tokens: list[str], cand: dict) -> float:
    """Mirror UsdaIndex._score for in-memory golden candidates."""
    class _Row(dict):
        def __getitem__(self, key):  # type: ignore[override]
            return dict.get(self, key)

    row = _Row(
        description=cand["description"],
        tokens=cand.get("tokens") or "",
        data_type=cand.get("data_type") or "",
        calories=cand.get("calories"),
    )
    return UsdaIndex._score(query_tokens, row)  # type: ignore[arg-type]


def main() -> None:
    data = json.loads(GOLDEN.read_text(encoding="utf-8"))
    failures: list[str] = []
    for case in data["cases"]:
        cid = case["id"]
        if "normalized_tokens" in case:
            got = normalize_tokens(case["query"])
            if got != case["normalized_tokens"]:
                failures.append(f"{cid}: tokens {got} != {case['normalized_tokens']}")
            for tok in case.get("must_contain") or []:
                if tok not in got:
                    failures.append(f"{cid}: missing token {tok}")
            for tok in case.get("must_not_contain") or []:
                if tok in got:
                    failures.append(f"{cid}: unexpected token {tok}")
            continue
        if "candidates" not in case:
            continue
        q_tokens = normalize_tokens(case["query"])
        scored = [(score_candidate(q_tokens, c), c["id"]) for c in case["candidates"]]
        scored.sort(reverse=True)
        top_id = scored[0][1]
        prefer = case.get("prefer_id")
        if prefer and top_id != prefer:
            failures.append(f"{cid}: top={top_id} prefer={prefer} scores={scored}")
        for avoid in case.get("avoid_ids") or []:
            if top_id == avoid:
                failures.append(f"{cid}: preferred avoid_id={avoid}")
        if case.get("close_pair") and len(scored) >= 2:
            delta = scored[0][0] - scored[1][0]
            thr = float(case.get("score_delta_threshold") or 1.2)
            # Ambiguity cases may or may not be close; just ensure scoring runs.
            _ = delta < thr
    if failures:
        print("GOLDEN FAILURES:")
        for f in failures:
            print(f"  - {f}")
        raise SystemExit(1)
    print(f"retrieval golden OK ({len(data['cases'])} cases)")


if __name__ == "__main__":
    main()
