#!/usr/bin/env python3
"""Enrich downloaded manifests with oracle clarification answers.

Reads data/manifests/{jfb,n5k}.jsonl, derives `clarify_portion` / `clarify_fat`
extras (clarify.py), and writes `<name>_clarify.jsonl` plus per-oracle covered-id
lists for --ids-restricted comparison:

    uv run python docs/benchmarks/food_accuracy/build_clarify_manifests.py
    uv run python docs/benchmarks/food_accuracy/run_eval.py \
        --manifest docs/benchmarks/food_accuracy/data/manifests/jfb_clarify.jsonl \
        --prompt compact_clarify_both --provider openrouter --model <pinned> \
        --ids "$(paste -sd, .../jfb_clarify_ids_both.txt)"

Nutrition5k ingredient lists are attached from the metadata cache when the
manifest predates ingredient-aware download_nutrition5k.py.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from clarify import derive_clarify_fields
from download_nutrition5k import load_dish_metadata
from schema import DATA_DIR, Sample, load_manifest, write_manifest

MANIFEST_DIR = DATA_DIR / "manifests"
N5K_META_DIR = DATA_DIR / "nutrition5k" / "metadata_cache"


def attach_n5k_ingredients(samples: list[Sample]) -> None:
    if not (N5K_META_DIR / "metadata").exists():
        print("WARN: Nutrition5k metadata cache missing; fat oracle unavailable for n5k", file=sys.stderr)
        return
    dishes = load_dish_metadata(N5K_META_DIR)
    for sample in samples:
        if sample.extra.get("ingredients"):
            continue
        meta = dishes.get(sample.meal_name or "")
        if meta is not None:
            sample.extra["ingredients"] = meta.get("ingredients") or []


def enrich(name: str, manifest_path: Path) -> None:
    samples = load_manifest(manifest_path)
    if name.startswith("n5k"):
        attach_n5k_ingredients(samples)

    portion_ids: list[str] = []
    fat_ids: list[str] = []
    fat_present = 0
    for sample in samples:
        fields = derive_clarify_fields(sample)
        sample.extra.update(fields)
        if "clarify_portion" in fields:
            portion_ids.append(sample.id)
        if "clarify_fat" in fields:
            fat_ids.append(sample.id)
            if fields["clarify_fat"]["present"]:
                fat_present += 1

    both_ids = [i for i in portion_ids if i in set(fat_ids)]
    out_path = manifest_path.with_name(f"{manifest_path.stem}_clarify.jsonl")
    write_manifest(out_path, samples)
    for suffix, ids in (("portion", portion_ids), ("fat", fat_ids), ("both", both_ids)):
        ids_path = manifest_path.with_name(f"{manifest_path.stem}_clarify_ids_{suffix}.txt")
        ids_path.write_text("\n".join(ids) + ("\n" if ids else ""), encoding="utf-8")

    n = len(samples)
    fat_rate = f"{fat_present}/{len(fat_ids)}" if fat_ids else "n/a"
    print(
        f"{name}: {n} samples -> {out_path.name} | portion oracle {len(portion_ids)}/{n}, "
        f"fat oracle {len(fat_ids)}/{n} (fat present {fat_rate}), both {len(both_ids)}/{n}"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Build clarify-enriched manifests")
    parser.add_argument(
        "--manifests",
        nargs="*",
        default=["jfb", "n5k"],
        help="Manifest stems under data/manifests (default: jfb n5k)",
    )
    args = parser.parse_args()

    missing = []
    for name in args.manifests:
        path = MANIFEST_DIR / f"{name}.jsonl"
        if not path.exists():
            missing.append(str(path))
            continue
        enrich(name, path)
    if missing:
        print(f"WARN: missing manifests (run the download scripts first): {missing}", file=sys.stderr)


if __name__ == "__main__":
    main()
