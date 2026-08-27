#!/usr/bin/env python3
"""Deterministic stratified subset (sex × BMI bucket). Writes gitignored data/."""

from __future__ import annotations

import argparse
import random
from collections import defaultdict
from pathlib import Path

from schema import DATA_DIR, Subject, read_jsonl, write_jsonl
from score import slice_key


def _bmi(s: Subject) -> float:
    return s.weight_kg / (s.height_cm / 100.0) ** 2


def sample(rows: list[Subject], n: int, seed: int) -> list[Subject]:
    buckets: dict[str, list[Subject]] = defaultdict(list)
    for s in rows:
        keys = slice_key(s.sex, _bmi(s), s.age)
        buckets[f"{keys['sex']}|{keys['bmi']}"].append(s)
    rng = random.Random(seed)
    for key in buckets:
        rng.shuffle(buckets[key])
    keys = sorted(buckets)
    out: list[Subject] = []
    i = 0
    while len(out) < n and keys:
        key = keys[i % len(keys)]
        if buckets[key]:
            out.append(buckets[key].pop())
        else:
            keys = [k for k in keys if buckets[k]]
            if not keys:
                break
            i = 0
            continue
        i += 1
    out.sort(key=lambda s: s.id)
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--n", type=int, default=200)
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()
    rows = list(read_jsonl(args.manifest))
    picked = sample(rows, args.n, args.seed)
    out = args.out or DATA_DIR / f"{args.manifest.stem}_n{len(picked)}_s{args.seed}.jsonl"
    write_jsonl(out, picked)
    print(f"wrote {len(picked)} / {len(rows)} -> {out}")


if __name__ == "__main__":
    main()
