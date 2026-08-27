#!/usr/bin/env python3
"""L0 body-fat formulas. Navy coefficients match production USNAVY."""

from __future__ import annotations

import math
from dataclasses import dataclass

from schema import SITE_FIELDS, Subject


def rfm(subject: Subject) -> float | None:
    """Woolcott & Bergman 2018. sex 0 male / 1 female."""
    if subject.height_cm <= 0 or not subject.waist_cm or subject.waist_cm <= 0:
        return None
    sex = 1.0 if subject.sex == "female" else 0.0
    return 64.0 - 20.0 * (subject.height_cm / subject.waist_cm) + 12.0 * sex


def us_navy(subject: Subject) -> float | None:
    """Metric US Navy; reject outside [2, 65] like Kotlin/JS."""
    if subject.height_cm <= 0 or not subject.neck_cm or not subject.waist_cm:
        return None
    neck = subject.neck_cm
    waist = subject.waist_cm
    height = subject.height_cm
    if subject.sex == "female":
        if not subject.hips_cm:
            return None
        inner = waist + subject.hips_cm - neck
        if inner <= 0:
            return None
        denom = 1.29579 - 0.35004 * math.log10(inner) + 0.22100 * math.log10(height)
    else:
        inner = waist - neck
        if inner <= 0:
            return None
        denom = 1.0324 - 0.19077 * math.log10(inner) + 0.15456 * math.log10(height)
    if denom == 0 or not math.isfinite(denom):
        return None
    percent = 495.0 / denom - 450.0
    if not math.isfinite(percent) or percent < 2 or percent > 65:
        return None
    return percent


def deurenberg(subject: Subject) -> float | None:
    """Deurenberg 1991: 1.20*BMI + 0.23*age - 10.8*sex - 5.4 (sex 1=male)."""
    if subject.height_cm <= 0 or subject.weight_kg <= 0:
        return None
    bmi = subject.weight_kg / (subject.height_cm / 100.0) ** 2
    sex = 1.0 if subject.sex == "male" else 0.0
    return 1.20 * bmi + 0.23 * subject.age - 10.8 * sex - 5.4


def bmi_as_bf(subject: Subject) -> float | None:
    if subject.height_cm <= 0 or subject.weight_kg <= 0:
        return None
    return subject.weight_kg / (subject.height_cm / 100.0) ** 2


FORMULAS = {
    "rfm": rfm,
    "navy": us_navy,
    "deurenberg": deurenberg,
    "bmi": bmi_as_bf,
}


def _feature_row(subject: Subject) -> list[float]:
    sex = 1.0 if subject.sex == "female" else 0.0
    bmi = bmi_as_bf(subject) or 0.0
    row = [
        float(subject.age),
        subject.height_cm,
        subject.weight_kg,
        bmi,
        sex,
    ]
    for name in SITE_FIELDS:
        val = getattr(subject, name)
        row.append(float(val) if val is not None else 0.0)
        row.append(0.0 if val is None else 1.0)
    return row


def ridge_oof(subjects: list[Subject], *, n_splits: int = 5) -> list[float | None]:
    """Out-of-fold Ridge; if this beats an LLM, the feature is a formula."""
    try:
        import numpy as np
        from sklearn.linear_model import RidgeCV
        from sklearn.model_selection import KFold
    except ImportError as exc:
        raise SystemExit(
            "ridge needs sklearn: uv run --with scikit-learn --with numpy python baselines.py"
        ) from exc

    n = len(subjects)
    if n < 4:
        return [None] * n
    x = np.array([_feature_row(s) for s in subjects], dtype=float)
    y = np.array([s.bf_percent for s in subjects], dtype=float)
    splits = min(n_splits, n)
    kf = KFold(n_splits=splits, shuffle=True, random_state=0)
    pred = np.full(n, np.nan)
    for train, test in kf.split(x):
        model = RidgeCV(alphas=(0.1, 1.0, 10.0, 100.0))
        model.fit(x[train], y[train])
        pred[test] = model.predict(x[test])
    return [None if math.isnan(v) else float(v) for v in pred]


@dataclass
class NavyGolden:
    sex: str
    height_cm: float
    neck_cm: float
    waist_cm: float
    hips_cm: float | None
    expected: float


# Hand-computed with the production coefficients (not Penrose GT).
NAVY_GOLDENS = [
    NavyGolden("male", 172.085, 36.2, 85.2, None, 18.9642),
    NavyGolden("female", 164.0, 32.0, 74.0, 98.0, 28.7050),
]


def check_navy_goldens(*, atol: float = 0.02) -> None:
    for g in NAVY_GOLDENS:
        got = us_navy(
            Subject(
                id="golden",
                source="golden",
                sex=g.sex,
                age=30,
                height_cm=g.height_cm,
                weight_kg=70.0,
                bf_percent=0.0,
                bf_method="dxa",
                neck_cm=g.neck_cm,
                waist_cm=g.waist_cm,
                hips_cm=g.hips_cm,
            )
        )
        assert got is not None, g
        if abs(got - g.expected) > atol:
            raise AssertionError(f"Navy golden {g.sex}: got {got:.4f} expected {g.expected:.4f}")


if __name__ == "__main__":
    check_navy_goldens()
    print("navy goldens ok")
