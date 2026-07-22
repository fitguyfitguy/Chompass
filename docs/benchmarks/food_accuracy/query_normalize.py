"""Shared query normalization for grounded retrieval (Python mirror of QueryNormalizer.kt)."""

from __future__ import annotations

import re

SYNONYMS = {
    "yoghurt": "yogurt",
    "yoghourt": "yogurt",
    "brezel": "pretzel",
    "laugenbrezel": "pretzel",
    "aubergine": "eggplant",
    "courgette": "zucchini",
    "mince": "ground",
    "minced": "ground",
    "capsicum": "pepper",
    "coriander": "cilantro",
    "biscuit": "cookie",
    "chips": "fries",
    "prawn": "shrimp",
    "prawns": "shrimp",
}

UNIT_NOISE = {
    "g",
    "gram",
    "grams",
    "kg",
    "mg",
    "ml",
    "l",
    "liter",
    "litre",
    "oz",
    "ounce",
    "ounces",
    "lb",
    "pound",
    "pounds",
    "cup",
    "cups",
    "tbsp",
    "tablespoon",
    "tablespoons",
    "tsp",
    "teaspoon",
    "teaspoons",
    "slice",
    "slices",
    "piece",
    "pieces",
    "serving",
    "servings",
    "large",
    "medium",
    "small",
    "can",
    "cans",
    "bottle",
    "glass",
    "glasses",
    "scoop",
    "scoops",
    "bar",
    "bars",
    "half",
    "quarter",
    "approx",
    "approximately",
    "about",
}

_NUM = re.compile(r"^\d+([./]\d+)?$")
_PUNCT = re.compile(r"[^a-z0-9]+")


def tokenize(text: str) -> list[str]:
    cleaned = _PUNCT.sub(" ", (text or "").lower())
    return [t for t in cleaned.split() if len(t) >= 2]


def normalize_tokens(text: str, *, strip_units: bool = True, apply_synonyms: bool = True) -> list[str]:
    tokens: list[str] = []
    for raw in tokenize(text):
        if strip_units and (_NUM.match(raw) or raw in UNIT_NOISE):
            continue
        tok = SYNONYMS.get(raw, raw) if apply_synonyms else raw
        if tok not in tokens:
            tokens.append(tok)
    return tokens


def normalize_query(text: str) -> str:
    return " ".join(normalize_tokens(text))
