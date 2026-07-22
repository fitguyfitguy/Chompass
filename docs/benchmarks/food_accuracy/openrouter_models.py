#!/usr/bin/env python3
"""Query OpenRouter model catalog and filter free / vision-capable models."""

from __future__ import annotations

import json
import re
import urllib.error
import urllib.request
from dataclasses import dataclass

OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models"
FREE_ROUTER_ID = "openrouter/free"
NOFUD_FREE_ROUTER_ID = "nofud/free"

# Models that are free but not useful for food JSON generation (classifiers, audio, etc.).
_EXCLUDE_ID_RE = re.compile(
    r"(content[-_]?safety|safety[-_]?classifier|moderation|lyria|"
    r"embedding|whisper|tts|image-edit|flux|stable-diffusion)",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class OpenRouterModel:
    id: str
    name: str
    input_modalities: tuple[str, ...]
    prompt_price: float
    completion_price: float
    description: str = ""

    @property
    def is_free(self) -> bool:
        return self.prompt_price == 0.0 and self.completion_price == 0.0

    @property
    def supports_vision(self) -> bool:
        return "image" in self.input_modalities

    @property
    def supports_text(self) -> bool:
        return "text" in self.input_modalities or not self.input_modalities


def _price(value: str | float | int | None) -> float:
    if value is None:
        return 0.0
    return float(value)


def is_excluded_model(model_id: str, *, name: str = "", description: str = "") -> bool:
    """True for content-safety / non-chat free models we never want in the pool."""
    blob = f"{model_id}\n{name}\n{description}"
    if _EXCLUDE_ID_RE.search(blob):
        return True
    if model_id in {FREE_ROUTER_ID, NOFUD_FREE_ROUTER_ID}:
        return True
    return False


def fetch_models(*, api_key: str | None = None, timeout_s: float = 60.0) -> list[OpenRouterModel]:
    headers = {"Accept": "application/json"}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"

    req = urllib.request.Request(OPENROUTER_MODELS_URL, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenRouter models HTTP {exc.code}: {detail}") from exc

    models: list[OpenRouterModel] = []
    for item in payload.get("data", []):
        arch = item.get("architecture") or {}
        modalities = tuple(arch.get("input_modalities") or ["text"])
        pricing = item.get("pricing") or {}
        models.append(
            OpenRouterModel(
                id=str(item["id"]),
                name=str(item.get("name") or item["id"]),
                input_modalities=modalities,
                prompt_price=_price(pricing.get("prompt")),
                completion_price=_price(pricing.get("completion")),
                description=str(item.get("description") or ""),
            )
        )
    return models


def list_free_models(
    *,
    api_key: str | None = None,
    vision: bool | None = None,
    include_router: bool = True,
    include_nofud_router: bool = False,
    exclude_filters: bool = True,
) -> list[OpenRouterModel]:
    """Return free models from the live catalog, optionally filtered for vision."""
    free = [m for m in fetch_models(api_key=api_key) if m.is_free or m.id.endswith(":free")]
    if exclude_filters:
        free = [
            m
            for m in free
            if not is_excluded_model(m.id, name=m.name, description=m.description)
        ]
    if vision is True:
        free = [m for m in free if m.supports_vision]
    elif vision is False:
        free = [m for m in free if not m.supports_vision]

    # De-dupe by id while preserving order.
    seen: set[str] = set()
    ordered: list[OpenRouterModel] = []
    if include_nofud_router:
        ordered.append(
            OpenRouterModel(
                id=NOFUD_FREE_ROUTER_ID,
                name="NoFUD Free Router (excludes content-safety)",
                input_modalities=("text", "image"),
                prompt_price=0.0,
                completion_price=0.0,
            )
        )
        seen.add(NOFUD_FREE_ROUTER_ID)
    if include_router:
        ordered.append(
            OpenRouterModel(
                id=FREE_ROUTER_ID,
                name="OpenRouter Free Router (dynamic)",
                input_modalities=("text", "image"),
                prompt_price=0.0,
                completion_price=0.0,
            )
        )
        seen.add(FREE_ROUTER_ID)

    for model in sorted(free, key=lambda m: m.id):
        if model.id in seen:
            continue
        seen.add(model.id)
        ordered.append(model)
    return ordered


def eligible_chat_free_models(
    *,
    api_key: str | None = None,
    require_vision: bool = False,
) -> list[OpenRouterModel]:
    """Free chat models suitable for food JSON (no content-safety / audio / embeddings)."""
    models = list_free_models(
        api_key=api_key,
        vision=True if require_vision else None,
        include_router=False,
        include_nofud_router=False,
        exclude_filters=True,
    )
    # Always need text in/out for our JSON schema prompts.
    return [m for m in models if m.supports_text]
