"""Shared helpers for image + short-note (L1/L2/Lq) manifest variants.

L0 = image only (``text`` absent)
L1 = meal title / coarse label as user ``text``
L2 = ingredient / food-item names only (no quantities or macros) as user ``text``
Lq = vague quantity diary note (bucket / coarsened amounts; no ``\\d+ g``) —
    see ``build_image_text_lq.py``

Text-only clones (``*_text_lq.jsonl`` / ``*_text_l1.jsonl``) keep the same
``text`` strings with ``modality=text`` and no image — see ``build_text_lq.py``.

Prompt builders inject ``sample.text`` as the optional user note (app
``analyzeFood(description=…)``). ``meal_name`` stays metadata-only.
"""

from __future__ import annotations

from pathlib import Path

from schema import Sample, write_manifest


def ingredient_names_text(ingredients: list | None) -> str | None:
    """Comma-join ingredient/food-item names; strip empty. No quantities."""
    if not ingredients:
        return None
    names: list[str] = []
    for item in ingredients:
        if isinstance(item, dict):
            name = str(item.get("name") or "").strip()
        else:
            name = str(item).strip()
        if name:
            names.append(name)
    if not names:
        return None
    # Preserve order, drop exact duplicates (common on multi-angle / repeated items).
    seen: set[str] = set()
    unique: list[str] = []
    for name in names:
        key = name.casefold()
        if key in seen:
            continue
        seen.add(key)
        unique.append(name)
    return ", ".join(unique)


def clone_sample(sample: Sample, *, text: str | None) -> Sample:
    return Sample(
        id=sample.id,
        modality=sample.modality,
        source=sample.source,
        calories=sample.calories,
        protein_g=sample.protein_g,
        carbs_g=sample.carbs_g,
        fat_g=sample.fat_g,
        text=text,
        image_path=sample.image_path,
        mass_g=sample.mass_g,
        meal_name=sample.meal_name,
        notes=sample.notes,
        extra=dict(sample.extra),
    )


def write_image_text_variants(
    samples: list[Sample],
    manifests_dir: Path,
    *,
    prefix: str,
    write_l1: bool = True,
    write_l2: bool = True,
    l1_text_fn=None,
    l2_text_fn=None,
) -> tuple[int, int]:
    """Write ``{prefix}_image_text_l1.jsonl`` / ``_l2.jsonl`` next to L0.

    ``l1_text_fn`` / ``l2_text_fn`` default to ``meal_name`` and ingredient
    names from ``extra["ingredients"]``. Return (n_l1, n_l2) written (0 if skipped).
    """
    manifests_dir = Path(manifests_dir)
    manifests_dir.mkdir(parents=True, exist_ok=True)

    def default_l1(sample: Sample) -> str | None:
        return sample.meal_name

    def default_l2(sample: Sample) -> str | None:
        return ingredient_names_text(sample.extra.get("ingredients"))

    l1_fn = l1_text_fn or default_l1
    l2_fn = l2_text_fn or default_l2

    n_l1 = n_l2 = 0
    if write_l1:
        l1 = [clone_sample(s, text=l1_fn(s)) for s in samples]
        path = manifests_dir / f"{prefix}_image_text_l1.jsonl"
        write_manifest(path, l1)
        n_l1 = len(l1)
        print(f"Wrote {n_l1} samples to {path}")
    if write_l2:
        l2 = [clone_sample(s, text=l2_fn(s)) for s in samples]
        path = manifests_dir / f"{prefix}_image_text_l2.jsonl"
        write_manifest(path, l2)
        n_l2 = len(l2)
        print(f"Wrote {n_l2} samples to {path}")
    return n_l1, n_l2
