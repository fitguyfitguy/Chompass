#!/usr/bin/env python3
"""Depth/volume estimation experiment against Nutrition5k ground-truth mass.

Standalone harness script (not routed through run_eval.py's prompt/provider
machinery — this is geometric/vision analysis, not an LLM call). Answers, in
order:

1. Oracle: does a food-height volume PROXY from the *true* RealSense depth_raw
   map predict mass_g, once a single global scale constant is fit across the
   dish set? (Ceiling case.)
2. Monocular: how much correlation is lost using a local, camera-only
   monocular depth model (Depth Anything V2 Small) instead of true depth,
   per-image aligned to the oracle depth via a least-squares affine fit?
   (Realistic phone-camera case.)
3. Video (if a side-angle clip was fetched): extract a few frames from the
   turntable clip and check how much a single-view relative-depth "bulge"
   proxy varies across viewing angles of the same static dish. This does NOT
   produce a calibrated mass estimate (no published camera geometry for the
   side cameras) — it is a directional signal only, reported as a
   coefficient of variation.

No camera intrinsics are published for this dataset (checked: no calibration
file under metadata/ or scripts/ in the GCS bucket), and the raw depth_raw
values observed (~3000-4000 raw units for the table plane) don't match a
plausible close-range overhead rig under any nominal RealSense mm-per-unit
assumption we tried — so this script does NOT attempt to convert depth to
absolute real-world volume (cm^3) or apply a physical density constant.
Instead it computes a volume PROXY (sum of pixel "height above table" times
depth^2, which is proportional to true volume up to an unknown, but per-rig
*constant*, camera-intrinsic factor) and fits one global linear scale k
against true mass_g across the whole dish set (mass ~= k * proxy). This is
the honest thing to measure without published calibration: does depth-derived
volume correlate with mass at all, not "can we hit absolute grams." See
docs/UNCERTAINTY_DRIVEN_ENTRY.md "New candidates" for context.

Usage:
    uv run --with torch --with transformers --with pillow --with numpy \\
        python docs/benchmarks/food_accuracy/depth_volume_eval.py \\
        --manifest docs/benchmarks/food_accuracy/data/manifests/n5k_depth.jsonl \\
        --out docs/benchmarks/food_accuracy/results/depth_volume/n5k
"""

from __future__ import annotations

import argparse
import csv
import json
import shutil
import subprocess
import sys
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
from PIL import Image

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from schema import DOCS_ROOT, ROOT, Sample, load_manifest

TABLE_DEPTH_PERCENTILE = 90.0
VIDEO_FRAME_FPS = 2


@dataclass
class DishResult:
    id: str
    mass_g: float
    oracle_volume_proxy: float | None
    mono_volume_proxy: float | None
    video_frames: int | None
    video_bulge_cv: float | None
    error: str | None = None
    # Filled in after the global scale fit (second pass).
    oracle_mass_g: float | None = None
    oracle_abs_err: float | None = None
    oracle_ape: float | None = None
    mono_mass_g: float | None = None
    mono_abs_err: float | None = None
    mono_ape: float | None = None


def resolve(rel_path: str) -> Path:
    # Downloaded Nutrition5k manifests store paths relative to docs/ (pre-move
    # layout) — mirrors Sample.resolved_image_path()'s fallback.
    path = Path(rel_path)
    if path.is_absolute():
        return path
    repo_relative = ROOT / path
    if repo_relative.exists():
        return repo_relative
    return DOCS_ROOT / path


def load_depth_mm(path: Path) -> np.ndarray:
    arr = np.array(Image.open(path))
    if arr.dtype != np.uint16:
        raise ValueError(f"Expected 16-bit depth PNG, got {arr.dtype} at {path}")
    return arr.astype(np.float64)


def volume_proxy(depth: np.ndarray) -> float:
    """Sum of pixel "height above table" times depth^2.

    Proportional to true volume up to an unknown but constant (same fixed
    overhead rig for every dish) camera-intrinsic factor. Not a physical
    volume — see module docstring. `depth` may be in any consistent unit
    (raw sensor units or oracle-calibrated monocular units); only relative
    consistency across dishes from the *same* camera matters, since the
    global scale fit absorbs whatever unit/intrinsic constant is in play.
    """
    valid = depth > 0
    if not np.any(valid):
        return 0.0
    table_depth = float(np.percentile(depth[valid], TABLE_DEPTH_PERCENTILE))
    height = np.where(valid, np.clip(table_depth - depth, 0, None), 0.0)
    return float(np.sum(height * np.square(depth)))


def fit_global_scale(proxies: list[float], masses: list[float]) -> float:
    """Least-squares scale k (through the origin) for mass ~= k * proxy."""
    p = np.array(proxies)
    m = np.array(masses)
    denom = float(np.sum(p * p))
    return float(np.sum(p * m) / denom) if denom else 0.0


def calibrate_monocular(oracle_depth_mm: np.ndarray, mono_relative: np.ndarray) -> np.ndarray:
    """Least-squares affine fit: oracle_depth ~= a * mono_relative + b."""
    valid = oracle_depth_mm > 0
    x = mono_relative[valid].ravel()
    y = oracle_depth_mm[valid].ravel()
    a, b = np.polyfit(x, y, 1)
    calibrated = a * mono_relative + b
    return calibrated


def bulge_proxy(relative_depth: np.ndarray) -> float:
    """Directional-only relative-height signal for a single uncalibrated view."""
    background = np.percentile(relative_depth, 100 - TABLE_DEPTH_PERCENTILE)
    # Depth-Anything outputs larger = closer, so "bulge" is values above background.
    bulge = np.clip(relative_depth - background, 0, None)
    return float(np.mean(bulge))


def extract_video_frames(video_path: Path, out_dir: Path, fps: int = VIDEO_FRAME_FPS) -> list[Path]:
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        return []
    out_dir.mkdir(parents=True, exist_ok=True)
    pattern = str(out_dir / "frame_%03d.jpg")
    cmd = [ffmpeg, "-y", "-i", str(video_path), "-vf", f"fps={fps}", pattern]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"WARN: ffmpeg failed for {video_path}: {result.stderr[-300:]}", file=sys.stderr)
        return []
    return sorted(out_dir.glob("frame_*.jpg"))


def run(manifest_path: str, out_dir: str, limit: int | None) -> None:
    samples = load_manifest(manifest_path, limit=limit)
    depth_samples = [s for s in samples if s.extra.get("depth_path")]
    if not depth_samples:
        raise SystemExit(f"No samples with depth_path in {manifest_path}")

    print(f"Loading Depth Anything V2 Small (monocular depth model)...")
    import torch
    from transformers import pipeline

    device = 0 if torch.cuda.is_available() else -1
    depth_pipe = pipeline(
        task="depth-estimation",
        model="depth-anything/Depth-Anything-V2-Small-hf",
        device=device,
    )

    results: list[DishResult] = []
    out_path = Path(out_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="n5k_frames_") as tmp:
        tmp_dir = Path(tmp)
        for sample in depth_samples:
            print(f"[{sample.id}] ...", end=" ", flush=True)
            try:
                results.append(_score_dish(sample, depth_pipe, tmp_dir))
                print("ok")
            except Exception as exc:  # keep going; record and move on
                print(f"FAILED: {exc}")
                results.append(
                    DishResult(
                        id=sample.id,
                        mass_g=sample.mass_g or 0.0,
                        oracle_volume_proxy=None,
                        mono_volume_proxy=None,
                        video_frames=None,
                        video_bulge_cv=None,
                        error=str(exc),
                    )
                )

    _fit_and_score(results)
    _write_results(results, out_path)


def _score_dish(sample: Sample, depth_pipe, tmp_dir: Path) -> DishResult:
    assert sample.mass_g is not None
    depth_path = resolve(str(sample.extra["depth_path"]))
    rgb_path = sample.resolved_image_path()
    assert rgb_path is not None

    oracle_depth = load_depth_mm(depth_path)
    oracle_proxy = volume_proxy(oracle_depth)

    rgb = Image.open(rgb_path).convert("RGB")
    mono_out = depth_pipe(rgb)
    mono_relative = np.array(mono_out["predicted_depth"].squeeze(0))
    if mono_relative.shape != oracle_depth.shape:
        mono_relative = np.array(
            Image.fromarray(mono_relative).resize(
                (oracle_depth.shape[1], oracle_depth.shape[0]), Image.BILINEAR
            )
        )
    mono_calibrated = calibrate_monocular(oracle_depth, mono_relative)
    mono_proxy = volume_proxy(np.where(oracle_depth > 0, mono_calibrated, 0.0))

    video_frames = None
    video_cv = None
    video_rel = sample.extra.get("video_path")
    if video_rel:
        frame_dir = tmp_dir / sample.id
        frames = extract_video_frames(resolve(str(video_rel)), frame_dir)
        if frames:
            proxies = []
            for frame_path in frames:
                frame_img = Image.open(frame_path).convert("RGB")
                frame_out = depth_pipe(frame_img)
                frame_depth = np.array(frame_out["predicted_depth"].squeeze(0))
                proxies.append(bulge_proxy(frame_depth))
            video_frames = len(proxies)
            mean_p = float(np.mean(proxies))
            video_cv = float(np.std(proxies) / mean_p) if mean_p else None

    return DishResult(
        id=sample.id,
        mass_g=sample.mass_g,
        oracle_volume_proxy=oracle_proxy,
        mono_volume_proxy=mono_proxy,
        video_frames=video_frames,
        video_bulge_cv=video_cv,
    )


def _fit_and_score(results: list[DishResult]) -> None:
    ok = [r for r in results if r.error is None]
    if not ok:
        return

    oracle_k = fit_global_scale(
        [r.oracle_volume_proxy for r in ok], [r.mass_g for r in ok]
    )
    mono_k = fit_global_scale([r.mono_volume_proxy for r in ok], [r.mass_g for r in ok])

    for r in ok:
        r.oracle_mass_g = oracle_k * r.oracle_volume_proxy
        r.oracle_abs_err = abs(r.oracle_mass_g - r.mass_g)
        r.oracle_ape = r.oracle_abs_err / r.mass_g if r.mass_g else None

        r.mono_mass_g = mono_k * r.mono_volume_proxy
        r.mono_abs_err = abs(r.mono_mass_g - r.mass_g)
        r.mono_ape = r.mono_abs_err / r.mass_g if r.mass_g else None


def _write_results(results: list[DishResult], out_dir: Path) -> None:
    csv_path = out_dir / "per_dish.csv"
    with csv_path.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(asdict(results[0]).keys()))
        writer.writeheader()
        for r in results:
            writer.writerow(asdict(r))

    ok = [r for r in results if r.error is None]
    n = len(ok)
    summary = {"n": n, "n_failed": len(results) - n}
    if n:
        oracle_apes = [r.oracle_ape for r in ok if r.oracle_ape is not None]
        mono_apes = [r.mono_ape for r in ok if r.mono_ape is not None]
        oracle_mae = [r.oracle_abs_err for r in ok if r.oracle_abs_err is not None]
        mono_mae = [r.mono_abs_err for r in ok if r.mono_abs_err is not None]
        masses = np.array([r.mass_g for r in ok])
        oracle_proxies = np.array([r.oracle_volume_proxy for r in ok])
        mono_proxies = np.array([r.mono_volume_proxy for r in ok])
        summary.update(
            {
                # MAE/MAPE here are IN-SAMPLE: the global scale k was fit on
                # this same n-dish set, so these overstate held-out accuracy.
                # Correlation (independent of the fit) is the more honest
                # signal for "does depth information predict mass at all"
                # at this small a sample size.
                "oracle_proxy_mass_corr": round(float(np.corrcoef(oracle_proxies, masses)[0, 1]), 3),
                "mono_proxy_mass_corr": round(float(np.corrcoef(mono_proxies, masses)[0, 1]), 3),
                "oracle_mass_mae_g_insample": round(float(np.mean(oracle_mae)), 1) if oracle_mae else None,
                "oracle_mass_mape_insample": round(float(np.mean(oracle_apes)), 4) if oracle_apes else None,
                "mono_mass_mae_g_insample": round(float(np.mean(mono_mae)), 1) if mono_mae else None,
                "mono_mass_mape_insample": round(float(np.mean(mono_apes)), 4) if mono_apes else None,
            }
        )
        video_cvs = [r.video_bulge_cv for r in ok if r.video_bulge_cv is not None]
        if video_cvs:
            summary["video_bulge_cv_mean"] = round(float(np.mean(video_cvs)), 3)
            summary["video_dishes_with_frames"] = len(video_cvs)

    summary_path = out_dir / "summary.json"
    summary_path.write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))
    print(f"Wrote {csv_path}")
    print(f"Wrote {summary_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Depth/volume estimation vs Nutrition5k mass_g")
    parser.add_argument(
        "--manifest",
        default="docs/benchmarks/food_accuracy/data/manifests/n5k_depth.jsonl",
    )
    parser.add_argument(
        "--out",
        default="docs/benchmarks/food_accuracy/results/depth_volume/n5k",
    )
    parser.add_argument("--limit", type=int, default=None)
    args = parser.parse_args()
    run(args.manifest, args.out, args.limit)


if __name__ == "__main__":
    main()
