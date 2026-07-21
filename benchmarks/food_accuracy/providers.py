#!/usr/bin/env python3
"""OpenAI-compatible, OpenRouter, NoFUD free-router, and stub providers."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import random
import time
import urllib.error
import urllib.request
from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path

from env_local import openrouter_api_key
from openrouter_models import (
    NOFUD_FREE_ROUTER_ID,
    eligible_chat_free_models,
)


@dataclass
class ProviderResponse:
    text: str
    latency_ms: float
    model: str
    usage: dict | None = None
    routed_model: str | None = None


class Provider(ABC):
    @abstractmethod
    def complete(self, *, prompt: str, image_path: Path | None = None) -> ProviderResponse:
        raise NotImplementedError


class StubProvider(Provider):
    """Deterministic fake macros for pipeline testing without API keys."""

    def complete(self, *, prompt: str, image_path: Path | None = None) -> ProviderResponse:
        seed = hashlib.sha256((prompt + str(image_path)).encode()).hexdigest()
        calories = 200 + (int(seed[:4], 16) % 600)
        protein = 10 + (int(seed[4:8], 16) % 40)
        carbs = 15 + (int(seed[8:12], 16) % 60)
        fat = 5 + (int(seed[12:16], 16) % 30)
        payload = {
            "name": "stub food",
            "calories": calories,
            "protein": float(protein),
            "carbs": float(carbs),
            "fat": float(fat),
            "serving_size_grams": 250.0,
            "unit_options": [],
        }
        return ProviderResponse(
            text=json.dumps(payload),
            latency_ms=1.0,
            model="stub",
        )


class OpenAICompatibleProvider(Provider):
    def __init__(
        self,
        *,
        model: str,
        api_key: str | None = None,
        base_url: str | None = None,
        timeout_s: float = 120.0,
        extra_headers: dict[str, str] | None = None,
        extra_body: dict | None = None,
    ) -> None:
        self.model = model
        self.api_key = api_key or os.environ.get("OPENAI_API_KEY", "")
        self.base_url = (base_url or os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1")).rstrip("/")
        self.timeout_s = timeout_s
        self.extra_headers = extra_headers or {}
        self.extra_body = extra_body or {}

    def complete(self, *, prompt: str, image_path: Path | None = None) -> ProviderResponse:
        if not self.api_key:
            raise RuntimeError("API key is required for this provider")

        content: list[dict] = [{"type": "text", "text": prompt}]
        if image_path is not None:
            if not image_path.exists():
                raise FileNotFoundError(f"Image not found: {image_path}")
            encoded = base64.b64encode(image_path.read_bytes()).decode("ascii")
            mime = "image/jpeg"
            if image_path.suffix.lower() == ".png":
                mime = "image/png"
            elif image_path.suffix.lower() == ".webp":
                mime = "image/webp"
            content.append(
                {
                    "type": "image_url",
                    "image_url": {"url": f"data:{mime};base64,{encoded}"},
                }
            )

        body = {
            "model": self.model,
            "messages": [{"role": "user", "content": content}],
            "temperature": 0.2,
            **self.extra_body,
        }
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}",
            **self.extra_headers,
        }
        req = urllib.request.Request(
            f"{self.base_url}/chat/completions",
            data=json.dumps(body).encode("utf-8"),
            headers=headers,
            method="POST",
        )

        started = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=self.timeout_s) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"API HTTP {exc.code}: {detail}") from exc

        latency_ms = (time.perf_counter() - started) * 1000.0
        try:
            text = payload["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise RuntimeError(f"Unexpected API response: {payload}") from exc

        usage = payload.get("usage")
        routed = payload.get("model")
        return ProviderResponse(
            text=text or "",
            latency_ms=latency_ms,
            model=self.model,
            usage=usage,
            routed_model=routed if routed and routed != self.model else routed,
        )


class OpenRouterProvider(OpenAICompatibleProvider):
    """OpenRouter chat/completions with NoFUD-aligned headers and reasoning disabled."""

    def __init__(
        self,
        *,
        model: str,
        api_key: str | None = None,
        timeout_s: float = 180.0,
    ) -> None:
        key = api_key or openrouter_api_key()
        super().__init__(
            model=model,
            api_key=key,
            base_url="https://openrouter.ai/api/v1",
            timeout_s=timeout_s,
            extra_headers={
                "HTTP-Referer": "https://codeberg.org/fitguy/NoFUD",
                "X-Title": "NoFUD Food Accuracy Benchmark",
            },
            extra_body={"reasoning": {"exclude": True}},
        )


class NofudFreeRouterProvider(Provider):
    """Pick randomly among live OpenRouter :free chat models, excluding content-safety.

    - Text prompts → any eligible free chat model
    - Image prompts → vision-capable free models only
    - On 429/502, try another model from the pool (up to ``failover`` attempts)
    """

    def __init__(
        self,
        *,
        api_key: str | None = None,
        timeout_s: float = 180.0,
        failover: int = 3,
        seed: int | None = None,
    ) -> None:
        self.api_key = api_key or openrouter_api_key()
        if not self.api_key:
            raise RuntimeError("OPENROUTER_TOKEN is required for nofud/free")
        self.timeout_s = timeout_s
        self.failover = max(1, failover)
        self._rng = random.Random(seed)
        self.model = NOFUD_FREE_ROUTER_ID
        self._text_pool: list[str] | None = None
        self._vision_pool: list[str] | None = None

    def refresh_pools(self) -> None:
        text = eligible_chat_free_models(api_key=self.api_key, require_vision=False)
        vision = eligible_chat_free_models(api_key=self.api_key, require_vision=True)
        self._text_pool = [m.id for m in text]
        self._vision_pool = [m.id for m in vision]
        if not self._text_pool:
            raise RuntimeError("No eligible free chat models found on OpenRouter")
        if not self._vision_pool:
            # Fall back to text-only pool listing for diagnostics; image calls will fail clearly.
            self._vision_pool = []

    def _pool_for(self, *, need_vision: bool) -> list[str]:
        if self._text_pool is None or self._vision_pool is None:
            self.refresh_pools()
        assert self._text_pool is not None and self._vision_pool is not None
        if need_vision:
            if not self._vision_pool:
                raise RuntimeError("No vision-capable free models available for nofud/free")
            return list(self._vision_pool)
        return list(self._text_pool)

    def complete(self, *, prompt: str, image_path: Path | None = None) -> ProviderResponse:
        need_vision = image_path is not None
        pool = self._pool_for(need_vision=need_vision)
        self._rng.shuffle(pool)
        candidates = pool[: self.failover]
        if not candidates:
            raise RuntimeError("Empty free-model pool")

        errors: list[str] = []
        for model_id in candidates:
            backend = OpenRouterProvider(
                model=model_id,
                api_key=self.api_key,
                timeout_s=self.timeout_s,
            )
            try:
                response = backend.complete(prompt=prompt, image_path=image_path)
            except Exception as exc:  # noqa: BLE001
                msg = str(exc)
                errors.append(f"{model_id}: {msg}")
                retryable = "429" in msg or "rate-limited" in msg or "502" in msg or "ResourceExhausted" in msg
                if retryable:
                    continue
                # Non-retryable: still try another free model once or twice.
                continue

            return ProviderResponse(
                text=response.text,
                latency_ms=response.latency_ms,
                model=NOFUD_FREE_ROUTER_ID,
                usage=response.usage,
                routed_model=response.routed_model or model_id,
            )

        raise RuntimeError(
            "nofud/free exhausted failover pool: " + " | ".join(errors[:5])
        )


def build_provider(name: str, *, model: str) -> Provider:
    if name == "stub":
        return StubProvider()
    if name == "openrouter":
        if model in {NOFUD_FREE_ROUTER_ID, "nofud/free-router", "free"}:
            return NofudFreeRouterProvider()
        return OpenRouterProvider(model=model)
    if name in {"openai", "ollama"}:
        return OpenAICompatibleProvider(model=model)
    raise ValueError(f"Unknown provider {name!r}. Use stub, openai, ollama, or openrouter.")
