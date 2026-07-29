#!/usr/bin/env python3
"""Post-hoc analysis over *existing* result artifacts — no API calls, no cost.

Re-scores predictions already stored in `results/*/samples.jsonl` under
transformations that need no new model output:

  A  per-model multiplicative bias calibration (leave-one-out cross-validated)
  B  self-consistency — median over repeated runs of the same model/prompt
  C  cross-model ensembling (median over N models)
  D  calibration + ensembling combined
  E  is the model's own `serving_size_grams` predictive of truth?
  F  does cross-model disagreement predict error (uncertainty trigger)?
  G  transfer — fit scale on one dataset/modality, apply to another

Every run listed in RUNS must cover the same manifest; the script intersects
sample ids and reports n. Results feed
`FOOD_ACCURACY_BENCHMARK_STATUS.md` § Post-hoc calibration & ensembling.

    uv run python docs/benchmarks/food_accuracy/posthoc_calibration.py
    uv run python docs/benchmarks/food_accuracy/posthoc_calibration.py --sections A C
"""

from __future__ import annotations

import argparse
import json
import statistics as st
from itertools import combinations
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "results"

MACROS = ["calories", "protein", "carbs", "fat"]
GT_KEY = {
    "calories": "calories",
    "protein": "protein_g",
    "carbs": "carbs_g",
    "fat": "fat_g",
}

# JFB-50 L0 `compact` runs (same 50 ids, image-only).
RUNS: dict[str, str] = {
    "gemini36_flash": "image_text_ab/l0_gemini36_flash",
    "gpt4o_mini": "image_text_ab/l0_gpt4o_mini",
    "gpt5_mini": "image_text_ab/l0_gpt5_mini",
    "gpt5_nano": "image_text_ab/l0_gpt5_nano",
    "claude3_haiku": "image_text_ab/l0_claude3_haiku",
    "claude_haiku45": "image_text_ab/l0_claude_haiku45",
    "qwen35_flash": "image_text_ab/l0_qwen35_flash",
    "gemma_free": "image_prompt_ab_gemma/compact",
    "nofud_free": "baseline_image_nofud_free_compact_cold",
    # three independent runs of one model+prompt -> self-consistency material
    "flashlite_r1": "image_text_ab/l0_gemini35_flash_lite",
    "flashlite_r2": "scale_ref_ab/jfb_compact_rerun",
    "flashlite_r3": "clarify_ab/jfb_compact",
}

REPEATS = ["flashlite_r1", "flashlite_r2", "flashlite_r3"]

# Cross-dataset / cross-modality transfer check (section G).
TRANSFER: dict[str, str] = {
    "JFB50 gemma_free (image)": "image_prompt_ab_gemma/compact",
    "JFB50 flashlite (image)": "image_text_ab/l0_gemini35_flash_lite",
    "N5k15 gemma_free (image)": "n5k_cursory_gemma_compact",
    "TEXT42 gemma_free": "prompt_ab_gemma/compact",
    "TEXT42 flashlite": "quick_gemini35_flash_lite_text",
}


def load(rel: str) -> dict[str, dict]:
    """Read a run's per-sample records, keeping only parseable full-macro rows."""
    path = ROOT / rel / "samples.jsonl"
    out: dict[str, dict] = {}
    for line in path.read_text().splitlines():
        if not line.strip():
            continue
        rec = json.loads(line)
        if not rec.get("parse_ok"):
            continue
        pred = rec["prediction"]
        if any(pred.get(m) is None for m in MACROS):
            continue
        out[rec["id"]] = {
            "pred": {m: float(pred[m]) for m in MACROS},
            "gt": {m: float(rec["ground_truth"][GT_KEY[m]]) for m in MACROS},
            "grams": pred.get("serving_size_grams"),
        }
    return out


def metrics(preds, gts, ids) -> tuple[float, float]:
    """WMAPE over the four macros, and the within-±20%-calories rate."""
    num = den = 0.0
    hits = 0
    for i in ids:
        p, g = preds[i], gts[i]
        for m in MACROS:
            num += abs(p[m] - g[m])
            den += abs(g[m])
        if g["calories"] and abs(p["calories"] - g["calories"]) / g["calories"] <= 0.20:
            hits += 1
    return num / den, hits / len(ids)


def fit_scale(preds, gts, ids, macro: str) -> float:
    """Scale minimising sum|s*pred - gt| — the prediction-weighted median ratio."""
    ratios = sorted(
        (gts[i][macro] / preds[i][macro], preds[i][macro])
        for i in ids
        if preds[i][macro] > 0
    )
    total = sum(w for _, w in ratios)
    acc = 0.0
    for ratio, weight in ratios:
        acc += weight
        if acc >= total / 2:
            return ratio
    return 1.0


def loo_calibrated(preds, gts, ids):
    """Leave-one-out: each sample is scaled by a factor fitted on the others."""
    out = {}
    for i in ids:
        rest = [j for j in ids if j != i]
        out[i] = {m: preds[i][m] * fit_scale(preds, gts, rest, m) for m in MACROS}
    return out


def ensemble(data, names, ids, agg=st.median):
    return {
        i: {m: agg([data[n][i]["pred"][m] for n in names]) for m in MACROS}
        for i in ids
    }


def corr(xs, ys) -> float:
    mx, my = sum(xs) / len(xs), sum(ys) / len(ys)
    cov = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    vx = sum((x - mx) ** 2 for x in xs) ** 0.5
    vy = sum((y - my) ** 2 for y in ys) ** 0.5
    return cov / (vx * vy) if vx and vy else 0.0


def show(label: str, preds, gts, ids):
    w, a = metrics(preds, gts, ids)
    print(f"  {label:<56} WMAPE {w * 100:6.2f}%   ±20% {a * 100:5.1f}%")
    return w, a


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--sections", nargs="*", default=list("ABCDEFG"))
    args = ap.parse_args()
    sections = {s.upper() for s in args.sections}

    data = {k: load(v) for k, v in RUNS.items()}
    ids = sorted(set.intersection(*(set(d) for d in data.values())))
    gts = {i: data["gemini36_flash"][i]["gt"] for i in ids}
    preds = {n: {i: data[n][i]["pred"] for i in ids} for n in RUNS}
    print(f"n common samples = {len(ids)}\n")

    print("=== BASELINE (single call, as published) ===")
    base = {n: show(n, preds[n], gts, ids) for n in RUNS}

    if "A" in sections:
        print("\n=== A) PER-MODEL BIAS CALIBRATION (leave-one-out CV) ===")
        for n in RUNS:
            s = {m: fit_scale(preds[n], gts, ids, m) for m in MACROS}
            w, a = metrics(loo_calibrated(preds[n], gts, ids), gts, ids)
            print(
                f"  {n:<16} kcal={s['calories']:.3f} pro={s['protein']:.3f} "
                f"carb={s['carbs']:.3f} fat={s['fat']:.3f}  ->  "
                f"WMAPE {w * 100:6.2f}% ({(w - base[n][0]) * 100:+.2f}pp)  "
                f"±20% {a * 100:5.1f}% ({(a - base[n][1]) * 100:+.1f}pp)"
            )

    if "B" in sections:
        print("\n=== B) SELF-CONSISTENCY (repeat runs, same model+prompt) ===")
        for n in REPEATS:
            show(f"single run {n}", preds[n], gts, ids)
        show("median of repeats", ensemble(data, REPEATS, ids), gts, ids)
        show("mean of repeats", ensemble(data, REPEATS, ids, lambda v: sum(v) / len(v)), gts, ids)

    pool = [n for n in RUNS if n not in REPEATS[1:]]
    best_combos = []
    if "C" in sections:
        print("\n=== C) CROSS-MODEL ENSEMBLE (median) ===")
        for k in (2, 3, 4):
            ranked = sorted(
                (metrics(ensemble(data, list(c), ids), gts, ids), c)
                for c in combinations(pool, k)
            )
            print(f"  -- best {k}-model medians --")
            for (w, a), c in ranked[:3]:
                print(f"  {'+'.join(c):<56} WMAPE {w * 100:6.2f}%   ±20% {a * 100:5.1f}%")
            best_combos.append(ranked[0][1])

    if "D" in sections and best_combos:
        print("\n=== D) CALIBRATION + ENSEMBLE ===")
        for combo in best_combos:
            show(
                "median-then-calibrate " + "+".join(combo),
                loo_calibrated(ensemble(data, list(combo), ids), gts, ids),
                gts,
                ids,
            )

    if "E" in sections:
        print("\n=== E) does predicted serving_size_grams track truth? ===")
        for n in RUNS:
            pairs = [
                (float(data[n][i]["grams"]), gts[i]["calories"])
                for i in ids
                if data[n][i]["grams"]
            ]
            if len(pairs) < 20:
                print(f"  {n:<16} grams present {len(pairs)}/{len(ids)} — skip")
                continue
            r = corr([x for x, _ in pairs], [y for _, y in pairs])
            print(f"  {n:<16} grams present {len(pairs)}/{len(ids)}  corr(grams, GT kcal) = {r:+.3f}")

    if "F" in sections:
        print("\n=== F) is cross-model disagreement an uncertainty trigger? ===")
        trio = ["gemini36_flash", "gpt4o_mini", "gemma_free"]
        rows = []
        for i in ids:
            vals = [data[n][i]["pred"]["calories"] for n in trio]
            med = st.median(vals)
            spread = (max(vals) - min(vals)) / med if med else 0.0
            err = abs(med - gts[i]["calories"]) / gts[i]["calories"] if gts[i]["calories"] else 0.0
            rows.append((spread, err))
        rows.sort()
        half = len(rows) // 2
        for label, part in (("low-disagreement ", rows[:half]), ("high-disagreement", rows[half:])):
            mean_err = sum(e for _, e in part) / len(part) * 100
            hit = sum(1 for _, e in part if e <= 0.2) / len(part) * 100
            print(f"  {label} half: mean |kcal err| {mean_err:5.1f}%   ±20% hit {hit:5.1f}%")
        print(f"  corr(disagreement, median abs kcal error) = {corr([s for s, _ in rows], [e for _, e in rows]):+.3f}")

    if "G" in sections:
        print("\n=== G) TRANSFER — fit scale on A, apply to B ===")
        sets = {k: load(v) for k, v in TRANSFER.items() if (ROOT / v / "samples.jsonl").exists()}
        scales = {}
        print(f"  {'split':<28} {'n':>3}  {'kcal':>6} {'pro':>6} {'carb':>6} {'fat':>6}   baseline")
        for k, d in sets.items():
            kid = sorted(d)
            p = {i: d[i]["pred"] for i in kid}
            g = {i: d[i]["gt"] for i in kid}
            s = {m: fit_scale(p, g, kid, m) for m in MACROS}
            scales[k] = s
            w, a = metrics(p, g, kid)
            print(
                f"  {k:<28} {len(kid):>3}  {s['calories']:>6.3f} {s['protein']:>6.3f} "
                f"{s['carbs']:>6.3f} {s['fat']:>6.3f}   {w * 100:5.2f}% / ±20% {a * 100:.0f}%"
            )
        pairs = [
            ("JFB50 gemma_free (image)", "N5k15 gemma_free (image)"),
            ("N5k15 gemma_free (image)", "JFB50 gemma_free (image)"),
            ("JFB50 gemma_free (image)", "TEXT42 gemma_free"),
        ]
        for a_name, b_name in pairs:
            if a_name not in scales or b_name not in sets:
                continue
            d = sets[b_name]
            kid = sorted(d)
            g = {i: d[i]["gt"] for i in kid}
            bw, bh = metrics({i: d[i]["pred"] for i in kid}, g, kid)
            moved = {i: {m: d[i]["pred"][m] * scales[a_name][m] for m in MACROS} for i in kid}
            w, h = metrics(moved, g, kid)
            print(f"  fit[{a_name}] -> {b_name}")
            print(
                f"      WMAPE {bw * 100:5.2f}% -> {w * 100:5.2f}% ({(w - bw) * 100:+.2f}pp)   "
                f"±20% {bh * 100:.0f}% -> {h * 100:.0f}% ({(h - bh) * 100:+.0f}pp)"
            )


if __name__ == "__main__":
    main()
