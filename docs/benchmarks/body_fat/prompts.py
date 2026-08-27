#!/usr/bin/env python3
"""JSON-only BF% prompts (no CoT in the user channel)."""

from __future__ import annotations

from schema import Subject

SYSTEM = (
    "Return only JSON: {\"bf_percent\": number, \"lo\": number, \"hi\": number}. "
    "bf_percent is body fat percentage (0-100). lo/hi are a plausible range. "
    "No markdown, no extra keys."
)


def _anthro(subject: Subject, *, drop_neck: bool) -> str:
    parts = [
        f"sex={subject.sex}",
        f"age_years={subject.age}",
        f"height_cm={subject.height_cm:.1f}",
        f"weight_kg={subject.weight_kg:.1f}",
    ]
    sites = [
        ("neck_cm", None if drop_neck else subject.neck_cm),
        ("waist_cm", subject.waist_cm),
        ("hips_cm", subject.hips_cm),
        ("chest_cm", subject.chest_cm),
        ("upper_arm_cm", subject.upper_arm_cm),
        ("thigh_cm", subject.thigh_cm),
        ("wrist_cm", subject.wrist_cm),
    ]
    for name, val in sites:
        if val is not None:
            parts.append(f"{name}={val:.1f}")
    return "\n".join(parts)


def user_prompt(subject: Subject, variant: str, navy: float | None) -> str:
    drop = variant == "missing_neck"
    body = _anthro(subject, drop_neck=drop)
    if variant == "navy_anchor" and navy is not None:
        body += (
            f"\nrough_navy_estimate_percent={navy:.1f} "
            "(rough estimate, not exact)"
        )
    return "Estimate body fat percent from these measurements:\n" + body
