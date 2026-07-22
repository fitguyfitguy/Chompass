#!/usr/bin/env python3
"""Core types and manifest I/O for the food accuracy benchmark."""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Iterator

ROOT = Path(__file__).resolve().parents[2]
BENCHMARK_ROOT = Path(__file__).resolve().parent
DATA_DIR = BENCHMARK_ROOT / "data"
RESULTS_DIR = BENCHMARK_ROOT / "results"

MACRO_FIELDS = ("calories", "protein_g", "carbs_g", "fat_g")


@dataclass
class GroundTruth:
    calories: float
    protein_g: float
    carbs_g: float
    fat_g: float
    mass_g: float | None = None

    def as_dict(self) -> dict[str, float | None]:
        out: dict[str, float | None] = {
            "calories": self.calories,
            "protein_g": self.protein_g,
            "carbs_g": self.carbs_g,
            "fat_g": self.fat_g,
        }
        if self.mass_g is not None:
            out["mass_g"] = self.mass_g
        return out


@dataclass
class Sample:
    id: str
    modality: str
    source: str
    calories: float
    protein_g: float
    carbs_g: float
    fat_g: float
    text: str | None = None
    image_path: str | None = None
    mass_g: float | None = None
    meal_name: str | None = None
    notes: str | None = None
    extra: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> Sample:
        known = {
            "id",
            "modality",
            "source",
            "calories",
            "protein_g",
            "carbs_g",
            "fat_g",
            "text",
            "image_path",
            "mass_g",
            "meal_name",
            "notes",
        }
        extra = {k: v for k, v in raw.items() if k not in known}
        return cls(
            id=str(raw["id"]),
            modality=str(raw["modality"]),
            source=str(raw["source"]),
            calories=float(raw["calories"]),
            protein_g=float(raw["protein_g"]),
            carbs_g=float(raw["carbs_g"]),
            fat_g=float(raw["fat_g"]),
            text=raw.get("text"),
            image_path=raw.get("image_path"),
            mass_g=float(raw["mass_g"]) if raw.get("mass_g") is not None else None,
            meal_name=raw.get("meal_name"),
            notes=raw.get("notes"),
            extra=extra,
        )

    def ground_truth(self) -> GroundTruth:
        return GroundTruth(
            calories=self.calories,
            protein_g=self.protein_g,
            carbs_g=self.carbs_g,
            fat_g=self.fat_g,
            mass_g=self.mass_g,
        )

    def to_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {
            "id": self.id,
            "modality": self.modality,
            "source": self.source,
            "calories": self.calories,
            "protein_g": self.protein_g,
            "carbs_g": self.carbs_g,
            "fat_g": self.fat_g,
        }
        if self.text is not None:
            out["text"] = self.text
        if self.image_path is not None:
            out["image_path"] = self.image_path
        if self.mass_g is not None:
            out["mass_g"] = self.mass_g
        if self.meal_name is not None:
            out["meal_name"] = self.meal_name
        if self.notes is not None:
            out["notes"] = self.notes
        out.update(self.extra)
        return out

    def resolved_image_path(self) -> Path | None:
        if not self.image_path:
            return None
        path = Path(self.image_path)
        if path.is_absolute():
            return path
        return ROOT / path


def load_manifest(path: Path | str, *, limit: int | None = None) -> list[Sample]:
    manifest_path = Path(path)
    if not manifest_path.is_absolute():
        manifest_path = ROOT / manifest_path
    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifest not found: {manifest_path}")

    samples: list[Sample] = []
    with manifest_path.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            samples.append(Sample.from_dict(json.loads(line)))
            if limit is not None and len(samples) >= limit:
                break
    return samples


def write_manifest(path: Path | str, samples: Iterator[Sample] | list[Sample]) -> None:
    out_path = Path(path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8") as handle:
        for sample in samples:
            handle.write(json.dumps(sample.to_dict(), ensure_ascii=False) + "\n")


def validate_sample(sample: Sample) -> list[str]:
    errors: list[str] = []
    if sample.modality not in {"text", "image"}:
        errors.append(f"{sample.id}: invalid modality {sample.modality!r}")
    if sample.modality == "text" and not sample.text:
        errors.append(f"{sample.id}: text modality requires text")
    if sample.modality == "image" and not sample.image_path:
        errors.append(f"{sample.id}: image modality requires image_path")
    for field_name in MACRO_FIELDS:
        value = getattr(sample, field_name)
        if value < 0:
            errors.append(f"{sample.id}: negative {field_name}")
    return errors
